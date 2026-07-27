package com.rtsbuilding.rtsbuilding.client.popup;

import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.input.overlay.LegacyGuiGraphics;
import com.rtsbuilding.rtsbuilding.client.record.CraftFeedbackIngredient;
import com.rtsbuilding.rtsbuilding.uikit.layout.CraftFeedbackLayout;
import com.rtsbuilding.rtsbuilding.uikit.theme.CraftFeedbackStyle;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiColor;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import org.lwjgl.opengl.GL11;

import java.util.List;

/** 绘制短暂的合成结果与材料消耗反馈，不拥有反馈数据或过期计时。 */
public final class RtsCraftFeedbackPopup {
    private RtsCraftFeedbackPopup() {
    }

    public static void render(LegacyGuiGraphics g, FontRenderer font, int screenWidth,
                              ClientRtsController controller) {
        render(g, font, screenWidth, CraftFeedbackLayout.TOP, controller);
    }

    public static void render(LegacyGuiGraphics g, FontRenderer font, int screenWidth,
                              int reservedTop, ClientRtsController controller) {
        if (g == null || font == null || controller == null) return;
        long now = System.currentTimeMillis();
        if (now >= controller.getCraftFeedbackExpiryMs() || controller.getCraftFeedbackCount() <= 0) return;

        ItemStack resultPreview = resolvePreview(controller.getCraftFeedbackItemId());
        String resultLabel = resultPreview.isEmpty()
                ? safe(controller.getCraftFeedbackItemId()) : resultPreview.getDisplayName();
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

        g.pushPose();
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GlStateManager.translate(0.0F, 0.0F, 700.0F);
        GlStateManager.disableDepth();
        drawPanelFrame(g, x, y, CraftFeedbackLayout.PANEL_W, panelH,
                fill, borderLight, borderDark);
        if (!resultPreview.isEmpty()) {
            g.renderItem(resultPreview, x + 8, y + 8);
            GlStateManager.disableDepth();
        }
        g.drawString(font, "Crafted x" + controller.getCraftFeedbackCount(),
                x + 30, y + 9, textColor.toArgb());
        g.drawString(font, font.trimStringToWidth(resultLabel, CraftFeedbackLayout.PANEL_W - 38),
                x + 30, y + 21, subColor.toArgb());
        g.drawString(font, "Consumed", x + 8, y + 40, subColor.toArgb());

        int rowY = y + CraftFeedbackLayout.BASE_H;
        for (int i = 0; i < visibleRows; i++) {
            CraftFeedbackIngredient ingredient = ingredients.get(i);
            g.fill(x + 8, rowY - 2, x + CraftFeedbackLayout.PANEL_W - 8,
                    rowY + 14, rowColor.toArgb());
            if (ingredient.preview() != null && !ingredient.preview().isEmpty()) {
                g.renderItem(ingredient.preview(), x + 10, rowY - 1);
                GlStateManager.disableDepth();
            }
            String label = ingredient.label() == null || ingredient.label().trim().isEmpty()
                    ? safe(ingredient.itemId()) : ingredient.label();
            g.drawString(font, font.trimStringToWidth(label, CraftFeedbackLayout.PANEL_W - 72),
                    x + 30, rowY + 1, textColor.toArgb());
            g.drawString(font, "x" + ingredient.count(),
                    x + CraftFeedbackLayout.PANEL_W - 30, rowY + 1, subColor.toArgb());
            rowY += CraftFeedbackLayout.ROW_H;
        }
        if (hasOverflow) {
            g.drawString(font, "+" + (ingredients.size() - visibleRows) + " more",
                    x + 10, rowY + 1, subColor.toArgb());
        }
        GL11.glPopAttrib();
        g.popPose();
    }

    private static ItemStack resolvePreview(String itemId) {
        if (itemId == null || itemId.trim().isEmpty()) return ItemStack.EMPTY;
        try {
            ResourceLocation key = new ResourceLocation(itemId);
            Item item = ForgeRegistries.ITEMS.getValue(key);
            return item == null ? ItemStack.EMPTY : new ItemStack(item);
        } catch (RuntimeException ignored) {
            return ItemStack.EMPTY;
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static void drawPanelFrame(LegacyGuiGraphics g, int x, int y, int w, int h,
                                       UiColor fill, UiColor light, UiColor dark) {
        g.fill(x, y, x + w, y + h, fill.toArgb());
        g.fill(x, y, x + w, y + 1, light.toArgb());
        g.fill(x, y, x + 1, y + h, light.toArgb());
        g.fill(x, y + h - 1, x + w, y + h, dark.toArgb());
        g.fill(x + w - 1, y, x + w, y + h, dark.toArgb());
    }
}
