package com.rtsbuilding.rtsbuilding.client.popup;


import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.record.CraftFeedbackIngredient;
import com.rtsbuilding.rtsbuilding.client.util.RtsClientUiUtil;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public final class RtsCraftFeedbackPopup {
    private static final int PANEL_W = 228;
    private static final int ROW_H = 18;
    private static final int MAX_ROWS = 4;

    private RtsCraftFeedbackPopup() {
    }

    public static void render(GuiGraphicsExtractor g, Font font, int screenWidth, ClientRtsController controller) {
        if (g == null || font == null || controller == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now >= controller.getCraftFeedbackExpiryMs() || controller.getCraftFeedbackCount() <= 0) {
            return;
        }

        ItemStack resultPreview = resolvePreview(controller.getCraftFeedbackItemId());
        String resultLabel = resultPreview.isEmpty() ? controller.getCraftFeedbackItemId() : resultPreview.getHoverName().getString();
        List<CraftFeedbackIngredient> ingredients = controller.getCraftFeedbackIngredients();
        int visibleRows = Math.min(MAX_ROWS, ingredients.size());
        boolean hasOverflow = ingredients.size() > visibleRows;
        int panelH = 54 + (visibleRows * ROW_H) + (hasOverflow ? 14 : 0);
        int x = (screenWidth - PANEL_W) / 2;
        int y = 18;

        float progress = (controller.getCraftFeedbackExpiryMs() - now) / 2200.0F;
        int alpha = Mth.clamp((int) (255.0F * progress), 84, 255);
        int fill = (alpha << 24) | 0x18222C;
        int borderLight = (alpha << 24) | 0x6C839A;
        int borderDark = (alpha << 24) | 0x0D1117;
        int textColor = (alpha << 24) | 0xF2F7FF;
        int subColor = (alpha << 24) | 0xC9D8E6;

        g.pose().pushPose();
        g.pose().translate(0.0F, 0.0F, 700.0F);
        drawPanelFrame(g, x, y, PANEL_W, panelH, fill, borderLight, borderDark);
        if (!resultPreview.isEmpty()) {
            g .item(resultPreview, x + 8, y + 8);
        }
        g .text(font, "Crafted x" + controller.getCraftFeedbackCount(), x + 30, y + 9, textColor, false);
        g .text(font, font.plainSubstrByWidth(resultLabel, PANEL_W - 38), x + 30, y + 21, subColor, false);

        g .text(font, "Consumed", x + 8, y + 40, subColor, false);

        int rowY = y + 54;
        for (int i = 0; i < visibleRows; i++) {
            CraftFeedbackIngredient ingredient = ingredients.get(i);
            g.fill(x + 8, rowY - 2, x + PANEL_W - 8, rowY + 14, (alpha << 24) | 0x22303C);
            if (!ingredient.preview().isEmpty()) {
                g .item(ingredient.preview(), x + 10, rowY - 1);
            }
            String label = ingredient.label() == null || ingredient.label().isBlank() ? ingredient.itemId() : ingredient.label();
            g .text(font, font.plainSubstrByWidth(label, PANEL_W - 72), x + 30, rowY + 1, textColor, false);
            g .text(font, "x" + ingredient.count(), x + PANEL_W - 30, rowY + 1, subColor, false);
            rowY += ROW_H;
        }
        if (hasOverflow) {
            g .text(font, "+" + (ingredients.size() - visibleRows) + " more", x + 10, rowY + 1, subColor, false);
        }
        g.pose().popPose();
    }

    private static ItemStack resolvePreview(String itemId) {
        Identifier key = Identifier.tryParse(itemId == null ? "" : itemId);
        if (key == null || !BuiltInRegistries.ITEM.containsKey(key)) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(BuiltInRegistries.ITEM.getValue(key));
    }

    private static void drawPanelFrame(GuiGraphicsExtractor g, int x, int y, int w, int h, int fillColor, int light, int dark) {
        RtsClientUiUtil.drawPanelFrame(g, x, y, w, h, fillColor, light, dark);
    }
}
