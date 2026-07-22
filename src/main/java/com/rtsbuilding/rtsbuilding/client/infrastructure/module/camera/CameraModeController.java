package com.rtsbuilding.rtsbuilding.client.infrastructure.module.camera;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.BlockHitResult;

public final class CameraModeController {

    private final CameraState state;
    private final CameraPoseComputer poseComputer;
    private final PlayerOrbitCameraMode playerOrbit;

    public CameraModeController(CameraState state, CameraPoseComputer poseComputer,
                                PlayerOrbitCameraMode playerOrbit) {
        this.state = state;
        this.poseComputer = poseComputer;
        this.playerOrbit = playerOrbit;
    }

    // ======================================================================
    //  Orbit mode — 相机绕着目标方块圆周旋转
    // ======================================================================

    public boolean enableOrbitMode() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK) {
            BlockHitResult hit = (BlockHitResult) mc.hitResult;
            BlockPos pos = hit.getBlockPos();
            state.orbitTargetX = pos.getX() + 0.5;
            state.orbitTargetY = pos.getY() + 0.5;
            state.orbitTargetZ = pos.getZ() + 0.5;
        } else {
            state.orbitTargetX = state.anchorX;
            state.orbitTargetY = state.anchorY;
            state.orbitTargetZ = state.anchorZ;
        }
        poseComputer.initOrbitPose(state, state.localX, state.localY, state.localZ);
        state.orbitMode = true;
        return true;
    }

    public boolean enableOrbitMode(BlockPos pos) {
        if (pos == null) return enableOrbitMode();
        state.orbitTargetX = pos.getX() + 0.5;
        state.orbitTargetY = pos.getY() + 0.5;
        state.orbitTargetZ = pos.getZ() + 0.5;
        poseComputer.initOrbitPose(state, state.localX, state.localY, state.localZ);
        state.orbitMode = true;
        return true;
    }

    public void disableOrbitMode() {
        state.orbitMode = false;
    }

    public boolean toggleOrbitMode() {
        if (state.orbitMode) {
            disableOrbitMode();
            return false;
        }
        return enableOrbitMode();
    }

    public boolean isOrbitMode() {
        return state.orbitMode;
    }

    public void restoreOrbitMode(double targetX, double targetY, double targetZ) {
        state.orbitTargetX = targetX;
        state.orbitTargetY = targetY;
        state.orbitTargetZ = targetZ;
        poseComputer.initOrbitPose(state, state.localX, state.localY, state.localZ);
        state.orbitMode = true;
    }

    // ======================================================================
    //  Player orbit mode — 绕玩家实体环绕旋转
    // ======================================================================

    public boolean enablePlayerOrbitMode() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;

        state.savedBlockOrbitMode = state.orbitMode;
        if (state.orbitMode) {
            state.savedOrbitTargetX = state.orbitTargetX;
            state.savedOrbitTargetY = state.orbitTargetY;
            state.savedOrbitTargetZ = state.orbitTargetZ;
            state.savedOrbitAngle = state.orbitAngle;
            state.savedOrbitPitch = state.orbitPitch;
            state.savedOrbitRadius = state.orbitRadius;
        }
        state.orbitMode = false;

        playerOrbit.init(state);
        state.playerOrbitMode = true;
        return true;
    }

    public void disablePlayerOrbitMode() {
        state.playerOrbitMode = false;
        if (state.savedBlockOrbitMode && !state.orbitMode) {
            state.orbitTargetX = state.savedOrbitTargetX;
            state.orbitTargetY = state.savedOrbitTargetY;
            state.orbitTargetZ = state.savedOrbitTargetZ;
            state.orbitAngle = state.savedOrbitAngle;
            state.orbitPitch = state.savedOrbitPitch;
            state.orbitRadius = state.savedOrbitRadius;
            state.orbitMode = true;
            poseComputer.initOrbitPose(state, state.localX, state.localY, state.localZ);
        }
        state.savedBlockOrbitMode = false;
    }

    public boolean togglePlayerOrbitMode() {
        if (state.playerOrbitMode) {
            disablePlayerOrbitMode();
            return false;
        }
        return enablePlayerOrbitMode();
    }

    public boolean isPlayerOrbitMode() {
        return state.playerOrbitMode;
    }

    public void clearModeState() {
        state.orbitMode = false;
        state.playerOrbitMode = false;
        state.savedBlockOrbitMode = false;
    }
}
