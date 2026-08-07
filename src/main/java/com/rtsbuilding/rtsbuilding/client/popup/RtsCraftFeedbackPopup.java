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
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** 完成合成后的短暂反馈：只用 Kit 样式渲染，不改变合成或储存逻辑。 */
public final class RtsCraftFeedbackPopup {
    private RtsCraftFeedbackPopup() {
    }

    public static void render(
            GuiGraphicsExtractor graphics, Font font, int screenWidth,
            ClientRtsController controller) {
        render(graphics, font, screenWidth, CraftFeedbackLayout.TOP, controller);
    }

    public static void render(
            GuiGraphicsExtractor graphics, Font font, int screenWidth,
            int reservedTop, ClientRtsController controller) {
        if (graphics == null || font == null || controller == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now >= controller.getCraftFeedbackExpiryMs() || controller.getCraftFeedbackCount() <= 0) {
            return;
        }

        ItemStack resultPreview = resolvePreview(controller.getCraftFeedbackItemId());
        String resultLabel = resultPreview.isEmpty()
                ? controller.getCraftFeedbackItemId() : resultPreview.getHoverName().getString();
        List<CraftFeedbackIngredient> ingredients = controller.getCraftFeedbackIngredients();
        int visibleRows = CraftFeedbackLayout.visibleRows(ingredients.size());
        boolean hasOverflow = ingredients.size() > visibleRows;
        int panelH = CraftFeedbackLayout.panelHeight(ingredients.size());
        int x = CraftFeedbackLayout.panelX(screenWidth);
        int y = CraftFeedbackLayout.panelY(reservedTop);

        double progress = (controller.getCraftFeedbackExpiryMs() - now) / 2200.0D;
        int alpha = CraftFeedbackStyle.alpha(progress);
        UiColor fill = CraftFeedbackStyle.faded(CraftFeedbackStyle.PANEL, alpha);
        UiColor borderLight = CraftFeedbackStyle.faded(CraftFeedbackStyle.BORDER_LIGHT, alpha);
        UiColor borderDark = CraftFeedbackStyle.faded(CraftFeedbackStyle.BORDER_DARK, alpha);
        UiColor textColor = CraftFeedbackStyle.faded(CraftFeedbackStyle.TEXT, alpha);
        UiColor subColor = CraftFeedbackStyle.faded(CraftFeedbackStyle.SECONDARY_TEXT, alpha);
        UiColor rowColor = CraftFeedbackStyle.faded(CraftFeedbackStyle.ROW, alpha);

        graphics.pose().pushMatrix();
        UiChromeRenderer.frame(new MinecraftUiCanvas(graphics, font),
                new UiRect(x, y, CraftFeedbackLayout.PANEL_W, panelH), 1.0D,
                fill, borderLight, borderDark);
        if (!resultPreview.isEmpty()) {
            graphics.item(resultPreview, x + 8, y + 8);
        }
        graphics.text(font, "Crafted x" + controller.getCraftFeedbackCount(),
                x + 30, y + 9, textColor.toArgb(), false);
        graphics.text(font, font.plainSubstrByWidth(
                        resultLabel, CraftFeedbackLayout.PANEL_W - 38),
                x + 30, y + 21, subColor.toArgb(), false);
        graphics.text(font, "Consumed", x + 8, y + 40, subColor.toArgb(), false);

        int rowY = y + CraftFeedbackLayout.BASE_H;
        for (int index = 0; index < visibleRows; index++) {
            CraftFeedbackIngredient ingredient = ingredients.get(index);
            graphics.fill(x + 8, rowY - 2,
                    x + CraftFeedbackLayout.PANEL_W - 8, rowY + 14, rowColor.toArgb());
            if (!ingredient.preview().isEmpty()) {
                graphics.item(ingredient.preview(), x + 10, rowY - 1);
            }
            String label = ingredient.label() == null || ingredient.label().isBlank()
                    ? ingredient.itemId() : ingredient.label();
            graphics.text(font, font.plainSubstrByWidth(label, CraftFeedbackLayout.PANEL_W - 72),
                    x + 30, rowY + 1, textColor.toArgb(), false);
            graphics.text(font, "x" + ingredient.count(),
                    x + CraftFeedbackLayout.PANEL_W - 30, rowY + 1,
                    subColor.toArgb(), false);
            rowY += CraftFeedbackLayout.ROW_H;
        }
        if (hasOverflow) {
            graphics.text(font, "+" + (ingredients.size() - visibleRows) + " more",
                    x + 10, rowY + 1, subColor.toArgb(), false);
        }
        graphics.pose().popMatrix();
    }

    private static ItemStack resolvePreview(String itemId) {
        Identifier key = Identifier.tryParse(itemId == null ? "" : itemId);
        if (key == null || !BuiltInRegistries.ITEM.containsKey(key)) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(BuiltInRegistries.ITEM.getValue(key));
    }
}
