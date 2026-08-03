package com.rtsbuilding.rtsbuilding.gametest;

import net.minecraft.gametest.framework.GameTestHelper;

/**
 * 为 1.19.2 GameTest 补齐主线使用的布尔断言便利入口。
 *
 * <p>这里只翻译测试框架 API，不吞掉失败，也不改变测试调度或游戏状态；条件不成立时
 * 始终交给 {@link GameTestHelper#fail(String)} 产生原生 GameTest 失败。</p>
 */
final class RtsGameTestAssertions {
    private RtsGameTestAssertions() {
    }

    static void assertTrue(GameTestHelper helper, boolean condition, String message) {
        if (!condition) {
            helper.fail(message);
        }
    }
}
