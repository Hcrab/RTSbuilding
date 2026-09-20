package com.rtsbuilding.rtsbuilding.client.rendering.blueprint;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.rtsbuilding.rtsbuilding.client.rendering.RtsPrivateBufferLifecycle;
import net.minecraft.client.renderer.RenderType;

/**
 * 蓝图透明模型专用的 index-only 排序 owner。
 *
 * <p>Forge 1.20.1 的 {@link BufferBuilder.SortState} 可以在不重新写入顶点的情况下
 * 生成新的索引批次。这个 owner 只持有一个 RTS 独占 scratch BufferBuilder；调用方
 * 必须在下一次 {@link #prepare(BufferBuilder.SortState, VertexSorting)} 前关闭返回的
 * {@link IndexOnlyBuffer}，从而让底层原生内存保持有界，并且不会碰共享 Tesselator。</p>
 */
final class BlueprintGhostIndexSorter implements AutoCloseable {
    private final VertexFormat.Mode mode;
    private final VertexFormat format;
    private final BufferBuilder scratch;
    private IndexOnlyBuffer outstanding;
    private boolean closed;

    BlueprintGhostIndexSorter(RenderType type) {
        this(type.bufferSize(), type.mode(), type.format());
    }

    BlueprintGhostIndexSorter(int bufferSize, VertexFormat.Mode mode, VertexFormat format) {
        this.mode = mode;
        this.format = format;
        this.scratch = new BufferBuilder(bufferSize);
    }

    /**
     * 以给定相机排序生成一个 index-only RenderedBuffer。
     *
     * <p>返回对象拥有一次性释放责任；生产上传成功时 VertexBuffer.upload 会接管并
     * 释放底层 RenderedBuffer，上传未发生或目标失效时则由 close() 释放。</p>
     */
    IndexOnlyBuffer prepare(BufferBuilder.SortState sortState, VertexSorting sorting) {
        if (closed || sortState == null || sorting == null) {
            return null;
        }
        if (outstanding != null) {
            outstanding.close();
            outstanding = null;
        }
        BufferBuilder.RenderedBuffer rendered = null;
        try {
            RtsPrivateBufferLifecycle.begin(scratch, mode, format);
            scratch.restoreSortState(sortState);
            scratch.setQuadSorting(sorting);
            rendered = scratch.end();
            outstanding = new IndexOnlyBuffer(rendered);
            return outstanding;
        } catch (RuntimeException | Error failure) {
            if (rendered != null) {
                rendered.release();
            }
            discardBuildingScratch();
            throw failure;
        }
    }

    private void discardBuildingScratch() {
        if (scratch.building()) {
            BufferBuilder.RenderedBuffer discarded = scratch.endOrDiscardIfEmpty();
            if (discarded != null) {
                discarded.release();
            }
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        if (outstanding != null) {
            outstanding.close();
            outstanding = null;
        }
        discardBuildingScratch();
        scratch.clear();
    }

    /** 一次 index-only 上传的所有权边界，避免有效/失效/异常路径重复 release。 */
    final class IndexOnlyBuffer implements AutoCloseable {
        private BufferBuilder.RenderedBuffer rendered;

        private IndexOnlyBuffer(BufferBuilder.RenderedBuffer rendered) {
            this.rendered = rendered;
        }

        BufferBuilder.RenderedBuffer rendered() {
            return rendered;
        }

        /**
         * 绑定已有 VAO 后只上传索引；目标无效或绑定失败时保留自身数据交给 close()。
         */
        boolean uploadInto(VertexBuffer target) {
            BufferBuilder.RenderedBuffer data = rendered;
            if (data == null || target == null || target.isInvalid()) {
                return false;
            }
            try {
                target.bind();
                if (target.isInvalid()) {
                    return false;
                }
                // VertexBuffer.upload 的正常/异常路径都会释放 data；此后本对象不再拥有它。
                rendered = null;
                target.upload(data);
                return true;
            } finally {
                VertexBuffer.unbind();
                // 绑定或失效检查失败时，所有权仍在本对象，由 close() 释放。
            }
        }

        @Override
        public void close() {
            BufferBuilder.RenderedBuffer data = rendered;
            rendered = null;
            if (data != null) {
                data.release();
            }
        }
    }
}
