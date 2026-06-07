package com.rtsbuilding.rtsbuilding.client.screen;

import com.rtsbuilding.rtsbuilding.client.rendering.builder.PlacementAnimationRenderer;
import com.rtsbuilding.rtsbuilding.client.screen.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.screen.interaction.InteractionTypes;
import com.rtsbuilding.rtsbuilding.client.screen.quickbuild.BuildShape;
import com.rtsbuilding.rtsbuilding.client.screen.quickbuild.ShapeFillMode;
import com.rtsbuilding.rtsbuilding.client.screen.shape.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.state.BlockState;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import static com.rtsbuilding.rtsbuilding.client.screen.BuilderScreenConstants.SHAPE_HISTORY_LIMIT;
import static com.rtsbuilding.rtsbuilding.client.screen.BuilderScreenConstants.SHAPE_ROTATE_STEP_DEGREES;

public final class ScreenShapeController {
    private BuilderScreen screen;
    private ClientRtsController controller;

    private ShapeBuildTypes.Session shapeBuildSession;
    private BlockHitResult shapeTemplateHit;
    private int shapeFootprintNudgeA = 0;
    private int shapeFootprintNudgeB = 0;
    private double shapeCursorY = 0.0D;
    private ShapeFillMode shapeFillMode = ShapeFillMode.FILL;
    private int shapeRotateDegrees = 0;
    private boolean altShapeMenuHeld = false;
    private final List<ShapeDataRecords.HistoryBatch> shapeUndoStack = new ArrayList<>();
    private final List<ShapeDataRecords.HistoryBatch> shapeRedoStack = new ArrayList<>();

    public void init(BuilderScreen screen, ClientRtsController controller) {
        this.screen = screen;
        this.controller = controller;
    }

    // ===== Public state accessors =====

    public ShapeFillMode getShapeFillMode() {
        return this.shapeFillMode;
    }

    public void setShapeFillMode(ShapeFillMode mode) {
        this.shapeFillMode = mode;
    }

    public int getShapeRotateDegrees() {
        return this.shapeRotateDegrees;
    }

    public int getShapeUndoSize() {
        return this.shapeUndoStack.size();
    }

    public int getShapeRedoSize() {
        return this.shapeRedoStack.size();
    }

    // ===== Shape session management =====

    public void clearShapeBuildSession() {
        this.shapeBuildSession = null;
        this.shapeTemplateHit = null;
        this.shapeFootprintNudgeA = 0;
        this.shapeFootprintNudgeB = 0;
    }

    public void rotateShapeByStep(int step) {
        int raw = this.shapeRotateDegrees + (step * SHAPE_ROTATE_STEP_DEGREES);
        this.shapeRotateDegrees = Math.floorMod(raw, 360);
        this.screen.persistUiState();
    }
    public void rotateToDegrees(int degrees) {
        this.shapeRotateDegrees = Math.floorMod(degrees, 360);
    }

    public void setShapeCursorY(double cursorY) {
        this.shapeCursorY = cursorY;
    }

    public ShapeBuildTypes.Session getShapeBuildSession() {
        return this.shapeBuildSession;
    }

    public void ensureFillModeForShape(BuildShape shape) {
        List<ShapeFillMode> modes = ShapeGeometryUtil.availableFillModes(shape);
        if (modes.isEmpty()) {
            this.shapeFillMode = ShapeFillMode.FILL;
            this.screen.persistUiState();
            return;
        }
        if (!modes.contains(this.shapeFillMode)) {
            this.shapeFillMode = modes.get(0);
            this.screen.persistUiState();
        }
    }

    public boolean cycleShapeFillModeForCurrentShape(int step) {
        BuildShape shape = this.controller.getBuildShape();
        List<ShapeFillMode> modes = ShapeGeometryUtil.availableFillModes(shape);
        if (modes.isEmpty()) {
            return false;
        }
        int currentIndex = modes.indexOf(this.shapeFillMode);
        if (currentIndex < 0) {
            this.shapeFillMode = modes.get(0);
            this.screen.persistUiState();
            return true;
        }
        int next = Math.floorMod(currentIndex + step, modes.size());
        this.shapeFillMode = modes.get(next);
        this.screen.persistUiState();
        return true;
    }

    // ===== Shape building flow =====

    public void placeWithShape(BlockHitResult hit, boolean forcePlace, Vec3 rayOrigin, Vec3 rayDir, double mouseY,
            boolean fluidPlacement, InteractionTypes.PlacementReplayKind replayKind, String replayItemId, int replayToolSlot) {
        if (hit == null) {
            return;
        }
        BuildShape shape = this.controller.getBuildShape();
        if (shape == BuildShape.BLOCK) {
            clearShapeBuildSession();
            if (fluidPlacement) {
                this.controller.placeSelectedFluid(hit, forcePlace, rayOrigin, rayDir);
            } else {
                this.controller.placeSelected(hit, forcePlace, rayOrigin, rayDir);
                recordSinglePlacementForUndo(hit, replayKind, replayItemId, replayToolSlot);
                // Single block pending ghost
                BlockState pendingState = resolvePendingGhostBlockState();
                PlacementAnimationRenderer.addPendingBatch(
                        List.of(hit.getBlockPos().immutable()), pendingState);
            }
            return;
        }
        advanceShapeSession(hit, rayDir, mouseY, shape);
    }

