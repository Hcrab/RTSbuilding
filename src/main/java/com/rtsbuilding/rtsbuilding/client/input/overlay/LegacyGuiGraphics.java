package com.rtsbuilding.rtsbuilding.client.input.overlay;

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
        Gui.drawRect(left, top, right, bottom, color);
    }

    public void drawString(FontRenderer font, String text, int x, int y, int color) {
        font.drawString(text == null ? "" : text, x, y, color, false);
    }

    public void drawString(FontRenderer font, String text, int x, int y, int color, boolean shadow) {
        font.drawString(text == null ? "" : text, x, y, color, shadow);
    }

    public void drawCenteredString(FontRenderer font, String text, int centerX, int y, int color) {
        String safe = text == null ? "" : text;
        font.drawString(safe, centerX - font.getStringWidth(safe) / 2, y, color, false);
    }

    public void renderItem(ItemStack stack, int x, int y) {
        if (stack == null || stack.isEmpty()) return;
        GlStateManager.enableDepth();
        minecraft.getRenderItem().renderItemAndEffectIntoGUI(stack, x, y);
        minecraft.getRenderItem().renderItemOverlayIntoGUI(minecraft.fontRenderer, stack, x, y, null);
    }

    public void renderTooltip(ItemStack stack, int mouseX, int mouseY) {
        if (stack == null || stack.isEmpty() || minecraft.player == null) return;
        List<String> lines = stack.getTooltip(minecraft.player, minecraft.gameSettings.advancedItemTooltips
                ? net.minecraft.client.util.ITooltipFlag.TooltipFlags.ADVANCED
                : net.minecraft.client.util.ITooltipFlag.TooltipFlags.NORMAL);
        GuiUtils.drawHoveringText(lines, mouseX, mouseY, screenWidth, screenHeight, 300, minecraft.fontRenderer);
    }
}
