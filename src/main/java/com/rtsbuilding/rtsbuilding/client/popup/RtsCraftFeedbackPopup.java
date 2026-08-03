package com.rtsbuilding.rtsbuilding.client.popup;

import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.record.CraftFeedbackIngredient;
import com.rtsbuilding.rtsbuilding.client.screen.canvas.MinecraftUiCanvas;
import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uikit.canvas.UiChromeRenderer;
import com.rtsbuilding.rtsbuilding.uikit.layout.CraftFeedbackLayout;
import com.rtsbuilding.rtsbuilding.uikit.theme.CraftFeedbackStyle;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiColor;
import net.minecraft.client.gui.Font;
import com.rtsbuilding.rtsbuilding.client.screen.canvas.RtsGuiContext;
import com.rtsbuilding.rtsbuilding.platform.RtsBuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public final class RtsCraftFeedbackPopup {
    private RtsCraftFeedbackPopup() {
    }

    public static void render(RtsGuiContext g, Font font, int screenWidth, ClientRtsController controller) {
        render(g, font, screenWidth, CraftFeedbackLayout.TOP, controller);
    }

    public static void render(RtsGuiContext g, Font font, int screenWidth,
                              int reservedTop, ClientRtsController controller) {
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
        int visibleRows = CraftFeedbackLayout.visibleRows(ingredients.size());
        boolean hasOverflow = ingredients.size() > visibleRows;
        int panelH = CraftFeedbackLayout.panelHeight(ingredients.size());
        int x = CraftFeedbackLayout.panelX(screenWidth);
        int y = CraftFeedbackLayout.panelY(reservedTop);

        double progress = (controller.getCraftFeedbackExpiryMs() - now) / 2200.0D;
        int alpha = CraftFeedbackStyle.alpha(progress);
        UiColor fill = CraftFeedbackStyle.faded(CraftFeedbackStyle.PANEL, alpha);
        UiColor borderLight = CraftFeedbackStyle.faded(
                CraftFeedbackStyle.BORDER_LIGHT, alpha);
        UiColor borderDark = CraftFeedbackStyle.faded(
                CraftFeedbackStyle.BORDER_DARK, alpha);
        UiColor textColor = CraftFeedbackStyle.faded(CraftFeedbackStyle.TEXT, alpha);
        UiColor subColor = CraftFeedbackStyle.faded(
                CraftFeedbackStyle.SECONDARY_TEXT, alpha);
        UiColor rowColor = CraftFeedbackStyle.faded(CraftFeedbackStyle.ROW, alpha);

        g.pose().pushPose();
        g.pose().translate(0.0F, 0.0F, 700.0F);
        UiChromeRenderer.frame(new MinecraftUiCanvas(g, font),
                new UiRect(x, y, CraftFeedbackLayout.PANEL_W, panelH), 1.0D,
                fill, borderLight, borderDark);
        if (!resultPreview.isEmpty()) {
            g.renderItem(resultPreview, x + 8, y + 8);
        }
        g.drawString(font, "Crafted x" + controller.getCraftFeedbackCount(),
                x + 30, y + 9, textColor.toArgb(), false);
        g.drawString(font, font.plainSubstrByWidth(
                        resultLabel, CraftFeedbackLayout.PANEL_W - 38),
                x + 30, y + 21, subColor.toArgb(), false);

        g.drawString(font, "Consumed", x + 8, y + 40, subColor.toArgb(), false);

        int rowY = y + CraftFeedbackLayout.BASE_H;
        for (int i = 0; i < visibleRows; i++) {
            CraftFeedbackIngredient ingredient = ingredients.get(i);
            g.fill(x + 8, rowY - 2,
                    x + CraftFeedbackLayout.PANEL_W - 8, rowY + 14,
                    rowColor.toArgb());
            if (!ingredient.preview().isEmpty()) {
                g.renderItem(ingredient.preview(), x + 10, rowY - 1);
            }
            String label = ingredient.label() == null || ingredient.label().isBlank() ? ingredient.itemId() : ingredient.label();
            g.drawString(font, font.plainSubstrByWidth(
                            label, CraftFeedbackLayout.PANEL_W - 72),
                    x + 30, rowY + 1, textColor.toArgb(), false);
            g.drawString(font, "x" + ingredient.count(),
                    x + CraftFeedbackLayout.PANEL_W - 30, rowY + 1,
                    subColor.toArgb(), false);
            rowY += CraftFeedbackLayout.ROW_H;
        }
        if (hasOverflow) {
            g.drawString(font, "+" + (ingredients.size() - visibleRows) + " more",
                    x + 10, rowY + 1, subColor.toArgb(), false);
        }
        g.pose().popPose();
    }

    private static ItemStack resolvePreview(String itemId) {
        ResourceLocation key = ResourceLocation.tryParse(itemId == null ? "" : itemId);
        if (key == null || !RtsBuiltInRegistries.ITEM.containsKey(key)) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(RtsBuiltInRegistries.ITEM.get(key));
    }
}
