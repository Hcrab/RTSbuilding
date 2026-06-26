package com.rtsbuilding.rtsbuilding.client.screen.panel.rightbar;

import com.rtsbuilding.rtsbuilding.client.screen.panel.base.RtsPanelApi;
import com.rtsbuilding.rtsbuilding.client.screen.panel.topbar.TopBarPanel;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.util.RtsClientUiUtil;
import com.rtsbuilding.rtsbuilding.common.persist.PersistableProperty;
import com.mojang.blaze3d.systems.RenderSystem;
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

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        int halfW = TEX_W / 2;
        int srcY = resizeHandler.isActive() ? STATE_H : 0;
        RtsClientUiUtil.drawNineSliceRegion(g, BORDER_TEXTURE,
                sb.x(), sb.y(), sb.width(), sb.height(), BORDER,
                TEX_W, TEX_FILE_H,
                0, srcY, halfW, STATE_H);

        RenderSystem.disableBlend();
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
