package com.rtsbuilding.rtsbuilding.client.screen.handler;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.client.bootstrap.ClientKeyMappings;
import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.rendering.animation.PlacementAnimationRenderer;
import com.rtsbuilding.rtsbuilding.client.rendering.builder.BuildGhostBlockStateResolver;
import com.rtsbuilding.rtsbuilding.client.rendering.util.RenderingUtil;
import com.rtsbuilding.rtsbuilding.client.screen.interaction.InteractionTypes;
import com.rtsbuilding.rtsbuilding.client.screen.quickbuild.BuildShape;
import com.rtsbuilding.rtsbuilding.client.screen.shape.ShapeBuildTypes;
import com.rtsbuilding.rtsbuilding.client.screen.shape.ShapeDataRecords;
import com.rtsbuilding.rtsbuilding.client.screen.shape.ShapeGeometryUtil;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.common.shape.model.ShapeFillMode;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.EndCrystalItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import static com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreenConstants.SHAPE_ROTATE_STEP_DEGREES;

public final class ScreenShapeController {
    private BuilderScreen screen;
    private ClientRtsController controller;

    private ShapeBuildTypes.Session shapeBuildSession;
    private BlockHitResult shapeTemplateHit;
    private int shapeFootprintNudgeA = 0;
    private int shapeFootprintNudgeB = 0;
    private double shapeCursorY = 0.0D;
    private ShapeFillMode shapeFillMode = ShapeFillMode.FILL;
    private boolean lineConnected = false;
    private int shapeRotateDegrees = 0;

    // ===== BUILD 模式独立按钮状态 =====
    private ShapeFillMode buildShapeFillMode = ShapeFillMode.FILL;
    private boolean buildLineConnected = false;
    private int buildRotateDegrees = 0;

    // ===== 范围破坏模式独立按钮状态 =====
    private ShapeFillMode destroyShapeFillMode = ShapeFillMode.FILL;
    private boolean destroyLineConnected = false;
    private int destroyRotateDegrees = 0;

    /** 当前活跃的是否为范围破坏模式（用于填充/连线/旋转的同步追踪） */
    private boolean destroyModeActive = false;
    private ShapeDataRecords.GhostPreview confirmedRangeDestroyPreview = ShapeDataRecords.GhostPreview.EMPTY;
    private long confirmedRangeDestroyPreviewUntilMs;
    private ShapeDataRecords.GhostPreview confirmedChainDestroyPreview = ShapeDataRecords.GhostPreview.EMPTY;
    private long confirmedChainDestroyPreviewUntilMs;
    private final PlacementHistoryManager placementHistory = new PlacementHistoryManager();

    public void init(BuilderScreen screen, ClientRtsController controller) {
        this.screen = screen;
        this.controller = controller;
        this.placementHistory.init(screen, controller);
    }

    // ===== Public state accessors =====

    public ShapeFillMode getShapeFillMode() {
        return this.shapeFillMode;
    }

    public void setShapeFillMode(ShapeFillMode mode) {
        this.shapeFillMode = mode;
        syncActiveToModeFields();
    }

    /** 返回 BUILD 模式的独立填充模式 */
    public ShapeFillMode getBuildShapeFillMode() {
        return this.buildShapeFillMode;
    }

    public void setBuildShapeFillMode(ShapeFillMode mode) {
        this.buildShapeFillMode = mode;
    }

    /** 返回范围破坏模式的独立填充模式 */
    public ShapeFillMode getDestroyShapeFillMode() {
        return this.destroyShapeFillMode;
    }

    public void setDestroyShapeFillMode(ShapeFillMode mode) {
        this.destroyShapeFillMode = mode;
    }

    public boolean isLineConnected() {
        return this.lineConnected;
    }

    public void setLineConnected(boolean connected) {
        this.lineConnected = connected;
        syncActiveToModeFields();
    }

    /** 返回 BUILD 模式的独立直线连接状态 */
    public boolean isBuildLineConnected() {
        return this.buildLineConnected;
    }

    public void setBuildLineConnected(boolean connected) {
        this.buildLineConnected = connected;
    }

    /** 返回范围破坏模式的独立直线连接状态 */
    public boolean isDestroyLineConnected() {
        return this.destroyLineConnected;
    }

    public void setDestroyLineConnected(boolean connected) {
        this.destroyLineConnected = connected;
    }

    public int getShapeRotateDegrees() {
        return this.shapeRotateDegrees;
    }

