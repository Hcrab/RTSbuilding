package com.rtsbuilding.rtsbuilding.client.presentation.panel.topbar;

import com.rtsbuilding.rtsbuilding.client.render.util.CursorRaycaster;
import com.rtsbuilding.rtsbuilding.client.util.animate.AnimFloat;
import com.rtsbuilding.rtsbuilding.client.util.animate.ColorAnimation;
import com.rtsbuilding.rtsbuilding.client.util.render.SdfRenderer;
import com.rtsbuilding.rtsbuilding.client.util.render.TextRenderer;
import com.rtsbuilding.rtsbuilding.client.util.state.TooltipController;
import com.rtsbuilding.rtsbuilding.client.util.theme.ThemeManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * 顶栏流体遮挡指示按钮：使用与其它下条目按钮一致的矢量背景风格，
 * 图案直接用文字提示（语言文件集中调配），开启流体遮挡（按住 F）时高亮。
 */
public final class FluidOcclusionIndicator {

    
    private static final int BTN_BG_TYPE = -1;

    private final AnimFloat hoverState = AnimFloat.hover();

    
    private final TooltipController tooltip = TooltipController.builder().build();

    public void render(GuiGraphics g, int mouseX, int mouseY, TopBarLayoutHelper.ButtonGroup group) {
        var rect = group.rect(0);
        boolean active = CursorRaycaster.isFluidRaycastActive();
        boolean hovering = rect.contains(mouseX, mouseY);
        float hoverT = hoverState.track(hovering);
        tooltip.update(hovering, false);

        SdfRenderer.drawButtonBg(g, BTN_BG_TYPE, true, active, hoverT,
                rect.x(), rect.y(), rect.width(), rect.height());

        var font = Minecraft.getInstance().font;
        String label = Component.translatable(TopBarLayoutHelper.FLUID_BTN_LABEL_KEY).getString();
        int textColor = active
                ? ThemeManager.getHoverTextColor()
                : ColorAnimation.lerpRGB(ThemeManager.getTextColor(), ThemeManager.getHoverTextColor(), hoverT);
        int textX = rect.x() + (rect.width() - font.width(label)) / 2;
        int textY = rect.y() + (rect.height() - font.lineHeight) / 2;
        TextRenderer.draw(g, label, textX, textY, textColor);
    }

    
    public void renderTooltipOverlay(GuiGraphics g, TopBarLayoutHelper.ButtonGroup group,
                                     int screenW, int screenH) {
        if (!tooltip.shouldRender()) return;

        var rect = group.rect(0);
        int textColor = ThemeManager.getTextColor();
        int shortcutColor = ColorAnimation.scale(textColor, 0.6f);
        String text = Component.translatable("tooltip.rtsbuilding.fluid_occlusion").getString() + "\n"
                + Component.translatable("tooltip.rtsbuilding.fluid_occlusion.desc").getString();
        tooltip.render(g, rect.x(), rect.y(), rect.width(), rect.height(),
                text, textColor, shortcutColor, screenW, screenH);
    }
}
