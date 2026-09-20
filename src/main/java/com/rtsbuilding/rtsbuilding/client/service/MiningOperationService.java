package com.rtsbuilding.rtsbuilding.client.service;

import com.rtsbuilding.rtsbuilding.client.diagnostic.RtsClientOperationDiagnostics;
import com.rtsbuilding.rtsbuilding.client.network.RtsClientPacketGateway;
import com.rtsbuilding.rtsbuilding.client.plugin.RtsClientPluginCatalog;
import com.rtsbuilding.rtsbuilding.client.record.AreaMineBounds;
import com.rtsbuilding.rtsbuilding.client.screen.ultimine.AreaMineShape;
import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.common.shape.model.AreaShape;
import com.rtsbuilding.rtsbuilding.common.shape.model.ShapeFillMode;
import com.rtsbuilding.rtsbuilding.common.destruction.RtsConvenienceDestroyMode;
import com.rtsbuilding.rtsbuilding.common.destruction.RtsConvenienceDestroySettings;
import com.rtsbuilding.rtsbuilding.common.diagnostics.RtsMiningStopOrigin;
import com.rtsbuilding.rtsbuilding.common.diagnostics.RtsTraceInputKind;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;

public final class MiningOperationService {

    // =========================================================================
    //  Constants
    // =========================================================================

    private static final int RTS_MINE_RENDER_ID = 0x525453;

    /** Area mine phase: inactive */
    public static final int AREA_MINE_PHASE_NONE = 0;
    /** Area mine phase: waiting for second click to define the base rectangle */
    public static final int AREA_MINE_PHASE_NEED_SECOND = 1;
    /** Area mine phase: waiting for scroll-wheel height adjustment then confirm */
    public static final int AREA_MINE_PHASE_NEED_HEIGHT = 2;
    /** 仅为旧调用点保留的默认轴长；实际范围优先读取服务端同步轴配置。 */
    public static final int AREA_MINE_MAX_SIZE = 64;

    // =========================================================================
    //  Mining state fields
    // =========================================================================

    /** Currently active mining block position */
    private BlockPos activeMinePos;
    /** Face of the block currently being mined */
    private int activeMineFace = -1;
    /** Tool hotbar slot used for the current mining operation */
    private int activeMineToolSlot;
    private long activeMineTraceId;
    private long activeMineTraceStartedNanos;
    private RtsTraceInputKind activeMineInputKind = RtsTraceInputKind.UNKNOWN;

    /** Block break render progress position */
    private BlockPos mineRenderPos;
    /** Block break render progress stage */
    private int mineRenderStage = -1;

    /** Most recently completed mine progress position (for completion animation) */
    private BlockPos mineProgressCompletedPos;
    /** System timestamp of the most recent mine progress completion */
    private long mineProgressCompletedAtMs;

    /** Ultimine progress: how many targets have been processed */
    private int ultimineProgressProcessed = -1;
    /** Ultimine progress: total number of targets */
    private int ultimineProgressTotal;

    // =========================================================================
    //  Area mine state
    // =========================================================================

    /** Current area mine phase */
    private int areaMinePhase = AREA_MINE_PHASE_NONE;
    /** Anchor point A: first click position (also the Y reference plane) */
    private BlockPos areaMinePointA;
    /** Anchor point B: second click position, together with A defines the base rectangle */
    private BlockPos areaMinePointB;
    /** Height offset: extends up/down from point A Y (scroll wheel, positive=up, negative=down) */
    private int areaMineHeightOffset;

    /** Current area mine shape */
    private AreaMineShape areaMineShape = AreaMineShape.CHAIN;

    // =========================================================================
    //  Network callbacks
    // =========================================================================

