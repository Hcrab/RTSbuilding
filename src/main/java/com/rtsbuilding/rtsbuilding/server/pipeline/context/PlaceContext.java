package com.rtsbuilding.rtsbuilding.server.pipeline.context;

import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelinePipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.placement.PlacementExecutePipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.validation.SessionValidatePipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.workflow.WorkflowStartPipe;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageSession;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 放置管线的强类型上下文。
 *
 * <p>这个类只负责放置请求的参数与共享数据访问，不负责实际放置、
 * 物品提取或 UI 刷新。这样后续 Forge 1.20.1 与 main 的 pipeline
 * 对齐时，业务 Pipe 可以共享同一组清晰的输入契约。</p>
 */
public class PlaceContext extends PipelineContext {

    private PlaceContext(ServerPlayer player, Map<String, Object> args) {
        super(player, args);
    }

    public static Builder builder(ServerPlayer player) {
        return new Builder(player);
    }

    public static PlaceContext require(PipelineContext ctx) {
        if (ctx instanceof PlaceContext pc) {
            return pc;
        }
        throw new IllegalArgumentException("Expected PlaceContext but got " + ctx.getClass().getSimpleName());
    }

    public List<BlockPos> getClickedPositions() {
        return getArg(PlacementExecutePipe.ARG_CLICKED_POSITIONS);
    }

    public Direction getFace() {
        return getArg(PlacementExecutePipe.ARG_FACE);
    }

    public double getHitOffsetX() {
        Double val = getArg(PlacementExecutePipe.ARG_HIT_OFFSET_X);
        return val != null ? val : 0.0D;
    }

    public double getHitOffsetY() {
        Double val = getArg(PlacementExecutePipe.ARG_HIT_OFFSET_Y);
        return val != null ? val : 0.0D;
    }

    public double getHitOffsetZ() {
        Double val = getArg(PlacementExecutePipe.ARG_HIT_OFFSET_Z);
        return val != null ? val : 0.0D;
    }

    public byte getRotateSteps() {
        Integer val = getArg(PlacementExecutePipe.ARG_ROTATE_STEPS);
        return val != null ? val.byteValue() : (byte) 0;
    }

    public boolean isForcePlace() {
        return hasArg(PlacementExecutePipe.ARG_FORCE_PLACE)
                && getArg(PlacementExecutePipe.ARG_FORCE_PLACE);
    }

    public boolean isSkipIfOccupied() {
        return hasArg(PlacementExecutePipe.ARG_SKIP_IF_OCCUPIED)
                && getArg(PlacementExecutePipe.ARG_SKIP_IF_OCCUPIED);
    }

    public String getItemId() {
        return getArg(PlacementExecutePipe.ARG_ITEM_ID);
    }

    public ItemStack getItemPrototype() {
        return getArg(PlacementExecutePipe.ARG_ITEM_PROTOTYPE);
    }

    public double getRayOriginX() {
        Double val = getArg(PlacementExecutePipe.ARG_RAY_ORIGIN_X);
        return val != null ? val : 0.0D;
    }

    public double getRayOriginY() {
        Double val = getArg(PlacementExecutePipe.ARG_RAY_ORIGIN_Y);
        return val != null ? val : 0.0D;
    }

    public double getRayOriginZ() {
        Double val = getArg(PlacementExecutePipe.ARG_RAY_ORIGIN_Z);
        return val != null ? val : 0.0D;
    }

    public double getRayDirX() {
        Double val = getArg(PlacementExecutePipe.ARG_RAY_DIR_X);
        return val != null ? val : 0.0D;
    }

    public double getRayDirY() {
        Double val = getArg(PlacementExecutePipe.ARG_RAY_DIR_Y);
        return val != null ? val : 0.0D;
    }

    public double getRayDirZ() {
        Double val = getArg(PlacementExecutePipe.ARG_RAY_DIR_Z);
        return val != null ? val : 0.0D;
    }

    public boolean isQuickBuild() {
        return hasArg(PlacementExecutePipe.ARG_QUICK_BUILD)
                && getArg(PlacementExecutePipe.ARG_QUICK_BUILD);
    }

    public boolean isForceEmptyHand() {
        return hasArg(PlacementExecutePipe.ARG_FORCE_EMPTY_HAND)
                && getArg(PlacementExecutePipe.ARG_FORCE_EMPTY_HAND);
    }

    public boolean isSendRemoteHint() {
        return !hasArg(PlacementExecutePipe.ARG_SEND_REMOTE_HINT)
                || getArg(PlacementExecutePipe.ARG_SEND_REMOTE_HINT);
    }

