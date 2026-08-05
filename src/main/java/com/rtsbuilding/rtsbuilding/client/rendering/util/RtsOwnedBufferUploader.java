package com.rtsbuilding.rtsbuilding.client.rendering.util;

import com.rtsbuilding.rtsbuilding.platform.render.BufferBuilder;
import com.rtsbuilding.rtsbuilding.platform.render.WorldVertexBufferUploader;

/**
 * 提交 RTS 渲染器自己拥有的 {@link BufferBuilder}。
 *
 * <p>1.12.2 的 {@link WorldVertexBufferUploader#draw(BufferBuilder)} 不会调用
 * {@code finishDrawing()}；直接上传会让缓冲区永久停留在 building 状态，并在下一帧
 * 再次 {@code begin()} 时崩溃。本工具固定执行 finish、上传和最终 reset，且绝不接触
 * Minecraft/Tessellator 的共享缓冲。</p>
 */
public final class RtsOwnedBufferUploader {
    private static final WorldVertexBufferUploader UPLOADER = new WorldVertexBufferUploader();

    private RtsOwnedBufferUploader() {
    }

    public static void draw(BufferBuilder buffer) {
        buffer.finishDrawing();
        try {
            UPLOADER.draw(buffer);
        } finally {
            // 原版上传器成功时也会 reset；这里同时覆盖 GL 上传中途抛错的清理路径。
            buffer.reset();
        }
    }
}
