package rearth.oritech.client.renderers;

import net.minecraft.client.MinecraftClient;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector2f;
import rearth.oritech.Oritech;
import rearth.oritech.block.entity.interaction.LaserArmBlockEntity;
import rearth.oritech.util.Geometry;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.DefaultedBlockGeoModel;

import java.util.HashMap;

public class LaserArmModel<T extends LaserArmBlockEntity & GeoAnimatable> extends DefaultedBlockGeoModel<T> {
    
    private static final HashMap<Long, ModelRenderData> additionalData = new HashMap<>();
    private static final HashMap<Long, Vec3d> drillOffsets = new HashMap<>();
    
    private Vec3d lastActivePlayerPos = Vec3d.ZERO;
    
    public LaserArmModel(String subpath) {
        super(Oritech.id(subpath));
    }
    
    private ModelRenderData getById(long id) {
        return additionalData.computeIfAbsent(id, s -> new ModelRenderData(0, 0, getAnimationProcessor().getBone("pivotX"), getAnimationProcessor().getBone("pivotY")));
    }
    
    private Vec3d getOffsetByDrillId(long id, T laserEntity) {
        return drillOffsets.computeIfAbsent(id, s -> {
            var drillFacing = laserEntity.getWorld().getBlockState(laserEntity.getCurrentTarget()).get(Properties.HORIZONTAL_FACING);
            return Geometry.rotatePosition(new Vec3d(1, 1.4, 0), drillFacing);
        });
    }
    
    @Override
    public void setCustomAnimations(T laserEntity, long instanceId, AnimationState<T> animationState) {
        
        Vec3d target;
        var isIdle = false;
        if (laserEntity.getCurrentTarget() == null || laserEntity.getCurrentTarget() == BlockPos.ORIGIN)  {
            target = getIdleTarget(laserEntity);
            isIdle = true;
        } else {
            target = laserEntity.getVisualTarget();
        }
        
        if (target == null || target == Vec3d.ZERO) return;
        
        if (!isIdle && laserEntity.isTargetingDeepdrill(laserEntity.getWorld().getBlockState(laserEntity.getCurrentTarget()).getBlock())) {
            var drillId = laserEntity.getCurrentTarget().asLong();
            var offset = getOffsetByDrillId(drillId, laserEntity);
            target = target.add(offset);
        }
        
        var ownPos = Vec3d.of(laserEntity.getPos());
        var offset = target.subtract(ownPos.add(0, 1.7 - 0.5, 0)); // add 1.55 to get to height of pivotX, minus block center offset
        
        // thanks to: https://math.stackexchange.com/questions/878785/how-to-find-an-angle-in-range0-360-between-2-vectors
        var offsetY = new Vector2f((float) offset.getX(), (float) offset.getZ());
        var forwardY = new Vector2f(0, 1);
        var detY = determinant(offsetY, forwardY);
        var dotY = offsetY.dot(forwardY);
        var angleY = Math.atan2(detY, dotY);
        
        // to create a 2d vector in a plane based on normal angleY
        var lengthY = offsetY.length();
        var heightDiff = offset.getY();
        
        var offsetX = new Vector2f(lengthY, (float) heightDiff);
        var forwardX = new Vector2f(0, 1);
        var detX = determinant(offsetX, forwardX);
        var dotX = offsetX.dot(forwardX);
        var angleX = Math.atan2(detX, dotX);
        
        angleX -= 42.5 * Geometry.DEG_TO_RAD; //to offset for parent bone rotations
        
        var data = getById(instanceId);
        
        if (data.boneX != null) {
            var newRotY = lerp(data.angleY, (float) angleY, 0.06f);
            var newRotX = lerp(data.angleX, (float) angleX, 0.06f);
            data.boneY.setRotY(newRotY);
            data.boneX.setRotX(newRotX);
            
            data.angleY = newRotY;
            data.angleX = newRotX;
        }
        
    }
    
    private Vec3d getIdleTarget(T entity) {
        var intervalA = (entity.getWorld().getTime() / 20) % 2 == 0;
        var intervalB = (entity.getWorld().getTime() / 60) % 2 == 0;
        var intervalC = (entity.getWorld().getTime() / 50) % 3 == 0;
        var intervalD = (entity.getWorld().getTime() / 80) % 2 == 0;
        
        var offsetB = new Vec3d(0, intervalA ? 0.4f : -1.8f, 0);
        var offsetC = new Vec3d(intervalB ? 0.4 : -0.4, 0, intervalC ? -0.7 : 0.4);
        var offsetD = new Vec3d(0, intervalD ? 0.2f : -3f, intervalB ? 0.3 : -0.3);
        
        if (entity.getWorld().getRandom().nextFloat() > 0.9f) {
             lastActivePlayerPos = MinecraftClient.getInstance().player.getEyePos();
        }
        
        if (lastActivePlayerPos.equals(Vec3d.ZERO))
            return Vec3d.ZERO;
        
        return lastActivePlayerPos.add(offsetB).add(offsetC).add(offsetD);
    }
    
    public static float lerp(float a, float b, float f) {
        return a + f * (b - a);
    }
    
    public static float determinant(Vector2f a, Vector2f b) {
        return a.x * b.y - a.y * b.x;
    }
    
    private static class ModelRenderData {
        protected float angleY;
        protected float angleX;
        protected GeoBone boneX;
        protected GeoBone boneY;
        
        public ModelRenderData(float angleX, float angleY, GeoBone boneX, GeoBone boneY) {
            this.angleY = angleY;
            this.angleX = angleX;
            this.boneX = boneX;
            this.boneY = boneY;
        }
    }
}
