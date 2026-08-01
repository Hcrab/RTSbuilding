package com.rtsbuilding.rtsbuilding.client.screen.shape;

import com.rtsbuilding.rtsbuilding.common.shape.model.ShapeFillMode;

/**
 * 保存快速建造与范围破坏各自独立的形状控件状态。
 *
 * <p>本类只负责当前模式和两套模式快照之间的同步，不读取世界、不持久化配置，
 * 也不提交建造或破坏任务。这样模式切换规则可以脱离世界交互流程独立测试。</p>
 */
public final class ShapeModeState {
    private ShapeFillMode activeFillMode = ShapeFillMode.FILL;
    private boolean activeLineConnected;
    private int activeRotateDegrees;
    private ShapeFillMode buildFillMode = ShapeFillMode.FILL;
    private boolean buildLineConnected;
    private int buildRotateDegrees;
    private ShapeFillMode destroyFillMode = ShapeFillMode.FILL;
    private boolean destroyLineConnected;
    private int destroyRotateDegrees;
    private boolean destroyActive;

    public ShapeFillMode activeFillMode() {
        return this.activeFillMode;
    }

    public void setActiveFillMode(ShapeFillMode mode) {
        this.activeFillMode = requireMode(mode);
        syncActiveControls();
    }

    public boolean activeLineConnected() {
        return this.activeLineConnected;
    }

    public void setActiveLineConnected(boolean connected) {
        this.activeLineConnected = connected;
        syncActiveControls();
    }

    public int activeRotateDegrees() {
        return this.activeRotateDegrees;
    }

    public void setActiveRotateDegrees(int degrees) {
        this.activeRotateDegrees = normalizeDegrees(degrees);
        syncActiveRotation();
    }

    public ShapeFillMode buildFillMode() {
        return this.buildFillMode;
    }

    public void setBuildFillMode(ShapeFillMode mode) {
        this.buildFillMode = requireMode(mode);
        if (!this.destroyActive) {
            this.activeFillMode = this.buildFillMode;
        }
    }

    public boolean buildLineConnected() {
        return this.buildLineConnected;
    }

    public void setBuildLineConnected(boolean connected) {
        this.buildLineConnected = connected;
    }

    public int buildRotateDegrees() {
        return this.buildRotateDegrees;
    }

    public void setBuildRotateDegrees(int degrees) {
        this.buildRotateDegrees = normalizeDegrees(degrees);
    }

    public ShapeFillMode destroyFillMode() {
        return this.destroyFillMode;
    }

    public void setDestroyFillMode(ShapeFillMode mode) {
        this.destroyFillMode = requireMode(mode);
        if (this.destroyActive) {
            this.activeFillMode = this.destroyFillMode;
        }
    }

    public boolean destroyLineConnected() {
        return this.destroyLineConnected;
    }

    public void setDestroyLineConnected(boolean connected) {
        this.destroyLineConnected = connected;
    }

    public int destroyRotateDegrees() {
        return this.destroyRotateDegrees;
    }

    public void setDestroyRotateDegrees(int degrees) {
        this.destroyRotateDegrees = normalizeDegrees(degrees);
    }

    public boolean destroyActive() {
        return this.destroyActive;
    }

    /** 保存当前建造状态并恢复上一次范围破坏状态。 */
    public void switchToDestroy() {
        if (this.destroyActive) {
            return;
        }
        this.buildFillMode = this.activeFillMode;
        this.buildLineConnected = this.activeLineConnected;
        this.buildRotateDegrees = this.activeRotateDegrees;
        applyDestroyState();
    }

    /** 保存当前范围破坏状态并恢复上一次建造状态。 */
    public void switchToBuild() {
        if (!this.destroyActive) {
            return;
        }
        this.destroyFillMode = this.activeFillMode;
        this.destroyLineConnected = this.activeLineConnected;
        this.destroyRotateDegrees = this.activeRotateDegrees;
        applyBuildState();
    }

    /** 直接启用建造快照，不反向覆盖已经载入的两套配置。 */
    public void applyBuildState() {
        this.activeFillMode = this.buildFillMode;
        this.activeLineConnected = this.buildLineConnected;
        this.activeRotateDegrees = this.buildRotateDegrees;
        this.destroyActive = false;
    }

    /** 直接启用范围破坏快照，不反向覆盖已经载入的两套配置。 */
    public void applyDestroyState() {
        this.activeFillMode = this.destroyFillMode;
        this.activeLineConnected = this.destroyLineConnected;
        this.activeRotateDegrees = this.destroyRotateDegrees;
        this.destroyActive = true;
    }

    private void syncActiveControls() {
        if (this.destroyActive) {
            this.destroyFillMode = this.activeFillMode;
            this.destroyLineConnected = this.activeLineConnected;
        } else {
            this.buildFillMode = this.activeFillMode;
            this.buildLineConnected = this.activeLineConnected;
        }
    }

    private void syncActiveRotation() {
        if (this.destroyActive) {
            this.destroyRotateDegrees = this.activeRotateDegrees;
        } else {
            this.buildRotateDegrees = this.activeRotateDegrees;
        }
    }

    private static ShapeFillMode requireMode(ShapeFillMode mode) {
        if (mode == null) {
            throw new IllegalArgumentException("mode");
        }
        return mode;
    }

    private static int normalizeDegrees(int degrees) {
        return Math.floorMod(degrees, 360);
    }
}
