package com.rtsbuilding.rtsbuilding.client.infrastructure.module.camera;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

final class PlayerOrbitCameraMode {

    
    
    

    private static final float ROT_INPUT_CLAMP = 20.0F;
    private static final float ROTATE_GAIN_X = 0.24F;
    private static final float ROTATE_GAIN_Y = 0.22F;
    private static final double DOLLY_PER_SCROLL = 2.6D;

    
    
    

    
    void init(CameraState state) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        double tx = mc.player.getX();
        double ty = mc.player.getY() + mc.player.getEyeHeight();
        double tz = mc.player.getZ();
        state.orbitTargetX = tx;
        state.orbitTargetY = ty;
        state.orbitTargetZ = tz;

        double dx = state.localX - tx;
        double dy = state.localY - ty;
        double dz = state.localZ - tz;
        state.orbitRadius = Math.sqrt(dx * dx + dy * dy + dz * dz);
        state.orbitAngle = Math.atan2(dx, dz);
        double distXZ = Math.sqrt(dx * dx + dz * dz);
        state.orbitPitch = Math.atan2(dy, distXZ);
        state.orbitRadius = Math.max(1.0, state.orbitRadius);
    }

    
    
    

    
    void processInput(CameraState state, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        
        
        double playerX = Mth.lerp(partialTick, mc.player.xo, mc.player.getX());
        double playerY = Mth.lerp(partialTick, mc.player.yo, mc.player.getY()) + mc.player.getEyeHeight();
        double playerZ = Mth.lerp(partialTick, mc.player.zo, mc.player.getZ());
        state.orbitTargetX = playerX;
        state.orbitTargetY = playerY;
        state.orbitTargetZ = playerZ;

        
        float rawX = Mth.clamp(state.pendingRawRotateX, -ROT_INPUT_CLAMP, ROT_INPUT_CLAMP);
        float rawY = Mth.clamp(state.pendingRawRotateY, -ROT_INPUT_CLAMP, ROT_INPUT_CLAMP);
        float panX = Mth.clamp(state.pendingPanX, -ROT_INPUT_CLAMP, ROT_INPUT_CLAMP);
        float panY = Mth.clamp(state.pendingPanY, -ROT_INPUT_CLAMP, ROT_INPUT_CLAMP);
        float sensScale = state.inputSensitivity;

        state.orbitAngle += (rawX + panX) * state.rotateSensitivity * sensScale * ROTATE_GAIN_X * 0.01;
        state.orbitPitch += (rawY + panY) * state.rotateSensitivity * sensScale * ROTATE_GAIN_Y * 0.01;
        
        if (state.pendingScroll != 0.0F) {
            double scroll = state.pendingScroll * DOLLY_PER_SCROLL;
            state.orbitRadius = Math.max(1.0, state.orbitRadius - scroll);
        }

        
        double sinAngle = Math.sin(state.orbitAngle);
        double cosAngle = Math.cos(state.orbitAngle);
        double cosPitch = Math.cos(state.orbitPitch);
        double sinPitch = Math.sin(state.orbitPitch);

        double tx = state.orbitTargetX;
        double ty = state.orbitTargetY;
        double tz = state.orbitTargetZ;
        double r = state.orbitRadius;

        state.localX = tx + r * sinAngle * cosPitch;
        state.localY = ty + r * sinPitch;
        state.localZ = tz + r * cosAngle * cosPitch;
        state.localHeightOffset = state.localY - state.anchorY;

        
        state.localYaw = Mth.wrapDegrees(180.0f - (float) Math.toDegrees(state.orbitAngle));
        state.localPitch = Mth.wrapDegrees((float) Math.toDegrees(state.orbitPitch));

        
        state.pendingRawRotateX = 0;
        state.pendingRawRotateY = 0;
        state.pendingPanX = 0;
        state.pendingPanY = 0;
        state.pendingScroll = 0;
    }
}
