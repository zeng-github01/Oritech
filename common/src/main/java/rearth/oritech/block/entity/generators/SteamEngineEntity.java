package rearth.oritech.block.entity.generators;

import dev.architectury.fluid.FluidStack;
import net.minecraft.block.BlockState;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.Fluids;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.util.Pair;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import rearth.oritech.Oritech;
import rearth.oritech.block.base.block.MultiblockMachine;
import rearth.oritech.block.base.entity.FluidMultiblockGeneratorBlockEntity;
import rearth.oritech.block.base.entity.MachineBlockEntity;
import rearth.oritech.block.base.entity.MultiblockGeneratorBlockEntity;
import rearth.oritech.block.blocks.processing.MachineCoreBlock;
import rearth.oritech.client.init.ModScreens;
import rearth.oritech.client.init.ParticleContent;
import rearth.oritech.init.BlockContent;
import rearth.oritech.init.BlockEntitiesContent;
import rearth.oritech.init.FluidContent;
import rearth.oritech.init.recipes.OritechRecipe;
import rearth.oritech.init.recipes.OritechRecipeType;
import rearth.oritech.init.recipes.RecipeContent;
import rearth.oritech.network.NetworkContent;
import rearth.oritech.util.Geometry;
import rearth.oritech.util.InventorySlotAssignment;
import rearth.oritech.util.fluid.FluidApi;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// progress is abused to sync active speed.
public class SteamEngineEntity extends MultiblockGeneratorBlockEntity implements FluidApi.FluidApiProvider {
    
    private static final int MAX_SPEED = 10;
    
    private final ArrayList<SteamEngineEntity> connectedTanks = new ArrayList<>();
    private SteamEngineEntity cachedTargetTank = null;
    
    public int energyProducedTick = 0;
    
    public SteamEngineEntity(BlockPos pos, BlockState state) {
        super(BlockEntitiesContent.STEAM_ENGINE_ENTITY, pos, state, Oritech.CONFIG.generators.steamEngineData.steamToRfRatio());
    }
    
    @Override
    public void initAddons() {
        setupTankCache();
        cachedTargetTank = reloadTargetTankFromCache();
    }
    
    @Override
    public boolean initMultiblock(BlockState state) {
        setupTankCache();
        return super.initMultiblock(state);
    }
    
    @Override
    public boolean boilerAcceptsInput(Fluid fluid) {
        return fluid.equals(FluidContent.STILL_STEAM.get());
    }
    
    @Override
    public void tick(World world, BlockPos pos, BlockState state, MachineBlockEntity blockEntity) {
        
        if (world.isClient || !isActive(state)) return;
        outputEnergy();
        
        progress = 0;
        var usedTankEntity = cachedTargetTank;
        
        if (usedTankEntity == null) {
            if (world.getTime() % 40 == 0) {
                setupTankCache();
                cachedTargetTank = reloadTargetTankFromCache();
            }
            return;
        }
        
        // todo fix networking for extra stats
        // todo improve chaining behaviour
        
        var usedSteamTank = usedTankEntity.boilerStorage.getInputContainer();
        var usedWaterTank = usedTankEntity.boilerStorage.getOutputContainer();
        var usedEnergyStorage = usedTankEntity.energyStorage;
        
        // optional config stops
        if (usedEnergyStorage.getAmount() >= usedEnergyStorage.getCapacity() && Oritech.CONFIG.generators.steamEngineData.stopOnEnergyFull()) return;
        if (usedWaterTank.getStack().getAmount() >= usedWaterTank.getCapacity() && Oritech.CONFIG.generators.steamEngineData.stopOnWaterFull()) return;
        
        // stop on no steam
        if (usedSteamTank.getStack().getAmount() <= 0) return;
        
        if (currentRecipe == OritechRecipe.DUMMY) {
            var candidate = FluidMultiblockGeneratorBlockEntity.getRecipe(usedSteamTank, world, getOwnRecipeType());
            if (candidate.isEmpty()) return;
            var recipe = candidate.get().value();
            if (!usedSteamTank.getStack().isFluidEqual(recipe.getFluidInput())) return;
            currentRecipe = recipe;
        }
        
        var speed = getSteamProcessingSpeed(usedSteamTank);
        
        var consumed = Math.max(1, currentRecipe.getFluidInput().getAmount() * speed);
        usedSteamTank.extract(currentRecipe.getFluidInput().copyWithAmount((long) consumed), false);
        usedWaterTank.insert(FluidStack.create(Fluids.WATER, (long) (consumed * 0.9f)), false);
        progress = (int) (speed * 100);
        
        var energyEfficiency = getSteamEnergyEfficiency(speed);
        var energyProduced = consumed * energyEfficiency * energyPerTick;
        usedEnergyStorage.amount = (long) Math.min(usedEnergyStorage.amount + energyProduced, usedEnergyStorage.capacity);
        usedTankEntity.energyProducedTick += (int) energyProduced;
        
        setBaseAddonData(new BaseAddonData(1 / (speed), 1 / energyEfficiency, 0, 0, 0));
        
        spawnParticles();
        lastWorkedAt = world.getTime();
        
        markDirty();
        
        if (networkDirty) {
            updateNetwork();
        }
    }
    
