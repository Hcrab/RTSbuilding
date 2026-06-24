package com.rtsbuilding.rtsbuilding.client.render.pass;

import com.mojang.blaze3d.vertex.PoseStack;
import com.rtsbuilding.rtsbuilding.client.render.RenderPass;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * 交互目标渲染 pass——悬停方块的包围盒 + 高亮。
 * 完全自包含，不依赖旧 client 代码。
 */
public final class InteractionTargetPass implements RenderPass {

    @Override
    public boolean shouldRender(Minecraft mc) {
        return mc.screen instanceof BuilderScreen;
    }

    @Override
    public void render(Minecraft mc, BufferAllocator alloc, PoseStack poseStack, float partialTick, int frameIndex) {
        var hit = mc.hitResult;
        if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
            var blockHit = (BlockHitResult) hit;
            var pos = blockHit.getBlockPos();
            // 高亮金框
            LevelRenderer.renderLineBox(
                    poseStack, alloc.brackets(),
                    pos.getX(), pos.getY(), pos.getZ(),
                    pos.getX() + 1.0, pos.getY() + 1.0, pos.getZ() + 1.0,
                    1.0f, 0.85f, 0.35f, 0.9f);
            // 命中面半透明高亮（无深度检测）
            LevelRenderer.addChainedFilledBoxVertices(
                    poseStack, alloc.noDepth(),
                    pos.getX(), pos.getY(), pos.getZ(),
                    pos.getX() + 1.0, pos.getY() + 1.0, pos.getZ() + 1.0,
                    1.0f, 0.95f, 0.5f, 0.06f);
        }
    }

    @Override
    public int requiredBuffers() {
        return 4 | 8; // BRACKET_QUADS | TARGET_NO_DEPTH
    }
}
