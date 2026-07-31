package com.rtsbuilding.rtsbuilding.client.presentation.panel.select;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.rtsbuilding.rtsbuilding.client.util.animate.ColorAnimation;
import com.rtsbuilding.rtsbuilding.client.util.render.DarkUiPalette;
import com.rtsbuilding.rtsbuilding.client.util.render.SdfRenderer;
import com.rtsbuilding.rtsbuilding.client.util.render.TextRenderer;
import com.rtsbuilding.rtsbuilding.client.util.theme.ThemeManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.joml.Quaternionf;

public final class SelectEntryRenderer {

    

    
    private SelectEntryRenderer() {}

    

    
    public static void renderEntryBg(GuiGraphics g, int x, int y, int w, int h, float t) {
        int fill = ColorAnimation.lerpRGB(DarkUiPalette.bg(), DarkUiPalette.accent(), t);
        SdfRenderer.drawBorderedRoundedRect(g, x, y, w, h, 4, DarkUiPalette.black(), fill, 1);
    }

    

    
    public static void renderEntryContent(GuiGraphics g, SelectableEntry entry,
                                           int itemX, int itemY, int entryW,
                                           int iconSize, int iconTextGap, boolean isHovered) {
        int iconCenterX = itemX + entryW / 2;
        int iconY = itemY;

        switch (entry) {
            case EntityEntry ee -> renderEntityIcon(g, ee.entity(), iconCenterX, iconY + iconSize / 2, iconSize);
            case BlockEntry be -> renderBlockIcon(g, be, iconCenterX, iconY + iconSize / 2, iconSize);
        }

        int textColor = isHovered
                ? ThemeManager.getHoverTextColor()
                : ThemeManager.getTextColor();
        int textY = iconY + iconSize + iconTextGap;
        int textX = itemX + (entryW - Minecraft.getInstance().font.width(entry.displayName())) / 2;
        TextRenderer.draw(g, entry.displayName(), textX, textY, textColor);
    }

    

    private static void renderEntityIcon(GuiGraphics g, Entity entity, int centerX, int centerY, int iconSize) {
        if (entity == null) return;
        if (entity instanceof LivingEntity) {
            renderLivingEntityModel(g, entity, centerX, centerY, iconSize);
        } else {
            ItemStack stack = entity.getPickResult();
            renderItemIcon(g, stack, centerX, centerY, iconSize);
        }
    }

    private static void renderLivingEntityModel(GuiGraphics g, Entity entity, int centerX, int centerY, int iconSize) {
        Minecraft mc = Minecraft.getInstance();
        EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();

        float entitySize = Math.max(entity.getBbWidth(), entity.getBbHeight());
        float entityHeight = entity.getBbHeight();
        float scale = iconSize / Math.max(entitySize, 0.1F);
        scale = Math.min(scale, iconSize * 2.0f);

        dispatcher.setRenderShadow(false);

        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(centerX, centerY, 100.0);
        pose.scale(scale, -scale, -scale);
        pose.translate(0.0, -entityHeight / 2.0F, 0.0);
        pose.mulPose(new Quaternionf().rotationY((float) Math.PI));

        RenderSystem.depthMask(true);
        dispatcher.render(entity, 0.0, 0.0, 0.0, 0.0F, 1.0F, pose, bufferSource, 0xF000F0);
        pose.popPose();
        bufferSource.endBatch();

        
        RenderSystem.clear(256, false); 

        
        RenderSystem.depthMask(false);
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        dispatcher.setRenderShadow(true);
    }

    

    private static void renderBlockIcon(GuiGraphics g, BlockEntry be, int centerX, int centerY, int iconSize) {
        renderItemIcon(g, be.createStack(), centerX, centerY, iconSize);
    }

    

    private static void renderItemIcon(GuiGraphics g, ItemStack stack, int centerX, int centerY, int iconSize) {
        if (stack == null || stack.isEmpty()) return;
        float scale = (float) iconSize / 16.0f;
        var pose = g.pose();
        pose.pushPose();
        pose.translate(centerX, centerY, 0);
        pose.scale(scale, scale, 1.0f);

        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        g.renderItem(stack, -8, -8);

        RenderSystem.clear(256, false); 

        RenderSystem.depthMask(false);
        RenderSystem.disableDepthTest();

        pose.popPose();
    }
}
