package rearth.oritech.neoforge;

import dev.architectury.fluid.FluidStack;
import dev.architectury.hooks.fluid.forge.FluidStackHooksForge;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import rearth.oritech.Oritech;
import rearth.oritech.util.fluid.BlockFluidApi;
import rearth.oritech.util.fluid.FluidApi;
import rearth.oritech.util.fluid.FluidApiProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class NeoforgeFluidApiImpl implements BlockFluidApi {
    
    private final List<Supplier<BlockEntityType<?>>> registeredBlockEntities = new ArrayList<>();
    
    @Override
    public void registerBlockEntity(Supplier<BlockEntityType<?>> typeSupplier) {
        registeredBlockEntities.add(typeSupplier);
    }
    
    public void registerEvent(RegisterCapabilitiesEvent event) {
        for (var supplied : registeredBlockEntities) {
            event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, supplied.get(), (entity, direction) -> {
                
                var storage = ((FluidApiProvider) entity).getFluidStorage(direction);
                if (storage instanceof FluidApi.InOutSlotContainer inOutContainer) {
                    return InOutContainerStorageWrapper.of(inOutContainer);
                } else if (storage instanceof FluidApi.SingleSlotContainer singleContainer) {
                    return SingleSlotContainerStorageWrapper.of(singleContainer);
                }
                
                Oritech.LOGGER.error("Error during fluid provider registration, unable to register a fluid container");
                Oritech.LOGGER.error("Erroring container type is: {}", supplied.get());
                
                return null;
            });
        }
    }
    
    @Override
    public FluidApi.FluidContainer find(World world, BlockPos pos, @Nullable BlockState state, @Nullable BlockEntity entity, @Nullable Direction direction) {
        var candidate = world.getCapability(Capabilities.FluidHandler.BLOCK, pos, state, entity, direction);
        return switch (candidate) {
            case null -> null;
            case SingleSlotContainerStorageWrapper wrapper -> wrapper.container;
            case InOutContainerStorageWrapper wrapper -> wrapper.container.getContainerForDirection(direction);
            default -> new NeoforgeStorageWrapper(candidate);
        };
    }
    
    @Override
    public FluidApi.FluidContainer find(World world, BlockPos pos, @Nullable Direction direction) {
        return find(world, pos, null, null, direction);
    }
    
    // used to interact with tanks from other mods
    public static class NeoforgeStorageWrapper extends FluidApi.FluidContainer {
        
        private final IFluidHandler storage;
        
        public NeoforgeStorageWrapper(IFluidHandler storage) {
            this.storage = storage;
        }
        
        @Override
        public long insert(FluidStack toInsert, boolean simulate) {
            var action = simulate ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE;
            return storage.fill(FluidStackHooksForge.toForge(toInsert), action);
        }
        
        @Override
        public long extract(FluidStack toExtract, boolean simulate) {
            var action = simulate ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE;
            return storage.drain(FluidStackHooksForge.toForge(toExtract), action).getAmount();
        }
        
        @Override
        public List<FluidStack> getContent() {
            
            var content = new ArrayList<FluidStack>();
            for (int i = 0; i < storage.getTanks(); i++) {
                var tank = storage.getFluidInTank(i);
                content.add(FluidStackHooksForge.fromForge(tank));
            }
            
            return content;
        }
        
        @Override
        public void update() {
        }
    }
    
    // this is used by other mods to interact with the oritech fluid containers
    public static class SingleSlotContainerStorageWrapper implements IFluidHandler {
        
        public final FluidApi.SingleSlotContainer container;
        
        public static SingleSlotContainerStorageWrapper of(@Nullable FluidApi.SingleSlotContainer container) {
            if (container == null) return null;
            return new SingleSlotContainerStorageWrapper(container);
        }
        
        public SingleSlotContainerStorageWrapper(FluidApi.SingleSlotContainer container) {
            this.container = container;
        }
        
        
        @Override
        public int getTanks() {
            return 1;
        }
        
        @Override
        public net.neoforged.neoforge.fluids.@NotNull FluidStack getFluidInTank(int i) {
            return FluidStackHooksForge.toForge(container.getStack());
        }
        
        @Override
        public int getTankCapacity(int i) {
            return (int) container.getCapacity();
        }
        
        @Override
        public boolean isFluidValid(int i, net.neoforged.neoforge.fluids.@NotNull FluidStack fluidStack) {
            return true;
        }
        
        @Override
        public int fill(net.neoforged.neoforge.fluids.@NotNull FluidStack fluidStack, @NotNull FluidAction fluidAction) {
            
            var result = (int) container.insert(FluidStackHooksForge.fromForge(fluidStack), fluidAction.simulate());
            
            if (result > 0 && fluidAction.execute())
                container.update();
            
            return result;
        }
        
        @Override
        public net.neoforged.neoforge.fluids.@NotNull FluidStack drain(net.neoforged.neoforge.fluids.@NotNull FluidStack fluidStack, @NotNull FluidAction fluidAction) {
            var extractedAmount = container.extract(FluidStackHooksForge.fromForge(fluidStack), fluidAction.simulate());
            
            if (extractedAmount > 0 && fluidAction.execute())
                container.update();
            
            return new net.neoforged.neoforge.fluids.FluidStack(fluidStack.getFluid(), (int) extractedAmount);
        }
        
        @Override
        public net.neoforged.neoforge.fluids.@NotNull FluidStack drain(int i, @NotNull FluidAction fluidAction) {
            var extractedAmount =  container.extract(container.getStack().copyWithAmount(i), fluidAction.simulate());
            
            if (extractedAmount > 0 && fluidAction.execute())
                container.update();
            
            return new net.neoforged.neoforge.fluids.FluidStack(container.getStack().getFluid(), (int) extractedAmount);
        }
    }
    
    // this is used by other mods to access an oritech in/out container
    public static class InOutContainerStorageWrapper implements IFluidHandler {
        
        public final FluidApi.InOutSlotContainer container;
        
        public static InOutContainerStorageWrapper of(FluidApi.InOutSlotContainer container) {
            if (container == null) return null;
            return new InOutContainerStorageWrapper(container);
        }
        
        public InOutContainerStorageWrapper(FluidApi.InOutSlotContainer container) {
            this.container = container;
        }
        
        @Override
        public int getTanks() {
            return 2;
        }
        
        @Override
        public net.neoforged.neoforge.fluids.@NotNull FluidStack getFluidInTank(int i) {
            if (i > 1) return net.neoforged.neoforge.fluids.FluidStack.EMPTY;
            return FluidStackHooksForge.toForge(container.getContent().get(i));
        }
        
        @Override
        public int getTankCapacity(int i) {
            return (int) container.getCapacity();
        }
        
        @Override
        public boolean isFluidValid(int i, net.neoforged.neoforge.fluids.@NotNull FluidStack fluidStack) {
            return true;
        }
        
        @Override
        public int fill(net.neoforged.neoforge.fluids.@NotNull FluidStack fluidStack, @NotNull FluidAction fluidAction) {
            var result = (int) container.insert(FluidStackHooksForge.fromForge(fluidStack), fluidAction.simulate());
            
            if (result > 0 && fluidAction.execute())
                container.update();
            
            return result;
        }
        
        @Override
        public net.neoforged.neoforge.fluids.@NotNull FluidStack drain(net.neoforged.neoforge.fluids.@NotNull FluidStack fluidStack, @NotNull FluidAction fluidAction) {
            var extractedAmount = container.extract(FluidStackHooksForge.fromForge(fluidStack), fluidAction.simulate());
            
            if (extractedAmount > 0 && fluidAction.execute())
                container.update();
            
            return new net.neoforged.neoforge.fluids.FluidStack(fluidStack.getFluid(), (int) extractedAmount);
        }
        
        @Override
        public net.neoforged.neoforge.fluids.@NotNull FluidStack drain(int i, @NotNull FluidAction fluidAction) {
            var extractedAmount =  container.extract(container.getOutStack().copyWithAmount(i), fluidAction.simulate());
            
            if (extractedAmount > 0 && fluidAction.execute())
                container.update();
            
            return new net.neoforged.neoforge.fluids.FluidStack(container.getOutStack().getFluid(), (int) extractedAmount);
        }
    }
}
