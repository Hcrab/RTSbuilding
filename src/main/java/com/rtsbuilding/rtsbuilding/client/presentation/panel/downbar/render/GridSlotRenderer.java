package com.rtsbuilding.rtsbuilding.client.presentation.panel.downbar.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.rtsbuilding.rtsbuilding.client.util.render.SpriteRenderer;
import com.rtsbuilding.rtsbuilding.client.util.render.model.SpriteRegion;
import com.rtsbuilding.rtsbuilding.client.util.render.model.TextureInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;


public final class GridSlotRenderer {

    

    
    public static final int SLOT_SIZE = 18;
    
    public static final int ICON_OFFSET = 1;

    

    
    public static final float AMOUNT_SCALE = 0.666f;
    
    public static final float INV_AMOUNT_SCALE = 1.0f / AMOUNT_SCALE;
    
    public static final int AMOUNT_COLOR = 0xFF_FFFFFF;
    
    public static final int FLUID_AMOUNT_COLOR = 0xFF_80C8FF;

    

    
    private static final ResourceLocation SLOTS_TEXTURE = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/down/slots.png");
    private static final int SLOTS_TEX_W = 32;
    private static final int SLOTS_TEX_H = 48;
    private static final int SLOTS_STATE_H = 16;
    
    private static final int SLOTS_SELECTED_V_OFFSET = 32;
    private static final TextureInfo SLOTS_TEX_INFO = new TextureInfo(
            SLOTS_TEXTURE, SLOTS_TEX_W, SLOTS_TEX_H,
            TextureInfo.ThemeLayout.HORIZONTAL_PAIR,
            TextureInfo.FilterMode.PIXEL);
    
    public static final SpriteRegion SLOT_NORMAL = new SpriteRegion(
            SLOTS_TEX_INFO, 0, 0, SLOTS_TEX_W / 2, SLOTS_STATE_H);
    
    public static final SpriteRegion SLOT_HOVER = new SpriteRegion(
            SLOTS_TEX_INFO, 0, SLOTS_STATE_H, SLOTS_TEX_W / 2, SLOTS_STATE_H);
    
    public static final SpriteRegion SLOT_SELECTED = new SpriteRegion(
            SLOTS_TEX_INFO, 0, SLOTS_SELECTED_V_OFFSET, SLOTS_TEX_W / 2, SLOTS_STATE_H);

    private GridSlotRenderer() {}

    

    
    public static void drawIcon(GuiGraphics g, ItemStack stack, int slotX, int slotY) {
        if (stack == null || stack.isEmpty()) return;

        RenderSystem.disableDepthTest();
        int iconX = slotX + ICON_OFFSET;
        int iconY = slotY + ICON_OFFSET;
        var pose = g.pose();
        pose.pushPose();
        pose.translate(iconX, iconY, 0);
        g.renderItem(stack, 0, 0);
        pose.popPose();

        RenderSystem.enableDepthTest();
        g.renderItemDecorations(Minecraft.getInstance().font, stack, iconX, iconY);
        RenderSystem.disableDepthTest();
    }

    

    
    public static void drawOverlay(GuiGraphics g, int slotX, int slotY,
                                   boolean hovered, boolean selected, int slotThemeOffset) {
        RenderSystem.disableDepthTest();
        var pose = g.pose();
        pose.pushPose();
        pose.translate(slotX, slotY, 300);

        if (selected) {
            SpriteRenderer.drawSprite(g, SLOT_SELECTED, slotThemeOffset, 0, 0, SLOT_SIZE, SLOT_SIZE);
        } else if (hovered) {
            SpriteRenderer.drawSprite(g, SLOT_HOVER, slotThemeOffset, 0, 0, SLOT_SIZE, SLOT_SIZE);
        }

        pose.popPose();
    }

    

    
    public static void drawAmountText(GuiGraphics g, Font font, long count,
                                       int slotX, int slotY, boolean isFluid) {
        if (count < 1) return;

        String text = formatAmount(count);
        int textW = font.width(text);

        int tx = (int) ((slotX + SLOT_SIZE) * INV_AMOUNT_SCALE - textW);
        int ty = (int) ((slotY + SLOT_SIZE) * INV_AMOUNT_SCALE - font.lineHeight);

        g.pose().pushPose();
        g.pose().scale(AMOUNT_SCALE, AMOUNT_SCALE, 1.0f);
        g.pose().translate(tx, ty, 200);

        int color = isFluid ? FLUID_AMOUNT_COLOR : AMOUNT_COLOR;
        g.drawString(font, text, 1, 1, 0xFF_000000, false);
        g.drawString(font, text, 0, 0, color, false);

        g.pose().popPose();
    }

    

    
    public static String formatAmount(long count) {
        if (count >= 1_000_000_000L) {
            double val = count / 100_000_000.0;
            return String.format("%.1fB", val / 10.0);
        } else if (count >= 1_000_000L) {
            double val = count / 100_000.0;
            return String.format("%.1fM", val / 10.0);
        } else if (count >= 1_000L) {
            double val = count / 100.0;
            return String.format("%.1fK", val / 10.0);
        }
        return String.valueOf(count);
    }
}
