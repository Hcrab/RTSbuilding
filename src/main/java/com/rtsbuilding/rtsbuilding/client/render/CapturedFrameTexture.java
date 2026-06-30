package com.rtsbuilding.rtsbuilding.client.render;

import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jetbrains.annotations.NotNull;

/**
 * 捕获帧纹理——包装 OpenGL 纹理 ID，使其可通过 Minecraft 的 {@link TextureManager}
 * 等方法中直接使用。
 *
 * <p>每帧由 {@link ViewCaptureService} 更新 {@link #setGlTextureId(int)}。</p>
 */
public class CapturedFrameTexture extends AbstractTexture {

    public CapturedFrameTexture() {
        // 开启双线性过滤，使 GUI 渲染放大世界画面时平滑而非像素化
        // （AbstractTexture.bind() 中会根据此标志设置 GL_TEXTURE_MIN/MAG_FILTER）
        this.blur = true;
    }

    @Override
    public void load(@NotNull ResourceManager resourceManager) {
    }

    /** 设置本帧的 OpenGL 纹理 ID。由 {@link ViewCaptureService} 每帧调用。 */
    public void setGlTextureId(int glId) {
        this.id = glId;
    }
}
