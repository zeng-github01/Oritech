package rearth.oritech.block.entity.storage;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import rearth.oritech.client.init.ModScreens;
import rearth.oritech.client.ui.BasicMachineScreenHandler;
import rearth.oritech.client.ui.UpgradableMachineScreenHandler;
import rearth.oritech.init.BlockEntitiesContent;
import rearth.oritech.network.NetworkContent;
import rearth.oritech.util.*;
import rearth.oritech.util.energy.EnergyApi;
import rearth.oritech.util.energy.containers.DelegatingEnergyStorage;
import rearth.oritech.util.energy.containers.DynamicStatisticEnergyContainer;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class UnstableContainerBlockEntity extends BlockEntity implements ScreenProvider, ExtendedScreenHandlerFactory, BlockEntityTicker<UnstableContainerBlockEntity>, GeoBlockEntity, MultiblockMachineController, EnergyApi.BlockProvider {
    
    public static final RawAnimation SETUP = RawAnimation.begin().thenPlay("setup").thenPlay("idle");
    public static final RawAnimation IDLE = RawAnimation.begin().thenPlay("idle");
    
    private final ArrayList<BlockPos> coreBlocksConnected = new ArrayList<>();
    
    public BlockState capturedBlock = Blocks.AIR.getDefaultState();
    private boolean networkDirty = false;
    
    //own storage
    protected final DynamicStatisticEnergyContainer energyStorage = new DynamicStatisticEnergyContainer(10_000_000_000L, 100_000_000, 100_000_000, this::markDirty);
    
    private final EnergyApi.EnergyContainer outputStorage = new DelegatingEnergyStorage(energyStorage, null) {
        @Override
        public boolean supportsInsertion() {
            return false;
        }
    };

    protected final AnimatableInstanceCache animatableInstanceCache = GeckoLibUtil.createInstanceCache(this);
    
    // client only
    public DynamicStatisticEnergyContainer.EnergyStatistics currentStats;
    
    public UnstableContainerBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntitiesContent.UNSTABLE_CONTAINER_BLOCK_ENTITY, pos, state);
    }
    
    @Override
    public void tick(World world, BlockPos pos, BlockState state, UnstableContainerBlockEntity blockEntity) {
        if (world.isClient) return;
        
        energyStorage.tick((int) world.getTime());
        
        if (energyStorage.amount > 0)
            outputEnergy();
        
        if (networkDirty) {
            updateNetwork();
        }
    }
    
    private void outputEnergy() {
        var positions = List.of(new Vec3i(0, -3, 0), new Vec3i(0, 2, 0));
        for (var outputPos : positions) {
            var worldPos = pos.add(outputPos);
            var candidate = EnergyApi.BLOCK.find(world, worldPos, null);
            if (candidate != null) {
                EnergyApi.transfer(energyStorage, candidate, energyStorage.maxExtract, false);
            }
        }
    }
    
    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        super.writeNbt(nbt, registryLookup);
        addMultiblockToNbt(nbt);
        var blockId = Registries.BLOCK.getId(capturedBlock.getBlock());
        nbt.putString("captured", blockId.toString());
        nbt.putLong("energy_stored", energyStorage.amount);
    }
    
    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        super.readNbt(nbt, registryLookup);
        loadMultiblockNbtData(nbt);
        energyStorage.amount = nbt.getLong("energy_stored");
        
        var blockId = nbt.getString("captured");
        if (!blockId.isBlank() && Registries.BLOCK.containsId(Identifier.of(blockId)))
            capturedBlock = Registries.BLOCK.get(Identifier.of(blockId)).getDefaultState();
        
    }
    
    private boolean isActivelyViewed() {
        var closestPlayer = Objects.requireNonNull(world).getClosestPlayer(pos.getX(), pos.getY(), pos.getZ(), 15, false);
        return closestPlayer != null && closestPlayer.currentScreenHandler instanceof BasicMachineScreenHandler handler && getPos().equals(handler.getBlockPos());
    }
    
    private void updateNetwork() {
        
        if (world.getTime() % 15 != 0 && !isActivelyViewed()) return;
        
        var statistics = energyStorage.getCurrentStatistics(world.getTime());
        
        networkDirty = false;
        
        NetworkContent.MACHINE_CHANNEL.serverHandle(this).send(new NetworkContent.GenericEnergySyncPacket(pos, energyStorage.amount, energyStorage.capacity));
        NetworkContent.MACHINE_CHANNEL.serverHandle(this).send(new NetworkContent.EnergyStatisticsPacket(pos, statistics));
        
        // sync contained block
        if (world.getTime() % 45 == 0)
            NetworkContent.MACHINE_CHANNEL.serverHandle(this).send(new NetworkContent.UnstableContainerContentPacket(pos, Registries.BLOCK.getId(capturedBlock.getBlock())));
    }
    
    @Override
    public void markDirty() {
        world.markDirty(pos);
        networkDirty = true;
    }
    
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "machine", 0, state -> {
            if (state.getController().getAnimationState().equals(AnimationController.State.STOPPED))
                return state.setAndContinue(SETUP);
            return PlayState.CONTINUE;
        }).setSoundKeyframeHandler(new AutoPlayingSoundKeyframeHandler<>()));
    }
    
    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animatableInstanceCache;
    }
    
    
    @Override
    public List<Vec3i> getCorePositions() {
        return List.of(
          new Vec3i(-1, -2, -1),
          new Vec3i(0, -2, -1),
          new Vec3i(1, -2, -1),
          new Vec3i(-1, -2, 0),
          new Vec3i(0, -2, 0),
          new Vec3i(1, -2, 0),
          new Vec3i(-1, -2, 1),
          new Vec3i(0, -2, 1),
          new Vec3i(1, -2, 1),
          new Vec3i(-1, -1, -1),
          new Vec3i(0, -1, -1),
          new Vec3i(1, -1, -1),
          new Vec3i(-1, -1, 0),
          new Vec3i(0, -1, 0),
          new Vec3i(1, -1, 0),
          new Vec3i(-1, -1, 1),
          new Vec3i(0, -1, 1),
          new Vec3i(1, -1, 1),
          new Vec3i(-1, 0, -1),
          new Vec3i(0, 0, -1),
          new Vec3i(1, 0, -1),
          new Vec3i(-1, 0, 0),
          new Vec3i(1, 0, 0),
          new Vec3i(-1, 0, 1),
          new Vec3i(0, 0, 1),
          new Vec3i(1, 0, 1),
          new Vec3i(0, 1, -1),
          new Vec3i(-1, 1, 0),
          new Vec3i(0, 1, 0),
          new Vec3i(1, 1, 0),
          new Vec3i(0, 1, 1)
        );
    }
    
    @Override
    public Direction getFacingForMultiblock() {
        return Direction.NORTH;
    }
    
    @Override
    public BlockPos getMachinePos() {
        return pos;
    }
    
    @Override
    public World getMachineWorld() {
        return world;
    }
    
    @Override
    public ArrayList<BlockPos> getConnectedCores() {
        return coreBlocksConnected;
    }
    
    @Override
    public void setCoreQuality(float quality) {
    
    }
    
    @Override
    public float getCoreQuality() {
        return 7;
    }
    
    @Override
    public InventoryProvider getInventoryForLink() {
        return null;
    }
    
    @Override
    public EnergyApi.EnergyContainer getEnergyStorageForLink(Direction direction) {
        return getStorage(direction);
    }
    
    @Override
    public void playSetupAnimation() {
    
    }
    
    @Override
    public void onCoreBroken(BlockPos corePos) {
        onBroken(corePos);
    }
    
    @Override
    public void onControllerBroken() {
        onBroken(pos);
    }
    
    private void onBroken(BlockPos eventSource) {
        
        if (eventSource != pos)
            world.breakBlock(pos, true);
        
        for (var corePos : coreBlocksConnected) {
            if (corePos.equals(eventSource)) continue;
            world.setBlockState(corePos, Blocks.AIR.getDefaultState());
        }
        
    }
    
    public void setCapturedBlock(BlockState capturedBlock) {
        this.capturedBlock = capturedBlock;
        markDirty();
    }
    
    @Override
    public EnergyApi.EnergyContainer getStorage(Direction direction) {
        
        if (direction == null) return energyStorage;
        
        if (direction.equals(Direction.DOWN) || direction.equals(Direction.UP))
            return outputStorage;
        
        return energyStorage;
    }
    
    @Override
    public List<GuiSlot> getGuiSlots() {
        return List.of();
    }
    
    @Override
    public float getDisplayedEnergyUsage() {
        return 0;   // todo
    }
    
    @Override
    public float getDisplayedEnergyTransfer() {
        return energyStorage.maxInsert;
    }
    
    @Override
    public float getProgress() {
        return 0;
    }
    
    @Override
    public InventoryInputMode getInventoryInputMode() {
        return InventoryInputMode.FILL_LEFT_TO_RIGHT;
    }
    
    @Override
    public Inventory getDisplayedInventory() {
        return new SimpleInventory();
    }
    
    @Override
    public ScreenHandlerType<?> getScreenHandlerType() {
        return ModScreens.STORAGE_SCREEN;
    }
    
    @Override
    public boolean inputOptionsEnabled() {
        return false;
    }
    
    @Override
    public boolean showProgress() {
        return false;
    }
    
    @Override
    public boolean showExpansionPanel() {
        return false;
    }
    
    @Override
    public Object getScreenOpeningData(ServerPlayerEntity serverPlayerEntity) {
        updateNetwork();
        return new ModScreens.UpgradableData(pos, new MachineAddonController.AddonUiData(List.of(), List.of(), 1f, 1f, pos, 0), getCoreQuality());
    }
    
    @Override
    public Text getDisplayName() {
        return Text.literal("");
    }
    
    @Override
    public @Nullable ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        NetworkContent.MACHINE_CHANNEL.serverHandle(this).send(new NetworkContent.FullEnergySyncPacket(pos, energyStorage.amount, energyStorage.capacity, energyStorage.maxInsert, energyStorage.maxExtract));
        
        return new UpgradableMachineScreenHandler(syncId, playerInventory, this, new MachineAddonController.AddonUiData(List.of(), List.of(), 1f, 1f, pos, 0), getCoreQuality());
    }
}
