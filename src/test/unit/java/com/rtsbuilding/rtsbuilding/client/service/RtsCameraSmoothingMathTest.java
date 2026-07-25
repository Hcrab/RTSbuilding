package com.rtsbuilding.rtsbuilding.client.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RtsCameraSmoothingMathTest {
    @Test
    void wasdStartsGraduallyAndKeepsOnlyAShortReleaseTail() {
        float speed = 0.0F;
        speed = RtsCameraSmoothingMath.approachAxis(
                speed, 1.0F, 0.05F,
                0.055F, 0.050F, 0.002F);

        assertTrue(speed > 0.0F && speed < 1.0F,
                "第一次 tick 应开始移动但不能直接跳到满速。");

        for (int i = 0; i < 8; i++) {
            speed = RtsCameraSmoothingMath.approachAxis(
                    speed, 1.0F, 0.05F,
                    0.055F, 0.050F, 0.002F);
        }
        float releaseStart = speed;
        speed = RtsCameraSmoothingMath.approachAxis(
                speed, 0.0F, 0.05F,
                0.055F, 0.050F, 0.002F);

        assertTrue(speed > 0.0F && speed < releaseStart,
                "松开后的第一个 tick 应保留短促减速尾巴。");

        for (int i = 0; i < 7; i++) {
            speed = RtsCameraSmoothingMath.approachAxis(
                    speed, 0.0F, 0.05F,
                    0.055F, 0.050F, 0.002F);
        }
        assertEquals(0.0F, speed, 0.002F,
                "减速尾巴必须快速归零，不能让相机长时间漂移。");
    }

    @Test
    void scrollConsumesTheSameTotalAcrossRenderFrameRates() {
        assertEquals(1.0F, consumeScrollAtFps(30), 0.001F);
        assertEquals(1.0F, consumeScrollAtFps(60), 0.001F);
        assertEquals(1.0F, consumeScrollAtFps(144), 0.001F);
    }

    @Test
    void angleInterpolationUsesTheShortPathAcrossTheWrapBoundary() {
        float next = RtsCameraSmoothingMath.interpolateAngleDegrees(179.0F, -179.0F, 0.5F);
        assertEquals(180.0F, next, 0.001F,
                "跨越 ±180° 时应走 2° 的短路径，不能反向旋转 358°。");
    }

    private static float consumeScrollAtFps(int fps) {
        float remaining = 1.0F;
        float consumed = 0.0F;
        float deltaSeconds = 1.0F / fps;
        int frames = Math.round(0.5F * fps);
        for (int i = 0; i < frames; i++) {
            RtsCameraSmoothingMath.DecayStep step =
                    RtsCameraSmoothingMath.consumeRemaining(
                            remaining, deltaSeconds, 0.045F, 0.0005F);
            consumed += step.consumed();
            remaining = step.remaining();
        }
        assertEquals(0.0F, remaining, 0.0005F,
                "半秒后滚轮尾量应已经完全收敛。");
        return consumed;
    }
}
