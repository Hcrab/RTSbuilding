package com.rtsbuilding.rtsbuilding.client.infrastructure.module.camera;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;


final class FreeCameraMode {

    
    
    

    private static final float ROT_INPUT_CLAMP = 20.0F;
    private static final float ROTATE_GAIN_X = 0.24F;
    private static final float ROTATE_GAIN_Y = 0.22F;
    private static final double DOLLY_PER_SCROLL = 2.6D;
    private static final double VERTICAL_SPEED = 0.32D;
    private static final double FAST_VERTICAL_SPEED = 0.55D;
    private static final double DOLLY_DAMP_MAX_DIST = 30.0D;
    private static final double DOLLY_DAMP_MIN_FACTOR = 0.05D;
    private static final double DOLLY_DAMP_RAY_RANGE = 128.0D;
    private static final double MIN_HEIGHT_OFFSET = -35.0D;
    private static final double MAX_HEIGHT_OFFSET = 110.0D;
    
    private static final float ROT_EMA_ALPHA = 0.28F;
    private static final float ROT_EMA_DECAY = 0.78F;
    private static final float CAMERA_INPUT_EPSILON = 1.0e-4F;

    
    private float emaRotateX;
    private float emaRotateY;

    
    
    

    
    void processInput(CameraState state, CameraInput input) {
        float rawX = Mth.clamp(state.pendingRawRotateX, -ROT_INPUT_CLAMP, ROT_INPUT_CLAMP);
        float rawY = Mth.clamp(state.pendingRawRotateY, -ROT_INPUT_CLAMP, ROT_INPUT_CLAMP);

        
        this.emaRotateX += (rawX - this.emaRotateX) * ROT_EMA_ALPHA;
        this.emaRotateY += (rawY - this.emaRotateY) * ROT_EMA_ALPHA;

        
        if (Math.abs(rawX) < CAMERA_INPUT_EPSILON) this.emaRotateX *= ROT_EMA_DECAY;
        if (Math.abs(rawY) < CAMERA_INPUT_EPSILON) this.emaRotateY *= ROT_EMA_DECAY;

        float sensScale = state.inputSensitivity;
        float rotateXForTick = this.emaRotateX * state.rotateSensitivity * sensScale;
        float rotateYForTick = this.emaRotateY * state.rotateSensitivity * sensScale;

        
        if (Math.abs(rotateXForTick) < CAMERA_INPUT_EPSILON) {
            rotateXForTick = 0.0F;
            this.emaRotateX = 0.0F;
        }
        if (Math.abs(rotateYForTick) < CAMERA_INPUT_EPSILON) {
            rotateYForTick = 0.0F;
            this.emaRotateY = 0.0F;
        }

        state.localYaw += rotateXForTick * ROTATE_GAIN_X;
        if (state.pendingRotateSteps != 0) {
            state.localYaw = snapQuarter(state.localYaw + 90.0F * state.pendingRotateSteps);
        }
        state.localPitch = Mth.wrapDegrees(state.localPitch + rotateYForTick * ROTATE_GAIN_Y);

        double sensNorm = state.rotateSensitivity / 5.0D;
        double speed = (input.fast ? 0.80D : 0.45D) * sensNorm;
        double yawRad = Math.toRadians(state.localYaw);
        double sin = Math.sin(yawRad);
        double cos = Math.cos(yawRad);

        double half = state.maxRadius;

        
        double kbDx = (-sin * input.forward + cos * input.strafe) * speed;
        double kbDz = (cos * input.forward + sin * input.strafe) * speed;
        double kbDy = input.vertical * (input.fast ? FAST_VERTICAL_SPEED : VERTICAL_SPEED) * sensNorm;
        state.localX = Mth.clamp(state.localX + kbDx, state.anchorX - half, state.anchorX + half);
        state.localY = Mth.clamp(state.localY + kbDy, state.anchorY + MIN_HEIGHT_OFFSET, state.anchorY + MAX_HEIGHT_OFFSET);
        state.localZ = Mth.clamp(state.localZ + kbDz, state.anchorZ - half, state.anchorZ + half);

        
        if (state.pendingScroll != 0.0F) {
            double pitchRad = Math.toRadians(state.localPitch);
            double dampingFactor = computeDollyDamping(state, yawRad, pitchRad);
            double scroll = state.pendingScroll * DOLLY_PER_SCROLL * dampingFactor;
            double scrollX = -Math.sin(yawRad) * Math.cos(pitchRad) * scroll;
            double scrollY = -Math.sin(pitchRad) * scroll;
            double scrollZ = Math.cos(yawRad) * Math.cos(pitchRad) * scroll;
            state.localX = Mth.clamp(state.localX + scrollX, state.anchorX - half, state.anchorX + half);
            state.localY = Mth.clamp(state.localY + scrollY, state.anchorY + MIN_HEIGHT_OFFSET, state.anchorY + MAX_HEIGHT_OFFSET);
            state.localZ = Mth.clamp(state.localZ + scrollZ, state.anchorZ - half, state.anchorZ + half);
        }

        
        if (state.pendingPanX != 0.0F || state.pendingPanY != 0.0F) {
            double dragScale = 0.010D * Math.max(8.0D, state.localHeightOffset) * sensScale * sensNorm;
            double dragDx = cos * -state.pendingPanY * dragScale + (-sin) * state.pendingPanX * dragScale;
            double dragDz = sin * -state.pendingPanY * dragScale + cos * state.pendingPanX * dragScale;
            state.localX = Mth.clamp(state.localX + dragDx, state.anchorX - half, state.anchorX + half);
            state.localZ = Mth.clamp(state.localZ + dragDz, state.anchorZ - half, state.anchorZ + half);
        }
        state.localHeightOffset = state.localY - state.anchorY;
    }

    
    void resetEma() {
        this.emaRotateX = 0.0F;
        this.emaRotateY = 0.0F;
    }

    
    
    

    
    CameraInput readCameraInput() {
        return new CameraInput(0.0F, 0.0F, 0.0F, Minecraft.getInstance().options.keySprint.isDown());
    }

    
    void resetAccumulation(CameraState state) {
        state.pendingPanX = 0.0F;
        state.pendingPanY = 0.0F;
        state.pendingScroll = 0.0F;
        state.pendingRotateSteps = 0;
        state.pendingRawRotateX = 0.0F;
        state.pendingRawRotateY = 0.0F;
    }

    
    
    

    
    private double computeDollyDamping(CameraState state, double yawRad, double pitchRad) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return 1.0D;