    public void selectRangeDestroyShape(BlockHitResult hit, double mouseY, Vec3 rayDir) {
        if (hit == null) {
            return;
        }
        BuildShape shape = this.controller.getBuildShape();
        if (shape == BuildShape.BLOCK) {
            clearShapeBuildSession();
            this.controller.confirmShapeAreaDestroy(List.of(hit.getBlockPos().immutable()), this.screen.getSelectedToolSlot());
            return;
        }
        advanceShapeSession(hit, rayDir, mouseY, shape);
    }

    private void advanceShapeSession(BlockHitResult hit, Vec3 rayDir, double mouseY, BuildShape shape) {
        if (this.shapeBuildSession == null || this.shapeBuildSession.shape() != shape) {
            this.shapeFootprintNudgeA = 0;
            this.shapeFootprintNudgeB = 0;
            Direction placementFace = ShapeGeometryUtil.resolveShapePlacementFace(shape, hit.getDirection(), rayDir);
            this.shapeTemplateHit = new BlockHitResult(hit.getLocation(), placementFace, hit.getBlockPos(), hit.isInside());
            this.shapeBuildSession = new ShapeBuildTypes.Session(
                    shape,
                    ShapeGeometryUtil.resolveShapeBuildFace(shape, hit.getDirection(), rayDir),
                    placementFace,
                    hit.getBlockPos(),
                    null,
                    ShapeBuildTypes.Phase.NEED_SECOND_POINT,
                    0,
                    mouseY);
            return;
        }
        ShapeBuildTypes.Session session = this.shapeBuildSession;
        if (session.phase() == ShapeBuildTypes.Phase.NEED_SECOND_POINT) {
            BlockPos pointB = resolveShapePlanePoint(session, hit);
            this.shapeBuildSession = new ShapeBuildTypes.Session(
                    shape,
                    session.planeFace(),
                    session.placementFace(),
                    session.pointA(),
                    pointB,
                    ShapeBuildTypes.Phase.READY_CONFIRM,
                    0,
                    ShapeGeometryUtil.requiresThirdPoint(shape) ? mouseY : session.boxHeightMouseBaseY());
            return;
        }
        if (session.phase() == ShapeBuildTypes.Phase.NEED_THIRD_POINT) {
            this.shapeBuildSession = new ShapeBuildTypes.Session(
                    shape,
                    session.planeFace(),
                    session.placementFace(),
                    session.pointA(),
                    session.pointB(),
                    ShapeBuildTypes.Phase.READY_CONFIRM,
                    session.boxHeightOffset(),
                    session.boxHeightMouseBaseY());
        }
    }

    public boolean tryConfirmPendingRangeDestroy() {
        if (!this.screen.isQuickBuildRangeDestroyMode() || this.controller.getBuildShape() == BuildShape.BLOCK) {
            return false;
        }
        ShapeBuildTypes.Input input = resolveCurrentShapeBuildInput(null, true);
        if (input == null) {
            return false;
        }
        List<BlockPos> targets = buildRangeDestroyTargets(input);
        clearShapeBuildSession();
        if (targets.isEmpty()) {
            return true;
        }
        this.controller.confirmShapeAreaDestroy(targets, this.screen.getSelectedToolSlot());
        return true;
    }

    public boolean tryConfirmPendingShapeBuild(boolean forcePlace) {
        if (this.controller.getBuildShape() == BuildShape.BLOCK) {
            return false;
        }
        boolean useFluid = this.controller.hasSelectedFluid();
        boolean usePinnedItem = this.controller.hasSelectedItem();
        if (!useFluid && !usePinnedItem && !this.screen.canUseToolSlotShapeSource()) {
            return false;
        }
        ShapeBuildTypes.Input input = resolveCurrentShapeBuildInput(null, true);
        if (input == null) {
            return false;
        }
        Minecraft mc = this.screen.getMinecraft();
        if (mc == null) {
            return false;
        }
        Vec3 rayOrigin = mc.gameRenderer.getMainCamera().getPosition();
        Vec3 rayDir = this.screen.computeCursorRayDirection();
        List<BlockHitResult> hits = buildShapePlacementHits(input, this.shapeFillMode);
        BlockHitResult templateHit = resolveShapeTemplateHit(input);
        clearShapeBuildSession();
        if (useFluid) {
            for (BlockHitResult shapedHit : hits) {
                this.controller.placeSelectedFluid(shapedHit, forcePlace, rayOrigin, rayDir);
            }
        } else {
            this.controller.placeSelectedBatch(hits, templateHit, forcePlace, rayOrigin, rayDir, true);
        }
        if (!useFluid) {
            List<BlockPos> positions = new ArrayList<>(hits.size());
            for (BlockHitResult shapedHit : hits) {
                positions.add(shapedHit.getBlockPos().immutable());
            }
            recordPlacementBatchForUndo(
                    usePinnedItem ? InteractionTypes.PlacementReplayKind.PIN_ITEM : InteractionTypes.PlacementReplayKind.TOOL_SLOT,
                    usePinnedItem ? this.controller.getSelectedItemId() : "",
                    usePinnedItem ? -1 : this.screen.getSelectedToolSlot(),
                    input.placementFace(),
                    positions);
        
            // Register pending ghosts for visual feedback while waiting for server confirmation
            BlockState pendingState = resolvePendingGhostBlockState();
            PlacementAnimationRenderer.addPendingBatch(positions, pendingState);
        }
        return true;
    }

