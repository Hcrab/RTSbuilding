package com.rtsbuilding.rtsbuilding.client.screen.handler;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.client.bootstrap.ClientKeyMappings;
import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.rendering.animation.PlacementAnimationRenderer;
import com.rtsbuilding.rtsbuilding.client.rendering.builder.BuildGhostBlockStateResolver;
import com.rtsbuilding.rtsbuilding.client.rendering.util.RenderingUtil;
import com.rtsbuilding.rtsbuilding.client.screen.culling.RtsBoxHandleInteraction;
import com.rtsbuilding.rtsbuilding.client.screen.culling.RtsCullingBox;
import com.rtsbuilding.rtsbuilding.client.screen.selection.RtsSelectionBoxAnimator;
import com.rtsbuilding.rtsbuilding.client.screen.interaction.InteractionTypes;
import com.rtsbuilding.rtsbuilding.client.screen.quickbuild.BuildShape;
import com.rtsbuilding.rtsbuilding.client.screen.shape.AdvancedShapeSelectionGeometry;
import com.rtsbuilding.rtsbuilding.client.screen.shape.ConfirmedDestroyPreviewState;
import com.rtsbuilding.rtsbuilding.client.screen.shape.RangeDestroySelectionLimiter;
import com.rtsbuilding.rtsbuilding.client.screen.shape.ShapeBuildTypes;
import com.rtsbuilding.rtsbuilding.client.screen.shape.ShapeConfirmationPolicy;
import com.rtsbuilding.rtsbuilding.client.screen.shape.ShapeDataRecords;
import com.rtsbuilding.rtsbuilding.client.screen.shape.ShapeDestroyTargetClassifier;
import com.rtsbuilding.rtsbuilding.client.screen.shape.ShapeGenerationPlanCache;
import com.rtsbuilding.rtsbuilding.client.screen.shape.ShapeGeometryUtil;
import com.rtsbuilding.rtsbuilding.client.screen.shape.ShapeModeState;
import com.rtsbuilding.rtsbuilding.client.screen.shape.ShapePlacementTargetResolver;
import com.rtsbuilding.rtsbuilding.client.screen.shape.ShapeSelectionTextPresenter;
import com.rtsbuilding.rtsbuilding.client.screen.shape.ShapeSessionInputResolver;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.common.shape.model.ShapeFillMode;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.EndCrystalItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreenConstants.SHAPE_MAX_DIMENSION;
import static com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreenConstants.SHAPE_ROTATE_STEP_DEGREES;

public final class ScreenShapeController {
    private static final int DEFAULT_AREA_MINE_MAX_SIZE = 36;
    private static final int DEFAULT_AREA_MINE_MAX_VOLUME =
            DEFAULT_AREA_MINE_MAX_SIZE * DEFAULT_AREA_MINE_MAX_SIZE * DEFAULT_AREA_MINE_MAX_SIZE;
    private static final RangeDestroySelectionLimiter.Limits SHAPE_SELECTION_LIMITS =
            new RangeDestroySelectionLimiter.Limits(
                    SHAPE_MAX_DIMENSION,
                    SHAPE_MAX_DIMENSION,
                    SHAPE_MAX_DIMENSION,
                    SHAPE_MAX_DIMENSION
                            * SHAPE_MAX_DIMENSION
                            * SHAPE_MAX_DIMENSION);

    private BuilderScreen screen;
    private ClientRtsController controller;

    private ShapeBuildTypes.Session shapeBuildSession;
    private BlockHitResult shapeTemplateHit;
    private int shapeFootprintNudgeA = 0;
    private int shapeFootprintNudgeB = 0;
    private double shapeCursorY = 0.0D;
    private final ShapeModeState modeState = new ShapeModeState();

    // ===== BUILD 模式独立按钮状态 =====

    // ===== 范围破坏模式独立按钮状态 =====

    /** 当前活跃的是否为范围破坏模式（用于填充/连线/旋转的同步追踪） */
    private final ConfirmedDestroyPreviewState confirmedDestroyPreviews = new ConfirmedDestroyPreviewState();
    private final PlacementHistoryManager placementHistory = new PlacementHistoryManager();
    private final RtsBoxHandleInteraction advancedBoxHandles = new RtsBoxHandleInteraction();
    private final RtsSelectionBoxAnimator shapeBoxAnimator = new RtsSelectionBoxAnimator();
    private final ShapeGenerationPlanCache shapeGenerationPlans = new ShapeGenerationPlanCache();

    public void init(BuilderScreen screen, ClientRtsController controller) {
        this.screen = screen;
        this.controller = controller;
        this.placementHistory.init(screen, controller);
    }

    // ===== Public state accessors =====

    public ShapeFillMode getShapeFillMode() {
        return this.modeState.activeFillMode();
    }

    public void setShapeFillMode(ShapeFillMode mode) {
        this.modeState.setActiveFillMode(mode);
    }

    /** 返回 BUILD 模式的独立填充模式 */
    public ShapeFillMode getBuildShapeFillMode() {
        return this.modeState.buildFillMode();
    }

    public void setBuildShapeFillMode(ShapeFillMode mode) {
        this.modeState.setBuildFillMode(mode);
    }

    /** 返回范围破坏模式的独立填充模式 */
    public ShapeFillMode getDestroyShapeFillMode() {
        return this.modeState.destroyFillMode();
    }

    public void setDestroyShapeFillMode(ShapeFillMode mode) {
        this.modeState.setDestroyFillMode(mode);
    }

    public boolean isLineConnected() {
        return this.modeState.activeLineConnected();
    }

    public void setLineConnected(boolean connected) {
        this.modeState.setActiveLineConnected(connected);
    }

    /** 返回 BUILD 模式的独立直线连接状态 */
    public boolean isBuildLineConnected() {
        return this.modeState.buildLineConnected();
    }

    public void setBuildLineConnected(boolean connected) {
        this.modeState.setBuildLineConnected(connected);
    }

    /** 返回范围破坏模式的独立直线连接状态 */
    public boolean isDestroyLineConnected() {
        return this.modeState.destroyLineConnected();
    }

    public void setDestroyLineConnected(boolean connected) {
        this.modeState.setDestroyLineConnected(connected);
    }

    public int getShapeRotateDegrees() {
        return this.modeState.activeRotateDegrees();
    }

    /** 返回 BUILD 模式的独立旋转角度 */
    public int getBuildRotateDegrees() {
        return this.modeState.buildRotateDegrees();
    }

    /** 返回范围破坏模式的独立旋转角度 */
    public int getDestroyRotateDegrees() {
        return this.modeState.destroyRotateDegrees();
    }

    public int getShapeUndoSize() {
        return this.placementHistory.getUndoSize();
    }

    // ===== Shape session management =====

    public void clearShapeBuildSession() {
        this.shapeBuildSession = null;
        this.shapeTemplateHit = null;
        this.shapeFootprintNudgeA = 0;
        this.shapeFootprintNudgeB = 0;
        this.advancedBoxHandles.clear();
        this.shapeBoxAnimator.clear();
        this.shapeGenerationPlans.clear();
    }

    public void rotateShapeByStep(int step) {
        int raw = this.modeState.activeRotateDegrees()
                + (step * SHAPE_ROTATE_STEP_DEGREES);
        this.modeState.setActiveRotateDegrees(raw);
        this.screen.persistUiState();
    }
    public void rotateToDegrees(int degrees) {
        this.modeState.setActiveRotateDegrees(degrees);
    }

    public void setBuildRotateDegrees(int degrees) {
        this.modeState.setBuildRotateDegrees(degrees);
    }

