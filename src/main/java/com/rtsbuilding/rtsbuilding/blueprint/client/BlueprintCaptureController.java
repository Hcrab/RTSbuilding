package com.rtsbuilding.rtsbuilding.blueprint.client;

import com.rtsbuilding.rtsbuilding.client.screen.culling.RtsCullingBox;
import com.rtsbuilding.rtsbuilding.client.screen.culling.RtsBoxHandleInteraction;
import com.rtsbuilding.rtsbuilding.client.screen.selection.RtsSelectionBoxAnimator;
import com.rtsbuilding.rtsbuilding.blueprint.format.BlueprintWriters;
import com.rtsbuilding.rtsbuilding.blueprint.RtsBlueprint;
import com.rtsbuilding.rtsbuilding.blueprint.network.S2CBlueprintStatusPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.rtsbuilding.rtsbuilding.blueprint.client.BlueprintCaptureGeometry.*;
import static com.rtsbuilding.rtsbuilding.blueprint.client.BlueprintPanelFiles.stripNbtExtension;

/**
 * Owns the mutable state for selecting and saving a blueprint capture region.
 */
public final class BlueprintCaptureController {
    private final RtsBoxHandleInteraction handleInteraction = new RtsBoxHandleInteraction();
    private final RtsSelectionBoxAnimator boxAnimator = new RtsSelectionBoxAnimator();
    private boolean active = false;
    private BlockPos firstWorldCorner = null;
    private BlockPos pointA = null;
    private BlockPos pointB = null;
    private BlockPos hoverPoint = null;
    private BlueprintCaptureSaveJob saveJob = null;
    private final Set<BlockPos> excludedBlocks = new HashSet<>();

    boolean isActive() {
        return active;
    }

    boolean isSaving() {
        return saveJob != null;
    }

    boolean isSelectionComplete() {
        return active && pointA != null && pointB != null;
    }

    BlockPos pointA() {
        return pointA;
    }

    BlockPos pointB() {
        return pointB;
    }

    BlockPos displayPointA() {
        RtsCullingBox box = previewBox();
        if (box != null) {
            return box.min();
        }
        return firstWorldCorner;
    }

    BlockPos displayPointB() {
        RtsCullingBox box = previewBox();
        return box == null ? null : box.max();
    }

    Direction hoveredHandleDirection() {
        return handleInteraction.hoveredDirection();
    }

    Direction activeHandleDirection() {
        return handleInteraction.activeDirection();
    }

