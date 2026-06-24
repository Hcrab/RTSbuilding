package com.rtsbuilding.rtsbuilding.client.render.pass;

import com.mojang.blaze3d.vertex.PoseStack;
import com.rtsbuilding.rtsbuilding.client.kernel.RtsClientKernel;
import com.rtsbuilding.rtsbuilding.client.render.GhostRingBuffer;
import com.rtsbuilding.rtsbuilding.client.render.RenderPass;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;

/**
 * 幽灵方块渲染 pass——使用 {@link GhostRingBuffer} 零分配渲染。
 * 完全自包含，不依赖旧 client 代码。
 */
public final class GhostBlockPass implements RenderPass {

    @Override
    public boolean shouldRender(Minecraft mc) {
        return mc.screen instanceof com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
    }

    @Override
    public void render(Minecraft mc, BufferAllocator alloc, PoseStack poseStack, float partialTick, int frameIndex) {
        // 通过 GhostRingBuffer 渲染存放的幽灵方块
        GhostRingBuffer ringBuf = getGhostRingBuffer();
        if (ringBuf != null && !ringBuf.isEmpty()) {
            long now = RtsClientKernel.get().renderPipeline().frameMillis();
            ringBuf.prune(now, 3000L);
            ringBuf.forEach((key, state, addedAt) -> {
                float ageTicks = (now - addedAt) / 50.0f;
                float scale = Math.min(1.0f, ageTicks / 6.0f);
                float inset = 0.5f - scale * 0.44f;
                int x = net.minecraft.core.BlockPos.getX(key);
                int y = net.minecraft.core.BlockPos.getY(key);
                int z = net.minecraft.core.BlockPos.getZ(key);
                // 半透明填充
                LevelRenderer.addChainedFilledBoxVertices(
                        poseStack, alloc.filledBox(),
                        x + inset, y + inset, z + inset,
                        x + 1.0 - inset, y + 1.0 - inset, z + 1.0 - inset,
                        0.4f, 0.85f, 0.9f, 0.12f);
                // 线框
                LevelRenderer.renderLineBox(
                        poseStack, alloc.lines(),
                        x + inset, y + inset, z + inset,
                        x + 1.0 - inset, y + 1.0 - inset, z + 1.0 - inset,
                        0.3f, 0.85f, 1.0f, 0.6f);
            });
        }
    }

    @Override
    public int requiredBuffers() {
        return 1 | 2; // LINES | FILLED_BOX
    }

    private static GhostRingBuffer getGhostRingBuffer() {
        return com.rtsbuilding.rtsbuilding.client.render.RingBufferHolder.INSTANCE;
    }
}
