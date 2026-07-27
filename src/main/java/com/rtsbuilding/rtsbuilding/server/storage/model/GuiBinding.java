package com.rtsbuilding.rtsbuilding.server.storage.model;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

import java.util.Objects;

/**
 * 玩家自定义的外部 GUI 快捷绑定。
 *
 * <p>存储一个目标方块和显示元数据，允许从 RTS 模式一键打开容器的 GUI。
 * @param pos       目标方块坐标
 * @param dimension 目标方块所在维度
 * @param label     玩家自定义的显示标签
 * @param itemId    用于图标的物品 ID
 * @param face      与方块交互的朝向
 */
public final class GuiBinding {
    private final BlockPos pos;
    private final int dimension;
    private final String label;
    private final String itemId;
    private final EnumFacing face;

    public GuiBinding(BlockPos pos, int dimension, String label, String itemId, EnumFacing face) {
        this.pos = Objects.requireNonNull(pos, "pos").toImmutable();
        this.dimension = dimension;
        this.label = label == null ? "" : label;
        this.itemId = itemId == null ? "" : itemId;
        this.face = face == null ? EnumFacing.UP : face;
    }

    public BlockPos pos() { return pos; }
    public int dimension() { return dimension; }
    public String label() { return label; }
    public String itemId() { return itemId; }
    public EnumFacing face() { return face; }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof GuiBinding)) return false;
        GuiBinding that = (GuiBinding) other;
        return dimension == that.dimension && pos.equals(that.pos) && label.equals(that.label)
                && itemId.equals(that.itemId) && face == that.face;
    }
    @Override public int hashCode() { return Objects.hash(pos, dimension, label, itemId, face); }
}
