package com.rtsbuilding.rtsbuilding.platform.math;

import java.util.Iterator;
import java.util.NoSuchElementException;

import net.minecraft.entity.Entity;

/**
 * 跨版本方块坐标值对象。
 *
 * <p>它保留 1.12.2 数据与算法层依赖的打包格式和常用几何操作，但所有世界访问仍必须
 * 通过 1.7.10 适配器拆成 x/y/z。嵌套可变类型只用于热循环，绝不能越过集合或异步边界。</p>
 */
public class BlockPos extends Vec3i {
    public static final BlockPos ORIGIN = new BlockPos(0, 0, 0);

    private static final int NUM_X_BITS = 26;
    private static final int NUM_Z_BITS = 26;
    private static final int NUM_Y_BITS = 12;
    private static final int Y_SHIFT = NUM_Z_BITS;
    private static final int X_SHIFT = Y_SHIFT + NUM_Y_BITS;
    private static final long X_MASK = (1L << NUM_X_BITS) - 1L;
    private static final long Y_MASK = (1L << NUM_Y_BITS) - 1L;
    private static final long Z_MASK = (1L << NUM_Z_BITS) - 1L;

    public BlockPos(int x, int y, int z) {
        super(x, y, z);
    }

    public BlockPos(double x, double y, double z) {
        this(floor(x), floor(y), floor(z));
    }

    public BlockPos(Vec3i vector) {
        this(vector.getX(), vector.getY(), vector.getZ());
    }

    public BlockPos(Vec3d vector) {
        this(vector.x, vector.y, vector.z);
    }

    public BlockPos(Entity entity) {
        this(entity.posX, entity.posY, entity.posZ);
    }

    public BlockPos add(int x, int y, int z) {
        return x == 0 && y == 0 && z == 0 ? this : new BlockPos(this.x + x, this.y + y, this.z + z);
    }

    public BlockPos add(double x, double y, double z) {
        return x == 0.0D && y == 0.0D && z == 0.0D
                ? this : new BlockPos(this.x + x, this.y + y, this.z + z);
    }

    public BlockPos add(Vec3i vector) {
        return this.add(vector.getX(), vector.getY(), vector.getZ());
    }

    public BlockPos subtract(Vec3i vector) {
        return this.add(-vector.getX(), -vector.getY(), -vector.getZ());
    }

    public BlockPos up() {
        return this.up(1);
    }

    public BlockPos up(int amount) {
        return this.offset(EnumFacing.UP, amount);
    }

    public BlockPos down() {
        return this.down(1);
    }

    public BlockPos down(int amount) {
        return this.offset(EnumFacing.DOWN, amount);
    }

    public BlockPos north() {
        return this.north(1);
    }

    public BlockPos north(int amount) {
        return this.offset(EnumFacing.NORTH, amount);
    }

    public BlockPos south() {
        return this.south(1);
    }

    public BlockPos south(int amount) {
        return this.offset(EnumFacing.SOUTH, amount);
    }

    public BlockPos west() {
        return this.west(1);
    }

    public BlockPos west(int amount) {
        return this.offset(EnumFacing.WEST, amount);
    }

    public BlockPos east() {
        return this.east(1);
    }

    public BlockPos east(int amount) {
        return this.offset(EnumFacing.EAST, amount);
    }

    public BlockPos offset(EnumFacing facing) {
        return this.offset(facing, 1);
    }

    public BlockPos offset(EnumFacing facing, int amount) {
        return amount == 0 ? this : this.add(
                facing.getXOffset() * amount,
                facing.getYOffset() * amount,
                facing.getZOffset() * amount);
    }

    @Override
    public BlockPos crossProduct(Vec3i other) {
        return new BlockPos(super.crossProduct(other));
    }

    public BlockPos toImmutable() {
        return this;
    }

    public long toLong() {
        return ((long) this.x & X_MASK) << X_SHIFT
                | ((long) this.y & Y_MASK) << Y_SHIFT
                | (long) this.z & Z_MASK;
    }

    public static BlockPos fromLong(long packed) {
        int x = unpackSigned(packed >> X_SHIFT, NUM_X_BITS);
        int y = unpackSigned(packed >> Y_SHIFT, NUM_Y_BITS);
        int z = unpackSigned(packed, NUM_Z_BITS);
        return new BlockPos(x, y, z);
    }

    public static Iterable<MutableBlockPos> getAllInBoxMutable(BlockPos first, BlockPos second) {
        int minX = Math.min(first.x, second.x);
        int minY = Math.min(first.y, second.y);
        int minZ = Math.min(first.z, second.z);
        int maxX = Math.max(first.x, second.x);
        int maxY = Math.max(first.y, second.y);
        int maxZ = Math.max(first.z, second.z);

        return () -> new Iterator<MutableBlockPos>() {
            private final MutableBlockPos cursor = new MutableBlockPos(minX, minY, minZ);
            private int nextX = minX;
            private int nextY = minY;
            private int nextZ = minZ;
            private boolean hasNext = true;

            @Override
            public boolean hasNext() {
                return this.hasNext;
            }

            @Override
            public MutableBlockPos next() {
                if (!this.hasNext) throw new NoSuchElementException();
                this.cursor.setPos(this.nextX, this.nextY, this.nextZ);

                if (this.nextX < maxX) {
                    this.nextX++;
                } else if (this.nextY < maxY) {
                    this.nextX = minX;
                    this.nextY++;
                } else if (this.nextZ < maxZ) {
                    this.nextX = minX;
                    this.nextY = minY;
                    this.nextZ++;
                } else {
                    this.hasNext = false;
                }

                // 为保持 vanilla 的低分配语义，迭代器复用同一对象；调用方若保存必须先 toImmutable()。
                return this.cursor;
            }
        };
    }

    private static int floor(double value) {
        int integer = (int) value;
        return value < integer ? integer - 1 : integer;
    }

    private static int unpackSigned(long value, int bits) {
        long mask = (1L << bits) - 1L;
        long result = value & mask;
        long sign = 1L << bits - 1;
        if ((result & sign) != 0L) result -= 1L << bits;
        return (int) result;
    }

    /** 仅供热循环复用；放入集合、任务或网络对象前必须调用 {@link #toImmutable()}。 */
    public static class MutableBlockPos extends BlockPos {
        public MutableBlockPos() {
            this(0, 0, 0);
        }

        public MutableBlockPos(int x, int y, int z) {
            super(x, y, z);
        }

        public MutableBlockPos setPos(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
            return this;
        }

        public MutableBlockPos setPos(Vec3i pos) {
            return this.setPos(pos.getX(), pos.getY(), pos.getZ());
        }

        public MutableBlockPos move(EnumFacing facing) {
            return this.move(facing, 1);
        }

        public MutableBlockPos move(EnumFacing facing, int amount) {
            return this.setPos(
                    this.x + facing.getXOffset() * amount,
                    this.y + facing.getYOffset() * amount,
                    this.z + facing.getZOffset() * amount);
        }

        @Override
        public BlockPos toImmutable() {
            return new BlockPos(this.x, this.y, this.z);
        }
    }
}
