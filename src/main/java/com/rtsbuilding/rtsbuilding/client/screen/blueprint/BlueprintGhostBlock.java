package com.rtsbuilding.rtsbuilding.client.screen.blueprint;

import com.rtsbuilding.rtsbuilding.platform.block.BlockState;
import com.rtsbuilding.rtsbuilding.platform.math.BlockPos;

import java.util.Objects;

/**
 * 蓝图世界虚影中的单个方块快照。
 *
 * <p>本记录只携带已经完成旋转的世界坐标、方块状态和“原蓝图方块缺失”标记，不负责
 * 材料判断、范围裁剪或实际渲染。将它从 BlueprintPanel 中移出后，预览生成器与各渲染器
 * 不再为了读取一条数据而依赖整个面板总状态。</p>
 */
public final class BlueprintGhostBlock {
    private final BlockPos pos;
    private final BlockState state;
    private final boolean missing;

    public BlueprintGhostBlock(BlockPos pos, BlockState state, boolean missing) {
        this.pos = pos;
        this.state = state;
        this.missing = missing;
    }

    public BlockPos pos() { return pos; }
    public BlockState state() { return state; }
    public boolean missing() { return missing; }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof BlueprintGhostBlock)) return false;
        BlueprintGhostBlock that = (BlueprintGhostBlock) other;
        return missing == that.missing && Objects.equals(pos, that.pos) && Objects.equals(state, that.state);
    }

    @Override public int hashCode() { return Objects.hash(pos, state, Boolean.valueOf(missing)); }

    @Override public String toString() {
        return "BlueprintGhostBlock[pos=" + pos + ", state=" + state + ", missing=" + missing + "]";
    }
}
