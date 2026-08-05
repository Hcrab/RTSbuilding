package com.rtsbuilding.rtsbuilding.platform.math;

import net.minecraft.entity.Entity;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.World;

/**
 * 跨版本射线命中快照。
 *
 * <p>1.7.10 的 {@link MovingObjectPosition} 用三个整数与面序号表示方块命中，并使用
 * 向量池对象；这里在进入业务层时立即复制成稳定值，离开业务层时再显式转回原生对象。</p>
 */
public final class RayTraceResult {
    public final Type typeOfHit;
    public final Vec3d hitVec;
    public final EnumFacing sideHit;
    public final Entity entityHit;
    private final BlockPos blockPos;

    public RayTraceResult(Vec3d hitVec, EnumFacing sideHit, BlockPos blockPos) {
        this(Type.BLOCK, hitVec, sideHit, blockPos, null);
    }

    public RayTraceResult(Entity entityHit, Vec3d hitVec) {
        this(Type.ENTITY, hitVec, null,
                entityHit == null ? BlockPos.ORIGIN : new BlockPos(entityHit), entityHit);
    }

    public RayTraceResult(Type type, Vec3d hitVec, EnumFacing sideHit, BlockPos blockPos) {
        this(type, hitVec, sideHit, blockPos, null);
    }

    private RayTraceResult(Type type, Vec3d hitVec, EnumFacing sideHit, BlockPos blockPos, Entity entityHit) {
        this.typeOfHit = type == null ? Type.MISS : type;
        this.hitVec = hitVec == null ? Vec3d.ZERO : hitVec;
        this.sideHit = sideHit;
        this.blockPos = blockPos == null ? BlockPos.ORIGIN : blockPos.toImmutable();
        this.entityHit = entityHit;
    }

    public BlockPos getBlockPos() {
        return this.blockPos;
    }

    public static RayTraceResult fromNative(MovingObjectPosition hit) {
        if (hit == null) return null;
        Type type = switch (hit.typeOfHit) {
            case BLOCK -> Type.BLOCK;
            case ENTITY -> Type.ENTITY;
            case MISS -> Type.MISS;
        };
        Vec3d vector = Vec3d.fromNative(hit.hitVec);
        EnumFacing side = type == Type.BLOCK && hit.sideHit >= 0 && hit.sideHit < 6
                ? EnumFacing.byIndex(hit.sideHit) : null;
        BlockPos pos = new BlockPos(hit.blockX, hit.blockY, hit.blockZ);
        return new RayTraceResult(type, vector, side, pos, hit.entityHit);
    }

    public MovingObjectPosition toNative() {
        if (this.typeOfHit == Type.ENTITY && this.entityHit != null) {
            return new MovingObjectPosition(this.entityHit, this.hitVec.toNative());
        }
        int side = this.sideHit == null ? -1 : this.sideHit.getIndex();
        return new MovingObjectPosition(
                this.blockPos.getX(), this.blockPos.getY(), this.blockPos.getZ(),
                side, this.hitVec.toNative(), this.typeOfHit == Type.BLOCK);
    }

    public static RayTraceResult trace(World world, Vec3d start, Vec3d end) {
        return trace(world, start, end, false, false, false);
    }

    public static RayTraceResult trace(World world, Vec3d start, Vec3d end,
                                       boolean stopOnLiquid, boolean ignoreNoBox, boolean returnLastMiss) {
        if (world == null || start == null || end == null) return null;
        return fromNative(world.func_147447_a(
                start.toNative(), end.toNative(), stopOnLiquid, ignoreNoBox, returnLastMiss));
    }

    public enum Type {
        MISS,
        BLOCK,
        ENTITY
    }
}
