package com.rtsbuilding.rtsbuilding.blueprint.server;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.blueprint.BlueprintBlockEntitySanitizer;
import com.rtsbuilding.rtsbuilding.blueprint.BlueprintReplaceRules;
import com.rtsbuilding.rtsbuilding.blueprint.BlueprintTransform;
import com.rtsbuilding.rtsbuilding.blueprint.RtsBlueprint;
import com.rtsbuilding.rtsbuilding.blueprint.RtsBlueprintBlock;
import com.rtsbuilding.rtsbuilding.blueprint.network.BlueprintNetworkHandlers;
import com.rtsbuilding.rtsbuilding.blueprint.network.S2CBlueprintStatusPayload;
import com.rtsbuilding.rtsbuilding.progression.RtsFeature;
import com.rtsbuilding.rtsbuilding.server.data.PlacedBlockTrackerData;
import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;
import com.rtsbuilding.rtsbuilding.server.service.RtsBlueprintService;
import com.rtsbuilding.rtsbuilding.server.storage.RtsLinkedStorageResolver;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidType;

import java.util.ArrayList;
import java.util.List;
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
        if (!RtsProgressionManager.canUse(player, RtsFeature.BLUEPRINTS)) {
            send(player, S2CBlueprintStatusPayload.ERROR, "screen.rtsbuilding.blueprints.status.locked", "");
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
        BlockPos centerOffset = BlueprintTransform.centerRotationOffset(
                job.blueprint().size(),
                job.yRotationSteps(),
                job.xRotationSteps(),
                job.zRotationSteps());
        while (index < job.blueprint().blocks().size() && processed < BLOCKS_PER_TICK) {
            RtsBlueprintBlock block = job.blueprint().blocks().get(index);
            index++;
            processed++;
            if (block.isMissingBlock()) {
                skippedMissingBlocks++;
                continue;
            }
            BlockPos target = job.anchor().offset(BlueprintTransform.rotateAroundCenter(
                    block.relativePos(),
                    job.yRotationSteps(),
                    job.xRotationSteps(),
                    job.zRotationSteps(),
                    centerOffset));
            if (!canStillPlace(player, level, target)) {
                skippedBlocked++;
                continue;
            }

            BlockState state = BlueprintTransform.rotateState(
                    block.state(),
                    job.yRotationSteps(),
                    job.xRotationSteps(),
                    job.zRotationSteps());
            List<Item> materialItems = materialItems(block, state);
            List<ItemStack> extractedMaterials = new ArrayList<>(materialItems.size());
            Fluid fluidCost = materialItems.isEmpty() ? fluidCostFor(Items.AIR, state) : Fluids.EMPTY;
            if (!player.isCreative()) {
                if (materialItems.isEmpty()) {
                    if (fluidCost == Fluids.WATER) {
                        if (!hasReusableWater(player)) {
                            skippedMissing++;
                            continue;
                        }
                    } else if (fluidCost == Fluids.LAVA) {
                        if (RtsBlueprintService.countBlueprintFluidMb(player, Fluids.LAVA) < FluidType.BUCKET_VOLUME) {
                            skippedMissing++;
                            continue;
                        }
                    } else {
                        skippedUnsupported++;
                        continue;
                    }
                } else {
                    boolean missingMaterial = false;
                    for (Item item : materialItems) {
                        ItemStack extracted = RtsBlueprintService.extractBlueprintMaterial(player, item, 1);
                        if (extracted.isEmpty()) {
                            missingMaterial = true;
                            break;
                        }
                        extractedMaterials.add(extracted);
                    }
                    if (missingMaterial) {
                        refundExtractedMaterials(player, extractedMaterials);
                        skippedMissing++;
                        continue;
                    }
                }
            }

            boolean placedBlock = level.setBlock(target, state, 3);
            if (!placedBlock) {
                if (!player.isCreative()) {
                    refundExtractedMaterials(player, extractedMaterials);
                }
                skippedBlocked++;
                continue;
            }
            if (!player.isCreative() && fluidCost == Fluids.LAVA
                    && !RtsBlueprintService.extractBlueprintFluid(player, Fluids.LAVA, FluidType.BUCKET_VOLUME)) {
                level.removeBlock(target, false);
                refundExtractedMaterials(player, extractedMaterials);
                skippedMissing++;
                continue;
            }
            applyBlockEntityTag(player, level, target, block);

            PlacedBlockTrackerData.get(level).mark(target);
            for (Item item : materialItems) {
                ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
                if (itemId != null) {
                    RtsBlueprintService.noteBlueprintBlockPlaced(player, target, itemId.toString());
                }
            }
            placed++;
        }

        if (index >= job.blueprint().blocks().size()) {
            JOBS.remove(player.getUUID());
            RtsBlueprintService.refreshBlueprintStoragePage(player);
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

    private static List<Item> materialItems(RtsBlueprintBlock block, BlockState state) {
        List<ResourceLocation> ids = RtsBlueprint.materialItemIds(block);
        if (ids.isEmpty() && state != null) {
            Item fallback = state.getBlock().asItem();
            return fallback == Items.AIR ? List.of() : List.of(fallback);
        }
        List<Item> out = new ArrayList<>(ids.size());
        for (ResourceLocation id : ids) {
            if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
                continue;
            }
            Item item = BuiltInRegistries.ITEM.get(id);
            if (item != null && item != Items.AIR) {
                out.add(item);
            }
        }
        return out.isEmpty() ? List.of() : List.copyOf(out);
    }

    private static void refundExtractedMaterials(ServerPlayer player, List<ItemStack> stacks) {
        if (player == null || stacks == null || stacks.isEmpty()) {
            return;
        }
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty()) {
                RtsBlueprintService.refundBlueprintMaterial(player, stack);
            }
        }
    }

    private static void applyBlockEntityTag(ServerPlayer player, ServerLevel level, BlockPos target, RtsBlueprintBlock block) {
        if (level == null || target == null || block == null || !block.hasBlockEntityTag()) {
            return;
        }
        BlockEntity blockEntity = level.getBlockEntity(target);
        if (blockEntity == null) {
            return;
        }
        CompoundTag tag = blueprintBlockEntityTagForPlacement(player, block);
        if (tag == null || tag.isEmpty()) {
            return;
        }
        tag.putInt("x", target.getX());
        tag.putInt("y", target.getY());
        tag.putInt("z", target.getZ());
        try {
            blockEntity.load(tag);
            blockEntity.setChanged();
            BlockState state = level.getBlockState(target);
            level.sendBlockUpdated(target, state, state, 3);
        } catch (RuntimeException ignored) {
        }
    }

    private static CompoundTag blueprintBlockEntityTagForPlacement(ServerPlayer player, RtsBlueprintBlock block) {
        if (block == null || block.blockEntityTag() == null || block.blockEntityTag().isEmpty()) {
            return null;
        }
        if (player != null && player.isCreative() && player.canUseGameMasterBlocks()) {
            return block.blockEntityTag().copy();
        }
        return BlueprintBlockEntitySanitizer.sanitizeForSurvivalPlacement(block.blockEntityTag());
    }

    private static boolean canStillPlace(ServerPlayer player, ServerLevel level, BlockPos target) {
        if (!RtsLinkedStorageResolver.canAccessWorldTarget(player, target)) {
            return false;
        }
        if (level.getBlockEntity(target) != null) {
            return false;
        }
        return BlueprintReplaceRules.canBlueprintReplace(level.getBlockState(target));
    }

    private static Fluid fluidCostFor(Item item, BlockState state) {
        if (item != Items.AIR || state == null) {
            return Fluids.EMPTY;
        }
        if (state.getFluidState().is(FluidTags.WATER)) {
            return Fluids.WATER;
        }
        if (state.getFluidState().is(FluidTags.LAVA)) {
            return Fluids.LAVA;
        }
        return Fluids.EMPTY;
    }

    private static boolean hasReusableWater(ServerPlayer player) {
        long waterBuckets = RtsBlueprintService.countBlueprintMaterial(player, Items.WATER_BUCKET);
        long storedWaterBuckets = RtsBlueprintService.countBlueprintFluidMb(player, Fluids.WATER) / FluidType.BUCKET_VOLUME;
        return waterBuckets + storedWaterBuckets >= 2L;
    }

    private static void abort(ServerPlayer player, String messageKey, String detail) {
        JOBS.remove(player.getUUID());
        RtsBlueprintService.refreshBlueprintStoragePage(player);
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
