package com.rtsbuilding.rtsbuilding.client.screen.handler;

import java.util.Objects;

/**
 * RTS UI 缩放帧管理。
 * <p>
 * 用于在缩放渲染/输入处理时临时修改屏幕尺寸，
 */
public final class RtsUiScaleFrame implements AutoCloseable {
    private final int oldW;
    private final int oldH;
    private final double scale;
    private final Runnable onClose;

    public RtsUiScaleFrame(int oldW, int oldH, double scale, Runnable onClose) {
        this.oldW = oldW;
        this.oldH = oldH;
        this.scale = scale;
        this.onClose = onClose;
    }

    public int oldW() { return oldW; }
    public int oldH() { return oldH; }
    public double scale() { return scale; }
    public Runnable onClose() { return onClose; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof RtsUiScaleFrame)) return false;
        RtsUiScaleFrame that = (RtsUiScaleFrame) other;
        return oldW == that.oldW && oldH == that.oldH
                && Double.compare(scale, that.scale) == 0
                && Objects.equals(onClose, that.onClose);
    }

    @Override public int hashCode() { return Objects.hash(oldW, oldH, scale, onClose); }
    @Override public String toString() {
        return "RtsUiScaleFrame[oldW=" + oldW + ", oldH=" + oldH
                + ", scale=" + scale + ", onClose=" + onClose + "]";
    }

    @Override
    public void close() {
        if (onClose != null) {
            onClose.run();
        }
    }
}
