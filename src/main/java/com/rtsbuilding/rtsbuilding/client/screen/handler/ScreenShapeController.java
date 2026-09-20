package com.rtsbuilding.rtsbuilding.client.screen.handler;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.client.bootstrap.ClientKeyMappings;
import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.rendering.animation.PlacementAnimationRenderer;
import com.rtsbuilding.rtsbuilding.client.screen.culling.RtsCullingBox;
import com.rtsbuilding.rtsbuilding.client.screen.interaction.InteractionTypes;
import com.rtsbuilding.rtsbuilding.client.screen.quickbuild.BuildShape;
import com.rtsbuilding.rtsbuilding.client.screen.shape.ConfirmedDestroyPreviewState;
import com.rtsbuilding.rtsbuilding.client.screen.shape.ShapeBuildTypes;
import com.rtsbuilding.rtsbuilding.client.screen.shape.ShapeConfirmedDestroyWorkArea;
import com.rtsbuilding.rtsbuilding.client.screen.shape.ShapeDataRecords;
import com.rtsbuilding.rtsbuilding.client.screen.shape.ShapeDestroyTargetClassifier;
import com.rtsbuilding.rtsbuilding.client.screen.shape.ShapeGenerationResult;
import com.rtsbuilding.rtsbuilding.client.screen.shape.ShapeGenerationStatus;
import com.rtsbuilding.rtsbuilding.client.screen.shape.ShapeGhostPreviewProvider;
import com.rtsbuilding.rtsbuilding.client.screen.shape.ShapeGeometryUtil;
import com.rtsbuilding.rtsbuilding.client.screen.shape.ShapeModeState;
import com.rtsbuilding.rtsbuilding.client.screen.shape.ShapePlacementTargetResolver;
import com.rtsbuilding.rtsbuilding.client.screen.shape.ShapeSelectionBoxController;
import com.rtsbuilding.rtsbuilding.client.screen.shape.ShapeSelectionSession;
import com.rtsbuilding.rtsbuilding.client.screen.shape.ShapeSelectionTextPresenter;
import com.rtsbuilding.rtsbuilding.client.screen.shape.ShapeWorldOperationPlanner;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.common.diagnostics.RtsTraceInputKind;
import com.rtsbuilding.rtsbuilding.common.shape.model.ShapeFillMode;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Set;

import static com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreenConstants.SHAPE_ROTATE_STEP_DEGREES;

/**
 * 形状交互的薄协调层。
 *
 * <p>会话、范围框、几何计划、预览和已确认破坏工作区分别拥有自己的状态；本类只串联
 * 点击、确认和 UI 查询，避免把几何算法与世界副作用重新堆回控制器。</p>
 */
public final class ScreenShapeController implements ShapeGhostPreviewProvider.Runtime {
    private BuilderScreen screen;
    private ClientRtsController controller;
    private final ShapeSelectionSession selectionSession = new ShapeSelectionSession();
    private final ShapeSelectionBoxController selectionBox = new ShapeSelectionBoxController();
    private final ShapeModeState modeState = new ShapeModeState();
    private final ShapeConfirmedDestroyWorkArea confirmedDestroyWorkArea = new ShapeConfirmedDestroyWorkArea();
    private final ShapeGhostPreviewProvider ghostPreviews = new ShapeGhostPreviewProvider();
    private final PlacementHistoryManager placementHistory = new PlacementHistoryManager();
    private final ShapeWorldOperationPlanner worldOperations = new ShapeWorldOperationPlanner();

    public void init(BuilderScreen screen, ClientRtsController controller) {
        this.screen = screen;
        this.controller = controller;
        this.selectionBox.init(screen, this.selectionSession::current, this.selectionSession::replace);
        this.selectionSession.init(screen, this.selectionBox);
        this.placementHistory.init(screen, controller);
        this.worldOperations.init(screen, controller, this.modeState, this.selectionSession, this.selectionBox);
        this.confirmedDestroyWorkArea.init(screen, controller, this.worldOperations::filterToBounds);
        this.ghostPreviews.init(screen, controller, this.confirmedDestroyWorkArea.state(), this);
    }

