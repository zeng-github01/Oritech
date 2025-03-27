package rearth.oritech.item.other;

import dev.architectury.fluid.FluidStack;
import dev.architectury.hooks.fluid.FluidStackHooks;
import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import rearth.oritech.Oritech;
import rearth.oritech.util.fluid.FluidApi;
import rearth.oritech.util.fluid.containers.SimpleItemFluidContainer;

import java.util.List;

public class SmallFluidTankBlockItem extends BlockItem implements FluidApi.ItemApiProvider {
    
    public SmallFluidTankBlockItem(Block block, Settings settings) {
        super(block, settings);
    }
    
    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        var data = stack.getOrDefault(FluidApi.ITEM.getFluidComponent(), FluidStack.empty());
        
        if (data.isEmpty()) {
            tooltip.add(Text.translatable("tooltip.oritech.fluid_empty"));
        } else {
            var amount = data.getAmount() / FluidStackHooks.bucketAmount();
            tooltip.add(Text.translatable("tooltip.oritech.fluid_content_tank_tooltip", amount, FluidStackHooks.getName(data).getString()));
        }
        
        super.appendTooltip(stack, context, tooltip, type);
        
    }
    
    @Override
    public Text getName(ItemStack stack) {
        var content = stack.getOrDefault(FluidApi.ITEM.getFluidComponent(), FluidStack.empty());
        if (content.isEmpty()) {
            return super.getName(stack);
        } else {
            return FluidStackHooks.getName(content).copy().formatted(Formatting.ITALIC).append(Text.literal(" ")).append(super.getName(stack));
        }
    }
    
    @Override
    public FluidApi.SingleSlotContainer getFluidStorage(ItemStack stack) {
        return new SimpleItemFluidContainer(Oritech.CONFIG.portableTankCapacityBuckets() * FluidStackHooks.bucketAmount(), stack);
    }
}
