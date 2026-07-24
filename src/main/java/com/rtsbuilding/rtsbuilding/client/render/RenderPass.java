package com.rtsbuilding.rtsbuilding.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rtsbuilding.rtsbuilding.client.render.util.CursorRaycaster.CursorRay;
import net.minecraft.client.Minecraft;

import javax.annotation.Nullable;


public interface RenderPass {

    
    default boolean shouldRender(Minecraft mc) {
        return true;
    }

    
    void render(Minecraft mc, BufferAllocator alloc, PoseStack poseStack, float partialTick, int frameIndex);

    
    default int requiredBuffers() {
        return 0;
    }

    
    record BufferAllocator(
            VertexConsumer lines,
            VertexConsumer filledBox,
            VertexConsumer brackets,
            VertexConsumer noDepth,
            VertexConsumer barrier,
            @Nullable CursorRay cursorRay
    ) {}
}
