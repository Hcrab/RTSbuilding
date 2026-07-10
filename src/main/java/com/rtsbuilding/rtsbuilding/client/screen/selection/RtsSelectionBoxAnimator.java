package com.rtsbuilding.rtsbuilding.client.screen.selection;

import com.rtsbuilding.rtsbuilding.client.screen.culling.RtsCullingBox;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

import java.util.function.LongSupplier;

/**
 * 世界空间盒子编辑器的视觉补间状态。
 *
 * <p>它只负责把整数方块盒子的旧 AABB 平滑过渡到新 AABB，不参与真实命中、剔除、蓝图保存或服务端同步。
 * 范围剔除和蓝图框选都通过这一层获得相同的拖拽手感，同时各自仍然保留自己的业务状态和颜色渲染。</p>
 */
public final class RtsSelectionBoxAnimator {
    private static final long DEFAULT_DURATION_MS = 90L;

    private final long durationMs;
    private final LongSupplier clock;
    private int animatedBoxId = -1;
    private AABB animatedStartAabb;
    private AABB animatedEndAabb;
    private long animatedStartMillis;

    public RtsSelectionBoxAnimator() {
        this(DEFAULT_DURATION_MS, System::currentTimeMillis);
    }

    RtsSelectionBoxAnimator(long durationMs) {
        this(durationMs, System::currentTimeMillis);
    }

    RtsSelectionBoxAnimator(long durationMs, LongSupplier clock) {
        this.durationMs = Math.max(1L, durationMs);
        this.clock = clock == null ? System::currentTimeMillis : clock;
    }

    public AABB renderAabb(RtsCullingBox box) {
        if (box == null) {
            return null;
        }
        long now = this.clock.getAsLong();
        AABB target = box.asAabb();
        if (box.id() != animatedBoxId || animatedStartAabb == null || animatedEndAabb == null) {
            animatedBoxId = box.id();
            animatedStartAabb = target;
            animatedEndAabb = target;
            animatedStartMillis = now;
            return target;
        }
        if (!animatedEndAabb.equals(target)) {
            animatedStartAabb = currentAnimatedAabb(now);
            animatedEndAabb = target;
            animatedStartMillis = now;
        }
        double raw = Mth.clamp((double) (now - animatedStartMillis) / (double) durationMs, 0.0D, 1.0D);
        if (raw >= 1.0D) {
            animatedStartAabb = target;
            animatedEndAabb = target;
            animatedStartMillis = now;
            return target;
        }
        return lerpAabb(animatedStartAabb, animatedEndAabb, easeOutCubic(raw));
    }

    public void animate(RtsCullingBox from, RtsCullingBox to) {
        if (from == null || to == null || from.equals(to)) {
            return;
        }
        long now = this.clock.getAsLong();
        AABB visualStart = from.id() == animatedBoxId && animatedStartAabb != null && animatedEndAabb != null
                ? currentAnimatedAabb(now)
                : from.asAabb();
        animatedBoxId = to.id();
        animatedStartAabb = visualStart;
        animatedEndAabb = to.asAabb();
        animatedStartMillis = now;
    }

    public void clearIfBox(int id) {
        if (animatedBoxId == id) {
            clear();
        }
    }

    public void clear() {
        animatedBoxId = -1;
        animatedStartAabb = null;
        animatedEndAabb = null;
        animatedStartMillis = 0L;
    }

    private AABB currentAnimatedAabb(long now) {
        double raw = Mth.clamp((double) (now - animatedStartMillis) / (double) durationMs, 0.0D, 1.0D);
        return lerpAabb(animatedStartAabb, animatedEndAabb, easeOutCubic(raw));
    }

    private static double easeOutCubic(double amount) {
        return 1.0D - Math.pow(1.0D - amount, 3.0D);
    }

    private static AABB lerpAabb(AABB from, AABB to, double amount) {
        return new AABB(
                Mth.lerp(amount, from.minX, to.minX),
                Mth.lerp(amount, from.minY, to.minY),
                Mth.lerp(amount, from.minZ, to.minZ),
                Mth.lerp(amount, from.maxX, to.maxX),
                Mth.lerp(amount, from.maxY, to.maxY),
                Mth.lerp(amount, from.maxZ, to.maxZ));
    }
}
