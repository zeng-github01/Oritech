package rearth.oritech.client.cablesurfer;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.Vec3;
import rearth.oritech.init.ItemContent;

public class ClientZiplineHandler {
    
    private static boolean active = false;
    private static Vec3 startPos;
    private static Vec3 endPos;
    
    private static float progress;          // 0.0 (Start) to 1.0 (End)
    private static float currentSpeed;      // Blocks per tick
    private static double totalDistance;
    
    // Config
    private static final float MAX_SPEED = 1.5f;     // Max speed
    private static final float ACCELERATION = 0.1f; // Speed gained per tick holding W
    private static final float DRAG = 0.98f;         // Air resistance (slows you down if you release W)
    private static final float HANG_OFFSET = 1.95f;   // Distance below the wire (Eye height + arm length)
    
    private static CameraType previousCamera;
    
    public static boolean isActive() {
        return active;
    }
    
    /**
     * Starts the zipline session.
     * @param start The absolute world coordinate of the wire start.
     * @param end The absolute world coordinate of the wire end.
     * @param initialSpeed Initial velocity (e.g. 0.0 or 0.5).
     */
    public static void start(Vec3 start, Vec3 end, float initialSpeed) {
        if (active) return; // Already riding
        
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        
        active = true;
        startPos = start;
        endPos = end;
        totalDistance = start.distanceTo(end);
        
        var searchPos = player.position().add(0, HANG_OFFSET, 0);
        var rawProgress = calculateClosestProgress(start, end, searchPos);
        
        var cableDir = end.subtract(start).normalize();
        var lookDir = player.getLookAngle();
        
        // Dot Product < 0 means looking opposite to cable direction
        if (cableDir.dot(lookDir) < 0) {
            // Player is looking backwards. Swap start/end.
            startPos = end;
            endPos = start;
            // Invert progress (0.1 becomes 0.9)
            progress = 1.0f - rawProgress;
        } else {
            // Player is looking forwards. Keep as is.
            startPos = start;
            endPos = end;
            progress = rawProgress;
        }
        
        currentSpeed = Math.abs(initialSpeed);
        if (currentSpeed == 0) currentSpeed = 0.15f;
        
        // Force 3rd Person Camera for better view
        previousCamera = Minecraft.getInstance().options.getCameraType();
        if (previousCamera == CameraType.FIRST_PERSON) {
            Minecraft.getInstance().options.setCameraType(CameraType.THIRD_PERSON_BACK);
        }
        
        // Initial Snap
        Vec3 ropePos = CableMath.getAt(startPos, endPos, progress);
        Vec3 initialPos = ropePos.add(0, -HANG_OFFSET, 0);
        player.setPos(initialPos.x, initialPos.y, initialPos.z);
        player.setDeltaMovement(player.getLookAngle().scale(currentSpeed));
    }
    
    private static float calculateClosestProgress(Vec3 start, Vec3 end, Vec3 targetPos) {
        var bestDistSq = Double.MAX_VALUE;
        var bestT = 0.0f;
        
        var steps = (int) start.distanceTo(end) + 1;
        
        for (int i = 0; i <= steps; i++) {
            float t = (float) i / steps;
            var pointOnWire = CableMath.getAt(start, end, t);
            
            double distSq = pointOnWire.distanceToSqr(targetPos);
            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                bestT = t;
            }
        }
        
        return bestT;
    }
    
    public static void onClientTick() {
        if (!active) return;
        
        var player = Minecraft.getInstance().player;
        if (player == null || !player.isAlive() || !player.getMainHandItem().is(ItemContent.WRENCH)) {
            dismount(false);
            return;
        }
        
        // Shift -> Drop
        if (player.input.shiftKeyDown) {
            dismount(false);
            return;
        }
        
        // Space -> Jump Off
        if (player.input.jumping) {
            dismount(true);
            return;
        }
        
        // W -> Accelerate
        if (player.input.up) {
            currentSpeed += ACCELERATION;
        }
        // S -> Brake / Reverse
        else if (player.input.down) {
            currentSpeed -= ACCELERATION;
        }
        
        // Apply Drag (Friction)
        currentSpeed *= DRAG;
        
        // Clamp Speed
        currentSpeed = Mth.clamp(currentSpeed, -MAX_SPEED, MAX_SPEED);
        
        // Convert speed (blocks/tick) to progress percentage (0.0-1.0)
        // distance = rate * time -> rate = distance / time (but here we step by speed)
        float progressDelta = (float) (currentSpeed / totalDistance);
        progress += progressDelta;
        
        // Check End of Line
        if (progress >= 1.0f) {
            progress = 1.0f;
            dismount(true); // Auto-jump at end
            return;
        } else if (progress <= 0.0f) {
            progress = 0.0f;
            currentSpeed = 0; // Hit the start, stop moving
            dismount(true);
        }
        
        // Calculate Position on Catenary Curve
        Vec3 ropePos = CableMath.getAt(startPos, endPos, progress);
        Vec3 nextPlayerPos = ropePos.add(0, -HANG_OFFSET, 0);
        
        if (currentSpeed > 0.6) { // todo add config var
            double blocksRemaining = (1.0f - progress) * totalDistance;
            
            // Calculate ejection distance dynamically
            // Formula: Minimum Buffer + (Speed * Ticks_Ahead)
            double dynamicEjectDist = 1.5 + (currentSpeed * 3);
            
            if (blocksRemaining < dynamicEjectDist) {
                dismount(false);
                var currentVel = player.getDeltaMovement();
                player.setPos(nextPlayerPos.x, nextPlayerPos.y + 1, nextPlayerPos.z);
                player.setDeltaMovement(currentVel.x * 1.2, 0.8, currentVel.z * 1.2);
                return;
            }
        }
        
        // We set the velocity to match the movement.
        // This ensures that if we dismount next tick, we carry the momentum.
        Vec3 oldPos = player.position();
        Vec3 velocity = nextPlayerPos.subtract(oldPos);
        
        var bounds = player.getDimensions(Pose.STANDING).makeBoundingBox(nextPlayerPos);
        bounds = bounds.deflate(0.1);
        
        if (!player.level().noCollision(player, bounds)) {
            dismount(false);
            return;
        }
        
        player.setPos(nextPlayerPos.x, nextPlayerPos.y, nextPlayerPos.z);
        player.setDeltaMovement(velocity);
        
        // Reset fall distance so we don't die on landing
        player.fallDistance = 0;
    }
    
    private static void dismount(boolean jump) {
        active = false;
        
        // Restore Camera
        if (previousCamera != null) {
            Minecraft.getInstance().options.setCameraType(previousCamera);
        }
        
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            if (jump) {
                // Add a little hop upwards + carry forward momentum
                Vec3 currentVel = player.getDeltaMovement();
                player.setDeltaMovement(currentVel.x * 1.2, 0.6, currentVel.z * 1.2);
            } else {
                // Just drop with current momentum
            }
        }
    }
}