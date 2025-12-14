package rearth.oritech.client.renderers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import rearth.oritech.block.entity.interaction.PowerPoleEntity;

public class PowerPoleLineRenderer implements BlockEntityRenderer<PowerPoleEntity> {
    
    @Override
    public void render(@NotNull PowerPoleEntity blockEntity, float partialTick, @NotNull PoseStack poseStack, @NotNull MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        
        var connections = blockEntity.getConnections();
        if (connections.isEmpty()) return;
        
        var startPos = blockEntity.getBlockPos();
        var startVec = Vec3.atCenterOf(startPos);
        
        var consumer = bufferSource.getBuffer(RenderType.solid());
        
        for (var targetPos : connections) {
            if (targetPos == null) continue;
            
            var targetVec = Vec3.atCenterOf(targetPos);
            var length = targetVec.subtract(startVec).multiply(0.4f, 0.4f, 0.4f);  // only draw first half of the cable
            
            // Define cable thickness
            var thickness = 0.05f;
            
            renderCable(poseStack, consumer, length, thickness, packedLight);
        }
    }
    
    private void renderCable(PoseStack poseStack, VertexConsumer consumer, Vec3 offset, float thickness, int packedLight) {
        poseStack.pushPose();
        
        // 1. Move to the center of the start block
        poseStack.translate(0.5, 0.5, 0.5);
        
        // 2. Calculate rotations to face the target
        var xzLen = Math.sqrt(offset.x * offset.x + offset.z * offset.z);
        var yRot = (float) (-Math.atan2(-offset.x, offset.z));
        var xRot = (float) (-Math.atan2(offset.y, xzLen));
        
        // 3. Apply rotations
        poseStack.mulPose(Axis.YP.rotation(yRot));
        poseStack.mulPose(Axis.XP.rotation(xRot + (float) (Math.PI / 2)));
        
        // 4. Draw the Cable (Box)
        var length = (float) offset.length();
        var pose = poseStack.last(); // Get the current Pose object
        var r = thickness;
        
        // Cable Color (Dark Grey)
        int red = 50, green = 50, blue = 50, alpha = 255;
        
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
    
    // Helper method using the PoseStack.Pose for both position and normal
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