    public ShapeFillMode getShapeFillMode() { return this.modeState.activeFillMode(); }
    public void setShapeFillMode(ShapeFillMode mode) { this.modeState.setActiveFillMode(mode); }
    public ShapeFillMode getBuildShapeFillMode() { return this.modeState.buildFillMode(); }
    public void setBuildShapeFillMode(ShapeFillMode mode) { this.modeState.setBuildFillMode(mode); }
    public ShapeFillMode getDestroyShapeFillMode() { return this.modeState.destroyFillMode(); }
    public void setDestroyShapeFillMode(ShapeFillMode mode) { this.modeState.setDestroyFillMode(mode); }
    public boolean isLineConnected() { return this.modeState.activeLineConnected(); }
    public void setLineConnected(boolean connected) { this.modeState.setActiveLineConnected(connected); }
    public boolean isBuildLineConnected() { return this.modeState.buildLineConnected(); }
    public void setBuildLineConnected(boolean connected) { this.modeState.setBuildLineConnected(connected); }
    public boolean isDestroyLineConnected() { return this.modeState.destroyLineConnected(); }
    public void setDestroyLineConnected(boolean connected) { this.modeState.setDestroyLineConnected(connected); }
    public int getShapeRotateDegrees() { return this.modeState.activeRotateDegrees(); }
    public int getBuildRotateDegrees() { return this.modeState.buildRotateDegrees(); }
    public int getDestroyRotateDegrees() { return this.modeState.destroyRotateDegrees(); }
    public int getShapeUndoSize() { return this.placementHistory.getUndoSize(); }
    public int getShapeRedoSize() { return this.placementHistory.getRedoSize(); }

    public void clearShapeBuildSession() {
        this.selectionSession.clear();
        this.worldOperations.clear();
    }

    public void rotateShapeByStep(int step) {
        this.modeState.setActiveRotateDegrees(
                this.modeState.activeRotateDegrees() + step * SHAPE_ROTATE_STEP_DEGREES);
        this.screen.persistUiState();
    }

    public void rotateToDegrees(int degrees) { this.modeState.setActiveRotateDegrees(degrees); }
    public void setBuildRotateDegrees(int degrees) { this.modeState.setBuildRotateDegrees(degrees); }
    public void setDestroyRotateDegrees(int degrees) { this.modeState.setDestroyRotateDegrees(degrees); }
    public void rotateDestroyToDegrees(int degrees) { this.modeState.setDestroyRotateDegrees(degrees); }
    public void setShapeCursorY(double cursorY) { this.selectionSession.setCursorY(cursorY); }
    public ShapeBuildTypes.Session getShapeBuildSession() { return this.selectionSession.current(); }

    public void ensureFillModeForShape(BuildShape shape) {
        List<ShapeFillMode> modes = ShapeGeometryUtil.availableFillModes(shape);
        if (modes.isEmpty()) {
            this.modeState.setActiveFillMode(ShapeFillMode.FILL);
            this.screen.persistUiState();
        } else if (!modes.contains(this.modeState.activeFillMode())) {
            this.modeState.setActiveFillMode(modes.get(0));
            this.screen.persistUiState();
        }
    }

    /** 校验范围破坏模式的填充模式是否对指定形状合法。 */
    public void ensureDestroyFillModeForShape(BuildShape shape) {
        List<ShapeFillMode> modes = ShapeGeometryUtil.availableFillModes(shape);
        if (modes.isEmpty()) {
            this.modeState.setDestroyFillMode(ShapeFillMode.FILL);
            this.screen.persistUiState();
        } else if (!modes.contains(this.modeState.destroyFillMode())) {
            this.modeState.setDestroyFillMode(modes.get(0));
            this.screen.persistUiState();
        }
    }

    public boolean cycleShapeFillModeForCurrentShape(int step) {
        List<ShapeFillMode> modes = ShapeGeometryUtil.availableFillModes(this.controller.getBuildShape());
        if (modes.isEmpty()) return false;
        int current = modes.indexOf(this.modeState.activeFillMode());
        if (current < 0) {
            this.modeState.setActiveFillMode(modes.get(0));
            this.screen.persistUiState();
            return true;
        }
        this.modeState.setActiveFillMode(modes.get(Math.floorMod(current + step, modes.size())));
        this.screen.persistUiState();
        return true;
    }

