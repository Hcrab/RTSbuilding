package com.rtsbuilding.rtsbuilding.gametest;

import net.minecraft.gametest.framework.GameTestHelper;

import java.util.Objects;

/**
 * 补齐 1.20.1 GameTestHelper 缺少的值相等断言，保持跨版本回归的失败语义。
 * 这里只适配断言 API，不模拟生产行为，也不接管测试生命周期。
 */
final class RtsGameTestAssertions {
    private RtsGameTestAssertions() {
    }

    static <T> void assertValueEqual(GameTestHelper helper, T expected, T actual, String message) {
        helper.assertTrue(Objects.equals(expected, actual),
                message + " (expected=" + expected + ", actual=" + actual + ")");
    }
}
