package com.rtsbuilding.rtsbuilding.client.screen.panel.select;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.rtsbuilding.rtsbuilding.client.util.render.CrossFadeRenderer;
import com.rtsbuilding.rtsbuilding.client.util.render.SpriteRenderer;
import com.rtsbuilding.rtsbuilding.client.util.render.TextRenderer;
import com.rtsbuilding.rtsbuilding.client.util.render.model.NineSliceRegion;
import com.rtsbuilding.rtsbuilding.client.util.render.model.TextureInfo;
import com.rtsbuilding.rtsbuilding.client.util.theme.ThemeManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.joml.Quaternionf;

/**
 * 条目渲染器——负责渲染 {@link SelectPanel} 中每个菜单项的可视内容。
 *
 * <p>职责单一：条目背景九宫格 + 实体模型/方块图标 + 文字标签。
 * 不持有可变状态，所有数据通过方法参数传入。</p>
 */
public final class SelectEntryRenderer {

    // ======================== 条目背景贴图 (base_ui_2.png) ========================

    /** base_ui_2.png：32×48，水平左暗右亮，垂直上正常下悬浮 */
    private static final ResourceLocation SELECT_BG_TEXTURE = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/base/base_ui/base_ui_2.png");
    private static final int SELECT_BG_TEX_W = 32;
    private static final int SELECT_BG_TEX_FILE_H = 48;
    /** 单个状态高度（0-16=正常态，16-32=悬浮态，无选中态） */
    private static final int SELECT_BG_STATE_H = 16;
    /** 九宫格边框宽度 */
    private static final int SELECT_BG_BORDER = 4;
    private static final TextureInfo SELECT_BG_TEX_INFO = new TextureInfo(
            SELECT_BG_TEXTURE, SELECT_BG_TEX_W, SELECT_BG_TEX_FILE_H,
            TextureInfo.ThemeLayout.HORIZONTAL_PAIR,
            TextureInfo.FilterMode.PIXEL);
    private static final NineSliceRegion SELECT_BG_NINE_SLICE = NineSliceRegion.fullTheme(
            SELECT_BG_TEX_INFO, SELECT_BG_STATE_H, SELECT_BG_BORDER);

    private SelectEntryRenderer() {}

    // ======================== 条目背景渲染 ========================

    /**
     * 渲染条目背景——使用 base_ui_2.png 九宫格贴图，正常态与悬浮态交叉淡入淡出。
     *
     * @param t 悬浮动画进度 [0, 1]
     */
    public static void renderEntryBg(GuiGraphics g, int x, int y, int w, int h, float t) {
        CrossFadeRenderer.render(t,
                () -> SpriteRenderer.drawNineSlice(g,
                        SELECT_BG_NINE_SLICE.withTheme().withVOffset(0),
                        x, y, w, h),
                () -> SpriteRenderer.drawNineSlice(g,
                        SELECT_BG_NINE_SLICE.withTheme().withVOffset(SELECT_BG_STATE_H),
                        x, y, w, h));
    }

    // ======================== 条目内容渲染（图标 + 文字）=======================

    /**
     * 渲染条目内容——实体模型/方块图标 + 下方名称文字。
     *
     * @param entry        条目数据
     * @param itemX        条目左上 X
     * @param itemY        条目左上 Y
     * @param entryW       条目宽度
     * @param iconSize     图标渲染尺寸
     * @param iconTextGap  图标与文字的间距
     * @param isHovered    是否悬停
     */
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

    // ======================== 实体图标渲染 ========================

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

        // ★ 清空深度缓冲：抹掉实体模型写入的深度值
        RenderSystem.clear(256, false); // GL_DEPTH_BUFFER_BIT

        // 恢复 GUI 默认 GL 状态
        RenderSystem.depthMask(false);
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        dispatcher.setRenderShadow(true);
    }

    // ======================== 方块物品图标渲染 ========================

    private static void renderBlockIcon(GuiGraphics g, BlockEntry be, int centerX, int centerY, int iconSize) {
        renderItemIcon(g, be.createStack(), centerX, centerY, iconSize);
    }

    // ======================== 通用物品图标渲染 ========================

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

        RenderSystem.clear(256, false); // GL_DEPTH_BUFFER_BIT

        RenderSystem.depthMask(false);
        RenderSystem.disableDepthTest();

        pose.popPose();
    }
}