    public boolean cycleDestroyShapeFillModeForCurrentShape(int step) {
        List<ShapeFillMode> modes = ShapeGeometryUtil.availableFillModes(this.controller.getBuildShape());
        if (modes.isEmpty()) return false;
        int current = modes.indexOf(this.modeState.destroyFillMode());
        if (current < 0) {
            this.modeState.setDestroyFillMode(modes.get(0));
            this.screen.persistUiState();
            return true;
        }
        this.modeState.setDestroyFillMode(modes.get(Math.floorMod(current + step, modes.size())));
        this.screen.persistUiState();
        return true;
    }

    public void switchToDestroy() { this.modeState.switchToDestroy(); }
    public void switchToBuild() { this.modeState.switchToBuild(); }
    public void applyBuildStateAsActive() { this.modeState.applyBuildState(); }
    public void applyDestroyStateAsActive() { this.modeState.applyDestroyState(); }

    public void placeWithShape(BlockHitResult hit, boolean forcePlace, Vec3 rayOrigin, Vec3 rayDir,
            double mouseY, boolean fluidPlacement, InteractionTypes.PlacementReplayKind replayKind,
            String replayItemId, int replayToolSlot) {
        if (hit == null) return;
        BuildShape shape = this.controller.getBuildShape();
        if (shape == BuildShape.BLOCK) {
            clearShapeBuildSession();
            if (fluidPlacement) {
                this.controller.placeSelectedFluid(hit, forcePlace, rayOrigin, rayDir);
            } else {
                this.controller.placeSelected(hit, forcePlace, rayOrigin, rayDir);
                BlockPos placePos = ShapePlacementTargetResolver.resolveClickedTarget(
                        hit.getBlockPos(), hit.getDirection(),
                        ShapePlacementTargetResolver.minecraftWorld(
                                this.screen.getMinecraft(), this.worldOperations.placementStack()));
                BlockState pendingState = this.worldOperations.pendingGhostState(placePos);
                if (placePos != null) {
                    PlacementAnimationRenderer.addPendingBatch(List.of(placePos.immutable()), pendingState);
                }
            }
            return;
        }
        advanceShapeSession(hit, rayDir, mouseY, shape);
        if (shouldSubmitShapeAfterSelection()) tryConfirmPendingShapeBuild(forcePlace);
    }

    public void selectRangeDestroyShape(
            BlockHitResult hit, double mouseY, Vec3 rayDir, RtsTraceInputKind inputKind) {
        if (hit == null) return;
        BuildShape shape = this.controller.getBuildShape();
        if (shape == BuildShape.BLOCK) {
            clearShapeBuildSession();
            List<BlockPos> breakable = ShapeDestroyTargetClassifier.breakableTargets(
                    List.of(hit.getBlockPos().immutable()), this.worldOperations::isBreakable);
            if (!breakable.isEmpty()) {
                List<BlockPos> bounded = this.worldOperations.filterToBounds(breakable);
                if (!bounded.isEmpty()) {
                    rememberConfirmedRangeDestroyPreview(
                            new ShapeDestroyTargetClassifier.Selection(bounded, List.of()));
                    this.controller.confirmShapeAreaDestroy(
                            bounded, this.screen.getSelectedToolSlot(), inputKind);
                }
            }
            return;
        }
        advanceShapeSession(hit, rayDir, mouseY, shape);
        if (shouldSubmitShapeAfterSelection()) tryConfirmPendingRangeDestroy(inputKind);
    }

    private boolean shouldSubmitShapeAfterSelection() {
        return this.selectionSession.shouldSubmitAfterSelection(Config.isKeyboardBatchConfirmEnabled());
    }

    private void advanceShapeSession(BlockHitResult hit, Vec3 rayDir, double mouseY, BuildShape shape) {
        this.confirmedDestroyWorkArea.clearChain();
        this.selectionSession.advance(hit, rayDir, mouseY, shape);
    }

