package com.rtsbuilding.rtsbuilding.client.screen.panel.component;

import com.rtsbuilding.rtsbuilding.client.util.state.HoverStateManager;
import com.rtsbuilding.rtsbuilding.client.util.render.model.SpriteRegion;
import com.rtsbuilding.rtsbuilding.client.util.render.model.TextureInfo;
import com.rtsbuilding.rtsbuilding.client.util.animate.ColorAnimation;
import com.rtsbuilding.rtsbuilding.client.util.render.CrossFadeRenderer;
import com.rtsbuilding.rtsbuilding.client.util.render.SpriteRenderer;
import com.rtsbuilding.rtsbuilding.client.util.render.TextRenderer;
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
    /** 纹理元数据缓存（由构造函数根据 raw 参数预计算，避免每帧 new） */
    private final TextureInfo texInfo;
    /** 普通态精灵区域（构造时预计算） */
    private final SpriteRegion normalRegion;
    /** 悬浮态精灵区域（构造时预计算） */
    private final SpriteRegion hoveredRegion;

    private static final int TEXT_COLOR = 0xFFD8E3EE;
    private static final int TEXT_COLOR_HOVER = 0xFFE8F0FA;
    private static final int TEXT_COLOR_DISABLED = 0xFF556677;
    private static final int BUTTON_BACKGROUND = 0xDD1A232E;
    private static final int BUTTON_HOVER = 0xDD2A3442;
    private static final int BORDER_LIGHT = 0xFF647B92;
    private static final int BORDER_DARK = 0xFF0D1117;

    /** 悬浮状态管理器 */
    private final HoverStateManager hoverState = new HoverStateManager();


    /**
     * 创建纯色按钮。
     */
    public RtsButton(int x, int y, int width, int height, Component message, OnPress onPress) {
        this(x, y, width, height, message, null, 0, 0, 0, 0, onPress);
    }

    /**
     * 创建带纹理的按钮，支持悬停状态切换。
     * <p>纹理参数在构造时预计算为 {@link TextureInfo} 和 {@link SpriteRegion} 并缓存，
     * 避免每帧重新创建对象。</p>
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
        // 预计算 TextureInfo 和 SpriteRegion（仅当有纹理时）
        if (textureLocation != null && textureWidth > 0 && textureHeight > 0) {
            this.texInfo = new TextureInfo(
                    textureLocation, fullTextureWidth, fullTextureHeight,
                    TextureInfo.ThemeLayout.HORIZONTAL_PAIR,
                    TextureInfo.FilterMode.PIXEL);
            this.normalRegion = new SpriteRegion(texInfo, textureU, textureV, textureWidth, textureHeight);
            this.hoveredRegion = new SpriteRegion(texInfo, textureU, hoverTextureV, textureWidth, hoverTextureHeight);
        } else {
            this.texInfo = null;
            this.normalRegion = null;
            this.hoveredRegion = null;
        }
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

        // 更新悬浮状态并推进动画（全局抑制由 HoverStateManager 内部处理）
        this.hoverState.update(isHovered);

        if (normalRegion != null && hoveredRegion != null) {
            // 使用预缓存的 SpriteRegion 渲染（新架构）
            renderWithSprite(guiGraphics);
        } else {
            // 使用纯色渲染
            renderWithSolidColor(guiGraphics);
        }

        // 计算文本颜色（悬浮时平滑过渡到高亮色）
        float hoverT = this.hoverState.getValue();
        int textColor = this.active
                ? ColorAnimation.lerpRGB(TEXT_COLOR, TEXT_COLOR_HOVER, hoverT)
                : TEXT_COLOR_DISABLED;
        String label = TextRenderer.trimToWidth(minecraft.font, this.getMessage().getString(),
                Math.max(4, this.width - 8));
        int textWidth = minecraft.font.width(label);
        int textX = this.getX() + (this.width - textWidth) / 2;
        int textY = this.getY() + (this.height - 8) / 2;

        // 绘制文本
        if (!label.isEmpty()) {
            TextRenderer.draw(guiGraphics, label, textX, textY, textColor);
        }
    }

    /** 使用预缓存的 SpriteRegion 渲染按钮（支持矢量缩放和悬停效果）。 */
    private void renderWithSprite(GuiGraphics guiGraphics) {
        float t = this.hoverState.getValue();
        CrossFadeRenderer.render(t,
                () -> SpriteRenderer.drawSprite(guiGraphics, normalRegion.withTheme(), this.getX(), this.getY(), this.width, this.height),
                () -> SpriteRenderer.drawSprite(guiGraphics, hoveredRegion.withTheme(), this.getX(), this.getY(), this.width, this.height));
    }

    /**
     * 使用纯色渲染按钮（RTS深色风格）。
     */
    private void renderWithSolidColor(GuiGraphics guiGraphics) {
        // 使用动画值插值背景颜色，实现悬浮过渡效果
        float t = this.hoverState.getValue();
        int backgroundColor = ColorAnimation.lerpRGB(BUTTON_BACKGROUND, BUTTON_HOVER, t);
        guiGraphics.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, backgroundColor);
        guiGraphics.hLine(this.getX(), this.getX() + this.width, this.getY(), BORDER_LIGHT);
        guiGraphics.hLine(this.getX(), this.getX() + this.width, this.getY() + this.height, BORDER_DARK);
        guiGraphics.vLine(this.getX(), this.getY(), this.getY() + this.height, BORDER_LIGHT);
        guiGraphics.vLine(this.getX() + this.width, this.getY(), this.getY() + this.height, BORDER_DARK);
    }

    // ======================== 工具方法 ========================

    @Override
    protected void updateWidgetNarration(@NotNull NarrationElementOutput narrationElementOutput) {
        this.defaultButtonNarrationText(narrationElementOutput);
    }

}
