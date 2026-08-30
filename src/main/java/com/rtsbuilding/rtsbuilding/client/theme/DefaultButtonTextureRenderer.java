package com.rtsbuilding.rtsbuilding.client.theme;

import com.mojang.blaze3d.systems.RenderSystem;
import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uikit.animation.UiControlAnimationState;
import com.rtsbuilding.rtsbuilding.uikit.layout.DefaultButtonTextureLayout;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiColor;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiTextureState;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.opengl.GL11;

/**
 * 按 Legacy 原图九宫切片绘制通用按钮。
 *
 * <p>本类不计算边框形状，也不绘制替代矩形；每一块都来自
 * {@code general/default_button.png} 的真实像素。动画只在四个原始状态之间交叉淡化，
 * 不改变按钮的命中框、布局或业务状态。</p>
 */
public final class DefaultButtonTextureRenderer {
    public static void renderAnimated(
            GuiGraphics graphics,
            UiRect bounds,
            UiControlAnimationState.Snapshot animation,
            UiColor disabledOverlay) {
        if (animation == null) throw new IllegalArgumentException("animation");
        double pressed = animation.press();
        double selected = (1.0D - pressed) * animation.selection();
        double hovered = (1.0D - pressed)
                * (1.0D - animation.selection()) * animation.hover();
        double inactive = Math.max(0.0D, 1.0D - pressed - selected - hovered);
        renderState(graphics, bounds, UiTextureState.INACTIVE, inactive);
        renderState(graphics, bounds, UiTextureState.HOVER, hovered);
        renderState(graphics, bounds, UiTextureState.ACTIVE, selected);
        renderState(graphics, bounds, UiTextureState.PRESSED, pressed);
        drawOverlay(graphics, bounds, disabledOverlay);
    }

    public static void renderHoverBlend(
            GuiGraphics graphics, UiRect bounds, double hover,
            UiColor disabledOverlay) {
        double amount = clamp(hover);
        renderState(graphics, bounds, UiTextureState.INACTIVE, 1.0D - amount);
        renderState(graphics, bounds, UiTextureState.HOVER, amount);
        drawOverlay(graphics, bounds, disabledOverlay);
    }

    public static void renderState(
            GuiGraphics graphics, UiRect bounds,
            UiTextureState state, double opacity) {
        if (graphics == null || bounds == null || state == null) {
            throw new IllegalArgumentException("graphics, bounds and state");
        }
        double alpha = clamp(opacity);
        if (alpha <= 0.001D) return;
        ResourceLocation texture = DefaultButtonTextureCatalog.resolve(state);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderTexture(0, texture);
        RenderSystem.texParameter(GL11.GL_TEXTURE_2D,
                GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        RenderSystem.texParameter(GL11.GL_TEXTURE_2D,
                GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        graphics.setColor(1.0F, 1.0F, 1.0F, (float) alpha);
        try {
            for (DefaultButtonTextureLayout.Slice slice
                    : DefaultButtonTextureLayout.slices(bounds, state)) {
                drawSlice(graphics, texture, slice);
            }
        } finally {
            graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.disableBlend();
        }
    }

    private static void drawSlice(
            GuiGraphics graphics,
            ResourceLocation texture,
            DefaultButtonTextureLayout.Slice slice) {
        UiRect source = slice.source();
        UiRect target = slice.target();
        if (target.getWidth() <= 0.0D || target.getHeight() <= 0.0D) return;
        graphics.pose().pushPose();
        graphics.pose().translate(target.getX(), target.getY(), 0.0D);
        graphics.pose().scale(
                (float) (target.getWidth() / source.getWidth()),
                (float) (target.getHeight() / source.getHeight()),
                1.0F);
        graphics.blit(
                texture, 0, 0,
                (int) source.getX(), (int) source.getY(),
                (int) source.getWidth(), (int) source.getHeight(),
                DefaultButtonTextureLayout.SHEET_WIDTH,
                DefaultButtonTextureLayout.SHEET_HEIGHT);
        graphics.pose().popPose();
    }

    private static void drawOverlay(
            GuiGraphics graphics, UiRect bounds, UiColor overlay) {
        if (overlay == null || overlay.alpha() <= 0) return;
        graphics.fill(
                (int) Math.round(bounds.getX()),
                (int) Math.round(bounds.getY()),
                (int) Math.round(bounds.right()),
                (int) Math.round(bounds.bottom()),
                overlay.toArgb());
    }

    private static double clamp(double value) {
        return Math.max(0.0D, Math.min(1.0D, value));
    }

    private DefaultButtonTextureRenderer() {
    }
}
