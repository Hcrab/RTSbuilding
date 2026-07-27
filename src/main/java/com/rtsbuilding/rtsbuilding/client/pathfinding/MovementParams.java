package com.rtsbuilding.rtsbuilding.client.pathfinding;

/**
 * 单个客户端 tick 使用的移动参数。
 *
 * <p>这是 Java 8 值类，不负责判断玩家当前属于哪一种移动模式；模式识别由
 * {@link MovementModeHandler} 和注册表负责。</p>
 */
public final class MovementParams {
    private final double speed;
    private final boolean threeDimensional;
    private final boolean allowSprint;
    private final boolean applyApproachSlowdown;
    private final boolean applyEntityInsideSlow;
    private final StuckBehavior stuckBehavior;
    private final boolean useInputSystem;
    private final boolean arrivalCheckHorizontalOnly;

    public MovementParams(double speed, boolean threeDimensional, boolean allowSprint,
                          boolean applyApproachSlowdown, boolean applyEntityInsideSlow,
                          StuckBehavior stuckBehavior) {
        this(speed, threeDimensional, allowSprint, applyApproachSlowdown,
                applyEntityInsideSlow, stuckBehavior, false, false);
    }

    public MovementParams(double speed, boolean threeDimensional, boolean allowSprint,
                          boolean applyApproachSlowdown, boolean applyEntityInsideSlow,
                          StuckBehavior stuckBehavior, boolean useInputSystem,
                          boolean arrivalCheckHorizontalOnly) {
        this.speed = speed;
        this.threeDimensional = threeDimensional;
        this.allowSprint = allowSprint;
        this.applyApproachSlowdown = applyApproachSlowdown;
        this.applyEntityInsideSlow = applyEntityInsideSlow;
        this.stuckBehavior = stuckBehavior;
        this.useInputSystem = useInputSystem;
        this.arrivalCheckHorizontalOnly = arrivalCheckHorizontalOnly;
    }

    public double speed() { return speed; }
    public boolean threeDimensional() { return threeDimensional; }
    public boolean allowSprint() { return allowSprint; }
    public boolean applyApproachSlowdown() { return applyApproachSlowdown; }
    public boolean applyEntityInsideSlow() { return applyEntityInsideSlow; }
    public StuckBehavior stuckBehavior() { return stuckBehavior; }
    public boolean useInputSystem() { return useInputSystem; }
    public boolean arrivalCheckHorizontalOnly() { return arrivalCheckHorizontalOnly; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof MovementParams)) return false;
        MovementParams that = (MovementParams) other;
        return Double.compare(speed, that.speed) == 0
                && threeDimensional == that.threeDimensional
                && allowSprint == that.allowSprint
                && applyApproachSlowdown == that.applyApproachSlowdown
                && applyEntityInsideSlow == that.applyEntityInsideSlow
                && stuckBehavior == that.stuckBehavior
                && useInputSystem == that.useInputSystem
                && arrivalCheckHorizontalOnly == that.arrivalCheckHorizontalOnly;
    }

    @Override
    public int hashCode() {
        long speedBits = Double.doubleToLongBits(speed);
        int result = (int) (speedBits ^ (speedBits >>> 32));
        result = 31 * result + (threeDimensional ? 1 : 0);
        result = 31 * result + (allowSprint ? 1 : 0);
        result = 31 * result + (applyApproachSlowdown ? 1 : 0);
        result = 31 * result + (applyEntityInsideSlow ? 1 : 0);
        result = 31 * result + (stuckBehavior == null ? 0 : stuckBehavior.hashCode());
        result = 31 * result + (useInputSystem ? 1 : 0);
        result = 31 * result + (arrivalCheckHorizontalOnly ? 1 : 0);
        return result;
    }

    @Override
    public String toString() {
        return "MovementParams[speed=" + speed
                + ", threeDimensional=" + threeDimensional
                + ", allowSprint=" + allowSprint
                + ", applyApproachSlowdown=" + applyApproachSlowdown
                + ", applyEntityInsideSlow=" + applyEntityInsideSlow
                + ", stuckBehavior=" + stuckBehavior
                + ", useInputSystem=" + useInputSystem
                + ", arrivalCheckHorizontalOnly=" + arrivalCheckHorizontalOnly + ']';
    }

    public enum StuckBehavior {
        /** 地面受阻时施加原版基础跳跃速度。 */
        JUMP,
        /** 液体中受阻时停止水平顶墙并上浮。 */
        FLOAT_UP,
        /** 飞行受阻时垂直抬升。 */
        FLY_UP,
        NONE
    }
}