    RtsCullingBox selectionBox() {
        if (pointA == null || pointB == null) {
            return null;
        }
        int minX = Math.min(pointA.getX(), pointB.getX());
        int minY = Math.min(pointA.getY(), pointB.getY()) + 1;
        int minZ = Math.min(pointA.getZ(), pointB.getZ());
        int maxX = Math.max(pointA.getX(), pointB.getX());
        int maxY = Math.max(pointA.getY(), pointB.getY());
        int maxZ = Math.max(pointA.getZ(), pointB.getZ());
        if (minY > maxY) {
            return null;
        }
        return new RtsCullingBox(1, new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ));
    }

    RtsCullingBox previewBox() {
        RtsCullingBox complete = selectionBox();
        if (complete != null) {
            return complete;
        }
        if (firstWorldCorner == null) {
            return null;
        }
        BlockPos second = hoverPoint == null ? firstWorldCorner : hoverPoint;
        return new RtsCullingBox(1, firstWorldCorner, second);
    }

    AABB previewAabbForRender() {
        RtsCullingBox box = previewBox();
        return box == null ? null : boxAnimator.renderAabb(box);
    }

    void updateHoverPoint(BlockPos pos) {
        hoverPoint = pos == null ? null : pos.immutable();
    }

    void updateHandleHover(Vec3 origin, Vec3 direction) {
        handleInteraction.updateHover(selectionBox(), origin, direction, active);
    }

    BlockPos previewPointB() {
        BlockPos display = displayPointB();
        return display != null ? display : hoverPoint;
    }

    String sizeText() {
        return boxSizeText(previewBox());
    }

    boolean shouldRenderPreviewFill() {
        return active && pointB != null;
    }

    boolean shouldRenderBlockHighlights(int limit) {
        if (!shouldRenderPreviewFill() || limit <= 0 || pointA == null || pointB == null) {
            return false;
        }
        long volume = captureVolume(pointA, pointB);
        return volume > 0L && volume <= limit;
    }

    List<BlockPos> includedBlocksForRender(Level level, int limit) {
        if (level == null || !shouldRenderBlockHighlights(limit)) {
            return List.of();
        }
        long volume = captureVolume(pointA, pointB);
        List<BlockPos> blocks = new ArrayList<>((int) volume);
        int minX = Math.min(pointA.getX(), pointB.getX());
        int minY = Math.min(pointA.getY(), pointB.getY()) + 1;
        int minZ = Math.min(pointA.getZ(), pointB.getZ());
        int maxX = Math.max(pointA.getX(), pointB.getX());
        int maxY = Math.max(pointA.getY(), pointB.getY());
        int maxZ = Math.max(pointA.getZ(), pointB.getZ());
        if (minY > maxY) {
            return List.of();
        }
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = minY; y <= maxY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int x = minX; x <= maxX; x++) {
                    cursor.set(x, y, z);
                    if (excludedBlocks.contains(cursor) || !level.hasChunkAt(cursor)) {
                        continue;
                    }
                    BlockState state = level.getBlockState(cursor);
                    if (!state.isAir() && !state.is(Blocks.STRUCTURE_VOID)) {
                        blocks.add(cursor.immutable());
                    }
                }
            }
        }
        return blocks;
    }

    List<BlockPos> excludedBlocksForRender(int limit) {
        if (!active || pointA == null || pointB == null || limit <= 0) {
            return List.of();
        }
        List<BlockPos> blocks = new ArrayList<>(Math.min(limit, excludedBlocks.size()));
        for (BlockPos pos : excludedBlocks) {
            if (blocks.size() >= limit) {
                break;
            }
            if (isInsideSelection(pointA, pointB, pos)) {
                blocks.add(pos);
            }
        }
        return blocks;
    }

    boolean toggle(StatusSink status) {
        if (saveJob != null) {
            status.set(S2CBlueprintStatusPayload.INFO, "screen.rtsbuilding.blueprints.status.save_busy", "");
            return active;
        }
        if (active) {
            cancel(status);
            return false;
        }
        start(status);
        return true;
    }

    void start(StatusSink status) {
        active = true;
        resetSelection();
        status.set(S2CBlueprintStatusPayload.INFO, "screen.rtsbuilding.blueprints.status.capture_start", "");
    }

    void cancel(StatusSink status) {
        if (saveJob != null) {
            status.set(S2CBlueprintStatusPayload.INFO, "screen.rtsbuilding.blueprints.status.save_busy", "");
            return;
        }
        resetSelection();
        active = false;
        status.set(S2CBlueprintStatusPayload.INFO, "screen.rtsbuilding.blueprints.status.capture_cancelled", "");
    }

    void clearSilently() {
        saveJob = null;
        resetSelection();
        active = false;
    }

    boolean releaseActiveHandle() {
        return handleInteraction.releaseActiveHandle();
    }

    boolean releaseActiveHandleIfDragged() {
        return handleInteraction.releaseActiveHandleIfDragged();
    }

    boolean handleWorldAction(BlockHitResult hit, Vec3 origin, Vec3 direction, StatusSink status) {
        if (!active) {
            return false;
        }
        if (saveJob != null) {
            status.set(S2CBlueprintStatusPayload.INFO, "screen.rtsbuilding.blueprints.status.save_busy", "");
            return true;
        }
        RtsBoxHandleInteraction.ClickResult handleClick = handleInteraction.clickHandle(selectionBox(), origin, direction);
        if (handleClick.handled()) {
            if (handleClick.kind() == RtsBoxHandleInteraction.ClickKind.RELEASED) {
                status.set(S2CBlueprintStatusPayload.INFO,
                        "screen.rtsbuilding.blueprints.status.capture_handle_released", "");
                return true;
            }
            status.set(S2CBlueprintStatusPayload.INFO,
                    "screen.rtsbuilding.blueprints.status.capture_handle_selected",
                    handleClick.direction().getName());
            return true;
        }
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) {
            return true;
        }
        if (pointB == null) {
            return acceptPoint(hit.getBlockPos(), status);
        }
        if (toggleBlockExclusion(hit.getBlockPos(), status)) {
            return true;
        }
        status.set(S2CBlueprintStatusPayload.INFO, "screen.rtsbuilding.blueprints.status.capture_adjust_hint",
                sizeText());
        return true;
    }

    boolean handleScroll(double scrollY, boolean fast, StatusSink status) {
        if (!active) {
            return false;
        }
        if (saveJob != null) {
            status.set(S2CBlueprintStatusPayload.INFO, "screen.rtsbuilding.blueprints.status.save_busy", "");
            return true;
        }
        return handleInteraction.handleScroll(scrollY, fast, (direction, delta) -> {
            adjustSelectionFromHandle(direction, delta, status);
            return true;
        });
    }

    boolean handleDrag(double dragX, double dragY, double axisX, double axisY, StatusSink status) {
        if (!active) {
            return false;
        }
        if (saveJob != null) {
            status.set(S2CBlueprintStatusPayload.INFO, "screen.rtsbuilding.blueprints.status.save_busy", "");
            return true;
        }
        return handleInteraction.handleDrag(dragX, dragY, axisX, axisY, (direction, delta) -> {
            adjustSelectionFromHandle(direction, delta, status);
            return true;
        });
    }

    boolean acceptPoint(BlockPos pos, StatusSink status) {
        if (!active || pos == null) {
            return false;
        }
        if (saveJob != null) {
            status.set(S2CBlueprintStatusPayload.INFO, "screen.rtsbuilding.blueprints.status.save_busy", "");
            return true;
        }
        if (pointB != null) {
            status.set(S2CBlueprintStatusPayload.INFO, "screen.rtsbuilding.blueprints.status.capture_adjust_hint",
                    sizeText());
            return false;
        }
        if (firstWorldCorner == null) {
            firstWorldCorner = pos.immutable();
            pointA = pos.below().immutable();
            pointB = null;
            hoverPoint = pos.immutable();
            excludedBlocks.clear();
            status.set(S2CBlueprintStatusPayload.INFO, "screen.rtsbuilding.blueprints.status.capture_a", "");
        } else {
            applySelectionBox(new RtsCullingBox(1, firstWorldCorner, pos.immutable()));
            excludedBlocks.clear();
            status.set(S2CBlueprintStatusPayload.SUCCESS, "screen.rtsbuilding.blueprints.status.capture_b",
                    sizeText());
        }
        return true;
    }

    boolean confirmSingleBlockSelection(StatusSink status) {
        if (!active || pointB != null || firstWorldCorner == null) {
            return false;
        }
        applySelectionBox(new RtsCullingBox(1, firstWorldCorner, firstWorldCorner));
        status.set(S2CBlueprintStatusPayload.SUCCESS, "screen.rtsbuilding.blueprints.status.capture_b", sizeText());
        return true;
    }

    boolean toggleBlockExclusion(BlockPos pos, StatusSink status) {
        if (!active || pointA == null || pointB == null || pos == null) {
            return false;
        }
        if (saveJob != null) {
            status.set(S2CBlueprintStatusPayload.INFO, "screen.rtsbuilding.blueprints.status.save_busy", "");
            return true;
        }
        if (!isInsideSelection(pointA, pointB, pos)) {
            return false;
        }
        BlockPos key = pos.immutable();
        if (excludedBlocks.remove(key)) {
            status.set(S2CBlueprintStatusPayload.SUCCESS,
                    "screen.rtsbuilding.blueprints.status.capture_block_included", shortPos(key));
        } else {
            excludedBlocks.add(key);
            status.set(S2CBlueprintStatusPayload.INFO,
                    "screen.rtsbuilding.blueprints.status.capture_block_excluded", shortPos(key));
        }
        return true;
    }

    void moveSelection(int deltaY, StatusSink status) {
        moveSelection(0, deltaY, 0, status);
    }

    void moveSelection(int deltaX, int deltaY, int deltaZ, StatusSink status) {
        if (saveJob != null) {
            status.set(S2CBlueprintStatusPayload.INFO, "screen.rtsbuilding.blueprints.status.save_busy", "");
            return;
        }
        if (pointA == null || (deltaX == 0 && deltaY == 0 && deltaZ == 0)) {
            status.set(S2CBlueprintStatusPayload.ERROR, "screen.rtsbuilding.blueprints.status.capture_incomplete", "");
            return;
        }
        RtsCullingBox previous = selectionBox();
        if (firstWorldCorner != null) {
            firstWorldCorner = firstWorldCorner.offset(deltaX, deltaY, deltaZ);
        }
        pointA = pointA.offset(deltaX, deltaY, deltaZ);
        if (pointB != null) {
            pointB = pointB.offset(deltaX, deltaY, deltaZ);
        }
        if (hoverPoint != null) {
            hoverPoint = hoverPoint.offset(deltaX, deltaY, deltaZ);
        }
        if (!excludedBlocks.isEmpty()) {
            Set<BlockPos> moved = new HashSet<>();
            for (BlockPos pos : excludedBlocks) {
                moved.add(pos.offset(deltaX, deltaY, deltaZ));
            }
            excludedBlocks.clear();
            excludedBlocks.addAll(moved);
        }
        RtsCullingBox movedBox = selectionBox();
        boxAnimator.animate(previous, movedBox);
        status.set(S2CBlueprintStatusPayload.INFO, "screen.rtsbuilding.blueprints.status.capture_moved", sizeText());
    }

    void expandVertical(int deltaY, StatusSink status) {
        adjustSelectionFromHandle(Direction.UP, deltaY, status);
    }

    void resizeSelection(int deltaX, int deltaY, int deltaZ, StatusSink status) {
        if (pointA == null || pointB == null || (deltaX == 0 && deltaY == 0 && deltaZ == 0)) {
            status.set(S2CBlueprintStatusPayload.ERROR, "screen.rtsbuilding.blueprints.status.capture_incomplete", "");
            return;
        }
        int nextX = Math.max(1, sizeX() + deltaX);
        int nextY = Math.max(1, sizeY() + deltaY);
        int nextZ = Math.max(1, sizeZ() + deltaZ);
        setSelectionSize(nextX, nextY, nextZ, status);
    }

    void setSelectionSize(int sizeX, int sizeY, int sizeZ, StatusSink status) {
        if (saveJob != null) {
            status.set(S2CBlueprintStatusPayload.INFO, "screen.rtsbuilding.blueprints.status.save_busy", "");
            return;
        }
        RtsCullingBox box = selectionBox();
        if (box == null) {
            status.set(S2CBlueprintStatusPayload.ERROR, "screen.rtsbuilding.blueprints.status.capture_incomplete", "");
            return;
        }
        BlockPos min = box.min();
        applySelectionBox(new RtsCullingBox(1, min, new BlockPos(
                min.getX() + Math.max(1, sizeX) - 1,
                min.getY() + Math.max(1, sizeY) - 1,
                min.getZ() + Math.max(1, sizeZ) - 1)));
        status.set(S2CBlueprintStatusPayload.INFO, "screen.rtsbuilding.blueprints.status.capture_resized", sizeText());
    }

    void adjustSelectionFromHandle(Direction direction, int delta, StatusSink status) {
        RtsCullingBox box = selectionBox();
        if (box == null || direction == null || delta == 0) {
            status.set(S2CBlueprintStatusPayload.ERROR, "screen.rtsbuilding.blueprints.status.capture_incomplete", "");
            return;
        }
        applySelectionBox(box.resizeFromHandle(direction, delta));
        status.set(S2CBlueprintStatusPayload.INFO, "screen.rtsbuilding.blueprints.status.capture_resized", sizeText());
    }

    int sizeX() {
        RtsCullingBox box = selectionBox();
        return box == null ? 0 : box.width();
    }

    int sizeY() {
        RtsCullingBox box = selectionBox();
        return box == null ? 0 : box.height();
    }

    int sizeZ() {
        RtsCullingBox box = selectionBox();
        return box == null ? 0 : box.depth();
    }

    String saveStatusLine() {
        return saveJob == null ? "" : saveJob.statusLine();
    }

    String saveProgressLine() {
        return saveJob == null ? "" : saveJob.progressLine();
    }

    long countCapturableBlocks(Level level) {
        if (level == null || pointA == null || pointB == null) {
            return 0L;
        }
        int minX = Math.min(pointA.getX(), pointB.getX());
        int minY = Math.min(pointA.getY(), pointB.getY()) + 1;
        int minZ = Math.min(pointA.getZ(), pointB.getZ());
        int maxX = Math.max(pointA.getX(), pointB.getX());
        int maxY = Math.max(pointA.getY(), pointB.getY());
        int maxZ = Math.max(pointA.getZ(), pointB.getZ());
        if (minY > maxY) {
            return 0L;
        }

        long count = 0L;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = minY; y <= maxY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int x = minX; x <= maxX; x++) {
                    cursor.set(x, y, z);
                    if (excludedBlocks.contains(cursor) || !level.hasChunkAt(cursor)) {
                        continue;
                    }
                    BlockState state = level.getBlockState(cursor);
                    if (!state.isAir() && !state.is(Blocks.STRUCTURE_VOID)) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    void startSave(Level level, String fileName, Path dest, StatusSink status) {
        if (saveJob != null) {
            status.set(S2CBlueprintStatusPayload.INFO, "screen.rtsbuilding.blueprints.status.save_busy", "");
            return;
        }
        if (level == null) {
            status.set(S2CBlueprintStatusPayload.ERROR, "screen.rtsbuilding.blueprints.status.save_failed", "No world");
            return;
        }
        if (pointA == null || pointB == null) {
            status.set(S2CBlueprintStatusPayload.ERROR, "screen.rtsbuilding.blueprints.status.capture_incomplete", "");
            return;
        }
        long volume = captureVolume(pointA, pointB);
        long maxCaptureVolume = BlueprintWriters.maxCaptureVolume();
        if (volume > maxCaptureVolume) {
            status.set(S2CBlueprintStatusPayload.ERROR, "screen.rtsbuilding.blueprints.status.save_failed",
                    "Selection volume " + volume + " > " + maxCaptureVolume);
            return;
        }
        saveJob = new BlueprintCaptureSaveJob(level, pointA, pointB, excludedBlocks,
                stripNbtExtension(fileName), fileName, dest, status::set);
        status.set(S2CBlueprintStatusPayload.INFO, "screen.rtsbuilding.blueprints.status.save_started", fileName);
    }

    SaveResult pollSaveResult() {
        if (saveJob == null) {
            return null;
        }
        BlueprintCaptureSaveJob.Result result = saveJob.tick();
        if (result == null) {
            return null;
        }
        saveJob = null;
        if (!result.success()) {
            return SaveResult.error(result.messageKey(), result.detail());
        }
        active = false;
        resetSelection();
        return SaveResult.success(result.path(), result.blueprint(), result.fileName());
    }

    private void applySelectionBox(RtsCullingBox box) {
        if (box == null) {
            return;
        }
        RtsCullingBox previous = selectionBox();
        this.firstWorldCorner = box.min();
        this.pointA = new BlockPos(box.min().getX(), box.min().getY() - 1, box.min().getZ());
        this.pointB = box.max();
        this.hoverPoint = box.max();
        this.excludedBlocks.removeIf(pos -> !isInsideSelection(pointA, pointB, pos));
        boxAnimator.animate(previous, box);
    }

    private void resetSelection() {
        firstWorldCorner = null;
        pointA = null;
        pointB = null;
        hoverPoint = null;
        handleInteraction.clear();
        boxAnimator.clear();
        excludedBlocks.clear();
    }

    private static String boxSizeText(RtsCullingBox box) {
        return box == null ? "" : box.width() + "x" + box.height() + "x" + box.depth();
    }

    @FunctionalInterface
    interface StatusSink {
        void set(byte status, String messageKey, String detail);
    }

    record SaveResult(Path path, RtsBlueprint blueprint, String fileName, String messageKey, String detail) {
        static SaveResult success(Path path, RtsBlueprint blueprint, String fileName) {
            return new SaveResult(path, blueprint, fileName, "", "");
        }

        static SaveResult error(String messageKey, String detail) {
            return new SaveResult(null, null, "", messageKey, detail == null ? "" : detail);
        }

        boolean success() {
            return path != null && blueprint != null;
        }
    }
}
