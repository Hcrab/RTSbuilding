package com.rtsbuilding.rtsbuilding.client.render.pass;

import com.mojang.blaze3d.vertex.PoseStack;
import com.rtsbuilding.rtsbuilding.client.render.RenderPass;
import net.minecraft.client.Minecraft;

/**
 * 蓝图幽灵渲染 pass——蓝图预览包围盒 + 方块模型。
 * <p>当前为空实现，由 GhostBlockPass 统一处理。</p>
 */
public final class BlueprintGhostPass implements RenderPass {

    @Override
    public boolean shouldRender(Minecraft mc) {
        return mc.screen instanceof com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
    }

    @Override
    public void render(Minecraft mc, BufferAllocator alloc, PoseStack poseStack, float partialTick, int frameIndex) {
        // 已由 GhostBlockPass 中的 GhostBlueprintBridge 统一渲染
    }

    @Override
    public int requiredBuffers() {
        return 0; // 由 GhostBlockPass 处理
    }
}
