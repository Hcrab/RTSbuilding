package com.rtsbuilding.rtsbuilding.uipreview;

import com.rtsbuilding.rtsbuilding.uikit.performance.UiRenderStats;

import java.awt.image.BufferedImage;

/** 持有一次离屏渲染结果及其结构快照。 */
public final class UiPreviewResult implements AutoCloseable {
    private final BufferedImageUiCanvas canvas;
    private final UiPreviewLayout layout;

    UiPreviewResult(BufferedImageUiCanvas canvas, UiPreviewLayout layout) {
        this.canvas = canvas;
        this.layout = layout;
    }

    public BufferedImage image() {
        return canvas.image();
    }

    public UiPreviewLayout layout() {
        return layout;
    }

    public int primitiveCount() {
        return canvas.primitiveCount();
    }

    public int maximumNineSliceQuads() {
        return canvas.maximumNineSliceQuads();
    }

    public UiRenderStats.Snapshot statsSnapshot() {
        return canvas.statsSnapshot();
    }

    @Override
    public void close() {
        canvas.close();
    }
}
