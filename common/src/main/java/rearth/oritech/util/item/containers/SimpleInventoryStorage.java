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
            var slotStack = getStack(slot);
            var slotLimit = getSlotLimit(slot);
            
            if (slotStack.isEmpty()) {
                var toInsert = Math.min(slotLimit, remaining);
                if (!simulate) setStack(slot, inserted.copyWithCount(toInsert));
                remaining -= toInsert;
            } else if (ItemStack.areItemsAndComponentsEqual(slotStack, inserted)) {
                var available = slotLimit - slotStack.getCount();
                var toInsert = Math.min(available, remaining);
                if (toInsert > 0) {
                    if (!simulate) slotStack.increment(toInsert);
                    remaining -= toInsert;
                }
            }
        }
        
        return inserted.getCount() - remaining;
    }
    
    @Override
    public ItemStack insertToSlot(ItemStack inserted, int slot, boolean simulate) {
        var slotStack = getStack(slot);
        var slotLimit = getSlotLimit(slot);
        
        if (slotStack.isEmpty()) {
            var toInsert = Math.min(slotLimit, inserted.getCount());
            if (!simulate) setStack(slot, inserted.copyWithCount(toInsert));
            return inserted.copyWithCount(inserted.getCount() - toInsert);
        }
        
        if (ItemStack.areItemsAndComponentsEqual(slotStack, inserted)) {
            var available = slotLimit - slotStack.getCount();
            var toInsert = Math.min(available, inserted.getCount());
            if (toInsert > 0) {
                if (!simulate) slotStack.increment(toInsert);
                return inserted.copyWithCount(inserted.getCount() - toInsert);
            }
        }
        
        return inserted;
    }
    
    @Override
    public int extract(ItemStack extracted, boolean simulate) {
        var remaining = extracted.getCount();
        for (var slot = 0; slot < size() && remaining > 0; slot++) {
            var slotStack = getStack(slot);
            if (slotStack.isEmpty() || !ItemStack.areItemsAndComponentsEqual(slotStack, extracted)) continue;
            
            var toExtract = Math.min(slotStack.getCount(), remaining);
            if (!simulate) slotStack.decrement(toExtract);
            remaining -= toExtract;
        }
        return extracted.getCount() - remaining;
    }
    
    @Override
    public ItemStack extractFromSlot(ItemStack extracted, int slot, boolean simulate) {
        var slotStack = getStack(slot);
        if (slotStack.isEmpty() || !ItemStack.areItemsAndComponentsEqual(slotStack, extracted)) {
            return ItemStack.EMPTY;
        }
        
        var toExtract = Math.min(slotStack.getCount(), extracted.getCount());
        var result = slotStack.copyWithCount(toExtract);
        if (!simulate) slotStack.decrement(toExtract);
        return result;
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
    }
}
