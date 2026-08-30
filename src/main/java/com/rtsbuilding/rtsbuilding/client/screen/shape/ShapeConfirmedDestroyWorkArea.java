package com.rtsbuilding.rtsbuilding.client.screen.shape;

import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.function.Function;

/**
 * 已确认破坏工作区的生命周期 owner。
 *
 * <p>它负责把候选区域裁入 RTS 边界、读取服务端任务进度并在目标消失后清理预览；不参与
 * 点击选区和形状生成。这样“鼠标下的候选虚影”和“已经提交的施工区”不会再混成同一状态。</p>
 */
public final class ShapeConfirmedDestroyWorkArea {
    private final ConfirmedDestroyPreviewState state = new ConfirmedDestroyPreviewState();
    private BuilderScreen screen;
    private ClientRtsController controller;
    private Function<List<BlockPos>, List<BlockPos>> boundsFilter;

    public void init(
            BuilderScreen screen,
            ClientRtsController controller,
            Function<List<BlockPos>, List<BlockPos>> boundsFilter) {
        this.screen = screen;
        this.controller = controller;
        this.boundsFilter = boundsFilter;
    }

    public ConfirmedDestroyPreviewState state() {
        return this.state;
    }

    public void clearChain() {
        this.state.clearChain();
    }

    public void rememberChain(List<BlockPos> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            this.state.clearChain();
            return;
        }
        List<BlockPos> bounded = filter(blocks);
        if (bounded.isEmpty()) {
            this.state.clearChain();
            return;
        }
        this.state.rememberChain(bounded);
    }

    public void rememberRange(ShapeDestroyTargetClassifier.Selection preview) {
        if (preview == null || preview.isEmpty()) {
            return;
        }
        List<BlockPos> breakable = filter(preview.breakableBlocks());
        if (!breakable.isEmpty()) {
            this.state.rememberRange(breakable, filter(preview.envelopeBlocks()));
        }
    }

    public List<ShapeDataRecords.GhostPreview> activeRanges() {
        return this.state.activeRanges(progress(), this::isLiveTarget);
    }

    public void removeRangeBlocks(List<BlockPos> skippedPositions) {
        this.state.removeRangeBlocks(skippedPositions);
    }

    public boolean hasActive() {
        return this.state.hasAnyActive(progress(), this::isLiveTarget);
    }

    public ConfirmedDestroyPreviewState.Progress progress() {
        BlockPos progressPos = this.controller.getMineProgressPos();
        RtsWorkflowStatus workflow = this.controller.findActiveDestroyWorkflow();
        return new ConfirmedDestroyPreviewState.Progress(
                progressPos,
                this.controller.getMineProgressStage(),
                workflow != null && workflow.totalBlocks() > 0);
    }

    public boolean isLiveTarget(BlockPos pos) {
        if (pos == null) {
            return false;
        }
        Minecraft mc = this.screen.getMinecraft();
        if (mc == null || mc.level == null) {
            return true;
        }
        BlockState state = mc.level.getBlockState(pos);
        return !state.isAir() && state.getFluidState().isEmpty();
    }

    private List<BlockPos> filter(List<BlockPos> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return List.of();
        }
        return this.boundsFilter == null ? blocks : this.boundsFilter.apply(blocks);
    }
}
