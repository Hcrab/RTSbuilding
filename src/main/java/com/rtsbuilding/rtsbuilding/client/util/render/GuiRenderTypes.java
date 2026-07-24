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


public final class GuiRenderTypes {

    private static final int BUFFER_SIZE = 786432;

    private record CacheKey(ResourceLocation texture, boolean linear, boolean mipmap) {
        CacheKey {
            Objects.requireNonNull(texture);
        }
    }

    private static final Map<CacheKey, RenderType> CACHE = Util.make(new HashMap<>(), map -> {
        
    });

    
    public static RenderType guiTextured(ResourceLocation texture, boolean linear, boolean mipmap) {
        return CACHE.computeIfAbsent(new CacheKey(texture, linear, mipmap), GuiRenderTypes::create);
    }

    
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
