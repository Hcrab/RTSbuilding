package com.rtsbuilding.rtsbuilding.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;

/**
 * 渲染 pass 接口——所有世界覆盖层渲染通过此接口注册到 {@link RenderPipeline}。
 *
 * <p>每个 pass 渲染时从 {@link BufferAllocator} 获取预分配的缓冲区。</p>
 */
public interface RenderPass {

    /** 是否需要渲染。返回 false 时跳过整个 pass，零开销。 */
    default boolean shouldRender(Minecraft mc) {
        return true;
    }

    /** 执行渲染逻辑。
     * @param poseStack 带摄像机偏移的 PoseStack（世界坐标空间），由 {@link RenderPipeline} 传入 */
    void render(Minecraft mc, BufferAllocator alloc, PoseStack poseStack, float partialTick, int frameIndex);

    /** 返回需要的缓冲区掩码（当前未使用，保留扩展）。 */
    default int requiredBuffers() {
        return 0;
    }

    /**
     * 缓冲区分配器——渲染管线的预分配缓冲区在此传递给各 pass。
     */
    record BufferAllocator(
            VertexConsumer lines,
            VertexConsumer filledBox,
            VertexConsumer brackets,
            VertexConsumer noDepth,
            VertexConsumer barrier
    ) {}
}
