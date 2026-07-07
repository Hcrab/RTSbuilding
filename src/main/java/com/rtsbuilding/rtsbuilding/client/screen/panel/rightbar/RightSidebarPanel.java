package com.rtsbuilding.rtsbuilding.client.screen.panel.rightbar;

import com.rtsbuilding.rtsbuilding.client.screen.panel.base.overlay.DownOverlayLayer;
import com.rtsbuilding.rtsbuilding.client.screen.panel.base.api.RtsPanelApi;
import com.rtsbuilding.rtsbuilding.client.screen.panel.base.component.EdgeResizeHandler;
import com.rtsbuilding.rtsbuilding.client.screen.panel.rightbar.overlay.LowerRightOverlayLayer;
import com.rtsbuilding.rtsbuilding.client.screen.panel.rightbar.overlay.UpperRightOverlayLayer;
import com.rtsbuilding.rtsbuilding.client.screen.panel.topbar.TopBarPanel;
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

    /** 布局帮助类实例 */
    private final RightSidebarLayoutHelper layout = new RightSidebarLayoutHelper();

    /** 左边框拖拽缩放处理器（水平 LEADING 边） */
    private final EdgeResizeHandler resizeHandler = new EdgeResizeHandler(
            EdgeResizeHandler.Orientation.HORIZONTAL,
            EdgeResizeHandler.Side.LEADING,
            20);

    // ======================== 内嵌层实例 ========================

    /** 上嵌层 */
    private final UpperRightOverlayLayer upperLayer = new UpperRightOverlayLayer();
    /** 下嵌层 */
    private final LowerRightOverlayLayer lowerLayer = new LowerRightOverlayLayer();

    // ======================== 嵌层分隔条拖拽状态 ========================

    /** 上嵌层高度（像素），-1 表示使用默认黄金比例 */
    private int upperOverlayHeight = -1;

    /** 是否正在拖拽嵌层分隔条 */
    private boolean isDraggingOverlayDivider;

    /** 拖拽起始鼠标 Y */
    private int dragOverlayDividerStartY;

    /** 拖拽起始上嵌层高度 */
    private int dragOverlayDividerStartUpperH;

    /** 分隔条可点击区域半高（以分隔线中心向两边延伸） */
    private static final int OVERLAY_DIVIDER_HALF_HIT = 2;

    /** 嵌层最小高度 */
    private static final int OVERLAY_MIN_SIZE = 20;

    @Override
    public void init(BuilderScreen screen) {
        this.screen = Objects.requireNonNull(screen,
                "RightSidebarPanel.init() called with null screen");
    }

    /**
     * 计算基于黄金比例的默认上嵌层高度。
     */
    private int defaultUpperOverlayHeight(int totalH) {
        int gap = 1;
        return Math.max(OVERLAY_MIN_SIZE, (totalH - gap) * 8 / 21);
    }

    /**
     * 将上嵌层高度钳制到合法范围，确保上下嵌层均不小于最小尺寸。
     */
    private int clampUpperOverlayHeight(int h, int totalH) {
        int gap = 1;
        int maxUpper = totalH - gap - OVERLAY_MIN_SIZE;
        return Math.max(OVERLAY_MIN_SIZE, Math.min(maxUpper, h));
    }

    /**
     * 获取当前有效的上嵌层高度（处理 -1 默认值并钳制范围）。
     */
    private int resolveUpperOverlayHeight() {
        RightSidebarLayoutHelper.Rect sb = layoutRect();
        if (this.upperOverlayHeight <= 0) {
            return defaultUpperOverlayHeight(sb.height());
        }
        return clampUpperOverlayHeight(this.upperOverlayHeight, sb.height());
    }

    // ======================== 布局快捷方法 ========================

    /** {@link RightSidebarLayoutHelper#sidebarRect} 的快捷调用，免去重复传参。 */
    private RightSidebarLayoutHelper.Rect layoutRect() {
        return layout.sidebarRect(
                this.screen.width, this.screen.height, this.currentWidth);
    }

    // ======================== 渲染 ========================

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        RightSidebarLayoutHelper.Rect sb = layoutRect();

        int srcY = resizeHandler.isActive() ? STATE_H : 0;
        SpriteRenderer.drawNineSlice(g, RIGHT_NINE_SLICE.withTheme().withVOffset(srcY),
                sb.x(), sb.y(), sb.width(), sb.height());
    }

    /**
     * 渲染内嵌层——由 BuilderScreen 在右边栏之上独立调用，作为装饰层。
     * <p>垂直分为上下两个 {@link DownOverlayLayer}，中间间隔 1px。
     * 每个嵌层使用 Scissor 裁剪确保内容不溢出。</p>
     * <p>分隔条（上下嵌层之间的 1px 间隙）支持拖拽调整上下比例。</p>
     */
    @Override
    public void renderOverlays(GuiGraphics g, int mouseX, int mouseY) {
        RightSidebarLayoutHelper.Rect sb = layoutRect();
        // 左边缩小 1px，让内嵌层与右边栏左边缘保持 1px 间距
        int ox = sb.x() + 1;
        int ow = sb.width() - 1;
        if (ow <= 0) return;

        int totalH = sb.height();
        int gap = 1;
        // 使用用户拖拽调整后的上嵌层高度，未调整时使用默认黄金比例
        int upperH = resolveUpperOverlayHeight();

        // 更新上嵌层位置并渲染（拖拽分隔条时强制显示激活态）
        upperLayer.setBounds(ox, sb.y(), ow, upperH);
        upperLayer.render(g, isDraggingOverlayDivider || isMouseInLayer(upperLayer, mouseX, mouseY));

        // 更新下嵌层位置并渲染（中间间隔 1px）
        int bottomY = sb.y() + upperH + gap;
        int lowerH = totalH - upperH - gap;
        if (lowerH > 0) {
            lowerLayer.setBounds(ox, bottomY, ow, lowerH);
            lowerLayer.render(g, isDraggingOverlayDivider || isMouseInLayer(lowerLayer, mouseX, mouseY));
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
        return isMouseOverRightOverlayDivider(mx, my);
    }

    /**
     * 检测鼠标是否位于右边栏上/下嵌层分隔条的可点击区域上。
     */
    private boolean isMouseOverRightOverlayDivider(int mx, int my) {
        RightSidebarLayoutHelper.Rect sb = layoutRect();
        if (sb.width() <= 0 || sb.height() <= 0) return false;
        // 水平方向：在整个嵌层宽度范围内
        int ox = sb.x() + 1;
        int ow = sb.width() - 1;
        if (mx < ox || mx >= ox + ow) return false;
        int divY = overlayDividerY();
        return my >= divY - OVERLAY_DIVIDER_HALF_HIT && my < divY + OVERLAY_DIVIDER_HALF_HIT + 1;
    }

    /**
     * 分隔条的 Y 坐标（位于上嵌层与下嵌层的 1px 间隙中心）。
     */
    private int overlayDividerY() {
        RightSidebarLayoutHelper.Rect sb = layoutRect();
        int totalH = sb.height();
        if (totalH <= 0) return 0;
        int upperH = resolveUpperOverlayHeight();
        return sb.y() + upperH;
    }

    // ======================== 交互：左边框拖拽缩放 + 嵌层分隔条拖拽 ========================

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        int mx = (int) mouseX;
        int my = (int) mouseY;
        // 优先检测嵌层分隔条点击
        if (isMouseOverRightOverlayDivider(mx, my)) {
            isDraggingOverlayDivider = true;
            dragOverlayDividerStartY = my;
            dragOverlayDividerStartUpperH = resolveUpperOverlayHeight();
            return true;
        }
        RightSidebarLayoutHelper.Rect sb = layoutRect();
        return resizeHandler.tryBegin(mouseX, mouseY,
                sb.x(), sb.y(), sb.height(),
                currentWidth, this.screen.width);
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
            int my = (int) mouseY;
            int deltaY = my - dragOverlayDividerStartY;
            int newUpperH = dragOverlayDividerStartUpperH + deltaY;
            RightSidebarLayoutHelper.Rect sb = layoutRect();
            int totalH = sb.height();
            if (totalH > 0) {
                this.upperOverlayHeight = clampUpperOverlayHeight(newUpperH, totalH);
            }
            return true;
        }
        if (!resizeHandler.isActive()) return false;
        this.currentWidth = resizeHandler.computeNewSize(mouseX);
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
     * 检测鼠标是否悬停在左边框缩放区域上（供 {@link BuilderScreen}
     * 更新光标样式用）。
     * <p>委托给 {@link EdgeResizeHandler#isOverEdge}。</p>
     */
    public boolean isMouseOverLeftEdge(int mx, int my) {
        RightSidebarLayoutHelper.Rect sb = layoutRect();
        return resizeHandler.isOverEdge(mx, my, sb.x(), sb.y(), sb.height());
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
                        v -> this.currentWidth = v),
                PersistableProperty.intField(
                        pk + ".upperOverlayHeight",
                        s -> s.rightSidebarUpperOverlayH,
                        (s, v) -> s.rightSidebarUpperOverlayH = v,
                        () -> this.upperOverlayHeight,
                        v -> this.upperOverlayHeight = v)
        );
    }
}
