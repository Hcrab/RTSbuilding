package com.rtsbuilding.rtsbuilding.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

import java.util.function.Function;

import static com.rtsbuilding.rtsbuilding.client.screen.BuilderScreenConstants.TOP_H;

/**
 * Renders the player's health, food, armor and absorption status bars at the
 * top-right of the RTS builder screen using a compact RTS-style design.
 *
 * <p>This class is intentionally stateless — every frame reads fresh data from
 * the local player and draws directly. It holds no animation state or cached
 * values, so it can be safely shared or recreated as needed.
 */
public final class PlayerStatusRenderer {

    private final BuilderScreen screen;

    public PlayerStatusRenderer(BuilderScreen screen) {
        this.screen = screen;
    }

    /**
     * Renders all player status bars (HP, food, armor, absorption) at the
     * top-right corner of the screen. Absorption is only drawn when active.
     */
    public void render(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.player.isRemoved()) return;

        var player = mc.player;
        float health = player.getHealth();
        float maxHealth = player.getMaxHealth();
        int food = player.getFoodData().getFoodLevel();
        int armor = player.getArmorValue();
        float absorption = player.getAbsorptionAmount();

        int barW = 130;
        int barH = 10;
        int right = this.screen.width - 8;
        int top = TOP_H + 4;
        int gap = 2;

        int hx = right - barW;
        int y = top;

        // ---- Health Bar (red) ----
        drawStatusBar(g, hx, y, barW, barH,
                Mth.clamp(health / maxHealth, 0.0F, 1.0F),
                pct -> pct > 0.5F ? 0xFFD04040 : (pct > 0.25F ? 0xFFD08030 : 0xFFC03020));
        g.drawString(this.screen.font(), String.format("HP %.0f/%.0f", health, maxHealth),
                hx + 4, y + 1, 0xFFFFFFFF, false);
        y += barH + gap;

        // ---- Food Bar (gold) ----
        drawStatusBar(g, hx, y, barW, barH,
                Mth.clamp(food / 20.0F, 0.0F, 1.0F),
                pct -> pct > 0.5F ? 0xFFC89030 : (pct > 0.25F ? 0xFFB07820 : 0xFFA06010));
        g.drawString(this.screen.font(), String.format("FD %d/20", food),
                hx + 4, y + 1, 0xFFFFFFFF, false);
        y += barH + gap;

        // ---- Armor Bar (steel blue) ----
        float armorMax = Math.max(20, armor);
        drawStatusBar(g, hx, y, barW, barH,
                Mth.clamp(armor / armorMax, 0.0F, 1.0F),
                pct -> 0xFF6B8FA0);
        g.drawString(this.screen.font(), String.format("AD %d", armor),
                hx + 4, y + 1, 0xFFFFFFFF, false);
        y += barH + gap;

        // ---- Absorption Bar (golden yellow, only when active) ----
        if (absorption > 0.0F) {
            float absMax = Math.max(maxHealth, absorption);
            drawStatusBar(g, hx, y, barW, barH,
                    Mth.clamp(absorption / absMax, 0.0F, 1.0F),
                    pct -> 0xFFE8C840);
            g.drawString(this.screen.font(), String.format("AB %.0f", absorption),
                    hx + 4, y + 1, 0xFFFFFFFF, false);
        }
    }

    /**
     * Draws a single compact status bar with background frame and colored fill.
     *
     * @param g        the graphics context
     * @param x        left edge of the bar
     * @param y        top edge of the bar
     * @param w        total width of the bar (including borders)
     * @param h        total height of the bar (including borders)
     * @param fillPct  fill ratio in [0, 1]
     * @param colorFn  maps the fill ratio to the fill ARGB colour
     */
    private static void drawStatusBar(GuiGraphics g, int x, int y, int w, int h,
                                       float fillPct, Function<Float, Integer> colorFn) {
        g.fill(x, y, x + w, y + h, 0xAA1A1E24);
        g.hLine(x, x + w, y, 0xFF3C4A5A);
        g.hLine(x, x + w, y + h, 0xFF0A0D12);
        g.vLine(x, y, y + h, 0xFF3C4A5A);
        g.vLine(x + w, y, y + h, 0xFF0A0D12);

        int fillW = Math.max(0, (int) ((w - 2) * fillPct));
        if (fillW > 0) {
            g.fill(x + 1, y + 1, x + 1 + fillW, y + h - 1, colorFn.apply(fillPct));
        }
    }
}
