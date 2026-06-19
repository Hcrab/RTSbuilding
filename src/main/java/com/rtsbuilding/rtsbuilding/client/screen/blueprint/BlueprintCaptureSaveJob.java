package com.rtsbuilding.rtsbuilding.client.screen.blueprint;

import com.rtsbuilding.rtsbuilding.common.blueprint.model.BlueprintFormat;
import com.rtsbuilding.rtsbuilding.common.blueprint.model.RtsBlueprint;
import com.rtsbuilding.rtsbuilding.common.blueprint.model.RtsBlueprintBlock;
import com.rtsbuilding.rtsbuilding.common.blueprint.io.BlueprintWriters;
import com.rtsbuilding.rtsbuilding.network.blueprint.S2CBlueprintStatusPayload;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Frame-sliced blueprint capture scanner plus asynchronous file writer.
 *
 * <p>The old implementation lived inside {@link BlueprintPanel}, which made the
 * UI state machine carry scanning budgets, cursor state, and write-future
 * handling. This class keeps that long-running save workflow isolated while
 * preserving the same visible behavior: scanning happens in small chunks, status
 * text is throttled, the bottom Y plane is skipped to match player selection
 * intuition, and the final vanilla NBT file is written off the render thread.</p>
 */
final class BlueprintCaptureSaveJob {
    private static final int CAPTURE_SCAN_BUDGET_PER_STEP = 64;
    private static final long CAPTURE_SCAN_TIME_BUDGET_NANOS = 500_000L;
    private static final long CAPTURE_SCAN_MIN_INTERVAL_MS = 15L;
    private static final long CAPTURE_STATUS_INTERVAL_MS = 250L;

    private final Level level;
    private final int minX;
    private final int minY;
    private final int captureMinY;
    private final int minZ;
    private final int maxX;
    private final int maxY;
    private final int maxZ;
    private final Vec3i size;
    private final String name;
    private final String fileName;
    private final Path dest;
    private final long volume;
    private final Set<BlockPos> excludedBlocks;
    private final List<RtsBlueprintBlock> blocks = new ArrayList<>();
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
    private final StatusSink statusSink;
    private int x;
    private int y;
    private int z;
    private long scanned;
    private long lastStatusMillis;
    private long lastScanMillis;
    private boolean scanComplete;
    private CompletableFuture<Result> writeFuture;

    /**
     * Creates a save job from the two selected capture corners.
     *
     * <p>The constructor normalizes the selected cuboid once. The live
     * {@link BlueprintPanel} can then keep ticking this object without repeating
     * coordinate math every frame.</p>
     */
    BlueprintCaptureSaveJob(Level level, BlockPos first, BlockPos second, Set<BlockPos> excludedBlocks,
            String name, String fileName, Path dest, StatusSink statusSink) {
        this.level = Objects.requireNonNull(level, "level");
        this.minX = Math.min(first.getX(), second.getX());
        this.minY = Math.min(first.getY(), second.getY());
        this.captureMinY = this.minY + 1;
        this.minZ = Math.min(first.getZ(), second.getZ());
        this.maxX = Math.max(first.getX(), second.getX());
        this.maxY = Math.max(first.getY(), second.getY());
        this.maxZ = Math.max(first.getZ(), second.getZ());
        this.size = new Vec3i(this.maxX - this.minX + 1, Math.max(0, this.maxY - this.minY), this.maxZ - this.minZ + 1);
        this.name = Objects.requireNonNull(name, "name");
        this.fileName = Objects.requireNonNull(fileName, "fileName");
        this.dest = Objects.requireNonNull(dest, "dest");
        this.volume = (long) this.size.getX() * this.size.getY() * this.size.getZ();
        this.excludedBlocks = Set.copyOf(excludedBlocks);
        this.statusSink = Objects.requireNonNull(statusSink, "statusSink");
        this.x = this.minX;
        this.y = this.captureMinY;
        this.z = this.minZ;
        this.scanComplete = this.captureMinY > this.maxY;
    }

    /**
     * Advances the save job by one render/client tick budget.
     *
     * @return {@code null} while work is still pending, or a {@link Result} when
     *         the scan/write has either succeeded or failed.
     */
    Result tick() {
        if (this.writeFuture != null) {
            if (!this.writeFuture.isDone()) {
                updateStatus("screen.rtsbuilding.blueprints.status.save_writing", "");
                return null;
            }
            try {
                return this.writeFuture.join();
            } catch (RuntimeException ex) {
                return Result.failure("screen.rtsbuilding.blueprints.status.save_failed", failureDetail(ex));
            }
        }

        long now = Util.getMillis();
        if (!this.scanComplete && now - this.lastScanMillis < CAPTURE_SCAN_MIN_INTERVAL_MS) {
            updateStatus("screen.rtsbuilding.blueprints.status.save_scanning", progressLine());
            return null;
        }
        this.lastScanMillis = now;

        int processed = 0;
        long stepStarted = System.nanoTime();
        while (!this.scanComplete
                && processed < CAPTURE_SCAN_BUDGET_PER_STEP
                && System.nanoTime() - stepStarted < CAPTURE_SCAN_TIME_BUDGET_NANOS) {
            Result failure = scanCurrentBlock();
            if (failure != null) {
                return failure;
            }
            advance();
            processed++;
            this.scanned++;
        }

        if (!this.scanComplete) {
            updateStatus("screen.rtsbuilding.blueprints.status.save_scanning", progressLine());
            return null;
        }
        if (this.blocks.isEmpty()) {
            return Result.failure("screen.rtsbuilding.blueprints.status.capture_empty", "");
        }

        RtsBlueprint blueprint = RtsBlueprint.create(
                this.name,
                this.fileName,
                BlueprintFormat.VANILLA_NBT,
                this.size,
                List.copyOf(this.blocks));
        updateStatus("screen.rtsbuilding.blueprints.status.save_writing", "");
        this.writeFuture = CompletableFuture.supplyAsync(() -> writeBlueprint(blueprint));
        return null;
    }

