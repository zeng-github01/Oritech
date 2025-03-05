package rearth.oritech.neoforge.mixin;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.Box;
import net.neoforged.neoforge.client.extensions.IBlockEntityRendererExtension;
import org.spongepowered.asm.mixin.Mixin;
import rearth.oritech.client.renderers.AcceleratorControllerRenderer;

@Mixin(AcceleratorControllerRenderer.class)
public class AcceleratorRenderBoundsMixin implements IBlockEntityRendererExtension {
    
    @Override
    public Box getRenderBoundingBox(BlockEntity blockEntity) {
        return Box.INFINITE;
    }
}
