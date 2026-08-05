package com.rtsbuilding.rtsbuilding.platform.render;

/** 仅为仍使用新版本 Tessellator 调用形状的 UI 绘制提供共享桥。 */
public final class Tessellator {
    private static final Tessellator INSTANCE = new Tessellator();
    private final BufferBuilder buffer = new BufferBuilder(256 * 1024);
    private final WorldVertexBufferUploader uploader = new WorldVertexBufferUploader();

    private Tessellator() {
    }

    public static Tessellator getInstance() { return INSTANCE; }
    public BufferBuilder getBuffer() { return this.buffer; }

    public void draw() {
        this.buffer.finishDrawing();
        try {
            this.uploader.draw(this.buffer);
        } finally {
            this.buffer.reset();
        }
    }
}
