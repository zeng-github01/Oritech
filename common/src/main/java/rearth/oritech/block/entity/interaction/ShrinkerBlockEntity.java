package rearth.oritech.block.entity.interaction;

import dev.architectury.registry.menu.ExtendedMenuProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.Vec3i;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jetbrains.annotations.Nullable;
import rearth.oritech.Oritech;
import rearth.oritech.api.energy.EnergyApi;
import rearth.oritech.api.energy.containers.DynamicEnergyStorage;
import rearth.oritech.api.item.ItemApi;
import rearth.oritech.api.item.containers.SimpleInventoryStorage;
import rearth.oritech.api.networking.NetworkedBlockEntity;
import rearth.oritech.api.networking.SyncField;
import rearth.oritech.api.networking.SyncType;
import rearth.oritech.block.base.entity.MachineBlockEntity;
import rearth.oritech.client.init.ModScreens;
import rearth.oritech.client.ui.UpgradableMachineScreenHandler;
import rearth.oritech.init.BlockEntitiesContent;
import rearth.oritech.util.*;
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

import static rearth.oritech.block.base.block.MultiblockMachine.ASSEMBLED;

public class ShrinkerBlockEntity extends NetworkedBlockEntity implements EnergyApi.BlockProvider, GeoBlockEntity, ExtendedMenuProvider,
                                                                           ScreenProvider, MultiblockMachineController, MachineAddonController {
    
    public static final RawAnimation SHRINK = RawAnimation.begin().thenPlay("work");
    
    protected final AnimatableInstanceCache animatableInstanceCache = GeckoLibUtil.createInstanceCache(this);
    
    @SyncField({SyncType.GUI_TICK, SyncType.GUI_OPEN})
    private final DynamicEnergyStorage energyStorage = new DynamicEnergyStorage(getDefaultCapacity(), getDefaultInsertRate(), 0, this::setChanged);
    
    public final SimpleInventoryStorage inventory = new SimpleInventoryStorage(1, this::setChanged);
    
    // multiblock
    private final ArrayList<BlockPos> coreBlocksConnected = new ArrayList<>();
    
    @SyncField(SyncType.GUI_OPEN)
    private float coreQuality = 1f;
    
    // addon data
    @SyncField(SyncType.GUI_OPEN)
    private final List<BlockPos> connectedAddons = new ArrayList<>();
    @SyncField(SyncType.GUI_OPEN)
    private final List<BlockPos> openSlots = new ArrayList<>();
    @SyncField(SyncType.GUI_OPEN)
    private BaseAddonData addonData = MachineAddonController.DEFAULT_ADDON_DATA;
    
    public ShrinkerBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntitiesContent.SHRINKER_BLOCK_ENTITY, pos, state);
    }
    
    @Override
    public void serverTick(Level world, BlockPos pos, BlockState state, NetworkedBlockEntity blockEntity) {
    
    }
    
    public void doShrink() {
        System.out.println("shrinking");
        triggerAnim("machine", "work");
    }
    
    @Override
    public EnergyApi.EnergyStorage getEnergyStorage(Direction direction) {
        return energyStorage;
    }
    
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "machine", 3, state -> {
            if (state.getController().getAnimationState().equals(AnimationController.State.STOPPED)) {
                var targetAnim = isActive(getBlockState()) ? MachineBlockEntity.IDLE : MachineBlockEntity.PACKAGED;
                state.resetCurrentAnimation();
                return state.setAndContinue(targetAnim);
            } else {
                // playing animation, keep going
                return PlayState.CONTINUE;
            }
        })
                          .triggerableAnim("work", SHRINK)
                          .triggerableAnim("deploy", MachineBlockEntity.SETUP)
                          .setSoundKeyframeHandler(new AutoPlayingSoundKeyframeHandler<>()));
    }
    
    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animatableInstanceCache;
    }
    
    private boolean isActive(BlockState state) {
        return state.getValue(ASSEMBLED);
    }
    
    @Override
    public void saveExtraData(FriendlyByteBuf buf) {
        sendUpdate(SyncType.GUI_OPEN);
        buf.writeBlockPos(worldPosition);
    }
    
    @Override
    public Component getDisplayName() {
        return Component.nullToEmpty("");
    }
    
    @Override
    public @Nullable AbstractContainerMenu createMenu(int i, Inventory inventory, Player player) {
        return new UpgradableMachineScreenHandler(i, inventory, this);
    }
    
    @Override
    public List<BlockPos> getConnectedAddons() {
        return connectedAddons;
    }
    
    @Override
    public List<BlockPos> getOpenAddonSlots() {
        return openSlots;
    }
    
    @Override
    public BlockPos getPosForAddon() {
        return getBlockPos();
    }
    
    @Override
    public Level getWorldForAddon() {
        return getLevel();
    }
    
    @Override
    public Direction getFacingForAddon() {
        return Objects.requireNonNull(level).getBlockState(getBlockPos()).getValue(BlockStateProperties.HORIZONTAL_FACING);
    }
    
    @Override
    public DynamicEnergyStorage getStorageForAddon() {
        return energyStorage;
    }
    
    @Override
    public ItemApi.InventoryStorage getInventoryForAddon() {
        return inventory;
    }
    
    @Override
    public ScreenProvider getScreenProvider() {
        return this;
    }
    
    @Override
    public List<Vec3i> getAddonSlots() {
        return List.of(
          new Vec3i(1, 0, 0)
        );
    }
    
    @Override
    public BaseAddonData getBaseAddonData() {
        return addonData;
    }
    
    @Override
    public void setBaseAddonData(BaseAddonData data) {
        this.addonData = data;
    }
    
    @Override
    public long getDefaultCapacity() {
        return 50_000_000L;
    }
    
    @Override
    public long getDefaultInsertRate() {
        return getDefaultCapacity() / 60;
    }
    
    @Override
    public List<Vec3i> getCorePositions() {
        return List.of(
          new Vec3i(1, 0, -1),
          new Vec3i(0, 0, -1),
          new Vec3i(1, 0, 1),
          new Vec3i(0, 0, 1)
        );
    }
    
    @Override
    public Direction getFacingForMultiblock() {
        return Objects.requireNonNull(level).getBlockState(getBlockPos()).getValue(BlockStateProperties.HORIZONTAL_FACING);
    }
    
    @Override
    public BlockPos getPosForMultiblock() {
        return getBlockPos();
    }
    
    @Override
    public Level getWorldForMultiblock() {
        return getLevel();
    }
    
    @Override
    public ArrayList<BlockPos> getConnectedCores() {
        return coreBlocksConnected;
    }
    
    @Override
    public void setCoreQuality(float quality) {
        this.coreQuality = quality;
    }
    
    @Override
    public float getCoreQuality() {
        return coreQuality;
    }
    
    @Override
    public ItemApi.InventoryStorage getInventoryForMultiblock() {
        return inventory;
    }
    
    @Override
    public EnergyApi.EnergyStorage getEnergyStorageForMultiblock(Direction direction) {
        return energyStorage;
    }
    
    @Override
    public void triggerSetupAnimation() {
        triggerAnim("machine", "deploy");
    }
    
    @Override
    public List<GuiSlot> getGuiSlots() {
        return List.of(new GuiSlot(0, 40, 40, true));
    }
    
    @Override
    public float getDisplayedEnergyUsage() {
        return getDefaultCapacity();
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
    public Container getDisplayedInventory() {
        return inventory;
    }
    
    @Override
    public MenuType<?> getScreenHandlerType() {
        return ModScreens.SHRINKER_SCREEN;
    }
    
    @Override
    public boolean showProgress() {
        return false;
    }
    
    public static void onPlayerUse(ShrinkerPlayerUsePacket packet, Player player, RegistryAccess registryAccess) {
        
        var world = player.level();
        var candidate = world.getBlockEntity(packet.pos(), BlockEntitiesContent.SHRINKER_BLOCK_ENTITY);
        candidate.ifPresent(ShrinkerBlockEntity::doShrink);
        
    }
    
    public record ShrinkerPlayerUsePacket(BlockPos pos) implements CustomPacketPayload {
        
        public static final CustomPacketPayload.Type<ShrinkerPlayerUsePacket> PACKET_ID = new CustomPacketPayload.Type<>(Oritech.id("shrink"));
        
        @Override
        public Type<? extends CustomPacketPayload> type() {
            return PACKET_ID;
        }
    }
}
