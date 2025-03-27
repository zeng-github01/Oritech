package rearth.oritech.util.fluid;

import dev.architectury.fluid.FluidStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class FluidApi {
    
    public static long transferFirst(FluidContainer from, FluidContainer to, long max, boolean simulate) {
        if (from.getContent().isEmpty()) return 0L;
        
        var kind = from.getContent().getFirst();
        return transfer(from, to, kind.copyWithAmount(max), simulate);
    }
    
    public static long transfer(FluidContainer from, FluidContainer to, FluidStack toMove, boolean simulate) {
        var extracted = from.extract(toMove, true);
        var inserted = to.insert(toMove.copyWithAmount(extracted), simulate);
        extracted = from.extract(toMove.copyWithAmount(inserted), simulate);
        
        if (extracted > 0 && !simulate) {
            from.update();
            to.update();
        }
        
        return extracted;
    }
    
    public static BlockFluidApi BLOCK;
    public static ItemFluidApi ITEM;
    
    public static abstract class FluidContainer {
        
        public boolean supportsInsertion() {
            return true;
        }
        
        public abstract long insert(FluidStack toInsert, boolean simulate);
        
        public boolean supportsExtraction() {
            return true;
        }
        
        public abstract long extract(FluidStack toExtract, boolean simulate);
        
        public abstract List<FluidStack> getContent();
        
        public abstract void update();
        
    }
    
    public interface ItemApiProvider {
        SingleSlotContainer getFluidStorage(ItemStack stack);
    }
    
    public interface FluidApiProvider {
        
        FluidContainer getFluidStorage(@Nullable Direction direction);
        
    }
    
    // used for things like tanks, etc.
    public static abstract class SingleSlotContainer extends FluidContainer {
        
        public abstract void setStack(FluidStack stack);
        public abstract FluidStack getStack();
        public abstract long getCapacity();
        
    }
    
    // used for things like the centrifuge or steam engine
    public static abstract class InOutSlotContainer extends FluidContainer {
        
        public abstract void setInStack(FluidStack stack);
        public abstract FluidStack getInStack();
        
        public abstract void setOutStack(FluidStack stack);
        public abstract FluidStack getOutStack();
        public abstract long getCapacity();
        public abstract FluidContainer getContainerForDirection(Direction direction);
        
    }
}
