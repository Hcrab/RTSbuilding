package com.rtsbuilding.rtsbuilding.client.screen.panel.downbar;

import com.rtsbuilding.rtsbuilding.client.screen.panel.base.api.RtsPanelApi;
import com.rtsbuilding.rtsbuilding.client.screen.panel.base.component.EdgeResizeHandler;
import com.rtsbuilding.rtsbuilding.client.screen.panel.base.overlay.DownOverlayLayer;
import com.rtsbuilding.rtsbuilding.client.screen.panel.downbar.overlay.LeftDownOverlayLayer;
import com.rtsbuilding.rtsbuilding.client.screen.panel.downbar.overlay.RightDownOverlayLayer;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.util.render.model.NineSliceRegion;
import com.rtsbuilding.rtsbuilding.client.util.render.model.TextureInfo;
import com.rtsbuilding.rtsbuilding.client.util.render.SpriteRenderer;
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
    private static final TextureInfo DOWN_TEX_INFO = new TextureInfo(
            BORDER_TEXTURE, TEX_W, TEX_FILE_H,
            TextureInfo.ThemeLayout.HORIZONTAL_PAIR,
            TextureInfo.FilterMode.PIXEL);
    private static final NineSliceRegion DOWN_NINE_SLICE = NineSliceRegion.fullTheme(
            DOWN_TEX_INFO, STATE_H, BORDER);

    /** 布局帮助类实例 */
    private final DownSidebarLayoutHelper layout = new DownSidebarLayoutHelper();

    /** 上边缘拖拽缩放处理器（垂直 LEADING 边） */
    private final EdgeResizeHandler resizeHandler = new EdgeResizeHandler(
            EdgeResizeHandler.Orientation.VERTICAL,
            EdgeResizeHandler.Side.LEADING,
            8);

    // ======================== 内嵌层实例 ========================

    /** 左嵌层 */
    private final LeftDownOverlayLayer leftLayer = new LeftDownOverlayLayer();
    /** 右嵌层 */
    private final RightDownOverlayLayer rightLayer = new RightDownOverlayLayer();

    // ======================== 嵌层分隔条拖拽状态 ========================

    /** 左嵌层宽度（像素），-1 表示使用默认黄金比例 */
    private int leftOverlayWidth = -1;

    /** 是否正在拖拽嵌层分隔条 */
    private boolean isDraggingOverlayDivider;

    /** 拖拽起始鼠标 X */
    private int dragOverlayDividerStartX;

    /** 拖拽起始左嵌层宽度 */
    private int dragOverlayDividerStartLeftW;

    /** 分隔条可点击区域半宽（以分隔线中心向两边延伸） */
    private static final int OVERLAY_DIVIDER_HALF_HIT = 2;

    /** 嵌层最小宽度 */
    private static final int OVERLAY_MIN_SIZE = 30;

    @Override
    public void init(BuilderScreen screen) {
        this.screen = Objects.requireNonNull(screen,
                "DownSidebarPanel.init() called with null screen");
    }

    /**
     * 计算基于黄金比例的默认左嵌层宽度。
     */
    private int defaultLeftOverlayWidth(int totalW) {
        int gap = 1;
        return Math.max(OVERLAY_MIN_SIZE, (totalW - gap) * 8 / 21);
    }

    /**
     * 将左嵌层宽度钳制到合法范围，确保左右嵌层均不小于最小尺寸。
     */
    private int clampLeftOverlayWidth(int w, int totalW) {
        int gap = 1;
        int maxLeft = totalW - gap - OVERLAY_MIN_SIZE;
        return Math.max(OVERLAY_MIN_SIZE, Math.min(maxLeft, w));
    }

    /**
     * 获取当前有效的左嵌层宽度（处理 -1 默认值并钳制范围）。
     */
    private int resolveLeftOverlayWidth() {
        DownSidebarLayoutHelper.Rect db = layoutRect();
        if (this.leftOverlayWidth <= 0) {
            return defaultLeftOverlayWidth(db.width());
        }
        return clampLeftOverlayWidth(this.leftOverlayWidth, db.width());
    }

    // ======================== 布局快捷方法 ========================

    /** {@link DownSidebarLayoutHelper#downBarRect} 的快捷调用，免去重复传参。 */
    private DownSidebarLayoutHelper.Rect layoutRect() {
        return layout.downBarRect(
                this.screen.width, this.screen.height, this.screen.getRightSidebarWidth(), this.currentHeight);
    }

    // ======================== 渲染 ========================

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        DownSidebarLayoutHelper.Rect db = layoutRect();
        if (db.width() <= 0 || db.height() <= 0) return;

        int srcY = resizeHandler.isActive() ? STATE_H : 0;
        SpriteRenderer.drawNineSlice(g, DOWN_NINE_SLICE.withTheme().withVOffset(srcY),
                db.x(), db.y(), db.width(), db.height());
    }

    /**
     * 渲染内嵌层——由 BuilderScreen 在下栏之上独立调用，作为装饰层。
     * <p>水平分为左右两个 {@link DownOverlayLayer}，中间间隔 1px。
     * 每个嵌层使用 Scissor 裁剪确保内容不溢出。</p>
     * <p>分隔条（左右嵌层之间的 1px 间隙）支持拖拽调整左右比例。</p>
     */
    @Override
    public void renderOverlays(GuiGraphics g, int mouseX, int mouseY) {
        DownSidebarLayoutHelper.Rect db = layoutRect();
        // 上边缩小 1px，让内嵌层与下栏上边缘保持 1px 间距
        int oy = db.y() + 1;
        int oh = db.height() - 1;
        if (db.width() <= 0 || oh <= 0) return;

        int totalW = db.width();
        int gap = 1;
        // 使用用户拖拽调整后的左嵌层宽度，未调整时使用默认黄金比例
        int leftW = resolveLeftOverlayWidth();

        // 更新左嵌层位置并渲染（拖拽分隔条时强制显示激活态）
        leftLayer.setBounds(db.x(), oy, leftW, oh);
        leftLayer.render(g, isDraggingOverlayDivider || isMouseInLayer(leftLayer, mouseX, mouseY));

        // 更新右嵌层位置并渲染（中间间隔 1px）
        int rightX = db.x() + leftW + gap;
        int rightW = totalW - leftW - gap;
        if (rightW > 0) {
            rightLayer.setBounds(rightX, oy, rightW, oh);
            rightLayer.render(g, isDraggingOverlayDivider || isMouseInLayer(rightLayer, mouseX, mouseY));
        }
    }

    /**
     * 检测鼠标是否位于嵌层区域内（排除 UI 覆盖区域）。
     */
    private boolean isMouseInLayer(DownOverlayLayer layer, int mouseX, int mouseY) {
        if (this.screen == null || this.screen.isMouseOverUI(mouseX, mouseY)) return false;
        return layer.contains(mouseX, mouseY);
    }

    /**
     * 公开方法：检测鼠标是否位于嵌层分隔条区域（供 {@link BuilderScreen} 更新光标用）。
     */
    public boolean isMouseOverOverlayDivider(int mx, int my) {
        return isMouseOverDownOverlayDivider(mx, my);
    }

    /**
     * 检测鼠标是否位于底部栏左/右嵌层分隔条的可点击区域上。
     */
    private boolean isMouseOverDownOverlayDivider(int mx, int my) {
        DownSidebarLayoutHelper.Rect db = layoutRect();
        if (db.width() <= 0 || db.height() <= 0) return false;
        // 垂直方向：在整个嵌层高度范围内
        if (my < db.y() + 1 || my >= db.y() + db.height() - 1) return false;
        int divX = overlayDividerX();
        return mx >= divX - OVERLAY_DIVIDER_HALF_HIT && mx < divX + OVERLAY_DIVIDER_HALF_HIT + 1;
    }

    /**
     * 分隔条的 X 坐标（位于左嵌层与右嵌层的 1px 间隙中心）。
     */
    private int overlayDividerX() {
        DownSidebarLayoutHelper.Rect db = layoutRect();
        int totalW = db.width();
        if (totalW <= 0) return 0;
        int leftW = resolveLeftOverlayWidth();
        // +1 = 左边 1px 内边距，leftW = 左嵌层宽度
        return db.x() + 1 + leftW;
    }

    // ======================== 交互：上边缘拖拽缩放 + 嵌层分隔条拖拽 ========================

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        int mx = (int) mouseX;
        int my = (int) mouseY;
        // 优先检测嵌层分隔条点击
        if (isMouseOverDownOverlayDivider(mx, my)) {
            isDraggingOverlayDivider = true;
            dragOverlayDividerStartX = mx;
            dragOverlayDividerStartLeftW = resolveLeftOverlayWidth();
            return true;
        }
        DownSidebarLayoutHelper.Rect db = layoutRect();
        return resizeHandler.tryBegin(mouseY, mouseX,
                db.y(), db.x(), db.width(),
                currentHeight, this.screen.height);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        if (isDraggingOverlayDivider) {
            isDraggingOverlayDivider = false;
            this.screen.persistUiState();
            return true;
        }
        if (resizeHandler.isActive()) {
            resizeHandler.end();
            this.screen.persistUiState();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button != 0) return false;
        if (isDraggingOverlayDivider) {
            int mx = (int) mouseX;
            int deltaX = mx - dragOverlayDividerStartX;
            int newLeftW = dragOverlayDividerStartLeftW + deltaX;
            DownSidebarLayoutHelper.Rect db = layoutRect();
            int totalW = db.width();
            if (totalW > 0) {
                this.leftOverlayWidth = clampLeftOverlayWidth(newLeftW, totalW);
            }
            return true;
        }
        if (!resizeHandler.isActive()) return false;
        this.currentHeight = resizeHandler.computeNewSize(mouseY);
        return true;
    }

    // ======================== 状态重置 ========================

    /**
     * 当屏幕大小变化导致布局失效时，重置嵌层分隔条拖拽状态。
     */
    public void resetOverlayDividerDrag() {
        isDraggingOverlayDivider = false;
    }

    /**
     * 检测鼠标是否悬停在上边缘缩放区域上（供 {@link BuilderScreen}
     * 更新光标样式用）。
     * <p>委托给 {@link EdgeResizeHandler#isOverEdge}。</p>
     */
    public boolean isMouseOverTopEdge(int mx, int my) {
        DownSidebarLayoutHelper.Rect db = layoutRect();
        return resizeHandler.isOverEdge(my, mx, db.y(), db.x(), db.width());
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
                        v -> this.currentHeight = v),
                PersistableProperty.intField(
                        pk + ".leftOverlayWidth",
                        s -> s.downSidebarLeftOverlayW,
                        (s, v) -> s.downSidebarLeftOverlayW = v,
                        () -> this.leftOverlayWidth,
                        v -> this.leftOverlayWidth = v)
        );
    }
}
