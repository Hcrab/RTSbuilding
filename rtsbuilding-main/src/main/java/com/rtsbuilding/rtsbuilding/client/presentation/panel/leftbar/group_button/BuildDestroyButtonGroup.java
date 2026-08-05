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

/**
 * 左面板建造/破坏按钮组：第一个为建造（construction.png），第二个为破坏（destruction.png）。
 * 互斥单选，默认选中建造。背景沿用左面板按钮组的矢量绘制（{@code SdfRenderer.drawButtonBg}），
 * 图标贴图与其他按钮一致（1024x512 水平主题对）。
 */
public final class BuildDestroyButtonGroup extends AbstractButtonGroup {

    
    private static final ResourceLocation CONSTRUCTION_BTN = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/left/button/construction.png");
    
    private static final ResourceLocation DESTRUCTION_BTN = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/left/button/destruction.png");

    /** 是否显示（仅建造模式显示）。 */
    private boolean show = false;

    /** 禁用状态：连锁破坏（ultimine）启用时，整个按钮组不可操作。 */
    private boolean disabled;

    /** 禁用覆盖层半透明黑色底（RGBA）。 */
    private static final int OVERLAY_COLOR = 0x73000000;

    
    private final TooltipController constructionTooltip = TooltipController.builder()
            .direction(TooltipController.Direction.RIGHT).build();
    private final TooltipController destructionTooltip = TooltipController.builder()
            .direction(TooltipController.Direction.RIGHT).build();

    public BuildDestroyButtonGroup() {
        super(Direction.VERTICAL, DEFAULT_BTN_SIZE, DEFAULT_INNER_GAP, true,
                null, null, null,
                CONSTRUCTION_BTN, DESTRUCTION_BTN);
        
        selected[0] = true;
    }

    public void setShow(boolean show) {
        this.show = show;
        if (!show) {
            selected[0] = false;
            selected[1] = false;
        }
    }

    public boolean isShow() {
        return show;
    }

    public boolean isDisabled() {
        return disabled;
    }

    /**
     * 禁用时清空选中态，使按钮图标回到未选中（暗色）状态，而非仅仅绘制覆盖层；
     * 解除禁用时若均未选中则回落默认选中建造。
     */
    public void setDisabled(boolean disabled) {
        this.disabled = disabled;
        if (disabled) {
            selected[0] = false;
            selected[1] = false;
        } else if (!selected[0] && !selected[1]) {
            selected[0] = true;
        }
    }

    /** 可见状态下的总高度（隐藏时为 0）。 */
    public int visibleHeight() {
        return show ? totalHeight() : 0;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, int originX, int originY) {
        if (!show) {
            return;
        }
        super.render(g, mouseX, mouseY, originX, originY);
        if (disabled) {
            // 半透明黑色圆角矩形覆盖整个按钮组，表示无法启用
            SdfRenderer.drawRoundedRect(g, originX, originY, buttonSize, totalHeight(),
                    4f, OVERLAY_COLOR);
        }
    }

    @Override
    public int mouseClicked(double mx, double my, int originX, int originY) {
        if (!show || disabled) {
            return -1;
        }
        return super.mouseClicked(mx, my, originX, originY);
    }

    
    public boolean isConstructionSelected() {
        return selected[0];
    }

    
    public boolean isDestructionSelected() {
        return selected[1];
    }

    
    public void selectConstruction() {
        selected[0] = true;
        selected[1] = false;
    }

    
    public void selectDestruction() {
        selected[0] = false;
        selected[1] = true;
    }

    
    public void tickTooltips(int mouseX, int mouseY, int originX, int originY) {
        if (!show) {
            constructionTooltip.update(false, false);
            destructionTooltip.update(false, false);
            return;
        }
        for (int i = 0; i < buttonCount(); i++) {
            int by = originY + i * (buttonSize + innerGap);
            boolean hover = mouseX >= originX && mouseX < originX + buttonSize
                    && mouseY >= by && mouseY < by + buttonSize;
            getTooltip(i).update(hover, false);
        }
    }

    
    public void renderTooltipOverlay(GuiGraphics g, int originX, int originY,
                                     int screenW, int screenH) {
        if (!show) {
            return;
        }
        int textColor = ThemeManager.getTextColor();
        int shortcutColor = ColorAnimation.scale(textColor, 0.6f);
        for (int i = 0; i < buttonCount(); i++) {
            int by = originY + i * (buttonSize + innerGap);
            TooltipController tooltip = getTooltip(i);
            if (tooltip.shouldRender()) {
                String text = i == 0
                        ? Component.translatable("tooltip.rtsbuilding.left.construction").getString() + "\n"
                                + Component.translatable("tooltip.rtsbuilding.left.construction.desc").getString()
                        : Component.translatable("tooltip.rtsbuilding.left.destruction").getString() + "\n"
                                + Component.translatable("tooltip.rtsbuilding.left.destruction.desc").getString();
                renderTooltipRight(g, tooltip, originX, by, buttonSize, buttonSize,
                        text, textColor, shortcutColor, screenW, screenH);
            }
        }
    }

    
    private TooltipController getTooltip(int i) {
        return switch (i) {
            case 0 -> constructionTooltip;
            case 1 -> destructionTooltip;
            default -> throw new IndexOutOfBoundsException("Unexpected index: " + i);
        };
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
