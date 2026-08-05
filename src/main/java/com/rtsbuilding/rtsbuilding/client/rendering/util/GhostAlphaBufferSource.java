package com.rtsbuilding.rtsbuilding.client.rendering.util;

import com.rtsbuilding.rtsbuilding.platform.render.BufferBuilder;

import java.nio.ByteBuffer;

/**
 * 1.12 幽灵模型的顶点 alpha 策略。
 *
 * <p>1.12 没有 MultiBufferSource/VertexConsumer；模型先烘焙进调用方拥有的 BLOCK
 * 缓冲，再由本工具原地覆盖颜色元素的 alpha 字节。RGB、光照、法线与纹理坐标均保留。</p>
 */
public final class GhostAlphaBufferSource {
    private GhostAlphaBufferSource() {
    }

    public static void forceVertexAlpha(BufferBuilder buffer, float alpha) {
        if (buffer == null || buffer.getVertexFormat() == null) return;
        int alphaByte = Math.max(0, Math.min(255, Math.round(alpha * 255.0F)));
        int stride = buffer.getVertexFormat().getSize();
        int colorOffset = buffer.getVertexFormat().getColorOffset();
        if (stride <= 0 || colorOffset < 0) return;
        ByteBuffer bytes = buffer.getByteBuffer();
        for (int vertex = 0; vertex < buffer.getVertexCount(); vertex++) {
            bytes.put(vertex * stride + colorOffset + 3, (byte) alphaByte);
        }
    }
}