    private void spawnParticles() {
        if (world.random.nextFloat() > 0.4) return;
        // emit particles
        var facing = getFacing();
        var offsetLocal = Geometry.rotatePosition(new Vec3d(0, 0, -0.5), facing);
        var emitPosition = Vec3d.ofCenter(pos).add(offsetLocal);
        
        ParticleContent.STEAM_ENGINE_WORKING.spawn(world, emitPosition, 1);
    }
    
    private float getSteamEnergyEfficiency(float x) {
        // basically a curve that goes through 0:0.5, 7:1 and 10:0.2
        return (float) (0.5f - 0.1966667f * x + 0.09166667f * Math.pow(x, 2) - 0.0075f * Math.pow(x, 3)) + 0.4f;
    }
    
    private void setupTankCache() {
        connectedTanks.clear();
        var facing = getFacing();
        
        // collect tanks (in both directions)
        for (int i = 1; i <= 10; i++) {
            if (!tryAddTank(facing, i)) break;
        }
        
        for (int i = 1; i <= 10; i++) {
            if (!tryAddTank(facing, -i)) break;
        }
    }
    
    private boolean tryAddTank(Direction facing, int i) {
        var checkPos = new BlockPos(Geometry.offsetToWorldPosition(facing, new Vec3i(i, 0, 0), pos));
        var state = world.getBlockState(checkPos);
        
        // redirect in case of machine core
        if (state.getBlock() instanceof MachineCoreBlock) {
            checkPos = MachineCoreBlock.getControllerPos(world, checkPos);
            state = world.getBlockState(checkPos);
        }
        
        if (state.getBlock().equals(BlockContent.STEAM_ENGINE_BLOCK) && state.get(MultiblockMachine.ASSEMBLED)) {
            connectedTanks.add(((SteamEngineEntity) world.getBlockEntity(new BlockPos(checkPos))));
        } else {
            return false;
        }
        return true;
    }
    
    private SteamEngineEntity reloadTargetTankFromCache() {
        var res = getBestInputFromConnectedEngine();
        if (res.boilerStorage.getInStack().getAmount() == 0) return null;
        return res;
    }
    
