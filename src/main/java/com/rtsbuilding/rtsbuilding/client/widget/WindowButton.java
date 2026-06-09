package com.rtsbuilding.rtsbuilding.client.widget;

import com.mojang.blaze3d.systems.RenderSystem;
import com.rtsbuilding.rtsbuilding.client.RtsClientUiUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/**
 * 自定义窗口按钮
 * 支持贴图绘制和矢量缩放
 */
public class WindowButton extends AbstractButton {

    public interface OnPress {
        void onPress(WindowButton button);
    }

    private final OnPress onPress;
    private final ResourceLocation textureLocation;
    private final int textureU;
    private final int textureV;
    private final int textureWidth;
    private final int textureHeight;
    private final int hoverTextureV;  // 悬停状态的贴图V坐标
    private final int hoverTextureHeight;  // 悬停状态的贴图高度
    private final int fullTextureWidth;   // 完整贴图的总宽度
    private final int fullTextureHeight;  // 完整贴图的总高度

    private static final int TEXT_COLOR = 0xFFD8E3EE;
    private static final int TEXT_COLOR_DISABLED = 0xFF556677;
    private static final int BUTTON_BACKGROUND = 0xDD1A232E;
    private static final int BUTTON_HOVER = 0xDD2A3442;
    private static final int BORDER_LIGHT = 0xFF647B92;
    private static final int BORDER_DARK = 0xFF0D1117;

    /**
     * When set, all WindowButton instances suppress hover/focus effects.
     * Used by RtsWindowPanel when rendering a window that is
     * covered by a higher overlapping window.
     */
    private static boolean globalSkipHover;

    /**
     * 创建纯色按钮
     */
    public WindowButton(int x, int y, int width, int height, Component message, OnPress onPress) {
        this(x, y, width, height, message, null, 0, 0, 0, 0, onPress);
    }

    /**
     * 创建带贴图的按钮（支持悬停状态切换）
     *
     * @param x X 坐标
     * @param y Y 坐标
     * @param width 按钮宽度
     * @param height 按钮高度
     * @param message 按钮文本
     * @param textureLocation 贴图资源位置（null 表示使用纯色）
     * @param textureU 贴图 U 坐标
     * @param textureV 贴图 V 坐标（正常状态）
     * @param textureWidth 贴图宽度
     * @param textureHeight 贴图高度（正常状态）
     * @param hoverTextureV 悬停状态的贴图 V 坐标
     * @param hoverTextureHeight 悬停状态的贴图高度
     * @param fullTextureWidth 完整贴图的总宽度
     * @param fullTextureHeight 完整贴图的总高度
     * @param onPress 点击回调
     */
    public WindowButton(int x, int y, int width, int height, Component message,
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
     * 创建带贴图的按钮（兼容旧版，悬停使用相同贴图）
     */
    public WindowButton(int x, int y, int width, int height, Component message,
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

        if (textureLocation != null && textureWidth > 0 && textureHeight > 0) {
            // 使用贴图绘制（矢量缩放）
            renderWithTexture(guiGraphics);
        } else {
            // 使用纯色绘制
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
            guiGraphics.drawString(minecraft.font, label, textX, textY, textColor, false);
        }
    }

    /**
     * 使用贴图绘制按钮（支持矢量缩放和悬停效果）
     */
    private void renderWithTexture(GuiGraphics guiGraphics) {
        // 确保贴图已加载
        var textureManager = Minecraft.getInstance().getTextureManager();
        var texture = textureManager.getTexture(textureLocation);

        if (texture == null) {
            // 尝试触发贴图自动加载
            try {
                // 使用 setShaderTexture 触发贴图加载
                RenderSystem.setShaderTexture(0, textureLocation);

                // 再次尝试获取贴图
                texture = textureManager.getTexture(textureLocation);

                if (texture == null) {
                    // 如果仍然无法加载，绘制红色方块提示
                    guiGraphics.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, 0xFFFF0000);
                    return;
                }
            } catch (Exception e) {
                e.printStackTrace();
                // 如果仍然无法加载，绘制红色方块提示
                guiGraphics.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, 0xFFFF0000);
                return;
            }
        }

        // 根据悬停状态选择不同的贴图区域（被覆盖窗口强制使用非悬停贴图）
        boolean effectiveHovered = isHovered && !globalSkipHover;
        int currentV = effectiveHovered ? hoverTextureV : textureV;
        int currentHeight = effectiveHovered ? hoverTextureHeight : textureHeight;

