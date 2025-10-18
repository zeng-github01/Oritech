package rearth.oritech.block.entity.addons;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import rearth.oritech.util.MachineAddonController;

public class CombiAddonEntity extends AddonBlockEntity {
    
    public MachineAddonController.BaseAddonData ownData;
    public boolean fluidAddon;
    
    public CombiAddonEntity(BlockPos pos, BlockState state) {
        super(pos, state);
    }
    
    @Override
    protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider registryLookup) {
        super.saveAdditional(nbt, registryLookup);
    }
    
    @Override
    protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider registryLookup) {
        super.loadAdditional(nbt, registryLookup);
    }
    
    @Override
    public void saveToItem(ItemStack stack, HolderLookup.Provider registries) {
        super.saveToItem(stack, registries);
    }
}
