package rearth.oritech.client.renderers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import rearth.oritech.block.entity.interaction.PowerPoleEntity;
import rearth.oritech.util.Geometry;

public class PowerPoleLineRenderer implements BlockEntityRenderer<PowerPoleEntity> {
    
    private static final ResourceLocation CABLE_TEXTURE = ResourceLocation.withDefaultNamespace("textures/block/white_concrete.png");
    
    @Override
    public void render(@NotNull PowerPoleEntity blockEntity, float partialTick, @NotNull PoseStack poseStack, @NotNull MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        
        var connections = blockEntity.getConnections();
        if (connections.isEmpty() || blockEntity.isRemoved()) return;
        
        var offsetRight = Geometry.getForward(blockEntity.getFacingForMultiblock());
        
        var camPos = Minecraft.getInstance().cameraEntity.blockPosition();
        
        var startPos = blockEntity.getBlockPos().offset(offsetRight);
        var startPosB = blockEntity.getBlockPos().offset(offsetRight.multiply(-1));
        var startVec = Vec3.atCenterOf(startPos);
        var startVecB = Vec3.atCenterOf(startPosB);
        
        var poleDist = blockEntity.getBlockPos().distSqr(camPos);
        
        var consumer = bufferSource.getBuffer(RenderType.entitySolid(CABLE_TEXTURE));
        
        for (var target : connections) {
            if (target == null) continue;
            
            var targetDist = target.pos().distSqr(camPos);
            if (targetDist < poleDist) continue;    // only render connections from the closer side
            
            // todo assign target A and B to closest A & B, to avoid crossing lines (calculate total length in both variants, and choose smaller one)
            var targetA = target.pos().offset(Geometry.getRight(target.facing()));
            var targetB = target.pos().offset(Geometry.getLeft(target.facing()));
            
            var targetVec = Vec3.atCenterOf(targetA);
            var targetVecB = Vec3.atCenterOf(targetB);
            var offset = targetVec.subtract(startVec);
            var offsetB = targetVecB.subtract(startVecB);
            
            var thickness = 0.05f;
            
            // todo fix render methods not respecting the start and end offset
            renderHangingCable(poseStack, consumer, offset, thickness, packedLight);
            // renderHangingCable(poseStack, consumer, offsetB, thickness, packedLight);
        }
    }
    
    private void renderHangingCable(PoseStack poseStack, VertexConsumer consumer, Vec3 totalOffset, float thickness, int packedLight) {
        poseStack.pushPose();
        
        poseStack.translate(0.5, 0.5, 0.5);
        
        var segments = 8;
        var totalLength = (float) totalOffset.length();
        var sag = totalLength * 0.1f;
        
        var currentPos = Vec3.ZERO;
        
        for (int i = 0; i < segments; i++) {
            var t = (float) (i + 1) / segments;
            
            // Linear interpolation
            var nextPos = totalOffset.scale(t);
            
            // Parabolic Sag to Y component
            // Formula: 4 * sag * t * (1-t)
            var sagY = -sag * 4 * t * (1 - t);
            nextPos = nextPos.add(0, sagY, 0);
            
            // Calculate vector for this specific segment
            var scaling = 1.01f;
            var segmentDelta = nextPos.subtract(currentPos).multiply(scaling, scaling, scaling);
            
            drawSegment(poseStack, consumer, currentPos, segmentDelta, thickness, packedLight);
            
            currentPos = nextPos;
        }
        
        poseStack.popPose();
    }
    
    private void drawSegment(PoseStack poseStack, VertexConsumer consumer, Vec3 startPos, Vec3 delta, float thickness, int packedLight) {
        poseStack.pushPose();
        
        // Move to start of segment
        poseStack.translate(startPos.x, startPos.y, startPos.z);
        
        // Calculate rotations to align the Y-axis (length) with the segment delta
        var xzLen = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        var yRot = (float) (-Math.atan2(-delta.x, delta.z));
        var xRot = (float) (-Math.atan2(delta.y, xzLen));
        
        poseStack.mulPose(Axis.YP.rotation(yRot));
        poseStack.mulPose(Axis.XP.rotation(xRot + (float) (Math.PI / 2)));
        
        // Draw the Box
        float length = (float) delta.length();
        float r = thickness;
        
        int red = 50, green = 50, blue = 50, alpha = 255;
        var pose = poseStack.last();
        
        // --- Side 1 (Front) ---
        addVertex(consumer, pose, -r, 0, r, red, green, blue, alpha, 0, 0, 0, 0, 1, packedLight);
        addVertex(consumer, pose, r, 0, r, red, green, blue, alpha, 1, 0, 0, 0, 1, packedLight);
        addVertex(consumer, pose, r, length, r, red, green, blue, alpha, 1, 1, 0, 0, 1, packedLight);
        addVertex(consumer, pose, -r, length, r, red, green, blue, alpha, 0, 1, 0, 0, 1, packedLight);
        
        // --- Side 2 (Back) ---
        addVertex(consumer, pose, r, 0, -r, red, green, blue, alpha, 0, 0, 0, 0, -1, packedLight);
        addVertex(consumer, pose, -r, 0, -r, red, green, blue, alpha, 1, 0, 0, 0, -1, packedLight);
        addVertex(consumer, pose, -r, length, -r, red, green, blue, alpha, 1, 1, 0, 0, -1, packedLight);
        addVertex(consumer, pose, r, length, -r, red, green, blue, alpha, 0, 1, 0, 0, -1, packedLight);
        
        // --- Side 3 (Left) ---
        addVertex(consumer, pose, -r, 0, -r, red, green, blue, alpha, 0, 0, -1, 0, 0, packedLight);
        addVertex(consumer, pose, -r, 0, r, red, green, blue, alpha, 1, 0, -1, 0, 0, packedLight);
        addVertex(consumer, pose, -r, length, r, red, green, blue, alpha, 1, 1, -1, 0, 0, packedLight);
        addVertex(consumer, pose, -r, length, -r, red, green, blue, alpha, 0, 1, -1, 0, 0, packedLight);
        
        // --- Side 4 (Right) ---
        addVertex(consumer, pose, r, 0, r, red, green, blue, alpha, 0, 0, 1, 0, 0, packedLight);
        addVertex(consumer, pose, r, 0, -r, red, green, blue, alpha, 1, 0, 1, 0, 0, packedLight);
        addVertex(consumer, pose, r, length, -r, red, green, blue, alpha, 1, 1, 1, 0, 0, packedLight);
        addVertex(consumer, pose, r, length, r, red, green, blue, alpha, 0, 1, 1, 0, 0, packedLight);
        
        poseStack.popPose();
    }
    
    private void addVertex(VertexConsumer consumer, PoseStack.Pose pose, float x, float y, float z, int r, int g, int b, int a, float u, float v, float nx, float ny, float nz, int packedLight) {
        consumer.addVertex(pose.pose(), x, y, z)
          .setColor(r, g, b, a)
          .setUv(u, v)
          .setOverlay(OverlayTexture.NO_OVERLAY)
          .setLight(packedLight)
          .setNormal(pose, nx, ny, nz);
    }
    
    @Override
    public boolean shouldRenderOffScreen(@NotNull PowerPoleEntity blockEntity) {
        return true;
    }
}