    private SteamEngineEntity getBestInputFromConnectedEngine() {
        
        var res = this;
        var highest = boilerStorage.getInStack().getAmount();
        
        for (var tank : connectedTanks) {
            
            if (tank == null) {
                connectedTanks.clear();
                connectedTanks.add(this);
                return this;
            }
            
            var tankAmount = tank.boilerStorage.getInStack().getAmount();
            if (tankAmount > highest) {
                highest = tankAmount;
                res = tank;
            }
        }
        
        return res;
    }
    
    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        super.readNbt(nbt, registryLookup);
    }
    
    private float getSteamProcessingSpeed(FluidApi.SingleSlotContainer usedTank) {
        var fillPercentage = usedTank.getStack().getAmount() / (float) usedTank.getCapacity();
        return fillPercentage * MAX_SPEED;
    }
    
    @Override
    protected float getAnimationSpeed() {
        if (progress == 0) return 1;
        return (float) progress / 100;
    }
    
    @Override
    public BarConfiguration getFluidConfiguration() {
        return new BarConfiguration(149, 10, 18, 64);
    }
    
    @Override
    protected void sendNetworkEntry() {
        super.sendNetworkEntry();
        NetworkContent.MACHINE_CHANNEL.serverHandle(this).send(new NetworkContent.GeneratorSteamSyncPacket(pos, boilerStorage.getInStack().getAmount(), boilerStorage.getOutStack().getAmount()));
        energyProducedTick = 0;
    }
    
    @Override
    protected OritechRecipeType getOwnRecipeType() {
        return RecipeContent.STEAM_ENGINE;
    }
    
    @Override
    public InventorySlotAssignment getSlots() {
        return new InventorySlotAssignment(0, 0, 0, 0);
    }
    
    @Override
    public List<GuiSlot> getGuiSlots() {
        return List.of();
    }
    
    @Override
    public ScreenHandlerType<?> getScreenHandlerType() {
        return ModScreens.STEAM_ENGINE_SCREEN;
    }
    
    @Override
    public int getInventorySize() {
        return 0;
    }
    
    @Override
    public long getDefaultCapacity() {
        return Oritech.CONFIG.generators.steamEngineData.energyCapacity();
    }
    
    @Override
    public long getDefaultExtractionRate() {
        return Oritech.CONFIG.generators.steamEngineData.maxEnergyExtraction();
    }
    
    @Override
    protected Set<Pair<BlockPos, Direction>> getOutputTargets(BlockPos pos, World world) {
        
        var res = new HashSet<Pair<BlockPos, Direction>>();
        
        var facing = getFacingForAddon();
        var posA = new Vec3i(0, 0, 1); // front
        var posB = new Vec3i(-1, 0, 0); // right
        var posC = new Vec3i(1, 0, 0);  // left
        var posD = new Vec3i(-1, 0, -1); // back left
        var posE = new Vec3i(1, 0, -1); // back right
        var posF = new Vec3i(0, 0, -2);  // back
        var worldPosA = (BlockPos) Geometry.offsetToWorldPosition(facing, posA, pos);
        var worldPosB = (BlockPos) Geometry.offsetToWorldPosition(facing, posB, pos);
        var worldPosC = (BlockPos) Geometry.offsetToWorldPosition(facing, posC, pos);
        var worldPosD = (BlockPos) Geometry.offsetToWorldPosition(facing, posD, pos);
        var worldPosE = (BlockPos) Geometry.offsetToWorldPosition(facing, posE, pos);
        var worldPosF = (BlockPos) Geometry.offsetToWorldPosition(facing, posF, pos);
        
        res.add(new Pair<>(worldPosA, Geometry.fromVector(Geometry.getForward(facing))));
        res.add(new Pair<>(worldPosB, Geometry.fromVector(Geometry.getLeft(facing))));
        res.add(new Pair<>(worldPosC, Geometry.fromVector(Geometry.getRight(facing))));
        res.add(new Pair<>(worldPosD, Geometry.fromVector(Geometry.getLeft(facing))));
        res.add(new Pair<>(worldPosE, Geometry.fromVector(Geometry.getRight(facing))));
        res.add(new Pair<>(worldPosF, Geometry.fromVector(Geometry.getBackward(facing))));
        
        return res;
        
    }
    
    @Override
    public List<Vec3i> getAddonSlots() {
        return List.of();
    }
    
    @Override
    public List<Vec3i> getCorePositions() {
        return List.of(
          new Vec3i(0, 1, 0),
          new Vec3i(0, 0, -1),
          new Vec3i(0, 1, -1)
        );
    }
    
    @Override
    public boolean showProgress() {
        return false;
    }
    
    @Override
    public FluidApi.FluidContainer getFluidStorage(@Nullable Direction direction) {
        return boilerStorage.getContainerForDirection(direction);
    }
}
