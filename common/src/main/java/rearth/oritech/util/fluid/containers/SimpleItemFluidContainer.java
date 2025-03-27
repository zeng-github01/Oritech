package rearth.oritech.util.fluid.containers;

import dev.architectury.fluid.FluidStack;
import net.minecraft.item.ItemStack;
import rearth.oritech.util.fluid.FluidApi;

public class SimpleItemFluidContainer extends SimpleFluidContainer {
    
    private final ItemStack itemStack;
    
    public SimpleItemFluidContainer(Long capacity, ItemStack itemStack) {
        super(capacity);
        this.itemStack = itemStack;
        this.setStack(itemStack.getOrDefault(FluidApi.ITEM.getFluidComponent(), FluidStack.empty()));
    }
    
    @Override
    public void update() {
        super.update();
        
        if (this.getStack().isEmpty()) {
            itemStack.remove(FluidApi.ITEM.getFluidComponent());
            return;
        }
        
        itemStack.set(FluidApi.ITEM.getFluidComponent(), this.getStack());
    }
}
