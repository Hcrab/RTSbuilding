package com.rtsbuilding.rtsbuilding.platform.math;

/** 把共享代码使用的现代数学命名收束到一个不依赖 Minecraft 版本的薄层。 */
public final class MathHelper {
    private MathHelper() {}

    public static int floor(double value) {
        int integer = (int) value;
        return value < integer ? integer - 1 : integer;
    }

    public static int floor(float value) {
        int integer = (int) value;
        return value < integer ? integer - 1 : integer;
    }

    public static int ceil(double value) {
        int integer = (int) value;
        return value > integer ? integer + 1 : integer;
    }

    public static int ceil(float value) {
        int integer = (int) value;
        return value > integer ? integer + 1 : integer;
    }

    public static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    public static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public static float wrapDegrees(float degrees) {
        float wrapped = degrees % 360.0F;
        if (wrapped >= 180.0F) wrapped -= 360.0F;
        if (wrapped < -180.0F) wrapped += 360.0F;
        return wrapped;
    }

    public static double wrapDegrees(double degrees) {
        double wrapped = degrees % 360.0D;
        if (wrapped >= 180.0D) wrapped -= 360.0D;
        if (wrapped < -180.0D) wrapped += 360.0D;
        return wrapped;
    }

    public static long getPositionRandom(BlockPos pos) {
        long value = (long) (pos.getX() * 3129871) ^ (long) pos.getZ() * 116129781L ^ pos.getY();
        value = value * value * 42317861L + value * 11L;
        return value;
    }
}