    /**
     * Applies a block mine progress update from the server.
     * Updates the render destroy progress; a negative stage clears rendering.
     */
    public void applyMineProgress(BlockPos pos, int stage) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }

        if (stage < 0) {
            clearActiveMineTargetIfMatches(pos);
            clearMineProgressRender(pos);
            return;
        }

        if (this.mineRenderPos != null && !this.mineRenderPos.equals(pos)) {
            minecraft.level.destroyBlockProgress(RTS_MINE_RENDER_ID, this.mineRenderPos, -1);
        }
        minecraft.level.destroyBlockProgress(RTS_MINE_RENDER_ID, pos, Math.min(9, stage));
        this.mineRenderPos = pos.immutable();
        this.mineRenderStage = Math.min(9, stage);
    }

    private void rememberMineProgressCompleted(BlockPos pos) {
        this.mineProgressCompletedPos = pos == null ? null : pos.immutable();
        this.mineProgressCompletedAtMs = System.currentTimeMillis();
    }

    // =========================================================================
    //  Mining operation methods
    // =========================================================================

    /**
     * Starts mining a single block.
     */
    public void startMining(BlockPos pos, int face, int toolSlot,
                            String selectedItemId, ItemStack selectedItemPreview,
                            boolean allowPlacedBlockRecovery, boolean toolProtectionEnabled) {
        startMining(pos, face, toolSlot, selectedItemId, selectedItemPreview,
                allowPlacedBlockRecovery, toolProtectionEnabled,
                RtsTraceInputKind.UNKNOWN, "UNKNOWN_PRESS");
    }

    public void startMining(BlockPos pos, int face, int toolSlot,
                            String selectedItemId, ItemStack selectedItemPreview,
                            boolean allowPlacedBlockRecovery, boolean toolProtectionEnabled,
                            RtsTraceInputKind inputKind, String inputOrigin) {
        if (pos == null) {
            return;
        }
        RtsClientOperationDiagnostics.superseded(this.activeMineTraceId);
        RtsTraceInputKind input = inputKind == null ? RtsTraceInputKind.UNKNOWN : inputKind;
        RtsClientOperationDiagnostics.TraceStart trace = RtsClientOperationDiagnostics.begin(
                "MINE_SINGLE", input, inputOrigin == null ? "UNKNOWN_PRESS" : inputOrigin,
                "INTERACT", false, "BLOCK", 1);
        this.activeMineTraceId = trace.traceId();
        this.activeMineTraceStartedNanos = trace.startedNanos();
        this.activeMineInputKind = input;
        this.activeMinePos = pos.immutable();
        this.activeMineFace = face;
        this.activeMineToolSlot = Mth.clamp(toolSlot, 0, 8);
        this.mineRenderPos = this.activeMinePos;
        this.mineRenderStage = 0;
        RtsClientPacketGateway.sendMineStart(
                this.activeMinePos,
                face,
                this.activeMineToolSlot,
                selectedMiningToolItemId(selectedItemId, selectedItemPreview),
                selectedMiningToolPrototype(selectedItemId, selectedItemPreview),
                allowPlacedBlockRecovery,
                toolProtectionEnabled, this.activeMineTraceId, input);
    }

    /**
     * Starts a chain (ultimine) mining operation.
     */
    public void startUltimine(BlockPos pos, int face, int toolSlot, int limit, byte mode,
                              String selectedItemId, ItemStack selectedItemPreview,
                              boolean toolProtectionEnabled) {
        startUltimine(pos, face, toolSlot, limit, mode, selectedItemId, selectedItemPreview,
                toolProtectionEnabled, RtsTraceInputKind.UNKNOWN, "UNKNOWN_PRESS");
    }

    public void startUltimine(BlockPos pos, int face, int toolSlot, int limit, byte mode,
                              String selectedItemId, ItemStack selectedItemPreview,
                              boolean toolProtectionEnabled, RtsTraceInputKind inputKind,
                              String inputOrigin) {
        if (pos == null) {
            return;
        }
        RtsClientOperationDiagnostics.superseded(this.activeMineTraceId);
        RtsTraceInputKind input = inputKind == null ? RtsTraceInputKind.UNKNOWN : inputKind;
        RtsClientOperationDiagnostics.TraceStart trace = RtsClientOperationDiagnostics.begin(
                "ULTIMINE", input, inputOrigin == null ? "UNKNOWN_PRESS" : inputOrigin,
                "ULTIMINE", true, "BLOCK", Math.max(0, limit));
        this.activeMineTraceId = trace.traceId();
        this.activeMineTraceStartedNanos = trace.startedNanos();
        this.activeMineInputKind = input;
        this.activeMinePos = pos.immutable();
        this.activeMineFace = face;
        this.activeMineToolSlot = Mth.clamp(toolSlot, 0, 8);
        this.mineRenderPos = this.activeMinePos;
        this.mineRenderStage = 0;
        RtsClientPacketGateway.sendUltimineStart(
                this.activeMinePos,
                face,
                this.activeMineToolSlot,
                selectedMiningToolItemId(selectedItemId, selectedItemPreview),
                selectedMiningToolPrototype(selectedItemId, selectedItemPreview),
                limit,
                mode,
                toolProtectionEnabled, this.activeMineTraceId, input);
    }

    /** 提交声明式便捷破坏请求；预览坐标不会进入协议，服务端会重新规划。 */
    public void confirmConvenienceDestroy(
            RtsConvenienceDestroyMode mode,
            BlockHitResult hit,
            RtsConvenienceDestroySettings settings,
            int toolSlot,
            String selectedItemId,
            ItemStack selectedItemPreview,
            boolean toolProtectionEnabled) {
        confirmConvenienceDestroy(mode, hit, settings, toolSlot, selectedItemId,
                selectedItemPreview, toolProtectionEnabled, RtsTraceInputKind.UNKNOWN);
    }

    public void confirmConvenienceDestroy(
            RtsConvenienceDestroyMode mode,
            BlockHitResult hit,
            RtsConvenienceDestroySettings settings,
            int toolSlot,
            String selectedItemId,
            ItemStack selectedItemPreview,
            boolean toolProtectionEnabled,
            RtsTraceInputKind inputKind) {
        if (mode == null || hit == null) {
            return;
        }
        RtsClientOperationDiagnostics.superseded(this.activeMineTraceId);
        RtsTraceInputKind input = inputKind == null ? RtsTraceInputKind.UNKNOWN : inputKind;
        RtsClientOperationDiagnostics.TraceStart trace = RtsClientOperationDiagnostics.begin(
                "CONVENIENCE_DESTROY_" + mode.name(), input, "CONFIRM",
                mode.name(), false, "SERVER_PLAN", 0);
        this.activeMineTraceId = trace.traceId();
        this.activeMineTraceStartedNanos = trace.startedNanos();
        this.activeMineInputKind = input;
        BlockPos anchor = hit.getBlockPos().immutable();
        this.activeMinePos = anchor;
        this.activeMineFace = hit.getDirection().get3DDataValue();
        this.activeMineToolSlot = Mth.clamp(toolSlot, 0, 8);
        this.mineRenderPos = anchor;
        this.mineRenderStage = 0;
        RtsClientPacketGateway.sendConvenienceDestroy(
                System.nanoTime(), mode, anchor, hit.getDirection(), settings,
                this.activeMineToolSlot,
                selectedMiningToolItemId(selectedItemId, selectedItemPreview),
                selectedMiningToolPrototype(selectedItemId, selectedItemPreview),
                toolProtectionEnabled, this.activeMineTraceId, input);
        clearAreaMineSession();
    }

    /** Mining progress is maintained server-side; the client does not need to send packets every tick. */
    public void continueMining(int toolSlot) {
        // no-op
    }

    /**
     * Aborts the current mining operation.
     */
    public void abortMining(int toolSlot) {
        abortMining(toolSlot, RtsMiningStopOrigin.EXPLICIT_CANCEL);
    }

    public void abortMining(int toolSlot, RtsMiningStopOrigin origin) {
        BlockPos abortPos = this.activeMinePos;
        int abortFace = this.activeMineFace;
        if (abortPos != null && abortFace >= 0) {
            int heldMs = activeMineTraceStartedNanos <= 0L
                    ? 0 : (int) Math.min(Integer.MAX_VALUE,
                            Math.max(0L, (System.nanoTime() - activeMineTraceStartedNanos) / 1_000_000L));
            if (this.activeMineTraceId != 0L) {
                RtsClientPacketGateway.sendMineAbort(
                        abortPos, abortFace, toolSlot, this.activeMineTraceId, heldMs,
                        this.activeMineInputKind, origin);
            } else {
                RtsClientPacketGateway.sendMineAbort(abortPos, abortFace, toolSlot);
            }
        }
        this.activeMineTraceId = 0L;
        this.activeMineTraceStartedNanos = 0L;
        this.activeMineInputKind = RtsTraceInputKind.UNKNOWN;
        this.activeMinePos = null;
        this.activeMineFace = -1;
        clearMineProgressRender(abortPos);
    }

    // =========================================================================
    //  Area mine operation methods
    // =========================================================================

    // ---------- State queries ----------

    public int getAreaMinePhase() {
        return this.areaMinePhase;
    }

    public BlockPos getAreaMinePointA() {
        return this.areaMinePointA;
    }

    public BlockPos getAreaMinePointB() {
        return this.areaMinePointB;
    }

    public int getAreaMineHeightOffset() {
        return this.areaMineHeightOffset;
    }

    // ---------- Bounds computation ----------

    /**
     * Computes the full 3D bounding box for an area mine based on two diagonal points and height offset.
     * <p>Uses pointA as the anchor:
     * <ul>
     *   <li>X/Z direction: determined by pointB, clamped to the synchronized axis spans</li>
     *   <li>Y direction: baseY + heightOffset, then clamped to the synchronized Y span</li>
     * </ul>
     *
     * @param pointA       anchor point A
     * @param pointB       diagonal point B
     * @param heightOffset height offset (positive=upward, negative=downward, 0=single base layer)
     * @return the clamped boundary result
     */
    public static AreaMineBounds computeAreaMineBounds(BlockPos pointA, BlockPos pointB, int heightOffset) {
        if (pointA == null || pointB == null) {
            return null;
        }
        int maxWidth = configInt(Config::areaMineMaxWidth, AREA_MINE_MAX_SIZE);
        int maxHeight = configInt(Config::areaMineMaxHeight, AREA_MINE_MAX_SIZE);
        int maxDepth = configInt(Config::areaMineMaxDepth, AREA_MINE_MAX_SIZE);
        int maxVolume = configInt(Config::areaMineMaxVolume,
                com.rtsbuilding.rtsbuilding.common.mining.MiningLimits.DEFAULT_VOLUME);

        long dx = Math.min(Math.abs((long) pointB.getX() - pointA.getX()), (long) maxWidth - 1L);
        int minX = pointB.getX() >= pointA.getX() ? pointA.getX() : safeAdd(pointA.getX(), -dx);
        int maxX = pointB.getX() >= pointA.getX() ? safeAdd(pointA.getX(), dx) : pointA.getX();

        long dz = Math.min(Math.abs((long) pointB.getZ() - pointA.getZ()), (long) maxDepth - 1L);
        int minZ = pointB.getZ() >= pointA.getZ() ? pointA.getZ() : safeAdd(pointA.getZ(), -dz);
        int maxZ = pointB.getZ() >= pointA.getZ() ? safeAdd(pointA.getZ(), dz) : pointA.getZ();

        int baseY = pointA.getY();
        long boundedHeightOffset = Math.max(-(long) maxHeight + 1L,
                Math.min((long) maxHeight - 1L, (long) heightOffset));
        int minY = safeAdd(baseY, Math.min(0L, boundedHeightOffset));
        int maxY = safeAdd(baseY, Math.max(0L, boundedHeightOffset));

        return clampAreaMineBounds(new AreaMineBounds(minX, maxX, minY, maxY, minZ, maxZ),
                maxWidth, maxHeight, maxDepth, maxVolume);
    }

    // ---------- Height setting ----------

    public void setAreaMineHeightOffset(int offset) {
        int maxHeight = configInt(Config::areaMineMaxHeight, AREA_MINE_MAX_SIZE);
        this.areaMineHeightOffset = Math.max(-(maxHeight - 1), Math.min(maxHeight - 1, offset));
    }

    public void adjustAreaMineHeightOffset(int delta) {
        long next = (long) this.areaMineHeightOffset + delta;
        setAreaMineHeightOffset(next <= Integer.MIN_VALUE ? Integer.MIN_VALUE
                : next >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) next);
    }

    // ---------- Selection management ----------

    public void setAreaMinePointA(BlockPos pos, double anchorX, double anchorZ, double maxRadius, boolean hasBounds) {
        this.areaMinePointA = pos == null ? null : clampToBounds(pos.immutable(), anchorX, anchorZ, maxRadius, hasBounds);
        this.areaMinePointB = null;
        this.areaMineHeightOffset = 0;
        this.areaMinePhase = pos == null ? AREA_MINE_PHASE_NONE : AREA_MINE_PHASE_NEED_SECOND;
        this.mineRenderPos = this.areaMinePointA;
        this.mineRenderStage = 0;
    }

    public void setAreaMinePointB(BlockPos pos, double anchorX, double anchorZ, double maxRadius, boolean hasBounds) {
        this.areaMinePointB = pos == null ? null : clampToBounds(pos.immutable(), anchorX, anchorZ, maxRadius, hasBounds);
        this.areaMineHeightOffset = 0;
        this.areaMinePhase = pos == null ? AREA_MINE_PHASE_NONE : AREA_MINE_PHASE_NEED_HEIGHT;
        this.mineRenderPos = this.areaMinePointB;
        this.mineRenderStage = 0;
    }

    private BlockPos clampToBounds(BlockPos pos, double anchorX, double anchorZ, double maxRadius, boolean hasBounds) {
        if (pos == null || !hasBounds) {
            return pos;
        }
        int minBlockX = Mth.floor(anchorX - maxRadius);
        int maxBlockX = Mth.ceil(anchorX + maxRadius) - 1;
        int minBlockZ = Mth.floor(anchorZ - maxRadius);
        int maxBlockZ = Mth.ceil(anchorZ + maxRadius) - 1;
        return new BlockPos(
                Mth.clamp(pos.getX(), minBlockX, maxBlockX),
                pos.getY(),
                Mth.clamp(pos.getZ(), minBlockZ, maxBlockZ));
    }

    public void clearAreaMineSession() {
        this.areaMinePhase = AREA_MINE_PHASE_NONE;
        this.areaMinePointA = null;
        this.areaMinePointB = null;
        this.areaMineHeightOffset = 0;
        this.mineRenderStage = -1;
    }

    public void confirmAreaMine(int toolSlot, ShapeFillMode fillMode,
                                String selectedItemId, ItemStack selectedItemPreview,
                                boolean toolProtectionEnabled) {
        confirmAreaMine(toolSlot, fillMode, selectedItemId, selectedItemPreview,
                toolProtectionEnabled, RtsTraceInputKind.UNKNOWN);
    }

    public void confirmAreaMine(int toolSlot, ShapeFillMode fillMode,
                                String selectedItemId, ItemStack selectedItemPreview,
                                boolean toolProtectionEnabled, RtsTraceInputKind inputKind) {
        if (this.areaMinePointA == null || this.areaMinePointB == null) {
            return;
        }
        AreaMineBounds bounds = computeAreaMineBounds(
                this.areaMinePointA, this.areaMinePointB, this.areaMineHeightOffset);

        this.activeMinePos = this.areaMinePointA.immutable();
        this.activeMineFace = Direction.UP.get3DDataValue();
        this.activeMineToolSlot = Mth.clamp(toolSlot, 0, 8);
        this.mineRenderPos = this.activeMinePos;
        this.mineRenderStage = 0;

        RtsClientOperationDiagnostics.superseded(this.activeMineTraceId);
        RtsTraceInputKind input = inputKind == null ? RtsTraceInputKind.UNKNOWN : inputKind;
        int targetCount = boundedVolume(bounds);
        RtsClientOperationDiagnostics.TraceStart trace = RtsClientOperationDiagnostics.begin(
                "AREA_MINE", input, "CONFIRM", "AREA", false, "BLOCK", targetCount);
        this.activeMineTraceId = trace.traceId();
        this.activeMineTraceStartedNanos = trace.startedNanos();
        this.activeMineInputKind = input;

        RtsClientPacketGateway.sendAreaMine(
                bounds.minX(), bounds.maxX(), bounds.minY(), bounds.maxY(),
                bounds.minZ(), bounds.maxZ(),
                this.activeMineToolSlot,
                selectedMiningToolItemId(selectedItemId, selectedItemPreview),
                selectedMiningToolPrototype(selectedItemId, selectedItemPreview),
                areaShapeOrdinal(this.areaMineShape),
                (byte) (fillMode == null ? ShapeFillMode.FILL : fillMode).ordinal(),
                toolProtectionEnabled, this.activeMineTraceId, input);

        clearAreaMineSession();
    }

    public void confirmShapeAreaDestroy(List<BlockPos> targets, int toolSlot,
                                        String selectedItemId, ItemStack selectedItemPreview,
                                        boolean toolProtectionEnabled) {
        confirmShapeAreaDestroy(targets, toolSlot, selectedItemId, selectedItemPreview,
                toolProtectionEnabled, RtsTraceInputKind.UNKNOWN);
    }

    public void confirmShapeAreaDestroy(List<BlockPos> targets, int toolSlot,
                                        String selectedItemId, ItemStack selectedItemPreview,
                                        boolean toolProtectionEnabled, RtsTraceInputKind inputKind) {
        if (targets == null || targets.isEmpty()) {
            return;
        }
        BlockPos first = targets.get(0).immutable();
        this.activeMinePos = first;
        this.activeMineFace = Direction.UP.get3DDataValue();
        this.activeMineToolSlot = Mth.clamp(toolSlot, 0, 8);
        this.mineRenderPos = first;
        this.mineRenderStage = 0;
        RtsClientOperationDiagnostics.superseded(this.activeMineTraceId);
        RtsTraceInputKind input = inputKind == null ? RtsTraceInputKind.UNKNOWN : inputKind;
        RtsClientOperationDiagnostics.TraceStart trace = RtsClientOperationDiagnostics.begin(
                "AREA_DESTROY", input, "CONFIRM", "AREA", false, "BLOCK", targets.size());
        this.activeMineTraceId = trace.traceId();
        this.activeMineTraceStartedNanos = trace.startedNanos();
        this.activeMineInputKind = input;
        RtsClientPacketGateway.sendAreaDestroy(
                targets,
                this.activeMineToolSlot,
                selectedMiningToolItemId(selectedItemId, selectedItemPreview),
                selectedMiningToolPrototype(selectedItemId, selectedItemPreview),
                toolProtectionEnabled, this.activeMineTraceId, input);
        clearAreaMineSession();
    }

    // =========================================================================
    //  Utility methods
    // =========================================================================

    private String selectedMiningToolItemId(String selectedItemId, ItemStack selectedItemPreview) {
        ItemStack prototype = selectedMiningToolPrototype(selectedItemId, selectedItemPreview);
        if (prototype.isEmpty()) {
            return "";
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(prototype.getItem());
        return id == null ? "" : id.toString();
    }

    private ItemStack selectedMiningToolPrototype(String selectedItemId, ItemStack selectedItemPreview) {
        if (selectedItemId == null || selectedItemId.isBlank() || selectedItemPreview == null || selectedItemPreview.isEmpty()) {
            return ItemStack.EMPTY;
        }
        if (selectedItemPreview.getItem() instanceof BlockItem
                || RtsClientPluginCatalog.isPluginItem(selectedItemPreview)) {
            return ItemStack.EMPTY;
        }
        ItemStack prototype = selectedItemPreview.copy();
        prototype.setCount(1);
        return prototype;
    }

    private static byte areaShapeOrdinal(AreaMineShape shape) {
        AreaShape areaShape = switch (shape == null ? AreaMineShape.BLOCK : shape) {
            case LINE -> AreaShape.LINE;
            case SQUARE -> AreaShape.SQUARE;
            case WALL -> AreaShape.WALL;
            case CIRCLE -> AreaShape.CIRCLE;
            case BOX -> AreaShape.BOX;
            case CYLINDER -> AreaShape.CYLINDER;
            case BALL -> AreaShape.BALL;
            case BLOCK, CHAIN -> AreaShape.BLOCK;
        };
        return (byte) areaShape.ordinal();
    }

    private static AreaMineBounds clampAreaMineBounds(
            AreaMineBounds bounds, int maxWidth, int maxHeight, int maxDepth, int maxVolume) {
        int minX = Math.min(bounds.minX(), bounds.maxX());
        int maxX = Math.max(bounds.minX(), bounds.maxX());
        int minY = Math.min(bounds.minY(), bounds.maxY());
        int maxY = Math.max(bounds.minY(), bounds.maxY());
        int minZ = Math.min(bounds.minZ(), bounds.maxZ());
        int maxZ = Math.max(bounds.minZ(), bounds.maxZ());
        long width = (long) maxX - minX + 1L;
        long height = (long) maxY - minY + 1L;
        long depth = (long) maxZ - minZ + 1L;
        var size = com.rtsbuilding.rtsbuilding.common.mining.MiningLimits.clampDimensions(
                (int) Math.min(width, Math.max(1L, maxWidth)),
                (int) Math.min(height, Math.max(1L, maxHeight)),
                (int) Math.min(depth, Math.max(1L, maxDepth)), maxVolume);
        return new AreaMineBounds(minX, safeAdd(minX, size.width() - 1L),
                minY, safeAdd(minY, size.height() - 1L), minZ, safeAdd(minZ, size.depth() - 1L));
    }

    private static int safeAdd(int base, long offset) {
        long value = (long) base + offset;
        return value <= Integer.MIN_VALUE ? Integer.MIN_VALUE
                : value >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
    }

    private static int configInt(java.util.function.IntSupplier supplier, int fallback) {
        try {
            return Math.max(1, supplier.getAsInt());
        } catch (IllegalStateException ignored) {
            return fallback;
        }
    }

    private static int boundedVolume(AreaMineBounds bounds) {
        if (bounds == null) return 0;
        long width = (long) bounds.maxX() - bounds.minX() + 1L;
        long height = (long) bounds.maxY() - bounds.minY() + 1L;
        long depth = (long) bounds.maxZ() - bounds.minZ() + 1L;
        long volume = width > Long.MAX_VALUE / Math.max(1L, height)
                ? Long.MAX_VALUE : width * height;
        volume = volume > Long.MAX_VALUE / Math.max(1L, depth)
                ? Long.MAX_VALUE : volume * depth;
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, volume));
    }

    // =========================================================================
    //  Progress queries
    // =========================================================================

    public int getMineProgressStage() {
        return this.mineRenderStage;
    }

    public BlockPos getMineProgressPos() {
        return this.mineRenderPos;
    }

    public BlockPos getMineProgressCompletedPos() {
        return this.mineProgressCompletedPos;
    }

    public long getMineProgressCompletedAtMs() {
        return this.mineProgressCompletedAtMs;
    }

    public int getUltimineProgressProcessed() {
        return this.ultimineProgressProcessed;
    }

    public int getUltimineProgressTotal() {
        return this.ultimineProgressTotal;
    }

    /**
     * Applies an ultimine progress update from the server.
     * See {@link com.rtsbuilding.rtsbuilding.network.builder.S2CRtsUltimineProgressPayload}.
     */
    public void applyUltimineProgress(int processed, int total) {
        this.ultimineProgressProcessed = processed;
        this.ultimineProgressTotal = total;
    }

    // =========================================================================
    //  Shape access
    // =========================================================================

    public AreaMineShape getAreaMineShape() {
        return this.areaMineShape;
    }

    public void setAreaMineShape(AreaMineShape shape) {
        this.areaMineShape = shape == null ? AreaMineShape.CHAIN : shape;
    }

    // =========================================================================
    //  State reset (called by Controller on enable/disable/death)
    // =========================================================================

    /** Clears all mining state (does not handle render cleanup). */
    public void clearMiningState() {
        RtsClientOperationDiagnostics.reset("CLIENT_RESET");
        this.activeMineTraceId = 0L;
        this.activeMineTraceStartedNanos = 0L;
        this.activeMineInputKind = RtsTraceInputKind.UNKNOWN;
        this.activeMinePos = null;
        this.activeMineFace = -1;
        this.mineRenderPos = null;
        this.mineRenderStage = -1;
        this.ultimineProgressProcessed = -1;
        this.ultimineProgressTotal = 0;
    }

    /** Clears mining render (including destroyBlockProgress) and resets all mining state. */
    public void clearMiningRenderState() {
        clearMineProgressRender(this.mineRenderPos);
        clearMiningState();
    }

    private void clearMineProgressRender(BlockPos fallbackPos) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null
                && this.mineRenderPos != null
                && (fallbackPos == null || this.mineRenderPos.equals(fallbackPos))) {
            minecraft.level.destroyBlockProgress(RTS_MINE_RENDER_ID, this.mineRenderPos, -1);
            this.mineRenderPos = null;
            this.mineRenderStage = -1;
        } else if (minecraft.level != null && fallbackPos != null) {
            minecraft.level.destroyBlockProgress(RTS_MINE_RENDER_ID, fallbackPos, -1);
            if (fallbackPos.equals(this.mineRenderPos)) {
                this.mineRenderPos = null;
                this.mineRenderStage = -1;
            }
        } else if (fallbackPos == null) {
            this.mineRenderPos = null;
            this.mineRenderStage = -1;
        }
    }

    private void clearActiveMineTargetIfMatches(BlockPos pos) {
        if (pos == null || !pos.equals(this.activeMinePos)) {
            return;
        }
        this.activeMinePos = null;
        this.activeMineFace = -1;
    }
}
