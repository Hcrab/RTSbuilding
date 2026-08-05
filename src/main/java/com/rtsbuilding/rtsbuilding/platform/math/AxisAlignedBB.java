package com.rtsbuilding.rtsbuilding.platform.math;

/**
 * 为 1.7.10 原生 AABB 补齐共享业务层需要的不可变现代语义。
 *
 * <p>父类可直接传给旧版世界/实体查询；本类重写 {@code offset}/{@code expand}，避免
 * 1.7.10 的原地修改和 1.12.2 的返回新对象语义混在一起。</p>
 */
public class AxisAlignedBB extends net.minecraft.util.AxisAlignedBB {
    public AxisAlignedBB(double minX, double minY, double minZ,
                         double maxX, double maxY, double maxZ) {
        super(minX, minY, minZ, maxX, maxY, maxZ);
    }

    public AxisAlignedBB(BlockPos pos) {
        this(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1.0D, pos.getY() + 1.0D, pos.getZ() + 1.0D);
    }

    public static AxisAlignedBB fromNative(net.minecraft.util.AxisAlignedBB box) {
        return box == null ? null : new AxisAlignedBB(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
    }

    @Override
    public AxisAlignedBB expand(double x, double y, double z) {
        double newMinX = x < 0.0D ? this.minX + x : this.minX;
        double newMaxX = x > 0.0D ? this.maxX + x : this.maxX;
        double newMinY = y < 0.0D ? this.minY + y : this.minY;
        double newMaxY = y > 0.0D ? this.maxY + y : this.maxY;
        double newMinZ = z < 0.0D ? this.minZ + z : this.minZ;
        double newMaxZ = z > 0.0D ? this.maxZ + z : this.maxZ;
        return new AxisAlignedBB(newMinX, newMinY, newMinZ, newMaxX, newMaxY, newMaxZ);
    }

    public AxisAlignedBB grow(double amount) {
        return this.grow(amount, amount, amount);
    }

    public AxisAlignedBB grow(double x, double y, double z) {
        return new AxisAlignedBB(
                this.minX - x, this.minY - y, this.minZ - z,
                this.maxX + x, this.maxY + y, this.maxZ + z);
    }

    @Override
    public AxisAlignedBB offset(double x, double y, double z) {
        return new AxisAlignedBB(
                this.minX + x, this.minY + y, this.minZ + z,
                this.maxX + x, this.maxY + y, this.maxZ + z);
    }

    public AxisAlignedBB offset(BlockPos pos) {
        return this.offset(pos.getX(), pos.getY(), pos.getZ());
    }

    public boolean intersects(AxisAlignedBB other) {
        return other.maxX > this.minX && other.minX < this.maxX
                && other.maxY > this.minY && other.minY < this.maxY
                && other.maxZ > this.minZ && other.minZ < this.maxZ;
    }

    public boolean contains(Vec3d point) {
        return point.x > this.minX && point.x < this.maxX
                && point.y > this.minY && point.y < this.maxY
                && point.z > this.minZ && point.z < this.maxZ;
    }

    public Vec3d getCenter() {
        return new Vec3d(
                (this.minX + this.maxX) * 0.5D,
                (this.minY + this.maxY) * 0.5D,
                (this.minZ + this.maxZ) * 0.5D);
    }

    public RayTraceResult calculateIntercept(Vec3d start, Vec3d end) {
        return RayTraceResult.fromNative(super.calculateIntercept(start.toNative(), end.toNative()));
    }

    public AxisAlignedBB union(AxisAlignedBB other) {
        return new AxisAlignedBB(
                Math.min(this.minX, other.minX),
                Math.min(this.minY, other.minY),
                Math.min(this.minZ, other.minZ),
                Math.max(this.maxX, other.maxX),
                Math.max(this.maxY, other.maxY),
                Math.max(this.maxZ, other.maxZ));
    }

    public AxisAlignedBB intersect(AxisAlignedBB other) {
        return new AxisAlignedBB(
                Math.max(this.minX, other.minX),
                Math.max(this.minY, other.minY),
                Math.max(this.minZ, other.minZ),
                Math.min(this.maxX, other.maxX),
                Math.min(this.maxY, other.maxY),
                Math.min(this.maxZ, other.maxZ));
    }

    @Override
    public AxisAlignedBB copy() {
        return new AxisAlignedBB(this.minX, this.minY, this.minZ, this.maxX, this.maxY, this.maxZ);
    }
}
