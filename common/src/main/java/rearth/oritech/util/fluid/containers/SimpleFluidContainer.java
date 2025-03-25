package rearth.oritech.util.fluid.containers;

import dev.architectury.fluid.FluidStack;
import net.minecraft.component.ComponentChanges;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.Fluids;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtOps;
import rearth.oritech.util.fluid.FluidApi;

import java.util.List;

public class SimpleFluidContainer extends FluidApi.SingleSlotContainer {
    
    public static Long transfer(SimpleFluidContainer from, SimpleFluidContainer to, long maxAmount, boolean simulate) {
        
        var extracted = from.extract(FluidStack.create(to.getFluid(), maxAmount, to.getChanges()), true);   // check how much we could extract at most
        var inserted = to.insert(FluidStack.create(to.getFluid(), extracted, to.getChanges()), simulate);   // insert max extraction amount
        extracted = from.extract(FluidStack.create(to.getFluid(), inserted, to.getChanges()), simulate);    // extract only how much was actually inserted
        
        if (extracted > 0 && !simulate) {
            from.update();
            to.update();
        }
        
        return extracted;
    }
    
    private FluidStack content;
    private final Long capacity;
    private final Runnable onUpdate;
    
    public SimpleFluidContainer(Long capacity, Runnable onUpdate) {
        this.capacity = capacity;
        this.onUpdate = onUpdate;
        this.content = FluidStack.create(Fluids.EMPTY, 0);
    }
    
    public SimpleFluidContainer(Long capacity) {
        this(capacity, () -> {});
    }
    
    
    @Override
    public long insert(FluidStack toInsert, boolean simulate) {
        return SimpleInOutFluidContainer.insertTo(toInsert, simulate, capacity, content, stack -> this.content = stack);
    }
    
    @Override
    public long extract(FluidStack toExtract, boolean simulate) {
        return SimpleInOutFluidContainer.extractFrom(toExtract, simulate, content);
    }
    
    @Override
    public List<FluidStack> getContent() {
        return List.of(content);
    }
    
    public void writeNbt(NbtCompound nbt, String suffix) {
        FluidStack.CODEC.encodeStart(NbtOps.INSTANCE, content).result().ifPresent(tag -> nbt.put("fluid" + suffix, tag));
    }
    
    public void readNbt(NbtCompound nbt, String suffix) {
        content = FluidStack.CODEC.parse(NbtOps.INSTANCE, nbt.get("fluid" + suffix)).result().orElse(FluidStack.empty());
    }
    
    public void setAmount(long amount) {
        content.setAmount(amount);
    }
    
    public long getAmount() {
        return content.getAmount();
    }
    
    public void setFluid(Fluid fluid) {
        content = FluidStack.create(fluid, getAmount(), getChanges());
    }
    
    public Fluid getFluid() {
        return content.getFluid();
    }
    
    public void setChanges(ComponentChanges data) {
        content = FluidStack.create(getFluid(), getAmount(), data);
    }
    
    public ComponentChanges getChanges() {
        return content.getPatch();
    }
    
    @Override
    public long getCapacity() {
        return capacity;
    }
    
    @Override
    public void update() {
        onUpdate.run();
    }
    
    @Override
    public void setStack(FluidStack stack) {
        content = stack;
    }
    
    @Override
    public FluidStack getStack() {
        return content;
    }
}
