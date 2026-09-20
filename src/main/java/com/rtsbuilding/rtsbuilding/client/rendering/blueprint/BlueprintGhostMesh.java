package com.rtsbuilding.rtsbuilding.client.rendering.blueprint;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.rtsbuilding.rtsbuilding.client.screen.blueprint.BlueprintGhostBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.nio.ByteBuffer;

/**
 * 单次蓝图烘焙的资源 owner。模型仅在渲染线程分批写入私有缓冲，完成的批次常驻 GPU。
 * 同时缓存缺失/非模型方块的线框，避免异常蓝图每帧再次生成海量线段。
 * 不持有活动世界；调用方负责蓝图/世界/资源变化时关闭本对象。
 */
final class BlueprintGhostMesh implements AutoCloseable {
    private static final int BLOCKS_PER_BATCH = 1024;
    private static final int BLOCKS_PER_FRAME = 1024;
    private static final long BUILD_NANOS = 2_000_000L;
    private final List<BlueprintGhostBlock> blocks;
    private final BlueprintPreviewClip clip;
    private final BlockPos sampleAnchor;
    private final List<Batch> batches = new ArrayList<>();
    private final List<Batch> visible = new ArrayList<>();
    private final PoseStack localPose = new PoseStack();
    private int cursor;
    private int batchStart;
    private int sortCursor;
    private AABB bounds;
    private AABB batchBounds;
    private Builder builder;

    BlueprintGhostMesh(List<BlueprintGhostBlock> blocks, BlueprintPreviewClip clip, BlockPos sampleAnchor) {
        this.blocks = blocks;
        this.clip = clip;
        this.sampleAnchor = sampleAnchor.immutable();
    }

    BlockPos sampleAnchor() { return sampleAnchor; }
    boolean complete() { return cursor >= blocks.size(); }
    AABB bounds() { return bounds; }

    /** 每帧有时间与工作量双预算；一个第三方方块回调不能在中途打断，故不是硬实时保证。 */
    void advance(Minecraft minecraft, Vec3 localCamera) {
        long started = System.nanoTime();
        int advanced = 0;
        while (!complete() && advanced < BLOCKS_PER_FRAME) {
            BlueprintGhostBlock block = blocks.get(cursor++);
            advanced++;
            if (clip.contains(block.pos())) {
                AABB cell = new AABB(block.pos());
                bounds = bounds == null ? cell : bounds.minmax(cell);
                batchBounds = batchBounds == null ? cell : batchBounds.minmax(cell);
                if (builder == null) builder = new Builder();
                if (BlueprintGhostBlockModelRenderer.hasModel(block)) {
                    BlueprintGhostBlockModelRenderer.bake(minecraft, block, sampleAnchor, localPose, builder.models);
                } else {
                    VertexConsumer target = block.missing() ? builder.missing : builder.fallback;
                    BlockPos pos = block.pos();
                    LevelRenderer.renderLineBox(localPose, target,
                            pos.getX() + .04, pos.getY() + .04, pos.getZ() + .04,
                            pos.getX() + .96, pos.getY() + .96, pos.getZ() + .96,
                            1, 1, 1, .90F);
                }
            }
            if (cursor - batchStart >= BLOCKS_PER_BATCH || complete()) finishBatch(localCamera);
            if (System.nanoTime() - started >= BUILD_NANOS) break;
        }
    }

    private void finishBatch(Vec3 localCamera) {
        if (builder != null) {
            try {
                batches.add(builder.upload(batchBounds, localCamera));
            } finally {
                builder.close();
                builder = null;
            }
        }
        batchStart = cursor;
        batchBounds = null;
    }

    void draw(PoseStack pose, BlockPos anchor, Frustum frustum, Vec3 localCamera,
            float red, float green, float blue, float missingRed, float missingGreen, float missingBlue) {
        // 只排序批次与必要的透明索引，不重新生成模型顶点。
        visible.clear();
        for (Batch batch : batches) {
            if (frustum == null || frustum.isVisible(batch.bounds.move(anchor))) visible.add(batch);
        }
        visible.sort(Comparator.comparingDouble((Batch batch) ->
                batch.bounds.getCenter().distanceToSqr(localCamera)).reversed());
        if (!batches.isEmpty()) {
            long started = System.nanoTime();
            for (int checked = 0; checked < batches.size(); checked++) {
                Batch batch = batches.get(sortCursor++ % batches.size());
                if (batch.models != null && batch.sortedFrom.distanceToSqr(localCamera) >= 1) {
                    batch.resort(localCamera);
                }
                if (System.nanoTime() - started >= 1_000_000L) break;
            }
            sortCursor %= batches.size();
        }
        Matrix4f modelView = new Matrix4f(RenderSystem.getModelViewMatrix()).mul(pose.last().pose())
                .translate(anchor.getX(), anchor.getY(), anchor.getZ());
        float[] previous = RenderSystem.getShaderColor().clone();
        try {
            drawLayer(RenderType.translucent(), modelView, 0, 1, 1, 1);
            drawLayer(RenderType.lines(), modelView, 1, red, green, blue);
            drawLayer(RenderType.lines(), modelView, 2, missingRed, missingGreen, missingBlue);
        } finally {
            VertexBuffer.unbind();
            RenderSystem.setShaderColor(previous[0], previous[1], previous[2], previous[3]);
        }
    }

