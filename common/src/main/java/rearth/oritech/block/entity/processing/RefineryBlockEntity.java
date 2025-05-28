package rearth.oritech.block.entity.processing;

import dev.architectury.fluid.FluidStack;
import dev.architectury.hooks.fluid.FluidStackHooks;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.text.Text;
import net.minecraft.util.Pair;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import rearth.oritech.Oritech;
import rearth.oritech.api.fluid.FluidApi;
import rearth.oritech.api.fluid.containers.SimpleFluidStorage;
import rearth.oritech.api.fluid.containers.SimpleInOutFluidStorage;
import rearth.oritech.block.base.entity.MachineBlockEntity;
import rearth.oritech.block.base.entity.MultiblockMachineEntity;
import rearth.oritech.client.init.ModScreens;
import rearth.oritech.client.init.ParticleContent;
import rearth.oritech.client.ui.CentrifugeScreenHandler;
import rearth.oritech.client.ui.RefineryScreenHandler;
import rearth.oritech.init.BlockEntitiesContent;
import rearth.oritech.init.FluidContent;
import rearth.oritech.init.recipes.OritechRecipe;
import rearth.oritech.init.recipes.OritechRecipeType;
import rearth.oritech.init.recipes.RecipeContent;
import rearth.oritech.util.Geometry;
import rearth.oritech.util.InventorySlotAssignment;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

// todo possibly decouple this from multiblockmachineentity?
public class RefineryBlockEntity extends MultiblockMachineEntity implements FluidApi.BlockProvider {
    
    // todo persistence, networking for those
    // own storage is exposed through this multiblock, the other storages are exposed through the respective modules
    public final SimpleInOutFluidStorage ownStorage = new SimpleInOutFluidStorage(16 * FluidStackHooks.bucketAmount(), this::markDirty);
    public final FluidApi.SingleSlotStorage nodeA = new SimpleFluidStorage(16 * FluidStackHooks.bucketAmount(), this::markDirty);
    public final FluidApi.SingleSlotStorage nodeB = new SimpleFluidStorage(16 * FluidStackHooks.bucketAmount(), this::markDirty);
    
    private int moduleCount;    // range 0-2
    
