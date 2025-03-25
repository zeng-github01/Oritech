package rearth.oritech.util.fluid;

import dev.architectury.fluid.FluidStack;
import net.minecraft.util.math.Direction;

import java.util.List;

public class FluidApi {
    
    public static BlockFluidApi BLOCK;
    
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
