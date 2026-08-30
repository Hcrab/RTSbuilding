package com.rtsbuilding.rtsbuilding.test;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 保证测试不会重新堆回无法区分职责的旧目录。 */
class TestSourceLayoutContractTest {

    @Test
    void testsAreSeparatedIntoUnitContractAndPerformanceRoots() throws Exception {
        Path testRoot = Path.of("src/test");
        assertTrue(hasJavaFiles(testRoot.resolve("unit/java")), "unit 测试目录不能为空");
        assertTrue(hasJavaFiles(testRoot.resolve("contract/java")), "contract 测试目录不能为空");
        assertTrue(hasJavaFiles(testRoot.resolve("performance/java")), "performance 测试目录不能为空");
        assertEquals(0L, countJavaFiles(testRoot.resolve("java")), "禁止继续向旧 src/test/java 添加测试");
    }

    private static boolean hasJavaFiles(Path root) throws Exception {
        return countJavaFiles(root) > 0L;
    }

    private static long countJavaFiles(Path root) throws Exception {
        if (!Files.isDirectory(root)) return 0L;
        try (var files = Files.walk(root)) {
            return files.filter(path -> path.toString().endsWith(".java")).count();
        }
    }
}
