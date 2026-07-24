package com.rtsbuilding.rtsbuilding.client.render;

import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jetbrains.annotations.NotNull;


public class CapturedFrameTexture extends AbstractTexture {

    public CapturedFrameTexture() {
        
        
        this.blur = true;
    }

    @Override
    public void load(@NotNull ResourceManager resourceManager) {
    }

    
    public void setGlTextureId(int glId) {
        this.id = glId;
    }
}
