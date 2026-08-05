package com.rtsbuilding.rtsbuilding.client.screen.shape;

import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.rendering.builder.BuildGhostBlockStateResolver;
import com.rtsbuilding.rtsbuilding.client.rendering.util.RenderingUtil;
import com.rtsbuilding.rtsbuilding.client.screen.culling.RtsCullingBox;
import com.rtsbuilding.rtsbuilding.client.screen.quickbuild.BuildShape;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import net.minecraft.client.Minecraft;
import com.rtsbuilding.rtsbuilding.platform.math.BlockPos;
import com.rtsbuilding.rtsbuilding.platform.math.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import com.rtsbuilding.rtsbuilding.platform.block.BlockState;
import com.rtsbuilding.rtsbuilding.platform.math.RayTraceResult;

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
            return java.util.Collections.emptyList();
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
        return RenderingUtil.filterBlocksWithinBounds(
                blocks, this.controller.getAnchorX(), this.controller.getAnchorZ(), this.controller.getMaxRadius());
    }

    public boolean isBreakable(BlockPos pos) {
        if (pos == null) {
            return false;
        }
        Minecraft mc = this.screen.getMinecraft();
        if (mc == null || mc.theWorld == null) {
            return true;
        }
        BlockState state = BlockState.fromWorld(mc.theWorld, pos);
        return !state.getMaterial().isLiquid()
                && !state.getBlock().isAir(mc.theWorld, pos.getX(), pos.getY(), pos.getZ())
                && state.getBlockHardness(mc.theWorld, pos) >= 0.0F;
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
        return mc != null && mc.thePlayer != null ? mc.thePlayer.getHeldItem() : null;
    }

    public BlockState pendingGhostState(BlockPos targetPos) {
        Minecraft mc = this.screen.getMinecraft();
        ItemStack stack = placementStack();
        if (com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(stack) || !(stack.getItem() instanceof ItemBlock)) {
            return null;
        }
        ItemBlock blockItem = (ItemBlock) stack.getItem();
        if (targetPos == null) {
            return BlockState.defaultState(net.minecraft.block.Block.getBlockFromItem(blockItem));
        }
        BlockState state = BuildGhostBlockStateResolver.resolveStateWithCamera(
                mc, blockItem, stack, targetPos);
        if (state == null) {
            return null;
        }
        int degrees = this.modeState.activeRotateDegrees();
        return degrees == 0
                ? state
                : BuildGhostBlockStateResolver.applyRotation(state, degrees, mc.theWorld, targetPos);
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
            ResourceLocation key = resourceLocation(itemId);
            return key != null
                    && com.rtsbuilding.rtsbuilding.platform.registry.RtsRegistries.ITEMS.containsKey(key)
                    && com.rtsbuilding.rtsbuilding.platform.registry.RtsRegistries.ITEMS.getObject(key) instanceof ItemBlock;
        }
        return this.screen.canUseToolSlotShapeSource();
    }

    public RayTraceResult templateHit(ShapeBuildTypes.Input input) {
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

    public static List<RayTraceResult> wrapPlacementHits(List<BlockPos> positions, EnumFacing face) {
        if (positions == null || positions.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        List<RayTraceResult> hits = new ArrayList<>(positions.size());
        for (BlockPos pos : positions) {
            hits.add(ShapeGeometryUtil.createShapePlacementHit(pos, face));
        }
        return hits;
    }

    private static ResourceLocation resourceLocation(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        try {
            return new ResourceLocation(value);
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
