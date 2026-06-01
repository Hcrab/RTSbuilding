package com.rtsbuilding.rtsbuilding.client.screen;

/**
 * RTS UI 缩放帧管理。
 * <p>
 * 用于在缩放渲染/输入处理时临时修改屏幕尺寸，
 * 在 {@link #close()} 中自动恢复原始尺寸。
 */
public record RtsUiScaleFrame(int oldW, int oldH, double scale, Runnable onClose) implements AutoCloseable {

    @Override
    public void close() {
        if (onClose != null) {
            onClose.run();
        }
    }
}
