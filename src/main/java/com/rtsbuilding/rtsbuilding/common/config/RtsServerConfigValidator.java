package com.rtsbuilding.rtsbuilding.common.config;

import com.rtsbuilding.rtsbuilding.common.mining.MiningLimits;
import com.rtsbuilding.rtsbuilding.common.smartfill.SmartFillLimits;

import java.util.HashSet;
import java.util.Set;

/** 不依赖 loader 的权限前置和跨字段约束校验。 */
public final class RtsServerConfigValidator {
    private RtsServerConfigValidator() {
    }

    public static String validate(RtsServerConfigView current,
                                  RtsServerConfigUpdateRequest request,
                                  boolean editable) {
        if (current == null || request == null || request.changes().isEmpty()) {
            return "no configuration changes";
        }
        if (!editable || !current.editable()) {
            return "server configuration is read-only";
        }
        if (request.expectedRevision() != current.revision()) {
            return "configuration revision changed";
        }
        Set<RtsServerConfigChange.Key> keys = new HashSet<>();
        for (RtsServerConfigChange change : request.changes()) {
            if (!keys.add(change.key())) {
                return "duplicate configuration key: " + change.key();
            }
            if (!validValue(change)) {
                return "invalid value for " + change.key();
            }
        }
        final RtsServerConfigView candidate;
        try {
            candidate = apply(current, request.changes());
        } catch (IllegalArgumentException invalidCombination) {
            // 让 G06 得到可保留草稿的结构化失败，而不是把跨字段非法值冒泡成崩溃。
            return invalidCombination.getMessage() == null
                    ? "configuration values are inconsistent" : invalidCombination.getMessage();
        }
        if (candidate.maxSelectionVolume() < 1 || candidate.maxSelectionVolume() > MiningLimits.MAX_VOLUME) {
            return "maxSelectionVolume out of range";
        }
        if (candidate.maxSelectionSizeX() < 1 || candidate.maxSelectionSizeY() < 1
                || candidate.maxSelectionSizeZ() < 1) {
            return "selection axes must be positive";
        }
        if (candidate.smartFillDefaultBlocks() > candidate.smartFillMaxBlocks()
                || candidate.smartFillDefaultDiameter() > candidate.smartFillMaxDiameter()) {
            return "smart fill defaults exceed their maxima";
        }
        if (candidate.defaultStoragePageSize() > candidate.maxStoragePageSize()) {
            return "default storage page size exceeds max";
        }
        if (candidate.maxActionRadiusBlocks() < 48 || candidate.maxActionRadiusBlocks() > 512
                || candidate.maxBlueprintBlocks() < 1 || candidate.maxBlueprintBlocks() > 200_000
                || candidate.ultimineMaxBlocks() < 1 || candidate.ultimineMaxBlocks() > MiningLimits.MAX_CHAIN_LIMIT
                || candidate.maxTreeBlocks() < 1 || candidate.maxTreeBlocks() > MiningLimits.MAX_TREE_BLOCKS
                || candidate.homeSelectionRadiusBlocks() < 1
                || candidate.maxShapeDimension() < 1
                || candidate.maxShapeRadius() < 1
                || candidate.homeRelocationCooldownDays() < 0
                || candidate.smartFillMaxBlocks() > SmartFillLimits.HARD_MAX_BLOCKS
                || candidate.smartFillMaxBlocks() < SmartFillLimits.MIN_BLOCKS
                || candidate.smartFillDefaultBlocks() < SmartFillLimits.MIN_BLOCKS
                || candidate.smartFillMaxDiameter() < SmartFillLimits.MIN_DIAMETER
                || candidate.smartFillDefaultDiameter() < SmartFillLimits.MIN_DIAMETER
                || candidate.smartFillMaxDiameter() > SmartFillLimits.HARD_MAX_DIAMETER
                || candidate.maxBatchBindingSelectionVolume() < 1 || candidate.maxBatchBindingSelectionVolume() > MiningLimits.MAX_VOLUME
                || candidate.maxBatchBindingSizeX() < 1 || candidate.maxBatchBindingSizeY() < 1
                || candidate.maxBatchBindingSizeZ() < 1 || candidate.maxLinkedStorages() < 1 || candidate.maxLinkedStorages() > 4_096
                || candidate.funnelPickupRadiusBlocks() < 0.0D || candidate.funnelPickupRadiusBlocks() > 32.0D
                || candidate.workflowsMaxActivePerPlayer() < 1 || candidate.workflowsMaxActivePerPlayer() > 1_024
                || candidate.historyMaxEntriesPerStack() < 1 || candidate.historyRetentionSeconds() < 1
                || candidate.funnelMaxEntitiesPerTick() < 1 || candidate.funnelMaxItemsPerTick() < 1
                || candidate.funnelBufferMaxStacks() < 1 || candidate.funnelTickInterval() < 1
                || candidate.storageDropCacheSoftCapacity() < 1
                || candidate.buildBatchBlocksPerTick() < 1 || candidate.buildBatchMaxQueuedJobs() < 1
                || candidate.taskEngineMaxUnitsPerTick() < 1 || candidate.taskEngineMaxUnitsPerSlice() < 1
                || candidate.taskEngineMaxNanosPerTick() < 1L
                || candidate.defaultStoragePageSize() > 4_096 || candidate.maxStoragePageSize() > 8_192
                || candidate.pageCacheMaxPlayers() < 1 || candidate.ae2NetworkRefreshThrottle() < 1
                || candidate.refinedStorageNetworkRefreshThrottle() < 1
                || candidate.diagnosticsMaxTraces() < 1 || candidate.diagnosticsMaxWorkflowLinks() < 1
                || candidate.diagnosticsMaxTaskLinks() < 1) {
            return "configuration value is outside its supported range";
        }
        return null;
    }

