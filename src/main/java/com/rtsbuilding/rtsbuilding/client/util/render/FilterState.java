package com.rtsbuilding.rtsbuilding.client.util.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.rtsbuilding.rtsbuilding.client.util.render.model.TextureInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;


public final class FilterState {

    private static final FilterState INSTANCE = new FilterState();

    
    private ResourceLocation lastTexture;
    
    private TextureInfo.FilterMode lastMode;

    private FilterState() {}

    
    public static FilterState getInstance() {
        return INSTANCE;
    }

    
    public void apply(TextureInfo info) {
        var loc = info.location();
        var mode = info.filterMode();
        if (loc.equals(lastTexture) && mode == lastMode) {
            return;
        }

        this.lastTexture = loc;
        this.lastMode = mode;

        var tex = Minecraft.getInstance().getTextureManager().getTexture(loc);
        RenderSystem.setShaderTexture(0, loc);

        switch (mode) {
            case PIXEL -> applyPixelFilter(tex);
            case NORMAL -> applyNormalFilter(tex);
            case HQ -> applyHqFilter(tex);
        }
    }

    
    public void invalidate() {
        this.lastTexture = null;
        this.lastMode = null;
    }

    

    private static void applyPixelFilter(net.minecraft.client.renderer.texture.AbstractTexture tex) {
        RenderSystem.texParameter(GL11.GL_TEXTURE_2D,
                GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        RenderSystem.texParameter(GL11.GL_TEXTURE_2D,
                GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        if (tex != null) tex.setFilter(false, false);
        
        RenderSystem.texParameter(GL11.GL_TEXTURE_2D,
                GL12.GL_TEXTURE_MAX_LEVEL, 0);
    }

    private static void applyNormalFilter(net.minecraft.client.renderer.texture.AbstractTexture tex) {
        RenderSystem.texParameter(GL11.GL_TEXTURE_2D,
                GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        RenderSystem.texParameter(GL11.GL_TEXTURE_2D,
                GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        if (tex != null) tex.setFilter(true, false);
        RenderSystem.texParameter(GL11.GL_TEXTURE_2D,
                GL12.GL_TEXTURE_MAX_LEVEL, 0);
    }

    private static void applyHqFilter(net.minecraft.client.renderer.texture.AbstractTexture tex) {
        if (tex != null) {
            tex.setFilter(true, true);
            
        }
        RenderSystem.texParameter(GL11.GL_TEXTURE_2D,
                GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
        RenderSystem.texParameter(GL11.GL_TEXTURE_2D,
                GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        RenderSystem.texParameter(GL11.GL_TEXTURE_2D,
                GL12.GL_TEXTURE_MAX_LEVEL, 4);
    }
}
