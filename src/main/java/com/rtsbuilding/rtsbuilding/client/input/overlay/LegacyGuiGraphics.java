package com.rtsbuilding.rtsbuilding.client.input.overlay;

import com.rtsbuilding.rtsbuilding.client.rendering.util.RtsGuiRenderState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import com.rtsbuilding.rtsbuilding.platform.render.GlStateManager;
import net.minecraft.item.ItemStack;
import cpw.mods.fml.client.config.GuiUtils;

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
        if (stack == null || com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(stack)) return;
        try (RtsGuiRenderState.Scope ignored = RtsGuiRenderState.beginItem()) {
            com.rtsbuilding.rtsbuilding.platform.client.MinecraftCompat.renderItem().renderItemAndEffectIntoGUI(
                    minecraft.fontRenderer, minecraft.getTextureManager(), stack, x, y);
            com.rtsbuilding.rtsbuilding.platform.client.MinecraftCompat.renderItem().renderItemOverlayIntoGUI(
                    minecraft.fontRenderer, minecraft.getTextureManager(), stack, x, y, null);
        }
    }

    public void renderTooltip(ItemStack stack, int mouseX, int mouseY) {
        if (stack == null || com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(stack) || minecraft.thePlayer == null) return;
        List<String> lines = stack.getTooltip(
                minecraft.thePlayer, minecraft.gameSettings.advancedItemTooltips);
        renderTooltipLines(lines, mouseX, mouseY);
    }

    public void renderTooltipText(String text, int mouseX, int mouseY) {
        if (text == null || text.isEmpty()) return;
        renderTooltipLines(java.util.Collections.singletonList(text), mouseX, mouseY);
    }

    public void renderTooltipLines(List<String> lines, int mouseX, int mouseY) {
        if (lines == null || lines.isEmpty()) return;
        try (RtsGuiRenderState.Scope ignored = RtsGuiRenderState.preserveForExternalGuiCall()) {
            drawHoveringText(lines, mouseX, mouseY);
        }
    }

    /** 1.7.10 Forge 没有公开的可复用悬浮提示入口，沿用原版布局并保持状态隔离。 */
    private void drawHoveringText(List<String> lines, int mouseX, int mouseY) {
        int width = 0;
        for (String line : lines) width = Math.max(width, minecraft.fontRenderer.getStringWidth(line));
        int x = mouseX + 12;
        int y = mouseY - 12;
        int height = lines.size() == 1 ? 8 : 8 + (lines.size() - 1) * 10;
        if (x + width > screenWidth) x -= 28 + width;
        if (y + height + 6 > screenHeight) y = screenHeight - height - 6;
        this.fill(x - 3, y - 4, x + width + 3, y + height + 4, 0xF0100010);
        int lineY = y;
        for (String line : lines) {
            minecraft.fontRenderer.drawStringWithShadow(line, x, lineY, 0xFFFFFFFF);
            lineY += 10;
        }
    }
}
