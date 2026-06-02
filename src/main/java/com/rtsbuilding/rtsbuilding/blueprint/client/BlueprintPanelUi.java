package com.rtsbuilding.rtsbuilding.blueprint.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * Small stateless drawing and text helpers shared by the blueprint panel.
 *
 * <p>The main {@link BlueprintPanel} class owns the player-facing state machine:
 * selected blueprint, capture points, pinned preview, material dialog, and
 * placement controls. This helper deliberately owns none of that state. Keeping
 * these primitives here makes the panel easier to read without changing any
 * gameplay behavior or input priority.</p>
 */
final class BlueprintPanelUi {
    private BlueprintPanelUi() {
    }

    /**
     * Computes a compact button width from the localized label.
     *
     * <p>The caller still chooses the minimum and maximum because different UI
     * rows have different space budgets. This helper only centralizes the text
     * measurement and clamping rule used throughout the blueprint panel.</p>
     */
    static int buttonWidth(Font font, String key, int min, int max) {
        int labelWidth = font == null ? 0 : font.width(text(key));
        return Mth.clamp(labelWidth + 10, min, max);
    }

    /**
     * Draws a standard RTS blueprint button in its inactive or hover state.
     */
    static void drawButton(GuiGraphics g, Font font, int x, int y, int w, int h, String label, boolean hover) {
        drawButton(g, font, x, y, w, h, label, hover, false);
    }

    /**
     * Draws a standard RTS blueprint button, including the active-state color.
     *
     * <p>This intentionally stays a low-level paint primitive. Higher-level code
     * decides whether a button is enabled, hovered, active, or should trigger an
     * action.</p>
     */
    static void drawButton(GuiGraphics g, Font font, int x, int y, int w, int h, String label, boolean hover, boolean active) {
        int fill = active ? 0xCC2E6A50 : (hover ? 0xCC334052 : 0xAA24303C);
        drawFrame(g, x, y, w, h, fill, 0xFF64788E, 0xFF0D1015);
        g.drawCenteredString(font, trim(font, label, w - 6), x + w / 2, y + 3, 0xFFEAF2FF);
    }

    /**
     * Draws the thin framed rectangles used by the blueprint panel.
     */
    static void drawFrame(GuiGraphics g, int x, int y, int w, int h, int fill, int light, int dark) {
        g.fill(x, y, x + w, y + h, fill);
        g.hLine(x, x + w, y, light);
        g.hLine(x, x + w, y + h, dark);
        g.vLine(x, y, y + h, light);
        g.vLine(x + w, y, y + h, dark);
    }

    /**
     * Returns whether a mouse coordinate is inside a UI rectangle.
     */
    static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }

    /**
     * Resolves a translation key to the current language text.
     */
    static String text(String key) {
        return Component.translatable(key).getString();
    }

    /**
     * Resolves a translation key with arguments to the current language text.
     */
    static String text(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    /**
     * Trims a string to fit within a pixel width and appends an ellipsis.
     *
     * <p>Minecraft's bitmap font is not monospaced across all glyphs and active
     * languages, so this uses the live {@link Font} measurement instead of a
     * character count.</p>
     */
    static String trim(Font font, String text, int maxWidth) {
        if (font == null || text == null || font.width(text) <= maxWidth) {
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
}
