package com.rtsbuilding.rtsbuilding.client.rendering.util;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.nio.FloatBuffer;

/**
 * 集中处理 LWJGL 2 的 OpenGL 状态查询约束。
 *
 * <p>LWJGL 2 的 {@link GL11#glGetFloat(int, FloatBuffer)} 绑定无论查询的实际返回长度，
 * 都会先校验缓冲区至少能容纳 16 个 float。调用方只需要当前颜色的四个分量，但仍不能
 * 传入长度为 4 的缓冲区，否则会在客户端渲染线程直接崩溃。</p>
 */
public final class RtsGlStateQueries {
    private static final int LWJGL_GL_GET_FLOAT_CAPACITY = 16;

    private RtsGlStateQueries() {
    }

    public static float[] currentColor() {
        FloatBuffer values = BufferUtils.createFloatBuffer(LWJGL_GL_GET_FLOAT_CAPACITY);
        GL11.glGetFloat(GL11.GL_CURRENT_COLOR, values);
        return new float[] {values.get(0), values.get(1), values.get(2), values.get(3)};
    }
}
