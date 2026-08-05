package com.rtsbuilding.rtsbuilding.platform.render;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * 可独立拥有、延迟提交的 1.7.10 顶点缓冲。
 *
 * <p>原版 1.7.10 的 Tessellator 是全局单例，无法安全承载 RTS 同时存在的深度、无深度、
 * 填充与线框缓冲。本类先把顶点保存到自己的字节缓冲，上传时才短暂借用原版 Tessellator，
 * 因而保留了新主线已经验证过的“私有缓冲，不结束 Minecraft 共享缓冲”边界。</p>
 */
public final class BufferBuilder {
    private ByteBuffer bytes;
    private VertexFormat format;
    private int drawMode;
    private int vertexCount;
    private boolean drawing;
    private double translationX;
    private double translationY;
    private double translationZ;
    private double nextX;
    private double nextY;
    private double nextZ;
    private double nextU;
    private double nextV;
    private int nextRed = 255;
    private int nextGreen = 255;
    private int nextBlue = 255;
    private int nextAlpha = 255;

    public BufferBuilder(int initialCapacity) {
        this.bytes = allocate(Math.max(256, initialCapacity));
    }

    public void begin(int drawMode, VertexFormat format) {
        if (this.drawing) throw new IllegalStateException("Buffer is already building");
        this.drawMode = drawMode;
        this.format = format;
        this.vertexCount = 0;
        this.drawing = true;
        resetPendingVertex();
    }

    public BufferBuilder pos(double x, double y, double z) {
        this.nextX = x + this.translationX;
        this.nextY = y + this.translationY;
        this.nextZ = z + this.translationZ;
        return this;
    }

    public BufferBuilder tex(double u, double v) {
        this.nextU = u;
        this.nextV = v;
        return this;
    }

    public BufferBuilder color(float red, float green, float blue, float alpha) {
        return color(toByte(red), toByte(green), toByte(blue), toByte(alpha));
    }

    public BufferBuilder color(int red, int green, int blue, int alpha) {
        this.nextRed = clampByte(red);
        this.nextGreen = clampByte(green);
        this.nextBlue = clampByte(blue);
        this.nextAlpha = clampByte(alpha);
        return this;
    }

    public void endVertex() {
        if (!this.drawing || this.format == null) throw new IllegalStateException("Buffer is not building");
        int offset = this.vertexCount * this.format.getSize();
        ensureCapacity(offset + this.format.getSize());
        this.bytes.putFloat(offset, (float) this.nextX);
        this.bytes.putFloat(offset + 4, (float) this.nextY);
        this.bytes.putFloat(offset + 8, (float) this.nextZ);
        if (this.format.getTextureOffset() >= 0) {
            this.bytes.putFloat(offset + this.format.getTextureOffset(), (float) this.nextU);
            this.bytes.putFloat(offset + this.format.getTextureOffset() + 4, (float) this.nextV);
        }
        if (this.format.getColorOffset() >= 0) {
            int color = offset + this.format.getColorOffset();
            this.bytes.put(color, (byte) this.nextRed);
            this.bytes.put(color + 1, (byte) this.nextGreen);
            this.bytes.put(color + 2, (byte) this.nextBlue);
            this.bytes.put(color + 3, (byte) this.nextAlpha);
        }
        this.vertexCount++;
        resetPendingVertex();
    }

    public void finishDrawing() {
        if (!this.drawing) throw new IllegalStateException("Buffer is not building");
        this.drawing = false;
    }

    public void reset() {
        this.vertexCount = 0;
        this.drawing = false;
        resetPendingVertex();
    }

    public void setTranslation(double x, double y, double z) {
        this.translationX = x;
        this.translationY = y;
        this.translationZ = z;
    }

    public int getVertexCount() { return this.vertexCount; }
    public int getDrawMode() { return this.drawMode; }
    public VertexFormat getVertexFormat() { return this.format; }
    public ByteBuffer getByteBuffer() { return this.bytes; }

    private void resetPendingVertex() {
        this.nextX = this.nextY = this.nextZ = 0.0D;
        this.nextU = this.nextV = 0.0D;
        this.nextRed = this.nextGreen = this.nextBlue = this.nextAlpha = 255;
    }

    private void ensureCapacity(int required) {
        if (required <= this.bytes.capacity()) return;
        int capacity = this.bytes.capacity();
        while (capacity < required) capacity = Math.max(capacity * 2, required);
        ByteBuffer expanded = allocate(capacity);
        for (int index = 0; index < this.vertexCount * this.format.getSize(); index++) {
            expanded.put(index, this.bytes.get(index));
        }
        this.bytes = expanded;
    }

    private static ByteBuffer allocate(int capacity) {
        return ByteBuffer.allocate(capacity).order(ByteOrder.nativeOrder());
    }

    private static int toByte(float value) { return clampByte(Math.round(value * 255.0F)); }
    private static int clampByte(int value) { return Math.max(0, Math.min(255, value)); }
}