    public boolean tryConfirmPendingRangeDestroy(RtsTraceInputKind inputKind) {
        if (!this.screen.isQuickBuildRangeDestroyMode()
                || this.controller.getBuildShape() == BuildShape.BLOCK) return false;
        ShapeBuildTypes.Input input = resolveCurrentShapeBuildInput(null, true);
        if (input == null) return false;
        ShapeGenerationResult generation = generationPlan(input);
        if (generation.status() == ShapeGenerationStatus.TOO_LARGE) return false;
        List<BlockPos> raw = generation.positions();
        List<BlockPos> breakable = ShapeDestroyTargetClassifier.breakableTargets(
                raw, this.worldOperations::isBreakable);
        List<BlockPos> boundedBreakable = this.worldOperations.filterToBounds(breakable);
        List<BlockPos> boundedEnvelope = this.worldOperations.filterToBounds(
                ShapeDestroyTargetClassifier.envelopeTargets(raw, boundedBreakable));
        clearShapeBuildSession();
        if (boundedBreakable.isEmpty()) return true;
        rememberConfirmedRangeDestroyPreview(
                new ShapeDestroyTargetClassifier.Selection(boundedBreakable, boundedEnvelope));
        this.controller.confirmShapeAreaDestroy(
                boundedBreakable, this.screen.getSelectedToolSlot(), inputKind);
        return true;
    }

    public boolean isAwaitingBatchPlaceConfirm() {
        return !this.screen.isQuickBuildRangeDestroyMode()
                && this.selectionSession.isAwaiting(this.controller.getBuildShape());
    }

    public boolean isAwaitingBatchDestroyConfirm() {
        return this.screen.isQuickBuildRangeDestroyMode()
                && !this.screen.isQuickBuildRangeDestroyChainMode()
                && this.selectionSession.isAwaiting(this.controller.getBuildShape());
    }

    public RtsCullingBox advancedRangeDestroyBox() { return this.selectionBox.box(); }
    public AABB advancedRangeDestroyRenderAabb() { return shapeSelectionRenderAabb(); }

    public AABB shapeSelectionRenderAabb() {
        ShapeBuildTypes.Session session = this.selectionSession.current();
        if (session == null || this.controller.getBuildShape() == BuildShape.BLOCK) return null;
        ShapeBuildTypes.Input input = resolveCurrentShapeBuildInput(this.screen.pickBlockHit(), false);
        if (input == null) return null;
        this.worldOperations.generate(input);
        return this.selectionBox.renderAabb(this.worldOperations.generatedBounds());
    }

    public Direction advancedRangeDestroyHoveredHandle() { return this.selectionBox.hoveredHandle(); }
    public Direction advancedRangeDestroyActiveHandle() { return this.selectionBox.activeHandle(); }
    public Set<Direction> advancedRangeDestroyAllowedHandleDirections() {
        return this.selectionBox.allowedDirections();
    }
    public void updateAdvancedRangeDestroyHover(Vec3 origin, Vec3 rayDirection, boolean enabled) {
        this.selectionBox.updateHover(origin, rayDirection, enabled);
    }
    public boolean clickAdvancedRangeDestroyHandle(Vec3 origin, Vec3 rayDirection) {
        return this.selectionBox.click(origin, rayDirection);
    }
    public boolean scrollAdvancedRangeDestroyHandle(double scrollY, boolean fast) {
        return this.selectionBox.scroll(scrollY, fast);
    }
    public boolean dragAdvancedRangeDestroyHandle(double dragX, double dragY, double axisX, double axisY) {
        return this.selectionBox.drag(dragX, dragY, axisX, axisY);
    }
    public boolean releaseAdvancedRangeDestroyHandleIfDragged() { return this.selectionBox.releaseIfDragged(); }
    public boolean nudgeCurrentShapeSelection(int dx, int dy, int dz) {
        return this.selectionBox.nudge(dx, dy, dz);
    }

