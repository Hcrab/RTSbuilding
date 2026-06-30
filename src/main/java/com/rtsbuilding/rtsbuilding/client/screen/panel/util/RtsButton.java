package com.rtsbuilding.rtsbuilding.client.screen.panel.util;

import com.mojang.blaze3d.systems.RenderSystem;
import com.rtsbuilding.rtsbuilding.client.util.AnimationFactory;
import com.rtsbuilding.rtsbuilding.client.util.RtsClientUiUtil;
import com.rtsbuilding.rtsbuilding.client.util.SmoothAnimator;
import com.rtsbuilding.rtsbuilding.client.util.ThemeManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/**
 * 自定义窗口按钮。
 * 支持纹理渲染和矢量缩放。
 */
public class RtsButton extends AbstractButton {

    public interface OnPress {
        void onPress(RtsButton button);
    }

    private final OnPress onPress;
    private final ResourceLocation textureLocation;
    private final int textureU;
    private final int textureV;
    private final int textureWidth;
    private final int textureHeight;
    private final int hoverTextureV;  // 悬停状态纹理V坐标
    private final int hoverTextureHeight;  // 悬停状态纹理高度
    private final int fullTextureWidth;   // 完整纹理总宽度
    private final int fullTextureHeight;  // 完整纹理总高度

    private static final int TEXT_COLOR = 0xFFD8E3EE;
    private static final int TEXT_COLOR_DISABLED = 0xFF556677;
    private static final int BUTTON_BACKGROUND = 0xDD1A232E;
    private static final int BUTTON_HOVER = 0xDD2A3442;
    private static final int BORDER_LIGHT = 0xFF647B92;
    private static final int BORDER_DARK = 0xFF0D1117;

    /** 悬浮态平滑动画器 */
    private final SmoothAnimator hoverAnim = AnimationFactory.createHoverAnim();
    /** 上一帧悬浮状态，用于检测变化 */
    private boolean lastHovered;

    /**
     * 设置后，所有RtsButton实例将抑制悬停/焦点效果。
     * 由RtsPanel在渲染被更高重叠窗口覆盖的窗口时使用。
     */
    private static boolean globalSkipHover;

    /**
     * 创建纯色按钮。
     */
    public RtsButton(int x, int y, int width, int height, Component message, OnPress onPress) {
        this(x, y, width, height, message, null, 0, 0, 0, 0, onPress);
    }

    /**
     * 创建带纹理的按钮，支持悬停状态切换。
     *
     * @param x X坐标
     * @param y Y坐标
     * @param width 按钮宽度
     * @param height 按钮高度
     * @param message 按钮文本
     * @param textureLocation 纹理资源位置（null表示纯色）
     * @param textureU 纹理U坐标
     * @param textureV 纹理V坐标（普通状态）
     * @param textureWidth 纹理宽度
     * @param textureHeight 纹理高度（普通状态）
     * @param hoverTextureV 悬停状态纹理V坐标
     * @param hoverTextureHeight 悬停状态纹理高度
     * @param fullTextureWidth 完整纹理总宽度
     * @param fullTextureHeight 完整纹理总高度
     * @param onPress 点击回调
     */
    public RtsButton(int x, int y, int width, int height, Component message,
                     ResourceLocation textureLocation, int textureU, int textureV,
                     int textureWidth, int textureHeight, int hoverTextureV, int hoverTextureHeight,
                     int fullTextureWidth, int fullTextureHeight, OnPress onPress) {
        super(x, y, width, height, message);
        this.onPress = onPress;
        this.textureLocation = textureLocation;
        this.textureU = textureU;
        this.textureV = textureV;
        this.textureWidth = textureWidth;
        this.textureHeight = textureHeight;
        this.hoverTextureV = hoverTextureV;
        this.hoverTextureHeight = hoverTextureHeight;
        this.fullTextureWidth = fullTextureWidth;
        this.fullTextureHeight = fullTextureHeight;
    }

    /**
     * 创建带纹理的按钮（旧版兼容，悬停时使用相同纹理）。
     */
    public RtsButton(int x, int y, int width, int height, Component message,
                     ResourceLocation textureLocation, int textureU, int textureV,
                     int textureWidth, int textureHeight, OnPress onPress) {
        this(x, y, width, height, message, textureLocation, textureU, textureV,
             textureWidth, textureHeight, textureV, textureHeight,
             textureWidth, textureHeight, onPress);
    }

    @Override
    public void onPress() {
        this.onPress.onPress(this);
    }

