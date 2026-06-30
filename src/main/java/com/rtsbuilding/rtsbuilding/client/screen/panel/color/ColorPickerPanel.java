package com.rtsbuilding.rtsbuilding.client.screen.panel.color;

import com.rtsbuilding.rtsbuilding.client.render.pass.BoundaryPass;
import com.rtsbuilding.rtsbuilding.client.screen.panel.base.RtsPanel;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.util.RtsClientUiUtil;
import com.rtsbuilding.rtsbuilding.client.util.ThemeManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * 调色盘面板——提供颜色轮盘 + 预设色块的屏障颜色选择器。
 *
 * <p>浮动窗口面板，继承 {@link RtsPanel}，由 {@link BuilderScreen} 管理。
 * 点击预设色块或颜色轮盘区域来修改 {@link BoundaryPass#barrierColor}。</p>
 */
public class ColorPickerPanel extends RtsPanel {

    // ======================== 面板尺寸 ========================

    private static final int PANEL_W = 197;
    private static final int PANEL_H = 250;

    // ======================== 颜色轮盘贴图 ========================

    private static final ResourceLocation COLOR_WHEEL_TEXTURE = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/color/colorwheel.png");
    private static final int COLOR_WHEEL_TEX_W = 89;
    private static final int COLOR_WHEEL_TEX_H = 89;
    /** 颜色轮盘在面板内的绘制尺寸 */
    private static final int WHEEL_DRAW_SIZE = 95;
    /** 颜色轮盘区域的外边距（给浮窗背景留空间） */
    private static final int WHEEL_PAD = 3;

    // ======================== 布局常量 ========================

    /** 当前颜色预览条高度 */
    private static final int PREVIEW_BAR_H = 16;
    /** 色块绘制尺寸 */
    private static final int SWATCH_SIZE = 17;
    /** 色块间距 */
    private static final int SWATCH_GAP = 3;
    /** 每行预设色块数 */
    private static final int SWATCHES_PER_ROW = 8;
    /** 预设色块行数 */
    private static final int SWATCH_ROWS = 2;

    // ======================== 预设颜色（ARGB） ========================

    private static final int[] PRESET_COLORS = {
            0xFFFF0000, // 红
            0xFFFF9800, // 橙
            0xFFFFEB3B, // 黄
            0xFF4CAF50, // 绿
            0xFF2196F3, // 蓝
            0xFF3F51B5, // 靛蓝
            0xFF9C27B0, // 紫
            0xFFE91E63, // 粉
            // 第二行
            0xFF795548, // 棕
            0xFF607D8B, // 灰蓝
            0xFF000000, // 黑
            0xFF666666, // 深灰
            0xFFAAAAAA, // 浅灰
            0xFFFFFFFF, // 白
            0xFFFFCC00, // 金黄
            0xFF00BCD4, // 青
    };

    public ColorPickerPanel() {
    }

    @Override
    public void init(BuilderScreen screen) {
        super.init(screen);
        this.resizable = false;
        this.draggable = true;
        this.closable = true;
    }

    // ======================== 渲染 ========================

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int cx = contentX();
        int cy = contentY();
        int cw = contentWidth();
        int ch = contentHeight();

        int textColor = ThemeManager.getTextColor();
        Font font = Minecraft.getInstance().font;

        // ---- 1. Color Wheel（带浮窗背景，居中显示）----
        int wheelAreaSize = WHEEL_DRAW_SIZE + WHEEL_PAD * 2;
        int wheelAreaX = cx + (cw - wheelAreaSize) / 2;
        int wheelAreaY = cy + 4;

        RenderSystem.enableBlend();
        RtsClientUiUtil.drawNineSliceFloatingPanel(g, wheelAreaX, wheelAreaY,
                wheelAreaSize, wheelAreaSize);
        RtsClientUiUtil.drawHighQualityImage(g, COLOR_WHEEL_TEXTURE,
                wheelAreaX + WHEEL_PAD, wheelAreaY + WHEEL_PAD,
                WHEEL_DRAW_SIZE, WHEEL_DRAW_SIZE,
                0, 0, COLOR_WHEEL_TEX_W, COLOR_WHEEL_TEX_H,
                COLOR_WHEEL_TEX_W, COLOR_WHEEL_TEX_H);
        RenderSystem.disableBlend();

        // ---- 2. 当前颜色预览条 ----
        int previewY = wheelAreaY + wheelAreaSize + 6;
        int previewX = cx + 6;
        int previewW = cw - 12;

        g.fill(previewX, previewY, previewX + previewW, previewY + PREVIEW_BAR_H,
                BoundaryPass.barrierColor);
        // 边框
        int borderColor = 0xFF666666;
        g.hLine(previewX, previewX + previewW, previewY, borderColor);
        g.hLine(previewX, previewX + previewW, previewY + PREVIEW_BAR_H, borderColor);
        g.vLine(previewX, previewY, previewY + PREVIEW_BAR_H, borderColor);
        g.vLine(previewX + previewW, previewY, previewY + PREVIEW_BAR_H, borderColor);

        // 十六进制颜色标签（居中显示，根据颜色亮度自动选择文字颜色）
        String hexStr = String.format("#%06X", BoundaryPass.barrierColor & 0xFFFFFF);
        int hexColor = isDarkColor(BoundaryPass.barrierColor) ? 0xFFFFFFFF : 0xFF000000;
        int hexX = cx + cw / 2 - font.width(hexStr) / 2;
        int hexY = previewY + (PREVIEW_BAR_H - font.lineHeight) / 2 + 1;
        g.drawString(font, hexStr, hexX, hexY, hexColor, false);

        // ---- 3. 预设色块标题 ----
        int presetLabelY = previewY + PREVIEW_BAR_H + 8;
        RtsClientUiUtil.drawUiText(g,
                Component.translatable("screen.rtsbuilding.color_picker.presets"),
                cx + 6, presetLabelY, textColor);

        // ---- 4. 预设色块网格 ----
        int gridStartX = cx + (cw - (SWATCHES_PER_ROW * (SWATCH_SIZE + SWATCH_GAP) - SWATCH_GAP)) / 2;
        int gridY = presetLabelY + 12;

        for (int i = 0; i < PRESET_COLORS.length; i++) {
            int row = i / SWATCHES_PER_ROW;
            int col = i % SWATCHES_PER_ROW;
            int sx = gridStartX + col * (SWATCH_SIZE + SWATCH_GAP);
            int sy = gridY + row * (SWATCH_SIZE + SWATCH_GAP);

            // 色块填充
            g.fill(sx, sy, sx + SWATCH_SIZE, sy + SWATCH_SIZE, PRESET_COLORS[i]);

            // 如果当前选中此颜色，绘制白色边框
            if (PRESET_COLORS[i] == BoundaryPass.barrierColor) {
                g.hLine(sx - 1, sx + SWATCH_SIZE, sy - 1, 0xFFFFFFFF);
                g.hLine(sx - 1, sx + SWATCH_SIZE, sy + SWATCH_SIZE, 0xFFFFFFFF);
                g.vLine(sx - 1, sy - 1, sy + SWATCH_SIZE, 0xFFFFFFFF);
                g.vLine(sx + SWATCH_SIZE, sy - 1, sy + SWATCH_SIZE, 0xFFFFFFFF);
            } else {
                // 普通色块用灰色细边框
                g.hLine(sx, sx + SWATCH_SIZE - 1, sy, 0xFF888888);
                g.hLine(sx, sx + SWATCH_SIZE - 1, sy + SWATCH_SIZE - 1, 0xFF444444);
                g.vLine(sx, sy, sy + SWATCH_SIZE - 1, 0xFF888888);
                g.vLine(sx + SWATCH_SIZE - 1, sy, sy + SWATCH_SIZE - 1, 0xFF444444);
            }
        }
    }

    // ======================== 交互 ========================

    @Override
    protected void handleContentClick(double mouseX, double mouseY, int button) {
        if (button != 0) return;

        int cx = contentX();
        int cw = contentWidth();

        // ---- 计算预设色块网格位置 ----
        int wheelAreaSize = WHEEL_DRAW_SIZE + WHEEL_PAD * 2;
        int wheelAreaY = contentY() + 4;
        int previewY = wheelAreaY + wheelAreaSize + 6;
        int presetLabelY = previewY + PREVIEW_BAR_H + 8;
        int gridY = presetLabelY + 12;
        int gridStartX = cx + (cw - (SWATCHES_PER_ROW * (SWATCH_SIZE + SWATCH_GAP) - SWATCH_GAP)) / 2;

        // 检测预设色块点击
        for (int i = 0; i < PRESET_COLORS.length; i++) {
            int row = i / SWATCHES_PER_ROW;
            int col = i % SWATCHES_PER_ROW;
            int sx = gridStartX + col * (SWATCH_SIZE + SWATCH_GAP);
            int sy = gridY + row * (SWATCH_SIZE + SWATCH_GAP);

            if (mouseX >= sx && mouseX <= sx + SWATCH_SIZE
                    && mouseY >= sy && mouseY <= sy + SWATCH_SIZE) {
                BoundaryPass.barrierColor = PRESET_COLORS[i];
                return;
            }
        }
    }

    // ======================== 面板属性 ========================

    @Override
    protected Component getTitle() {
        return Component.translatable("screen.rtsbuilding.color_picker.title");
    }

    @Override
    protected int getDefaultWidth() {
        return PANEL_W;
    }

    @Override
    protected int getDefaultHeight() {
        return PANEL_H;
    }

    @Override
    protected void computeDefaultPosition() {
        if (this.screen != null) {
            this.windowX = Math.max(8, (this.screen.width - this.windowWidth) / 2);
            this.windowY = Math.max(60, (this.screen.height - this.windowHeight) / 2);
        }
    }

    // ======================== 工具方法 ========================

    /**
     * 判断颜色是否为深色（用于在颜色预览条上选择文字颜色）。
     * 使用加权亮度公式：0.299R + 0.587G + 0.114B，阈值 128。
     */
    private static boolean isDarkColor(int argb) {
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        return (r * 0.299 + g * 0.587 + b * 0.114) < 128;
    }
}
