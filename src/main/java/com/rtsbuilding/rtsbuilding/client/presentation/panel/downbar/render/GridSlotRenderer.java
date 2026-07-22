package com.rtsbuilding.rtsbuilding.client.presentation.panel.downbar.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.rtsbuilding.rtsbuilding.client.util.render.SpriteRenderer;
import com.rtsbuilding.rtsbuilding.client.util.render.model.SpriteRegion;
import com.rtsbuilding.rtsbuilding.client.util.render.model.TextureInfo;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * 网格单格渲染器——将一格子的渲染拆分为独立的职责方法，
 * 与批量背景平铺 ({@link SpriteRenderer#drawTiledGrid}) 配合使用。
 *
 * <p>方法设计为三阶段管线：</p>
 * <ol>
 *   <li>{@link #drawIcon(GuiGraphics, ItemStack, int, int)} —— 物品图标</li>
 *   <li>{@link #drawAmountText(GuiGraphics, Font, long, int, int, boolean)} —— 数量文本</li>
 *   <li>{@link #drawOverlay(GuiGraphics, int, int, boolean, boolean, int)} —— 悬浮/选中覆盖层</li>
 * </ol>
 *
 * <p>背景绘制由 {@link SpriteRenderer#drawTiledGrid} 批量完成，不在此处逐格绘制。</p>
 */
public final class GridSlotRenderer {

    // ======================== 布局常量 ========================

    /** 每个格子的尺寸（宽高一致） */
    public static final int SLOT_SIZE = 18;
    /** 物品图标在格子内的偏移（居中，16×16 图标在 18×18 格子中上下各 1px） */
    public static final int ICON_OFFSET = 1;

    // ======================== 数量文本常量 ========================

    /** 数量文本缩放系数（参考 AE2 StackSizeRenderer 默认值 0.666f） */
    public static final float AMOUNT_SCALE = 0.666f;
    /** 缩放倒数 */
    public static final float INV_AMOUNT_SCALE = 1.0f / AMOUNT_SCALE;
    /** 物品数量文本颜色 */
    public static final int AMOUNT_COLOR = 0xFF_FFFFFF;
    /** 流体数量文本颜色（浅蓝色调，与物品区分） */
    public static final int FLUID_AMOUNT_COLOR = 0xFF_80C8FF;

    // ======================== 格子贴图（slots.png）=======================

    /** slots.png：32×48，水平双主题，垂直 0-16=正常，16-32=悬浮，32-48=选中 */
    private static final ResourceLocation SLOTS_TEXTURE = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/down/slots.png");
    private static final int SLOTS_TEX_W = 32;
    private static final int SLOTS_TEX_H = 48;
    private static final int SLOTS_STATE_H = 16;
    /** 选中态垂直偏移（y=32-48） */
    private static final int SLOTS_SELECTED_V_OFFSET = 32;
    private static final TextureInfo SLOTS_TEX_INFO = new TextureInfo(
            SLOTS_TEXTURE, SLOTS_TEX_W, SLOTS_TEX_H,
            TextureInfo.ThemeLayout.HORIZONTAL_PAIR,
            TextureInfo.FilterMode.PIXEL);
    /** 正常态精灵（v=0~16，半区宽=16） */
    public static final SpriteRegion SLOT_NORMAL = new SpriteRegion(
            SLOTS_TEX_INFO, 0, 0, SLOTS_TEX_W / 2, SLOTS_STATE_H);
    /** 悬浮态精灵（v=16~32） */
    public static final SpriteRegion SLOT_HOVER = new SpriteRegion(
            SLOTS_TEX_INFO, 0, SLOTS_STATE_H, SLOTS_TEX_W / 2, SLOTS_STATE_H);
    /** 选中态精灵（v=32~48）——半透明覆盖层，盖在图标之上指示已选中 */
    public static final SpriteRegion SLOT_SELECTED = new SpriteRegion(
            SLOTS_TEX_INFO, 0, SLOTS_SELECTED_V_OFFSET, SLOTS_TEX_W / 2, SLOTS_STATE_H);

    private GridSlotRenderer() {}

    // ======================== 物品图标 ========================

    /**
     * 在格子内居中绘制物品图标（16×16 缩放至 SLOT_SIZE 内的居中位置）。
     *
     * @param g      GuiGraphics
     * @param stack  要绘制的物品
     * @param slotX  格子左上 X
     * @param slotY  格子左上 Y
     */
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
    }

    // ======================== 覆盖层（悬浮/选中）=======================

    /**
     * 绘制格子的悬浮或选中覆盖层。
     * <p>选中态优先于悬浮态。</p>
     *
     * @param g               GuiGraphics
     * @param slotX           格子左上 X
     * @param slotY           格子左上 Y
     * @param hovered         是否悬浮
     * @param selected        是否选中
     * @param slotThemeOffset 预计算的主题偏移（由 {@link SpriteRenderer#getThemeOffset} 获得）
     */
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

    // ======================== 数量文本 ========================

    /**
     * 在格子右下角绘制缩放后的数量文本（参考 AE2 StackSizeRenderer 风格）。
     *
     * @param g       GuiGraphics
     * @param font    使用的字体（已由调用方解析，含物品专属字体回退）
     * @param count   数量
     * @param slotX   格子左上 X
     * @param slotY   格子左上 Y
     * @param isFluid 是否为流体（影响文本颜色）
     */
    public static void drawAmountText(GuiGraphics g, Font font, long count,
                                      int slotX, int slotY, boolean isFluid) {
        if (count <= 1) return;

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

    // ======================== 数量格式化（AE2 风格）=======================

    /**
     * 将数量格式化为紧凑形式（参考 AE2 AmountFormat.SLOT 风格）。
     *
     * <ul>
     *   <li>&ge; 1,000,000,000 → "1.0B"（十亿）</li>
     *   <li>&ge; 1,000,000 → "1.0M"（百万）</li>
     *   <li>&ge; 1,000 → "1.0K"（千）</li>
     *   <li>其他 → 原样输出</li>
     * </ul>
     */
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
