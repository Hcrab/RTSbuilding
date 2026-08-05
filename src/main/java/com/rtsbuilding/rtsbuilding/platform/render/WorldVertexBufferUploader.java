package com.rtsbuilding.rtsbuilding.platform.render;

import java.nio.ByteBuffer;

import net.minecraft.client.renderer.Tessellator;

/** 把 RTS 私有缓冲短暂提交给 1.7.10 的全局 Tessellator。 */
public final class WorldVertexBufferUploader {
    public void draw(BufferBuilder buffer) {
        if (buffer == null || buffer.getVertexFormat() == null || buffer.getVertexCount() <= 0) return;
        Tessellator tessellator = Tessellator.instance;
        VertexFormat format = buffer.getVertexFormat();
        ByteBuffer bytes = buffer.getByteBuffer();
        tessellator.startDrawing(buffer.getDrawMode());
        for (int vertex = 0; vertex < buffer.getVertexCount(); vertex++) {
            int offset = vertex * format.getSize();
            if (format.getTextureOffset() >= 0) {
                tessellator.setTextureUV(
                        bytes.getFloat(offset + format.getTextureOffset()),
                        bytes.getFloat(offset + format.getTextureOffset() + 4));
            }
            if (format.getColorOffset() >= 0) {
                int color = offset + format.getColorOffset();
                tessellator.setColorRGBA(bytes.get(color) & 255, bytes.get(color + 1) & 255,
                        bytes.get(color + 2) & 255, bytes.get(color + 3) & 255);
            }
            tessellator.addVertex(bytes.getFloat(offset), bytes.getFloat(offset + 4), bytes.getFloat(offset + 8));
        }
        tessellator.draw();
    }
}