    public boolean tryConfirmPendingShapeBuild(boolean forcePlace) {
        if (this.controller.getBuildShape() == BuildShape.BLOCK) return false;
        boolean useFluid = this.controller.hasSelectedFluid();
        boolean usePinnedItem = this.controller.hasSelectedItem();
        if (!useFluid && !usePinnedItem && !this.screen.canUseToolSlotShapeSource()) return false;
        ShapeBuildTypes.Input input = resolveCurrentShapeBuildInput(null, true);
        if (input == null) return false;
        Minecraft mc = this.screen.getMinecraft();
        if (mc == null) return false;
        Vec3 rayOrigin = mc.gameRenderer.getMainCamera().getPosition();
        Vec3 rayDir = this.screen.computeCursorRayDirection();
        BlockHitResult templateHit = this.worldOperations.templateHit(input);
        return this.worldOperations.execute(
                input,
                this.worldOperations::filterPlacementTargets,
                bounded -> {
                    List<BlockHitResult> hits = ShapeWorldOperationPlanner.wrapPlacementHits(
                            bounded, input.placementFace());
                    if (useFluid) {
                        for (BlockHitResult shapedHit : hits) {
                            this.controller.placeSelectedFluid(shapedHit, forcePlace, rayOrigin, rayDir);
                        }
                    } else {
                        this.controller.placeSelectedBatch(
                                hits, templateHit, forcePlace, rayOrigin, rayDir, true,
                                this.screen.isQuickBuildCreativeOverwriteEnabled());
                    }
                },
                this::clearShapeBuildSession);
    }

    public ShapeDataRecords.GhostPreview getShapeGhostPreview() { return this.ghostPreviews.snapshot(); }
    @Override public ShapeBuildTypes.Session session() { return this.selectionSession.current(); }
    @Override public ShapeBuildTypes.Input resolveInput(BlockHitResult cursorHit, boolean requireReady) {
        return resolveCurrentShapeBuildInput(cursorHit, requireReady);
    }
    @Override public List<BlockPos> generate(ShapeBuildTypes.Input input) { return this.worldOperations.generate(input); }
    @Override public ShapeGenerationResult generationPlan(ShapeBuildTypes.Input input) {
        return this.worldOperations.generationPlan(input);
    }
    @Override public List<BlockPos> filterPlacementTargets(ShapeBuildTypes.Input input, List<BlockPos> targets) {
        return this.worldOperations.filterPlacementTargets(input, targets);
    }
    @Override public boolean isBreakable(BlockPos pos) { return this.worldOperations.isBreakable(pos); }
    @Override public ConfirmedDestroyPreviewState.Progress destroyProgress() {
        return this.confirmedDestroyWorkArea.progress();
    }
    @Override public boolean isLiveDestroyTarget(BlockPos pos) {
        return this.confirmedDestroyWorkArea.isLiveTarget(pos);
    }

    public void rememberConfirmedChainDestroyPreview(List<BlockPos> blocks) {
        this.confirmedDestroyWorkArea.rememberChain(blocks);
    }
    public List<ShapeDataRecords.GhostPreview> getConfirmedRangeDestroyPreviews() {
        return this.confirmedDestroyWorkArea.activeRanges();
    }
    public void removeConfirmedRangeDestroyPreviewBlocks(List<BlockPos> skippedPositions) {
        this.confirmedDestroyWorkArea.removeRangeBlocks(skippedPositions);
    }
    public boolean hasConfirmedDestroyWorkArea() { return this.confirmedDestroyWorkArea.hasActive(); }

    public boolean undoLastPlacementBatch() { return this.placementHistory.undo(); }
    public boolean redoLastPlacementBatch() { return this.placementHistory.redo(); }
    public void recordSinglePlacementForUndo(BlockHitResult hit,
            InteractionTypes.PlacementReplayKind replayKind, String itemId, int toolSlot) { }
    public void recordBreakForUndo(List<BlockPos> positions, Direction face, int toolSlot) { }
    public void recordPendingBreakForUndo(List<BlockPos> positions, Direction face, int toolSlot) { }

    public boolean adjustShapeDimensionNudge(int delta, boolean adjustSecondaryAxis, boolean adjustHeight) {
        return this.selectionSession.adjustDimension(delta, adjustSecondaryAxis, adjustHeight);
    }
    public boolean canAdjustCurrentShapeHeight() {
        return this.selectionSession.canAdjustHeight(this.controller.getBuildShape());
    }
    public boolean adjustShapeHeightNudge(int delta) { return this.selectionSession.adjustHeight(delta); }
    public boolean handleShapeHeightMouseScrolled(double scrollY) {
        if (scrollY == 0.0D || !canAdjustCurrentShapeHeight()) return false;
        int delta = scrollY > 0.0D ? 1 : -1;
        if (isAltDown()) delta *= 4;
        return adjustShapeHeightNudge(delta);
    }