    @Override
    protected void renderWidget(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();

        // 检测悬浮状态变化并启动动画
        boolean effectiveHovered = isHovered && !globalSkipHover;
        if (effectiveHovered != this.lastHovered) {
            this.lastHovered = effectiveHovered;
            this.hoverAnim.start(effectiveHovered ? 1.0f : 0.0f);
        }
        this.hoverAnim.tick();

        if (textureLocation != null && textureWidth > 0 && textureHeight > 0) {
            // 使用纹理渲染（矢量缩放）
            renderWithTexture(guiGraphics);
        } else {
            // 使用纯色渲染
            renderWithSolidColor(guiGraphics);
        }

        // 计算文本位置（居中）
        int textColor = this.active ? TEXT_COLOR : TEXT_COLOR_DISABLED;
        String label = RtsClientUiUtil.trimToWidth(minecraft.font, this.getMessage().getString(),
                Math.max(4, this.width - 8));
        int textWidth = minecraft.font.width(label);
        int textX = this.getX() + (this.width - textWidth) / 2;
        int textY = this.getY() + (this.height - 8) / 2;

        // 绘制文本
        if (!label.isEmpty()) {
            RtsClientUiUtil.drawUiText(guiGraphics, label, textX, textY, textColor);
        }
    }

    /**
     * 使用纹理渲染按钮（支持矢量缩放和悬停效果）。
     */
    private void renderWithTexture(GuiGraphics guiGraphics) {
        // 双主题偏移：亮色主题使用右半区
        int themeUOffset = ThemeManager.getInstance().themeU(textureWidth);

        // 将普通态（textureV）和悬浮态（hoverTextureV）贴图交叉淡入淡出
        float t = this.hoverAnim.getValue();

        // 启用混合模式以支持透明
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(
                org.lwjgl.opengl.GL11.GL_SRC_ALPHA,
                org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA,
                org.lwjgl.opengl.GL11.GL_ONE,
                org.lwjgl.opengl.GL11.GL_ZERO
        );

        if (t > 0.001f && t < 0.999f) {
            // 过渡动画：普通态全不透明 + 悬浮态淡入叠加
            RtsClientUiUtil.drawScaledImage(guiGraphics, textureLocation,
                    this.getX(), this.getY(), this.width, this.height,
                    textureU + themeUOffset, textureV,
                    textureWidth, textureHeight,
                    fullTextureWidth, fullTextureHeight);
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, t);
            RtsClientUiUtil.drawScaledImage(guiGraphics, textureLocation,
                    this.getX(), this.getY(), this.width, this.height,
                    textureU + themeUOffset, hoverTextureV,
                    textureWidth, hoverTextureHeight,
                    fullTextureWidth, fullTextureHeight);
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        } else if (t >= 0.999f) {
            RtsClientUiUtil.drawScaledImage(guiGraphics, textureLocation,
                    this.getX(), this.getY(), this.width, this.height,
                    textureU + themeUOffset, hoverTextureV,
                    textureWidth, hoverTextureHeight,
                    fullTextureWidth, fullTextureHeight);
        } else {
            RtsClientUiUtil.drawScaledImage(guiGraphics, textureLocation,
                    this.getX(), this.getY(), this.width, this.height,
                    textureU + themeUOffset, textureV,
                    textureWidth, textureHeight,
                    fullTextureWidth, fullTextureHeight);
        }

        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.disableBlend();
    }

    /**
     * 使用纯色渲染按钮（RTS深色风格）。
     */
    private void renderWithSolidColor(GuiGraphics guiGraphics) {
        // 使用动画值插值背景颜色，实现悬浮过渡效果
        float t = this.hoverAnim.getValue();
        int backgroundColor = SmoothAnimator.lerpColor(BUTTON_BACKGROUND, BUTTON_HOVER, t);
        RtsClientUiUtil.drawPanelFrame(guiGraphics,
                this.getX(), this.getY(), this.width, this.height,
                backgroundColor, BORDER_LIGHT, BORDER_DARK);
    }

    // ======================== 工具方法 ========================

    @Override
    protected void updateWidgetNarration(@NotNull NarrationElementOutput narrationElementOutput) {
        this.defaultButtonNarrationText(narrationElementOutput);
    }

    /**
     * 设置是否所有RtsButton实例应在下一次渲染调用时全局跳过
     * 悬停/焦点视觉效果。
     */
    public static void setGlobalSkipHover(boolean skip) {
        globalSkipHover = skip;
    }

}
