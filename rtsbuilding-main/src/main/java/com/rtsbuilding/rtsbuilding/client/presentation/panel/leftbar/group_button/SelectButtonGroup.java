package com.rtsbuilding.rtsbuilding.client.presentation.panel.leftbar.group_button;

import com.mojang.blaze3d.systems.RenderSystem;
import com.rtsbuilding.rtsbuilding.client.input.RtsKeyMappings;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.base.button.AbstractButtonGroup;
import com.rtsbuilding.rtsbuilding.client.util.animate.ColorAnimation;
import com.rtsbuilding.rtsbuilding.client.util.render.SdfRenderer;
import com.rtsbuilding.rtsbuilding.client.util.render.TextRenderer;
import com.rtsbuilding.rtsbuilding.client.util.state.TooltipController;
import com.rtsbuilding.rtsbuilding.client.util.theme.ThemeManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class SelectButtonGroup extends AbstractButtonGroup {

    
    private static final ResourceLocation BTN_TEXTURE = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/left/button/click.png");
    
    private static final ResourceLocation SELECT_BTN = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/left/button/select.png");

    

    private final TooltipController clickBtnTooltip = TooltipController.builder().direction(TooltipController.Direction.RIGHT).build();
    private final TooltipController selectBtnTooltip = TooltipController.builder().direction(TooltipController.Direction.RIGHT).build();

    /** 禁用状态：建造模式开启连锁挖掘时，框选模式（第二个按钮）不可启用。 */
    private boolean disabled;

    /** 禁用覆盖层半透明黑色底（RGBA）。 */
    private static final int OVERLAY_COLOR = 0x73000000;

    public SelectButtonGroup() {
        super(Direction.VERTICAL, DEFAULT_BTN_SIZE, DEFAULT_INNER_GAP, true,
                null, null, null,
                BTN_TEXTURE, SELECT_BTN);
        
        selected[0] = true;
    }

    public boolean isDisabled() {
        return disabled;
    }

    /**
     * 仅禁用框选模式（第二个按钮），点击模式保持可用。
     * 进入禁用时强制回到点击模式选中态，退出时保持点击模式。
     */
    public void setDisabled(boolean disabled) {
        this.disabled = disabled;
        if (disabled) {
            selected[0] = true;
            selected[1] = false;
        }
    }

    
    public void toggleSelection() {
        if (disabled) {
            return;
        }
        selected[0] = !selected[0];
        selected[1] = !selected[1];
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, int originX, int originY) {
        super.render(g, mouseX, mouseY, originX, originY);
        if (disabled) {
            renderDisabledOverlay(g, originX, originY);
        }
    }

    @Override
    public int mouseClicked(double mx, double my, int originX, int originY) {
        // 仅拦截框选按钮（index 1），点击按钮（index 0）保持可用
        int selectY = originY + buttonSize + innerGap;
        if (disabled && mx >= originX && mx < originX + buttonSize
                && my >= selectY && my < selectY + buttonSize) {
            return -1;
        }
        return super.mouseClicked(mx, my, originX, originY);
    }

    /**
     * 在框选模式按钮上方绘制一层半透明黑色圆角矩形覆盖层，
     * 表示该按钮当前无法启用。
     */
    private void renderDisabledOverlay(GuiGraphics g, int originX, int originY) {
        int by = originY + buttonSize + innerGap;
        SdfRenderer.drawRoundedRect(g, originX, by, buttonSize, buttonSize,
                4f, OVERLAY_COLOR);
    }

    
    public void tickTooltips(int mouseX, int mouseY, int originX, int originY) {
        int bx = originX;
        int by = originY;

        
        boolean hover0 = mouseX >= bx && mouseX < bx + buttonSize
                && mouseY >= by && mouseY < by + buttonSize;
        clickBtnTooltip.update(hover0, false);

        
        boolean hover1 = mouseX >= bx && mouseX < bx + buttonSize
                && mouseY >= by + buttonSize && mouseY < by + buttonSize * 2;
        selectBtnTooltip.update(hover1, false);
    }

    
    public void renderTooltipOverlay(GuiGraphics g, int originX, int originY,
                                     int screenW, int screenH) {
        String keyText = RtsKeyMappings.TOGGLE_SELECT_MODE_KEY.getTranslatedKeyMessage().getString();
        int textColor = ThemeManager.getTextColor();
        int shortcutColor = ColorAnimation.scale(textColor, 0.6f);

        
        if (clickBtnTooltip.shouldRender()) {
            String text = Component.translatable("tooltip.rtsbuilding.left.click_button").getString() + "\n"
                    + Component.translatable("tooltip.rtsbuilding.left.click_button.desc").getString() + "\n"
                    + Component.translatable("tooltip.rtsbuilding.shortcut", keyText).getString();
            renderTooltipRight(g, clickBtnTooltip,
                    originX, originY, buttonSize, buttonSize,
                    text, textColor, shortcutColor, screenW, screenH);
        }

        
        if (selectBtnTooltip.shouldRender()) {
            String text = Component.translatable("tooltip.rtsbuilding.left.select_button").getString() + "\n"
                    + Component.translatable("tooltip.rtsbuilding.left.select_button.desc").getString() + "\n"
                    + Component.translatable("tooltip.rtsbuilding.shortcut", keyText).getString();
            renderTooltipRight(g, selectBtnTooltip,
                    originX, originY + buttonSize, buttonSize, buttonSize,
                    text, textColor, shortcutColor, screenW, screenH);
        }
    }

    
    private static void renderTooltipRight(GuiGraphics g, TooltipController tooltip,
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

        
        int tipX = btnX + btnW + 2;
        int tipY = btnY;
        tipX = Math.max(0, Math.min(tipX, screenW - tipW));
        tipY = Math.max(0, Math.min(tipY, screenH - tipH));

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        SdfRenderer.drawVectorFloatingPanel(g, tipX, tipY, tipW, tipH, false, alpha);

        float textY = tipY + padV;
        for (int i = 0; i < lines.length; i++) {
            int lineColor = (i == lines.length - 1) ? shortcutColor : color;
            g.pose().pushPose();
            g.pose().translate(tipX + padH, textY, 0);
            g.pose().scale(0.75f, 0.75f, 1.0f);
            TextRenderer.draw(g, lines[i], 0, 0, lineColor);
            g.pose().popPose();
            textY += scaledLineH + scaledLineGap;
        }

        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.disableBlend();
    }
}
