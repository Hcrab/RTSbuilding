package com.rtsbuilding.rtsbuilding.server.pipeline.context;

import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelinePipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.mining.MiningExecutePipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.mining.UltimineExecutePipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.tool.ToolBorrowPipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.validation.SessionValidatePipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.workflow.WorkflowStartPipe;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageSession;
import com.rtsbuilding.rtsbuilding.server.storage.RtsToolLease;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 挖掘管线的强类型上下文。
 *
 * <p>当前 Forge 1.20.1 先用它承载单方块远程挖掘和停止挖掘。
 * 连锁/范围挖掘仍走旧处理器，等工具借用和队列语义完全对齐后再迁入。</p>
 */
public class MiningContext extends PipelineContext {
    private MiningContext(ServerPlayer player, Map<String, Object> args) {
        super(player, args);
    }

    public static Builder builder(ServerPlayer player) {
        return new Builder(player);
    }

    public static MiningContext require(PipelineContext ctx) {
        if (ctx instanceof MiningContext mc) {
            return mc;
        }
        throw new IllegalArgumentException("Expected MiningContext but got " + ctx.getClass().getSimpleName());
    }

    public int getToolSlot() {
        Integer val = getArg(ToolBorrowPipe.ARG_TOOL_SLOT);
        return val != null ? val : -1;
    }

    public String getToolItemId() {
        return getArg(ToolBorrowPipe.ARG_TOOL_ITEM_ID);
    }

    public ItemStack getToolPrototype() {
        return getArg(ToolBorrowPipe.ARG_TOOL_PROTOTYPE);
    }

    public BlockPos getPos() {
        return getArg(MiningExecutePipe.ARG_POS);
    }

    @Nullable
    public Direction getFace() {
        return getArg(MiningExecutePipe.ARG_FACE);
    }

    public boolean isAllowPlacedBlockRecovery() {
        return hasArg(MiningExecutePipe.ARG_ALLOW_PLACED_BLOCK_RECOVERY)
                && getArg(MiningExecutePipe.ARG_ALLOW_PLACED_BLOCK_RECOVERY);
    }

    public boolean isToolProtectionEnabled() {
        return !hasArg(MiningExecutePipe.ARG_TOOL_PROTECTION_ENABLED)
                || getArg(MiningExecutePipe.ARG_TOOL_PROTECTION_ENABLED);
    }

    @Nullable
    public RtsStorageSession getResolvedSession() {
        return getData(SessionValidatePipe.KEY_SESSION);
    }

    @Nullable
    public RtsToolLease getToolLease() {
        return getData(ToolBorrowPipe.KEY_TOOL_LEASE);
    }

    public boolean hasToolLease() {
        return hasData(ToolBorrowPipe.KEY_TOOL_LEASE);
    }

    public int getWorkflowEntryId() {
        Integer val = getData(PipelineContext.KEY_WORKFLOW_ENTRY_ID);
        return val != null ? val : -1;
    }

    public boolean hasWorkflowEntryId() {
        return hasData(PipelineContext.KEY_WORKFLOW_ENTRY_ID);
    }

    public boolean isSelectedToolRequested() {
        Boolean val = getData(ToolBorrowPipe.KEY_SELECTED_TOOL_REQUESTED);
        return val != null && val;
    }

    public static final class Builder {
        private final ServerPlayer player;
        private final Map<String, Object> args = new HashMap<>();

        private Builder(ServerPlayer player) {
            this.player = player;
        }

        public Builder toolSlot(int toolSlot) {
            args.put(ToolBorrowPipe.ARG_TOOL_SLOT.name(), toolSlot);
            return this;
        }

        public Builder toolItemId(String toolItemId) {
            args.put(ToolBorrowPipe.ARG_TOOL_ITEM_ID.name(), toolItemId);
            return this;
        }

        public Builder toolPrototype(ItemStack toolPrototype) {
            args.put(ToolBorrowPipe.ARG_TOOL_PROTOTYPE.name(), toolPrototype);
            return this;
        }

        public Builder pos(BlockPos pos) {
            args.put(MiningExecutePipe.ARG_POS.name(), pos);
            return this;
        }

        public Builder face(Direction face) {
            args.put(MiningExecutePipe.ARG_FACE.name(), face);
            return this;
        }

        public Builder allowPlacedBlockRecovery(boolean allow) {
            args.put(MiningExecutePipe.ARG_ALLOW_PLACED_BLOCK_RECOVERY.name(), allow);
            return this;
        }

        public Builder toolProtectionEnabled(boolean enabled) {
            args.put(MiningExecutePipe.ARG_TOOL_PROTECTION_ENABLED.name(), enabled);
            return this;
        }

        public Builder totalBlocks(int total) {
            args.put(WorkflowStartPipe.ARG_TOTAL_BLOCKS.name(), total);
            return this;
        }

        public Builder requestedLimit(int limit) {
            args.put(UltimineExecutePipe.ARG_REQUESTED_LIMIT.name(), limit);
            return this;
        }

        public Builder mode(byte mode) {
            args.put(UltimineExecutePipe.ARG_MODE.name(), mode);
            return this;
        }

        public Builder minX(int minX) {
            args.put(UltimineExecutePipe.ARG_MIN_X.name(), minX);
            return this;
        }

        public Builder maxX(int maxX) {
            args.put(UltimineExecutePipe.ARG_MAX_X.name(), maxX);
            return this;
        }

        public Builder minY(int minY) {
            args.put(UltimineExecutePipe.ARG_MIN_Y.name(), minY);
            return this;
        }

        public Builder maxY(int maxY) {
            args.put(UltimineExecutePipe.ARG_MAX_Y.name(), maxY);
            return this;
        }

        public Builder minZ(int minZ) {
            args.put(UltimineExecutePipe.ARG_MIN_Z.name(), minZ);
            return this;
        }

        public Builder maxZ(int maxZ) {
            args.put(UltimineExecutePipe.ARG_MAX_Z.name(), maxZ);
            return this;
        }

        public Builder shapeType(byte shapeType) {
            args.put(UltimineExecutePipe.ARG_SHAPE_TYPE.name(), shapeType);
            return this;
        }

        public Builder fillType(byte fillType) {
            args.put(UltimineExecutePipe.ARG_FILL_TYPE.name(), fillType);
            return this;
        }

        public Builder positions(List<BlockPos> positions) {
            args.put(UltimineExecutePipe.ARG_POSITIONS.name(), positions);
            return this;
        }

        public MiningContext build() {
            return new MiningContext(player, args);
        }
    }
}
