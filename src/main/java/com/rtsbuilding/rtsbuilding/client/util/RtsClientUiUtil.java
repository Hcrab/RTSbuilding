package com.rtsbuilding.rtsbuilding.client.util;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import com.rtsbuilding.rtsbuilding.uicore.bottom.BottomBarUiFormats;

public final class RtsClientUiUtil {
    private static final float SLOT_COUNT_SCALE = 0.65F;
    private static final long EFFECTIVELY_INFINITE_COUNT = Long.MAX_VALUE;

    private RtsClientUiUtil() {
    }

    public static void drawPanelFrame(GuiGraphics guiGraphics, int x, int y, int w, int h, int fillColor, int light, int dark) {
        guiGraphics.fill(x, y, x + w, y + h, fillColor);
        guiGraphics.hLine(x, x + w, y, light);
        guiGraphics.hLine(x, x + w, y + h, dark);
        guiGraphics.vLine(x, y, y + h, light);
        guiGraphics.vLine(x + w, y, y + h, dark);
    }

    public static String trimToWidth(Font font, String text, int maxWidth) {
        if (text == null || text.isEmpty() || font == null || font.width(text) <= maxWidth) {
            return text == null ? "" : text;
        }
        String ellipsis = "...";
        int limit = Math.max(0, maxWidth - font.width(ellipsis));
        int cut = text.length();
        while (cut > 0 && font.width(text.substring(0, cut)) > limit) {
            cut--;
        }
        return text.substring(0, cut) + ellipsis;
    }

    public static void drawCenteredStringNoShadow(GuiGraphics guiGraphics, Font font, String text,
            int centerX, int y, int color) {
        String safeText = text == null ? "" : text;
        guiGraphics.drawString(font, safeText, centerX - font.width(safeText) / 2, y, color, false);
    }

    public static void drawCenteredStringNoShadow(GuiGraphics guiGraphics, Font font, Component text,
            int centerX, int y, int color) {
        drawCenteredStringNoShadow(guiGraphics, font, text == null ? "" : text.getString(), centerX, y, color);
    }

    public static String compactCount(long value) {
        return BottomBarUiFormats.compactCount(value);
    }

    public static String compactFluidAmount(long milliBuckets) {
        return BottomBarUiFormats.compactFluidAmount(milliBuckets);
    }

    public static void drawSlotCountOverlay(GuiGraphics guiGraphics, Font font, int slotX, int slotY, int slotSize, String countText, int color) {
        if (font == null || countText == null || countText.isEmpty()) {
            return;
        }

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0.0F, 0.0F, 300.0F);
        guiGraphics.fill(slotX + 1, slotY + slotSize - 7, slotX + slotSize - 1, slotY + slotSize - 1, 0xB0000000);
        guiGraphics.pose().translate(0.0F, 0.0F, 1.0F);
        guiGraphics.pose().scale(SLOT_COUNT_SCALE, SLOT_COUNT_SCALE, 1.0F);

        int scaledX = Math.round((slotX + slotSize - 2) / SLOT_COUNT_SCALE);
        int scaledY = Math.round((slotY + slotSize - 7) / SLOT_COUNT_SCALE);
        int textWidth = font.width(countText);
        guiGraphics.drawString(font, countText, scaledX - textWidth, scaledY, color, true);
        guiGraphics.pose().popPose();
    }
}
