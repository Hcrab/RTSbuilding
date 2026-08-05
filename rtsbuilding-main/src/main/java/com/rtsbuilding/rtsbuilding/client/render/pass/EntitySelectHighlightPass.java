package com.rtsbuilding.rtsbuilding.client.render.pass;

import com.mojang.blaze3d.vertex.PoseStack;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.interaction.SelectionHighlight;
import com.rtsbuilding.rtsbuilding.client.render.RenderPass;
import com.rtsbuilding.rtsbuilding.client.render.util.CornerBracketRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;

public final class EntitySelectHighlightPass implements RenderPass {

    
    @Nullable
    private SelectionHighlight highlightSource;

    
    public void setHighlightSource(@org.jetbrains.annotations.Nullable SelectionHighlight highlightSource) {
        this.highlightSource = highlightSource;
    }

    @Override
    public boolean shouldRender(Minecraft mc) {
        return mc.screen instanceof com.rtsbuilding.rtsbuilding.client.presentation.standalone.BuilderScreen;
    }

    @Override
    public void render(Minecraft mc, BufferAllocator alloc, PoseStack poseStack,
                       float partialTick, int frameIndex) {
        if (mc.level == null || highlightSource == null) return;

        AABB smoothBounds = highlightSource.updateAndGetSmoothBounds();
        if (smoothBounds == null) return;

        int color = InteractionTargetPass.entityTargetColor;
        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;

        
        CornerBracketRenderer.renderCornerBrackets(poseStack, alloc.brackets(),
                smoothBounds.minX, smoothBounds.minY, smoothBounds.minZ,
                smoothBounds.maxX, smoothBounds.maxY, smoothBounds.maxZ,
                r, g, b, 0.9f, 0);
        
        if (BoxSelectionPass.depthTestEnabled) {
            CornerBracketRenderer.renderCornerBrackets(poseStack, alloc.noDepth(),
                    smoothBounds.minX, smoothBounds.minY, smoothBounds.minZ,
                    smoothBounds.maxX, smoothBounds.maxY, smoothBounds.maxZ,
                    r, g, b, CornerBracketRenderer.DEFAULT_NO_DEPTH_ALPHA, 0);
        }
    }

    @Override
    public int requiredBuffers() {
        return 4 | 8; 
    }
}
