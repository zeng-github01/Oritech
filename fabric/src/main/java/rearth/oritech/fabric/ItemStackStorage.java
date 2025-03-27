package rearth.oritech.fabric;

import net.fabricmc.fabric.api.transfer.v1.item.base.SingleStackStorage;
import net.minecraft.item.ItemStack;
import org.apache.commons.lang3.mutable.MutableObject;

public class ItemStackStorage extends SingleStackStorage {
    
    private final MutableObject<ItemStack> stack;
    
    public ItemStackStorage(MutableObject<ItemStack> stack) {
        this.stack = stack;
    }
    
    @Override
    protected ItemStack getStack() {
        return stack.getValue();
    }
    
    @Override
    protected void setStack(ItemStack stack) {
        this.stack.setValue(stack);
    }
}
