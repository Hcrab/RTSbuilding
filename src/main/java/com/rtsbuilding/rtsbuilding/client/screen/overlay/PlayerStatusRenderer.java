package com.rtsbuilding.rtsbuilding.client.screen.overlay;

import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.input.overlay.LegacyGuiGraphics;
import com.rtsbuilding.rtsbuilding.client.screen.canvas.MinecraftUiCanvas;
import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uikit.canvas.PlayerStatusChromeRenderer;
import com.rtsbuilding.rtsbuilding.uikit.layout.PlayerStatusLayout;
import com.rtsbuilding.rtsbuilding.uikit.theme.PlayerStatusStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import com.rtsbuilding.rtsbuilding.platform.math.MathHelper;

import static com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreenConstants.TOP_H;

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
    public void render(LegacyGuiGraphics g) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.thePlayer == null || !mc.thePlayer.isEntityAlive()) return;

        EntityPlayer player = mc.thePlayer;
        float health = player.getHealth();
        float maxHealth = player.getMaxHealth();
        int food = player.getFoodStats().getFoodLevel();
        int armor = player.getTotalArmorValue();
        float absorption = player.getAbsorptionAmount();

        MinecraftUiCanvas canvas = new MinecraftUiCanvas(g, this.screen.font(), this.screen);
        int row = 0;

        // ---- Health Bar (red) ----
        float healthRatio = MathHelper.clamp(health / maxHealth, 0.0F, 1.0F);
        UiRect bounds = PlayerStatusLayout.bar(this.screen.width, TOP_H, row++);
        PlayerStatusChromeRenderer.renderBar(
                canvas, bounds, healthRatio, PlayerStatusStyle.health(healthRatio));
        g.drawString(this.screen.font(), String.format("HP %.0f/%.0f", health, maxHealth),
                (int) bounds.getX() + 4, (int) bounds.getY() + 1,
                PlayerStatusStyle.TEXT.toArgb(), false);

        // ---- Food Bar (gold) ----
        float foodRatio = MathHelper.clamp(food / 20.0F, 0.0F, 1.0F);
        bounds = PlayerStatusLayout.bar(this.screen.width, TOP_H, row++);
        PlayerStatusChromeRenderer.renderBar(
                canvas, bounds, foodRatio, PlayerStatusStyle.food(foodRatio));
        g.drawString(this.screen.font(), String.format("FD %d/20", food),
                (int) bounds.getX() + 4, (int) bounds.getY() + 1,
                PlayerStatusStyle.TEXT.toArgb(), false);

        // ---- Armor Bar (steel blue) ----
        float armorMax = Math.max(20, armor);
        float armorRatio = MathHelper.clamp(armor / armorMax, 0.0F, 1.0F);
        bounds = PlayerStatusLayout.bar(this.screen.width, TOP_H, row++);
        PlayerStatusChromeRenderer.renderBar(
                canvas, bounds, armorRatio, PlayerStatusStyle.ARMOR);
        g.drawString(this.screen.font(), String.format("AD %d", armor),
                (int) bounds.getX() + 4, (int) bounds.getY() + 1,
                PlayerStatusStyle.TEXT.toArgb(), false);

        // ---- Absorption Bar (golden yellow, only when active) ----
        if (absorption > 0.0F) {
            float absMax = Math.max(maxHealth, absorption);
            float absorptionRatio = MathHelper.clamp(absorption / absMax, 0.0F, 1.0F);
            bounds = PlayerStatusLayout.bar(this.screen.width, TOP_H, row);
            PlayerStatusChromeRenderer.renderBar(
                    canvas, bounds, absorptionRatio, PlayerStatusStyle.ABSORPTION);
            g.drawString(this.screen.font(), String.format("AB %.0f", absorption),
                    (int) bounds.getX() + 4, (int) bounds.getY() + 1,
                    PlayerStatusStyle.TEXT.toArgb(), false);
        }
    }
}
