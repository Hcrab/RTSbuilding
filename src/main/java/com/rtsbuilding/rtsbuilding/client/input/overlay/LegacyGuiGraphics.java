package com.rtsbuilding.rtsbuilding.client.input.overlay;

import com.rtsbuilding.rtsbuilding.client.rendering.util.RtsGuiRenderState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.client.config.GuiUtils;

import java.util.List;

/**
 * 将 1.12 的立即绘制 API 收拢成容器覆盖层需要的最小表面。
 * 该类不保存界面状态，也不接管 Minecraft 的共享缓冲区。
 */
public final class LegacyGuiGraphics {
    private final Minecraft minecraft;
    private final int screenWidth;
    private final int screenHeight;
    /**
     * 当前容器传入的透明度乘子。它只服务于窗口退场等视觉过渡，不能改变控件逻辑状态、
     * 鼠标命中或物品堆栈；子控件与文本必须继承同一个值，避免窗口框架淡出而内容悬浮。
     */
    private double alphaMultiplier = 1.0D;

    public LegacyGuiGraphics(Minecraft minecraft, int screenWidth, int screenHeight) {
        this.minecraft = minecraft;
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
    }

    public void pushPose() {
        GlStateManager.pushMatrix();
    }

    public void scale(float x, float y, float z) {
        GlStateManager.scale(x, y, z);
    }

    public void popPose() {
        GlStateManager.popMatrix();
    }

    public void fill(int left, int top, int right, int bottom, int color) {
        Gui.drawRect(left, top, right, bottom, applyAlpha(color));
    }

    public void drawString(FontRenderer font, String text, int x, int y, int color) {
        font.drawString(text == null ? "" : text, x, y, applyAlpha(color), false);
    }

    public void drawString(FontRenderer font, String text, int x, int y, int color, boolean shadow) {
        font.drawString(text == null ? "" : text, x, y, applyAlpha(color), shadow);
    }

    public void drawCenteredString(FontRenderer font, String text, int centerX, int y, int color) {
        String safe = text == null ? "" : text;
        font.drawString(safe, centerX - font.getStringWidth(safe) / 2, y, applyAlpha(color), false);
    }

    /** 以嵌套方式临时乘上容器透明度，调用方务必在 finally 中恢复。 */
    public double pushAlpha(double multiplier) {
        double previous = alphaMultiplier;
        alphaMultiplier *= Math.max(0.0D, Math.min(1.0D, multiplier));
        return previous;
    }

    /** 恢复 {@link #pushAlpha(double)} 保存的上层透明度。 */
    public void restoreAlpha(double previous) {
        alphaMultiplier = Math.max(0.0D, Math.min(1.0D, previous));
    }

    /** 供直接纹理控件使用，保证纹理也与父窗口文字一起渐隐。 */
    public float alphaMultiplier() {
        return (float) alphaMultiplier;
    }

    private int applyAlpha(int color) {
        int alpha = (int) Math.round((color >>> 24 & 0xFF) * alphaMultiplier);
        return alpha << 24 | color & ((1 << 24) - 1);
    }

    public void renderItem(ItemStack stack, int x, int y) {
        if (stack == null || stack.isEmpty()) return;
        try (RtsGuiRenderState.Scope ignored = RtsGuiRenderState.beginItem()) {
            minecraft.getRenderItem().renderItemAndEffectIntoGUI(stack, x, y);
            minecraft.getRenderItem().renderItemOverlayIntoGUI(
                    minecraft.fontRenderer, stack, x, y, null);
        }
    }

    public void renderTooltip(ItemStack stack, int mouseX, int mouseY) {
        if (stack == null || stack.isEmpty() || minecraft.player == null) return;
        List<String> lines = stack.getTooltip(minecraft.player, minecraft.gameSettings.advancedItemTooltips
                ? net.minecraft.client.util.ITooltipFlag.TooltipFlags.ADVANCED
                : net.minecraft.client.util.ITooltipFlag.TooltipFlags.NORMAL);
        renderTooltipLines(lines, mouseX, mouseY);
    }

    public void renderTooltipText(String text, int mouseX, int mouseY) {
        if (text == null || text.isEmpty()) return;
        renderTooltipLines(java.util.Collections.singletonList(text), mouseX, mouseY);
    }

    public void renderTooltipLines(List<String> lines, int mouseX, int mouseY) {
        if (lines == null || lines.isEmpty()) return;
        try (RtsGuiRenderState.Scope ignored = RtsGuiRenderState.preserveForExternalGuiCall()) {
            GuiUtils.drawHoveringText(
                    lines, mouseX, mouseY, screenWidth, screenHeight, 300, minecraft.fontRenderer);
        }
    }
}
