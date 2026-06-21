package com.rtsbuilding.rtsbuilding.blueprint.client;


import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.rtsbuilding.rtsbuilding.blueprint.RtsBlueprint;
import com.rtsbuilding.rtsbuilding.blueprint.format.BlueprintWriters;
import com.rtsbuilding.rtsbuilding.blueprint.network.S2CBlueprintStatusPayload;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import static com.rtsbuilding.rtsbuilding.blueprint.client.BlueprintCaptureGeometry.captureSizeText;
import static com.rtsbuilding.rtsbuilding.blueprint.client.BlueprintCaptureGeometry.captureVolume;
import static com.rtsbuilding.rtsbuilding.blueprint.client.BlueprintCaptureGeometry.isInsideSelection;
import static com.rtsbuilding.rtsbuilding.blueprint.client.BlueprintCaptureGeometry.shortPos;
import static com.rtsbuilding.rtsbuilding.blueprint.client.BlueprintPanelFiles.stripNbtExtension;

/**
 * Owns the mutable state for selecting and saving a blueprint capture region.
 */
public final class BlueprintCaptureController {
    private boolean active = false;
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

    void updateHoverPoint(BlockPos pos) {
        hoverPoint = pos == null ? null : pos.immutable();
    }

    BlockPos previewPointB() {
        if (pointB != null) {
            return pointB;
        }
        return hoverPoint != null ? hoverPoint : pointA;
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
        pointA = null;
        pointB = null;
        hoverPoint = null;
        excludedBlocks.clear();
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
                    captureSizeText(pointA, pointB));
            return false;
        }
        if (pointA == null) {
            pointA = pos.immutable();
            pointB = null;
            hoverPoint = pos.immutable();
            excludedBlocks.clear();
            status.set(S2CBlueprintStatusPayload.INFO, "screen.rtsbuilding.blueprints.status.capture_a", shortPos(pointA));
        } else {
            pointB = pos.immutable();
            hoverPoint = pointB;
            excludedBlocks.clear();
            status.set(S2CBlueprintStatusPayload.SUCCESS, "screen.rtsbuilding.blueprints.status.capture_b",
                    captureSizeText(pointA, pointB));
        }
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
        status.set(S2CBlueprintStatusPayload.INFO, "screen.rtsbuilding.blueprints.status.capture_moved",
                captureSizeText(pointA, pointB));
    }

    void expandVertical(int deltaY, StatusSink status) {
        if (saveJob != null) {
            status.set(S2CBlueprintStatusPayload.INFO, "screen.rtsbuilding.blueprints.status.save_busy", "");
            return;
        }
        if (pointA == null || pointB == null || deltaY == 0) {
            status.set(S2CBlueprintStatusPayload.ERROR, "screen.rtsbuilding.blueprints.status.capture_incomplete", "");
            return;
        }
        int minX = Math.min(pointA.getX(), pointB.getX());
        int minY = Math.min(pointA.getY(), pointB.getY());
        int minZ = Math.min(pointA.getZ(), pointB.getZ());
        int maxX = Math.max(pointA.getX(), pointB.getX());
        int maxY = Math.max(pointA.getY(), pointB.getY());
        int maxZ = Math.max(pointA.getZ(), pointB.getZ());
        maxY = Math.max(minY, maxY + deltaY);
        pointA = new BlockPos(minX, minY, minZ);
        pointB = new BlockPos(maxX, maxY, maxZ);
        status.set(S2CBlueprintStatusPayload.INFO, "screen.rtsbuilding.blueprints.status.capture_resized",
                captureSizeText(pointA, pointB));
    }

    void resizeSelection(int deltaX, int deltaY, int deltaZ, StatusSink status) {
        if (pointA == null || pointB == null || (deltaX == 0 && deltaY == 0 && deltaZ == 0)) {
            status.set(S2CBlueprintStatusPayload.ERROR, "screen.rtsbuilding.blueprints.status.capture_incomplete", "");
            return;
        }
        int nextX = Math.max(1, sizeX() + deltaX);
        int nextY = Math.max(0, sizeY() + deltaY);
        int nextZ = Math.max(1, sizeZ() + deltaZ);
        setSelectionSize(nextX, nextY, nextZ, status);
    }

    void setSelectionSize(int sizeX, int sizeY, int sizeZ, StatusSink status) {
        if (saveJob != null) {
            status.set(S2CBlueprintStatusPayload.INFO, "screen.rtsbuilding.blueprints.status.save_busy", "");
            return;
        }
        if (pointA == null || pointB == null) {
            status.set(S2CBlueprintStatusPayload.ERROR, "screen.rtsbuilding.blueprints.status.capture_incomplete", "");
            return;
        }
        int minX = Math.min(pointA.getX(), pointB.getX());
        int minY = Math.min(pointA.getY(), pointB.getY());
        int minZ = Math.min(pointA.getZ(), pointB.getZ());
        int nextMaxX = minX + Math.max(1, sizeX) - 1;
        int nextMaxY = minY + Math.max(0, sizeY);
        int nextMaxZ = minZ + Math.max(1, sizeZ) - 1;
        pointA = new BlockPos(minX, minY, minZ);
        pointB = new BlockPos(nextMaxX, nextMaxY, nextMaxZ);
        hoverPoint = pointB;
        excludedBlocks.removeIf(pos -> !isInsideSelection(pointA, pointB, pos));
        status.set(S2CBlueprintStatusPayload.INFO, "screen.rtsbuilding.blueprints.status.capture_resized",
                captureSizeText(pointA, pointB));
    }

    int sizeX() {
        return pointA == null || pointB == null ? 0 : Math.abs(pointA.getX() - pointB.getX()) + 1;
    }

    int sizeY() {
        return pointA == null || pointB == null ? 0 : Math.abs(pointA.getY() - pointB.getY());
    }

    int sizeZ() {
        return pointA == null || pointB == null ? 0 : Math.abs(pointA.getZ() - pointB.getZ()) + 1;
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

    private void resetSelection() {
        pointA = null;
        pointB = null;
        hoverPoint = null;
        excludedBlocks.clear();
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
