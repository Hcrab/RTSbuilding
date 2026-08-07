package com.rtsbuilding.rtsbuilding.client.screen.workflow;

import com.rtsbuilding.rtsbuilding.client.util.RtsClientUiUtil;
import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * 两类恢复窗口共享的 Minecraft 字体和物品小工具。
 *
 * <p>本类不排版、不选择颜色、不持有状态，也不发送恢复命令。</p>
 */
final class WorkflowResumeRenderSupport {
    private WorkflowResumeRenderSupport() {
    }

    static void draw(
            GuiGraphicsExtractor graphics,
            Font font,
            String text,
            int x,
            int y,
            int color) {
        graphics.text(font, text, x, y, color, false);
    }

    static void drawActionText(
            GuiGraphicsExtractor graphics,
            Font font,
            UiRect action,
            String translationKey,
            int color) {
        RtsClientUiUtil.drawCenteredStringNoShadow(graphics, font,
                text(translationKey),
                (int) (action.getX() + action.getWidth() / 2.0D),
                (int) action.getY() + 4, color);
    }

    static ItemStack item(String itemId) {
        Identifier id = Identifier.tryParse(itemId);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(BuiltInRegistries.ITEM.getValue(id));
    }

    static String truncate(String label, Font font, int maxPixels) {
        String safe = label == null ? "" : label;
        if (font.width(safe) <= maxPixels) {
            return safe;
        }
        while (!safe.isEmpty() && font.width(safe + "…") > maxPixels) {
            safe = safe.substring(0, safe.length() - 1);
        }
        return safe + "…";
    }

    static String text(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }
}