        Vec3 from = new Vec3(state.localX, state.localY, state.localZ);
        double dx = -Math.sin(yawRad) * Math.cos(pitchRad);
        double dy = -Math.sin(pitchRad);
        double dz = Math.cos(yawRad) * Math.cos(pitchRad);
        Vec3 to = from.add(dx * DOLLY_DAMP_RAY_RANGE, dy * DOLLY_DAMP_RAY_RANGE, dz * DOLLY_DAMP_RAY_RANGE);

        BlockHitResult hit = mc.level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.getCameraEntity()));
        if (hit.getType() == HitResult.Type.BLOCK) {
            double dist = from.distanceTo(hit.getLocation());
            if (dist < DOLLY_DAMP_MAX_DIST) {
                double t = dist / DOLLY_DAMP_MAX_DIST;
                t = t * t * (3.0D - 2.0D * t);
                return Mth.lerp(t, DOLLY_DAMP_MIN_FACTOR, 1.0D);
            }
        }
        return 1.0D;
    }

    
    
    

    static float snapQuarter(float yaw) {
        return Math.round(yaw / 90.0F) * 90.0F;
    }

    
    
    

    record CameraInput(float forward, float strafe, float vertical, boolean fast) {
        boolean hasMovement() {
            return forward != 0.0F || strafe != 0.0F || vertical != 0.0F;
        }
    }
}
