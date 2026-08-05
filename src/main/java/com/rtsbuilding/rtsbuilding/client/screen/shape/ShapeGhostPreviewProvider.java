package com.rtsbuilding.rtsbuilding.client.screen.shape;

import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.screen.quickbuild.BuildShape;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import net.minecraft.client.Minecraft;
import com.rtsbuilding.rtsbuilding.platform.math.BlockPos;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemMonsterPlacer;
import com.rtsbuilding.rtsbuilding.platform.math.RayTraceResult;

import java.util.List;

/**
 * 形状、单方块与范围破坏虚影的唯一决策入口。
 *
 * <p>该 owner 只组装渲染快照，不修改会话、不发送网络请求。形状生成与世界资格判断通过
 * 窄运行时端口复用正式执行路径，保证预览不会悄悄形成第二套算法。</p>
 */
public final class ShapeGhostPreviewProvider {
    public interface Runtime {
        ShapeBuildTypes.Session session();

        ShapeBuildTypes.Input resolveInput(RayTraceResult cursorHit, boolean requireReady);

        List<BlockPos> generate(ShapeBuildTypes.Input input);

        List<BlockPos> filterPlacementTargets(ShapeBuildTypes.Input input, List<BlockPos> targets);

        boolean isBreakable(BlockPos pos);

        ConfirmedDestroyPreviewState.Progress destroyProgress();

        boolean isLiveDestroyTarget(BlockPos pos);
    }

    private BuilderScreen screen;
    private ClientRtsController controller;
    private ConfirmedDestroyPreviewState confirmedPreviews;
    private Runtime runtime;

    public void init(
            BuilderScreen screen,
            ClientRtsController controller,
            ConfirmedDestroyPreviewState confirmedPreviews,
            Runtime runtime) {
        this.screen = screen;
        this.controller = controller;
        this.confirmedPreviews = confirmedPreviews;
        this.runtime = runtime;
    }

    public ShapeDataRecords.GhostPreview snapshot() {
        if (this.screen.isQuickBuildRangeDestroyMode()) {
            return destroyPreview();
        }
        return buildPreview();
    }

    private ShapeDataRecords.GhostPreview destroyPreview() {
        if (this.screen.isQuickBuildRangeDestroyChainMode()) {
            ShapeDataRecords.GhostPreview confirmed = this.confirmedPreviews.activeChain(
                    this.runtime.destroyProgress(), this.runtime::isLiveDestroyTarget);
            if (confirmed != ShapeDataRecords.GhostPreview.EMPTY) {
                return confirmed;
            }
            List<BlockPos> preview = this.screen.collectUltiminePreviewBlocks();
            return preview.isEmpty()
                    ? ShapeDataRecords.GhostPreview.EMPTY
                    : new ShapeDataRecords.GhostPreview(preview, true, true, java.util.Collections.emptyList(), true);
        }
        if (this.controller.getBuildShape() == BuildShape.BLOCK) {
            RayTraceResult hit = this.screen.pickBlockHit();
            if (hit == null) {
                return ShapeDataRecords.GhostPreview.EMPTY;
            }
            List<BlockPos> breakable = ShapeDestroyTargetClassifier.breakableTargets(
                    java.util.Collections.singletonList(hit.getBlockPos()), this.runtime::isBreakable);
            return breakable.isEmpty()
                    ? ShapeDataRecords.GhostPreview.EMPTY
                    : new ShapeDataRecords.GhostPreview(breakable, true, true, java.util.Collections.emptyList());
        }
        ShapeBuildTypes.Input input = this.runtime.resolveInput(this.screen.pickBlockHit(), false);
        if (input == null) {
            return ShapeDataRecords.GhostPreview.EMPTY;
        }
        ShapeDestroyTargetClassifier.Selection selection = ShapeDestroyTargetClassifier.classify(
                this.runtime.generate(input), this.runtime::isBreakable);
        boolean ready = ready();
        if (selection.breakableBlocks().isEmpty()) {
            return selection.envelopeBlocks().isEmpty()
                    ? ShapeDataRecords.GhostPreview.EMPTY
                    : new ShapeDataRecords.GhostPreview(java.util.Collections.emptyList(), ready, true, selection.envelopeBlocks());
        }
        return new ShapeDataRecords.GhostPreview(
                selection.breakableBlocks(), ready, true, selection.envelopeBlocks());
    }

    private ShapeDataRecords.GhostPreview buildPreview() {
        if (this.controller.getBuildShape() == BuildShape.BLOCK) {
            return singleBlockPreview();
        }
        if (!this.controller.hasSelectedItem()
                && !this.controller.hasSelectedFluid()
                && !this.screen.canUseToolSlotShapeSource()) {
            return ShapeDataRecords.GhostPreview.EMPTY;
        }
        ShapeBuildTypes.Input input = this.runtime.resolveInput(this.screen.pickBlockHit(), false);
        if (input == null) {
            return ShapeDataRecords.GhostPreview.EMPTY;
        }
        List<BlockPos> blocks = this.runtime.filterPlacementTargets(input, this.runtime.generate(input));
        return blocks.isEmpty() ? ShapeDataRecords.GhostPreview.EMPTY : new ShapeDataRecords.GhostPreview(blocks, ready());
    }

    private ShapeDataRecords.GhostPreview singleBlockPreview() {
        if (this.controller.isEmptyHandSelected() || this.controller.hasSelectedFluid()) {
            return ShapeDataRecords.GhostPreview.EMPTY;
        }
        Minecraft mc = this.screen.getMinecraft();
        if (!hasSingleBlockSource(mc)) {
            return ShapeDataRecords.GhostPreview.EMPTY;
        }
        RayTraceResult hit = this.screen.pickBlockHit();
        if (hit == null || mc == null || mc.theWorld == null || mc.thePlayer == null) {
            return ShapeDataRecords.GhostPreview.EMPTY;
        }
        ItemStack stack = this.controller.hasSelectedItem()
                ? this.controller.getSelectedItemPreview()
                : mc.thePlayer.getHeldItem();
        if (com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(stack)) {
            return ShapeDataRecords.GhostPreview.EMPTY;
        }
        BlockPos target = ShapePlacementTargetResolver.resolveSingleGhostTarget(mc, hit, stack);
        return target == null
                ? ShapeDataRecords.GhostPreview.EMPTY
                : new ShapeDataRecords.GhostPreview(java.util.Collections.singletonList(target), true);
    }

    private boolean hasSingleBlockSource(Minecraft mc) {
        if (this.controller.hasSelectedItem() || this.screen.canUseToolSlotShapeSource()) {
            return true;
        }
        if (mc == null || mc.thePlayer == null) {
            return false;
        }
        return mc.thePlayer.getHeldItem().getItem() instanceof ItemBlock
                || mc.thePlayer.getHeldItem().getItem() instanceof ItemMonsterPlacer;
    }

    private boolean ready() {
        ShapeBuildTypes.Session session = this.runtime.session();
        return session != null && session.phase() == ShapeBuildTypes.Phase.READY_CONFIRM;
    }
}
