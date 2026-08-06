package com.rtsbuilding.rtsbuilding.client.screen.shape;

import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.compat.sable.RtsSableClientSpatialCompat;
import com.rtsbuilding.rtsbuilding.client.rendering.builder.BuildGhostBlockStateResolver;
import com.rtsbuilding.rtsbuilding.client.screen.culling.RtsCullingBox;
import com.rtsbuilding.rtsbuilding.client.screen.quickbuild.BuildShape;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.ArrayList;
import java.util.List;

import static com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreenConstants.SHAPE_MAX_DIMENSION;

/**
 * 形状操作的纯规划与世界资格 adapter。
 *
 * <p>它统一生成、边界过滤、占用判断和最终目标模板，但不决定何时确认，也不直接发包。
 * 顶层控制器把最终执行器注入进来，因此放置与破坏继续共享完全相同的目标规划链。</p>
 */
public final class ShapeWorldOperationPlanner {
    @FunctionalInterface
    public interface TargetFilter {
        List<BlockPos> filter(ShapeBuildTypes.Input input, List<BlockPos> rawPositions);
    }

    @FunctionalInterface
    public interface TargetExecutor {
        void execute(List<BlockPos> validPositions);
    }

    private final ShapeGenerationPlanCache generationPlans = new ShapeGenerationPlanCache();
    private BuilderScreen screen;
    private ClientRtsController controller;
    private ShapeModeState modeState;
    private ShapeSelectionSession session;
    private ShapeSelectionBoxController selectionBox;

    public void init(
            BuilderScreen screen,
            ClientRtsController controller,
            ShapeModeState modeState,
            ShapeSelectionSession session,
            ShapeSelectionBoxController selectionBox) {
        this.screen = screen;
        this.controller = controller;
        this.modeState = modeState;
        this.session = session;
        this.selectionBox = selectionBox;
    }

    public void clear() {
        this.generationPlans.clear();
    }

    public RtsCullingBox generatedBounds() {
        return this.generationPlans.bounds();
    }

    public List<BlockPos> generate(ShapeBuildTypes.Input input) {
        if (input == null) {
            return List.of();
        }
        boolean rangeDestroy = this.screen.isQuickBuildRangeDestroyMode()
                && !this.screen.isQuickBuildRangeDestroyChainMode();
        RtsCullingBox advancedBox = this.selectionBox.hasEditableSession() ? this.selectionBox.box() : null;
        return this.generationPlans.positions(new ShapeGenerationPlanCache.Request(
                input,
                this.modeState.activeFillMode(),
                advancedBox,
                rangeDestroy,
                ShapeSelectionBoxController.currentRangeDestroyLimits(),
                SHAPE_MAX_DIMENSION));
    }

    public List<BlockPos> filterToBounds(List<BlockPos> blocks) {
        if (!this.controller.hasBounds() || blocks == null) {
            return blocks;
        }
        Minecraft mc = this.screen.getMinecraft();
        return RtsSableClientSpatialCompat.filterWithinBounds(
                mc == null ? null : mc.level,
                blocks, this.controller.getAnchorX(), this.controller.getAnchorZ(), this.controller.getMaxRadius());
    }

    public boolean isBreakable(BlockPos pos) {
        if (pos == null) {
            return false;
        }
        Minecraft mc = this.screen.getMinecraft();
        if (mc == null || mc.level == null) {
            return true;
        }
        BlockState state = mc.level.getBlockState(pos);
        return state.getFluidState().isEmpty()
                && !state.isAir()
                && state.getDestroySpeed(mc.level, pos) >= 0.0F;
    }

    public List<BlockPos> filterPlacementTargets(ShapeBuildTypes.Input input, List<BlockPos> targets) {
        if (this.screen.isQuickBuildCreativeOverwriteEnabled()) {
            return ShapePlacementTargetResolver.resolveOverwriteTargets(targets);
        }
        return ShapePlacementTargetResolver.resolveTargets(
                input,
                targets,
                shouldSkipOccupiedTargets(input),
                ShapePlacementTargetResolver.minecraftWorld(this.screen.getMinecraft(), placementStack()));
    }

    public ItemStack placementStack() {
        if (this.controller.hasSelectedItem()) {
            return this.controller.getSelectedItemPreview();
        }
        Minecraft mc = this.screen.getMinecraft();
        return mc != null && mc.player != null ? mc.player.getMainHandItem() : ItemStack.EMPTY;
    }

    public BlockState pendingGhostState(BlockPos targetPos) {
        Minecraft mc = this.screen.getMinecraft();
        ItemStack stack = placementStack();
        if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem blockItem)) {
            return null;
        }
        if (targetPos == null) {
            return blockItem.getBlock().defaultBlockState();
        }
        BlockState state = BuildGhostBlockStateResolver.resolveStateWithCamera(
                mc, blockItem, stack, targetPos);
        if (state == null) {
            return null;
        }
        int degrees = this.modeState.activeRotateDegrees();
        return degrees == 0
                ? state
                : BuildGhostBlockStateResolver.applyRotation(state, degrees, mc.level, targetPos);
    }

    private boolean shouldSkipOccupiedTargets(ShapeBuildTypes.Input input) {
        if (input == null || input.shape() == BuildShape.BLOCK) {
            return false;
        }
        ShapeBuildTypes.Session current = this.session.current();
        if (current == null || current.phase() != ShapeBuildTypes.Phase.READY_CONFIRM
                || this.controller.hasSelectedFluid()) {
            return false;
        }
        if (this.controller.hasSelectedItem()) {
            String itemId = this.controller.getSelectedItemId();
            ResourceLocation key = itemId == null || itemId.isBlank() ? null : ResourceLocation.tryParse(itemId);
            return key != null
                    && BuiltInRegistries.ITEM.containsKey(key)
                    && BuiltInRegistries.ITEM.get(key) instanceof BlockItem;
        }
        return this.screen.canUseToolSlotShapeSource();
    }

    public BlockHitResult templateHit(ShapeBuildTypes.Input input) {
        if (this.session.templateHit() != null) {
            return this.session.templateHit();
        }
        if (input == null || input.pointA() == null || input.placementFace() == null) {
            return null;
        }
        return ShapeGeometryUtil.createShapePlacementHit(input.pointA(), input.placementFace());
    }

    public boolean execute(
            ShapeBuildTypes.Input input,
            TargetFilter filter,
            TargetExecutor executor,
            Runnable clearSession) {
        List<BlockPos> targets = filter.filter(input, generate(input));
        clearSession.run();
        if (targets.isEmpty()) {
            return true;
        }
        List<BlockPos> bounded = filterToBounds(targets);
        if (!bounded.isEmpty()) {
            executor.execute(bounded);
        }
        return true;
    }

    public static List<BlockHitResult> wrapPlacementHits(List<BlockPos> positions, Direction face) {
        if (positions == null || positions.isEmpty()) {
            return List.of();
        }
        List<BlockHitResult> hits = new ArrayList<>(positions.size());
        for (BlockPos pos : positions) {
            hits.add(ShapeGeometryUtil.createShapePlacementHit(pos, face));
        }
        return hits;
    }
}