    /** 纯函数合并，用于服务端保存前校验和单元测试。 */
    public static RtsServerConfigView apply(RtsServerConfigView current,
                                            java.util.List<RtsServerConfigChange> changes) {
        boolean survival = current.enableSurvivalProgression();
        boolean share = current.shareSurvivalProgressionWithTeams();
        int radius = current.maxActionRadiusBlocks();
        boolean blueprints = current.enableBlueprints();
        int blueprintBlocks = current.maxBlueprintBlocks();
        int volume = current.maxSelectionVolume(), x = current.maxSelectionSizeX();
        int y = current.maxSelectionSizeY(), z = current.maxSelectionSizeZ();
        int chain = current.ultimineMaxBlocks(), chainTick = current.ultimineBlocksPerTick(), tree = current.maxTreeBlocks();
        String harvestTier = current.areaMineMaxHarvestTier();
        int homeRadius = current.homeSelectionRadiusBlocks(), cooldown = current.homeRelocationCooldownDays();
        int shapeDimension = current.maxShapeDimension(), shapeRadius = current.maxShapeRadius();
        int smartMax = current.smartFillMaxBlocks(), smartDefault = current.smartFillDefaultBlocks();
        int diameterMax = current.smartFillMaxDiameter(), diameterDefault = current.smartFillDefaultDiameter();
        int batchVolume = current.maxBatchBindingSelectionVolume(), batchX = current.maxBatchBindingSizeX();
        int batchY = current.maxBatchBindingSizeY(), batchZ = current.maxBatchBindingSizeZ();
        int linked = current.maxLinkedStorages(); double funnelRadius = current.funnelPickupRadiusBlocks();
        int workflows = current.workflowsMaxActivePerPlayer(), historyEntries = current.historyMaxEntriesPerStack();
        int historySeconds = current.historyRetentionSeconds(), entities = current.funnelMaxEntitiesPerTick();
        int items = current.funnelMaxItemsPerTick(), buffer = current.funnelBufferMaxStacks();
        int interval = current.funnelTickInterval(), drop = current.storageDropCacheSoftCapacity();
        int buildTick = current.buildBatchBlocksPerTick(), buildJobs = current.buildBatchMaxQueuedJobs();
        int unitsTick = current.taskEngineMaxUnitsPerTick(), unitsSlice = current.taskEngineMaxUnitsPerSlice();
        long nanos = current.taskEngineMaxNanosPerTick(); int page = current.defaultStoragePageSize();
        int maxPage = current.maxStoragePageSize(), pagePlayers = current.pageCacheMaxPlayers();
        int ae2 = current.ae2NetworkRefreshThrottle(), rs = current.refinedStorageNetworkRefreshThrottle();
        int traces = current.diagnosticsMaxTraces(), workflowLinks = current.diagnosticsMaxWorkflowLinks();
        int taskLinks = current.diagnosticsMaxTaskLinks();
        for (RtsServerConfigChange change : changes) {
            switch (change.key()) {
                case ENABLE_SURVIVAL_PROGRESSION -> survival = ((RtsServerConfigChange.BooleanValue) change.value()).value();
                case SHARE_SURVIVAL_PROGRESSION_WITH_TEAMS -> share = ((RtsServerConfigChange.BooleanValue) change.value()).value();
                case MAX_ACTION_RADIUS_BLOCKS -> radius = ((RtsServerConfigChange.IntValue) change.value()).value();
                case ENABLE_BLUEPRINTS -> blueprints = ((RtsServerConfigChange.BooleanValue) change.value()).value();
                case MAX_BLUEPRINT_BLOCKS -> blueprintBlocks = ((RtsServerConfigChange.IntValue) change.value()).value();
                case MAX_SELECTION_VOLUME -> volume = ((RtsServerConfigChange.IntValue) change.value()).value();
                case MAX_SELECTION_SIZE_X -> x = ((RtsServerConfigChange.IntValue) change.value()).value();
                case MAX_SELECTION_SIZE_Y -> y = ((RtsServerConfigChange.IntValue) change.value()).value();
                case MAX_SELECTION_SIZE_Z -> z = ((RtsServerConfigChange.IntValue) change.value()).value();
                case ULTIMINE_MAX_BLOCKS -> chain = ((RtsServerConfigChange.IntValue) change.value()).value();
                case ULTIMINE_BLOCKS_PER_TICK -> chainTick = ((RtsServerConfigChange.IntValue) change.value()).value();
                case AREA_MINE_MAX_HARVEST_TIER -> harvestTier = ((RtsServerConfigChange.StringValue) change.value()).value();
                case MAX_TREE_BLOCKS -> tree = ((RtsServerConfigChange.IntValue) change.value()).value();
                case HOME_SELECTION_RADIUS_BLOCKS -> homeRadius = ((RtsServerConfigChange.IntValue) change.value()).value();
                case HOME_RELOCATION_COOLDOWN_DAYS -> cooldown = ((RtsServerConfigChange.IntValue) change.value()).value();
                case MAX_SHAPE_DIMENSION -> shapeDimension = ((RtsServerConfigChange.IntValue) change.value()).value();
                case MAX_SHAPE_RADIUS -> shapeRadius = ((RtsServerConfigChange.IntValue) change.value()).value();
                case SMART_FILL_MAX_BLOCKS -> smartMax = ((RtsServerConfigChange.IntValue) change.value()).value();
                case SMART_FILL_DEFAULT_BLOCKS -> smartDefault = ((RtsServerConfigChange.IntValue) change.value()).value();
                case SMART_FILL_MAX_DIAMETER -> diameterMax = ((RtsServerConfigChange.IntValue) change.value()).value();
                case SMART_FILL_DEFAULT_DIAMETER -> diameterDefault = ((RtsServerConfigChange.IntValue) change.value()).value();
                case MAX_BATCH_BINDING_SELECTION_VOLUME -> batchVolume = ((RtsServerConfigChange.IntValue) change.value()).value();
                case MAX_BATCH_BINDING_SIZE_X -> batchX = ((RtsServerConfigChange.IntValue) change.value()).value();
                case MAX_BATCH_BINDING_SIZE_Y -> batchY = ((RtsServerConfigChange.IntValue) change.value()).value();
                case MAX_BATCH_BINDING_SIZE_Z -> batchZ = ((RtsServerConfigChange.IntValue) change.value()).value();
                case MAX_LINKED_STORAGES -> linked = ((RtsServerConfigChange.IntValue) change.value()).value();
                case FUNNEL_PICKUP_RADIUS_BLOCKS -> funnelRadius = ((RtsServerConfigChange.DoubleValue) change.value()).value();
                case WORKFLOWS_MAX_ACTIVE_PER_PLAYER -> workflows = ((RtsServerConfigChange.IntValue) change.value()).value();
                case HISTORY_MAX_ENTRIES_PER_STACK -> historyEntries = ((RtsServerConfigChange.IntValue) change.value()).value();
                case HISTORY_RETENTION_SECONDS -> historySeconds = ((RtsServerConfigChange.IntValue) change.value()).value();
                case FUNNEL_MAX_ENTITIES_PER_TICK -> entities = ((RtsServerConfigChange.IntValue) change.value()).value();
                case FUNNEL_MAX_ITEMS_PER_TICK -> items = ((RtsServerConfigChange.IntValue) change.value()).value();
                case FUNNEL_BUFFER_MAX_STACKS -> buffer = ((RtsServerConfigChange.IntValue) change.value()).value();
                case FUNNEL_TICK_INTERVAL -> interval = ((RtsServerConfigChange.IntValue) change.value()).value();
                case STORAGE_DROP_CACHE_SOFT_CAPACITY -> drop = ((RtsServerConfigChange.IntValue) change.value()).value();
                case BUILD_BATCH_BLOCKS_PER_TICK -> buildTick = ((RtsServerConfigChange.IntValue) change.value()).value();
                case BUILD_BATCH_MAX_QUEUED_JOBS -> buildJobs = ((RtsServerConfigChange.IntValue) change.value()).value();
                case TASK_ENGINE_MAX_UNITS_PER_TICK -> unitsTick = ((RtsServerConfigChange.IntValue) change.value()).value();
                case TASK_ENGINE_MAX_UNITS_PER_SLICE -> unitsSlice = ((RtsServerConfigChange.IntValue) change.value()).value();
                case TASK_ENGINE_MAX_NANOS_PER_TICK -> nanos = ((RtsServerConfigChange.LongValue) change.value()).value();
                case DEFAULT_STORAGE_PAGE_SIZE -> page = ((RtsServerConfigChange.IntValue) change.value()).value();
                case MAX_STORAGE_PAGE_SIZE -> maxPage = ((RtsServerConfigChange.IntValue) change.value()).value();
                case PAGE_CACHE_MAX_PLAYERS -> pagePlayers = ((RtsServerConfigChange.IntValue) change.value()).value();
                case AE2_NETWORK_REFRESH_THROTTLE -> ae2 = ((RtsServerConfigChange.IntValue) change.value()).value();
                case REFINED_STORAGE_NETWORK_REFRESH_THROTTLE -> rs = ((RtsServerConfigChange.IntValue) change.value()).value();
                case DIAGNOSTICS_MAX_TRACES -> traces = ((RtsServerConfigChange.IntValue) change.value()).value();
                case DIAGNOSTICS_MAX_WORKFLOW_LINKS -> workflowLinks = ((RtsServerConfigChange.IntValue) change.value()).value();
                case DIAGNOSTICS_MAX_TASK_LINKS -> taskLinks = ((RtsServerConfigChange.IntValue) change.value()).value();
            }
        }
        return new RtsServerConfigView(current.revision(), current.editable(), current.worldLoaded(), survival, share,
                radius, blueprints, blueprintBlocks, volume, x, y, z, chain, chainTick, harvestTier, tree, homeRadius, cooldown,
                shapeDimension, shapeRadius, smartMax, smartDefault, diameterMax, diameterDefault,
                batchVolume, batchX, batchY, batchZ, linked, funnelRadius, workflows, historyEntries,
                historySeconds, entities, items, buffer, interval, drop, buildTick, buildJobs, unitsTick,
                unitsSlice, nanos, page, maxPage, pagePlayers, ae2, rs, traces, workflowLinks, taskLinks);
    }

    private static boolean validValue(RtsServerConfigChange change) {
        if (change.value() instanceof RtsServerConfigChange.IntValue value) {
            return value.value() > 0 || (change.key() == RtsServerConfigChange.Key.HOME_RELOCATION_COOLDOWN_DAYS
                    && value.value() == 0);
        }
        if (change.value() instanceof RtsServerConfigChange.LongValue value) {
            return value.value() > 0L;
        }
        if (change.value() instanceof RtsServerConfigChange.DoubleValue value) {
            return Double.isFinite(value.value()) && value.value() >= 0.0D;
        }
        if (change.value() instanceof RtsServerConfigChange.StringValue value) {
            return change.key() == RtsServerConfigChange.Key.AREA_MINE_MAX_HARVEST_TIER
                    && switch (value.value()) {
                case "STONE", "IRON", "DIAMOND", "UNLIMITED" -> true;
                default -> false;
            };
        }
        return true;
    }
}
