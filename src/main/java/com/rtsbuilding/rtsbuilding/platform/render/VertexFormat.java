package com.rtsbuilding.rtsbuilding.platform.render;

/** 1.7.10 私有顶点缓冲需要的最小格式描述。 */
public final class VertexFormat {
    private final int size;
    private final int textureOffset;
    private final int colorOffset;

    VertexFormat(int size, int textureOffset, int colorOffset) {
        this.size = size;
        this.textureOffset = textureOffset;
        this.colorOffset = colorOffset;
    }

    public int getSize() { return this.size; }
    public int getTextureOffset() { return this.textureOffset; }
    public int getColorOffset() { return this.colorOffset; }
}