    public String fillModeLabel(ShapeFillMode mode) {
        return ShapeSelectionTextPresenter.fillModeLabel(mode, this.screen::text);
    }
    public static String shapeDimensionLabel(BuildShape shape) {
        return ShapeSelectionTextPresenter.dimensionLabel(shape);
    }
    public String currentShapeSizeText() {
        BuildShape shape = this.controller.getBuildShape();
        if (shape == BuildShape.BLOCK) return ShapeSelectionTextPresenter.sizeText(shape, List.of());
        ShapeBuildTypes.Input input = resolveCurrentShapeBuildInput(this.screen.pickBlockHit(), false);
        return input == null
                ? ShapeSelectionTextPresenter.sizeText(shape, List.of())
                : ShapeSelectionTextPresenter.sizeText(shape, this.worldOperations.generate(input));
    }
    public String currentShapeCostText() {
        if (this.screen.isQuickBuildRangeDestroyChainMode()) {
            return ShapeSelectionTextPresenter.countText(this.screen.collectUltiminePreviewBlocks().size());
        }
        BuildShape shape = this.controller.getBuildShape();
        if (shape == BuildShape.BLOCK) return ShapeSelectionTextPresenter.countText(1);
        ShapeBuildTypes.Input input = resolveCurrentShapeBuildInput(this.screen.pickBlockHit(), false);
        if (input == null) return ShapeSelectionTextPresenter.countText(0);
        if (this.screen.isQuickBuildRangeDestroyMode()) {
            return ShapeSelectionTextPresenter.countText(
                    ShapeDestroyTargetClassifier.breakableTargets(
                            this.worldOperations.generate(input), this.worldOperations::isBreakable).size());
        }
        return ShapeSelectionTextPresenter.countText(
                this.worldOperations.filterPlacementTargets(input, this.worldOperations.generate(input)).size());
    }
    public String pendingShapeStatusText() {
        BuildShape currentShape = this.controller.getBuildShape();
        boolean destroyMode = this.screen.isQuickBuildRangeDestroyMode();
        ShapeSelectionTextPresenter.Status status = new ShapeSelectionTextPresenter.Status(
                this.screen.isQuickBuildOpen(), currentShape, destroyMode,
                this.screen.isQuickBuildRangeDestroyChainMode(), this.selectionSession.current());
        return ShapeSelectionTextPresenter.pendingStatusText(
                status, () -> confirmKeyLabel(destroyMode), this.screen::text,
                this.worldOperations.generationStatus(
                        resolveCurrentShapeBuildInput(this.screen.pickBlockHit(), false)));
    }
    private String confirmKeyLabel(boolean destroyMode) {
        if (Config.isKeyboardBatchConfirmEnabled()) {
            return (destroyMode ? ClientKeyMappings.CONFIRM_BATCH_DESTROY
                    : ClientKeyMappings.CONFIRM_BATCH_PLACE).getTranslatedKeyMessage().getString();
        }
        return this.screen.text(destroyMode ? "screen.rtsbuilding.input.lmb" : "screen.rtsbuilding.input.rmb");
    }
    public String shapeLabel(BuildShape shape) {
        return ShapeSelectionTextPresenter.shapeLabel(shape, this.screen::text);
    }

    private ShapeBuildTypes.Input resolveCurrentShapeBuildInput(BlockHitResult cursorHit, boolean requireReady) {
        return this.selectionSession.resolveInput(
                cursorHit, requireReady, this.controller.getBuildShape(), this.modeState.activeLineConnected());
    }

    private void rememberConfirmedRangeDestroyPreview(ShapeDestroyTargetClassifier.Selection preview) {
        this.confirmedDestroyWorkArea.rememberRange(preview);
    }

    private boolean isAltDown() {
        Minecraft mc = this.screen.getMinecraft();
        if (mc == null) return false;
        long window = mc.getWindow().getWindow();
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_ALT) == GLFW.GLFW_PRESS;
    }
}
