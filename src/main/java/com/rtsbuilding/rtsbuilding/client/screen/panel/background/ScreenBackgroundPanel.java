package com.rtsbuilding.rtsbuilding.client.screen.panel.background;

import com.mojang.blaze3d.systems.RenderSystem;
import com.rtsbuilding.rtsbuilding.client.screen.panel.base.RtsPanelApi;
import com.rtsbuilding.rtsbuilding.client.screen.panel.topbar.TopBarLayoutHelper;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.util.RtsClientUiUtil;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * 屏幕背景面板——在未被顶部栏、右边框和下边框遮挡的区域渲染九宫格背景贴图。
 *
 * <p>贴图 screen_ui.png 为 256×128，左半=暗色主题，右半=亮色主题，
 * 由 {@link RtsClientUiUtil#drawNineSliceRegion} 自动根据当前主题切换。
 * 背景区域从顶部栏上半部分底部（y=24）延伸到下边框顶部，
 * 从屏幕左边缘延伸到右边框左边缘，不遮挡任何交互元素。</p>
 */
public final class ScreenBackgroundPanel implements RtsPanelApi {

    private BuilderScreen screen;

    // ======================== 贴图资源 ========================

    /** 屏幕背景九宫格贴图（256×128，左暗右亮） */
    private static final ResourceLocation SCREEN_UI_TEXTURE = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/base/screen_ui.png");
    /** 贴图文件总宽度 */
    private static final int TEX_W = 256;
    /** 贴图文件总高度 */
    private static final int TEX_FILE_H = 128;
    /** 单主题半区宽度 */
    private static final int HALF_W = TEX_W / 2;       // 128
    /** 单个状态高度（仅一个状态，等于文件高度） */
    private static final int STATE_H = TEX_FILE_H;      // 128
    /** 九宫格边框宽度 */
    private static final int BORDER = 8;

    /** 背景起始 Y——顶部栏上半部分底部（与右边框顶部对齐） */
    public static final int BACKGROUND_TOP_Y = TopBarLayoutHelper.TOP_BAR_HEIGHT;

    @Override
    public void init(BuilderScreen screen) {
        this.screen = screen;
    }

    // ======================== 渲染 ========================

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (this.screen == null) return;

        // 未被遮挡区域：从顶部栏上半部分底部到下边框顶部，从左边缘到右边框左边缘
        int contentW = this.screen.width - this.screen.getRightSidebarWidth();
        int contentH = this.screen.height - BACKGROUND_TOP_Y - this.screen.getDownSidebarHeight();
        if (contentW <= 0 || contentH <= 0) return;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        // drawNineSliceRegion 自动根据主题偏移到左半区（暗色）或右半区（亮色）
        RtsClientUiUtil.drawNineSliceRegion(g, SCREEN_UI_TEXTURE,
                0, BACKGROUND_TOP_Y, contentW, contentH, BORDER,
                TEX_W, TEX_FILE_H,
                0, 0, HALF_W, STATE_H);

        RenderSystem.disableBlend();
    }

    /**
     * 计算内容区域的虚拟坐标边界矩形。
     * <p>此区域即未被顶部栏、右边框和下边框遮挡、渲染九宫格背景贴图的区域。
     * 摄像机拖拽鼠标环绕逻辑使用此边界来判断是否需要绕环。</p>
     */
    public static ContentBounds contentBounds(BuilderScreen screen) {
        int contentW = screen.width - screen.getRightSidebarWidth();
        int contentH = screen.height - BACKGROUND_TOP_Y - screen.getDownSidebarHeight();
        return new ContentBounds(0, BACKGROUND_TOP_Y, Math.max(contentW, 0), Math.max(contentH, 0));
    }

    /**
     * 内容区域不可变边界。
     *
     * @param left   左边界（虚拟坐标，恒为 0）
     * @param top    上边界（虚拟坐标，等于 BACKGROUND_TOP_Y）
     * @param width  内容区域宽度
     * @param height 内容区域高度（已扣除下边框高度）
     */
    public record ContentBounds(int left, int top, int width, int height) {
        public int right() { return left + width; }
        public int bottom() { return top + height; }
    }

    // ======================== 无交互 ========================

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }

    @Override
    public List<com.rtsbuilding.rtsbuilding.common.persist.PersistableProperty> persistableProperties() {
        return List.of();
    }
}