    // ===== Ghost preview =====

    public ShapeDataRecords.GhostPreview getShapeGhostPreview() {
        if (this.screen.isQuickBuildRangeDestroyMode()) {
            if (this.controller.getBuildShape() == BuildShape.BLOCK) {
                BlockHitResult hit = this.screen.pickBlockHit();
                if (hit == null) {
                    return ShapeDataRecords.GhostPreview.EMPTY;
                }
                RangeDestroyPreview preview = buildRangeDestroyPreview(List.of(hit.getBlockPos().immutable()));
                return preview.isEmpty()
                        ? ShapeDataRecords.GhostPreview.EMPTY
                        : new ShapeDataRecords.GhostPreview(preview.breakableBlocks(), true, true, preview.emptyBlocks());
            }
            ShapeBuildTypes.Input input = resolveCurrentShapeBuildInput(this.screen.pickBlockHit(), false);
            if (input == null) {
                return ShapeDataRecords.GhostPreview.EMPTY;
            }
            RangeDestroyPreview preview = buildRangeDestroyPreview(input);
            if (preview.isEmpty()) {
                return ShapeDataRecords.GhostPreview.EMPTY;
            }
            boolean ready = this.shapeBuildSession != null && this.shapeBuildSession.phase() == ShapeBuildTypes.Phase.READY_CONFIRM;
            return new ShapeDataRecords.GhostPreview(preview.breakableBlocks(), ready, true, preview.emptyBlocks());
        }
        // Ultimine (chain-mining) ghost — FTB Ultimine style
        // Always returns a preview when the ultimine panel is open, even when the block
        // list is empty; the renderer handles empty blocks gracefully.
        if (this.screen.isUltimineOpen()) {
            List<BlockPos> ultimineBlocks = this.screen.collectUltiminePreviewBlocks();
            return new ShapeDataRecords.GhostPreview(ultimineBlocks, false, true, List.of(), true);
        }
        if (this.controller.getBuildShape() == BuildShape.BLOCK) {
            return ShapeDataRecords.GhostPreview.EMPTY;
        }
        if (!this.controller.hasSelectedItem() && !this.controller.hasSelectedFluid() && !this.screen.canUseToolSlotShapeSource()) {
            return ShapeDataRecords.GhostPreview.EMPTY;
        }
        ShapeBuildTypes.Input input = resolveCurrentShapeBuildInput(this.screen.pickBlockHit(), false);
        if (input == null) {
            return ShapeDataRecords.GhostPreview.EMPTY;
        }
        List<BlockPos> blocks = filterOccupiedReadyShapeTargets(input, ShapeGeometryUtil.buildShapePositions(input, this.shapeFillMode));
        if (blocks.isEmpty()) {
            return ShapeDataRecords.GhostPreview.EMPTY;
        }
        boolean ready = this.shapeBuildSession != null && this.shapeBuildSession.phase() == ShapeBuildTypes.Phase.READY_CONFIRM;
        return new ShapeDataRecords.GhostPreview(blocks, ready);
    }

    // ===== Input life cycle =====

    public void updateAltShapeWheelLifecycle() {
        // The old Alt-held radial selector was a pre-refactor affordance and is now
        // intentionally dormant. Shape selection lives in the top bar, while this
        // controller continues to own the actual build-session geometry.
        this.altShapeMenuHeld = false;
    }

    // ===== Undo / Redo =====

    public boolean undoLastPlacementBatch() {
        if (this.shapeUndoStack.isEmpty()) {
            return false;
        }
        ShapeDataRecords.HistoryBatch batch = this.shapeUndoStack.remove(this.shapeUndoStack.size() - 1);
        List<BlockPos> positions = batch.positions();
        for (int i = positions.size() - 1; i >= 0; i--) {
            this.controller.breakPlaced(positions.get(i), batch.face(), true);
        }
        this.shapeRedoStack.add(batch);
        if (this.shapeRedoStack.size() > SHAPE_HISTORY_LIMIT) {
            this.shapeRedoStack.remove(0);
        }
        return true;
    }

    public boolean redoLastPlacementBatch() {
        if (this.shapeRedoStack.isEmpty()) {
            return false;
        }
        Minecraft mc = this.screen.getMinecraft();
        if (mc == null) {
            return false;
        }
        int idx = this.shapeRedoStack.size() - 1;
        ShapeDataRecords.HistoryBatch batch = this.shapeRedoStack.get(idx);
        if (batch.replayKind() == InteractionTypes.PlacementReplayKind.PIN_ITEM) {
            if (!this.controller.hasSelectedItem() || !batch.itemId().equals(this.controller.getSelectedItemId())) {
                return false;
            }
        } else {
            if (mc.player == null) {
                return false;
            }
            this.controller.clearPlacementSelectionPreserveMode();
            this.screen.setSelectedToolSlot(batch.toolSlot());
        }
        this.shapeRedoStack.remove(idx);
        Vec3 rayOrigin = mc.gameRenderer.getMainCamera().getPosition();
        Vec3 rayDir = this.screen.computeCursorRayDirection();
        List<BlockHitResult> hits = new ArrayList<>(batch.positions().size());
        for (BlockPos pos : batch.positions()) {
            hits.add(ShapeGeometryUtil.createShapePlacementHit(pos, batch.face()));
        }
        this.controller.placeSelectedBatch(hits, false, rayOrigin, rayDir, false);
        this.shapeUndoStack.add(batch);
        if (this.shapeUndoStack.size() > SHAPE_HISTORY_LIMIT) {
            this.shapeUndoStack.remove(0);
        }
        return true;
    }

