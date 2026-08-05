package com.rtsbuilding.rtsbuilding.platform.render;

/** RTSBuilding 在 1.7.10 上实际使用的四种顶点布局。 */
public final class DefaultVertexFormats {
    public static final VertexFormat POSITION_COLOR = new VertexFormat(16, -1, 12);
    public static final VertexFormat POSITION_TEX = new VertexFormat(20, 12, -1);
    public static final VertexFormat POSITION_TEX_COLOR = new VertexFormat(24, 12, 20);
    public static final VertexFormat BLOCK = new VertexFormat(24, 12, 20);

    private DefaultVertexFormats() {
    }
}
