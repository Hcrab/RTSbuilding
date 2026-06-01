package com.rtsbuilding.rtsbuilding.blueprint.server;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.blueprint.BlueprintReplaceRules;
import com.rtsbuilding.rtsbuilding.blueprint.BlueprintTransform;
import com.rtsbuilding.rtsbuilding.blueprint.RtsBlueprint;
import com.rtsbuilding.rtsbuilding.blueprint.RtsBlueprintBlock;
import com.rtsbuilding.rtsbuilding.blueprint.network.BlueprintNetworkHandlers;
import com.rtsbuilding.rtsbuilding.blueprint.network.S2CBlueprintStatusPayload;
import com.rtsbuilding.rtsbuilding.server.RtsStorageManager;
import com.rtsbuilding.rtsbuilding.server.data.PlacedBlockTrackerData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BlueprintPlacementService {
    private static final int BLOCKS_PER_TICK = 64;
    private static final Map<UUID, PlacementJob> JOBS = new ConcurrentHashMap<>();

    private BlueprintPlacementService() {
    }

    public static void queuePlacement(ServerPlayer player, RtsBlueprint blueprint, BlockPos anchor,
            byte yRotationSteps, byte xRotationSteps, byte zRotationSteps) {
        if (player == null || blueprint == null || anchor == null) {
            return;
        }
        if (!Config.areBlueprintsEnabled()) {
            send(player, S2CBlueprintStatusPayload.ERROR, "screen.rtsbuilding.blueprints.status.disabled", "");
            return;
        }
        if (blueprint.blocks().isEmpty()) {
            send(player, S2CBlueprintStatusPayload.ERROR, "screen.rtsbuilding.blueprints.status.empty", "");
            return;
        }
        int maxBlocks = Config.maxBlueprintBlocks();
        if (blueprint.blockCount() > maxBlocks) {
            send(player, S2CBlueprintStatusPayload.ERROR, "screen.rtsbuilding.blueprints.status.too_many_blocks",
                    blueprint.blockCount() + "/" + maxBlocks);
            return;
        }

        JOBS.put(player.getUUID(), new PlacementJob(
                blueprint,
                anchor.immutable(),
                BlueprintTransform.normalizeSteps(yRotationSteps),
                BlueprintTransform.normalizeSteps(xRotationSteps),
                BlueprintTransform.normalizeSteps(zRotationSteps),
                0,
                0,
                0,
                0,
                0,
                0));
        send(player, S2CBlueprintStatusPayload.INFO, "screen.rtsbuilding.blueprints.status.queued",
                Integer.toString(blueprint.blockCount()));
    }

    public static void tick(ServerPlayer player) {
        if (player == null) {
            return;
        }
        PlacementJob job = JOBS.get(player.getUUID());
        if (job == null) {
            return;
        }
        if (!Config.areBlueprintsEnabled()) {
            abort(player, "screen.rtsbuilding.blueprints.status.disabled", "");
            return;
        }

        ServerLevel level = player.serverLevel();
        int processed = 0;
        int placed = job.placedCount();
        int skippedMissing = job.skippedMissing();
        int skippedUnsupported = job.skippedUnsupported();
        int skippedMissingBlocks = job.skippedMissingBlocks();
        int skippedBlocked = job.skippedBlocked();
        int index = job.nextIndex();
        while (index < job.blueprint().blocks().size() && processed < BLOCKS_PER_TICK) {
            RtsBlueprintBlock block = job.blueprint().blocks().get(index);
            index++;
            processed++;
            if (block.isMissingBlock()) {
                skippedMissingBlocks++;
                continue;
            }
            BlockPos target = job.anchor().offset(BlueprintTransform.rotate(
                    block.relativePos(),
                    job.yRotationSteps(),
                    job.xRotationSteps(),
                    job.zRotationSteps()));
            if (!canStillPlace(player, level, target)) {
                skippedBlocked++;
                continue;
            }

            BlockState state = BlueprintTransform.rotateState(
                    block.state(),
                    job.yRotationSteps(),
                    job.xRotationSteps(),
                    job.zRotationSteps());
            Item item = state.getBlock().asItem();
            ItemStack extracted = ItemStack.EMPTY;
            if (!player.isCreative()) {
                if (item == Items.AIR) {
                    skippedUnsupported++;
                    continue;
                }
                extracted = RtsStorageManager.extractBlueprintMaterial(player, item, 1);
                if (extracted.isEmpty()) {
                    skippedMissing++;
                    continue;
                }
            }

            boolean placedBlock = level.setBlock(target, state, 3);
            if (!placedBlock) {
                if (!player.isCreative() && !extracted.isEmpty()) {
                    RtsStorageManager.refundBlueprintMaterial(player, extracted);
                }
                skippedBlocked++;
                continue;
            }

            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
            PlacedBlockTrackerData.get(level).mark(target);
            if (item != Items.AIR) {
                RtsStorageManager.noteBlueprintBlockPlaced(player, target, itemId == null ? "" : itemId.toString());
            }
            placed++;
        }

        if (index >= job.blueprint().blocks().size()) {
            JOBS.remove(player.getUUID());
            RtsStorageManager.refreshBlueprintStoragePage(player);
            send(player, S2CBlueprintStatusPayload.SUCCESS, "screen.rtsbuilding.blueprints.status.complete_partial",
                    completionSummary(placed, job.blueprint().blockCount(), skippedMissing, skippedUnsupported, skippedMissingBlocks, skippedBlocked));
        } else {
            JOBS.put(player.getUUID(), job.withProgress(index, placed, skippedMissing, skippedUnsupported, skippedMissingBlocks, skippedBlocked));
        }
    }

    public static void clear(ServerPlayer player) {
        if (player != null) {
            JOBS.remove(player.getUUID());
        }
    }

    private static boolean canStillPlace(ServerPlayer player, ServerLevel level, BlockPos target) {
        if (!RtsStorageManager.canAccessBlueprintTarget(player, target)) {
            return false;
        }
        if (level.getBlockEntity(target) != null) {
            return false;
        }
        return BlueprintReplaceRules.canBlueprintReplace(level.getBlockState(target));
    }

    private static void abort(ServerPlayer player, String messageKey, String detail) {
        JOBS.remove(player.getUUID());
        RtsStorageManager.refreshBlueprintStoragePage(player);
        send(player, S2CBlueprintStatusPayload.ERROR, messageKey, detail);
    }

    private static void send(ServerPlayer player, byte status, String messageKey, String detail) {
        BlueprintNetworkHandlers.send(player, status, messageKey, detail);
    }

    private static String shortPos(BlockPos pos) {
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }

    private static String completionSummary(int placed, int total, int skippedMissing, int skippedUnsupported,
            int skippedMissingBlocks, int skippedBlocked) {
        int skipped = Math.max(0, skippedMissing)
                + Math.max(0, skippedUnsupported)
                + Math.max(0, skippedMissingBlocks)
                + Math.max(0, skippedBlocked);
        return placed + "/" + total + " placed, " + skipped + " skipped"
                + " (missing " + skippedMissing
                + ", unsupported " + skippedUnsupported
                + ", missing blocks " + skippedMissingBlocks
                + ", blocked " + skippedBlocked + ")";
    }

    private record PlacementJob(
            RtsBlueprint blueprint,
            BlockPos anchor,
            int yRotationSteps,
            int xRotationSteps,
            int zRotationSteps,
            int nextIndex,
            int placedCount,
            int skippedMissing,
            int skippedUnsupported,
            int skippedMissingBlocks,
            int skippedBlocked) {
        PlacementJob withProgress(int nextIndex, int placedCount, int skippedMissing, int skippedUnsupported,
                int skippedMissingBlocks, int skippedBlocked) {
            return new PlacementJob(
                    this.blueprint,
                    this.anchor,
                    this.yRotationSteps,
                    this.xRotationSteps,
                    this.zRotationSteps,
                    nextIndex,
                    placedCount,
                    skippedMissing,
                    skippedUnsupported,
                    skippedMissingBlocks,
                    skippedBlocked);
        }
    }
}
