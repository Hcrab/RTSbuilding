package com.rtsbuilding.rtsbuilding.client.screen.canvas;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.rtsbuilding.rtsbuilding.uikit.theme.RtsMainlineTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

/**
 * 生产 UI 在 Minecraft 1.19.2 上使用的稳定绘制上下文。
 *
 * <p>该类负责把共享 UI 所需的矩形、文字、纹理、物品、tooltip、裁剪和 pose 操作投影到
 * 1.19.2 的 {@link PoseStack}/{@link GuiComponent} API。面板、弹窗和布局代码只依赖本契约，
 * 不直接知道旧版渲染入口。它不拥有 Screen 生命周期，也不访问世界渲染缓冲。</p>
 *
 * <p>需要批处理的少量 3D GUI 绘制使用本上下文私有的 {@link BufferBuilder}。因此
 * {@link #flush()} 只结束私有 GUI 批次，不会结束 Minecraft 的共享世界 buffer，避免与
 * Sodium/Embeddium 类优化模组发生生命周期冲突。</p>
 */
public final class RtsGuiContext {
    private static final int PRIVATE_BUFFER_SIZE = 256;
    /** 旧版 GUI 投影的可用深度很窄，物品只需略高于所属面板。 */
    private static final float ITEM_MODEL_Z = 8.0F;
    private static final float ITEM_DECORATION_Z = 16.0F;

    private final PoseStack pose;
    private final Screen tooltipHost;
    private final MultiBufferSource.BufferSource privateBuffers;

    public RtsGuiContext(PoseStack pose) {
        this(pose, Minecraft.getInstance().screen);
    }

    public RtsGuiContext(PoseStack pose, Screen tooltipHost) {
        if (pose == null) {
            throw new IllegalArgumentException("pose must not be null");
        }
        this.pose = pose;
        this.tooltipHost = tooltipHost;
        this.privateBuffers = MultiBufferSource.immediate(new BufferBuilder(PRIVATE_BUFFER_SIZE));
    }

    public PoseStack pose() {
        return pose;
    }

    public MultiBufferSource.BufferSource bufferSource() {
        return privateBuffers;
    }

    public void fill(int x1, int y1, int x2, int y2, int color) {
        GuiComponent.fill(pose, x1, y1, x2, y2, color);
    }

    public void hLine(int x1, int x2, int y, int color) {
        if (x2 < x1) {
            int swap = x1;
            x1 = x2;
            x2 = swap;
        }
        fill(x1, y, x2 + 1, y + 1, color);
    }

    public void vLine(int x, int y1, int y2, int color) {
        if (y2 < y1) {
            int swap = y1;
            y1 = y2;
            y2 = swap;
        }
        fill(x, y1, x + 1, y2 + 1, color);
    }

    /** 绘制一像素矩形轮廓，语义与新版 GuiGraphics.renderOutline 一致。 */
    public void renderOutline(int x, int y, int width, int height, int color) {
        if (width <= 0 || height <= 0) return;
        hLine(x, x + width - 1, y, color);
        hLine(x, x + width - 1, y + height - 1, color);
        if (height > 2) {
            vLine(x, y + 1, y + height - 2, color);
            vLine(x + width - 1, y + 1, y + height - 2, color);
        }
    }

    public int drawString(Font font, String text, int x, int y, int color) {
        return drawString(font, text, x, y, color, true);
    }

    public int drawString(Font font, String text, int x, int y, int color, boolean shadow) {
        return shadow ? font.drawShadow(pose, text, x, y, color) : font.draw(pose, text, x, y, color);
    }

    public int drawString(Font font, Component text, int x, int y, int color) {
        return drawString(font, text, x, y, color, true);
    }

    public int drawString(Font font, Component text, int x, int y, int color, boolean shadow) {
        return shadow ? font.drawShadow(pose, text, x, y, color) : font.draw(pose, text, x, y, color);
    }

    public int drawString(Font font, FormattedCharSequence text, int x, int y, int color) {
        return drawString(font, text, x, y, color, true);
    }

    public int drawString(Font font, FormattedCharSequence text, int x, int y, int color,
                          boolean shadow) {
        return shadow ? font.drawShadow(pose, text, x, y, color) : font.draw(pose, text, x, y, color);
    }

    public void drawCenteredString(Font font, String text, int centerX, int y, int color) {
        drawString(font, text, centerX - font.width(text) / 2, y, color);
    }

    public void drawCenteredString(Font font, Component text, int centerX, int y, int color) {
        drawString(font, text, centerX - font.width(text) / 2, y, color);
    }

    public void drawCenteredString(Font font, FormattedCharSequence text, int centerX, int y,
                                   int color) {
        drawString(font, text, centerX - font.width(text) / 2, y, color);
    }

    public void blit(ResourceLocation texture, int x, int y, int u, int v, int width, int height) {
        RenderSystem.setShaderTexture(0, texture);
        GuiComponent.blit(pose, x, y, (float) u, (float) v, width, height, 256, 256);
    }

