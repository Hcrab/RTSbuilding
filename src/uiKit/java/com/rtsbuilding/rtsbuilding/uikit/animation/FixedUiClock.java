package com.rtsbuilding.rtsbuilding.uikit.animation;

/** 测试和无头场景使用的可控时钟。 */
public final class FixedUiClock implements UiClock {
    private long nowMillis;

    public FixedUiClock(long nowMillis) {
        this.nowMillis = nowMillis;
    }

    @Override
    public long nowMillis() {
        return nowMillis;
    }

    public void setMillis(long newTimeMillis) {
        nowMillis = newTimeMillis;
    }

    public void advanceMillis(long deltaMillis) {
        if (deltaMillis < 0L) {
            throw new IllegalArgumentException("clock cannot move backwards");
        }
        nowMillis += deltaMillis;
    }
}
