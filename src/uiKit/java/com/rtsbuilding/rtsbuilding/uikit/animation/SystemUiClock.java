package com.rtsbuilding.rtsbuilding.uikit.animation;

/** 生产 UI 使用的单调时钟；测试与离屏截图继续注入 {@link FixedUiClock}。 */
public final class SystemUiClock implements UiClock {
    public static final SystemUiClock INSTANCE = new SystemUiClock();

    @Override
    public long nowMillis() {
        return System.nanoTime() / 1_000_000L;
    }

    private SystemUiClock() {
    }
}
