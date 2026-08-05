package com.rtsbuilding.rtsbuilding.platform.math;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Locale;

import net.minecraftforge.common.util.ForgeDirection;

/**
 * 版本中立的六面方向枚举，序号保持 1.12.2 的 DOWN、UP、NORTH、SOUTH、WEST、EAST。
 *
 * <p>稳定序号让既有网络包和 NBT 可以原样保留；调用 1.7.10 Forge API 时显式转换为
 * {@link ForgeDirection}，避免让 Forge 类型渗入共享业务层。</p>
 */
public enum EnumFacing {
    DOWN(0, -1, 0, Axis.Y, AxisDirection.NEGATIVE),
    UP(0, 1, 0, Axis.Y, AxisDirection.POSITIVE),
    NORTH(0, 0, -1, Axis.Z, AxisDirection.NEGATIVE),
    SOUTH(0, 0, 1, Axis.Z, AxisDirection.POSITIVE),
    WEST(-1, 0, 0, Axis.X, AxisDirection.NEGATIVE),
    EAST(1, 0, 0, Axis.X, AxisDirection.POSITIVE);

    public static final EnumFacing[] VALUES = values();
    public static final EnumFacing[] HORIZONTALS = { NORTH, EAST, SOUTH, WEST };

    private final int xOffset;
    private final int yOffset;
    private final int zOffset;
    private final Axis axis;
    private final AxisDirection axisDirection;
    private final Vec3i directionVec;

    EnumFacing(int xOffset, int yOffset, int zOffset, Axis axis, AxisDirection axisDirection) {
        this.xOffset = xOffset;
        this.yOffset = yOffset;
        this.zOffset = zOffset;
        this.axis = axis;
        this.axisDirection = axisDirection;
        this.directionVec = new Vec3i(xOffset, yOffset, zOffset);
    }

    public int getIndex() {
        return this.ordinal();
    }

    public int getXOffset() {
        return this.xOffset;
    }

    public int getYOffset() {
        return this.yOffset;
    }

    public int getZOffset() {
        return this.zOffset;
    }

    public Vec3i getDirectionVec() {
        return this.directionVec;
    }

    public Axis getAxis() {
        return this.axis;
    }

    public AxisDirection getAxisDirection() {
        return this.axisDirection;
    }

    public String getName() {
        return this.name().toLowerCase(Locale.ROOT);
    }

    /** 保留 1.12.2 的序列化名称别名。 */
    public String getName2() {
        return getName();
    }

    public EnumFacing getOpposite() {
        return switch (this) {
            case DOWN -> UP;
            case UP -> DOWN;
            case NORTH -> SOUTH;
            case SOUTH -> NORTH;
            case WEST -> EAST;
            case EAST -> WEST;
        };
    }

    public EnumFacing rotateY() {
        return switch (this) {
            case NORTH -> EAST;
            case EAST -> SOUTH;
            case SOUTH -> WEST;
            case WEST -> NORTH;
            default -> throw new IllegalStateException("垂直方向不能绕 Y 轴按水平规则旋转: " + this);
        };
    }

    public EnumFacing rotateYCCW() {
        return switch (this) {
            case NORTH -> WEST;
            case WEST -> SOUTH;
            case SOUTH -> EAST;
            case EAST -> NORTH;
            default -> throw new IllegalStateException("垂直方向不能绕 Y 轴按水平规则旋转: " + this);
        };
    }

    public ForgeDirection toForgeDirection() {
        return ForgeDirection.getOrientation(this.ordinal());
    }

    public static EnumFacing fromForgeDirection(ForgeDirection direction) {
        return direction == null || direction == ForgeDirection.UNKNOWN
                ? null : byIndex(direction.ordinal());
    }

    public static EnumFacing byIndex(int index) {
        return VALUES[Math.floorMod(index, VALUES.length)];
    }

    public static EnumFacing fromAngle(double angle) {
        int horizontal = Math.floorMod((int) Math.floor(angle / 90.0D + 0.5D), 4);
        return HORIZONTALS[horizontal];
    }

    public static EnumFacing getFacingFromVector(float x, float y, float z) {
        EnumFacing best = NORTH;
        float bestDot = Float.NEGATIVE_INFINITY;
        for (EnumFacing candidate : VALUES) {
            float dot = x * candidate.xOffset + y * candidate.yOffset + z * candidate.zOffset;
            if (dot > bestDot) {
                bestDot = dot;
                best = candidate;
            }
        }
        return best;
    }

    public enum Axis {
        X, Y, Z;

        public boolean isHorizontal() {
            return this == X || this == Z;
        }

        public boolean isVertical() {
            return this == Y;
        }

        public String getName() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }

    public enum AxisDirection {
        POSITIVE(1), NEGATIVE(-1);

        private final int offset;

        AxisDirection(int offset) {
            this.offset = offset;
        }

        public int getOffset() {
            return this.offset;
        }
    }

    public enum Plane implements Iterable<EnumFacing> {
        HORIZONTAL(HORIZONTALS),
        VERTICAL(new EnumFacing[] { UP, DOWN });

        private final EnumFacing[] facings;

        Plane(EnumFacing[] facings) {
            this.facings = facings;
        }

        @Override
        public Iterator<EnumFacing> iterator() {
            return Arrays.asList(this.facings).iterator();
        }
    }
}
