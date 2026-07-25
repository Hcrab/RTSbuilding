package com.rtsbuilding.rtsbuilding.client.service;

/**
 * RTS 相机平滑使用的纯数学辅助。
 *
 * <p>本类只负责与帧率无关的指数追随、剩余输入消费和角度插值；
 * 它不读取按键、不修改相机实体，也不负责网络同步。把这些公式集中在这里，
 * 可以用普通单元测试验证 30/60/144 FPS 下的结果一致性，避免相机服务再次
 * 混入依赖渲染环境才能测试的零散平滑公式。
 */
final class RtsCameraSmoothingMath {
    private RtsCameraSmoothingMath() {
    }

    static float approachAxis(
            float current,
            float target,
            float deltaSeconds,
            float accelerationSeconds,
            float decelerationSeconds,
            float epsilon) {
        if (!Float.isFinite(current) || !Float.isFinite(target)) {
            return 0.0F;
        }
        if (Math.abs(target - current) <= epsilon) {
            return target;
        }

        boolean reversing = current != 0.0F && target != 0.0F
                && Math.signum(current) != Math.signum(target);
        boolean slowingDown = Math.abs(target) < Math.abs(current);
        float responseSeconds = reversing || slowingDown
                ? decelerationSeconds
                : accelerationSeconds;
        float alpha = exponentialAlpha(deltaSeconds, responseSeconds);
        float next = current + ((target - current) * alpha);
        return Math.abs(target - next) <= epsilon ? target : next;
    }

    static DecayStep consumeRemaining(
            float remaining,
            float deltaSeconds,
            float responseSeconds,
            float epsilon) {
        if (!Float.isFinite(remaining) || Math.abs(remaining) <= epsilon) {
            return new DecayStep(0.0F, 0.0F);
        }

        float consumed = remaining * exponentialAlpha(deltaSeconds, responseSeconds);
        float next = remaining - consumed;
        if (Math.abs(next) <= epsilon) {
            consumed += next;
            next = 0.0F;
        }
        return new DecayStep(consumed, next);
    }

    static float exponentialAlpha(float deltaSeconds, float responseSeconds) {
        if (!(deltaSeconds > 0.0F) || !Float.isFinite(deltaSeconds)) {
            return 0.0F;
        }
        if (!(responseSeconds > 0.0F) || !Float.isFinite(responseSeconds)) {
            return 1.0F;
        }
        return (float) (1.0D - Math.exp(-deltaSeconds / responseSeconds));
    }

    static float interpolateAngleDegrees(float current, float target, float alpha) {
        float delta = wrapDegrees(target - current);
        return current + (delta * clampUnit(alpha));
    }

    private static float wrapDegrees(float degrees) {
        float wrapped = degrees % 360.0F;
        if (wrapped >= 180.0F) {
            wrapped -= 360.0F;
        }
        if (wrapped < -180.0F) {
            wrapped += 360.0F;
        }
        return wrapped;
    }

    private static float clampUnit(float value) {
        if (!Float.isFinite(value)) {
            return 0.0F;
        }
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    record DecayStep(float consumed, float remaining) {
    }
}
