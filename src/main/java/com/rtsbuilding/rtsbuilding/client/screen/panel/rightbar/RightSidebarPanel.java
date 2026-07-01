package com.rtsbuilding.rtsbuilding.client.screen.panel.rightbar;

import com.rtsbuilding.rtsbuilding.client.screen.panel.base.RtsPanelApi;
import com.rtsbuilding.rtsbuilding.client.screen.panel.topbar.TopBarPanel;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.util.NineSliceRegion;
import com.rtsbuilding.rtsbuilding.client.util.RtsClientUiUtil;
import com.rtsbuilding.rtsbuilding.client.util.TextureInfo;
import com.rtsbuilding.rtsbuilding.common.persist.PersistableProperty;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Objects;

/**
 * 右边框——固定在屏幕右侧的装饰性边框。
 *
 * <p>60px 宽，从屏幕顶部延伸到屏幕底部，使用 {@code right_ui.png} 九宫格贴图绘制。
 * 贴图 32×32：左半暗色/右半亮色（双主题），上半正常态、下半为左边框点击态。
 * 点击左边框区域会切换为按下态贴图，松开后恢复。</p>
 */
public final class RightSidebarPanel implements RtsPanelApi {

    /** 所属的 BuilderScreen 引用，在 init() 中设置 */
    private BuilderScreen screen;

    /**
     * 当前右边框宽度（初始值使用 {@link RightSidebarLayoutHelper#SIDEBAR_WIDTH}）。
     * <p>后续可通过拖拽收缩/拉伸动态调整此值，其他面板通过 {@link #getCurrentWidth()} 获取。</p>
     */
    private int currentWidth = RightSidebarLayoutHelper.SIDEBAR_WIDTH;

    /**
     * 设置当前右边框宽度。
     */
    public void setCurrentWidth(int width) {
        this.currentWidth = Math.max(30, Math.min(width, this.screen != null ? this.screen.width / 4 : 2000));
    }

    /**
     * 返回当前右边框宽度，供其他组件（如 {@link TopBarPanel}）
     * 动态调整布局位置。
     */
    public int getCurrentWidth() {
        return currentWidth;
    }

    // ======================== 贴图资源 ========================

    /** 右边框背景贴图（32×32，左半暗色/右半亮色，上半正常/下半左边框点击态） */
    private static final ResourceLocation BORDER_TEXTURE = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/right/right_ui.png");
    /** 贴图文件总宽度（双主题横向翻倍） */
    private static final int TEX_W = 32;
    /** 贴图文件总高度（正常 + 点击态各 16px） */
    private static final int TEX_FILE_H = 32;
    /** 单个状态的高度（正常态 / 左边框点击态） */
    private static final int STATE_H = 16;
    /** 九宫格边框宽度 */
    private static final int BORDER = 2;
    private static final TextureInfo RIGHT_TEX_INFO = new TextureInfo(
            BORDER_TEXTURE, TEX_W, TEX_FILE_H,
            TextureInfo.ThemeLayout.HORIZONTAL_PAIR,
            TextureInfo.FilterMode.PIXEL);
    private static final NineSliceRegion RIGHT_NINE_SLICE = NineSliceRegion.fullTheme(
            RIGHT_TEX_INFO, STATE_H, BORDER);
    // ======================== 内嵌层贴图 ========================

    /** 右边栏内嵌层贴图（256×256，水平左暗右亮，垂直上正常下激活） */
    private static final ResourceLocation OVERLAY_TEXTURE = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/base/overlay_ui.png");
    /** 贴图文件总宽度 */
    private static final int OVERLAY_TEX_W = 256;
    /** 贴图文件总高度 */
    private static final int OVERLAY_TEX_FILE_H = 256;
    /** 单主题半区宽度 */
    private static final int OVERLAY_HALF_W = 128;
    /** 单个状态高度 */
    private static final int OVERLAY_STATE_H = 128;
    /** 鼠标位于区域内时使用的源 Y 偏移（下半部分） */
    private static final int OVERLAY_ACTIVE_V_OFFSET = 128;
    /** 九宫格边框宽度 */
    private static final int OVERLAY_BORDER = 8;
    private static final TextureInfo OVERLAY_TEX_INFO = new TextureInfo(
            OVERLAY_TEXTURE, OVERLAY_TEX_W, OVERLAY_TEX_FILE_H,
            TextureInfo.ThemeLayout.HORIZONTAL_PAIR,
            TextureInfo.FilterMode.PIXEL);
    private static final NineSliceRegion OVERLAY_NINE_SLICE = NineSliceRegion.fullTheme(
            OVERLAY_TEX_INFO, OVERLAY_STATE_H, OVERLAY_BORDER);

    /** 左边框拖拽缩放处理器 */
    private final RightSidebarResizeHandler resizeHandler = new RightSidebarResizeHandler();

