package rearth.oritech.util.fluid;

import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.Nullable;

public interface FluidApiProvider {
    
    FluidApi.FluidContainer getFluidStorage(@Nullable Direction direction);
    
}
