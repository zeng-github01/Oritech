package rearth.oritech.util.fluid;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import rearth.oritech.util.energy.EnergyApi;

import java.util.function.Supplier;

public interface BlockFluidApi {
    
    void registerBlockEntity(Supplier<BlockEntityType<?>> typeSupplier);
    
    FluidApi.FluidContainer find(World world, BlockPos pos, @Nullable BlockState state, @Nullable BlockEntity entity, @Nullable Direction direction);
    
    FluidApi.FluidContainer find(World world, BlockPos pos, @Nullable Direction direction);
}