    @Override
    public void init(BuilderScreen screen) {
        this.screen = Objects.requireNonNull(screen,
                "RightSidebarPanel.init() called with null screen");
    }

    // ======================== 布局快捷方法 ========================

    /** {@link RightSidebarLayoutHelper#sidebarRect} 的快捷调用，免去重复传参。 */
    private RightSidebarLayoutHelper.Rect layoutRect() {
        return RightSidebarLayoutHelper.sidebarRect(
                this.screen.width, this.screen.height, this.currentWidth);
    }

    // ======================== 渲染 ========================

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        RightSidebarLayoutHelper.Rect sb = layoutRect();

        int srcY = resizeHandler.isActive() ? STATE_H : 0;
        RtsClientUiUtil.drawNineSliceRegion(g, RIGHT_NINE_SLICE.withTheme().withVOffset(srcY),
                sb.x(), sb.y(), sb.width(), sb.height());
    }

    /**
     * 渲染内嵌层（overlay_ui.png）——由 BuilderScreen 在右边栏之上独立调用，作为装饰层。
     * <p>水平分为上下两个嵌层，中间间隔 1px。每块嵌层与 {@link ScreenBackgroundPanel} 相同贴图逻辑：
     * 水平左半=暗色主题、右半=亮色主题，由 {@link RtsClientUiUtil#drawNineSliceRegion} 自动切换；
     * 垂直上半=正常状态、下半=鼠标位于对应嵌层区域内时使用。</p>
     */
    public void renderOverlay(GuiGraphics g, int mouseX, int mouseY) {
        RightSidebarLayoutHelper.Rect sb = layoutRect();
        // 左边缩小 1px，让内嵌层与右边栏左边缘保持 1px 间距
        int ox = sb.x() + 1;
        int ow = sb.width() - 1;
        if (ow <= 0) return;

        int totalH = sb.height();
        int gap = 1;
        // 上嵌层 : 下嵌层 = 黄金比例（φ ≈ 1.618，取斐波那契近似 8:13）
        int upperH = (totalH - gap) * 8 / 21;

        // 上嵌层
        renderOverlayPart(g, ox, sb.y(), ow, upperH, mouseX, mouseY);
        // 下嵌层（中间间隔 1px）
        int bottomY = sb.y() + upperH + gap;
        renderOverlayPart(g, ox, bottomY, ow, totalH - upperH - gap, mouseX, mouseY);
    }

    /**
     * 渲染一块嵌层区域。
     *
     * @param ox      目标区域 X
     * @param oy      目标区域 Y
     * @param ow      目标区域宽度
     * @param oh      目标区域高度
     */
    private void renderOverlayPart(GuiGraphics g, int ox, int oy, int ow, int oh,
                                   int mouseX, int mouseY) {
        if (oh <= 0) return;

        boolean mouseInArea = (this.screen == null || !this.screen.isMouseOverUI(mouseX, mouseY))
                && mouseX >= ox && mouseX < ox + ow
                && mouseY >= oy && mouseY < oy + oh;
        int srcY = mouseInArea ? OVERLAY_ACTIVE_V_OFFSET : 0;

        RtsClientUiUtil.drawNineSliceRegion(g, OVERLAY_NINE_SLICE.withTheme().withVOffset(srcY),
                ox, oy, ow, oh);
    }

    // ======================== 交互：左边框拖拽缩放 ========================

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        RightSidebarLayoutHelper.Rect sb = layoutRect();
        return resizeHandler.tryBegin(mouseX, mouseY,
                sb.x(), sb.y(), sb.height(),
                currentWidth, this.screen.width);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        if (resizeHandler.isActive()) {
            resizeHandler.end();
            this.screen.persistUiState();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button != 0 || !resizeHandler.isActive()) return false;
        this.currentWidth = resizeHandler.computeWidth(mouseX);
        return true;
    }

    /**
     * 检测鼠标是否悬停在左边框缩放区域上（供 {@link BuilderScreen}
     * 更新光标样式用）。
     * <p>委托给 {@link RightSidebarResizeHandler#isOverLeftEdge}。</p>
     */
    public boolean isMouseOverLeftEdge(int mx, int my) {
        RightSidebarLayoutHelper.Rect sb = layoutRect();
        return resizeHandler.isOverLeftEdge(mx, my, sb.x(), sb.y(), sb.height());
    }

    @Override
    public List<PersistableProperty> persistableProperties() {
        String pk = "rightSidebar";
        return List.of(
                PersistableProperty.intField(
                        pk + ".width",
                        s -> s.rightSidebarWidth,
                        (s, v) -> s.rightSidebarWidth = v,
                        () -> this.currentWidth,
                        v -> this.currentWidth = v)
        );
    }
}
