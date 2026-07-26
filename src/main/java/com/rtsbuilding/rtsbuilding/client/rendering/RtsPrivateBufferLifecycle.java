package com.rtsbuilding.rtsbuilding.client.rendering;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexFormat;

/**
 * 管理 RTS 自有 {@link BufferBuilder} 在 Forge 1.20.1 下的跨帧复用。
 *
 * <p>这个助手只处理由 RTSBuilding 独占的缓冲，不得用于 Minecraft 或其他模组共享的
 * Tesselator 缓冲。旧版 {@code BufferBuilder.discard()} 只丢弃已经生成的结果，
 * 不会结束仍处于 building 状态的当前批次；因此重入前必须显式结束或丢弃当前批次。
 */
public final class RtsPrivateBufferLifecycle {
    private RtsPrivateBufferLifecycle() {}

    /**
     * 结束可能由上一帧或异常路径遗留的批次，然后按指定格式开始一个新批次。
     * 非空遗留结果不会被绘制，因为它已经错过所属帧；释放结果可以避免占住底层缓冲。
     */
    public static void begin(BufferBuilder buffer, VertexFormat.Mode mode, VertexFormat format) {
        if (buffer.building()) {
            BufferBuilder.RenderedBuffer stale = buffer.endOrDiscardIfEmpty();
            if (stale != null) {
                stale.release();
            }
        }
        buffer.begin(mode, format);
    }
}
