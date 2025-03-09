package rearth.oritech.util.item;

import net.minecraft.block.Block;
import net.minecraft.client.render.item.BuiltinModelItemRenderer;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.util.ActionResult;
import org.jetbrains.annotations.Nullable;
import rearth.oritech.Oritech;
import rearth.oritech.init.BlockContent;
import rearth.oritech.init.BlockEntitiesContent;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.model.DefaultedBlockGeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.function.Consumer;

public class UnstableContainerItem extends Item implements GeoItem {
    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);
    private final float scale;
    private final String name;
    
    public UnstableContainerItem(Settings settings, float scale, String name) {
        super(settings);
        this.scale = scale;
        this.name = name;
    }
    
    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        
        consumer.accept(new GeoRenderProvider() {
            GeoItemRenderer<UnstableContainerItem> renderer = null;
            
            @Override
            public @Nullable BuiltinModelItemRenderer getGeoItemRenderer() {
                if (this.renderer == null)
                    this.renderer = new GeoItemRenderer<>(new DefaultedBlockGeoModel<>(Oritech.id("models/" + name)));
                
                this.renderer.withScale(scale);
                
                return this.renderer;
            }
        });
    }
    
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    
    }
    
    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        
        var targetBlockPos = context.getBlockPos();
        var targetBlockState = context.getWorld().getBlockState(targetBlockPos);
        
        if (targetBlockState.getBlock().equals(BlockContent.BLACK_HOLE_BLOCK) || targetBlockState.getBlock().equals(BlockContent.FLUXITE_BLOCK)) {
            context.getWorld().setBlockState(targetBlockPos, BlockContent.UNSTABLE_CONTAINER.getDefaultState());
            var createdBlockState = context.getWorld().getBlockState(targetBlockPos);
            createdBlockState.getBlock().onPlaced(context.getWorld(), targetBlockPos, createdBlockState, context.getPlayer(), context.getStack());
            context.getWorld().getBlockEntity(targetBlockPos, BlockEntitiesContent.UNSTABLE_CONTAINER_BLOCK_ENTITY).get().setCapturedBlock(targetBlockState);
            
            var player = context.getPlayer();
            if (!player.isCreative()) {
                var stack = context.getStack();
                stack.decrement(1);
            }
            return ActionResult.CONSUME;
        }
        
        return super.useOnBlock(context);
    }
    
    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }
}
