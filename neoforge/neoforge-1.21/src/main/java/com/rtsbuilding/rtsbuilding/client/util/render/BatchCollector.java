package com.rtsbuilding.rtsbuilding.client.util.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.rtsbuilding.rtsbuilding.client.util.render.model.TextureInfo;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Deprecated
public final class BatchCollector {

    

    
    record BlitCmd(
            ResourceLocation texture,
            TextureInfo texInfo,        
            int dstX, int dstY, int dstW, int dstH,
            int srcX, int srcY, int srcW, int srcH,
            int texW, int texH,
            int z,                  
            float tintR, float tintG, float tintB, float tintA  
    ) {}

    

    
    private final List<BlitCmd> commands = new ArrayList<>(256);

    
    private boolean frameActive;

    

    
    public void beginFrame() {
        this.commands.clear();
        this.frameActive = true;
    }

    
    public void endFrame(GuiGraphics g) {
        if (!this.frameActive) return;
        this.frameActive = false;
        flush(g);
    }

    
    public void flush(GuiGraphics g) {
        if (commands.isEmpty()) return;

        
        commands.sort(Comparator.comparing(BlitCmd::texture)
                .thenComparingInt(cmd -> cmd.texInfo.filterMode().ordinal())
                .thenComparingInt(BlitCmd::z));

        try (BlendScope blend = BlendScope.normal()) {
            ResourceLocation currentTex = null;
            TextureInfo currentTexInfo = null;

            for (BlitCmd cmd : commands) {
                
                if (cmd.texture != currentTex || cmd.texInfo != currentTexInfo) {
                    if (cmd.texInfo != currentTexInfo) {
                        currentTexInfo = cmd.texInfo;
                        FilterState.getInstance().apply(cmd.texInfo);
                    }
                    currentTex = cmd.texture;
                }

                
                RenderSystem.setShaderColor(cmd.tintR, cmd.tintG, cmd.tintB, cmd.tintA);

                
                g.blit(currentTex,
                        cmd.dstX, cmd.dstY, cmd.dstW, cmd.dstH,
                        cmd.srcX, cmd.srcY, cmd.srcW, cmd.srcH,
                        cmd.texW, cmd.texH);
            }

            
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        }

        commands.clear();
    }

    

    
    private static float[] captureTint() {
        return RenderSystem.getShaderColor();
    }

    
    public void sprite(TextureInfo texInfo, int u, int v, int srcW, int srcH,
                       int dstX, int dstY, int dstW, int dstH) {
        if (!frameActive) return;
        float[] tint = captureTint();
        commands.add(new BlitCmd(
                texInfo.location(), texInfo,
                dstX, dstY, dstW, dstH,
                u, v, srcW, srcH,
                texInfo.fullWidth(), texInfo.fullHeight(),
                0, tint[0], tint[1], tint[2], tint[3]));
    }

    
    public void nineSlice(TextureInfo texInfo, int u, int v, int regionW, int regionH, int border,
                          int dstX, int dstY, int dstW, int dstH) {
        if (!frameActive) return;

        ResourceLocation tex = texInfo.location();
        int texW = texInfo.fullWidth();
        int texH = texInfo.fullHeight();
        float[] tint = captureTint();

        
        int srcLeft = u;
        int srcRight = u + regionW - border;
        int srcTop = v;
        int srcBottom = v + regionH - border;

        int dstLeft = dstX;
        int dstRight = dstX + dstW - border;
        int dstTop = dstY;
        int dstBottom = dstY + dstH - border;

        
        int srcCenterW = regionW - border * 2;
        int srcCenterH = regionH - border * 2;
        int dstCenterW = dstW - border * 2;
        int dstCenterH = dstH - border * 2;

        
        

        
        
        commands.add(new BlitCmd(tex, texInfo, dstLeft, dstTop, border, border,
                srcLeft, srcTop, border, border, texW, texH, 0, tint[0], tint[1], tint[2], tint[3]));

        
        if (dstCenterW > 0 && srcCenterW > 0) {
            int tiledSrcW = Math.min(srcCenterW, dstCenterW);
            commands.add(new BlitCmd(tex, texInfo, dstLeft + border, dstTop, dstCenterW, border,
                    srcLeft + border, srcTop, tiledSrcW, border, texW, texH, 0, tint[0], tint[1], tint[2], tint[3]));
        }

        
        commands.add(new BlitCmd(tex, texInfo, dstRight, dstTop, border, border,
                srcRight, srcTop, border, border, texW, texH, 0, tint[0], tint[1], tint[2], tint[3]));

        
        if (dstCenterH > 0) {
            
            commands.add(new BlitCmd(tex, texInfo, dstLeft, dstTop + border, border, dstCenterH,
                    srcLeft, srcTop + border, border, srcCenterH, texW, texH, 0, tint[0], tint[1], tint[2], tint[3]));

            
            if (dstCenterW > 0 && srcCenterW > 0) {
                int tiledSrcW2 = Math.min(srcCenterW, dstCenterW);
                int tiledSrcH2 = Math.min(srcCenterH, dstCenterH);
                commands.add(new BlitCmd(tex, texInfo,
                        dstLeft + border, dstTop + border, dstCenterW, dstCenterH,
                        srcLeft + border, srcTop + border, tiledSrcW2, tiledSrcH2, texW, texH, 0, tint[0], tint[1], tint[2], tint[3]));
            }

            
            commands.add(new BlitCmd(tex, texInfo, dstRight, dstTop + border, border, dstCenterH,
                    srcRight, srcTop + border, border, srcCenterH, texW, texH, 0, tint[0], tint[1], tint[2], tint[3]));
        }

        
        
        commands.add(new BlitCmd(tex, texInfo, dstLeft, dstBottom, border, border,
                srcLeft, srcBottom, border, border, texW, texH, 0, tint[0], tint[1], tint[2], tint[3]));

        
        if (dstCenterW > 0 && srcCenterW > 0) {
            int tiledSrcW3 = Math.min(srcCenterW, dstCenterW);
            commands.add(new BlitCmd(tex, texInfo, dstLeft + border, dstBottom, dstCenterW, border,
                    srcLeft + border, srcBottom, tiledSrcW3, border, texW, texH, 0, tint[0], tint[1], tint[2], tint[3]));
        }

        
        commands.add(new BlitCmd(tex, texInfo, dstRight, dstBottom, border, border,
                srcRight, srcBottom, border, border, texW, texH, 0, tint[0], tint[1], tint[2], tint[3]));
    }

    
    public int size() {
        return commands.size();
    }

    
    public boolean isFrameActive() {
        return frameActive;
    }
}
