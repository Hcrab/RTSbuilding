package com.rtsbuilding.rtsbuilding.platform.fluid;

/**
 * Fabric Transfer API 使用的离散流体单位。
 *
 * <p>一桶固定为 81,000 droplets。旧代码中的部分方法名仍带有 {@code Mb} 后缀，迁移期
 * 只保留名字以缩小调用面变化，实际数值在 Fabric 线统一按 droplets 解释。
 */
public final class RtsFluidUnits {
    public static final int BUCKET = 81_000;

    private RtsFluidUnits() {
    }
}
