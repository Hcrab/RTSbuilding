package com.rtsbuilding.rtsbuilding.platform.math;

import net.minecraft.util.Vec3;

/**
 * 业务层使用的不可变双精度向量。
 *
 * <p>1.7.10 原生 {@link Vec3} 由世界对象的向量池管理且字段命名不同；本类让镜头、
 * 线框和射线算法保持纯 Java，再在世界调用边界显式转换。</p>
 */
public final class Vec3d {
    public static final Vec3d ZERO = new Vec3d(0.0D, 0.0D, 0.0D);

    public final double x;
    public final double y;
    public final double z;

    public Vec3d(double x, double y, double z) {
        this.x = x == -0.0D ? 0.0D : x;
        this.y = y == -0.0D ? 0.0D : y;
        this.z = z == -0.0D ? 0.0D : z;
    }

    public Vec3d(Vec3i vector) {
        this(vector.getX(), vector.getY(), vector.getZ());
    }

    public static Vec3d fromNative(Vec3 vector) {
        return vector == null ? null : new Vec3d(vector.xCoord, vector.yCoord, vector.zCoord);
    }

    public Vec3 toNative() {
        return Vec3.createVectorHelper(this.x, this.y, this.z);
    }

    public Vec3d add(Vec3d other) {
        return this.add(other.x, other.y, other.z);
    }

    public Vec3d add(double x, double y, double z) {
        return new Vec3d(this.x + x, this.y + y, this.z + z);
    }

    public Vec3d subtract(Vec3d other) {
        return new Vec3d(this.x - other.x, this.y - other.y, this.z - other.z);
    }

    public Vec3d scale(double factor) {
        return new Vec3d(this.x * factor, this.y * factor, this.z * factor);
    }

    public double dotProduct(Vec3d other) {
        return this.x * other.x + this.y * other.y + this.z * other.z;
    }

    public Vec3d crossProduct(Vec3d other) {
        return new Vec3d(
                this.y * other.z - this.z * other.y,
                this.z * other.x - this.x * other.z,
                this.x * other.y - this.y * other.x);
    }

    public double lengthSquared() {
        return this.x * this.x + this.y * this.y + this.z * this.z;
    }

    public double length() {
        return Math.sqrt(this.lengthSquared());
    }

    public Vec3d normalize() {
        double length = this.length();
        return length < 1.0E-8D ? ZERO : this.scale(1.0D / length);
    }

    public double squareDistanceTo(Vec3d other) {
        double dx = other.x - this.x;
        double dy = other.y - this.y;
        double dz = other.z - this.z;
        return dx * dx + dy * dy + dz * dz;
    }

    public double distanceTo(Vec3d other) {
        return Math.sqrt(this.squareDistanceTo(other));
    }

    @Override
    public boolean equals(Object value) {
        if (this == value) return true;
        if (!(value instanceof Vec3d)) return false;
        Vec3d other = (Vec3d) value;
        return Double.compare(this.x, other.x) == 0
                && Double.compare(this.y, other.y) == 0
                && Double.compare(this.z, other.z) == 0;
    }

    @Override
    public int hashCode() {
        long bits = Double.doubleToLongBits(this.x);
        int result = (int) (bits ^ bits >>> 32);
        bits = Double.doubleToLongBits(this.y);
        result = 31 * result + (int) (bits ^ bits >>> 32);
        bits = Double.doubleToLongBits(this.z);
        return 31 * result + (int) (bits ^ bits >>> 32);
    }

    @Override
    public String toString() {
        return "Vec3d{" + this.x + "," + this.y + "," + this.z + "}";
    }
}
