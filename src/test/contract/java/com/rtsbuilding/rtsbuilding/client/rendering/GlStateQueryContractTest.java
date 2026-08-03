package com.rtsbuilding.rtsbuilding.client.rendering;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 锁定 LWJGL 2 状态查询的缓冲区约束，避免只在真实客户端渲染时才发现崩溃。 */
class GlStateQueryContractTest {
    private static final Path CLIENT_SOURCES = Path.of(
            "src", "main", "java", "com", "rtsbuilding", "rtsbuilding", "client");
    private static final Path QUERY_HELPER = CLIENT_SOURCES.resolve(
            Path.of("rendering", "util", "RtsGlStateQueries.java"));

    @Test
    void currentColorQueriesAreCentralizedInLwjglSafeHelper() throws IOException {
        List<Path> directQueries;
        try (Stream<Path> files = Files.walk(CLIENT_SOURCES)) {
            directQueries = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> contains(path,
                            "GL11.glGetFloat(GL11.GL_CURRENT_COLOR,"))
                    .map(Path::normalize)
                    .toList();
        }

        assertEquals(List.of(QUERY_HELPER.normalize()), directQueries,
                "当前颜色必须统一通过安全查询工具读取，不能重新引入 4-float 缓冲区");
    }

    @Test
    void helperReservesTheSixteenFloatsRequiredByLwjgl2Binding() throws IOException {
        String helper = Files.readString(QUERY_HELPER);

        assertTrue(helper.contains("LWJGL_GL_GET_FLOAT_CAPACITY = 16"),
                "LWJGL 2 glGetFloat(FloatBuffer) 会无条件校验至少 16 个 float");
        assertTrue(helper.contains("createFloatBuffer(LWJGL_GL_GET_FLOAT_CAPACITY)"));
        assertFalse(helper.contains("createFloatBuffer(4)"));
    }

    private static boolean contains(Path path, String token) {
        try {
            return Files.readString(path).contains(token);
        } catch (IOException exception) {
            throw new IllegalStateException("无法读取客户端源码: " + path, exception);
        }
    }
}
