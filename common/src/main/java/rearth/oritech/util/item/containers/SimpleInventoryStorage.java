package rearth.oritech.util.item.containers;

import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import rearth.oritech.util.item.ItemApi;

public class SimpleInventoryStorage extends SimpleInventory implements ItemApi.InventoryStorage {
    
    private final Runnable onUpdate;
    
    public SimpleInventoryStorage(int size, Runnable onUpdate) {
        super(size);
        this.onUpdate = onUpdate;
    }
    
    @Override
    public int insert(ItemStack inserted, boolean simulate) {
        var remaining = inserted.getCount();
        for (var slot = 0; slot < size() && remaining > 0; slot++) {
            remaining -= insertToSlot(inserted.copyWithCount(remaining), slot, simulate);
        }
        
        return inserted.getCount() - remaining;
    }
    
    @Override
    public int insertToSlot(ItemStack inserted, int slot, boolean simulate) {
        var slotStack = getStack(slot);
        var slotLimit = getSlotLimit(slot);
        
        if (slotStack.isEmpty()) {
            var toInsert = Math.min(slotLimit, inserted.getCount());
            if (!simulate) setStack(slot, inserted.copyWithCount(toInsert));
            return toInsert;
        }
        
        if (ItemStack.areItemsAndComponentsEqual(slotStack, inserted)) {
            var available = slotLimit - slotStack.getCount();
            var toInsert = Math.min(available, inserted.getCount());
            if (toInsert > 0) {
                if (!simulate) slotStack.increment(toInsert);
                return toInsert;
            }
        }
        
        return 0;
    }
    
    @Override
    public int extract(ItemStack extracted, boolean simulate) {
        var remaining = extracted.getCount();
        for (var slot = 0; slot < size() && remaining > 0; slot++) {
            remaining -= extractFromSlot(extracted.copyWithCount(remaining), slot, simulate);
        }
        return extracted.getCount() - remaining;
    }
    
    @Override
    public int extractFromSlot(ItemStack extracted, int slot, boolean simulate) {
        var slotStack = getStack(slot);
        if (slotStack.isEmpty() || !ItemStack.areItemsAndComponentsEqual(slotStack, extracted))
            return 0;
        
        var toExtract = Math.min(slotStack.getCount(), extracted.getCount());
        if (!simulate) slotStack.decrement(toExtract);
        return toExtract;
    }
    
    @Override
    public void setStackInSlot(int slot, ItemStack stack) {
        this.setStack(slot, stack);
    }
    
    @Override
    public ItemStack getStackInSlot(int slot) {
        return this.getStack(slot);
    }
    
    @Override
    public int getSlotCount() {
        return this.size();
    }
    
    @Override
    public int getSlotLimit(int slot) {
        return 64;
    }
    
    @Override
    public void update() {
        onUpdate.run();
        this.markDirty();
    }
}
