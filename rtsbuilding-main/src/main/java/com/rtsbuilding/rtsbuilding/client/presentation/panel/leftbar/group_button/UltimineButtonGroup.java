package com.rtsbuilding.rtsbuilding.client.presentation.panel.leftbar.group_button;

import com.mojang.blaze3d.systems.RenderSystem;
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

public final class UltimineButtonGroup extends AbstractButtonGroup {

    
    private static final ResourceLocation ULTIMINE_BTN = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/left/button/ultimine.png");

    
    private boolean show = false;

    
    private final TooltipController ultimineBtnTooltip = TooltipController.builder()
            .direction(TooltipController.Direction.RIGHT).build();

    public UltimineButtonGroup() {
        super(Direction.VERTICAL, DEFAULT_BTN_SIZE, DEFAULT_INNER_GAP, true,
                null, null, null,
                ULTIMINE_BTN);
    }

    
    public void setShow(boolean show) {
        this.show = show;
        if (!show) {
            selected[0] = false;
        }
    }

    
    public boolean isShow() {
        return show;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, int originX, int originY) {
        if (!show) return;
        renderOnlyBg(g, mouseX, mouseY, 0, originX, originY);
        renderSinglePattern(g, mouseX, mouseY, 0, originX, originY);
    }

    
    private void renderOnlyBg(GuiGraphics g, int mouseX, int mouseY, int index, int bx, int by) {
        boolean hovering = mouseX >= bx && mouseX < bx + buttonSize
                && mouseY >= by && mouseY < by + buttonSize;
        float hoverT = this.hoverStates[index].track(hovering);
        SdfRenderer.drawButtonBg(g, 3, false, selected[index], hoverT,
                bx, by, buttonSize, buttonSize);
    }

    @Override
    public int mouseClicked(double mx, double my, int originX, int originY) {
        if (!show) return -1;
        if (mx >= originX && mx < originX + buttonSize
                && my >= originY && my < originY + buttonSize) {
            onButtonClick(0);
            return 0;
        }
        return -1;
    }

    @Override
    protected void onButtonClick(int index) {
        
        selected[index] = !selected[index];
    }

    
    public void tickTooltips(int mouseX, int mouseY, int originX, int originY) {
        if (!show) {
            ultimineBtnTooltip.update(false, false);
            return;
        }
        boolean hover = mouseX >= originX && mouseX < originX + buttonSize
                && mouseY >= originY && mouseY < originY + buttonSize;
        ultimineBtnTooltip.update(hover, false);
    }

    
    public void renderTooltipOverlay(GuiGraphics g, int originX, int originY,
                                     int screenW, int screenH) {
        if (!show || !ultimineBtnTooltip.shouldRender()) return;
        int textColor = ThemeManager.getTextColor();
        int shortcutColor = ColorAnimation.scale(textColor, 0.6f);
        String text = Component.translatable("tooltip.rtsbuilding.left.ultimine").getString() + "\n"
                + Component.translatable("tooltip.rtsbuilding.left.ultimine.desc").getString();
        renderTooltipRight(g, ultimineBtnTooltip, originX, originY, buttonSize, buttonSize,
                text, textColor, shortcutColor, screenW, screenH);
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
