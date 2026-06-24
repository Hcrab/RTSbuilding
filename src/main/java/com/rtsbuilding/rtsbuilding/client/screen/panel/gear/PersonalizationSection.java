package com.rtsbuilding.rtsbuilding.client.screen.panel.gear;

import com.rtsbuilding.rtsbuilding.client.screen.panel.base.util.SettingsSection;
import com.rtsbuilding.rtsbuilding.client.screen.panel.util.ThemeSwitchComponent;
import com.rtsbuilding.rtsbuilding.client.util.RtsClientUiUtil;
import com.rtsbuilding.rtsbuilding.client.util.ThemeManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * 个性化设置折叠分区——在设置面板中管理"个性化设置"分区的渲染和交互。
 *
 * <p>该分区目前包含以下 UI 元素：</p>
 * <ul>
 *   <li>主题标签（左对齐）：显示当前主题模式名称（亮色/暗色）</li>
 *   <li>主题开关（右对齐）：使用 {@link ThemeSwitchComponent} 实现的滑块式开关，
 *       点击可在亮/暗主题之间切换</li>
 * </ul>
 *
 * <p>布局说明：内容区域共 2 行空行（34px 高），主题开关垂直居中于内容区域，
 * 标签与开关同一行水平左右对齐。
 * 开关位置在渲染时缓存到 {@link #themeSwitchX} / {@link #themeSwitchY}，
 * 点击检测直接复用缓存值，确保渲染与点击坐标绝对一致。</p>
 */
public class PersonalizationSection extends SettingsSection {

    /** 主题开关组件实例——渲染和交互全部委托给该组件 */
    private final ThemeSwitchComponent themeSwitch = new ThemeSwitchComponent();

    /**
     * 最近渲染时主题开关左上角的屏幕坐标缓存。
     * 在 {@link #renderThemeSwitch} 中更新，在 {@link #onContentLineClick} 中直接复用，
     * 避免重复计算，同时保证点击检测与渲染位置绝对一致。
     */
    private int themeSwitchX, themeSwitchY;

    public PersonalizationSection() {
        super("screen.rtsbuilding.settings.category.personalization");
    }

    // ======================== 内容行 ========================

    /**
     * 返回内容行数组。
     * <p>此处返回 2 个空字符串，仅用于通过父类布局机制控制内容区域的高度。
     * 实际渲染内容（主题标签+开关）使用坐标定位，不依赖行文本。</p>
     *
     * @return 2 个空字符串，表示 2 行空白，总高度 = 2 × 14 + 6 = 34px
     */
    @Override
    protected String[] getContentLines() {
        return new String[] { "", "" };
    }

    // ======================== 渲染 ========================

    /**
     * 渲染分区内容——主题标签和主题开关。
     * <p>布局方式：</p>
     * <ol>
     *   <li>主题标签左对齐，垂直居中于内容区域</li>
     *   <li>主题开关右对齐，垂直居中于内容区域</li>
     * </ol>
     *
     * @param g      渲染上下文
     * @param mouseX 鼠标 X 坐标（传递给主题开关用于悬浮检测）
     * @param mouseY 鼠标 Y 坐标
     * @param x      内容区域左上角 X（不含左 padding）
     * @param y      内容区域左上角 Y（含标题栏偏移）
     * @param w      内容区域宽度
     * @param lines  内容行数组（此处仅取长度计算内容区域高度）
     */
    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w, String[] lines) {
        // 根据行数计算内容区域总高度：行数 × 行高 + 底部间距
        int contentH = lines.length * getLineHeight() + 6;

        // ======================== 主题标签（左对齐）=======================
        renderThemeLabel(g, x, y, contentH);

        // ======================== 主题开关（右对齐）=======================
        renderThemeSwitch(g, mouseX, mouseY, x, y, w, contentH);
    }

    /**
     * 渲染主题标签。
     * <p>标签文本通过 {@code ThemeManager} 获取当前主题模式，
     * 使用本地化键动态拼接，例如 "主题：亮色" 或 "主题：暗色"。</p>
     *
     * @param g         渲染上下文
     * @param x         内容区域左上角 X
     * @param y         内容区域左上角 Y
     * @param contentH  内容区域总高度（用于垂直居中定位）
     */
    private void renderThemeLabel(GuiGraphics g, int x, int y, int contentH) {
        // 获取当前主题模式
        boolean lightMode = ThemeManager.getInstance().isLightMode();
        // 根据当前主题选择对应的本地化键
        String modeKey = lightMode
                ? "screen.rtsbuilding.settings.theme.light"
                : "screen.rtsbuilding.settings.theme.dark";
        // 拼接完整标签文本："主题：亮色" 或 "主题：暗色"
        String label = Component.translatable(
                "screen.rtsbuilding.settings.category.personalization.theme",
                Component.translatable(modeKey)).getString();
        // 计算标签垂直居中位置（基于单行文本高度）
        int textY = y + (contentH - getLineHeight()) / 2;
        // 在左边缘向右偏移 6px 处绘制标签文本
        RtsClientUiUtil.drawUiText(g, label, x + 6, textY + 2, getTextColor());
    }

    /**
     * 渲染主题开关并缓存其位置。
     * <p>开关始终位于内容区域右边缘向左偏移 6px 处，
     * 垂直方向上居中于整个内容区域。
     * 渲染完成后将开关左上角坐标缓存到 {@link #themeSwitchX} / {@link #themeSwitchY}，
     * 供 {@link #onContentLineClick} 直接使用。</p>
     *
     * @param g         渲染上下文
     * @param mouseX    鼠标 X 坐标（传递给主题开关用于悬浮检测）
     * @param mouseY    鼠标 Y 坐标
     * @param x         内容区域左上角 X
     * @param y         内容区域左上角 Y
     * @param w         内容区域宽度
     * @param contentH  内容区域总高度（用于垂直居中定位）
     */
    private void renderThemeSwitch(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w, int contentH) {
        // 计算开关左上角 X：内容区域右边缘 - 开关尺寸 - 右边缘间距 6px
        this.themeSwitchX = x + w - ThemeSwitchComponent.SIZE - 6;
        // 计算开关左上角 Y：内容区域顶部 + (内容高度 - 开关高度) / 2（垂直居中）
        this.themeSwitchY = y + (contentH - ThemeSwitchComponent.SIZE) / 2;
        // 委托给 ThemeSwitchComponent 进行渲染（含悬浮高亮和滑动动画）
        themeSwitch.render(g, mouseX, mouseY, themeSwitchX, themeSwitchY);
    }

    // ======================== 交互 ========================

    /**
     * 处理内容行的点击事件。
     * <p>直接使用 {@link #renderThemeSwitch} 缓存的开关坐标进行点击检测，
     * 确保点击范围与视觉渲染位置完全一致。
     * 如果命中开关区域则委托给 {@link ThemeSwitchComponent#handleClick} 切换主题。</p>
     *
     * <p>点击检测采用坐标判定而非行索引判定，因为 ThemeSwitchComponent 是 32×32 的方形开关，
     * 高度可能跨越多个行边界，不适合用 lineIndex 进行检测。</p>
     *
     * @param lineIndex 被点击的行索引（此处未使用，采用坐标判定）
     * @param mouseX    鼠标点击 X 坐标
     * @param mouseY    鼠标点击 Y 坐标
     * @param contentX  内容区域左上角 X（此处未使用）
     * @param contentY  内容区域左上角 Y（此处未使用）
     * @param contentW  内容区域宽度（此处未使用）
     * @return true 如果点击已被消费（命中主题开关并执行了切换）
     */
    @Override
    protected boolean onContentLineClick(int lineIndex, double mouseX, double mouseY,
                                         int contentX, int contentY, int contentW) {
        // 直接复用渲染缓存的位置，确保点击检测与渲染位置绝对一致
        // 检测逻辑：鼠标坐标是否在开关的矩形区域内
        if (mouseX >= themeSwitchX && mouseX < themeSwitchX + ThemeSwitchComponent.SIZE
                && mouseY >= themeSwitchY && mouseY < themeSwitchY + ThemeSwitchComponent.SIZE) {
            // 命中开关区域，委托主题开关组件处理点击（切换主题）
            return themeSwitch.handleClick(mouseX, mouseY, themeSwitchX, themeSwitchY);
        }
        // 未命中任何可交互元素，返回 false 让父类继续处理
        return false;
    }
}