        // 启用混合模式以支持透明度
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(
            org.lwjgl.opengl.GL11.GL_SRC_ALPHA,
            org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA,
            org.lwjgl.opengl.GL11.GL_ONE,
            org.lwjgl.opengl.GL11.GL_ZERO
        );

        // 绑定贴图（在设置参数之前绑定）
        RenderSystem.setShaderTexture(0, textureLocation);

        // 设置高质量的纹理过滤参数
        // 缩小过滤：三线性过滤（mipmap + 线性插值）
        RenderSystem.texParameter(
            org.lwjgl.opengl.GL11.GL_TEXTURE_2D,
            org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER,
            org.lwjgl.opengl.GL11.GL_LINEAR_MIPMAP_LINEAR
        );
        // 放大过滤：线性插值
        RenderSystem.texParameter(
            org.lwjgl.opengl.GL11.GL_TEXTURE_2D,
            org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER,
            org.lwjgl.opengl.GL11.GL_LINEAR
        );
        // 尝试设置各向异性过滤以提高斜向缩放质量
        // 注意：各向异性过滤是 OpenGL 扩展，需要检查支持情况
        try {
            // 使用 ARB_texture_filter_anisotropic 扩展常量
            int GL_TEXTURE_MAX_ANISOTROPY_EXT = 0x84FE;
            int GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT = 0x84FF;

            int maxAniso = org.lwjgl.opengl.GL11.glGetInteger(GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT);
            if (maxAniso > 0) {
                float anisoLevel = Math.min(16.0f, maxAniso);
                org.lwjgl.opengl.GL11.glTexParameterf(
                    org.lwjgl.opengl.GL11.GL_TEXTURE_2D,
                    GL_TEXTURE_MAX_ANISOTROPY_EXT,
                    anisoLevel
                );
            }
        } catch (Exception e) {
            // 忽略不支持的各向异性过滤
        }

        // 使用 PoseStack 变换进行缩放（避免裁剪问题）
        guiGraphics.pose().pushPose();

        // 计算缩放比例（使用按钮实际尺寸和要渲染的纹理尺寸）
        float scaleX = (float) this.width / textureWidth;
        float scaleY = (float) this.height / textureHeight;

        // 应用缩放变换
        guiGraphics.pose().translate(this.getX(), this.getY(), 0);
        guiGraphics.pose().scale(scaleX, scaleY, 1.0f);

        // 绘制原始尺寸的纹理（blit 会自动使用当前绑定的纹理）
        guiGraphics.blit(
            textureLocation,
            0,  // 相对于变换后的位置
            0,  // 相对于变换后的位置
            textureU,
            currentV,      // 使用对应的V坐标
            textureWidth,  // 要渲染的宽度
            currentHeight, // 要渲染的高度
            fullTextureWidth,   // 完整贴图的总宽度
            fullTextureHeight   // 完整贴图的总高度
        );

        // 恢复变换状态
        guiGraphics.pose().popPose();

        // 恢复默认设置
        RenderSystem.disableBlend();
        RenderSystem.texParameter(
            org.lwjgl.opengl.GL11.GL_TEXTURE_2D,
            org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER,
            org.lwjgl.opengl.GL11.GL_NEAREST
        );
        RenderSystem.texParameter(
            org.lwjgl.opengl.GL11.GL_TEXTURE_2D,
            org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER,
            org.lwjgl.opengl.GL11.GL_NEAREST
        );
    }

    /**
     * 使用纯色绘制按钮（RTS 深色风格）
     */
    private void renderWithSolidColor(GuiGraphics guiGraphics) {
        // 确定背景颜色（被覆盖窗口强制使用非悬停颜色）
        int backgroundColor = (!globalSkipHover && this.isHoveredOrFocused()) ? BUTTON_HOVER : BUTTON_BACKGROUND;
        RtsClientUiUtil.drawPanelFrame(guiGraphics,
                this.getX(), this.getY(), this.width, this.height,
                backgroundColor, BORDER_LIGHT, BORDER_DARK);
    }

    @Override
    protected void updateWidgetNarration(@NotNull NarrationElementOutput narrationElementOutput) {
        this.defaultButtonNarrationText(narrationElementOutput);
    }

    /**
     * Sets whether all WindowButton instances should globally skip
     * hover/focus visual effects during the next render call.
     */
    public static void setGlobalSkipHover(boolean skip) {
        globalSkipHover = skip;
    }
}
