package com.rtsbuilding.rtsbuilding.client.screen.panel.downbar;

import com.mojang.blaze3d.systems.RenderSystem;
import com.rtsbuilding.rtsbuilding.client.screen.panel.base.RtsPanelApi;
import com.rtsbuilding.rtsbuilding.client.screen.panel.rightbar.RightSidebarPanel;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.util.RtsClientUiUtil;
import com.rtsbuilding.rtsbuilding.common.persist.PersistableProperty;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Objects;

/**
 * 下边框——固定在屏幕底部的装饰性边框。
 *
 * <p>16px 高，从屏幕左边缘延伸到右边框左边缘，使用 {@code down_ui.png} 九宫格贴图绘制。
 * 贴图 32×32：左半暗色/右半亮色（双主题），上半正常态、下半拖拽态。</p>
 */
public final class DownSidebarPanel implements RtsPanelApi {

    /** 所属的 BuilderScreen 引用，在 init() 中设置 */
    private BuilderScreen screen;

    /**
     * 当前下边框高度（初始值使用 {@link DownSidebarLayoutHelper#DOWN_BAR_HEIGHT}）。
     * <p>后续可通过拖拽收缩/拉伸动态调整此值。</p>
     */
    private int currentHeight = DownSidebarLayoutHelper.DOWN_BAR_HEIGHT;

    /**
     * 设置当前下边框高度。
     */
    public void setCurrentHeight(int height) {
        this.currentHeight = Math.max(8, Math.min(height, this.screen != null ? this.screen.height / 4 : 2000));
    }

    /**
     * 返回当前下边框高度。
     */
    public int getCurrentHeight() {
        return currentHeight;
    }

    // ======================== 贴图资源 ========================

    /** 下边框背景贴图（32×32，左半暗色/右半亮色，上半正常/下半拖拽态） */
    private static final ResourceLocation BORDER_TEXTURE = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/down/down_ui.png");
    /** 贴图文件总宽度（双主题横向翻倍） */
    private static final int TEX_W = 32;
    /** 贴图文件总高度（正常 + 拖拽态各 16px） */
    private static final int TEX_FILE_H = 32;
    /** 单个状态的高度（正常态 / 拖拽态） */
    private static final int STATE_H = 16;
    /** 九宫格边框宽度 */
    private static final int BORDER = 2;
    // ======================== 内嵌层贴图 ========================

    /** 下栏内嵌层贴图（256×256，水平左暗右亮，垂直上正常下激活） */
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

    /** 上边缘拖拽缩放处理器 */
    private final DownSidebarResizeHandler resizeHandler = new DownSidebarResizeHandler();

    @Override
    public void init(BuilderScreen screen) {
        this.screen = Objects.requireNonNull(screen,
                "DownSidebarPanel.init() called with null screen");
    }

    // ======================== 布局快捷方法 ========================

    /** {@link DownSidebarLayoutHelper#downBarRect} 的快捷调用，免去重复传参。 */
    private DownSidebarLayoutHelper.Rect layoutRect() {
        return DownSidebarLayoutHelper.downBarRect(
                this.screen.width, this.screen.height, this.screen.getRightSidebarWidth(), this.currentHeight);
    }

    // ======================== 渲染 ========================

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        DownSidebarLayoutHelper.Rect db = layoutRect();
        if (db.width() <= 0 || db.height() <= 0) return;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        int halfW = TEX_W / 2;
        int srcY = resizeHandler.isActive() ? STATE_H : 0;
        RtsClientUiUtil.drawNineSliceRegion(g, BORDER_TEXTURE,
                db.x(), db.y(), db.width(), db.height(), BORDER,
                TEX_W, TEX_FILE_H,
                0, srcY, halfW, STATE_H);

        RenderSystem.disableBlend();
    }

    /**
     * 渲染内嵌层（overlay_ui.png）——由 BuilderScreen 在下栏之上独立调用，作为装饰层。
     * <p>与 {@link RightSidebarPanel} 相同贴图逻辑：
     * 水平左半=暗色主题、右半=亮色主题，由 {@link RtsClientUiUtil#drawNineSliceRegion} 自动切换；
     * 垂直上半=正常状态、下半=鼠标位于下栏区域内时使用。</p>
     */
    public void renderOverlay(GuiGraphics g, int mouseX, int mouseY) {
        DownSidebarLayoutHelper.Rect db = layoutRect();
        // 上边缩小 1px，让内嵌层与下栏上边缘保持 1px 间距
        int oy = db.y() + 1;
        int oh = db.height() - 1;
        if (db.width() <= 0 || oh <= 0) return;

        boolean mouseInArea = mouseX >= db.x() && mouseX < db.x() + db.width()
                && mouseY >= oy && mouseY < oy + oh;
        int srcY = mouseInArea ? OVERLAY_ACTIVE_V_OFFSET : 0;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        RtsClientUiUtil.drawNineSliceRegion(g, OVERLAY_TEXTURE,
                db.x(), oy, db.width(), oh, OVERLAY_BORDER,
                OVERLAY_TEX_W, OVERLAY_TEX_FILE_H,
                0, srcY, OVERLAY_HALF_W, OVERLAY_STATE_H);

        RenderSystem.disableBlend();
    }

    // ======================== 交互：上边缘拖拽缩放 ========================

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        DownSidebarLayoutHelper.Rect db = layoutRect();
        return resizeHandler.tryBegin(mouseX, mouseY,
                db.x(), db.y(), db.width(),
                currentHeight, this.screen.height);
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
        this.currentHeight = resizeHandler.computeHeight(mouseY);
        return true;
    }

    /**
     * 检测鼠标是否悬停在上边缘缩放区域上（供 {@link BuilderScreen}
     * 更新光标样式用）。
     * <p>委托给 {@link DownSidebarResizeHandler#isOverTopEdge}。</p>
     */
    public boolean isMouseOverTopEdge(int mx, int my) {
        DownSidebarLayoutHelper.Rect db = layoutRect();
        return resizeHandler.isOverTopEdge(mx, my, db.x(), db.y(), db.width());
    }

    @Override
    public List<PersistableProperty> persistableProperties() {
        String pk = "downSidebar";
        return List.of(
                PersistableProperty.intField(
                        pk + ".height",
                        s -> s.downSidebarHeight,
                        (s, v) -> s.downSidebarHeight = v,
                        () -> this.currentHeight,
                        v -> this.currentHeight = v)
        );
    }
}
