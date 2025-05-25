package rearth.oritech.block.entity.processing;

import dev.architectury.fluid.FluidStack;
import dev.architectury.hooks.fluid.FluidStackHooks;
import net.minecraft.block.BlockState;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;
import org.jetbrains.annotations.Nullable;
import rearth.oritech.Oritech;
import rearth.oritech.api.fluid.FluidApi;
import rearth.oritech.api.fluid.containers.SimpleFluidStorage;
import rearth.oritech.api.fluid.containers.SimpleInOutFluidStorage;
import rearth.oritech.block.base.entity.MultiblockMachineEntity;
import rearth.oritech.client.init.ModScreens;
import rearth.oritech.client.init.ParticleContent;
import rearth.oritech.init.BlockEntitiesContent;
import rearth.oritech.init.recipes.OritechRecipeType;
import rearth.oritech.init.recipes.RecipeContent;
import rearth.oritech.util.Geometry;
import rearth.oritech.util.InventorySlotAssignment;

import java.util.List;

public class RefineryBlockEntity extends MultiblockMachineEntity implements FluidApi.BlockProvider {
    
    // todo persistence, networking for those
    private final SimpleInOutFluidStorage ownStorage = new SimpleInOutFluidStorage(16 * FluidStackHooks.bucketAmount(), this::markDirty);
    private final FluidApi.SingleSlotStorage nodeA = new SimpleFluidStorage(16 * FluidStackHooks.bucketAmount(), this::markDirty);
    private final FluidApi.SingleSlotStorage nodeB = new SimpleFluidStorage(16 * FluidStackHooks.bucketAmount(), this::markDirty);
    
    public RefineryBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntitiesContent.REFINERY_ENTITY, pos, state, Oritech.CONFIG.processingMachines.fragmentForgeData.energyPerTick());
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
          new GuiSlot(0, 56, 38),
          new GuiSlot(1, 117, 20, true));
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
          new Vec3i(0, 1,0),    // middle
          new Vec3i(0, 0,-1),    // right
          new Vec3i(0, 1,-1),
          new Vec3i(1, 0,-1),    // back right
          new Vec3i(1, 1,-1),
          new Vec3i(1, 0, 0),    // back middle
          new Vec3i(1, 1,0),
          new Vec3i(2, 0, -1),    // backer middle
          new Vec3i(2, 1,-1)
        );
    }
    
    // x = back, // z = left
    @Override
    public List<Vec3i> getAddonSlots() {
        return List.of(
        );
    }
    
    @Override
    public FluidApi.FluidStorage getFluidStorage(@Nullable Direction direction) {
        return null;
    }
    }
    
}
