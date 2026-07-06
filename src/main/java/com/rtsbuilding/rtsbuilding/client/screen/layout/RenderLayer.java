package com.rtsbuilding.rtsbuilding.client.screen.layout;

/**
 * RTS BuilderScreen 渲染层级定义（从底到顶）。
 *
 * <p>该枚举形式化定义了 {@link com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen#render}
 * 方法的渲染顺序。每一层由 {@link PanelRegistry} 统一编排，新面板只需注册到对应层即可
 * 自动参与渲染，无需修改 BuilderScreen。</p>
 *
 * <pre>
 * 层级顺序：
 *   0. BASE_FILL      — 用不透明黑色填充整个屏幕，屏蔽世界渲染
 *   1. WORLD_CAPTURE  — 无人机捕获画面的 letterbox 渲染
 *   2. CONTENT_PANELS — 固定面板层（TopBar / LeftSidebar / RightSidebar / DownSidebar）
 *   3. DECORATION     — 九宫格装饰层（screen_ui / right_ui / down_ui 的 overlay）
 *   4. TOOLTIP        — 工具提示覆盖层
 *   5. FLOATING       — 浮窗面板（GearMenu / ColorPicker），永远在最顶层
 *   6. POST_RENDER    — 框选系统更新、光标样式等后处理
 * </pre>
 */
public enum RenderLayer {

    /** 0. 底层：黑色填充 */
    BASE_FILL,
    /** 1. 世界画面：无人机捕获帧 */
    WORLD_CAPTURE,
    /** 2. 内容面板：固定的 UI 边框与按钮 */
    CONTENT_PANELS,
    /** 3. 九宫格装饰层 */
    DECORATION,
    /** 4. 工具提示覆盖层 */
    TOOLTIP,
    /** 5. 浮窗面板层（最顶层） */
    FLOATING,
    /** 6. 后处理层（框选、光标等） */
    POST_RENDER
}