    public void setDestroyRotateDegrees(int degrees) {
        this.modeState.setDestroyRotateDegrees(degrees);
    }

    public void rotateDestroyToDegrees(int degrees) {
        this.modeState.setDestroyRotateDegrees(degrees);
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
            this.modeState.setActiveFillMode(ShapeFillMode.FILL);
            this.screen.persistUiState();
            return;
        }
        if (!modes.contains(this.modeState.activeFillMode())) {
            this.modeState.setActiveFillMode(modes.get(0));
            this.screen.persistUiState();
        }
    }

    /** 校验范围破坏模式的填充模式是否对指定形状合法 */
    public void ensureDestroyFillModeForShape(BuildShape shape) {
        List<ShapeFillMode> modes = ShapeGeometryUtil.availableFillModes(shape);
        if (modes.isEmpty()) {
            this.modeState.setDestroyFillMode(ShapeFillMode.FILL);
            this.screen.persistUiState();
            return;
        }
        if (!modes.contains(this.modeState.destroyFillMode())) {
            this.modeState.setDestroyFillMode(modes.get(0));
            this.screen.persistUiState();
        }
    }

    public boolean cycleShapeFillModeForCurrentShape(int step) {
        BuildShape shape = this.controller.getBuildShape();
        List<ShapeFillMode> modes = ShapeGeometryUtil.availableFillModes(shape);
        if (modes.isEmpty()) {
            return false;
        }
        int currentIndex = modes.indexOf(this.modeState.activeFillMode());
        if (currentIndex < 0) {
            this.modeState.setActiveFillMode(modes.get(0));
            this.screen.persistUiState();
            return true;
        }
        int next = Math.floorMod(currentIndex + step, modes.size());
        this.modeState.setActiveFillMode(modes.get(next));
        this.screen.persistUiState();
        return true;
    }

    /** 范围破坏模式下的填充模式循环切换 */
    public boolean cycleDestroyShapeFillModeForCurrentShape(int step) {
        BuildShape shape = this.controller.getBuildShape();
        List<ShapeFillMode> modes = ShapeGeometryUtil.availableFillModes(shape);
        if (modes.isEmpty()) {
            return false;
        }
        int currentIndex = modes.indexOf(this.modeState.destroyFillMode());
        if (currentIndex < 0) {
            this.modeState.setDestroyFillMode(modes.get(0));
            this.screen.persistUiState();
            return true;
        }
        int next = Math.floorMod(currentIndex + step, modes.size());
        this.modeState.setDestroyFillMode(modes.get(next));
        this.screen.persistUiState();
        return true;
    }

    // ===== 模式切换：在 BUILD 与 DESTROY 之间搬运状态 =====

    /**
     * 从 BUILD 切换到 DESTROY：保存当前活跃状态到 BUILD 独立字段，
     * 然后将之前保存的 DESTROY 独立状态恢复到活跃字段。
     */
    public void switchToDestroy() {
        // 保存当前活跃的 BUILD 状态
        // 恢复 DESTROY 状态到活跃字段
        this.modeState.switchToDestroy();
    }

    /**
     * 从 DESTROY 切换到 BUILD：保存当前活跃状态到 DESTROY 独立字段，
     * 然后将之前保存的 BUILD 独立状态恢复到活跃字段。
     */
    public void switchToBuild() {
        // 保存当前活跃的 DESTROY 状态
        // 恢复 BUILD 状态到活跃字段
        this.modeState.switchToBuild();
    }

    /**
     * 初始化时调用：将持久化的 BUILD 独立状态直接复制到活跃字段，
     * 不覆盖独立字段中的值。
     */
    public void applyBuildStateAsActive() {
        this.modeState.applyBuildState();
    }

    /**
     * 初始化时调用：将持久化的 DESTROY 独立状态直接复制到活跃字段，
     * 不覆盖独立字段中的值。
     */
    public void applyDestroyStateAsActive() {
        this.modeState.applyDestroyState();
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
                // Single block pending ghost 鈥?resolve target position for accurate direction
                BlockPos placePos = ShapePlacementTargetResolver.resolveClickedTarget(
                        hit.getBlockPos(),
                        hit.getDirection(),
                        ShapePlacementTargetResolver.minecraftWorld(
                                this.screen.getMinecraft(),
                                resolveShapePlacementStackForContext()));
                BlockState pendingState = resolvePendingGhostBlockState(placePos);
                if (placePos != null) {
                    PlacementAnimationRenderer.addPendingBatch(List.of(placePos.immutable()), pendingState);
                }
            }
            return;
        }
        advanceShapeSession(hit, rayDir, mouseY, shape);
        if (shouldSubmitShapeAfterSelection()) {
            tryConfirmPendingShapeBuild(forcePlace);
        }
    }

    public void selectRangeDestroyShape(BlockHitResult hit, double mouseY, Vec3 rayDir) {
        if (hit == null) {
            return;
        }
        BuildShape shape = this.controller.getBuildShape();
        if (shape == BuildShape.BLOCK) {
            clearShapeBuildSession();
            List<BlockPos> breakable = ShapeDestroyTargetClassifier.breakableTargets(
                    List.of(hit.getBlockPos().immutable()),
                    this::isBreakableDestroyTarget);
            if (!breakable.isEmpty()) {
                List<BlockPos> boundsFiltered = filterToBounds(breakable);
                if (!boundsFiltered.isEmpty()) {
                    rememberConfirmedRangeDestroyPreview(
                            new ShapeDestroyTargetClassifier.Selection(boundsFiltered, List.of()));
                    this.controller.confirmShapeAreaDestroy(boundsFiltered, this.screen.getSelectedToolSlot());
                }
            }
            return;
        }
        advanceShapeSession(hit, rayDir, mouseY, shape);
        if (shouldSubmitShapeAfterSelection()) {
            tryConfirmPendingRangeDestroy();
        }
    }

    private boolean shouldSubmitShapeAfterSelection() {
        return this.shapeBuildSession != null
                && ShapeConfirmationPolicy.shouldSubmitAfterSelection(
                        Config.isKeyboardBatchConfirmEnabled(),
                        this.shapeBuildSession.phase());
    }

    /**
     * Starts a new shape build session or advances an existing one.
     * <p>
     * First interaction creates a session and sets the first anchor point.
     * Subsequent interactions dispatch to per-shape handler methods so that
     * each shape's click flow and height logic stays colocated.
     */
    private void advanceShapeSession(BlockHitResult hit, Vec3 rayDir, double mouseY, BuildShape shape) {
        if (this.shapeBuildSession == null || this.shapeBuildSession.shape() != shape) {
            startNewSession(hit, rayDir, mouseY, shape);
            return;
        }
        advanceSessionByShape(hit, mouseY);
    }

    private void startNewSession(BlockHitResult hit, Vec3 rayDir, double mouseY, BuildShape shape) {
        this.confirmedDestroyPreviews.clearChain();
        this.shapeFootprintNudgeA = 0;
        this.shapeFootprintNudgeB = 0;
        Direction placementFace = ShapeGeometryUtil.resolveShapePlacementFace(shape, hit.getDirection(), rayDir);
        this.shapeTemplateHit = new BlockHitResult(hit.getLocation(), placementFace, hit.getBlockPos(), hit.isInside());
        this.shapeBuildSession = new ShapeBuildTypes.Session(
                shape,
                resolveShapeBuildFace(shape, hit.getDirection(), rayDir),
                placementFace,
                hit.getBlockPos(),
                null,
                ShapeBuildTypes.Phase.NEED_SECOND_POINT,
                0,
                mouseY);
    }

    private Direction resolveShapeBuildFace(BuildShape shape, Direction clickedFace, Vec3 rayDir) {
        if (shape == BuildShape.CIRCLE || shape == BuildShape.CYLINDER) {
            return this.screen != null && this.screen.isRoundShapeVertical(shape)
                    ? resolveVerticalRoundShapeFace(clickedFace, rayDir)
                    : Direction.UP;
        }
        return ShapeGeometryUtil.resolveShapeBuildFace(shape, clickedFace, rayDir);
    }

    private static Direction resolveVerticalRoundShapeFace(Direction clickedFace, Vec3 rayDir) {
        if (rayDir != null && (Math.abs(rayDir.x) > 1.0E-5D || Math.abs(rayDir.z) > 1.0E-5D)) {
            Direction nearest = Direction.getNearest(rayDir.x, 0.0D, rayDir.z);
            if (nearest.getAxis() != Direction.Axis.Y) {
                return nearest;
            }
        }
        return clickedFace != null && clickedFace.getAxis() != Direction.Axis.Y ? clickedFace : Direction.NORTH;
    }

    private void advanceSessionByShape(BlockHitResult hit, double mouseY) {
        switch (this.shapeBuildSession.shape()) {
            case LINE -> advanceLineSession(hit, mouseY);
            case SQUARE -> advanceSquareSession(hit, mouseY);
            case WALL -> advanceWallSession(hit, mouseY);
            case CIRCLE -> advanceCircleSession(hit, mouseY);
            case CYLINDER -> advanceCylinderSession(hit, mouseY);
            case BALL -> advanceBallSession(hit, mouseY);
            case BOX -> advanceBoxSession(hit, mouseY);
            default -> {}
        }
    }

    /** LINE: second click determines length, then immediately ready to confirm. */
    private void advanceLineSession(BlockHitResult hit, double mouseY) {
        ShapeBuildTypes.Session session = this.shapeBuildSession;
        if (session.phase() != ShapeBuildTypes.Phase.NEED_SECOND_POINT) return;
        boolean verticalLine = isVerticalLine(session.shape());
        BlockPos pointB = verticalLine
                ? ShapeSessionInputResolver.resolveVerticalLinePoint(session, hit)
                : resolveShapePlanePoint(session, hit);
        this.shapeBuildSession = new ShapeBuildTypes.Session(
                session.shape(), session.planeFace(), session.placementFace(),
                session.pointA(), pointB,
                ShapeBuildTypes.Phase.READY_CONFIRM,
                verticalLine && pointB != null && session.pointA() != null
                        ? pointB.getY() - session.pointA().getY()
                        : 0,
                session.boxHeightMouseBaseY());
    }

    /** SQUARE: second click determines opposite corner, then immediately ready to confirm. */
    private void advanceSquareSession(BlockHitResult hit, double mouseY) {
        ShapeBuildTypes.Session session = this.shapeBuildSession;
        if (session.phase() != ShapeBuildTypes.Phase.NEED_SECOND_POINT) return;
        BlockPos pointB = resolveShapePlanePoint(session, hit);
        this.shapeBuildSession = readySession(session, pointB, session.boxHeightMouseBaseY());
    }

    /**
     * WALL: three-click flow.
     * <ol>
     *   <li>First click sets pointA (session creation)</li>
     *   <li>Second click sets pointB, enters NEED_THIRD_POINT for height</li>
     *   <li>Third click confirms height 鈫?READY_CONFIRM</li>
     * </ol>
     */
    private void advanceWallSession(BlockHitResult hit, double mouseY) {
        ShapeBuildTypes.Session session = this.shapeBuildSession;
        if (session.phase() == ShapeBuildTypes.Phase.NEED_SECOND_POINT) {
            BlockPos pointB = isAdvancedShape(session.shape())
                    ? resolveAdvancedBoxSecondPoint(session, hit)
                    : resolveShapePlanePoint(session, hit);
            this.shapeBuildSession = isAdvancedShape(session.shape())
                    ? readySession(session, pointB, mouseY)
                    : new ShapeBuildTypes.Session(
                            session.shape(), session.planeFace(), session.placementFace(),
                            session.pointA(), pointB,
                            ShapeBuildTypes.Phase.NEED_THIRD_POINT, 0, mouseY);
            return;
        }
        if (session.phase() == ShapeBuildTypes.Phase.NEED_THIRD_POINT) {
            this.shapeBuildSession = new ShapeBuildTypes.Session(
                    session.shape(), session.planeFace(), session.placementFace(),
                    session.pointA(), session.pointB(),
                    ShapeBuildTypes.Phase.READY_CONFIRM,
                    session.boxHeightOffset(), session.boxHeightMouseBaseY());
        }
    }

    /** CIRCLE: second click determines radius, then immediately ready to confirm. */
    private void advanceCircleSession(BlockHitResult hit, double mouseY) {
        ShapeBuildTypes.Session session = this.shapeBuildSession;
        if (session.phase() != ShapeBuildTypes.Phase.NEED_SECOND_POINT) return;
        BlockPos pointB = resolveShapePlanePoint(session, hit);
        this.shapeBuildSession = readySession(session, pointB, session.boxHeightMouseBaseY());
    }

    /** CYLINDER: 第二点确定圆形底面半径，然后用滚轮调整高度。 */
    private void advanceCylinderSession(BlockHitResult hit, double mouseY) {
        ShapeBuildTypes.Session session = this.shapeBuildSession;
        if (session.phase() == ShapeBuildTypes.Phase.NEED_SECOND_POINT) {
            BlockPos pointB = isAdvancedShape(session.shape())
                    ? resolveAdvancedBoxSecondPoint(session, hit)
                    : resolveShapePlanePoint(session, hit);
            this.shapeBuildSession = isAdvancedShape(session.shape())
                    ? readySession(session, pointB, mouseY)
                    : new ShapeBuildTypes.Session(
                            session.shape(), session.planeFace(), session.placementFace(),
                            session.pointA(), pointB,
                            ShapeBuildTypes.Phase.NEED_THIRD_POINT, 0, mouseY);
            return;
        }
        if (session.phase() == ShapeBuildTypes.Phase.NEED_THIRD_POINT) {
            this.shapeBuildSession = new ShapeBuildTypes.Session(
                    session.shape(), session.planeFace(), session.placementFace(),
                    session.pointA(), session.pointB(),
                    ShapeBuildTypes.Phase.READY_CONFIRM,
                    session.boxHeightOffset(), session.boxHeightMouseBaseY());
        }
    }

    /** BALL: 第二点确定球半径，然后立即进入确认阶段。 */
    private void advanceBallSession(BlockHitResult hit, double mouseY) {
        ShapeBuildTypes.Session session = this.shapeBuildSession;
        if (session.phase() != ShapeBuildTypes.Phase.NEED_SECOND_POINT) return;
        BlockPos pointB = isAdvancedShape(session.shape())
                ? resolveAdvancedBoxSecondPoint(session, hit)
                : resolveShapePlanePoint(session, hit);
        this.shapeBuildSession = readySession(session, pointB, session.boxHeightMouseBaseY());
    }

    /**
     * BOX: three-click flow.
     * <ol>
     *   <li>First click sets pointA (session creation)</li>
     *   <li>Second click sets pointB, enters NEED_THIRD_POINT for height</li>
     *   <li>Third click confirms height 鈫?READY_CONFIRM</li>
     * </ol>
     */
    private void advanceBoxSession(BlockHitResult hit, double mouseY) {
        ShapeBuildTypes.Session session = this.shapeBuildSession;
        if (session.phase() == ShapeBuildTypes.Phase.NEED_SECOND_POINT) {
            BlockPos pointB = isAdvancedShape(session.shape())
                    ? resolveAdvancedBoxSecondPoint(session, hit)
                    : resolveShapePlanePoint(session, hit);
            ShapeBuildTypes.Session next = new ShapeBuildTypes.Session(
                    session.shape(), session.planeFace(), session.placementFace(),
                    session.pointA(), pointB,
                    isAdvancedShape(session.shape())
                            ? ShapeBuildTypes.Phase.READY_CONFIRM
                            : ShapeBuildTypes.Phase.NEED_THIRD_POINT,
                    isAdvancedShape(session.shape())
                            ? pointB.getY() - session.pointA().getY()
                            : 0,
                    mouseY);
            this.shapeBuildSession = isAdvancedShape(session.shape())
                    ? AdvancedShapeSelectionGeometry.sessionFromBox(
                            next,
                            clampAdvancedShapeBox(
                                    AdvancedShapeSelectionGeometry.boxFromSession(next),
                                    session.pointA()))
                    : next;
            return;
        }
        if (session.phase() == ShapeBuildTypes.Phase.NEED_THIRD_POINT) {
            this.shapeBuildSession = new ShapeBuildTypes.Session(
                    session.shape(), session.planeFace(), session.placementFace(),
                    session.pointA(), session.pointB(),
                    ShapeBuildTypes.Phase.READY_CONFIRM,
                    session.boxHeightOffset(), session.boxHeightMouseBaseY());
        }
    }

    public boolean tryConfirmPendingRangeDestroy() {
        if (!this.screen.isQuickBuildRangeDestroyMode() || this.controller.getBuildShape() == BuildShape.BLOCK) {
            return false;
        }
        ShapeBuildTypes.Input input = resolveCurrentShapeBuildInput(null, true);
        if (input == null) return false;
        List<BlockPos> raw = generateShapePositions(input);
        List<BlockPos> breakable =
                ShapeDestroyTargetClassifier.breakableTargets(raw, this::isBreakableDestroyTarget);
        List<BlockPos> boundedBreakable = filterToBounds(breakable);
        List<BlockPos> boundedEnvelope = filterToBounds(
                ShapeDestroyTargetClassifier.envelopeTargets(raw, boundedBreakable));
        clearShapeBuildSession();
        if (boundedBreakable.isEmpty()) {
            return true;
        }
        rememberConfirmedRangeDestroyPreview(
                new ShapeDestroyTargetClassifier.Selection(boundedBreakable, boundedEnvelope));
        this.controller.confirmShapeAreaDestroy(boundedBreakable, this.screen.getSelectedToolSlot());
        return true;
    }

    public boolean isAwaitingBatchPlaceConfirm() {
        return !this.screen.isQuickBuildRangeDestroyMode() && isAwaitingBatchConfirm();
    }

    public boolean isAwaitingBatchDestroyConfirm() {
        return this.screen.isQuickBuildRangeDestroyMode()
                && !this.screen.isQuickBuildRangeDestroyChainMode()
                && isAwaitingBatchConfirm();
    }

    public RtsCullingBox advancedRangeDestroyBox() {
        if (!isAdvancedShapeSelectionSession()) {
            return null;
        }
        return AdvancedShapeSelectionGeometry.boxFromSession(this.shapeBuildSession);
    }

    public net.minecraft.world.phys.AABB advancedRangeDestroyRenderAabb() {
        return shapeSelectionRenderAabb();
    }

    /**
     * 返回当前快速建造/破坏范围的平滑视觉包围盒。
     *
     * <p>普通与高级模式共用同一个动画器；箭头只决定是否允许编辑，不再决定是否有插值。</p>
     */
    public AABB shapeSelectionRenderAabb() {
        if (this.shapeBuildSession == null || this.controller.getBuildShape() == BuildShape.BLOCK) {
            return null;
        }
        ShapeBuildTypes.Input input = resolveCurrentShapeBuildInput(this.screen.pickBlockHit(), false);
        if (input == null) {
            return null;
        }
        generateShapePositions(input);
        RtsCullingBox bounds = this.shapeGenerationPlans.bounds();
        return bounds == null
                ? null
                : this.shapeBoxAnimator.renderAabb(bounds);
    }

    public Direction advancedRangeDestroyHoveredHandle() {
        return this.advancedBoxHandles.hoveredDirection();
    }

    public Direction advancedRangeDestroyActiveHandle() {
        return this.advancedBoxHandles.activeDirection();
    }

    public Set<Direction> advancedRangeDestroyAllowedHandleDirections() {
        if (!isAdvancedShapeSelectionSession()) {
            return Set.of();
        }
        BuildShape shape = this.shapeBuildSession.shape();
        if (shape == BuildShape.SQUARE) {
            return EnumSet.of(Direction.EAST, Direction.WEST, Direction.SOUTH, Direction.NORTH);
        }
        if (shape == BuildShape.CIRCLE) {
            return directionsForPlaneAxes(shape, this.shapeBuildSession.planeFace());
        }
        if (shape == BuildShape.WALL) {
            RtsCullingBox box =
                    AdvancedShapeSelectionGeometry.boxFromSession(this.shapeBuildSession);
            return box.width() >= box.depth()
                    ? EnumSet.of(Direction.EAST, Direction.WEST, Direction.UP, Direction.DOWN)
                    : EnumSet.of(Direction.SOUTH, Direction.NORTH, Direction.UP, Direction.DOWN);
        }
        return EnumSet.allOf(Direction.class);
    }

    private static Set<Direction> directionsForPlaneAxes(BuildShape shape, Direction face) {
        Direction[] axes = ShapeGeometryUtil.resolveShapePlaneAxes(shape, face);
        EnumSet<Direction> directions = EnumSet.noneOf(Direction.class);
        for (Direction axis : axes) {
            directions.add(axis);
            directions.add(axis.getOpposite());
        }
        return directions;
    }

    public void updateAdvancedRangeDestroyHover(Vec3 origin, Vec3 rayDirection, boolean enabled) {
        RtsCullingBox box = advancedRangeDestroyBox();
        this.advancedBoxHandles.updateHover(
                box, origin, rayDirection, enabled && box != null, advancedRangeDestroyAllowedHandleDirections());
    }

    public boolean clickAdvancedRangeDestroyHandle(Vec3 origin, Vec3 rayDirection) {
        RtsCullingBox box = advancedRangeDestroyBox();
        if (box == null) {
            return false;
        }
        return this.advancedBoxHandles.clickHandle(
                box, origin, rayDirection, advancedRangeDestroyAllowedHandleDirections()).handled();
    }

    public boolean scrollAdvancedRangeDestroyHandle(double scrollY, boolean fast) {
        return this.advancedBoxHandles.handleScroll(scrollY, fast, this::resizeAdvancedRangeDestroyBox);
    }

    public boolean dragAdvancedRangeDestroyHandle(double dragX, double dragY, double axisX, double axisY) {
        return this.advancedBoxHandles.handleDrag(dragX, dragY, axisX, axisY, this::resizeAdvancedRangeDestroyBox);
    }

    public boolean releaseAdvancedRangeDestroyHandleIfDragged() {
        return this.advancedBoxHandles.releaseActiveHandleIfDragged();
    }

    private boolean isAwaitingBatchConfirm() {
        BuildShape currentShape = this.controller.getBuildShape();
        return currentShape != BuildShape.BLOCK
                && this.shapeBuildSession != null
                && this.shapeBuildSession.shape() == currentShape
                && this.shapeBuildSession.phase() == ShapeBuildTypes.Phase.READY_CONFIRM;
    }

    private boolean isAdvancedShapeSelectionSession() {
        return this.screen != null
                && this.screen.isAdvancedShapeMode()
                && this.shapeBuildSession != null
                && isAdvancedShape(this.shapeBuildSession.shape())
                && this.shapeBuildSession.phase() == ShapeBuildTypes.Phase.READY_CONFIRM
                && this.shapeBuildSession.pointB() != null;
    }

    private boolean isAdvancedShape(BuildShape shape) {
        return this.screen != null
                && this.screen.isAdvancedShapeMode()
                && shape != null
                && shape != BuildShape.BLOCK
                && shape != BuildShape.LINE;
    }

    private boolean resizeAdvancedRangeDestroyBox(Direction direction, int delta) {
        if (direction == null || delta == 0 || !isAdvancedShapeSelectionSession()) {
            return false;
        }
        RtsCullingBox current =
                AdvancedShapeSelectionGeometry.boxFromSession(this.shapeBuildSession);
        RtsCullingBox resized = current.resizeFromHandle(direction, delta);
        if (!withinAdvancedShapeCaps(resized)) {
            return true;
        }
        this.shapeBoxAnimator.animate(current, resized);
        this.shapeBuildSession = AdvancedShapeSelectionGeometry.sessionFromBox(
                this.shapeBuildSession,
                resized);
        return true;
    }

    public boolean nudgeCurrentShapeSelection(int dx, int dy, int dz) {
        if (this.shapeBuildSession == null || (dx == 0 && dy == 0 && dz == 0)) {
            return false;
        }
        ShapeBuildTypes.Session session = this.shapeBuildSession;
        if (session.shape() == BuildShape.BLOCK || session.pointB() == null
                || session.phase() == ShapeBuildTypes.Phase.NEED_SECOND_POINT) {
            return false;
        }
        RtsCullingBox oldBox = isAdvancedShapeSelectionSession()
                ? AdvancedShapeSelectionGeometry.boxFromSession(session)
                : null;
        this.shapeBuildSession = new ShapeBuildTypes.Session(
                session.shape(),
                session.planeFace(),
                session.placementFace(),
                session.pointA().offset(dx, dy, dz),
                session.pointB().offset(dx, dy, dz),
                session.phase(),
                session.boxHeightOffset(),
                session.boxHeightMouseBaseY());
        if (oldBox != null) {
            this.shapeBoxAnimator.animate(
                    oldBox,
                    AdvancedShapeSelectionGeometry.boxFromSession(this.shapeBuildSession));
        }
        return true;
    }

    private BlockPos resolveAdvancedBoxSecondPoint(ShapeBuildTypes.Session session, BlockHitResult hit) {
        if (hit == null) {
            return resolveShapePlanePoint(session, null);
        }
        Minecraft mc = this.screen.getMinecraft();
        BlockPos clicked = hit.getBlockPos();
        if (mc != null && mc.level != null
                && mc.level.getBlockState(clicked).isAir()
                && mc.level.getFluidState(clicked).isEmpty()) {
            return resolveShapePlanePoint(session, hit);
        }
        return clicked;
    }

    private static boolean withinClientAreaMineCaps(RtsCullingBox box) {
        return RangeDestroySelectionLimiter.contains(
                box,
                currentRangeDestroyLimits());
    }

    private boolean withinAdvancedShapeCaps(RtsCullingBox box) {
        if (box == null) {
            return false;
        }
        if (this.screen != null && this.screen.isQuickBuildRangeDestroyMode()) {
            return withinClientAreaMineCaps(box);
        }
        return box.width() <= SHAPE_MAX_DIMENSION
                && box.height() <= SHAPE_MAX_DIMENSION
                && box.depth() <= SHAPE_MAX_DIMENSION;
    }

    private RtsCullingBox clampAdvancedShapeBox(RtsCullingBox box, BlockPos anchor) {
        if (this.screen != null && this.screen.isQuickBuildRangeDestroyMode()) {
            return RangeDestroySelectionLimiter.clampBox(
                    box,
                    anchor,
                    currentRangeDestroyLimits());
        }
        return RangeDestroySelectionLimiter.clampBox(
                box,
                anchor,
                SHAPE_SELECTION_LIMITS);
    }

    private static RangeDestroySelectionLimiter.Limits currentRangeDestroyLimits() {
        return new RangeDestroySelectionLimiter.Limits(
                configInt(
                        Config::areaMineMaxWidth,
                        DEFAULT_AREA_MINE_MAX_SIZE),
                configInt(
                        Config::areaMineMaxHeight,
                        DEFAULT_AREA_MINE_MAX_SIZE),
                configInt(
                        Config::areaMineMaxDepth,
                        DEFAULT_AREA_MINE_MAX_SIZE),
                configInt(
                        Config::areaMineMaxVolume,
                        DEFAULT_AREA_MINE_MAX_VOLUME));
    }

    private static int configInt(java.util.function.IntSupplier supplier, int fallback) {
        try {
            return Math.max(1, supplier.getAsInt());
        } catch (IllegalStateException ignored) {
            return fallback;
        }
    }

    private ShapeBuildTypes.Session readySession(ShapeBuildTypes.Session session, BlockPos pointB, double mouseY) {
        ShapeBuildTypes.Session ready = new ShapeBuildTypes.Session(
                session.shape(),
                session.planeFace(),
                session.placementFace(),
                session.pointA(),
                pointB,
                ShapeBuildTypes.Phase.READY_CONFIRM,
                isAdvancedShape(session.shape()) && pointB != null
                        ? AdvancedShapeSelectionGeometry.initialHeightOffset(
                                session.shape(),
                                session.pointA(),
                                pointB)
                        : 0,
                mouseY);
        return isAdvancedShape(session.shape())
                ? AdvancedShapeSelectionGeometry.sessionFromBox(
                        ready,
                        clampAdvancedShapeBox(
                                AdvancedShapeSelectionGeometry.initialBox(ready),
                                session.pointA()))
                : ready;
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
        BlockHitResult templateHit = resolveShapeTemplateHit(input);

        return executeShapeOperation(
                input,
                (in, raw) -> filterOccupiedReadyShapeTargets(in, raw),
                bounded -> {
                    List<BlockHitResult> hits = wrapPlacementHits(bounded, input.placementFace());
                    if (useFluid) {
                        for (BlockHitResult shapedHit : hits) {
                            this.controller.placeSelectedFluid(shapedHit, forcePlace, rayOrigin, rayDir);
                        }
                    } else {
                        this.controller.placeSelectedBatch(hits, templateHit, forcePlace, rayOrigin, rayDir, true,
                                this.screen.isQuickBuildCreativeOverwriteEnabled());
                    }
                });
    }

    // ===== Ghost preview =====

    public ShapeDataRecords.GhostPreview getShapeGhostPreview() {
        if (this.screen.isQuickBuildRangeDestroyMode()) {
            if (this.screen.isQuickBuildRangeDestroyChainMode()) {
                ShapeDataRecords.GhostPreview confirmed = this.confirmedDestroyPreviews.activeChain(
                        currentDestroyPreviewProgress(),
                        this::isLiveConfirmedDestroyTarget);
                if (confirmed != ShapeDataRecords.GhostPreview.EMPTY) {
                    return confirmed;
                }
                List<BlockPos> preview = this.screen.collectUltiminePreviewBlocks();
                return preview.isEmpty()
                        ? ShapeDataRecords.GhostPreview.EMPTY
                        : new ShapeDataRecords.GhostPreview(preview, true, true, List.of(), true);
            }
            if (this.controller.getBuildShape() == BuildShape.BLOCK) {
                BlockHitResult hit = this.screen.pickBlockHit();
                if (hit == null) {
                    return ShapeDataRecords.GhostPreview.EMPTY;
                }
                List<BlockPos> breakable = ShapeDestroyTargetClassifier.breakableTargets(
                        List.of(hit.getBlockPos().immutable()),
                        this::isBreakableDestroyTarget);
                return breakable.isEmpty()
                        ? ShapeDataRecords.GhostPreview.EMPTY
                        : new ShapeDataRecords.GhostPreview(breakable, true, true, List.of());
            }
            ShapeBuildTypes.Input input = resolveCurrentShapeBuildInput(this.screen.pickBlockHit(), false);
            if (input == null) {
                return ShapeDataRecords.GhostPreview.EMPTY;
            }
            List<BlockPos> raw = generateShapePositions(input);
            ShapeDestroyTargetClassifier.Selection selection =
                    ShapeDestroyTargetClassifier.classify(raw, this::isBreakableDestroyTarget);
            List<BlockPos> breakable = selection.breakableBlocks();
            List<BlockPos> emptyEnvelope = selection.envelopeBlocks();
            boolean ready = this.shapeBuildSession != null && this.shapeBuildSession.phase() == ShapeBuildTypes.Phase.READY_CONFIRM;
            if (breakable.isEmpty()) {
                return emptyEnvelope.isEmpty()
                        ? ShapeDataRecords.GhostPreview.EMPTY
                        : new ShapeDataRecords.GhostPreview(List.of(), ready, true, emptyEnvelope);
            }
            return new ShapeDataRecords.GhostPreview(breakable, ready, true, emptyEnvelope);
        }
        if (this.controller.getBuildShape() == BuildShape.BLOCK) {
            if (this.controller.isEmptyHandSelected()) {
                return ShapeDataRecords.GhostPreview.EMPTY;
            }
            // Pre-placement ghost for single block: show translucent block model
            // at the cursor's target position before the player clicks.
            if (this.controller.hasSelectedFluid()) {
                return ShapeDataRecords.GhostPreview.EMPTY;
            }
            // Check that a block item source is available
            if (!this.controller.hasSelectedItem() && !this.screen.canUseToolSlotShapeSource()) {
                Minecraft mc = this.screen.getMinecraft();
                if (mc == null || mc.player == null) {
                    return ShapeDataRecords.GhostPreview.EMPTY;
                }
                if (!(mc.player.getMainHandItem().getItem() instanceof BlockItem)
                        && !(mc.player.getMainHandItem().getItem() instanceof SpawnEggItem)
                        && !(mc.player.getMainHandItem().getItem() instanceof EndCrystalItem)) {
                    return ShapeDataRecords.GhostPreview.EMPTY;
                }
            }
            BlockHitResult hit = this.screen.pickBlockHit();
            if (hit == null) {
                return ShapeDataRecords.GhostPreview.EMPTY;
            }
            Minecraft mc = this.screen.getMinecraft();
            if (mc == null || mc.level == null || mc.player == null) {
                return ShapeDataRecords.GhostPreview.EMPTY;
            }

            // Resolve the held item stack (same approach as resolvePendingGhostBlockState)
            ItemStack itemStack = ItemStack.EMPTY;
            if (this.controller.hasSelectedItem()) {
                itemStack = this.controller.getSelectedItemPreview();
            } else {
                itemStack = mc.player.getMainHandItem();
            }
            if (itemStack.isEmpty()) {
                return ShapeDataRecords.GhostPreview.EMPTY;
            }

            BlockPos placePos = ShapePlacementTargetResolver.resolveSingleGhostTarget(mc, hit, itemStack);
            if (placePos == null) {
                return ShapeDataRecords.GhostPreview.EMPTY;
            }
            return new ShapeDataRecords.GhostPreview(List.of(placePos), true);
        }
        if (!this.controller.hasSelectedItem() && !this.controller.hasSelectedFluid() && !this.screen.canUseToolSlotShapeSource()) {
            return ShapeDataRecords.GhostPreview.EMPTY;
        }
        ShapeBuildTypes.Input input = resolveCurrentShapeBuildInput(this.screen.pickBlockHit(), false);
        if (input == null) {
            return ShapeDataRecords.GhostPreview.EMPTY;
        }
        List<BlockPos> blocks = filterOccupiedReadyShapeTargets(input, generateShapePositions(input));
        if (blocks.isEmpty()) {
            return ShapeDataRecords.GhostPreview.EMPTY;
        }
        boolean ready = this.shapeBuildSession != null && this.shapeBuildSession.phase() == ShapeBuildTypes.Phase.READY_CONFIRM;
        return new ShapeDataRecords.GhostPreview(blocks, ready);
    }

    /**
     * Keeps the connected-destroy work area visible after the click that starts
     * server-side mining. The preview remains tied to the original block set so
     * the green progress overlay does not snap back to the cursor while the
     * batch is processing; it is cleared once those target blocks are gone.
     */
    public void rememberConfirmedChainDestroyPreview(List<BlockPos> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            this.confirmedDestroyPreviews.clearChain();
            return;
        }
        List<BlockPos> boundsFiltered = filterToBounds(blocks);
        if (boundsFiltered.isEmpty()) {
            this.confirmedDestroyPreviews.clearChain();
            return;
        }
        this.confirmedDestroyPreviews.rememberChain(boundsFiltered);
    }

    /**
     * Returns the active Range Destroy work-area preview that should remain visible
     * after its selection click. This deliberately exposes at most one preview because
     * the server mining state is a single queue; showing multiple work areas would
     * imply parallel mining that does not exist.
     */
    public List<ShapeDataRecords.GhostPreview> getConfirmedRangeDestroyPreviews() {
        return this.confirmedDestroyPreviews.activeRanges(
                currentDestroyPreviewProgress(),
                this::isLiveConfirmedDestroyTarget);
    }

    /**
     * 移除服务端明确因采掘等级不足而拒绝的范围破坏坐标。
     *
     * <p>客户端点击时只能生成候选预览，最终可挖集合仍以服务端为准。收到回执后
     * 只裁掉被拒绝的方块，继续保留同一批次中真正会被挖掘的目标和进度高亮。
     */
    public void removeConfirmedRangeDestroyPreviewBlocks(List<BlockPos> skippedPositions) {
        this.confirmedDestroyPreviews.removeRangeBlocks(skippedPositions);
    }

    /** Returns whether a confirmed destructive work area is currently active. */
    public boolean hasConfirmedDestroyWorkArea() {
        return this.confirmedDestroyPreviews.hasAnyActive(
                currentDestroyPreviewProgress(),
                this::isLiveConfirmedDestroyTarget);
    }

    // ===== Undo =====

    public boolean undoLastPlacementBatch() {
        return this.placementHistory.undo();
    }

    /**
     * 璁板綍鍗曟鏂瑰潡鏀剧疆鍒版挙鍥炴爤锛堝凡鍦ㄦ湇鍔＄璁板綍锛屽鎴风涓嶅啀鍙備笌锛夈€?     */
    public void recordSinglePlacementForUndo(BlockHitResult hit, InteractionTypes.PlacementReplayKind replayKind, String itemId, int toolSlot) {
    }

    /**
     * 璁板綍鏂瑰潡鐮村潖鎿嶄綔鍒版挙鍥炴爤锛堝凡鍦ㄦ湇鍔＄璁板綍锛屽鎴风涓嶅啀鍙備笌锛夈€?     */
    public void recordBreakForUndo(List<BlockPos> positions, Direction face, int toolSlot) {
    }

    /**
     * 璁板綍寰呮湇鍔＄纭鐨勭牬鍧忔壒娆″埌鎾ゅ洖鏍堬紙宸插湪鏈嶅姟绔褰曪紝瀹㈡埛绔笉鍐嶅弬涓庯級銆?     */
    public void recordPendingBreakForUndo(List<BlockPos> positions, Direction face, int toolSlot) {
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
                && canAdjustShapeHeight(this.shapeBuildSession.shape())
                && (this.shapeBuildSession.shape() != BuildShape.LINE || isVerticalLine(BuildShape.LINE));
    }

    private static boolean canAdjustShapeHeight(BuildShape shape) {
        return shape == BuildShape.LINE
                || shape == BuildShape.WALL
                || shape == BuildShape.CYLINDER
                || shape == BuildShape.BOX;
    }

    public boolean adjustShapeHeightNudge(int delta) {
        if (delta == 0 || this.shapeBuildSession == null || !canAdjustShapeHeight(this.shapeBuildSession.shape())) {
            return false;
        }
        if (this.shapeBuildSession.shape() == BuildShape.LINE && !isVerticalLine(BuildShape.LINE)) {
            return false;
        }
        if ((this.shapeBuildSession.shape() == BuildShape.BOX
                || this.shapeBuildSession.shape() == BuildShape.CYLINDER
                || this.shapeBuildSession.shape() == BuildShape.WALL)
                && this.shapeBuildSession.phase() != ShapeBuildTypes.Phase.NEED_THIRD_POINT) {
            return false;
        }
        int nextOffset = ShapeGeometryUtil.clampShapeOffset(this.shapeBuildSession.boxHeightOffset() + delta);
        BlockPos nextPointB = this.shapeBuildSession.pointB();
        if (this.shapeBuildSession.shape() == BuildShape.LINE && this.shapeBuildSession.pointA() != null) {
            nextPointB = this.shapeBuildSession.pointA().offset(0, nextOffset, 0);
        }
        this.shapeBuildSession = new ShapeBuildTypes.Session(
                this.shapeBuildSession.shape(),
                this.shapeBuildSession.planeFace(),
                this.shapeBuildSession.placementFace(),
                this.shapeBuildSession.pointA(),
                nextPointB,
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
        return ShapeSelectionTextPresenter.fillModeLabel(mode, this.screen::text);
    }

    public static String shapeDimensionLabel(BuildShape shape) {
        return ShapeSelectionTextPresenter.dimensionLabel(shape);
    }

    public String currentShapeSizeText() {
        BuildShape shape = this.controller.getBuildShape();
        if (shape == BuildShape.BLOCK) {
            return ShapeSelectionTextPresenter.sizeText(shape, List.of());
        }
        ShapeBuildTypes.Input input = resolveCurrentShapeBuildInput(this.screen.pickBlockHit(), false);
        if (input == null) {
            return ShapeSelectionTextPresenter.sizeText(shape, List.of());
        }
        return ShapeSelectionTextPresenter.sizeText(shape, generateShapePositions(input));
    }

    public String currentShapeCostText() {
        if (this.screen.isQuickBuildRangeDestroyChainMode()) {
            List<BlockPos> preview = this.screen.collectUltiminePreviewBlocks();
            return ShapeSelectionTextPresenter.countText(preview.size());
        }
        BuildShape shape = this.controller.getBuildShape();
        if (shape == BuildShape.BLOCK) {
            return ShapeSelectionTextPresenter.countText(1);
        }
        ShapeBuildTypes.Input input = resolveCurrentShapeBuildInput(this.screen.pickBlockHit(), false);
        if (input == null) {
            return ShapeSelectionTextPresenter.countText(0);
        }
        if (this.screen.isQuickBuildRangeDestroyMode()) {
            return ShapeSelectionTextPresenter.countText(
                    ShapeDestroyTargetClassifier.breakableTargets(
                            generateShapePositions(input),
                            this::isBreakableDestroyTarget).size());
        }
        List<BlockPos> blocks = filterOccupiedReadyShapeTargets(input, generateShapePositions(input));
        return ShapeSelectionTextPresenter.countText(blocks.size());
    }

    public String pendingShapeStatusText() {
        BuildShape currentShape = this.controller.getBuildShape();
        boolean destroyMode = this.screen.isQuickBuildRangeDestroyMode();
        ShapeSelectionTextPresenter.Status status = new ShapeSelectionTextPresenter.Status(
                this.screen.isQuickBuildOpen(),
                currentShape,
                destroyMode,
                this.screen.isQuickBuildRangeDestroyChainMode(),
                this.shapeBuildSession);
        return ShapeSelectionTextPresenter.pendingStatusText(
                status,
                () -> confirmKeyLabel(destroyMode),
                this.screen::text);
    }

    private String confirmKeyLabel(boolean destroyMode) {
        if (Config.isKeyboardBatchConfirmEnabled()) {
            return (destroyMode ? ClientKeyMappings.CONFIRM_BATCH_DESTROY : ClientKeyMappings.CONFIRM_BATCH_PLACE)
                    .getTranslatedKeyMessage()
                    .getString();
        }
        return this.screen.text(destroyMode ? "screen.rtsbuilding.input.lmb" : "screen.rtsbuilding.input.rmb");
    }

    public String shapeLabel(BuildShape shape) {
        return ShapeSelectionTextPresenter.shapeLabel(shape, this.screen::text);
    }

    // ===== Internal helpers =====

    /**
     * Resolves the block state to use for pending ghost rendering at placement confirmation time.
     * Uses the target鈫抍amera direction to simulate {@link
     * net.minecraft.world.level.block.Block#getStateForPlacement(BlockPlaceContext)}
     * so the ghost preview matches the server-placed block state.
     *
     * @param targetPos actual block position where the new block will be placed
     */
    private BlockState resolvePendingGhostBlockState(BlockPos targetPos) {
        Minecraft mc = this.screen.getMinecraft();
        ItemStack itemStack = ItemStack.EMPTY;

        if (this.controller.hasSelectedItem()) {
            itemStack = this.controller.getSelectedItemPreview();
        } else if (mc != null && mc.player != null) {
            itemStack = mc.player.getMainHandItem();
        }

        if (itemStack.isEmpty() || !(itemStack.getItem() instanceof BlockItem blockItem)) {
            return null;
        }

        // If no target position, use default state
        if (targetPos == null) {
            return blockItem.getBlock().defaultBlockState();
        }

        // Resolve block state using BuildGhostBlockStateResolver (deduplicated)
        BlockState state = BuildGhostBlockStateResolver.resolveStateWithCamera(mc, blockItem, itemStack, targetPos);
        if (state == null) return null;

        // Apply rotation from shape controller
        int rotateDegrees = this.modeState.activeRotateDegrees();
        if (rotateDegrees != 0) {
            state = BuildGhostBlockStateResolver.applyRotation(state, rotateDegrees, mc.level, targetPos);
        }
        return state;
    }

    private ShapeBuildTypes.Input resolveCurrentShapeBuildInput(BlockHitResult cursorHit, boolean requireReady) {
        ShapeBuildTypes.Session session = this.shapeBuildSession;
        if (session == null || session.shape() != this.controller.getBuildShape()) {
            return null;
        }
        Minecraft mc = this.screen.getMinecraft();
        Vec3 rayOrigin = mc != null && mc.gameRenderer != null
                ? mc.gameRenderer.getMainCamera().getPosition()
                : null;
        Vec3 rayDirection = mc != null ? this.screen.computeCursorRayDirection() : null;
        return ShapeSessionInputResolver.resolve(
                session,
                cursorHit,
                requireReady,
                isVerticalLine(session.shape()),
                this.modeState.activeLineConnected(),
                this.shapeFootprintNudgeA,
                this.shapeFootprintNudgeB,
                rayOrigin,
                rayDirection);
    }

    private boolean isVerticalLine(BuildShape shape) {
        return shape == BuildShape.LINE
                && this.screen != null
                && this.screen.isRoundShapeVertical(BuildShape.LINE);
    }

    /**
     * 交互阶段只负责提供当前相机射线，平面命中计算统一交给纯解析器。
     */
    private BlockPos resolveShapePlanePoint(
            ShapeBuildTypes.Session session,
            BlockHitResult cursorHit) {
        Minecraft mc = this.screen.getMinecraft();
        Vec3 rayOrigin = mc != null && mc.gameRenderer != null
                ? mc.gameRenderer.getMainCamera().getPosition()
                : null;
        Vec3 rayDirection = mc != null ? this.screen.computeCursorRayDirection() : null;
        return ShapeSessionInputResolver.resolvePlanePoint(
                session, cursorHit, rayOrigin, rayDirection);
    }

    private void rememberConfirmedRangeDestroyPreview(ShapeDestroyTargetClassifier.Selection preview) {
        if (preview == null || preview.isEmpty()) {
            return;
        }
        List<BlockPos> boundsFiltered = filterToBounds(preview.breakableBlocks());
        List<BlockPos> envelopeFiltered = filterToBounds(preview.envelopeBlocks());
        if (boundsFiltered.isEmpty()) {
            return;
        }
        this.confirmedDestroyPreviews.rememberRange(boundsFiltered, envelopeFiltered);
    }

    private ConfirmedDestroyPreviewState.Progress currentDestroyPreviewProgress() {
        BlockPos progressPos = this.controller.getMineProgressPos();
        RtsWorkflowStatus workflow = this.controller.findActiveDestroyWorkflow();
        return new ConfirmedDestroyPreviewState.Progress(
                progressPos,
                this.controller.getMineProgressStage(),
                workflow != null && workflow.totalBlocks() > 0);
    }

    private boolean isLiveConfirmedDestroyTarget(BlockPos pos) {
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

    private List<BlockPos> filterToBounds(List<BlockPos> blocks) {
        if (!this.controller.hasBounds() || blocks == null) {
            return blocks;
        }
        return RenderingUtil.filterBlocksWithinBounds(blocks,
                this.controller.getAnchorX(), this.controller.getAnchorZ(), this.controller.getMaxRadius());
    }

    private boolean isBreakableDestroyTarget(BlockPos pos) {
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

    /**
     * Step 1 共享: 从当前形状输入和填充模式生成原始方块位置列表。
     * <p>
     * 范围放置和范围破坏共用此方法，确保两侧的形状生成逻辑一致。
     */
    private List<BlockPos> generateShapePositions(ShapeBuildTypes.Input input) {
        if (input == null) {
            return List.of();
        }
        boolean rangeDestroy = this.screen.isQuickBuildRangeDestroyMode()
                && !this.screen.isQuickBuildRangeDestroyChainMode();
        RtsCullingBox advancedBox = isAdvancedShapeSelectionSession() ? advancedRangeDestroyBox() : null;
        return this.shapeGenerationPlans.positions(new ShapeGenerationPlanCache.Request(
                input,
                this.modeState.activeFillMode(),
                advancedBox,
                rangeDestroy,
                currentRangeDestroyLimits(),
                SHAPE_MAX_DIMENSION));
    }

    /**
     * 将过滤后的放置目标位置列表包装为 BlockHitResult 列表。
     * <p>
     * 范围放置的 Step 2→Step 3 之间的数据转换步骤，与范围破坏的原始 List<BlockPos> 发送形成对称。
     */
    private static List<BlockHitResult> wrapPlacementHits(List<BlockPos> positions, Direction face) {
        if (positions == null || positions.isEmpty()) return List.of();
        List<BlockHitResult> hits = new ArrayList<>(positions.size());
        for (BlockPos pos : positions) {
            hits.add(ShapeGeometryUtil.createShapePlacementHit(pos, face));
        }
        return hits;
    }

    // ========================================================================
    //  形状操作模板 — 生成→过滤→发送
    // ========================================================================

    /** 形状位置过滤策略。 */
    @FunctionalInterface
    private interface PositionFilter {
        List<BlockPos> filter(ShapeBuildTypes.Input input, List<BlockPos> rawPositions);
    }

    /** 形状位置执行策略。 */
    @FunctionalInterface
    private interface PositionExecutor {
        void execute(List<BlockPos> validPositions);
    }

    /**
     * 形状操作通用执行模板：生成→过滤→发送。
     * <p>
     * 所有形状操作共享此方法的骨架，差异部分通过
     * {@link PositionFilter} 和 {@link PositionExecutor} 注入。
     * <ol>
     *   <li><b>生成</b> — 调用 {@link #generateShapePositions} 生成原始位置</li>
     *   <li><b>过滤</b> — 委托给 {@code filter} 进行操作特定的有效性校验</li>
     *   <li><b>清理+空值检查</b> — 清空会话、检查列表是否为空、检查边界</li>
     *   <li><b>发送</b> — 委托给 {@code executor} 执行最终的发送/执行逻辑</li>
     * </ol>
     */
    private boolean executeShapeOperation(ShapeBuildTypes.Input input,
                                          PositionFilter filter,
                                          PositionExecutor executor) {
        // Step 1: 生成 - Generate raw shape positions
        List<BlockPos> rawPositions = generateShapePositions(input);

        // Step 2: 过滤 - Apply operation-specific filtering
        List<BlockPos> validPositions = filter.filter(input, rawPositions);

        clearShapeBuildSession();
        if (validPositions.isEmpty()) return true;

        List<BlockPos> bounded = filterToBounds(validPositions);
        if (bounded.isEmpty()) return true;

        // Step 3: 发送 - Send result to server
        executor.execute(bounded);
        return true;
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
        if (this.screen.isQuickBuildCreativeOverwriteEnabled()) {
            return ShapePlacementTargetResolver.resolveOverwriteTargets(targets);
        }
        boolean strictEmptyLock = shouldSkipOccupiedReadyShapeTargets(input);
        ItemStack placementStack = resolveShapePlacementStackForContext();
        return ShapePlacementTargetResolver.resolveTargets(
                input,
                targets,
                strictEmptyLock,
                ShapePlacementTargetResolver.minecraftWorld(this.screen.getMinecraft(), placementStack));
    }

    private ItemStack resolveShapePlacementStackForContext() {
        if (this.controller.hasSelectedItem()) {
            return this.controller.getSelectedItemPreview();
        }
        Minecraft mc = this.screen.getMinecraft();
        if (mc != null && mc.player != null) {
            return mc.player.getMainHandItem();
        }
        return ItemStack.EMPTY;
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



    private boolean isAltDown() {
        Minecraft mc = this.screen.getMinecraft();
        if (mc == null) {
            return false;
        }
        long window = mc.getWindow().getWindow();
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_ALT) == GLFW.GLFW_PRESS;
    }

}
