package rearth.oritech.util.fluid;

import dev.architectury.fluid.FluidStack;
import net.minecraft.component.ComponentType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.apache.commons.lang3.mutable.MutableObject;
import rearth.oritech.init.ComponentContent;

import java.util.function.Supplier;

public interface ItemFluidApi {
    
    void registerForItem(Supplier<Item> itemSupplier);
    
    FluidApi.FluidContainer find(MutableObject<ItemStack> stack);
    
    default ComponentType<FluidStack> getFluidComponent() {
        return ComponentContent.STORED_FLUID.get();
    }
}
