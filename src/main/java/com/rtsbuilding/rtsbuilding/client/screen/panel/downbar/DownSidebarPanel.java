package com.rtsbuilding.rtsbuilding.client.screen.panel.downbar;

import com.rtsbuilding.rtsbuilding.client.screen.panel.base.RtsPanelApi;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.util.RtsClientUiUtil;
import com.rtsbuilding.rtsbuilding.common.persist.PersistableProperty;
import com.mojang.blaze3d.systems.RenderSystem;
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
