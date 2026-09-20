package com.rtsbuilding.rtsbuilding.common.config;

import com.rtsbuilding.rtsbuilding.common.mining.MiningLimits;
import com.rtsbuilding.rtsbuilding.common.smartfill.SmartFillLimits;
import java.util.HashSet;
import java.util.Set;

/** 不依赖 loader 的权限前置和跨字段约束校验。 */
public final class RtsServerConfigValidator {
    private RtsServerConfigValidator() { }
    public static String validate(RtsServerConfigView current, RtsServerConfigUpdateRequest request, boolean editable) {
        if (current == null || request == null || request.changes().isEmpty()) return "no configuration changes";
        if (!editable || !current.editable()) return "server configuration is read-only";
        if (request.expectedRevision() != current.revision()) return "configuration revision changed";
        Set<RtsServerConfigChange.Key> keys = new HashSet<>();
        for (RtsServerConfigChange change : request.changes()) {
            if (!keys.add(change.key())) return "duplicate configuration key: " + change.key();
            if (change.value() instanceof RtsServerConfigChange.IntValue value
                    && value.value() <= 0
                    && !(change.key() == RtsServerConfigChange.Key.HOME_RELOCATION_COOLDOWN_DAYS && value.value() == 0)) return "invalid value for " + change.key();
            if (change.value() instanceof RtsServerConfigChange.LongValue value && value.value() <= 0L) return "invalid value for " + change.key();
            if (change.value() instanceof RtsServerConfigChange.DoubleValue value && (!Double.isFinite(value.value()) || value.value() < 0.0D)) return "invalid value for " + change.key();
            if (change.value() instanceof RtsServerConfigChange.StringValue value
                    && (change.key() != RtsServerConfigChange.Key.AREA_MINE_MAX_HARVEST_TIER
                    || !switch (value.value()) { case "STONE", "IRON", "DIAMOND", "UNLIMITED" -> true; default -> false; })) return "invalid value for " + change.key();
        }
        final RtsServerConfigView candidate;
        try {
            candidate = apply(current, request.changes());
        } catch (IllegalArgumentException invalidCombination) {
            // 让 G06 得到可保留草稿的结构化失败，而不是把跨字段非法值冒泡成崩溃。
            return invalidCombination.getMessage() == null
                    ? "configuration values are inconsistent" : invalidCombination.getMessage();
        }
        if (candidate.maxSelectionVolume() < 1 || candidate.maxSelectionVolume() > MiningLimits.MAX_VOLUME
                || candidate.maxSelectionSizeX() < 1 || candidate.maxSelectionSizeY() < 1
                || candidate.maxSelectionSizeZ() < 1
                || candidate.smartFillDefaultBlocks() > candidate.smartFillMaxBlocks()
                || candidate.smartFillDefaultDiameter() > candidate.smartFillMaxDiameter() || candidate.defaultStoragePageSize() > candidate.maxStoragePageSize()
                || candidate.maxActionRadiusBlocks() < 48 || candidate.maxActionRadiusBlocks() > 512
                || candidate.maxBlueprintBlocks() < 1 || candidate.maxBlueprintBlocks() > 200_000
                || candidate.ultimineMaxBlocks() < 1 || candidate.ultimineMaxBlocks() > MiningLimits.MAX_CHAIN_LIMIT
                || candidate.maxTreeBlocks() < 1 || candidate.maxTreeBlocks() > MiningLimits.MAX_TREE_BLOCKS
                || candidate.homeSelectionRadiusBlocks() < 1
                || candidate.homeRelocationCooldownDays() < 0
                || candidate.maxShapeDimension() < 1
                || candidate.maxShapeRadius() < 1
                || candidate.smartFillMaxBlocks() > SmartFillLimits.HARD_MAX_BLOCKS || candidate.smartFillMaxBlocks() < SmartFillLimits.MIN_BLOCKS
                || candidate.smartFillDefaultBlocks() < SmartFillLimits.MIN_BLOCKS || candidate.smartFillMaxDiameter() > SmartFillLimits.HARD_MAX_DIAMETER
                || candidate.smartFillMaxDiameter() < SmartFillLimits.MIN_DIAMETER || candidate.smartFillDefaultDiameter() < SmartFillLimits.MIN_DIAMETER
                || candidate.maxBatchBindingSelectionVolume() < 1 || candidate.maxBatchBindingSelectionVolume() > MiningLimits.MAX_VOLUME
                || candidate.maxBatchBindingSizeX() < 1 || candidate.maxBatchBindingSizeY() < 1 || candidate.maxBatchBindingSizeZ() < 1
                || candidate.maxLinkedStorages() < 1 || candidate.maxLinkedStorages() > 4_096
                || candidate.funnelPickupRadiusBlocks() < 0.0D || candidate.funnelPickupRadiusBlocks() > 32.0D
                || candidate.workflowsMaxActivePerPlayer() < 1 || candidate.workflowsMaxActivePerPlayer() > 1_024
                || candidate.historyMaxEntriesPerStack() < 1 || candidate.historyRetentionSeconds() < 1
                || candidate.funnelMaxEntitiesPerTick() < 1 || candidate.funnelMaxItemsPerTick() < 1
                || candidate.funnelBufferMaxStacks() < 1 || candidate.funnelTickInterval() < 1
                || candidate.storageDropCacheSoftCapacity() < 1 || candidate.buildBatchBlocksPerTick() < 1
                || candidate.buildBatchMaxQueuedJobs() < 1 || candidate.taskEngineMaxUnitsPerTick() < 1
                || candidate.taskEngineMaxUnitsPerSlice() < 1 || candidate.taskEngineMaxNanosPerTick() < 1L
                || candidate.defaultStoragePageSize() > 4_096 || candidate.maxStoragePageSize() > 8_192
                || candidate.pageCacheMaxPlayers() < 1 || candidate.ae2NetworkRefreshThrottle() < 1
                || candidate.refinedStorageNetworkRefreshThrottle() < 1 || candidate.diagnosticsMaxTraces() < 1
                || candidate.diagnosticsMaxWorkflowLinks() < 1 || candidate.diagnosticsMaxTaskLinks() < 1) return "configuration value is outside its supported range";
        return null;
    }
    public static RtsServerConfigView apply(RtsServerConfigView current, java.util.List<RtsServerConfigChange> changes) {
        boolean survival=current.enableSurvivalProgression(), share=current.shareSurvivalProgressionWithTeams(), blueprints=current.enableBlueprints();
        int action=current.maxActionRadiusBlocks(), blueprint=current.maxBlueprintBlocks(), volume=current.maxSelectionVolume(), x=current.maxSelectionSizeX(), y=current.maxSelectionSizeY(), z=current.maxSelectionSizeZ(), chain=current.ultimineMaxBlocks(), chainTick=current.ultimineBlocksPerTick(), tree=current.maxTreeBlocks(), home=current.homeSelectionRadiusBlocks(), cooldown=current.homeRelocationCooldownDays(), shape=current.maxShapeDimension(), radius=current.maxShapeRadius(), smart=current.smartFillMaxBlocks(), smartDefault=current.smartFillDefaultBlocks(), diameter=current.smartFillMaxDiameter(), diameterDefault=current.smartFillDefaultDiameter(), batch=current.maxBatchBindingSelectionVolume(), batchX=current.maxBatchBindingSizeX(), batchY=current.maxBatchBindingSizeY(), batchZ=current.maxBatchBindingSizeZ(), linked=current.maxLinkedStorages(), workflows=current.workflowsMaxActivePerPlayer(), history=current.historyMaxEntriesPerStack(), historySeconds=current.historyRetentionSeconds(), entities=current.funnelMaxEntitiesPerTick(), items=current.funnelMaxItemsPerTick(), buffer=current.funnelBufferMaxStacks(), interval=current.funnelTickInterval(), drop=current.storageDropCacheSoftCapacity(), buildTick=current.buildBatchBlocksPerTick(), buildJobs=current.buildBatchMaxQueuedJobs(), units=current.taskEngineMaxUnitsPerTick(), slice=current.taskEngineMaxUnitsPerSlice(), page=current.defaultStoragePageSize(), maxPage=current.maxStoragePageSize(), players=current.pageCacheMaxPlayers(), ae2=current.ae2NetworkRefreshThrottle(), rs=current.refinedStorageNetworkRefreshThrottle(), traces=current.diagnosticsMaxTraces(), workflowLinks=current.diagnosticsMaxWorkflowLinks(), taskLinks=current.diagnosticsMaxTaskLinks();
        String harvestTier=current.areaMineMaxHarvestTier();
        double funnel=current.funnelPickupRadiusBlocks(); long nanos=current.taskEngineMaxNanosPerTick();
        for (RtsServerConfigChange c : changes) switch (c.key()) {
            case ENABLE_SURVIVAL_PROGRESSION -> survival=((RtsServerConfigChange.BooleanValue)c.value()).value(); case SHARE_SURVIVAL_PROGRESSION_WITH_TEAMS -> share=((RtsServerConfigChange.BooleanValue)c.value()).value(); case ENABLE_BLUEPRINTS -> blueprints=((RtsServerConfigChange.BooleanValue)c.value()).value(); case MAX_ACTION_RADIUS_BLOCKS -> action=((RtsServerConfigChange.IntValue)c.value()).value(); case MAX_BLUEPRINT_BLOCKS -> blueprint=((RtsServerConfigChange.IntValue)c.value()).value(); case MAX_SELECTION_VOLUME -> volume=((RtsServerConfigChange.IntValue)c.value()).value(); case MAX_SELECTION_SIZE_X -> x=((RtsServerConfigChange.IntValue)c.value()).value(); case MAX_SELECTION_SIZE_Y -> y=((RtsServerConfigChange.IntValue)c.value()).value(); case MAX_SELECTION_SIZE_Z -> z=((RtsServerConfigChange.IntValue)c.value()).value(); case ULTIMINE_MAX_BLOCKS -> chain=((RtsServerConfigChange.IntValue)c.value()).value(); case ULTIMINE_BLOCKS_PER_TICK -> chainTick=((RtsServerConfigChange.IntValue)c.value()).value(); case AREA_MINE_MAX_HARVEST_TIER -> harvestTier=((RtsServerConfigChange.StringValue)c.value()).value(); case MAX_TREE_BLOCKS -> tree=((RtsServerConfigChange.IntValue)c.value()).value(); case HOME_SELECTION_RADIUS_BLOCKS -> home=((RtsServerConfigChange.IntValue)c.value()).value(); case HOME_RELOCATION_COOLDOWN_DAYS -> cooldown=((RtsServerConfigChange.IntValue)c.value()).value(); case MAX_SHAPE_DIMENSION -> shape=((RtsServerConfigChange.IntValue)c.value()).value(); case MAX_SHAPE_RADIUS -> radius=((RtsServerConfigChange.IntValue)c.value()).value(); case SMART_FILL_MAX_BLOCKS -> smart=((RtsServerConfigChange.IntValue)c.value()).value(); case SMART_FILL_DEFAULT_BLOCKS -> smartDefault=((RtsServerConfigChange.IntValue)c.value()).value(); case SMART_FILL_MAX_DIAMETER -> diameter=((RtsServerConfigChange.IntValue)c.value()).value(); case SMART_FILL_DEFAULT_DIAMETER -> diameterDefault=((RtsServerConfigChange.IntValue)c.value()).value(); case MAX_BATCH_BINDING_SELECTION_VOLUME -> batch=((RtsServerConfigChange.IntValue)c.value()).value(); case MAX_BATCH_BINDING_SIZE_X -> batchX=((RtsServerConfigChange.IntValue)c.value()).value(); case MAX_BATCH_BINDING_SIZE_Y -> batchY=((RtsServerConfigChange.IntValue)c.value()).value(); case MAX_BATCH_BINDING_SIZE_Z -> batchZ=((RtsServerConfigChange.IntValue)c.value()).value(); case MAX_LINKED_STORAGES -> linked=((RtsServerConfigChange.IntValue)c.value()).value(); case FUNNEL_PICKUP_RADIUS_BLOCKS -> funnel=((RtsServerConfigChange.DoubleValue)c.value()).value(); case WORKFLOWS_MAX_ACTIVE_PER_PLAYER -> workflows=((RtsServerConfigChange.IntValue)c.value()).value(); case HISTORY_MAX_ENTRIES_PER_STACK -> history=((RtsServerConfigChange.IntValue)c.value()).value(); case HISTORY_RETENTION_SECONDS -> historySeconds=((RtsServerConfigChange.IntValue)c.value()).value(); case FUNNEL_MAX_ENTITIES_PER_TICK -> entities=((RtsServerConfigChange.IntValue)c.value()).value(); case FUNNEL_MAX_ITEMS_PER_TICK -> items=((RtsServerConfigChange.IntValue)c.value()).value(); case FUNNEL_BUFFER_MAX_STACKS -> buffer=((RtsServerConfigChange.IntValue)c.value()).value(); case FUNNEL_TICK_INTERVAL -> interval=((RtsServerConfigChange.IntValue)c.value()).value(); case STORAGE_DROP_CACHE_SOFT_CAPACITY -> drop=((RtsServerConfigChange.IntValue)c.value()).value(); case BUILD_BATCH_BLOCKS_PER_TICK -> buildTick=((RtsServerConfigChange.IntValue)c.value()).value(); case BUILD_BATCH_MAX_QUEUED_JOBS -> buildJobs=((RtsServerConfigChange.IntValue)c.value()).value(); case TASK_ENGINE_MAX_UNITS_PER_TICK -> units=((RtsServerConfigChange.IntValue)c.value()).value(); case TASK_ENGINE_MAX_UNITS_PER_SLICE -> slice=((RtsServerConfigChange.IntValue)c.value()).value(); case TASK_ENGINE_MAX_NANOS_PER_TICK -> nanos=((RtsServerConfigChange.LongValue)c.value()).value(); case DEFAULT_STORAGE_PAGE_SIZE -> page=((RtsServerConfigChange.IntValue)c.value()).value(); case MAX_STORAGE_PAGE_SIZE -> maxPage=((RtsServerConfigChange.IntValue)c.value()).value(); case PAGE_CACHE_MAX_PLAYERS -> players=((RtsServerConfigChange.IntValue)c.value()).value(); case AE2_NETWORK_REFRESH_THROTTLE -> ae2=((RtsServerConfigChange.IntValue)c.value()).value(); case REFINED_STORAGE_NETWORK_REFRESH_THROTTLE -> rs=((RtsServerConfigChange.IntValue)c.value()).value(); case DIAGNOSTICS_MAX_TRACES -> traces=((RtsServerConfigChange.IntValue)c.value()).value(); case DIAGNOSTICS_MAX_WORKFLOW_LINKS -> workflowLinks=((RtsServerConfigChange.IntValue)c.value()).value(); case DIAGNOSTICS_MAX_TASK_LINKS -> taskLinks=((RtsServerConfigChange.IntValue)c.value()).value();
        }
        return new RtsServerConfigView(current.revision(), current.editable(), current.worldLoaded(), survival, share, action, blueprints, blueprint, volume, x, y, z, chain, chainTick, harvestTier, tree, home, cooldown, shape, radius, smart, smartDefault, diameter, diameterDefault, batch, batchX, batchY, batchZ, linked, funnel, workflows, history, historySeconds, entities, items, buffer, interval, drop, buildTick, buildJobs, units, slice, nanos, page, maxPage, players, ae2, rs, traces, workflowLinks, taskLinks);
    }
}
