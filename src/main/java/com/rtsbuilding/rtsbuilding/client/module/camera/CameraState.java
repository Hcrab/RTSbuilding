package com.rtsbuilding.rtsbuilding.client.module.camera;

/**
 * 相机状态——纯数据容器，无业务逻辑。
 * 由 {@link CameraModule} 统一管理，子组件通过包内可见字段直接访问。
 *
 * <p>本类仅包含摄像机运行时状态：位移、姿态、灵敏度、累积输入。
 * 视图管理（视角捕获/恢复）归 {@link CameraViewManager}，
 * 实体同步归 {@link CameraEntitySync}。</p>
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
    /** 连续灵敏度值（0.1 ~ 2.0，默认 1.0） */
    float inputSensitivity = 1.0F;
    boolean invertPanX, invertPanY;

    // Pending input (accumulated between ticks)
    float pendingPanX, pendingPanY;
    float pendingScroll;
    int pendingRotateSteps;
    float pendingRawRotateX, pendingRawRotateY;

    // ======================================================================
    //  Orbit mode state——绕目标方块圆周旋转
    // ======================================================================

    /** 是否启用轨道旋转模式 */
    boolean orbitMode;

    /** 轨道旋转水平角（弧度），绕目标水平旋转 */
    double orbitAngle;

    /** 轨道旋转俯仰角（弧度），控制上下角度 */
    double orbitPitch;

    /** 轨道半径——相机到目标方块的距离 */
    double orbitRadius;

    /** 目标方块中心 X */
    double orbitTargetX;

    /** 目标方块中心 Y（方块中心 = blockY + 0.5） */
    double orbitTargetY;

    /** 目标方块中心 Z */
    double orbitTargetZ;

    // Previous tick's orbit params（用于 partialTick 圆弧插值）
    double prevOrbitAngle, prevOrbitPitch, prevOrbitRadius;

    // ======================================================================
    //  Saved block orbit state——玩家环绕切换时保存/恢复
    // ======================================================================

    /** 进入玩家环绕前，方块环绕模式是否开启 */
    boolean savedBlockOrbitMode;
    double savedOrbitTargetX, savedOrbitTargetY, savedOrbitTargetZ;
    double savedOrbitAngle, savedOrbitPitch, savedOrbitRadius;

    // ======================================================================
    //  Public API (called by CameraModule)
    // ======================================================================

    public boolean isEnabled() { return this.enabled; }
    public boolean isLocalReady() { return this.localReady; }

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

    /** 是否处于轨道旋转模式（绕目标方块） */
    public boolean isOrbitMode() { return orbitMode; }

    /** 方块轨道环绕目标中心 X */
    public double getOrbitTargetX() { return orbitTargetX; }
    /** 设置方块轨道环绕目标中心 X */
    public void setOrbitTargetX(double v) { this.orbitTargetX = v; }
    /** 方块轨道环绕目标中心 Y */
    public double getOrbitTargetY() { return orbitTargetY; }
    /** 设置方块轨道环绕目标中心 Y */
    public void setOrbitTargetY(double v) { this.orbitTargetY = v; }
    /** 方块轨道环绕目标中心 Z */
    public double getOrbitTargetZ() { return orbitTargetZ; }
    /** 设置方块轨道环绕目标中心 Z */
    public void setOrbitTargetZ(double v) { this.orbitTargetZ = v; }

    /** 是否处于玩家环绕模式（绕玩家实体） */
    boolean playerOrbitMode;

    public boolean isPlayerOrbitMode() { return playerOrbitMode; }

    void setBounds(double x, double y, double z, double r) {
        this.anchorX = x;
        this.anchorY = y;
        this.anchorZ = z;
        this.maxRadius = r;
    }
}
