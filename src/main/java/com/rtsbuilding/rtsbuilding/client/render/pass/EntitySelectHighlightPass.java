package com.rtsbuilding.rtsbuilding.client.render.pass;

import com.mojang.blaze3d.vertex.PoseStack;
import com.rtsbuilding.rtsbuilding.client.render.RenderPass;
import com.rtsbuilding.rtsbuilding.client.render.util.CornerBracketRenderer;
import com.rtsbuilding.rtsbuilding.client.screen.panel.select.SelectPanel;
import com.rtsbuilding.rtsbuilding.client.screen.panel.select.SelectionHighlight;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;

/**
 * 交互目标选择高亮渲染 pass——为 {@link com.rtsbuilding.rtsbuilding.client.screen.panel.select.SelectPanel}
 * 中悬停的实体或方块绘制角支架线框。
 *
 * <p>通过注入的 {@link SelectionHighlight} 获取当前悬停目标，
 * 并利用内置 {@link CornerBracketRenderer.SmoothTarget} 做帧间平滑过渡，
 * 使角支架在条目间切换时丝滑移动而非瞬间跳跃。</p>
 */
public final class EntitySelectHighlightPass implements RenderPass {

    /** 高亮状态源——由 {@link BuilderScreen} 在 init() 中注入 */
    @Nullable
    private SelectionHighlight highlightSource;

    /**
     * 设置高亮状态源。
     * <p>由 {@link BuilderScreen#init()} 在创建后调用，建立与 {@link SelectPanel} 的共享状态通道。</p>
     */
    public void setHighlightSource(@org.jetbrains.annotations.Nullable SelectionHighlight highlightSource) {
        this.highlightSource = highlightSource;
    }

    @Override
    public boolean shouldRender(Minecraft mc) {
        return mc.screen instanceof com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
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

        // 深度检测层
        CornerBracketRenderer.renderCornerBrackets(poseStack, alloc.brackets(),
                smoothBounds.minX, smoothBounds.minY, smoothBounds.minZ,
                smoothBounds.maxX, smoothBounds.maxY, smoothBounds.maxZ,
                r, g, b, 0.9f, 0);
        // 穿墙层
        if (BoxSelectionPass.depthTestEnabled) {
            CornerBracketRenderer.renderCornerBrackets(poseStack, alloc.noDepth(),
                    smoothBounds.minX, smoothBounds.minY, smoothBounds.minZ,
                    smoothBounds.maxX, smoothBounds.maxY, smoothBounds.maxZ,
                    r, g, b, CornerBracketRenderer.DEFAULT_NO_DEPTH_ALPHA, 0);
        }
    }

    @Override
    public int requiredBuffers() {
        return 4 | 8; // BRACKET_QUADS | TARGET_NO_DEPTH
    }
}
