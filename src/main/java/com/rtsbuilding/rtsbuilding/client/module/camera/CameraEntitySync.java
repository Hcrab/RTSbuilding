package com.rtsbuilding.rtsbuilding.client.module.camera;

import com.rtsbuilding.rtsbuilding.common.entity.RtsCameraEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;

/**
 * 摄像机镜像实体同步器——管理 {@link RtsCameraEntity} 的生命周期与姿态同步。
 *
 * <p>职责：</p>
 * <ul>
 *   <li>确保每 tick 存在一个与 RTS 摄像机姿态一致的本地镜像实体</li>
 *   <li>通过 {@code mc.setCameraEntity()} 将摄像机实体设为主渲染视角</li>
 *   <li>10 tick 冷却防抖，防止频繁切换导致渲染抖动</li>
 * </ul>
 */
final class CameraEntitySync {

    private static final int RESTORE_COOLDOWN_TICKS = 10;

    /** 本地镜像摄像机实体 */
    RtsCameraEntity localMirrorCamera;
    /** 设置摄像机实体的冷却计数器 */
    int cameraRestoreCooldown;
    /** 缓存的实体类型 */
    private EntityType<RtsCameraEntity> cachedCameraEntityType;

    /**
     * 每 tick 同步实体位置和视角，必要时切换 {@code mc.cameraEntity}。
     * <p>仅在 {@link CameraModule#tick} 和开启相机时调用。</p>
     */
    void sync(Minecraft mc, CameraState state) {
        if (mc.level == null || !state.localReady) return;
        ensureMirrorCamera(mc);
        if (localMirrorCamera != null) {
            // 归一化 prevYaw 使其靠近 localYaw，避免跨 ±180° 边界时插值走远路
            // 例如 prevYaw=179°, localYaw=-1° 时，归一化为 -181°→实际存 -179° 等效角
            float normPrevYaw = state.localYaw + Mth.wrapDegrees(state.prevYaw - state.localYaw);
            localMirrorCamera.snapInterpolated(
                    state.prevX, state.prevY, state.prevZ, normPrevYaw, state.prevPitch,
                    state.localX, state.localY, state.localZ, state.localYaw, state.localPitch);
            // 传递轨道参数实现圆弧插值
            localMirrorCamera.setOrbitInterp(
                    state.prevOrbitAngle, state.prevOrbitPitch, state.prevOrbitRadius,
                    state.orbitAngle, state.orbitPitch, state.orbitRadius,
                    state.orbitTargetX, state.orbitTargetY, state.orbitTargetZ,
                    state.orbitMode);
            if (mc.getCameraEntity() != localMirrorCamera) {
                if (cameraRestoreCooldown <= 0) {
                    mc.setCameraEntity(localMirrorCamera);
                    cameraRestoreCooldown = RESTORE_COOLDOWN_TICKS;
                } else {
                    cameraRestoreCooldown--;
                }
            } else if (cameraRestoreCooldown > 0) {
                cameraRestoreCooldown--;
            }
        }
    }

    private void ensureMirrorCamera(Minecraft mc) {
        if (localMirrorCamera != null && localMirrorCamera.level() == mc.level) return;
        if (cachedCameraEntityType == null) {
            cachedCameraEntityType = (EntityType<RtsCameraEntity>) com.rtsbuilding.rtsbuilding.common.RtsEntities.RTS_CAMERA_ENTITY.get();
        }
        localMirrorCamera = new RtsCameraEntity(cachedCameraEntityType, mc.level);
    }

    /** 将当前姿态保存为上一帧插值基准。 */
    void savePrevState(CameraState state) {
        state.prevX = state.localX;
        state.prevY = state.localY;
        state.prevZ = state.localZ;
        state.prevYaw = state.localYaw;
        state.prevPitch = state.localPitch;
        // 保存轨道参数供 partialTick 圆弧插值
        state.prevOrbitAngle = state.orbitAngle;
        state.prevOrbitPitch = state.orbitPitch;
        state.prevOrbitRadius = state.orbitRadius;
    }

    /**
     * 帧率级快照——直接设置实体位置到当前状态值，跳过插值。
     * 由 {@link CameraModule#onRenderFrame} 在每帧渲染前调用，
     * 使用 {@link RtsCameraEntity#snapTo} 使 xo=x, yRotO=yRot，
     * 从而 {@code getEyePosition(partialTick)} 直接返回当前精确位置。
     */
    void snapToState(CameraState state) {
        if (localMirrorCamera != null) {
            localMirrorCamera.snapTo(state.localX, state.localY, state.localZ,
                    state.localYaw, state.localPitch);
        }
    }

    /** 清理实体引用和冷却状态。 */
    void clear() {
        localMirrorCamera = null;
        cameraRestoreCooldown = 0;
    }
}
