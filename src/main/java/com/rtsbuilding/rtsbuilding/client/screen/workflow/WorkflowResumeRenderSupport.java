package com.rtsbuilding.rtsbuilding.client.screen.workflow;

import com.rtsbuilding.rtsbuilding.client.input.overlay.LegacyGuiGraphics;
import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

/**
 * 两类恢复窗口 renderer 共用的 Minecraft 字体与物品小工具。
 *
 * <p>本类不排版、不选择颜色、不持有状态，也不发送网络命令。</p>
 */
final class WorkflowResumeRenderSupport {
    private WorkflowResumeRenderSupport() {
    }

    static void draw(
            LegacyGuiGraphics graphics,
            FontRenderer font,
            String text,
            int x,
            int y,
            int color) {
        graphics.drawString(font, text, x, y, color, false);
    }

    static void drawActionText(
            LegacyGuiGraphics graphics,
            FontRenderer font,
            UiRect action,
            String translationKey,
            int color) {
        graphics.drawCenteredString(font, text(translationKey),
                (int) (action.getX() + action.getWidth() / 2.0D),
                (int) action.getY() + 4, color);
    }

    static ItemStack item(String itemId) {
        if (itemId == null || itemId.isEmpty()) {
            return ItemStack.EMPTY;
        }
        final ResourceLocation id;
        try {
            id = new ResourceLocation(itemId);
        } catch (RuntimeException exception) {
            return ItemStack.EMPTY;
        }
        if (!Item.REGISTRY.containsKey(id)) {
            return ItemStack.EMPTY;
        }
        Item item = Item.REGISTRY.getObject(id);
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }

    static String truncate(
            String label,
            FontRenderer font,
            int maxPixels) {
        String safe = label == null ? "" : label;
        if (font.getStringWidth(safe) <= maxPixels) {
            return safe;
        }
        while (!safe.isEmpty()
                && font.getStringWidth(safe + "…") > maxPixels) {
            safe = safe.substring(0, safe.length() - 1);
        }
        return safe + "…";
    }

    static String text(String key, Object... args) {
        return I18n.format(key, args);
    }
}