    @Nullable
    public RtsStorageSession getResolvedSession() {
        return getData(SessionValidatePipe.KEY_SESSION);
    }

    public int getWorkflowEntryId() {
        Integer val = getData(PipelineContext.KEY_WORKFLOW_ENTRY_ID);
        return val != null ? val : -1;
    }

    public boolean hasWorkflowEntryId() {
        return hasData(PipelineContext.KEY_WORKFLOW_ENTRY_ID);
    }

    public static final class Builder {
        private final ServerPlayer player;
        private final Map<String, Object> args = new HashMap<>();

        private Builder(ServerPlayer player) {
            this.player = player;
        }

        public Builder clickedPositions(List<BlockPos> positions) {
            args.put(PlacementExecutePipe.ARG_CLICKED_POSITIONS.name(), positions);
            return this;
        }

        public Builder face(Direction face) {
            args.put(PlacementExecutePipe.ARG_FACE.name(), face);
            return this;
        }

        public Builder hitOffsetX(double hitOffsetX) {
            args.put(PlacementExecutePipe.ARG_HIT_OFFSET_X.name(), hitOffsetX);
            return this;
        }

        public Builder hitOffsetY(double hitOffsetY) {
            args.put(PlacementExecutePipe.ARG_HIT_OFFSET_Y.name(), hitOffsetY);
            return this;
        }

        public Builder hitOffsetZ(double hitOffsetZ) {
            args.put(PlacementExecutePipe.ARG_HIT_OFFSET_Z.name(), hitOffsetZ);
            return this;
        }

        public Builder rotateSteps(byte rotateSteps) {
            args.put(PlacementExecutePipe.ARG_ROTATE_STEPS.name(), (int) rotateSteps);
            return this;
        }

        public Builder forcePlace(boolean forcePlace) {
            args.put(PlacementExecutePipe.ARG_FORCE_PLACE.name(), forcePlace);
            return this;
        }

        public Builder skipIfOccupied(boolean skipIfOccupied) {
            args.put(PlacementExecutePipe.ARG_SKIP_IF_OCCUPIED.name(), skipIfOccupied);
            return this;
        }

        public Builder itemId(String itemId) {
            args.put(PlacementExecutePipe.ARG_ITEM_ID.name(), itemId);
            return this;
        }

        public Builder itemPrototype(ItemStack itemPrototype) {
            args.put(PlacementExecutePipe.ARG_ITEM_PROTOTYPE.name(), itemPrototype);
            return this;
        }

        public Builder rayOriginX(double rayOriginX) {
            args.put(PlacementExecutePipe.ARG_RAY_ORIGIN_X.name(), rayOriginX);
            return this;
        }

        public Builder rayOriginY(double rayOriginY) {
            args.put(PlacementExecutePipe.ARG_RAY_ORIGIN_Y.name(), rayOriginY);
            return this;
        }

        public Builder rayOriginZ(double rayOriginZ) {
            args.put(PlacementExecutePipe.ARG_RAY_ORIGIN_Z.name(), rayOriginZ);
            return this;
        }

        public Builder rayDirX(double rayDirX) {
            args.put(PlacementExecutePipe.ARG_RAY_DIR_X.name(), rayDirX);
            return this;
        }

        public Builder rayDirY(double rayDirY) {
            args.put(PlacementExecutePipe.ARG_RAY_DIR_Y.name(), rayDirY);
            return this;
        }

        public Builder rayDirZ(double rayDirZ) {
            args.put(PlacementExecutePipe.ARG_RAY_DIR_Z.name(), rayDirZ);
            return this;
        }

        public Builder quickBuild(boolean quickBuild) {
            args.put(PlacementExecutePipe.ARG_QUICK_BUILD.name(), quickBuild);
            return this;
        }

        public Builder forceEmptyHand(boolean forceEmptyHand) {
            args.put(PlacementExecutePipe.ARG_FORCE_EMPTY_HAND.name(), forceEmptyHand);
            return this;
        }

        public Builder sendRemoteHint(boolean sendRemoteHint) {
            args.put(PlacementExecutePipe.ARG_SEND_REMOTE_HINT.name(), sendRemoteHint);
            return this;
        }

        public Builder totalBlocks(int total) {
            args.put(WorkflowStartPipe.ARG_TOTAL_BLOCKS.name(), total);
            return this;
        }

        public PlaceContext build() {
            return new PlaceContext(player, args);
        }
    }
}
