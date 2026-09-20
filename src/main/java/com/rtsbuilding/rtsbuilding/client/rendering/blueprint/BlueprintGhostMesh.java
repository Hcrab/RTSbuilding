package com.rtsbuilding.rtsbuilding.client.rendering.blueprint;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.rtsbuilding.rtsbuilding.client.rendering.RtsPrivateBufferLifecycle;
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

/**
 * 单次蓝图烘焙的私有 GPU 资源 owner。
 *
 * <p>Forge 1.20.1 没有 NeoForge 1.21.1 的 MeshData/ByteBufferBuilder API，
 * 因此这里使用同等语义的独占 BufferBuilder→VertexBuffer 上传链。模型在渲染线程
 * 分批建立，完成批次跨帧复用；相机变化只生成 index-only 排序索引，不令整栋网格
 * 或透明模型顶点失效。</p>
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
    private final BlueprintGhostIndexSorter indexSorter =
            new BlueprintGhostIndexSorter(RenderType.translucent());
    private final BlueprintGhostSortScheduler<Batch> sortScheduler =
            new BlueprintGhostSortScheduler<>();
    private int cursor;
    private int batchStart;
    private AABB bounds;
    private AABB batchBounds;
    private Builder builder;

    BlueprintGhostMesh(List<BlueprintGhostBlock> blocks, BlueprintPreviewClip clip,
            BlockPos sampleAnchor) {
        this.blocks = blocks == null ? List.of() : blocks;
        this.clip = clip;
        this.sampleAnchor = sampleAnchor.immutable();
    }

    BlockPos sampleAnchor() { return sampleAnchor; }
    boolean complete() { return cursor >= blocks.size(); }
    AABB bounds() { return bounds; }

    /** 每帧同时受方块数与时间预算约束；单个模型回调不会被强行中断。 */
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
                    BlueprintGhostBlockModelRenderer.bake(
                            minecraft, block, sampleAnchor, localPose, builder.models);
                } else {
                    VertexConsumer target = block.missing() ? builder.missing : builder.fallback;
                    BlockPos pos = block.pos();
                    LevelRenderer.renderLineBox(localPose, target,
                            pos.getX() + .04, pos.getY() + .04, pos.getZ() + .04,
                            pos.getX() + .96, pos.getY() + .96, pos.getZ() + .96,
                            1, 1, 1, .90F);
                }
            }
            if (cursor - batchStart >= BLOCKS_PER_BATCH || complete()) {
                finishBatch(localCamera);
            }
            if (System.nanoTime() - started >= BUILD_NANOS) break;
        }
    }

    private void finishBatch(Vec3 localCamera) {
        if (builder != null) {
            try {
                Batch batch = builder.upload(batchBounds, localCamera);
                if (batch != null) batches.add(batch);
            } finally {
                builder.close();
                builder = null;
            }
        }
        batchStart = cursor;
        batchBounds = null;
    }

    void draw(PoseStack pose, BlockPos anchor, Frustum frustum, Vec3 localCamera,
            float red, float green, float blue,
            float missingRed, float missingGreen, float missingBlue) {
        // 批次内部的透明四边形也必须随局部相机变化重排；调度器跨帧轮转并限制预算。
        sortScheduler.schedule(batches, localCamera,
                batch -> batch.sortDistanceToSqr(localCamera),
                Batch::sortEligible,
                batch -> batch.resort(indexSorter, localCamera));
        visible.clear();
        for (Batch batch : batches) {
            if (frustum == null || frustum.isVisible(batch.bounds.move(anchor))) {
                visible.add(batch);
            }
        }
        // 相机变化只影响批次绘制次序；已上传顶点和批次内容保持不变。
        visible.sort(Comparator.comparingDouble((Batch batch) ->
                batch.bounds.getCenter().distanceToSqr(localCamera)).reversed());
        Matrix4f modelView = new Matrix4f(RenderSystem.getModelViewMatrix())
                .mul(pose.last().pose())
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

    private void drawLayer(RenderType type, Matrix4f modelView, int layer,
            float red, float green, float blue) {
        type.setupRenderState();
        try {
            RenderSystem.setShaderColor(red, green, blue, 1);
            for (Batch batch : visible) {
                VertexBuffer buffer = layer == 0 ? batch.models
                        : layer == 1 ? batch.fallback : batch.missing;
                if (buffer == null || buffer.isInvalid()) continue;
                buffer.bind();
                buffer.drawWithShader(modelView, RenderSystem.getProjectionMatrix(),
                        RenderSystem.getShader());
            }
        } finally {
            VertexBuffer.unbind();
            type.clearRenderState();
        }
    }

    @Override
    public void close() {
        if (builder != null) {
            builder.close();
            builder = null;
        }
        batches.forEach(Batch::close);
        batches.clear();
        visible.clear();
        sortScheduler.reset();
        indexSorter.close();
    }

    /** 一次最多暂存一个批次的 CPU 顶点，上传后立即由 VertexBuffer 接管并释放。 */
    private static final class Builder implements AutoCloseable {
        private final RenderType modelType = RenderType.translucent();
        private final RenderType lineType = RenderType.lines();
        private final BufferBuilder modelBuilder = new BufferBuilder(modelType.bufferSize());
        private final BufferBuilder fallbackBuilder = new BufferBuilder(lineType.bufferSize());
        private final BufferBuilder missingBuilder = new BufferBuilder(lineType.bufferSize());
        private final VertexConsumer models = modelBuilder;
        private final VertexConsumer fallback = fallbackBuilder;
        private final VertexConsumer missing = missingBuilder;

        private Builder() {
            RtsPrivateBufferLifecycle.begin(modelBuilder, modelType.mode(), modelType.format());
            RtsPrivateBufferLifecycle.begin(fallbackBuilder, lineType.mode(), lineType.format());
            RtsPrivateBufferLifecycle.begin(missingBuilder, lineType.mode(), lineType.format());
        }

        Batch upload(AABB bounds, Vec3 camera) {
            if (bounds == null) return null;
            Batch batch = new Batch(bounds, camera);
            try {
                ModelData modelData = buildModel(modelBuilder,
                        VertexSorting.byDistance(
                                (float) camera.x, (float) camera.y, (float) camera.z));
                if (modelData != null) {
                    batch.includeModelBounds(modelData.rendered());
                    batch.sortState = modelData.sortState();
                    batch.models = uploadMesh(modelData.rendered());
                    if (batch.models == null) {
                        batch.sortState = null;
                    }
                }
                batch.fallback = uploadMesh(build(fallbackBuilder,
                        VertexSorting.DISTANCE_TO_ORIGIN, false));
                batch.missing = uploadMesh(build(missingBuilder,
                        VertexSorting.DISTANCE_TO_ORIGIN, false));
                return batch.hasBuffer() ? batch : null;
            } catch (RuntimeException | Error failure) {
                batch.close();
                throw failure;
            }
        }

        private static ModelData buildModel(BufferBuilder builder, VertexSorting sorting) {
            if (!builder.building() || builder.isCurrentBatchEmpty()) {
                if (builder.building()) {
                    BufferBuilder.RenderedBuffer discarded = builder.endOrDiscardIfEmpty();
                    if (discarded != null) discarded.release();
                }
                return null;
            }
            builder.setQuadSorting(sorting);
            // SortState 必须在 end/reset 前捕获，供之后的 index-only scratch 使用。
            BufferBuilder.SortState sortState = builder.getSortState();
            return new ModelData(builder.end(), sortState);
        }

        private static BufferBuilder.RenderedBuffer build(BufferBuilder builder,
                VertexSorting sorting, boolean sortQuads) {
            if (!builder.building() || builder.isCurrentBatchEmpty()) {
                if (builder.building()) {
                    BufferBuilder.RenderedBuffer discarded = builder.endOrDiscardIfEmpty();
                    if (discarded != null) discarded.release();
                }
                return null;
            }
            if (sortQuads) {
                builder.setQuadSorting(sorting);
            }
            return builder.end();
        }

        private static VertexBuffer uploadMesh(BufferBuilder.RenderedBuffer data) {
            if (data == null) return null;
            VertexBuffer buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
            boolean handedOff = false;
            try {
                if (buffer.isInvalid()) return null;
                buffer.bind();
                if (buffer.isInvalid()) return null;
                handedOff = true;
                buffer.upload(data);
                return buffer;
            } catch (RuntimeException | Error failure) {
                buffer.close();
                throw failure;
            } finally {
                VertexBuffer.unbind();
                if (!handedOff) {
                    // upload 尚未接管时，RenderedBuffer 仍由本方法负责释放。
                    data.release();
                    buffer.close();
                }
            }
        }

        @Override
        public void close() {
            discardIfBuilding(modelBuilder);
            discardIfBuilding(fallbackBuilder);
            discardIfBuilding(missingBuilder);
        }

        private static void discardIfBuilding(BufferBuilder buffer) {
            if (buffer.building()) {
                BufferBuilder.RenderedBuffer discarded = buffer.endOrDiscardIfEmpty();
                if (discarded != null) discarded.release();
            }
        }

        private record ModelData(BufferBuilder.RenderedBuffer rendered,
                BufferBuilder.SortState sortState) {
        }
    }

    private static final class Batch implements AutoCloseable {
        private AABB bounds;
        private VertexBuffer models;
        private VertexBuffer fallback;
        private VertexBuffer missing;
        private BufferBuilder.SortState sortState;
        private Vec3 sortedFrom;

        private Batch(AABB bounds, Vec3 sortedFrom) {
            this.bounds = bounds;
            this.sortedFrom = sortedFrom;
        }
        private boolean hasBuffer() { return models != null || fallback != null || missing != null; }

        private boolean sortEligible() {
            return models != null && !models.isInvalid()
                    && sortState != null && sortedFrom != null;
        }

        private double sortDistanceToSqr(Vec3 camera) {
            return sortedFrom == null ? 0.0D : sortedFrom.distanceToSqr(camera);
        }

        private void resort(BlueprintGhostIndexSorter sorter, Vec3 camera) {
            if (!sortEligible() || sortDistanceToSqr(camera)
                    < BlueprintGhostSortScheduler.SORT_DISTANCE_SQR) {
                return;
            }
            BlueprintGhostIndexSorter.IndexOnlyBuffer index = sorter.prepare(sortState,
                    VertexSorting.byDistance((float) camera.x, (float) camera.y, (float) camera.z));
            if (index == null) return;
            try {
                if (index.uploadInto(models)) {
                    sortedFrom = camera;
                }
            } finally {
                // 成功上传时 VertexBuffer 已释放底层数据；失效/异常时这里负责释放。
                index.close();
            }
        }

        /** 用真实模型顶点扩大批次包围盒，不能假定第三方方块模型只落在方块格内。 */
        private void includeModelBounds(BufferBuilder.RenderedBuffer data) {
            if (data == null || data.isEmpty()) return;
            VertexFormat format = data.drawState().format();
            int positionOffset = -1;
            for (var element : format.getElements()) {
                if (element.isPosition()) {
                    positionOffset = format.getOffset(element.getIndex());
                    break;
                }
            }
            if (positionOffset < 0) return;
            int stride = format.getVertexSize();
            var vertices = data.vertexBuffer();
            double minX = bounds.minX;
            double minY = bounds.minY;
            double minZ = bounds.minZ;
            double maxX = bounds.maxX;
            double maxY = bounds.maxY;
            double maxZ = bounds.maxZ;
            for (int vertex = 0; vertex < data.drawState().vertexCount(); vertex++) {
                int base = vertex * stride + positionOffset;
                if (base < 0 || base + 12 > vertices.limit()) break;
                float x = vertices.getFloat(base);
                float y = vertices.getFloat(base + 4);
                float z = vertices.getFloat(base + 8);
                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                minZ = Math.min(minZ, z);
                maxX = Math.max(maxX, x);
                maxY = Math.max(maxY, y);
                maxZ = Math.max(maxZ, z);
            }
            bounds = new AABB(minX, minY, minZ, maxX, maxY, maxZ);
        }

        @Override
        public void close() {
            if (models != null) models.close();
            if (fallback != null) fallback.close();
            if (missing != null) missing.close();
            models = fallback = missing = null;
            sortState = null;
            sortedFrom = null;
            bounds = null;
        }
    }
}
