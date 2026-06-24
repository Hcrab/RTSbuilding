package com.rtsbuilding.rtsbuilding.client.module.camera;

import com.rtsbuilding.rtsbuilding.common.entity.RtsCameraEntity;
import net.minecraft.client.CameraType;
import net.minecraft.world.entity.Entity;

/**
 * 相机状态——纯数据容器，无业务逻辑。
 * 由 {@link CameraModule} 统一管理。
 */
public final class CameraState {

    // RTS toggle
    boolean enabled;
    boolean localReady;

    // Anchor & bounds
    double anchorX, anchorY, anchorZ;
    double maxRadius;

    // Local camera pose
    double localX, localY, localZ;
    double localHeightOffset;
    float localYaw, localPitch;

    // Previous tick's pose（用于 entity old→current partialTick 插值）
    double prevX, prevY, prevZ;
    float prevYaw, prevPitch;

    // Sensitivity & preferences
    float rotateSensitivity = 5.00F;
    int inputSensitivityIndex = 2;
    /** 连续灵敏度值（0.1 ~ 2.0，默认 1.0），替代 inputSensitivityIndex 的离散预设方案 */
    float inputSensitivity = 1.0F;
    boolean invertPanX, invertPanY;

    // Pending input (accumulated between ticks)
    float pendingPanX, pendingPanY;
    float pendingScroll;
    int pendingRotateSteps;
    float pendingRawRotateX, pendingRawRotateY;

    // Mirror camera entity
    RtsCameraEntity localMirrorCamera;

    // Previous view restoration
    Entity prevCameraEntity;
    CameraType prevCameraType = CameraType.FIRST_PERSON;
    boolean prevBobView = true;
    double prevFovScale = 1.0D;

    // ======================================================================
    //  Public API (called by CameraModule)
    // ======================================================================

    public boolean isEnabled() {
        return this.enabled;
    }

    public boolean isLocalReady() {
        return this.localReady;
    }

    public double getLocalX() { return localX; }
    public double getLocalY() { return localY; }
    public double getLocalZ() { return localZ; }
    public double getHeightOffset() { return localHeightOffset; }
    public float getYaw() { return localYaw; }
    public float getPitch() { return localPitch; }

    public double getAnchorX() { return anchorX; }
    public double getAnchorY() { return anchorY; }
    public double getAnchorZ() { return anchorZ; }
    public double getMaxRadius() { return maxRadius; }

    public boolean isInvertPanX() { return invertPanX; }
    public boolean isInvertPanY() { return invertPanY; }

    public void setInvertPanX(boolean v) { this.invertPanX = v; }
    public void setInvertPanY(boolean v) { this.invertPanY = v; }
    public void toggleInvertPanX() { this.invertPanX = !this.invertPanX; }
    public void toggleInvertPanY() { this.invertPanY = !this.invertPanY; }

    void setBounds(double x, double y, double z, double r) {
        this.anchorX = x;
        this.anchorY = y;
        this.anchorZ = z;
        this.maxRadius = r;
    }
}
