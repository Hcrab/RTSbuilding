package com.rtsbuilding.rtsbuilding.server.pipeline.context;

import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelinePipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.mining.MiningExecutePipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.mining.UltimineExecutePipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.tool.ToolBorrowPipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.validation.SessionValidatePipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.workflow.WorkflowStartPipe;
import com.rtsbuilding.rtsbuilding.server.service.mining.RtsToolLease;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 挖掘操作的强类型管道上下文。
 *
 * <p>提供对挖掘特定参数和共享数据的类型安全访问器，
 * 消除了整个挖掘管道实现中的 {@code ctx.<BlockPos>getArg(ARG_POS)} 强制转换。</p>
 *
 * <p>属于挖掘管道（MINE_SINGLE、ULTIMINE、
 * AREA_MINE、AREA_DESTROY）的 Pipe 应在
 * {@link PipelinePipe#execute(PipelineContext)} 开头调用 {@link #require(PipelineContext)}:</p>
 * <pre>{@code
 * MiningContext mctx = MiningContext.require(ctx);
 * BlockPos pos = mctx.getPos();
 * Direction face = mctx.getFace();
 * }</pre>
 */
public class MiningContext extends PipelineContext {

    /**
     * 创建新的挖掘管道上下文。
     *
     * @param player 执行操作的服务器端玩家
     * @param args   不可变输入参数（会创建防御性副本）
     */
    private MiningContext(ServerPlayer player, Map<String, Object> args) {
        super(player, args);
    }

    /**
     * 创建一个新的 {@link Builder}，用于构建 {@link MiningContext}，
     * 提供类型安全的流式 setter，消除 {@code Map<String, Object>}
     * 样板代码。
     */
    public static Builder builder(ServerPlayer player) {
        return new Builder(player);
    }

    /**
     * 安全地将 {@link PipelineContext} 转换为 {@link MiningContext}。
     *
     * <p>使用此方法代替原始的 {@code (MiningContext) ctx} 转换。
     * 如果上下文不是 {@code MiningContext}，会抛出携带描述性消息的
     * {@link IllegalArgumentException}，这比裸的
     * {@link ClassCastException} 更容易诊断配置错误的管道。</p>
     *
     * @param ctx  要转换的管道上下文
     * @return 相同的上下文，类型化为 {@code MiningContext}
     * @throws IllegalArgumentException 如果 {@code ctx} 不是
     *         {@code MiningContext} 实例
     */
    public static MiningContext require(PipelineContext ctx) {
        if (ctx instanceof MiningContext mc) {
            return mc;
        }
        throw new IllegalArgumentException(
                "Expected MiningContext but got " + ctx.getClass().getSimpleName()
                + ". This pipe requires a mining pipeline (e.g. MINE_SINGLE, "
                + "ULTIMINE, AREA_MINE, AREA_DESTROY). "
                + "Did you register it in the wrong pipeline?");
    }

    // ──────────────────────────────────────────────────────────────
    //  工具参数
    // ──────────────────────────────────────────────────────────────

    /** 返回借用工具的快捷栏槽索引。 */
    public int getToolSlot() {
        Integer val = getArg(ToolBorrowPipe.ARG_TOOL_SLOT);
        return val != null ? val : -1;
    }

    /** 返回工具物品 ID（可能为空）。 */
    public String getToolItemId() {
        return getArg(ToolBorrowPipe.ARG_TOOL_ITEM_ID);
    }

    /** 返回工具原型堆栈。 */
    public ItemStack getToolPrototype() {
        return getArg(ToolBorrowPipe.ARG_TOOL_PROTOTYPE);
    }

    // ──────────────────────────────────────────────────────────────
    //  挖掘参数
    // ──────────────────────────────────────────────────────────────

    /** 返回目标方块位置。 */
    public BlockPos getPos() {
        return getArg(MiningExecutePipe.ARG_POS);
    }

    /**
     * 返回挖掘面。
     *
     * @return 面方向，如果未提供则返回 {@code null}
     *         （默认为 {@link Direction#DOWN}）
     */
    @Nullable
    public Direction getFace() {
        return getArg(MiningExecutePipe.ARG_FACE);
    }

    /**
     * 返回是否启用了已放置方块恢复。
     * 如果参数不存在则默认为 {@code false}。
     */
    public boolean isAllowPlacedBlockRecovery() {
        return hasArg(MiningExecutePipe.ARG_ALLOW_PLACED_BLOCK_RECOVERY)
                && getArg(MiningExecutePipe.ARG_ALLOW_PLACED_BLOCK_RECOVERY);
    }

    /**
     * 返回是否启用了工具保护。
     * 如果参数不存在则默认为 {@code true}。
     */
    public boolean isToolProtectionEnabled() {
        return !hasArg(MiningExecutePipe.ARG_TOOL_PROTECTION_ENABLED)
                || getArg(MiningExecutePipe.ARG_TOOL_PROTECTION_ENABLED);
    }

    // ──────────────────────────────────────────────────────────────
    //  构建器
    // ──────────────────────────────────────────────────────────────

    /**
     * {@link MiningContext} 的类型安全流式构建器。
     *
     * <p>用法：</p>
     * <pre>{@code
     * MiningContext ctx = MiningContext.builder(player)
     *     .toolSlot(toolSlot)
     *     .toolItemId(toolItemId)
     *     .pos(pos)
     *     .face(face)
     *     .build();
     *
     * PipelineRegistry.execute(type, ctx);
     * }</pre>
     */
    public static final class Builder {
        private final ServerPlayer player;
        private final Map<String, Object> args = new HashMap<>();

        private Builder(ServerPlayer player) {
            this.player = player;
        }

        /** 工具槽索引。 */
        public Builder toolSlot(int toolSlot) {
            args.put(ToolBorrowPipe.ARG_TOOL_SLOT.name(), toolSlot);
            return this;
        }

        /** 工具物品 ID。 */
        public Builder toolItemId(String toolItemId) {
            args.put(ToolBorrowPipe.ARG_TOOL_ITEM_ID.name(), toolItemId);
            return this;
        }

        /** 工具原型堆栈。 */
        public Builder toolPrototype(ItemStack toolPrototype) {
            args.put(ToolBorrowPipe.ARG_TOOL_PROTOTYPE.name(), toolPrototype);
            return this;
        }

        /** 目标方块位置。 */
        public Builder pos(BlockPos pos) {
            args.put(MiningExecutePipe.ARG_POS.name(), pos);
            return this;
        }

        /** 挖掘面方向。 */
        public Builder face(Direction face) {
            args.put(MiningExecutePipe.ARG_FACE.name(), face);
            return this;
        }

        /** 允许已放置方块恢复。 */
        public Builder allowPlacedBlockRecovery(boolean allow) {
            args.put(MiningExecutePipe.ARG_ALLOW_PLACED_BLOCK_RECOVERY.name(), allow);
            return this;
        }

        /** 启用工具保护。 */
        public Builder toolProtectionEnabled(boolean enabled) {
            args.put(MiningExecutePipe.ARG_TOOL_PROTECTION_ENABLED.name(), enabled);
            return this;
        }

        /** 工作流的总方块数。 */
        public Builder totalBlocks(int total) {
            args.put(WorkflowStartPipe.ARG_TOTAL_BLOCKS.name(), total);
            return this;
        }

        /** 连锁挖掘操作的请求限制。 */
        public Builder requestedLimit(int limit) {
            args.put(UltimineExecutePipe.ARG_REQUESTED_LIMIT.name(), limit);
            return this;
        }

        /** 连锁挖掘模式。 */
        public Builder mode(byte mode) {
            args.put(UltimineExecutePipe.ARG_MODE.name(), mode);
            return this;
        }

        /** 区域操作的最小 X。 */
        public Builder minX(int minX) {
            args.put(UltimineExecutePipe.ARG_MIN_X.name(), minX);
            return this;
        }

        /** 区域操作的最大 X。 */
        public Builder maxX(int maxX) {
            args.put(UltimineExecutePipe.ARG_MAX_X.name(), maxX);
            return this;
        }

        /** 区域操作的最小 Y。 */
        public Builder minY(int minY) {
            args.put(UltimineExecutePipe.ARG_MIN_Y.name(), minY);
            return this;
        }

        /** 区域操作的最大 Y。 */
        public Builder maxY(int maxY) {
            args.put(UltimineExecutePipe.ARG_MAX_Y.name(), maxY);
            return this;
        }

        /** 区域操作的最小 Z。 */
        public Builder minZ(int minZ) {
            args.put(UltimineExecutePipe.ARG_MIN_Z.name(), minZ);
            return this;
        }

        /** 区域操作的最大 Z。 */
        public Builder maxZ(int maxZ) {
            args.put(UltimineExecutePipe.ARG_MAX_Z.name(), maxZ);
            return this;
        }

        /** 区域操作的形状类型。 */
        public Builder shapeType(byte shapeType) {
            args.put(UltimineExecutePipe.ARG_SHAPE_TYPE.name(), shapeType);
            return this;
        }

        /** 区域操作的填充类型。 */
        public Builder fillType(byte fillType) {
            args.put(UltimineExecutePipe.ARG_FILL_TYPE.name(), fillType);
            return this;
        }

        /** AREA_DESTROY 的位置列表。 */
        public Builder positions(List<BlockPos> positions) {
            args.put(UltimineExecutePipe.ARG_POSITIONS.name(), positions);
            return this;
        }

        /** 构建 {@link MiningContext}。 */
        public MiningContext build() {
            return new MiningContext(player, args);
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  共享数据访问器
    // ──────────────────────────────────────────────────────────────

    /**
     * 返回来自 {@link SessionValidatePipe} 解析的存储会话，
     * 如果尚未设置则返回 {@code null}。
     */
    @Nullable
    public RtsStorageSession getResolvedSession() {
        return getData(SessionValidatePipe.KEY_SESSION);
    }

    /**
     * 返回来自 {@link ToolBorrowPipe} 的借用工具租约，
     * 如果未设置（创造模式快速路径）则返回 {@code null}。
     */
    @Nullable
    public RtsToolLease getToolLease() {
        return getData(ToolBorrowPipe.KEY_TOOL_LEASE);
    }

    /** 如果共享数据中存在工具租约则返回 {@code true}。 */
    public boolean hasToolLease() {
        return hasData(ToolBorrowPipe.KEY_TOOL_LEASE);
    }

    /**
     * 返回来自 {@link WorkflowStartPipe} 的工作流条目 ID，
     * 如果未设置则返回 {@code -1}。
     */
    public int getWorkflowEntryId() {
        Integer val = getData(PipelineContext.KEY_WORKFLOW_ENTRY_ID);
        return val != null ? val : -1;
    }

    /** 如果共享数据中存在工作流条目 ID 则返回 {@code true}。 */
    public boolean hasWorkflowEntryId() {
        return hasData(PipelineContext.KEY_WORKFLOW_ENTRY_ID);
    }

    /**
     * 返回是否请求了特定的工具
     *（相对于自由形式/任意工具模式）。
     */
    public boolean isSelectedToolRequested() {
        Boolean val = getData(ToolBorrowPipe.KEY_SELECTED_TOOL_REQUESTED);
        return val != null && val;
    }
}
