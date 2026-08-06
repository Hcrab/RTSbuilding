package com.rtsbuilding.rtsbuilding.client.theme;

import com.mojang.blaze3d.platform.NativeImage;
import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiIndexedTextureSpec;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiPaletteTextureBaker;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiTextureState;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiThemeDefinition;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiThemeRenderMode;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiThemeRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Palette 动态纹理的客户端生命周期 owner。
 *
 * <p>只在首次请求、主题切换或资源重载后执行 CPU 烘焙；常规绘制只查询缓存。清空缓存时通过
 * {@link TextureManager#release(ResourceLocation)} 释放 GPU/NativeImage 资源，不触碰共享渲染 buffer。</p>
 */
public final class UiThemeTextureCache {
    public static final UiThemeTextureCache INSTANCE = new UiThemeTextureCache();

    private final Map<Key, ResourceLocation> textures = new HashMap<Key, ResourceLocation>();
    private long generation;

    private UiThemeTextureCache() {
        UiThemeRuntime.manager().addListener((previous, current) -> clear());
    }

    public synchronized ResourceLocation resolve(ResourceLocation source, UiTextureState state,
                                                 UiIndexedTextureSpec spec) {
        UiThemeDefinition theme = UiThemeRuntime.manager().active();
        if (theme.renderMode() != UiThemeRenderMode.PALETTE) {
            throw new IllegalStateException("Legacy Direct must not request Palette textures");
        }
        Key key = new Key(theme.id(), source, state, spec.roles(), generation);
        ResourceLocation existing = textures.get(key);
        if (existing != null) return existing;
        try {
            ResourceLocation baked = bakeAndRegister(source, state, theme, spec);
            textures.put(key, baked);
            return baked;
        } catch (RuntimeException | IOException failure) {
            RtsbuildingMod.LOGGER.error(
                    "Palette 纹理烘焙失败，临时显示单源资源：theme={} source={} state={}",
                    theme.id(), source, state, failure);
            return source;
        }
    }

    public synchronized void clear() {
        TextureManager manager = Minecraft.getInstance().getTextureManager();
        for (ResourceLocation texture : textures.values()) {
            manager.release(texture);
        }
        textures.clear();
        generation++;
    }

    int cachedTextureCount() {
        return textures.size();
    }

    private static ResourceLocation bakeAndRegister(ResourceLocation source, UiTextureState state,
                                                    UiThemeDefinition theme,
                                                    UiIndexedTextureSpec spec) throws IOException {
        Minecraft minecraft = Minecraft.getInstance();
        Resource resource = minecraft.getResourceManager().getResource(source)
                .orElseThrow(() -> new IOException("missing Palette source " + source));
        try (InputStream stream = resource.open(); NativeImage sourceImage = NativeImage.read(stream)) {
            int width = sourceImage.getWidth();
            int height = sourceImage.getHeight();
            int[] sourceArgb = new int[width * height];
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    sourceArgb[y * width + x] = abgrToArgb(sourceImage.getPixelRGBA(x, y));
                }
            }
            int[] outputArgb = UiPaletteTextureBaker.bake(sourceArgb, spec, theme, state);
            NativeImage output = new NativeImage(width, height, false);
            boolean ownershipTransferred = false;
            try {
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        output.setPixelRGBA(x, y, abgrToArgb(outputArgb[y * width + x]));
                    }
                }
                DynamicTexture dynamic = new DynamicTexture(output);
                ResourceLocation registered = minecraft.getTextureManager().register(
                        "rts_theme_" + sanitize(theme.id()) + "_"
                                + Integer.toUnsignedString(source.toString().hashCode(), 36) + "_"
                                + Integer.toUnsignedString(spec.roles().hashCode(), 36) + "_"
                                + state.name().toLowerCase(), dynamic);
                ownershipTransferred = true;
                return registered;
            } finally {
                if (!ownershipTransferred) output.close();
            }
        }
    }

    /** 交换红蓝通道；NativeImage 的 packed RGBA 与主题使用的 ARGB 互为该变换。 */
    private static int abgrToArgb(int color) {
        return color & 0xFF00FF00
                | color >>> 16 & 0x000000FF
                | color << 16 & 0x00FF0000;
    }

    private static String sanitize(String id) {
        return id.replace(':', '_').replace('/', '_').replace('.', '_');
    }

    private static final class Key {
        private final String themeId;
        private final ResourceLocation source;
        private final UiTextureState state;
        private final Map<Integer, UiIndexedTextureSpec.Role> roles;
        private final long generation;

        private Key(String themeId, ResourceLocation source, UiTextureState state,
                    Map<Integer, UiIndexedTextureSpec.Role> roles, long generation) {
            this.themeId = themeId;
            this.source = source;
            this.state = state;
            this.roles = roles;
            this.generation = generation;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Key)) return false;
            Key key = (Key) other;
            return generation == key.generation && themeId.equals(key.themeId)
                    && source.equals(key.source) && state == key.state && roles.equals(key.roles);
        }

        @Override
        public int hashCode() {
            return Objects.hash(themeId, source, state, roles, generation);
        }
    }
}
