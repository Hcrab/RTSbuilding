package com.rtsbuilding.rtsbuilding.platform.math;

/**
 * 不依赖 Minecraft 版本的三维整数向量。
 *
 * <p>它承接 1.8 以后才出现的 Minecraft {@code Vec3i} 数据职责，但不冒充
 * {@code net.minecraft} 类。世界读写仍由版本适配器完成。</p>
 */
public class Vec3i implements Comparable<Vec3i> {
    public static final Vec3i NULL_VECTOR = new Vec3i(0, 0, 0);

    protected int x;
    protected int y;
    protected int z;

    public Vec3i(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public int getX() {
        return this.x;
    }

    public int getY() {
        return this.y;
    }

    public int getZ() {
        return this.z;
    }

    public Vec3i crossProduct(Vec3i other) {
        return new Vec3i(
                this.y * other.z - this.z * other.y,
                this.z * other.x - this.x * other.z,
                this.x * other.y - this.y * other.x);
    }

    public double distanceSq(double x, double y, double z) {
        double dx = this.x - x;
        double dy = this.y - y;
        double dz = this.z - z;
        return dx * dx + dy * dy + dz * dz;
    }

    public double distanceSq(Vec3i other) {
        return this.distanceSq(other.x, other.y, other.z);
    }

    @Override
    public int compareTo(Vec3i other) {
        if (this.y != other.y) return Integer.compare(this.y, other.y);
        if (this.z != other.z) return Integer.compare(this.z, other.z);
        return Integer.compare(this.x, other.x);
    }

    @Override
    public boolean equals(Object value) {
        if (this == value) return true;
        if (!(value instanceof Vec3i)) return false;
        Vec3i other = (Vec3i) value;
        return this.x == other.x && this.y == other.y && this.z == other.z;
    }

    @Override
    public int hashCode() {
        return (this.y + this.z * 31) * 31 + this.x;
    }

    @Override
    public String toString() {
        return "Vec3i{" + this.x + "," + this.y + "," + this.z + "}";
    }
}
