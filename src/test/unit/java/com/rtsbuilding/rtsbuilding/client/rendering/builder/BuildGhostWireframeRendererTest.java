package com.rtsbuilding.rtsbuilding.client.rendering.builder;

import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BuildGhostWireframeRendererTest {
    @Test
    void appendsEveryBlockToTheBufferOwnedByTheWorldRenderStage() {
        BufferBuilder callerBuffer = new BufferBuilder(4096);
        callerBuffer.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);

        BuildGhostWireframeRenderer.renderWireframes(
                Arrays.asList(new BlockPos(2, 64, 3), new BlockPos(3, 64, 3)),
                callerBuffer,
                true);

        // 与 main 的 renderLineBox 一致：每个方块追加 12 条独立边，即 24 个顶点。
        // 若渲染器重新创建、提交或重置了私有缓冲，这里会得到 0 而不是 48。
        assertEquals(48, callerBuffer.getVertexCount());
    }
}
