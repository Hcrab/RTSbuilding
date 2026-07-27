package com.rtsbuilding.rtsbuilding.server.pipeline.context;

import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelinePipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.placement.PlacementExecutePipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.validation.SessionValidatePipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.workflow.WorkflowStartPipe;
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
 * 放置操作的强类型管道上下文。
 *
 * <p>提供对放置特定参数和共享数据的类型安全访问器，
 * 消除了整个放置管道实现中的 {@code ctx.<BlockPos>getArg(ARG_POS)} 强制转换。</p>
 *
 * <p>属于放置管道（PLACE_SINGLE、PLACE_BATCH、
 * QUICK_BUILD）的 Pipe 应在 {@link PipelinePipe#execute(PipelineContext)} 开头
 * 调用 {@link #require(PipelineContext)}:</p>
 * <pre>{@code
 * PlaceContext pctx = PlaceContext.require(ctx);
 * List<BlockPos> positions = pctx.getClickedPositions();
 * Direction face = pctx.getFace();
 * }</pre>
 */
public class PlaceContext extends PipelineContext {

    /**
     * 创建新的放置管道上下文。
     *
     * @param player 执行操作的服务器端玩家
     * @param args   不可变输入参数（会创建防御性副本）
     */
    private PlaceContext(ServerPlayer player, Map<String, Object> args) {
        super(player, args);
    }

    /**
     * 创建一个新的 {@link Builder}，用于构建 {@link PlaceContext}，
     * 提供类型安全的流式 setter，消除 {@code Map<String, Object>}
     * 样板代码。
     */
    public static Builder builder(ServerPlayer player) {
        return new Builder(player);
    }

    /**
     * 安全地将 {@link PipelineContext} 转换为 {@link PlaceContext}。
     *
     * <p>使用此方法代替原始的 {@code (PlaceContext) ctx} 转换。
     * 如果上下文不是 {@code PlaceContext}，会抛出携带描述性消息的
     * {@link IllegalArgumentException}，这比裸的
     * {@link ClassCastException} 更容易诊断配置错误的管道。</p>
     *
     * @param ctx  要转换的管道上下文
     * @return 相同的上下文，类型化为 {@code PlaceContext}
     * @throws IllegalArgumentException 如果 {@code ctx} 不是
     *         {@code PlaceContext} 实例
     */
    public static PlaceContext require(PipelineContext ctx) {
        if (ctx instanceof PlaceContext pc) {
            return pc;
        }
        throw new IllegalArgumentException(
                "Expected PlaceContext but got " + ctx.getClass().getSimpleName()
                + ". This pipe requires a placement pipeline (e.g. PLACE_SINGLE, "
                + "PLACE_BATCH, QUICK_BUILD). "
                + "Did you register it in the wrong pipeline?");
    }

    // ──────────────────────────────────────────────────────────────
    //  放置参数
    // ──────────────────────────────────────────────────────────────

    /** 返回要放置的位置列表。 */
    public List<BlockPos> getClickedPositions() {
        return getArg(PlacementExecutePipe.ARG_CLICKED_POSITIONS);
    }

    /** 返回放置面。 */
    public Direction getFace() {
        return getArg(PlacementExecutePipe.ARG_FACE);
    }

    /** 返回 X 命中偏移。 */
    public double getHitOffsetX() {
        Double val = getArg(PlacementExecutePipe.ARG_HIT_OFFSET_X);
        return val != null ? val : 0.0D;
    }

    /** 返回 Y 命中偏移。 */
    public double getHitOffsetY() {
        Double val = getArg(PlacementExecutePipe.ARG_HIT_OFFSET_Y);
        return val != null ? val : 0.0D;
    }

    /** 返回 Z 命中偏移。 */
    public double getHitOffsetZ() {
        Double val = getArg(PlacementExecutePipe.ARG_HIT_OFFSET_Z);
        return val != null ? val : 0.0D;
    }

    /**
     * 返回旋转步数。
     * 如果参数不存在则默认为 {@code 0}。
     */
    public byte getRotateSteps() {
        Integer val = getArg(PlacementExecutePipe.ARG_ROTATE_STEPS);
        return val != null ? val.byteValue() : (byte) 0;
    }

    /**
     * 返回是否启用了强制放置。
     * 如果参数不存在则默认为 {@code false}。
     */
    public boolean isForcePlace() {
        return hasArg(PlacementExecutePipe.ARG_FORCE_PLACE)
                && getArg(PlacementExecutePipe.ARG_FORCE_PLACE);
    }

    /**
     * 返回是否应跳过已被占用的位置。
     * 如果参数不存在则默认为 {@code false}。
     */
    public boolean isSkipIfOccupied() {
        return hasArg(PlacementExecutePipe.ARG_SKIP_IF_OCCUPIED)
                && getArg(PlacementExecutePipe.ARG_SKIP_IF_OCCUPIED);
    }

    /** 返回要放置的物品 ID。 */
    public String getItemId() {
        return getArg(PlacementExecutePipe.ARG_ITEM_ID);
    }

    /** 返回物品原型堆栈。 */
    public ItemStack getItemPrototype() {
        return getArg(PlacementExecutePipe.ARG_ITEM_PROTOTYPE);
    }

    /** 返回射线原点 X 坐标。 */
    public double getRayOriginX() {
        Double val = getArg(PlacementExecutePipe.ARG_RAY_ORIGIN_X);
        return val != null ? val : 0.0D;
    }

    /** 返回射线原点 Y 坐标。 */
    public double getRayOriginY() {
        Double val = getArg(PlacementExecutePipe.ARG_RAY_ORIGIN_Y);
        return val != null ? val : 0.0D;
    }

    /** 返回射线原点 Z 坐标。 */
    public double getRayOriginZ() {
        Double val = getArg(PlacementExecutePipe.ARG_RAY_ORIGIN_Z);
        return val != null ? val : 0.0D;
    }

    /** 返回射线方向 X 分量。 */
    public double getRayDirX() {
        Double val = getArg(PlacementExecutePipe.ARG_RAY_DIR_X);
        return val != null ? val : 0.0D;
    }

    /** 返回射线方向 Y 分量。 */
    public double getRayDirY() {
        Double val = getArg(PlacementExecutePipe.ARG_RAY_DIR_Y);
        return val != null ? val : 0.0D;
    }

    /** 返回射线方向 Z 分量。 */
    public double getRayDirZ() {
        Double val = getArg(PlacementExecutePipe.ARG_RAY_DIR_Z);
        return val != null ? val : 0.0D;
    }

    /**
     * 返回是否为快速构建放置。
     * 如果参数不存在则默认为 {@code false}。
     */
    public boolean isQuickBuild() {
        return hasArg(PlacementExecutePipe.ARG_QUICK_BUILD)
                && getArg(PlacementExecutePipe.ARG_QUICK_BUILD);
    }

    /**
     * 返回是否强制空手放置。
     * 如果参数不存在则默认为 {@code false}。
     */
    public boolean isForceEmptyHand() {
        return hasArg(PlacementExecutePipe.ARG_FORCE_EMPTY_HAND)
                && getArg(PlacementExecutePipe.ARG_FORCE_EMPTY_HAND);
    }

    /**
     * 返回是否应发送远程放置提示。
     * 如果参数不存在则默认为 {@code true}。
     */
    public boolean isSendRemoteHint() {
        return !hasArg(PlacementExecutePipe.ARG_SEND_REMOTE_HINT)
                || getArg(PlacementExecutePipe.ARG_SEND_REMOTE_HINT);
    }

    // ──────────────────────────────────────────────────────────────
    //  构建器
    // ──────────────────────────────────────────────────────────────

    /**
     * {@link PlaceContext} 的类型安全流式构建器。
     *
     * <p>用法：</p>
     * <pre>{@code
     * PlaceContext ctx = PlaceContext.builder(player)
     *     .clickedPositions(positions)
     *     .face(face)
     *     .itemId(itemId)
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

        /** 点击的方块位置。 */
        public Builder clickedPositions(List<BlockPos> positions) {
            args.put(PlacementExecutePipe.ARG_CLICKED_POSITIONS.name(), positions);
            return this;
        }

        /** 放置面方向。 */
        public Builder face(Direction face) {
            args.put(PlacementExecutePipe.ARG_FACE.name(), face);
            return this;
        }

        /** 命中偏移 X。 */
        public Builder hitOffsetX(double hitOffsetX) {
            args.put(PlacementExecutePipe.ARG_HIT_OFFSET_X.name(), hitOffsetX);
            return this;
        }

        /** 命中偏移 Y。 */
        public Builder hitOffsetY(double hitOffsetY) {
            args.put(PlacementExecutePipe.ARG_HIT_OFFSET_Y.name(), hitOffsetY);
            return this;
        }

        /** 命中偏移 Z。 */
        public Builder hitOffsetZ(double hitOffsetZ) {
            args.put(PlacementExecutePipe.ARG_HIT_OFFSET_Z.name(), hitOffsetZ);
            return this;
        }

        /** 旋转步数。 */
        public Builder rotateSteps(byte rotateSteps) {
            args.put(PlacementExecutePipe.ARG_ROTATE_STEPS.name(), (int) rotateSteps);
            return this;
        }

        /** 强制放置标志。 */
        public Builder forcePlace(boolean forcePlace) {
            args.put(PlacementExecutePipe.ARG_FORCE_PLACE.name(), forcePlace);
            return this;
        }

        /** 若已占用则跳过标志。 */
        public Builder skipIfOccupied(boolean skipIfOccupied) {
            args.put(PlacementExecutePipe.ARG_SKIP_IF_OCCUPIED.name(), skipIfOccupied);
            return this;
        }

        /** 要放置的物品 ID。 */
        public Builder itemId(String itemId) {
            args.put(PlacementExecutePipe.ARG_ITEM_ID.name(), itemId);
            return this;
        }

        /** 物品原型堆栈。 */
        public Builder itemPrototype(ItemStack itemPrototype) {
            args.put(PlacementExecutePipe.ARG_ITEM_PROTOTYPE.name(), itemPrototype);
            return this;
        }

        /** 射线原点 X。 */
        public Builder rayOriginX(double rayOriginX) {
            args.put(PlacementExecutePipe.ARG_RAY_ORIGIN_X.name(), rayOriginX);
            return this;
        }

        /** 射线原点 Y。 */
        public Builder rayOriginY(double rayOriginY) {
            args.put(PlacementExecutePipe.ARG_RAY_ORIGIN_Y.name(), rayOriginY);
            return this;
        }

        /** 射线原点 Z。 */
        public Builder rayOriginZ(double rayOriginZ) {
            args.put(PlacementExecutePipe.ARG_RAY_ORIGIN_Z.name(), rayOriginZ);
            return this;
        }

        /** 射线方向 X。 */
        public Builder rayDirX(double rayDirX) {
            args.put(PlacementExecutePipe.ARG_RAY_DIR_X.name(), rayDirX);
            return this;
        }

        /** 射线方向 Y。 */
        public Builder rayDirY(double rayDirY) {
            args.put(PlacementExecutePipe.ARG_RAY_DIR_Y.name(), rayDirY);
            return this;
        }

        /** 射线方向 Z。 */
        public Builder rayDirZ(double rayDirZ) {
            args.put(PlacementExecutePipe.ARG_RAY_DIR_Z.name(), rayDirZ);
            return this;
        }

        /** 快速构建标志。 */
        public Builder quickBuild(boolean quickBuild) {
            args.put(PlacementExecutePipe.ARG_QUICK_BUILD.name(), quickBuild);
            return this;
        }

        /** 强制空手标志。 */
        public Builder forceEmptyHand(boolean forceEmptyHand) {
            args.put(PlacementExecutePipe.ARG_FORCE_EMPTY_HAND.name(), forceEmptyHand);
            return this;
        }

        /** 发送远程提示标志。 */
        public Builder sendRemoteHint(boolean sendRemoteHint) {
            args.put(PlacementExecutePipe.ARG_SEND_REMOTE_HINT.name(), sendRemoteHint);
            return this;
        }

        /** 工作流的总方块数。 */
        public Builder totalBlocks(int total) {
            args.put(WorkflowStartPipe.ARG_TOTAL_BLOCKS.name(), total);
            return this;
        }

        /** 构建 {@link PlaceContext}。 */
        public PlaceContext build() {
            return new PlaceContext(player, args);
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
}
