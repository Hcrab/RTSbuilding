package com.rtsbuilding.rtsbuilding.client.util;

import com.rtsbuilding.rtsbuilding.client.input.overlay.LegacyGuiGraphics;
import com.rtsbuilding.rtsbuilding.uicore.bottom.BottomBarUiFormats;
import com.rtsbuilding.rtsbuilding.uikit.theme.RtsMainlineTheme;
import net.minecraft.client.gui.FontRenderer;
import com.rtsbuilding.rtsbuilding.platform.render.GlStateManager;

public final class RtsClientUiUtil {
    private static final float SLOT_COUNT_SCALE = 0.65F;
    private static final long EFFECTIVELY_INFINITE_COUNT = Long.MAX_VALUE;

    private RtsClientUiUtil() {
    }

    public static String trimToWidth(FontRenderer font, String text, int maxWidth) {
        if (text == null || text.isEmpty() || font == null || font.getStringWidth(text) <= maxWidth) {
            return text == null ? "" : text;
        }
        String ellipsis = "...";
        int limit = Math.max(0, maxWidth - font.getStringWidth(ellipsis));
        int cut = text.length();
        while (cut > 0 && font.getStringWidth(text.substring(0, cut)) > limit) {
            cut--;
        }
        return text.substring(0, cut) + ellipsis;
    }

    public static void drawCenteredStringNoShadow(LegacyGuiGraphics guiGraphics, FontRenderer font, String text,
            int centerX, int y, int color) {
        String safeText = text == null ? "" : text;
        guiGraphics.drawString(font, safeText, centerX - font.getStringWidth(safeText) / 2, y, color, false);
    }

    public static String compactCount(long value) {
        return BottomBarUiFormats.compactCount(value);
    }

    public static String compactFluidAmount(long milliBuckets) {
        return BottomBarUiFormats.compactFluidAmount(milliBuckets);
    }

    public static void drawSlotCountOverlay(LegacyGuiGraphics guiGraphics, FontRenderer font, int slotX, int slotY, int slotSize, String countText, int color) {
        if (font == null || countText == null || countText.isEmpty()) {
            return;
        }

        guiGraphics.pushPose();
        GlStateManager.translate(0.0F, 0.0F, 300.0F);
        guiGraphics.fill(slotX + 1, slotY + slotSize - 7,
                slotX + slotSize - 1, slotY + slotSize - 1,
                RtsMainlineTheme.SLOT_COUNT_BACKGROUND.toArgb());
        GlStateManager.translate(0.0F, 0.0F, 1.0F);
        guiGraphics.scale(SLOT_COUNT_SCALE, SLOT_COUNT_SCALE, 1.0F);

        int scaledX = Math.round((slotX + slotSize - 2) / SLOT_COUNT_SCALE);
        int scaledY = Math.round((slotY + slotSize - 7) / SLOT_COUNT_SCALE);
        int textWidth = font.getStringWidth(countText);
        // 底栏和浮窗可能互相覆盖，数量文字不使用阴影，避免阴影批次穿透后绘制的半透明面板。
        guiGraphics.drawString(font, countText, scaledX - textWidth, scaledY, color, false);
        guiGraphics.popPose();
    }
}