    /** 返回 BUILD 模式的独立旋转角度 */
    public int getBuildRotateDegrees() {
        return this.buildRotateDegrees;
    }

    /** 返回范围破坏模式的独立旋转角度 */
    public int getDestroyRotateDegrees() {
        return this.destroyRotateDegrees;
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
    }

    public void rotateShapeByStep(int step) {
        int raw = this.shapeRotateDegrees + (step * SHAPE_ROTATE_STEP_DEGREES);
        this.shapeRotateDegrees = Math.floorMod(raw, 360);
        syncActiveRotationToModeFields();
        this.screen.persistUiState();
    }
    public void rotateToDegrees(int degrees) {
        this.shapeRotateDegrees = Math.floorMod(degrees, 360);
    }

    public void setBuildRotateDegrees(int degrees) {
        this.buildRotateDegrees = Math.floorMod(degrees, 360);
    }

    public void setDestroyRotateDegrees(int degrees) {
        this.destroyRotateDegrees = Math.floorMod(degrees, 360);
    }

    public void rotateDestroyToDegrees(int degrees) {
        this.destroyRotateDegrees = Math.floorMod(degrees, 360);
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
            syncActiveToModeFields();
            this.screen.persistUiState();
            return;
        }
        if (!modes.contains(this.shapeFillMode)) {
            this.shapeFillMode = modes.get(0);
            syncActiveToModeFields();
            this.screen.persistUiState();
        }
    }

    /** 校验范围破坏模式的填充模式是否对指定形状合法 */
    public void ensureDestroyFillModeForShape(BuildShape shape) {
        List<ShapeFillMode> modes = ShapeGeometryUtil.availableFillModes(shape);
        if (modes.isEmpty()) {
            this.destroyShapeFillMode = ShapeFillMode.FILL;
            this.screen.persistUiState();
            return;
        }
        if (!modes.contains(this.destroyShapeFillMode)) {
            this.destroyShapeFillMode = modes.get(0);
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
            syncActiveToModeFields();
            this.screen.persistUiState();
            return true;
        }
        int next = Math.floorMod(currentIndex + step, modes.size());
        this.shapeFillMode = modes.get(next);
        syncActiveToModeFields();
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
        int currentIndex = modes.indexOf(this.destroyShapeFillMode);
        if (currentIndex < 0) {
            this.destroyShapeFillMode = modes.get(0);
            this.screen.persistUiState();
            return true;
        }
        int next = Math.floorMod(currentIndex + step, modes.size());
        this.destroyShapeFillMode = modes.get(next);
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
        this.buildShapeFillMode = this.shapeFillMode;
        this.buildLineConnected = this.lineConnected;
        this.buildRotateDegrees = this.shapeRotateDegrees;
        // 恢复 DESTROY 状态到活跃字段
        this.shapeFillMode = this.destroyShapeFillMode;
        this.lineConnected = this.destroyLineConnected;
        this.shapeRotateDegrees = this.destroyRotateDegrees;
        this.destroyModeActive = true;
    }

    /**
     * 从 DESTROY 切换到 BUILD：保存当前活跃状态到 DESTROY 独立字段，
     * 然后将之前保存的 BUILD 独立状态恢复到活跃字段。
     */
    public void switchToBuild() {
        // 保存当前活跃的 DESTROY 状态
        this.destroyShapeFillMode = this.shapeFillMode;
        this.destroyLineConnected = this.lineConnected;
        this.destroyRotateDegrees = this.shapeRotateDegrees;
        // 恢复 BUILD 状态到活跃字段
        this.shapeFillMode = this.buildShapeFillMode;
        this.lineConnected = this.buildLineConnected;
        this.shapeRotateDegrees = this.buildRotateDegrees;
        this.destroyModeActive = false;
    }

    /**
     * 初始化时调用：将持久化的 BUILD 独立状态直接复制到活跃字段，
     * 不覆盖独立字段中的值。
     */
    public void applyBuildStateAsActive() {
        this.shapeFillMode = this.buildShapeFillMode;
        this.lineConnected = this.buildLineConnected;
        this.shapeRotateDegrees = this.buildRotateDegrees;
        this.destroyModeActive = false;
    }

    /**
     * 初始化时调用：将持久化的 DESTROY 独立状态直接复制到活跃字段，
     * 不覆盖独立字段中的值。
     */
    public void applyDestroyStateAsActive() {
        this.shapeFillMode = this.destroyShapeFillMode;
        this.lineConnected = this.destroyLineConnected;
        this.shapeRotateDegrees = this.destroyRotateDegrees;
        this.destroyModeActive = true;
    }

    // ===== 模式状态同步 =====

    /**
     * 将活跃字段（shapeFillMode/lineConnected）同步到当前模式对应的独立字段。
     * 确保在每次活跃字段被外部修改时，模式独立字段保持同步。
     */
    private void syncActiveToModeFields() {
        if (this.destroyModeActive) {
            this.destroyShapeFillMode = this.shapeFillMode;
            this.destroyLineConnected = this.lineConnected;
        } else {
            this.buildShapeFillMode = this.shapeFillMode;
            this.buildLineConnected = this.lineConnected;
        }
    }

    /** 将活跃旋转角度同步到当前模式对应的独立字段。 */
    private void syncActiveRotationToModeFields() {
        if (this.destroyModeActive) {
            this.destroyRotateDegrees = this.shapeRotateDegrees;
        } else {
            this.buildRotateDegrees = this.shapeRotateDegrees;
        }
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
                BlockPos placePos = resolvePlacementTargetPos(hit.getBlockPos(), hit.getDirection());
                BlockState pendingState = resolvePendingGhostBlockState(placePos);
                if (placePos != null) {
                    PlacementAnimationRenderer.addPendingBatch(List.of(placePos.immutable()), pendingState);
                }
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
            List<BlockPos> breakable = collectBreakableTargets(List.of(hit.getBlockPos().immutable()));
            if (!breakable.isEmpty()) {
                List<BlockPos> boundsFiltered = filterToBounds(breakable);
                if (!boundsFiltered.isEmpty()) {
                    rememberConfirmedRangeDestroyPreview(new RangeDestroyPreview(new ArrayList<>(boundsFiltered)));
                    this.controller.confirmShapeAreaDestroy(boundsFiltered, this.screen.getSelectedToolSlot());
                }
            }
            return;
        }
        advanceShapeSession(hit, rayDir, mouseY, shape);
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
        clearConfirmedChainDestroyPreview();
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
        BlockPos pointB = resolveShapePlanePoint(session, hit);
        this.shapeBuildSession = new ShapeBuildTypes.Session(
                session.shape(), session.planeFace(), session.placementFace(),
                session.pointA(), pointB,
                ShapeBuildTypes.Phase.READY_CONFIRM, 0, session.boxHeightMouseBaseY());
    }

    /** SQUARE: second click determines opposite corner, then immediately ready to confirm. */
    private void advanceSquareSession(BlockHitResult hit, double mouseY) {
        ShapeBuildTypes.Session session = this.shapeBuildSession;
        if (session.phase() != ShapeBuildTypes.Phase.NEED_SECOND_POINT) return;
        BlockPos pointB = resolveShapePlanePoint(session, hit);
        this.shapeBuildSession = new ShapeBuildTypes.Session(
                session.shape(), session.planeFace(), session.placementFace(),
                session.pointA(), pointB,
                ShapeBuildTypes.Phase.READY_CONFIRM, 0, session.boxHeightMouseBaseY());
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
            BlockPos pointB = resolveShapePlanePoint(session, hit);
            this.shapeBuildSession = new ShapeBuildTypes.Session(
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
        this.shapeBuildSession = new ShapeBuildTypes.Session(
                session.shape(), session.planeFace(), session.placementFace(),
                session.pointA(), pointB,
                ShapeBuildTypes.Phase.READY_CONFIRM, 0, session.boxHeightMouseBaseY());
    }

    /** CYLINDER: 第二点确定圆形底面半径，然后用滚轮调整高度。 */
    private void advanceCylinderSession(BlockHitResult hit, double mouseY) {
        ShapeBuildTypes.Session session = this.shapeBuildSession;
        if (session.phase() == ShapeBuildTypes.Phase.NEED_SECOND_POINT) {
            BlockPos pointB = resolveShapePlanePoint(session, hit);
            this.shapeBuildSession = new ShapeBuildTypes.Session(
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
        BlockPos pointB = resolveShapePlanePoint(session, hit);
        this.shapeBuildSession = new ShapeBuildTypes.Session(
                session.shape(), session.planeFace(), session.placementFace(),
                session.pointA(), pointB,
                ShapeBuildTypes.Phase.READY_CONFIRM, 0, session.boxHeightMouseBaseY());
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
            BlockPos pointB = resolveShapePlanePoint(session, hit);
            this.shapeBuildSession = new ShapeBuildTypes.Session(
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

    public boolean tryConfirmPendingRangeDestroy() {
        if (!this.screen.isQuickBuildRangeDestroyMode() || this.controller.getBuildShape() == BuildShape.BLOCK) {
            return false;
        }
        ShapeBuildTypes.Input input = resolveCurrentShapeBuildInput(null, true);
        if (input == null) return false;

        return executeShapeOperation(
                input,
                (in, raw) -> collectBreakableTargets(raw),
                bounded -> {
                    rememberConfirmedRangeDestroyPreview(new RangeDestroyPreview(new ArrayList<>(bounded)));
                    this.controller.confirmShapeAreaDestroy(bounded, this.screen.getSelectedToolSlot());
                });
    }

    public boolean isAwaitingBatchPlaceConfirm() {
        return !this.screen.isQuickBuildRangeDestroyMode() && isAwaitingBatchConfirm();
    }

    public boolean isAwaitingBatchDestroyConfirm() {
        return this.screen.isQuickBuildRangeDestroyMode()
                && !this.screen.isQuickBuildRangeDestroyChainMode()
                && isAwaitingBatchConfirm();
    }

    private boolean isAwaitingBatchConfirm() {
        BuildShape currentShape = this.controller.getBuildShape();
        return currentShape != BuildShape.BLOCK
                && this.shapeBuildSession != null
                && this.shapeBuildSession.shape() == currentShape
                && this.shapeBuildSession.phase() == ShapeBuildTypes.Phase.READY_CONFIRM;
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
                        this.controller.placeSelectedBatch(hits, templateHit, forcePlace, rayOrigin, rayDir, true);
                    }
                });
    }

    // ===== Ghost preview =====

    public ShapeDataRecords.GhostPreview getShapeGhostPreview() {
        if (this.screen.isQuickBuildRangeDestroyMode()) {
            if (this.screen.isQuickBuildRangeDestroyChainMode()) {
                ShapeDataRecords.GhostPreview confirmed = confirmedChainDestroyPreviewOrEmpty();
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
                List<BlockPos> breakable = collectBreakableTargets(List.of(hit.getBlockPos().immutable()));
                return breakable.isEmpty()
                        ? ShapeDataRecords.GhostPreview.EMPTY
                        : new ShapeDataRecords.GhostPreview(breakable, true, true, List.of());
            }
            ShapeBuildTypes.Input input = resolveCurrentShapeBuildInput(this.screen.pickBlockHit(), false);
            if (input == null) {
                return ShapeDataRecords.GhostPreview.EMPTY;
            }
            List<BlockPos> breakable = collectBreakableTargets(generateShapePositions(input));
            if (breakable.isEmpty()) {
                return ShapeDataRecords.GhostPreview.EMPTY;
            }
            boolean ready = this.shapeBuildSession != null && this.shapeBuildSession.phase() == ShapeBuildTypes.Phase.READY_CONFIRM;
            return new ShapeDataRecords.GhostPreview(breakable, ready, true, List.of());
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

            // Use BlockPlaceContext to properly determine the placement position,
            // matching the server's auto-adjustment logic (handles slab merging
            // where canBeReplaced(context) returns true for matching slabs, etc.).
            BlockPlaceContext context = new BlockPlaceContext(
                    mc.level, mc.player, InteractionHand.MAIN_HAND, itemStack, hit);
            BlockPos placePos = context.getClickedPos();
            if (placePos == null) {
                return ShapeDataRecords.GhostPreview.EMPTY;
            }

            // Use context-aware canBeReplaced() instead of the no-arg variant so
            // that blocks replaceable only with context (e.g., slabs that can merge
            // into DOUBLE) are not incorrectly treated as occupied.
            if (mc.level.hasChunkAt(placePos)) {
                if (!mc.level.getBlockState(placePos).isAir()
                        && !mc.level.getBlockState(placePos).canBeReplaced(context)) {
                    return ShapeDataRecords.GhostPreview.EMPTY;
                }
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
            clearConfirmedChainDestroyPreview();
            return;
        }
        List<BlockPos> boundsFiltered = filterToBounds(blocks);
        if (boundsFiltered.isEmpty()) {
            clearConfirmedChainDestroyPreview();
            return;
        }
        this.confirmedChainDestroyPreview = new ShapeDataRecords.GhostPreview(
                copyImmutableBlocks(boundsFiltered),
                true,
                true,
                List.of(),
                true,
                true);
        this.confirmedChainDestroyPreviewUntilMs = System.currentTimeMillis() + 2500L;
    }

    /**
     * Returns the active Range Destroy work-area preview that should remain visible
     * after its selection click. This deliberately exposes at most one preview because
     * the server mining state is a single queue; showing multiple work areas would
     * imply parallel mining that does not exist.
     */
    public List<ShapeDataRecords.GhostPreview> getConfirmedRangeDestroyPreviews() {
        ShapeDataRecords.GhostPreview preview = confirmedRangeDestroyPreviewOrEmpty();
        return preview == ShapeDataRecords.GhostPreview.EMPTY ? List.of() : List.of(preview);
    }

    /** Returns whether a confirmed destructive work area is currently active. */
    public boolean hasConfirmedDestroyWorkArea() {
        return confirmedRangeDestroyPreviewOrEmpty() != ShapeDataRecords.GhostPreview.EMPTY
                || confirmedChainDestroyPreviewOrEmpty() != ShapeDataRecords.GhostPreview.EMPTY;
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
                && canAdjustShapeHeight(this.shapeBuildSession.shape());
    }

    private static boolean canAdjustShapeHeight(BuildShape shape) {
        return shape == BuildShape.WALL || shape == BuildShape.CYLINDER || shape == BuildShape.BOX;
    }

    public boolean adjustShapeHeightNudge(int delta) {
        if (delta == 0 || this.shapeBuildSession == null || !canAdjustShapeHeight(this.shapeBuildSession.shape())) {
            return false;
        }
        if ((this.shapeBuildSession.shape() == BuildShape.BOX
                || this.shapeBuildSession.shape() == BuildShape.CYLINDER
                || this.shapeBuildSession.shape() == BuildShape.WALL)
                && this.shapeBuildSession.phase() != ShapeBuildTypes.Phase.NEED_THIRD_POINT) {
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
            case CYLINDER, BALL, BOX -> "3D";
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
        List<BlockPos> blocks = generateShapePositions(input);
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
        if (this.screen.isQuickBuildRangeDestroyChainMode()) {
            List<BlockPos> preview = this.screen.collectUltiminePreviewBlocks();
            return Integer.toString(preview.size());
        }
        BuildShape shape = this.controller.getBuildShape();
        if (shape == BuildShape.BLOCK) {
            return "1";
        }
        ShapeBuildTypes.Input input = resolveCurrentShapeBuildInput(this.screen.pickBlockHit(), false);
        if (input == null) {
            return "0";
        }
        if (this.screen.isQuickBuildRangeDestroyMode()) {
            return Integer.toString(collectBreakableTargets(generateShapePositions(input)).size());
        }
        List<BlockPos> blocks = filterOccupiedReadyShapeTargets(input, generateShapePositions(input));
        return Integer.toString(blocks.size());
    }

    public String pendingShapeStatusText() {
        if (!this.screen.isQuickBuildOpen()) {
            return "";
        }
        BuildShape currentShape = this.controller.getBuildShape();
        boolean destroyMode = this.screen.isQuickBuildRangeDestroyMode();
        if (this.screen.isQuickBuildRangeDestroyChainMode()) {
            return this.screen.text("screen.rtsbuilding.shape_status.destroy_chain");
        }
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
            case READY_CONFIRM -> {
                String key = confirmStatusKey(currentShape, destroyMode);
                yield this.screen.text(key, confirmKeyLabel(destroyMode));
            }
        };
    }

    private static String confirmStatusKey(BuildShape shape, boolean destroyMode) {
        if (shape == BuildShape.WALL) {
            return destroyMode
                    ? "screen.rtsbuilding.shape_status.destroy_confirm_wall"
                    : "screen.rtsbuilding.shape_status.confirm_wall";
        }
        if (shape == BuildShape.CYLINDER) {
            return destroyMode
                    ? "screen.rtsbuilding.shape_status.destroy_confirm_cylinder"
                    : "screen.rtsbuilding.shape_status.confirm_cylinder";
        }
        return destroyMode
                ? "screen.rtsbuilding.shape_status.destroy_confirm"
                : "screen.rtsbuilding.shape_status.confirm";
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
        if (shape == null) {
            return this.screen.text("screen.rtsbuilding.shape.block");
        }
        return switch (shape) {
            case BLOCK -> this.screen.text("screen.rtsbuilding.shape.block");
            case LINE -> this.screen.text("screen.rtsbuilding.shape.line");
            case SQUARE -> this.screen.text("screen.rtsbuilding.shape.square");
            case WALL -> this.screen.text("screen.rtsbuilding.shape.wall");
            case CIRCLE -> this.screen.text("screen.rtsbuilding.shape.circle");
            case CYLINDER -> this.screen.text("screen.rtsbuilding.shape.cylinder");
            case BALL -> this.screen.text("screen.rtsbuilding.shape.ball");
            case BOX -> this.screen.text("screen.rtsbuilding.shape.box");
        };
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
        int rotateDegrees = this.shapeRotateDegrees;
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
            return new ShapeBuildTypes.Input(session.shape(), session.planeFace(), session.placementFace(), pointA, pointB, 0, this.lineConnected);
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
            return new ShapeBuildTypes.Input(session.shape(), session.planeFace(), session.placementFace(), pointA, pointB, resolveBoxHeightOffset(session), this.lineConnected);
        }
        pointB = applyShapeFootprintNudges(session.shape(), session.planeFace(), pointA, pointB);
        return new ShapeBuildTypes.Input(session.shape(), session.planeFace(), session.placementFace(), pointA, pointB, resolveBoxHeightOffset(session), this.lineConnected);
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
                || shape == BuildShape.CYLINDER
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
        if (shape == BuildShape.BOX || shape == BuildShape.CYLINDER) {
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

    private void rememberConfirmedRangeDestroyPreview(RangeDestroyPreview preview) {
        if (preview == null || preview.isEmpty()) {
            return;
        }
        List<BlockPos> boundsFiltered = filterToBounds(preview.breakableBlocks());
        if (boundsFiltered.isEmpty()) {
            return;
        }
        this.confirmedRangeDestroyPreview = new ShapeDataRecords.GhostPreview(
                new ArrayList<>(boundsFiltered),
                true,
                true,
                List.of(),
                false,
                true);
        this.confirmedRangeDestroyPreviewUntilMs = System.currentTimeMillis() + 2500L;
    }

    private ShapeDataRecords.GhostPreview confirmedRangeDestroyPreviewOrEmpty() {
        ShapeDataRecords.GhostPreview preview = this.confirmedRangeDestroyPreview;
        if (preview == null
                || preview == ShapeDataRecords.GhostPreview.EMPTY
                || (preview.blocks().isEmpty() && preview.emptyBlocks().isEmpty())) {
            return ShapeDataRecords.GhostPreview.EMPTY;
        }
        if (!hasAnyLiveConfirmedDestroyTarget(preview)) {
            clearConfirmedRangeDestroyPreview();
            return ShapeDataRecords.GhostPreview.EMPTY;
        }
        long now = System.currentTimeMillis();
        BlockPos progressPos = this.controller.getMineProgressPos();
        boolean containsProgress = previewContains(preview, progressPos);
        boolean miningProgressBelongsHere = containsProgress && this.controller.getMineProgressStage() >= 0;
        RtsWorkflowStatus workflow = this.controller.findActiveDestroyWorkflow();
        boolean batchProgressBelongsHere = containsProgress
                && workflow != null
                && workflow.totalBlocks() > 0;
        if (miningProgressBelongsHere || batchProgressBelongsHere) {
            this.confirmedRangeDestroyPreviewUntilMs = now + 850L;
            return preview;
        }
        if (now <= this.confirmedRangeDestroyPreviewUntilMs) {
            return preview;
        }
        clearConfirmedRangeDestroyPreview();
        return ShapeDataRecords.GhostPreview.EMPTY;
    }

    private ShapeDataRecords.GhostPreview confirmedChainDestroyPreviewOrEmpty() {
        ShapeDataRecords.GhostPreview preview = this.confirmedChainDestroyPreview;
        if (preview == null
                || preview == ShapeDataRecords.GhostPreview.EMPTY
                || (preview.blocks().isEmpty() && preview.emptyBlocks().isEmpty())) {
            return ShapeDataRecords.GhostPreview.EMPTY;
        }
        if (!hasAnyLiveConfirmedDestroyTarget(preview)) {
            clearConfirmedChainDestroyPreview();
            return ShapeDataRecords.GhostPreview.EMPTY;
        }
        long now = System.currentTimeMillis();
        BlockPos progressPos = this.controller.getMineProgressPos();
        boolean containsProgress = previewContains(preview, progressPos);
        boolean hasForeignProgress = progressPos != null
                && this.controller.getMineProgressStage() >= 0
                && !containsProgress;
        if (hasForeignProgress) {
            clearConfirmedChainDestroyPreview();
            return ShapeDataRecords.GhostPreview.EMPTY;
        }
        boolean miningProgressBelongsHere = containsProgress && this.controller.getMineProgressStage() >= 0;
        RtsWorkflowStatus workflow = this.controller.findActiveDestroyWorkflow();
        boolean batchProgressBelongsHere = containsProgress
                && workflow != null
                && workflow.totalBlocks() > 0;
        if (miningProgressBelongsHere || batchProgressBelongsHere) {
            this.confirmedChainDestroyPreviewUntilMs = now + 850L;
            return preview;
        }
        if (now <= this.confirmedChainDestroyPreviewUntilMs) {
            return preview;
        }
        clearConfirmedChainDestroyPreview();
        return ShapeDataRecords.GhostPreview.EMPTY;
    }

    private void clearConfirmedChainDestroyPreview() {
        this.confirmedChainDestroyPreview = ShapeDataRecords.GhostPreview.EMPTY;
        this.confirmedChainDestroyPreviewUntilMs = 0L;
    }

    private void clearConfirmedRangeDestroyPreview() {
        this.confirmedRangeDestroyPreview = ShapeDataRecords.GhostPreview.EMPTY;
        this.confirmedRangeDestroyPreviewUntilMs = 0L;
    }

    private boolean hasAnyLiveConfirmedDestroyTarget(ShapeDataRecords.GhostPreview preview) {
        if (preview == null || preview.blocks().isEmpty()) {
            return false;
        }
        Minecraft mc = this.screen.getMinecraft();
        if (mc == null || mc.level == null) {
            return true;
        }
        for (BlockPos pos : preview.blocks()) {
            if (pos == null) {
                continue;
            }
            BlockState state = mc.level.getBlockState(pos);
            if (!state.isAir() && state.getFluidState().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static boolean previewContains(ShapeDataRecords.GhostPreview preview, BlockPos pos) {
        if (preview == null || pos == null) {
            return false;
        }
        return contains(preview.blocks(), pos) || contains(preview.emptyBlocks(), pos);
    }

    private static boolean contains(List<BlockPos> blocks, BlockPos pos) {
        if (blocks == null || pos == null) {
            return false;
        }
        for (BlockPos block : blocks) {
            if (pos.equals(block)) {
                return true;
            }
        }
        return false;
    }

    private static List<BlockPos> copyImmutableBlocks(List<BlockPos> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return List.of();
        }
        List<BlockPos> copy = new ArrayList<>(blocks.size());
        for (BlockPos pos : blocks) {
            if (pos != null) {
                copy.add(pos.immutable());
            }
        }
        return copy;
    }

    private List<BlockPos> filterToBounds(List<BlockPos> blocks) {
        if (!this.controller.hasBounds() || blocks == null) {
            return blocks;
        }
        return RenderingUtil.filterBlocksWithinBounds(blocks,
                this.controller.getAnchorX(), this.controller.getAnchorZ(), this.controller.getMaxRadius());
    }

    /**
     * Collects breakable (non-air, non-fluid, destructible) block positions from the given list.
     * <p>
     * Shared by both ghost-preview and cost-count code paths to avoid
     * duplicating the same filtering logic.
     */
    private List<BlockPos> collectBreakableTargets(List<BlockPos> targets) {
        if (targets == null || targets.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<BlockPos> breakable = new LinkedHashSet<>(targets.size());
        Minecraft mc = this.screen.getMinecraft();
        if (mc == null || mc.level == null) {
            for (BlockPos pos : targets) {
                if (pos != null) {
                    breakable.add(pos.immutable());
                }
            }
            return new ArrayList<>(breakable);
        }
        for (BlockPos pos : targets) {
            if (pos == null) {
                continue;
            }
            BlockState state = mc.level.getBlockState(pos);
            if (!state.getFluidState().isEmpty() || state.isAir() || state.getDestroySpeed(mc.level, pos) < 0.0F) {
                continue;
            }
            breakable.add(pos.immutable());
        }
        return new ArrayList<>(breakable);
    }

    private record RangeDestroyPreview(List<BlockPos> breakableBlocks) {
        private static final RangeDestroyPreview EMPTY = new RangeDestroyPreview(List.of());

        private boolean isEmpty() {
            return this.breakableBlocks.isEmpty();
        }
    }

    /**
     * Step 1 共享: 从当前形状输入和填充模式生成原始方块位置列表。
     * <p>
     * 范围放置和范围破坏共用此方法，确保两侧的形状生成逻辑一致。
     */
    private List<BlockPos> generateShapePositions(ShapeBuildTypes.Input input) {
        if (input == null) return List.of();
        return ShapeGeometryUtil.buildShapePositions(input, this.shapeFillMode);
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
        if (targets == null || targets.isEmpty()) {
            return List.of();
        }
        if (input == null || input.placementFace() == null) {
            return targets;
        }
        boolean strictEmptyLock = shouldSkipOccupiedReadyShapeTargets(input);
        boolean uniformPlacement = shouldUseUniformShapePlanePlacement(input);
        ItemStack placementStack = resolveShapePlacementStackForContext();
        LinkedHashSet<BlockPos> resolved = new LinkedHashSet<>(targets.size());
        Minecraft mc = this.screen.getMinecraft();
        if (mc == null || mc.level == null) {
            for (BlockPos clickedPos : targets) {
                if (clickedPos == null) {
                    continue;
                }
                BlockPos placePos = uniformPlacement
                        ? resolveUniformShapePlacementTargetPos(input, clickedPos, placementStack)
                        : resolvePlacementTargetPos(clickedPos, input.placementFace(), placementStack);
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
                    ? resolveUniformShapePlacementTargetPos(input, clickedPos, placementStack)
                    : resolvePlacementTargetPos(clickedPos, input.placementFace(), placementStack);
            if (placePos == null) {
                continue;
            }
            if (strictEmptyLock
                    && mc.level.hasChunkAt(placePos)
                    && !canReplaceForShapePlacement(placePos, input.placementFace(), placementStack)) {
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
            case LINE, SQUARE, WALL, CYLINDER, BALL, BOX -> true;
            default -> false;
        };
    }

    private BlockPos resolvePlacementTargetPos(BlockPos clickedPos, Direction face) {
        return resolvePlacementTargetPos(clickedPos, face, resolveShapePlacementStackForContext());
    }

    private BlockPos resolvePlacementTargetPos(BlockPos clickedPos, Direction face, ItemStack placementStack) {
        Minecraft mc = this.screen.getMinecraft();
        if (clickedPos == null || face == null || mc == null || mc.level == null) {
            return null;
        }
        if (!mc.level.hasChunkAt(clickedPos)) {
            return clickedPos;
        }
        return canReplaceForShapePlacement(clickedPos, face, placementStack) ? clickedPos : clickedPos.relative(face);
    }

    private BlockPos resolveUniformShapePlacementTargetPos(ShapeBuildTypes.Input input, BlockPos clickedPos) {
        return resolveUniformShapePlacementTargetPos(input, clickedPos, resolveShapePlacementStackForContext());
    }

    private BlockPos resolveUniformShapePlacementTargetPos(ShapeBuildTypes.Input input, BlockPos clickedPos,
                                                          ItemStack placementStack) {
        if (input == null || clickedPos == null) {
            return null;
        }
        BlockPos anchor = input.pointA();
        Direction face = input.placementFace();
        if (anchor == null || face == null) {
            return clickedPos;
        }
        BlockPos anchorPlaced = resolvePlacementTargetPos(anchor, face, placementStack);
        if (anchorPlaced == null) {
            return clickedPos;
        }
        return clickedPos.offset(
                anchorPlaced.getX() - anchor.getX(),
                anchorPlaced.getY() - anchor.getY(),
                anchorPlaced.getZ() - anchor.getZ());
    }

    private boolean canReplaceForShapePlacement(BlockPos clickedPos, Direction face, ItemStack placementStack) {
        Minecraft mc = this.screen.getMinecraft();
        if (clickedPos == null || face == null || mc == null || mc.level == null || !mc.level.hasChunkAt(clickedPos)) {
            return false;
        }
        BlockState state = mc.level.getBlockState(clickedPos);
        BlockPlaceContext context = createShapePlacementContext(clickedPos, face, placementStack);
        return context == null ? state.canBeReplaced() : state.canBeReplaced(context);
    }

    private BlockPlaceContext createShapePlacementContext(BlockPos clickedPos, Direction face, ItemStack placementStack) {
        Minecraft mc = this.screen.getMinecraft();
        if (clickedPos == null || face == null || placementStack == null || placementStack.isEmpty()
                || mc == null || mc.level == null || mc.player == null) {
            return null;
        }
        return new BlockPlaceContext(
                mc.level,
                mc.player,
                InteractionHand.MAIN_HAND,
                placementStack,
                ShapeGeometryUtil.createShapePlacementHit(clickedPos, face));
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

    private double currentMouseX() {
        return this.screen.getCurrentMouseX();
    }

    private double currentMouseY() {
        return this.screen.getCurrentMouseY();
    }

}