    public RefineryBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntitiesContent.REFINERY_ENTITY, pos, state, Oritech.CONFIG.processingMachines.fragmentForgeData.energyPerTick());
    }
    
    @Override
    public void tick(World world, BlockPos pos, BlockState state, MachineBlockEntity blockEntity) {
        super.tick(world, pos, state, blockEntity);
        
        if (world.isClient) {
            ownStorage.setStack(0, FluidStack.create(Fluids.FLOWING_WATER, 4 * FluidStackHooks.bucketAmount()));
            ownStorage.setStack(1, FluidStack.create(Fluids.WATER, 6 * FluidStackHooks.bucketAmount()));
            nodeA.setStack(FluidStack.create(Fluids.FLOWING_LAVA, 6 * FluidStackHooks.bucketAmount()));
            nodeB.setStack(FluidStack.create(FluidContent.FLOWING_FUEL.get(), 6 * FluidStackHooks.bucketAmount()));
            return;
        }
        
        if (world.getTime() % 25 == 0) {
            refreshModules();
        }
    }
    
    private void refreshModules() {
        moduleCount = 0;
        var startPos = pos.up(2);
        
        for (int i = 0; i <= 1; i++) {
            var candidatePos = startPos.add(0, i, 0);
            var candidate = world.getBlockEntity(candidatePos, BlockEntitiesContent.REFINERY_MODULE_ENTITY);
            if (candidate.isEmpty() || !candidate.get().isActive(candidate.get().getCachedState())) break;
            
            moduleCount++;
            candidate.get().setOwningRefinery(this);
        }
    }
    
    public int getModuleCount() {
        return moduleCount;
    }
    
    @Override
    protected Optional<RecipeEntry<OritechRecipe>> getRecipe() {
        
        // get recipes matching input items
        var candidates = Objects.requireNonNull(world).getRecipeManager().getAllMatches(getOwnRecipeType(), getInputInventory(), world);
        // filter out recipes based on input tank
        var fluidRecipe = candidates.stream().filter(candidate -> CentrifugeBlockEntity.recipeInputMatchesTank(ownStorage.getInputContainer().getStack(), candidate.value())).findAny();
        if (fluidRecipe.isPresent()) {
            return fluidRecipe;
        }
        
        return Optional.empty();
    }
    
    @Override
    protected void craftItem(OritechRecipe activeRecipe, List<ItemStack> outputInventory, List<ItemStack> inputInventory) {
        super.craftItem(activeRecipe, outputInventory, inputInventory);
        craftFluids(activeRecipe);
    }
    
    private void craftFluids(OritechRecipe activeRecipe) {
        // create outputs, remove inputs
        
        // remove input fluid
        ownStorage.getInputContainer().extract(ownStorage.getInputContainer().getStack().copyWithAmount(activeRecipe.getFluidInput().amount()), false);
        
        // create output fluids
        var outputs = calculateOutputFluids(activeRecipe);
        for (int i = 0; i < outputs.size(); i++) {
            var output = outputs.get(i);
            var outputTank = getOutputStorage(i);
            outputTank.insert(output, false);
        }
        
    }
    
    private List<FluidStack> calculateOutputFluids(OritechRecipe recipe) {
        // if no modules are installed, output twice the resulting items and fluids
        // if one module is installed, output twice the output A
        // if both are installed, output all as normal
        // todo add gui tooltip for this behaviour
        
        var outA = recipe.getFluidOutputs().get(0);
        var outB = recipe.getFluidOutputs().get(1);
        
        return switch (moduleCount) {
            case 0 -> List.of(outA.copyWithAmount(outA.getAmount() * 2));
            case 1 -> List.of(outA, outB.copyWithAmount(outB.getAmount() * 2));
            case 2 -> recipe.getFluidOutputs();
            default -> throw new IllegalStateException("more than 2 modules is not supported/allowed");
        };
    }
    
    private boolean getItemOutputMultiplier() {
        return getModuleCount() == 0;
    }
    
    @Override
    public boolean canOutputRecipe(OritechRecipe recipe) {
        
        // 0 = base output, 1&2 = module outputs
        // checks if all fluid outputs for the active modules fit
        var fluidOutputs = calculateOutputFluids(recipe);
        for (int i = 0; i <= moduleCount; i++) {
            if (i >= fluidOutputs.size()) break;
            var fluidOutput = fluidOutputs.get(i);
            if (fluidOutput == null || fluidOutput.isEmpty()) continue;
            var storage = getOutputStorage(i);
            var inserted = storage.insert(fluidOutput, true);
            if (inserted != fluidOutput.getAmount()) return false;
        }
        
        return super.canOutputRecipe(recipe);
    }
    
    @Nullable
    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        return new RefineryScreenHandler(syncId, playerInventory, this, getUiData(), getCoreQuality());
    }
    
    @Override
    public BarConfiguration getFluidConfiguration() {
        return new BarConfiguration(28, 6, 21, 74);
    }
    
    @Override
    public long getDefaultCapacity() {
        return Oritech.CONFIG.processingMachines.fragmentForgeData.energyCapacity();
    }
    
    @Override
    public long getDefaultInsertRate() {
        return Oritech.CONFIG.processingMachines.fragmentForgeData.maxEnergyInsertion();
    }
    
    @Override
    protected OritechRecipeType getOwnRecipeType() {
        return RecipeContent.REFINERY;
    }
    
    @Override
    protected void useEnergy() {
        super.useEnergy();
        
        if (world.random.nextFloat() > 0.8) return;
        // emit particles
        var facing = getFacing();
        var offsetLocal = Geometry.rotatePosition(new Vec3d(0.4, 0.6, 0.5), facing);
        var emitPosition = Vec3d.ofCenter(pos).add(offsetLocal);
        
        // todo
        ParticleContent.GRINDER_WORKING.spawn(world, emitPosition, 1);
        
    }
    
    @Override
    public InventorySlotAssignment getSlotAssignments() {
        return new InventorySlotAssignment(0, 1, 1, 1);
    }
    
    @Override
    public List<GuiSlot> getGuiSlots() {
        return List.of(
          new GuiSlot(0, 62, 8),
          new GuiSlot(1, 62, 61, true));
    }
    
    @Override
    public ScreenHandlerType<?> getScreenHandlerType() {
        return ModScreens.REFINERY_SCREEN;
    }
    
    @Override
    public int getInventorySize() {
        return 2;
    }
    
    @Override
    public boolean inputOptionsEnabled() {
        return false;
    }
    
    // x = back
    // y = up
    // z = left
    
    @Override
    public List<Vec3i> getCorePositions() {
        return List.of(
          new Vec3i(0, 1, 0),    // middle
          new Vec3i(0, 0, -1),    // right
          new Vec3i(0, 1, -1),
          new Vec3i(1, 0, -1),    // back right
          new Vec3i(1, 1, -1),
          new Vec3i(1, 0, 0),    // back middle
          new Vec3i(1, 1, 0),
          new Vec3i(2, 0, -1),    // backer middle
          new Vec3i(2, 1, -1)
        );
    }
    
    // x = back, // z = left
    @Override
    public List<Vec3i> getAddonSlots() {
        return List.of();
    }
    
    @Override
    public FluidApi.FluidStorage getFluidStorage(@Nullable Direction direction) {
        return ownStorage;
    }
    
    public FluidApi.FluidStorage getFluidStorageForModule(BlockPos modulePos) {
        var yDist = modulePos.getY() - this.pos.getY();
        if (yDist == 2) return nodeA;
        if (yDist == 3) return nodeB;
        throw new IllegalStateException("Module needs to be either 1 or 2 blocks above");
    }
    
    @Override
    public List<Pair<Text, Text>> getExtraExtensionLabels() {
        return List.of(new Pair<>(Text.literal("\uD83D\uDCE6: " + moduleCount), Text.translatable("tooltip.oritech.refinery_module_count")));
    }
    
    private FluidApi.SingleSlotStorage getOutputStorage(int i) {
        if (i == 0) return ownStorage.getOutputContainer();
        if (i == 1) return nodeA;
        if (i == 2) return nodeB;
        throw new IllegalArgumentException("Only has 2 storage modules, tried accessing: " + i);
    }
}
    

