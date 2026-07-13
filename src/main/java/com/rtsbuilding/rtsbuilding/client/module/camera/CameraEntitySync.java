package com.rtsbuilding.rtsbuilding.client.module.camera;

import com.rtsbuilding.rtsbuilding.common.entity.RtsCameraEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EntityType;

/**
 * 摄像机镜像实体同步器——管理 {@link RtsCameraEntity} 的生命周期与姿态同步。
 *
 * <p>职责：</p>
 * <ul>
 *   <li>确保存在一个与 RTS 摄像机姿态一致的本地镜像实体</li>
 *   <li>通过 {@code mc.setCameraEntity()} 将摄像机实体设为主渲染视角</li>
 *   <li>实体位置在 {@link CameraModule#onRenderFrame} 中帧率级更新，
 *       通过 {@link #snapToState} 直接设置 xo=x, yRotO=yRot，消除 20Hz tick 插值跳跃</li>
 * </ul>
 *
 * <p>注意：不再在 {@link CameraModule#tick} 中调用 sync()，实体仅由帧率级驱动。</p>
 */
final class CameraEntitySync {

    /** 本地镜像摄像机实体 */
    RtsCameraEntity localMirrorCamera;
    /** 缓存的实体类型 */
    private EntityType<RtsCameraEntity> cachedCameraEntityType;

    /**
     * 确保镜像实体存在——若为 null 或 level 变化则新建。
     * 可在 {@code enableCamera()} 或 {@code tick()} 中调用。
     */
    void ensureMirrorCamera(Minecraft mc) {
        if (mc.level == null) return;
        if (localMirrorCamera != null && localMirrorCamera.level() == mc.level) return;
        if (cachedCameraEntityType == null) {
            cachedCameraEntityType = (EntityType<RtsCameraEntity>) com.rtsbuilding.rtsbuilding.common.RtsEntities.RTS_CAMERA_ENTITY.get();
        }
        localMirrorCamera = new RtsCameraEntity(cachedCameraEntityType, mc.level);
    }

    /**
     * 将镜像实体设为主渲染视角。仅在 {@code enableCamera()} 中调用一次。
     */
    void setAsCameraEntity(Minecraft mc) {
        if (localMirrorCamera != null && mc.getCameraEntity() != localMirrorCamera) {
            mc.setCameraEntity(localMirrorCamera);
        }
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

    /** 清理实体引用。 */
    void clear() {
        localMirrorCamera = null;
    }
}
