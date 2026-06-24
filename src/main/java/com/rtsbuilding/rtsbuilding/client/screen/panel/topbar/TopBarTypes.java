package com.rtsbuilding.rtsbuilding.client.screen.panel.topbar;

import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.common.build.BuilderMode;

/**
 * 顶部栏数据类型容器。
 *
 * <p>将按钮标识枚举和布局参数记录组合在一起，
 * 二者始终由 {@link TopBarPanel} 和
 * {@link BuilderScreen} 共用。
 */
public final class TopBarTypes {

    /**
     * 顶部栏按钮标识枚举。
     * <p>定义顶部栏中所有可能的按钮类型，用于布局构建、图标渲染派发和点击事件路由。</p>
     */
    public enum TopBarButtonId {
    }

    /**
     * 顶部栏按钮布局参数（不可变）。
     *
     * @param id       按钮标识
     * @param x        按钮左边缘 X 坐标
     * @param width    按钮宽度（像素）
     * @param label    显示标签（仅图标按钮为空）
     * @param iconOnly true 表示该按钮绘制图标而非文本标签
     * @param active   true 表示按钮应高亮显示（已切换为打开状态）
     */
    public record TopBarButtonLayout(
            TopBarButtonId id,
            int x,
            int width,
            String label,
            boolean iconOnly,
            boolean active) {}

    /**
     * 将当前 {@link BuilderMode BuilderMode} 映射到用于高亮对应模式按钮的高层操作类别。
     */
    public enum TopAction {
    }

    private TopBarTypes() {}
}
