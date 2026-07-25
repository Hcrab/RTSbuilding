package com.rtsbuilding.rtsbuilding.uicore.blueprint;

import java.util.Objects;

/** 不依赖 Minecraft 的蓝图坐标/尺寸值。 */
public final class BlueprintInt3 {
    public final int x;
    public final int y;
    public final int z;

    public BlueprintInt3(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public BlueprintInt3 add(int dx, int dy, int dz) {
        return new BlueprintInt3(x + dx, y + dy, z + dz);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof BlueprintInt3)) return false;
        BlueprintInt3 that = (BlueprintInt3) other;
        return x == that.x && y == that.y && z == that.z;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y, z);
    }
}
