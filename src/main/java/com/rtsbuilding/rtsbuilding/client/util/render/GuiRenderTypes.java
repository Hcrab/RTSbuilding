package com.rtsbuilding.rtsbuilding.client.util.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.rtsbuilding.rtsbuilding.client.util.render.model.TextureInfo;
import net.minecraft.Util;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * GUI 纹理渲染类型工厂——缓存按纹理+过滤模式区分的 {@link RenderType}，支持 VertexConsumer 批量提交。
 *
 * <p>替换逐 tile 调用 {@code g.blit()} 的旧方案。所有同纹理的九宫格瓷砖/精灵图
 * 共享同一个 VertexConsumer，显著减少 OpenGL draw call 次数。</p>
 */
public final class GuiRenderTypes {

    private static final int BUFFER_SIZE = 786432;

    private record CacheKey(ResourceLocation texture, boolean linear, boolean mipmap) {
        CacheKey {
            Objects.requireNonNull(texture);
        }
    }

    private static final Map<CacheKey, RenderType> CACHE = Util.make(new HashMap<>(), map -> {
        // SoftReference value 由 Minecraft 的 RenderType 内部管理
    });

    /**
     * 获取（或创建并缓存）一个 GUI 纹理渲染类型。
     *
     * @param texture 纹理路径
     * @param linear  true=GL_LINEAR 过滤，false=GL_NEAREST 过滤
     * @param mipmap  true=启用 mipmap
     */
    public static RenderType guiTextured(ResourceLocation texture, boolean linear, boolean mipmap) {
        return CACHE.computeIfAbsent(new CacheKey(texture, linear, mipmap), GuiRenderTypes::create);
    }

    /**
     * 便捷方法——根据 {@link TextureInfo.FilterMode} 自动选择过滤参数。
     */
    public static RenderType fromTextureInfo(ResourceLocation texture, TextureInfo.FilterMode filterMode) {
        return switch (filterMode) {
            case PIXEL -> guiTextured(texture, false, false);
            case NORMAL -> guiTextured(texture, true, false);
            case HQ -> guiTextured(texture, true, true);
        };
    }

    private static RenderType create(CacheKey key) {
        return RenderType.create(
                "rtsbuilding_gui_textured",
                DefaultVertexFormat.POSITION_TEX_COLOR,
                VertexFormat.Mode.QUADS,
                BUFFER_SIZE,
                false,
                false,
                RenderType.CompositeState.builder()
                        .setShaderState(new RenderStateShard.ShaderStateShard(GameRenderer::getPositionTexColorShader))
                        .setTextureState(new RenderStateShard.TextureStateShard(key.texture, key.linear, key.mipmap))
                        .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                        .createCompositeState(false)
        );
    }

    private GuiRenderTypes() {}
}