    private void drawLayer(RenderType type, Matrix4f modelView, int layer, float r, float g, float b) {
        type.setupRenderState();
        try {
            RenderSystem.setShaderColor(r, g, b, 1);
            for (Batch batch : visible) {
                VertexBuffer buffer = layer == 0 ? batch.models : layer == 1 ? batch.fallback : batch.missing;
                if (buffer == null) continue;
                buffer.bind();
                buffer.drawWithShader(modelView, RenderSystem.getProjectionMatrix(), RenderSystem.getShader());
            }
        } finally {
            VertexBuffer.unbind();
            type.clearRenderState();
        }
    }

    @Override
    public void close() {
        if (builder != null) { builder.close(); builder = null; }
        batches.forEach(Batch::close);
        batches.clear();
        visible.clear();
    }

    /** 一次最多暂存一个批次的原生顶点内存，上传后立即释放。 */
    private static final class Builder implements AutoCloseable {
        private final ByteBufferBuilder modelBytes = new ByteBufferBuilder(256 * 1024);
        private final ByteBufferBuilder fallbackBytes = new ByteBufferBuilder(4096);
        private final ByteBufferBuilder missingBytes = new ByteBufferBuilder(4096);
        private final BufferBuilder models = new BufferBuilder(modelBytes, VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
        private final BufferBuilder fallback = new BufferBuilder(fallbackBytes, RenderType.lines().mode(), RenderType.lines().format());
        private final BufferBuilder missing = new BufferBuilder(missingBytes, RenderType.lines().mode(), RenderType.lines().format());

        Batch upload(AABB bounds, Vec3 camera) {
            Batch batch = new Batch(bounds, camera);
            try {
                MeshData modelData = models.build();
                if (modelData != null) {
                    try {
                        batch.sortState = modelData.sortQuads(modelBytes,
                                VertexSorting.byDistance((float) camera.x, (float) camera.y, (float) camera.z));
                        batch.includeModelBounds(modelData);
                        batch.models = uploadMesh(modelData);
                    } finally {
                        modelData.close();
                    }
                }
                batch.fallback = uploadMesh(fallback.build());
                batch.missing = uploadMesh(missing.build());
                return batch;
            } catch (RuntimeException | Error failure) {
                batch.close();
                throw failure;
            } finally {
                VertexBuffer.unbind();
            }
        }

        private static VertexBuffer uploadMesh(MeshData data) {
            if (data == null) return null;
            VertexBuffer buffer = null;
            try (data) {
                buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
                buffer.bind();
                buffer.upload(data);
                return buffer;
            } catch (RuntimeException | Error failure) {
                if (buffer != null) buffer.close();
                throw failure;
            }
        }

        @Override
        public void close() {
            modelBytes.close();
            fallbackBytes.close();
            missingBytes.close();
        }
    }

    private static final class Batch implements AutoCloseable {
        private AABB bounds;
        private VertexBuffer models, fallback, missing;
        private MeshData.SortState sortState;
        private Vec3 sortedFrom;

        private Batch(AABB bounds, Vec3 sortedFrom) {
            this.bounds = bounds;
            this.sortedFrom = sortedFrom;
        }

        /** 用实际顶点扩展视锥范围，不能假定模组模型一定装在一个方块格里。 */
        private void includeModelBounds(MeshData data) {
            ByteBuffer vertices = data.vertexBuffer();
            VertexFormat format = data.drawState().format();
            int offset = format.getOffset(VertexFormatElement.POSITION);
            int stride = format.getVertexSize();
            double minX = bounds.minX, minY = bounds.minY, minZ = bounds.minZ;
            double maxX = bounds.maxX, maxY = bounds.maxY, maxZ = bounds.maxZ;
            for (int vertex = 0; vertex < data.drawState().vertexCount(); vertex++) {
                int base = vertex * stride + offset;
                float x = vertices.getFloat(base), y = vertices.getFloat(base + 4), z = vertices.getFloat(base + 8);
                minX = Math.min(minX, x); minY = Math.min(minY, y); minZ = Math.min(minZ, z);
                maxX = Math.max(maxX, x); maxY = Math.max(maxY, y); maxZ = Math.max(maxZ, z);
            }
            bounds = new AABB(minX, minY, minZ, maxX, maxY, maxZ);
        }

        private void resort(Vec3 camera) {
            if (sortState == null) return;
            try (ByteBufferBuilder indices = new ByteBufferBuilder(4096)) {
                models.bind();
                models.uploadIndexBuffer(sortState.buildSortedIndexBuffer(indices,
                        VertexSorting.byDistance((float) camera.x, (float) camera.y, (float) camera.z)));
                sortedFrom = camera;
            } finally {
                VertexBuffer.unbind();
            }
        }

        @Override
        public void close() {
            if (models != null) models.close();
            if (fallback != null) fallback.close();
            if (missing != null) missing.close();
        }
    }
}
