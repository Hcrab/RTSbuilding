package com.rtsbuilding.rtsbuilding.client.screen.overlay;

import com.rtsbuilding.rtsbuilding.client.screen.BuilderScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

import java.util.function.Function;

import static com.rtsbuilding.rtsbuilding.client.screen.BuilderScreenConstants.TOP_H;

/**
 * 在 RTS 界面右上角绘制玩家生命、饥饿、护甲和吸收状态。
 *
 * <p>这个渲染器只读取本地玩家的当前状态，不持有业务状态；设置开关由
 * {@link com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController}
 * 管理。</p>
 */
public final class PlayerStatusRenderer {
    private final BuilderScreen screen;

    public PlayerStatusRenderer(BuilderScreen screen) {
        this.screen = screen;
    }

    public void render(GuiGraphics g) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null || minecraft.player.isRemoved()) {
            return;
        }
        var player = minecraft.player;
        float health = player.getHealth();
        float maxHealth = player.getMaxHealth();
        int food = player.getFoodData().getFoodLevel();
        int armor = player.getArmorValue();
        float absorption = player.getAbsorptionAmount();

        int barW = 130;
        int barH = 10;
        int right = this.screen.width - 8;
        int x = right - barW;
        int y = TOP_H + 4;
        int gap = 2;

        drawStatusBar(g, x, y, barW, barH,
                Mth.clamp(health / Math.max(1.0F, maxHealth), 0.0F, 1.0F),
                pct -> pct > 0.5F ? 0xFFD04040 : (pct > 0.25F ? 0xFFD08030 : 0xFFC03020));
        g.drawString(this.screen.font(), String.format("HP %.0f/%.0f", health, maxHealth),
                x + 4, y + 1, 0xFFFFFFFF, false);
        y += barH + gap;

        drawStatusBar(g, x, y, barW, barH,
                Mth.clamp(food / 20.0F, 0.0F, 1.0F),
                pct -> pct > 0.5F ? 0xFFC89030 : (pct > 0.25F ? 0xFFB07820 : 0xFFA06010));
        g.drawString(this.screen.font(), String.format("FD %d/20", food),
                x + 4, y + 1, 0xFFFFFFFF, false);
        y += barH + gap;

        float armorMax = Math.max(20.0F, armor);
        drawStatusBar(g, x, y, barW, barH,
                Mth.clamp(armor / armorMax, 0.0F, 1.0F),
                pct -> 0xFF6B8FA0);
        g.drawString(this.screen.font(), String.format("AD %d", armor),
                x + 4, y + 1, 0xFFFFFFFF, false);
        y += barH + gap;

        if (absorption > 0.0F) {
            float absorptionMax = Math.max(maxHealth, absorption);
            drawStatusBar(g, x, y, barW, barH,
                    Mth.clamp(absorption / absorptionMax, 0.0F, 1.0F),
                    pct -> 0xFFE8C840);
            g.drawString(this.screen.font(), String.format("AB %.0f", absorption),
                    x + 4, y + 1, 0xFFFFFFFF, false);
        }
    }

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