    /**
     * Returns the current localized status line for the capture overlay.
     */
    String statusLine() {
        return this.writeFuture == null
                ? BlueprintPanelUi.text("screen.rtsbuilding.blueprints.status.save_scanning", progressLine())
                : BlueprintPanelUi.text("screen.rtsbuilding.blueprints.status.save_writing");
    }

    /**
     * Returns a raw scanned/volume counter used by status text.
     */
    String progressLine() {
        return this.scanned + "/" + this.volume;
    }

    /**
     * Reads the current world block and appends it to the pending blueprint when
     * it is a supported non-air block inside a loaded chunk.
     */
    private Result scanCurrentBlock() {
        this.cursor.set(this.x, this.y, this.z);
        if (this.excludedBlocks.contains(this.cursor) || !this.level.hasChunkAt(this.cursor)) {
            return null;
        }

        BlockState state = this.level.getBlockState(this.cursor);
        if (state.isAir() || state.is(Blocks.STRUCTURE_VOID)) {
            return null;
        }

        this.blocks.add(new RtsBlueprintBlock(
                new BlockPos(this.x - this.minX, this.y - this.captureMinY, this.z - this.minZ),
                state,
                captureBlockEntityTag(this.cursor),
                "",
                resolveMaterialItemId(state, this.cursor)));
        int maxCaptureBlocks = BlueprintWriters.maxCaptureBlocks();
        if (this.blocks.size() > maxCaptureBlocks) {
            return Result.failure("screen.rtsbuilding.blueprints.status.save_failed",
                    "Blueprint capture contains more than " + maxCaptureBlocks + " blocks");
        }
        return null;
    }

    private CompoundTag captureBlockEntityTag(BlockPos pos) {
        BlockEntity blockEntity = this.level.getBlockEntity(pos);
        if (blockEntity == null) {
            return new CompoundTag();
        }
        try {
            CompoundTag tag = blockEntity.saveWithFullMetadata(this.level.registryAccess());
            tag.remove("x");
            tag.remove("y");
            tag.remove("z");
            return tag;
        } catch (RuntimeException ignored) {
            return new CompoundTag();
        }
    }

    private String resolveMaterialItemId(BlockState state, BlockPos pos) {
        if (state == null || pos == null) {
            return "";
        }
        try {
            ItemStack cloneStack = state.getBlock().getCloneItemStack(this.level, pos, state);
            if (!cloneStack.isEmpty()) {
                ResourceLocation id = BuiltInRegistries.ITEM.getKey(cloneStack.getItem());
                if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
                    return id.toString();
                }
            }
        } catch (RuntimeException ignored) {
        }
        ResourceLocation fallback = BuiltInRegistries.ITEM.getKey(state.getBlock().asItem());
        return fallback == null || !BuiltInRegistries.ITEM.containsKey(fallback) ? "" : fallback.toString();
    }

    /**
     * Writes the finished blueprint to disk on the async worker.
     */
    private Result writeBlueprint(RtsBlueprint blueprint) {
        try {
            BlueprintWriters.writeVanillaStructure(blueprint, this.dest);
            return Result.success(this.fileName, this.dest, blueprint);
        } catch (Exception ex) {
            return Result.failure("screen.rtsbuilding.blueprints.status.save_failed", failureDetail(ex));
        }
    }

    /**
     * Pushes status updates through the panel's existing status rendering path.
     */
    private void updateStatus(String key, String detail) {
        long now = Util.getMillis();
        if (now - this.lastStatusMillis < CAPTURE_STATUS_INTERVAL_MS) {
            return;
        }
        this.lastStatusMillis = now;
        this.statusSink.set(S2CBlueprintStatusPayload.INFO, key, detail);
    }

    /**
     * Moves the scan cursor in X/Z/Y order, matching the original implementation.
     */
    private void advance() {
        if (++this.x > this.maxX) {
            this.x = this.minX;
            if (++this.z > this.maxZ) {
                this.z = this.minZ;
                if (++this.y > this.maxY) {
                    this.scanComplete = true;
                }
            }
        }
    }

    /**
     * Converts exceptions to short status text without swallowing serious Errors.
     */
    private static String failureDetail(Throwable throwable) {
        if (throwable == null) {
            return "Unknown error";
        }
        Throwable cause = throwable.getCause() == null ? throwable : throwable.getCause();
        String message = cause.getMessage();
        return message == null || message.isBlank() ? cause.getClass().getSimpleName() : message;
    }

    /**
     * Small bridge back into {@link BlueprintPanel#setStatus(byte, String, String)}.
     */
    @FunctionalInterface
    interface StatusSink {
        void set(byte status, String messageKey, String detail);
    }

    /**
     * Completion object returned once a capture job is no longer pending.
     */
    record Result(boolean success, String fileName, Path path, RtsBlueprint blueprint, String messageKey, String detail) {
        static Result success(String fileName, Path path, RtsBlueprint blueprint) {
            return new Result(true, fileName, path, blueprint, "", "");
        }

        static Result failure(String messageKey, String detail) {
            return new Result(false, "", null, null, messageKey, detail == null ? "" : detail);
        }
    }

}
