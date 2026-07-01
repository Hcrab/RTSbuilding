package com.rtsbuilding.rtsbuilding.client.screen.panel.background;

import com.mojang.blaze3d.systems.RenderSystem;
import com.rtsbuilding.rtsbuilding.client.render.ViewCaptureService;
import com.rtsbuilding.rtsbuilding.client.screen.panel.base.RtsPanelApi;
import com.rtsbuilding.rtsbuilding.client.screen.panel.topbar.TopBarLayoutHelper;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * 屏幕背景面板——在 RTS 模式下显示无人机捕获的世界画面，回退到九宫格背景贴图。
 *
 * <p>当 {@link ViewCaptureService} 有有效捕获帧时，将捕获的无人机视角画面
 * 渲染为 BuilderScreen 的背景（底层）。否则使用九宫格背景贴图作为占位。</p>
 *
 * <p>贴图 screen_ui.png 为 256×256，水平左半=暗色主题、右半=亮色主题，
 * 由 {@link RtsClientUiUtil#drawNineSliceRegion} 自动根据当前主题切换；
 * 垂直上半=正常状态、下半=鼠标位于背景区域内时使用。
 * 背景区域从顶部栏上半部分底部（y=24）延伸到下边框顶部，
 * 从屏幕左边缘延伸到右边框左边缘，不遮挡任何交互元素。</p>
 */
public final class ScreenBackgroundPanel implements RtsPanelApi {

    private BuilderScreen screen;
    /** 九宫格渲染缓存（将平铺拼装结果缓存到纹理，每帧仅需一次 blit） */
    private final NineSliceCache cache = new NineSliceCache();

    // ======================== 贴图资源 ========================

    /** 屏幕背景九宫格贴图（256×256，水平左暗右亮，垂直上正常下激活）——无捕获帧时回退 */
    private static final ResourceLocation SCREEN_UI_TEXTURE = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/base/screen_ui.png");
    /** 贴图文件总宽度 */
    private static final int TEX_W = 256;
    /** 贴图文件总高度（256，垂直方向两个状态各 128） */
    private static final int TEX_FILE_H = 256;
    /** 单主题半区宽度 */
    private static final int HALF_W = TEX_W / 2;       // 128
    /** 单个状态高度 */
    private static final int STATE_H = 128;
    /** 鼠标位于背景区域内时使用的源 Y 偏移（下半部分） */
    private static final int ACTIVE_V_OFFSET = 128;
    /** 九宫格边框宽度 */
    private static final int BORDER = 8;
    private static final TextureInfo SCREEN_TEX_INFO = new TextureInfo(
            SCREEN_UI_TEXTURE, TEX_W, TEX_FILE_H,
            TextureInfo.ThemeLayout.HORIZONTAL_PAIR,
            TextureInfo.FilterMode.PIXEL);
    private static final NineSliceRegion SCREEN_NINE_SLICE = NineSliceRegion.fullTheme(
            SCREEN_TEX_INFO, STATE_H, BORDER);

    /** 背景起始 Y——顶部栏上半部分底部（与右边框顶部对齐） */
    public static final int BACKGROUND_TOP_Y = TopBarLayoutHelper.TOP_BAR_HEIGHT;

    /** 捕获画面渲染放大倍数（1.3 = 放大 1.3 倍，显示更近的视角） */
    public static final double CAPTURE_SCALE = 1.24;

    @Override
    public void init(BuilderScreen screen) {
        this.screen = screen;
    }

    // ======================== 渲染 ========================

    /**
     * render() 入口——保留供无捕获帧时使用，有捕获帧时由 BuilderScreen 直接调 renderCapturedFrameAt
     */
    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (this.screen == null) return;
        // 有捕获帧但没走 renderCapturedFrameAt 时回退到全屏（理论上不会发生）
        if (ViewCaptureService.hasValidFrame()) {
            renderCapturedFrameAt(g, 0, 0, this.screen.width, this.screen.height);
        }
    }


    /**
     * 在最上层渲染九宫格装饰层（screen_ui.png）。
     * <p>有捕获帧时作为装饰边框覆盖在所有面板之上，
     * 九宫格中心透明区域让捕获画面可见；
     * 无捕获帧时作为背景占位渲染在内容区域。</p>
     */
    public void renderOverlay(GuiGraphics g, int mouseX, int mouseY) {
        if (this.screen == null) return;

        renderNineSliceFallback(g, mouseX, mouseY);
    }

    /**
     * 渲染无人机捕获的世界画面——按原始比例居中缩放到目标区域，不拉伸。<p>
     * 捕获画面始终等比缩放，适配目标区域较短的边方向，另一方向留黑边（letterbox）。
     * 目标区域由调用方根据当前侧边栏尺寸动态计算。</p>
     *
     * @param destX  目标区域左上角 X
     * @param destY  目标区域左上角 Y
     * @param destW  目标区域宽度
     * @param destH  目标区域高度
     */
    public void renderCapturedFrameAt(GuiGraphics g, int destX, int destY, int destW, int destH) {
        int capW = ViewCaptureService.getCaptureWidth();
        int capH = ViewCaptureService.getCaptureHeight();
        if (capW <= 0 || capH <= 0 || destW <= 0 || destH <= 0) return;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        // 计算保持捕获画面比例的目标渲染矩形（居中 letterbox）
        double capAspect = (double) capW / capH;
        double destAspect = (double) destW / destH;

        int renderW, renderH, renderX, renderY;
        if (capAspect > destAspect) {
            // 捕获画面更宽 → 按宽度适配，上下留黑边
            renderW = destW;
            renderH = (int) Math.round(destW / capAspect);
            renderX = destX;
            renderY = destY + (destH - renderH) / 2;
        } else {
            // 捕获画面更高 → 按高度适配，左右留黑边
            renderH = destH;
            renderW = (int) Math.round(destH * capAspect);
            renderX = destX + (destW - renderW) / 2;
            renderY = destY;
        }

        // 应用放大倍数：放大后重新居中，超出内容区的部分被上层侧边栏/Scissor 裁剪
        renderW = (int) Math.round(renderW * CAPTURE_SCALE);
        renderH = (int) Math.round(renderH * CAPTURE_SCALE);
        renderX = destX + (destW - renderW) / 2;
        renderY = destY + (destH - renderH) / 2;

        // 先填充黑色背景（letterbox 黑边）
        if (renderX > destX || renderY > destY
                || renderX + renderW < destX + destW
                || renderY + renderH < destY + destH) {
            g.fill(destX, destY, destX + destW, destY + destH, 0xFF000000);
        }

        // 关闭 blend：捕获画面已是完整渲染场景（含雨雪、云层等半透明元素），
        // 直接覆盖黑底，避免半透明像素与黑底混合导致颜色异常
        RenderSystem.disableBlend();

        // 渲染捕获画面（保持原始比例，不拉伸）
        g.blit(ViewCaptureService.getCapturedFrameLocation(),
                renderX, renderY, renderW, renderH,
                0, 0, capW, capH,
                capW, capH);
    }

    /**
     * 无捕获帧时渲染九宫格背景贴图作为占位。
     * <p>见于 RTS 模式开启但首次捕获尚未完成时的短暂过渡。</p>
     */
    private void renderNineSliceFallback(GuiGraphics g, int mouseX, int mouseY) {
        // 未被遮挡区域：从顶部栏上半部分底部到下边框顶部，从左边缘到右边框左边缘
        int contentW = this.screen.width - this.screen.getRightSidebarWidth();
        int contentH = this.screen.height - BACKGROUND_TOP_Y - this.screen.getDownSidebarHeight();
        if (contentW <= 0 || contentH <= 0) return;

        // 判断鼠标是否在背景内容区域内（但若在浮动窗口/弹出菜单上则抑制，避免上层UI遮挡时误亮起）
        boolean mouseInArea = (this.screen == null || !this.screen.isMouseOverUI(mouseX, mouseY))
                && mouseX >= 0 && mouseX < contentW
                && mouseY >= BACKGROUND_TOP_Y && mouseY < BACKGROUND_TOP_Y + contentH;
        NineSliceRegion spec = mouseInArea
                ? SCREEN_NINE_SLICE.withVOffset(ACTIVE_V_OFFSET)
                : SCREEN_NINE_SLICE;

        cache.drawOrCache(g, spec.withTheme(),
                0, BACKGROUND_TOP_Y, contentW, contentH);
    }

    /**
     * 计算内容区域的虚拟坐标边界矩形。
     * <p>此区域即未被顶部栏、右边框和下边框遮挡的区域。
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