    // ===== Dimension / Nudge adjustments =====

    public boolean adjustShapeDimensionNudge(int delta, boolean adjustSecondaryAxis, boolean adjustHeight) {
        if (delta == 0 || this.shapeBuildSession == null) {
            return false;
        }
        if (adjustHeight && canAdjustShapeHeight(this.shapeBuildSession.shape())) {
            return adjustShapeHeightNudge(delta);
        }
        return adjustShapeFootprintNudge(delta, adjustSecondaryAxis);
    }

    private boolean adjustShapeFootprintNudge(int delta, boolean secondaryAxis) {
        if (delta == 0 || this.shapeBuildSession == null) {
            return false;
        }
        if (this.shapeBuildSession.shape() == BuildShape.BLOCK) {
            return false;
        }
        if (this.shapeBuildSession.phase() != ShapeBuildTypes.Phase.NEED_SECOND_POINT
                && this.shapeBuildSession.phase() != ShapeBuildTypes.Phase.NEED_THIRD_POINT
                && this.shapeBuildSession.phase() != ShapeBuildTypes.Phase.READY_CONFIRM) {
            return false;
        }
        if (secondaryAxis) {
            this.shapeFootprintNudgeB = ShapeGeometryUtil.clampShapeOffset(this.shapeFootprintNudgeB + delta);
        } else {
            this.shapeFootprintNudgeA = ShapeGeometryUtil.clampShapeOffset(this.shapeFootprintNudgeA + delta);
        }
        return true;
    }

    public boolean canAdjustCurrentShapeHeight() {
        return this.shapeBuildSession != null
                && this.shapeBuildSession.shape() == this.controller.getBuildShape()
                && canAdjustShapeHeight(this.shapeBuildSession.shape());
    }

    private static boolean canAdjustShapeHeight(BuildShape shape) {
        return shape == BuildShape.WALL || shape == BuildShape.BOX;
    }

    public boolean adjustShapeHeightNudge(int delta) {
        if (delta == 0 || this.shapeBuildSession == null || !canAdjustShapeHeight(this.shapeBuildSession.shape())) {
            return false;
        }
        if (this.shapeBuildSession.shape() == BuildShape.BOX
                && this.shapeBuildSession.phase() != ShapeBuildTypes.Phase.NEED_THIRD_POINT
                && this.shapeBuildSession.phase() != ShapeBuildTypes.Phase.READY_CONFIRM) {
            return false;
        }
        if (this.shapeBuildSession.shape() == BuildShape.WALL
                && this.shapeBuildSession.phase() != ShapeBuildTypes.Phase.READY_CONFIRM) {
            return false;
        }
        int nextOffset = ShapeGeometryUtil.clampShapeOffset(this.shapeBuildSession.boxHeightOffset() + delta);
        this.shapeBuildSession = new ShapeBuildTypes.Session(
                this.shapeBuildSession.shape(),
                this.shapeBuildSession.planeFace(),
                this.shapeBuildSession.placementFace(),
                this.shapeBuildSession.pointA(),
                this.shapeBuildSession.pointB(),
                this.shapeBuildSession.phase(),
                nextOffset,
                this.shapeBuildSession.boxHeightMouseBaseY());
        return true;
    }

    public boolean handleShapeHeightMouseScrolled(double scrollY) {
        if (scrollY == 0.0D || !canAdjustCurrentShapeHeight()) {
            return false;
        }
        int delta = scrollY > 0.0D ? 1 : -1;
        if (isAltDown()) {
            delta *= 4;
        }
        return adjustShapeHeightNudge(delta);
    }

    // ===== Label / status helpers =====

    public String fillModeLabel(ShapeFillMode mode) {
        if (mode == null) {
            return this.screen.text("screen.rtsbuilding.fill.fill");
        }
        return switch (mode) {
            case FILL -> this.screen.text("screen.rtsbuilding.fill.fill");
            case HOLLOW -> this.screen.text("screen.rtsbuilding.fill.hollow");
            case SKELETON -> this.screen.text("screen.rtsbuilding.fill.skeleton");
        };
    }

    public static String shapeDimensionLabel(BuildShape shape) {
        if (shape == null) {
            return "2D";
        }
        return switch (shape) {
            case LINE -> "1D";
            case BOX -> "3D";
            default -> "2D";
        };
    }

