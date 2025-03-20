package rearth.oritech.client.renderers;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import rearth.oritech.block.entity.pipes.ItemPipeInterfaceEntity;

import java.util.HashSet;

public class ItemPipeTransferRenderer implements BlockEntityRenderer<ItemPipeInterfaceEntity> {
    
    @Override
    public void render(ItemPipeInterfaceEntity entity, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay) {
        
        if (entity.activeStacks == null || entity.activeStacks.isEmpty()) return;
        
        var time = entity.getWorld().getTime() + tickDelta;
        var removedStacks = new HashSet<ItemPipeInterfaceEntity.RenderStackData>();
        
        for (var renderedStack : entity.activeStacks) {
            var age = time - renderedStack.startedAt();
            var neededTime = ItemPipeInterfaceEntity.calculatePathLength(renderedStack.pathLength());    // about 2 blocks/s, but much faster on longer paths
            var progress = age / neededTime;
            if (progress > 1) {
                removedStacks.add(renderedStack);
                continue;
            }
            
            progress = sigmoidFitted(progress);
            
            // get position in path at current progress (traverse path to current progress)
            var targetPathProgress = renderedStack.pathLength() * progress;
            var pathProgress = 0;
            var pathPosition = renderedStack.path().getFirst();
            Vec3d targetPos = Vec3d.ZERO;
            
            for (var segment : renderedStack.path()) {
                var segmentDist = segment.getManhattanDistance(pathPosition);
                
                if (pathProgress + segmentDist < targetPathProgress) {
                    pathProgress += segmentDist;
                    pathPosition = segment;
                } else {    // reaching or overshooting target
                    var remainingDist = targetPathProgress - pathProgress;
                    var targetOffset = Vec3d.of(segment.subtract(pathPosition)).normalize().multiply(remainingDist);
                    targetPos = Vec3d.of(pathPosition).add(targetOffset);
                    break;
                }
                
            }
            
            var offset = targetPos.subtract(Vec3d.of(entity.getPos()));
            
            matrices.push();
            matrices.translate(offset.x + 0.5, offset.y + 0.5, offset.z + 0.5);
            matrices.scale(0.4f, 0.4f, 0.4f);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-140));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-30));
            
            var renderedItem = renderedStack.rendered();
            
            MinecraftClient.getInstance().getItemRenderer().renderItem(
              renderedItem,
              ModelTransformationMode.GUI,
              light,
              OverlayTexture.DEFAULT_UV,
              matrices,
              vertexConsumers,
              entity.getWorld(),
              0
            );
            
            matrices.pop();
            
        }
        
        entity.activeStacks.removeAll(removedStacks);
        
    }
    
    private static double sigmoidFitted(double x) {
        return sigmoid((x - 0.5) * 2) + 0.5f;
    }
    
    private static double sigmoid(double x) {
        return x / (1 + Math.abs(x));
    }
}
