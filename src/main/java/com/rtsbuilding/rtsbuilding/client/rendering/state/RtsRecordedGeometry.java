package com.rtsbuilding.rtsbuilding.client.rendering.state;

import com.mojang.blaze3d.vertex.VertexConsumer;

import java.util.ArrayList;
import java.util.List;

/**
 * 提取阶段冻结的 RTS 世界顶点。
 *
 * <p>它只记录 Mojang 通用顶点属性，不认识 NeoForge 事件、RenderType 或 collector。
 * 平台桥在提交阶段按 {@link Layer} 选择真正的渲染层并重放这些顶点。</p>
 */
public final class RtsRecordedGeometry {
    private RtsRecordedGeometry() {
    }

    public enum Layer {
        CHUNK_XRAY_FILL,
        CHUNK_XRAY_LINES,
        BOUNDARY,
        LINES,
        FILLED_BOX,
        BRACKET_QUADS,
        TARGET_NO_DEPTH_QUADS,
        CULLING_HANDLE_NO_DEPTH_FILL,
        CULLING_HANDLE_NO_DEPTH_LINES
    }

    public record Batch(Layer layer, List<Vertex> vertices) {
        public Batch {
            vertices = vertices == null ? List.of() : List.copyOf(vertices);
        }
    }

    public record Vertex(
            float x, float y, float z,
            int packedColor, boolean packedColorUsed,
            int red, int green, int blue, int alpha,
            float u, float v, boolean uvUsed,
            int overlayU, int overlayV, boolean overlayUsed,
            int lightU, int lightV, boolean lightUsed,
            float normalX, float normalY, float normalZ, boolean normalUsed,
            float lineWidth, boolean lineWidthUsed) {

        public void replay(
                com.mojang.blaze3d.vertex.PoseStack.Pose pose,
                VertexConsumer consumer,
                boolean requireLineWidth) {
            consumer.addVertex(pose, x, y, z);
            if (packedColorUsed) {
                consumer.setColor(packedColor);
            } else {
                consumer.setColor(red, green, blue, alpha);
            }
            if (uvUsed) consumer.setUv(u, v);
            if (overlayUsed) consumer.setUv1(overlayU, overlayV);
            if (lightUsed) consumer.setUv2(lightU, lightV);
            if (normalUsed) consumer.setNormal(pose, normalX, normalY, normalZ);
            if (lineWidthUsed || requireLineWidth) {
                consumer.setLineWidth(lineWidthUsed ? lineWidth : 1.0F);
            }
        }
    }

    /**
     * 把旧几何算法写入的 {@link VertexConsumer} 调用冻结为不可变顶点。
     * 新顶点开始时会自动结束前一个顶点，行为与 BufferBuilder 一致。
     */
    public static final class Recorder implements VertexConsumer {
        private final List<Vertex> vertices = new ArrayList<>();
        private MutableVertex current;

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            finishCurrent();
            current = new MutableVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            MutableVertex vertex = requireCurrent();
            vertex.red = red;
            vertex.green = green;
            vertex.blue = blue;
            vertex.alpha = alpha;
            vertex.packedColorUsed = false;
            return this;
        }

        @Override
        public VertexConsumer setColor(int color) {
            MutableVertex vertex = requireCurrent();
            vertex.packedColor = color;
            vertex.packedColorUsed = true;
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            MutableVertex vertex = requireCurrent();
            vertex.u = u;
            vertex.v = v;
            vertex.uvUsed = true;
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            MutableVertex vertex = requireCurrent();
            vertex.overlayU = u;
            vertex.overlayV = v;
            vertex.overlayUsed = true;
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            MutableVertex vertex = requireCurrent();
            vertex.lightU = u;
            vertex.lightV = v;
            vertex.lightUsed = true;
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            MutableVertex vertex = requireCurrent();
            vertex.normalX = x;
            vertex.normalY = y;
            vertex.normalZ = z;
            vertex.normalUsed = true;
            return this;
        }

        @Override
        public VertexConsumer setLineWidth(float width) {
            MutableVertex vertex = requireCurrent();
            vertex.lineWidth = width;
            vertex.lineWidthUsed = true;
            return this;
        }

        public List<Vertex> freeze() {
            finishCurrent();
            return List.copyOf(vertices);
        }

        private MutableVertex requireCurrent() {
            if (current == null) {
                throw new IllegalStateException("必须先调用 addVertex 再写入顶点属性");
            }
            return current;
        }

        private void finishCurrent() {
            if (current != null) {
                vertices.add(current.freeze());
                current = null;
            }
        }
    }

    private static final class MutableVertex {
        private final float x;
        private final float y;
        private final float z;
        private int packedColor = 0xFFFFFFFF;
        private boolean packedColorUsed;
        private int red = 255;
        private int green = 255;
        private int blue = 255;
        private int alpha = 255;
        private float u;
        private float v;
        private boolean uvUsed;
        private int overlayU;
        private int overlayV;
        private boolean overlayUsed;
        private int lightU;
        private int lightV;
        private boolean lightUsed;
        private float normalX;
        private float normalY;
        private float normalZ;
        private boolean normalUsed;
        private float lineWidth = 1.0F;
        private boolean lineWidthUsed;

        private MutableVertex(float x, float y, float z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        private Vertex freeze() {
            return new Vertex(
                    x, y, z,
                    packedColor, packedColorUsed,
                    red, green, blue, alpha,
                    u, v, uvUsed,
                    overlayU, overlayV, overlayUsed,
                    lightU, lightV, lightUsed,
                    normalX, normalY, normalZ, normalUsed,
                    lineWidth, lineWidthUsed);
        }
    }
}