    public String currentShapeSizeText() {
        BuildShape shape = this.controller.getBuildShape();
        if (shape == BuildShape.BLOCK) {
            return "1*1*1";
        }
        ShapeBuildTypes.Input input = resolveCurrentShapeBuildInput(this.screen.pickBlockHit(), false);
        if (input == null) {
            return "0*0*0";
        }
        List<BlockPos> blocks = ShapeGeometryUtil.buildShapePositions(input, this.shapeFillMode);
        if (blocks.isEmpty()) {
            return "0*0*0";
        }
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (BlockPos pos : blocks) {
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        int sx = (maxX - minX) + 1;
        int sy = (maxY - minY) + 1;
        int sz = (maxZ - minZ) + 1;
        return sx + "*" + sy + "*" + sz;
    }

    public String currentShapeCostText() {
        BuildShape shape = this.controller.getBuildShape();
        if (shape == BuildShape.BLOCK) {
            return "1";
        }
        ShapeBuildTypes.Input input = resolveCurrentShapeBuildInput(this.screen.pickBlockHit(), false);
        if (input == null) {
            return "0";
        }
        if (this.screen.isQuickBuildRangeDestroyMode()) {
            return Integer.toString(buildRangeDestroyTargets(input).size());
        }
        List<BlockPos> blocks = filterOccupiedReadyShapeTargets(input, ShapeGeometryUtil.buildShapePositions(input, this.shapeFillMode));
        return Integer.toString(blocks.size());
    }

    public String pendingShapeStatusText() {
        if (!this.screen.isQuickBuildOpen()) {
            return "";
        }
        BuildShape currentShape = this.controller.getBuildShape();
        boolean destroyMode = this.screen.isQuickBuildRangeDestroyMode();
        if (currentShape == BuildShape.BLOCK) {
            return this.screen.text(destroyMode
                    ? "screen.rtsbuilding.shape_status.destroy"
                    : "screen.rtsbuilding.shape_status.place");
        }
        if (this.shapeBuildSession == null || this.shapeBuildSession.shape() != currentShape) {
            return this.screen.text(destroyMode
                    ? "screen.rtsbuilding.shape_status.destroy_step_a"
                    : "screen.rtsbuilding.shape_status.step_a");
        }
        return switch (this.shapeBuildSession.phase()) {
            case NEED_SECOND_POINT -> {
                BlockPos a = this.shapeBuildSession.pointA();
                yield this.screen.text(destroyMode
                        ? "screen.rtsbuilding.shape_status.destroy_step_b"
                        : "screen.rtsbuilding.shape_status.step_b", a.getX(), a.getY(), a.getZ());
            }
            case NEED_THIRD_POINT -> this.screen.text(destroyMode
                    ? "screen.rtsbuilding.shape_status.destroy_step_height"
                    : "screen.rtsbuilding.shape_status.step_height");
            case READY_CONFIRM -> currentShape == BuildShape.WALL
                    ? this.screen.text(destroyMode
                            ? "screen.rtsbuilding.shape_status.destroy_confirm_wall"
                            : "screen.rtsbuilding.shape_status.confirm_wall")
                    : this.screen.text(destroyMode
                            ? "screen.rtsbuilding.shape_status.destroy_confirm"
                            : "screen.rtsbuilding.shape_status.confirm");
        };
    }

    public String shapeLabel(BuildShape shape) {
        if (shape == null) {
            return this.screen.text("screen.rtsbuilding.shape.block");
        }
        return switch (shape) {
            case BLOCK -> this.screen.text("screen.rtsbuilding.shape.block");
            case LINE -> this.screen.text("screen.rtsbuilding.shape.line");
            case SQUARE -> this.screen.text("screen.rtsbuilding.shape.square");
            case WALL -> this.screen.text("screen.rtsbuilding.shape.wall");
            case CIRCLE -> this.screen.text("screen.rtsbuilding.shape.circle");
            case BOX -> this.screen.text("screen.rtsbuilding.shape.box");
        };
    }

    // ===== Internal helpers =====

    /**
     * Resolves the block state to use for pending ghost rendering at placement confirmation time.
     */
    private BlockState resolvePendingGhostBlockState() {
        Minecraft mc = this.screen.getMinecraft();
        if (this.controller.hasSelectedItem()) {
            ItemStack preview = this.controller.getSelectedItemPreview();
            if (preview.getItem() instanceof BlockItem blockItem) {
                return blockItem.getBlock().defaultBlockState();
            }
        }
        if (mc != null && mc.player != null) {
            ItemStack mainHand = mc.player.getMainHandItem();
            if (mainHand.getItem() instanceof BlockItem blockItem) {
                return blockItem.getBlock().defaultBlockState();
            }
        }
        return null;
    }

    private ShapeBuildTypes.Input resolveCurrentShapeBuildInput(BlockHitResult cursorHit, boolean requireReady) {
        ShapeBuildTypes.Session session = this.shapeBuildSession;
        if (session == null || session.shape() != this.controller.getBuildShape()) {
            return null;
        }
        if (requireReady && session.phase() != ShapeBuildTypes.Phase.READY_CONFIRM) {
            return null;
        }
        BlockPos pointA = session.pointA();
        if (pointA == null) {
            return null;
        }
        if (session.phase() == ShapeBuildTypes.Phase.NEED_SECOND_POINT) {
            if (requireReady) {
                return null;
            }
            BlockPos pointB = resolveShapePlanePoint(session, cursorHit);
            pointB = applyShapeFootprintNudges(session.shape(), session.planeFace(), pointA, pointB);
            return new ShapeBuildTypes.Input(session.shape(), session.planeFace(), session.placementFace(), pointA, pointB, 0);
        }
        BlockPos pointB = session.pointB();
        if (pointB == null) {
            return null;
        }
        if (session.phase() == ShapeBuildTypes.Phase.NEED_THIRD_POINT) {
            if (requireReady) {
                return null;
            }
            pointB = applyShapeFootprintNudges(session.shape(), session.planeFace(), pointA, pointB);
            return new ShapeBuildTypes.Input(session.shape(), session.planeFace(), session.placementFace(), pointA, pointB, resolveBoxHeightOffset(session));
        }
        pointB = applyShapeFootprintNudges(session.shape(), session.planeFace(), pointA, pointB);
        return new ShapeBuildTypes.Input(session.shape(), session.planeFace(), session.placementFace(), pointA, pointB, resolveBoxHeightOffset(session));
    }

    private int resolveBoxHeightOffset(ShapeBuildTypes.Session session) {
        if (session == null) {
            return 0;
        }
        return session.boxHeightOffset();
    }

    private BlockPos resolveShapePlanePoint(ShapeBuildTypes.Session session, BlockHitResult cursorHit) {
        if (session == null) {
            return cursorHit != null ? cursorHit.getBlockPos() : null;
        }
        BlockPos pointA = session.pointA();
        if (pointA == null) {
            return cursorHit != null ? cursorHit.getBlockPos() : null;
        }
        BuildShape shape = session.shape();
        if (shape == null || shape == BuildShape.BLOCK) {
            return cursorHit != null ? cursorHit.getBlockPos() : pointA;
        }
        Direction planeFace = session.planeFace();
        if (shape == BuildShape.LINE
                || shape == BuildShape.SQUARE
                || shape == BuildShape.WALL
                || shape == BuildShape.BOX) {
            planeFace = Direction.UP;
        }
        if (planeFace == null) {
            return cursorHit != null ? cursorHit.getBlockPos() : pointA;
        }
        Vec3 planeHit = intersectCursorRayWithShapePlane(pointA, planeFace);
        if (planeHit == null && cursorHit != null) {
            planeHit = cursorHit.getLocation();
        }
        if (planeHit == null) {
            return pointA;
        }
        return blockPosFromPlaneHit(pointA, planeFace, planeHit);
    }

    private Vec3 intersectCursorRayWithShapePlane(BlockPos anchor, Direction face) {
        Minecraft mc = this.screen.getMinecraft();
        if (anchor == null || face == null || mc == null || mc.gameRenderer == null) {
            return null;
        }
        Vec3 rayOrigin = mc.gameRenderer.getMainCamera().getPosition();
        Vec3 rayDir = this.screen.computeCursorRayDirection();
        if (rayOrigin == null || rayDir == null) {
            return null;
        }
        Vec3 planeAnchor = Vec3.atCenterOf(anchor);
        double planeCoord = switch (face.getAxis()) {
            case X -> planeAnchor.x;
            case Y -> planeAnchor.y;
            case Z -> planeAnchor.z;
        };
        double originCoord = switch (face.getAxis()) {
            case X -> rayOrigin.x;
            case Y -> rayOrigin.y;
            case Z -> rayOrigin.z;
        };
        double dirCoord = switch (face.getAxis()) {
            case X -> rayDir.x;
            case Y -> rayDir.y;
            case Z -> rayDir.z;
        };
        if (Math.abs(dirCoord) < 1.0E-5D) {
            return null;
        }
        double t = (planeCoord - originCoord) / dirCoord;
        if (t <= 0.0D || t > 128.0D) {
            return null;
        }
        return rayOrigin.add(rayDir.scale(t));
    }

    private static BlockPos blockPosFromPlaneHit(BlockPos anchor, Direction face, Vec3 hitVec) {
        if (anchor == null || face == null || hitVec == null) {
            return anchor;
        }
        return switch (face.getAxis()) {
            case X -> new BlockPos(anchor.getX(), Mth.floor(hitVec.y), Mth.floor(hitVec.z));
            case Y -> new BlockPos(Mth.floor(hitVec.x), anchor.getY(), Mth.floor(hitVec.z));
            case Z -> new BlockPos(Mth.floor(hitVec.x), Mth.floor(hitVec.y), anchor.getZ());
        };
    }

    private BlockPos applyShapeFootprintNudges(BuildShape shape, Direction face, BlockPos pointA, BlockPos pointB) {
        if (pointA == null || pointB == null) {
            return pointB;
        }
        if (this.shapeFootprintNudgeA == 0 && this.shapeFootprintNudgeB == 0) {
            return pointB;
        }
        if (shape == null || shape == BuildShape.BLOCK) {
            return pointB;
        }
        Direction axisA;
        Direction axisB;
        if (shape == BuildShape.BOX) {
            axisA = Direction.EAST;
            axisB = Direction.SOUTH;
        } else {
            Direction[] axes = ShapeGeometryUtil.resolveShapePlaneAxes(shape, face);
            if (axes.length < 2) {
                return pointB;
            }
            axisA = axes[0];
            axisB = axes[1];
        }
        int dx = pointB.getX() - pointA.getX();
        int dy = pointB.getY() - pointA.getY();
        int dz = pointB.getZ() - pointA.getZ();
        int nextA = ShapeGeometryUtil.clampShapeOffset(ShapeGeometryUtil.dotDelta(dx, dy, dz, axisA) + this.shapeFootprintNudgeA);
        int nextB = ShapeGeometryUtil.clampShapeOffset(ShapeGeometryUtil.dotDelta(dx, dy, dz, axisB) + this.shapeFootprintNudgeB);
        return ShapeGeometryUtil.offsetPos(pointA, axisA, nextA, axisB, nextB);
    }

    private List<BlockPos> buildRangeDestroyTargets(ShapeBuildTypes.Input input) {
        if (input == null) {
            return List.of();
        }
        return filterBreakableRangeDestroyTargets(ShapeGeometryUtil.buildShapePositions(input, this.shapeFillMode));
    }

    private RangeDestroyPreview buildRangeDestroyPreview(ShapeBuildTypes.Input input) {
        if (input == null) {
            return RangeDestroyPreview.EMPTY;
        }
        return buildRangeDestroyPreview(ShapeGeometryUtil.buildShapePositions(input, this.shapeFillMode));
    }

    private RangeDestroyPreview buildRangeDestroyPreview(List<BlockPos> targets) {
        if (targets == null || targets.isEmpty()) {
            return RangeDestroyPreview.EMPTY;
        }
        LinkedHashSet<BlockPos> breakable = new LinkedHashSet<>(targets.size());
        LinkedHashSet<BlockPos> empty = new LinkedHashSet<>();
        Minecraft mc = this.screen.getMinecraft();
        if (mc == null || mc.level == null) {
            for (BlockPos pos : targets) {
                if (pos != null) {
                    breakable.add(pos.immutable());
                }
            }
            return new RangeDestroyPreview(new ArrayList<>(breakable), List.of());
        }
        for (BlockPos pos : targets) {
            if (pos == null) {
                continue;
            }
            BlockState state = mc.level.getBlockState(pos);
            if (state.isAir()) {
                empty.add(pos.immutable());
                continue;
            }
            if (state.getDestroySpeed(mc.level, pos) < 0.0F) {
                continue;
            }
            breakable.add(pos.immutable());
        }
        return new RangeDestroyPreview(new ArrayList<>(breakable), new ArrayList<>(empty));
    }

    private List<BlockPos> filterBreakableRangeDestroyTargets(List<BlockPos> targets) {
        if (targets == null || targets.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<BlockPos> unique = new LinkedHashSet<>(targets.size());
        Minecraft mc = this.screen.getMinecraft();
        if (mc == null || mc.level == null) {
            for (BlockPos pos : targets) {
                if (pos != null) {
                    unique.add(pos.immutable());
                }
            }
            return new ArrayList<>(unique);
        }
        for (BlockPos pos : targets) {
            if (pos == null) {
                continue;
            }
            BlockState state = mc.level.getBlockState(pos);
            if (state.isAir() || state.getDestroySpeed(mc.level, pos) < 0.0F) {
                continue;
            }
            unique.add(pos.immutable());
        }
        return new ArrayList<>(unique);
    }

    private record RangeDestroyPreview(List<BlockPos> breakableBlocks, List<BlockPos> emptyBlocks) {
        private static final RangeDestroyPreview EMPTY = new RangeDestroyPreview(List.of(), List.of());

        private boolean isEmpty() {
            return this.breakableBlocks.isEmpty() && this.emptyBlocks.isEmpty();
        }
    }

    private List<BlockHitResult> buildShapePlacementHits(ShapeBuildTypes.Input input, ShapeFillMode fillMode) {
        List<BlockPos> positions = filterOccupiedReadyShapeTargets(input, ShapeGeometryUtil.buildShapePositions(input, fillMode));
        List<BlockHitResult> hits = new ArrayList<>(positions.size());
        for (BlockPos pos : positions) {
            hits.add(ShapeGeometryUtil.createShapePlacementHit(pos, input.placementFace()));
        }
        return hits;
    }

    private BlockHitResult resolveShapeTemplateHit(ShapeBuildTypes.Input input) {
        if (this.shapeTemplateHit != null) {
            return this.shapeTemplateHit;
        }
        if (input == null || input.pointA() == null || input.placementFace() == null) {
            return null;
        }
        return ShapeGeometryUtil.createShapePlacementHit(input.pointA(), input.placementFace());
    }

    private List<BlockPos> filterOccupiedReadyShapeTargets(ShapeBuildTypes.Input input, List<BlockPos> targets) {
        if (targets == null || targets.isEmpty()) {
            return List.of();
        }
        if (input == null || input.placementFace() == null) {
            return targets;
        }
        boolean strictEmptyLock = shouldSkipOccupiedReadyShapeTargets(input);
        boolean uniformPlacement = shouldUseUniformShapePlanePlacement(input);
        LinkedHashSet<BlockPos> resolved = new LinkedHashSet<>(targets.size());
        Minecraft mc = this.screen.getMinecraft();
        if (mc == null || mc.level == null) {
            for (BlockPos clickedPos : targets) {
                if (clickedPos == null) {
                    continue;
                }
                BlockPos placePos = uniformPlacement
                        ? resolveUniformShapePlacementTargetPos(input, clickedPos)
                        : resolvePlacementTargetPos(clickedPos, input.placementFace());
                if (placePos != null) {
                    resolved.add(placePos.immutable());
                }
            }
            return new ArrayList<>(resolved);
        }
        for (BlockPos clickedPos : targets) {
            if (clickedPos == null) {
                continue;
            }
            BlockPos placePos = uniformPlacement
                    ? resolveUniformShapePlacementTargetPos(input, clickedPos)
                    : resolvePlacementTargetPos(clickedPos, input.placementFace());
            if (placePos == null) {
                continue;
            }
            if (strictEmptyLock
                    && mc.level.hasChunkAt(placePos)
                    && !mc.level.getBlockState(placePos).canBeReplaced()) {
                continue;
            }
            resolved.add(placePos.immutable());
        }
        return new ArrayList<>(resolved);
    }

    private boolean shouldUseUniformShapePlanePlacement(ShapeBuildTypes.Input input) {
        if (input == null || input.placementFace() == null) {
            return false;
        }
        return switch (input.shape()) {
            case LINE, SQUARE, WALL, BOX -> true;
            default -> false;
        };
    }

    private BlockPos resolvePlacementTargetPos(BlockPos clickedPos, Direction face) {
        Minecraft mc = this.screen.getMinecraft();
        if (clickedPos == null || face == null || mc == null || mc.level == null) {
            return null;
        }
        if (!mc.level.hasChunkAt(clickedPos)) {
            return clickedPos;
        }
        return mc.level.getBlockState(clickedPos).canBeReplaced() ? clickedPos : clickedPos.relative(face);
    }

    private BlockPos resolveUniformShapePlacementTargetPos(ShapeBuildTypes.Input input, BlockPos clickedPos) {
        if (input == null || clickedPos == null) {
            return null;
        }
        BlockPos anchor = input.pointA();
        Direction face = input.placementFace();
        if (anchor == null || face == null) {
            return clickedPos;
        }
        BlockPos anchorPlaced = resolvePlacementTargetPos(anchor, face);
        if (anchorPlaced == null) {
            return clickedPos;
        }
        return clickedPos.offset(
                anchorPlaced.getX() - anchor.getX(),
                anchorPlaced.getY() - anchor.getY(),
                anchorPlaced.getZ() - anchor.getZ());
    }

    private boolean shouldSkipOccupiedReadyShapeTargets(ShapeBuildTypes.Input input) {
        if (input == null || input.shape() == BuildShape.BLOCK) {
            return false;
        }
        if (this.shapeBuildSession == null || this.shapeBuildSession.phase() != ShapeBuildTypes.Phase.READY_CONFIRM) {
            return false;
        }
        if (this.controller.hasSelectedFluid()) {
            return false;
        }
        if (this.controller.hasSelectedItem()) {
            String itemId = this.controller.getSelectedItemId();
            if (itemId == null || itemId.isBlank()) {
                return false;
            }
            ResourceLocation key = ResourceLocation.tryParse(itemId);
            if (key == null || !BuiltInRegistries.ITEM.containsKey(key)) {
                return false;
            }
            return BuiltInRegistries.ITEM.get(key) instanceof BlockItem;
        }
        return this.screen.canUseToolSlotShapeSource();
    }

    // ===== Undo record helpers =====

    public void recordSinglePlacementForUndo(BlockHitResult hit, InteractionTypes.PlacementReplayKind replayKind, String itemId, int toolSlot) {
        if (hit == null) {
            return;
        }
        recordPlacementBatchForUndo(replayKind, itemId, toolSlot, hit.getDirection(), List.of(hit.getBlockPos().immutable()));
    }

    private void recordPlacementBatchForUndo(InteractionTypes.PlacementReplayKind replayKind, String itemId, int toolSlot, Direction face, List<BlockPos> positions) {
        if (positions == null || positions.isEmpty()) {
            return;
        }
        if (replayKind == InteractionTypes.PlacementReplayKind.PIN_ITEM && (itemId == null || itemId.isBlank())) {
            return;
        }
        ShapeDataRecords.HistoryBatch batch = new ShapeDataRecords.HistoryBatch(
                replayKind,
                itemId == null ? "" : itemId,
                Mth.clamp(toolSlot, 0, 8),
                face,
                List.copyOf(positions));
        this.shapeUndoStack.add(batch);
        if (this.shapeUndoStack.size() > SHAPE_HISTORY_LIMIT) {
            this.shapeUndoStack.remove(0);
        }
        this.shapeRedoStack.clear();
    }

    // ===== Alt shape wheel =====

    private boolean shouldOpenAltShapeWheel(double mouseY) {
        return this.controller.isEnabled()
                && !this.screen.isShapeWheelOpen()
                && !this.screen.isInteractionWheelOpen()
                && !this.screen.isGuideOpen()
                && this.screen.isWorldArea(this.screen.getCurrentMouseX(), mouseY);
    }

    private boolean isAltDown() {
        Minecraft mc = this.screen.getMinecraft();
        if (mc == null) {
            return false;
        }
        long window = mc.getWindow().getWindow();
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_ALT) == GLFW.GLFW_PRESS;
    }

    private double currentMouseX() {
        return this.screen.getCurrentMouseX();
    }

    private double currentMouseY() {
        return this.screen.getCurrentMouseY();
    }
}
