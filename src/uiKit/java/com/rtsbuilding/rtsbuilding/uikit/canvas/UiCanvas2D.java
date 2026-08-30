package com.rtsbuilding.rtsbuilding.uikit.canvas;

import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiColor;

/**
 * Core/Kit 渲染器可依赖的最小二维画布边界。
 *
 * <p>接口只表达矩形、无阴影文本、裁剪与局部变换；它不拥有 Minecraft
 * RenderType、共享缓冲区、物品渲染或世界渲染生命周期。平台适配器必须在调用侧
 * 完成坐标取整和颜色转换。</p>
 */
public interface UiCanvas2D {
    void fill(UiRect rect, UiColor color);

    /** 高频 chrome 路径可覆写此方法，避免为每个九宫格切片分配临时矩形。 */
    default void fill(double x, double y, double width, double height, UiColor color) {
        fill(new UiRect(x, y, width, height), color);
    }

    void text(String text, double x, double topY, UiColor color);

    void pushClip(UiRect clip);

    void popClip();

    void pushTransform();

    void popTransform();

    void translate(double x, double y);

    void scale(double x, double y);
}