    public void blit(ResourceLocation texture, int x, int y, int u, int v,
                     int width, int height, int textureWidth, int textureHeight) {
        RenderSystem.setShaderTexture(0, texture);
        GuiComponent.blit(pose, x, y, (float) u, (float) v,
                width, height, textureWidth, textureHeight);
    }

    public void setColor(float red, float green, float blue, float alpha) {
        RenderSystem.setShaderColor(red, green, blue, alpha);
    }

    public void renderItem(ItemStack stack, int x, int y) {
        if (stack == null || stack.isEmpty()) return;
        Minecraft minecraft = Minecraft.getInstance();
        ItemRenderer renderer = minecraft.getItemRenderer();
        BakedModel model = renderer.getModel(stack, null, null, 0);

        minecraft.getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS)
                .setFilter(false, false);
        RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_BLOCKS);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        boolean flatLighting = !model.usesBlockLight();
        pose.pushPose();
        try {
            pose.translate(x + 8.0D, y + 8.0D, ITEM_MODEL_Z);
            pose.scale(16.0F, -16.0F, 16.0F);
            if (flatLighting) Lighting.setupForFlatItems();
            renderer.render(stack, ItemTransforms.TransformType.GUI, false, pose,
                    privateBuffers, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, model);
            privateBuffers.endBatch();
            RenderSystem.enableDepthTest();
        } finally {
            if (flatLighting) Lighting.setupFor3DItems();
            pose.popPose();
        }
    }

    public void renderItemDecorations(Font font, ItemStack stack, int x, int y) {
        renderItemDecorations(font, stack, x, y, null);
    }

    public void renderItemDecorations(Font font, ItemStack stack, int x, int y, String countText) {
        if (stack == null || stack.isEmpty()) return;
        pose.pushPose();
        try {
            pose.translate(0.0D, 0.0D, ITEM_DECORATION_Z);
            String count = countText != null ? countText
                    : stack.getCount() == 1 ? null : String.valueOf(stack.getCount());
            if (count != null) {
                font.draw(pose, count, x + 17.0F - font.width(count), y + 9.0F,
                        RtsMainlineTheme.ITEM_DECORATION_TEXT.toArgb());
            }
            if (stack.isBarVisible()) {
                int barWidth = stack.getBarWidth();
                int barColor = stack.getBarColor();
                int opaqueBlack = RtsMainlineTheme.ITEM_DURABILITY_BACKGROUND.toArgb();
                fill(x + 2, y + 13, x + 15, y + 15, opaqueBlack);
                fill(x + 2, y + 13, x + 2 + barWidth, y + 14,
                        opaqueBlack | barColor);
            }
            Minecraft minecraft = Minecraft.getInstance();
            float cooldown = minecraft.player == null ? 0.0F
                    : minecraft.player.getCooldowns()
                    .getCooldownPercent(stack.getItem(), minecraft.getFrameTime());
            if (cooldown > 0.0F) {
                int top = y + (int) Math.floor(16.0F * (1.0F - cooldown));
                int height = (int) Math.ceil(16.0F * cooldown);
                fill(x, top, x + 16, top + height,
                        RtsMainlineTheme.ITEM_COOLDOWN_OVERLAY.toArgb());
            }
        } finally {
            pose.popPose();
        }
    }

    public void renderTooltip(Font font, ItemStack stack, int x, int y) {
        Screen host = tooltipHost();
        if (host != null) {
            host.renderTooltip(pose, host.getTooltipFromItem(stack), stack.getTooltipImage(),
                    x, y, font, stack);
        }
    }

    public void renderTooltip(Font font, Component text, int x, int y) {
        Screen host = tooltipHost();
        if (host != null) {
            host.renderTooltip(pose, text, x, y);
        }
    }

    /**
     * 将 GUI 坐标转换为 1.19.2 RenderSystem 使用的 framebuffer 坐标。
     *
     * <p>转换只在此处发生一次，并翻转 Y 轴；调用方必须传入半开矩形的右下边界。</p>
     */
    public void enableScissor(int x1, int y1, int x2, int y2) {
        Window window = Minecraft.getInstance().getWindow();
        double scale = window.getGuiScale();
        int framebufferX = (int) Math.floor(x1 * scale);
        int framebufferY = (int) Math.floor(window.getHeight() - y2 * scale);
        int framebufferWidth = Math.max(0, (int) Math.ceil((x2 - x1) * scale));
        int framebufferHeight = Math.max(0, (int) Math.ceil((y2 - y1) * scale));
        RenderSystem.enableScissor(framebufferX, framebufferY, framebufferWidth, framebufferHeight);
    }

    public void disableScissor() {
        RenderSystem.disableScissor();
    }

    public void flush() {
        privateBuffers.endBatch();
    }

    private Screen tooltipHost() {
        return tooltipHost != null ? tooltipHost : Minecraft.getInstance().screen;
    }
}
