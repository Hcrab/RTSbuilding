package com.rtsbuilding.rtsbuilding.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

final class RtsClientUiUtil {
    private RtsClientUiUtil() {
    }

    static void drawPanelFrame(GuiGraphics guiGraphics, int x, int y, int w, int h, int fillColor, int light, int dark) {
        guiGraphics.fill(x, y, x + w, y + h, fillColor);
        guiGraphics.hLine(x, x + w, y, light);
        guiGraphics.hLine(x, x + w, y + h, dark);
        guiGraphics.vLine(x, y, y + h, light);
        guiGraphics.vLine(x + w, y, y + h, dark);
    }

    static String trimToWidth(Font font, String text, int maxWidth) {
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

    static String compactCount(long value) {
        if (value >= 1_000_000L) {
            return String.format("%.1fM", value / 1_000_000.0);
        }
        if (value >= 1_000L) {
            return String.format("%.1fK", value / 1_000.0);
        }
        return Long.toString(value);
    }

    static String compactFluidAmount(long milliBuckets) {
        long buckets = Math.max(0L, milliBuckets / 1000L);
        if (buckets >= 1_000_000L) {
            return String.format("%.1fM B", buckets / 1_000_000.0);
        }
        if (buckets >= 1_000L) {
            return String.format("%.1fK B", buckets / 1_000.0);
        }
        return buckets + " B";
    }
}
