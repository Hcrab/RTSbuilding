package com.rtsbuilding.rtsbuilding.client.screen.panel.leftbar.group_button;

import com.mojang.blaze3d.systems.RenderSystem;
import com.rtsbuilding.rtsbuilding.client.input.RtsKeyMappings;
import com.rtsbuilding.rtsbuilding.client.screen.panel.base.AbstractButtonGroup;
import com.rtsbuilding.rtsbuilding.client.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * 选择按钮组——包含 click_button（选中/建筑模式切换）和 select_button（选择工具）。
 *
 * <p>该组为第一组（group 0），位于所有按钮最上方。</p>
 */
public final class SelectButtonGroup extends AbstractButtonGroup {

    /** click_button.png 贴图路径（1024×1536，横向双主题，纵向3状态） */
    private static final ResourceLocation BTN_TEXTURE = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/left/default_button/click_button.png");
    /** select_button.png 贴图路径 */
    private static final ResourceLocation SELECT_BTN = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/left/default_button/select_button.png");

    // ----- 浮窗提示 -----
    private final FloatingTooltip clickBtnTooltip = new FloatingTooltip();
    private final FloatingTooltip selectBtnTooltip = new FloatingTooltip();

    public SelectButtonGroup() {
        super(BTN_TEXTURE, SELECT_BTN);
        // 初始化默认选中 click_button（索引 0）
        selected[0] = true;
    }

    /**
     * 切换选择模式——在 click_button（索引 0）和 select_button（索引 1）之间切换。
     */
    public void toggleSelection() {
        selected[0] = !selected[0];
        selected[1] = !selected[1];
    }

    /** 刷新 tooltip 状态——由 LeftSidebarPanel.render() 每帧调用 */
    public void tickTooltips(int mouseX, int mouseY, int originX, int originY) {
        int bx = originX;
        int by = originY;

        // click_button（索引 0）
        boolean hover0 = mouseX >= bx && mouseX < bx + buttonSize
                && mouseY >= by && mouseY < by + buttonSize;
        clickBtnTooltip.tick();
        clickBtnTooltip.update(hover0, false);

        // select_button（索引 1）
        boolean hover1 = mouseX >= bx && mouseX < bx + buttonSize
                && mouseY >= by + buttonSize && mouseY < by + buttonSize * 2;
        selectBtnTooltip.tick();
        selectBtnTooltip.update(hover1, false);
    }

    /** 在覆盖层阶段渲染 tooltip，定位在按钮右侧 */
    public void renderTooltipOverlay(GuiGraphics g, int originX, int originY,
                                     int screenW, int screenH) {
        String keyText = RtsKeyMappings.TOGGLE_SELECT_MODE_KEY.getTranslatedKeyMessage().getString();
        int textColor = ThemeManager.getTextColor();
        int shortcutColor = SmoothAnimator.scaleColor(textColor, 0.6f);

        // click_button
        if (clickBtnTooltip.shouldRender()) {
            String text = Component.translatable("tooltip.rtsbuilding.left.click_button").getString() + "\n"
                    + Component.translatable("tooltip.rtsbuilding.left.click_button.desc").getString() + "\n"
                    + Component.translatable("tooltip.rtsbuilding.shortcut", keyText).getString();
            renderTooltipRight(g, clickBtnTooltip,
                    originX, originY, buttonSize, buttonSize,
                    text, textColor, shortcutColor, screenW, screenH);
        }

        // select_button
        if (selectBtnTooltip.shouldRender()) {
            String text = Component.translatable("tooltip.rtsbuilding.left.select_button").getString() + "\n"
                    + Component.translatable("tooltip.rtsbuilding.left.select_button.desc").getString() + "\n"
                    + Component.translatable("tooltip.rtsbuilding.shortcut", keyText).getString();
            renderTooltipRight(g, selectBtnTooltip,
                    originX, originY + buttonSize, buttonSize, buttonSize,
                    text, textColor, shortcutColor, screenW, screenH);
        }
    }

    /** 在按钮右侧渲染浮窗 */
    private static void renderTooltipRight(GuiGraphics g, FloatingTooltip tooltip,
                                            int btnX, int btnY, int btnW, int btnH,
                                            String text, int color, int shortcutColor,
                                            int screenW, int screenH) {
        float alpha = tooltip.getAlpha();
        var font = Minecraft.getInstance().font;

        String[] lines = text.split("\n");
        int lineHeight = font.lineHeight;
        int lineGap = 1;
        float scaledLineH = lineHeight * 0.75f;
        float scaledLineGap = lineGap * 0.75f;
        int maxLineW = 0;
        for (String line : lines) {
            maxLineW = Math.max(maxLineW, font.width(line));
        }
        int padH = 6, padV = 3;
        int tipW = (int)(maxLineW * 0.75f) + padH * 2;
        int tipH = (int)(scaledLineH * lines.length + scaledLineGap * (lines.length - 1)) + padV * 2;

        // 定位到按钮右侧
        int tipX = btnX + btnW + 2;
        int tipY = btnY;
        tipX = Math.max(0, Math.min(tipX, screenW - tipW));
        tipY = Math.max(0, Math.min(tipY, screenH - tipH));

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, alpha);
        RtsClientUiUtil.drawNineSliceFloatingPanel(g, tipX, tipY, tipW, tipH);

        float textY = tipY + padV;
        for (int i = 0; i < lines.length; i++) {
            int lineColor = (i == lines.length - 1) ? shortcutColor : color;
            g.pose().pushPose();
            g.pose().translate(tipX + padH, textY, 0);
            g.pose().scale(0.75f, 0.75f, 1.0f);
            RtsClientUiUtil.drawUiText(g, lines[i], 0, 0, lineColor);
            g.pose().popPose();
            textY += scaledLineH + scaledLineGap;
        }

        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }
}
