package com.rtsbuilding.rtsbuilding.common.config;

import com.rtsbuilding.rtsbuilding.common.mining.MiningLimits;

/**
 * 服务端规则的只读快照。
 *
 * <p>该类型只描述已经通过 loader 配置验证的有效值，不读文件、不写配置，也不缓存
 * 世界状态。客户端设置界面、诊断和服务端配置入口都应使用这份快照，避免用本地
 * defaultconfig 文件猜测远端世界规则。</p>
 */
public record RtsServerConfigView(
        int revision,
        boolean editable,
        boolean worldLoaded,
        boolean enableSurvivalProgression,
        boolean shareSurvivalProgressionWithTeams,
        int maxActionRadiusBlocks,
        boolean enableBlueprints,
        int maxBlueprintBlocks,
        int maxSelectionVolume,
        int maxSelectionSizeX,
        int maxSelectionSizeY,
        int maxSelectionSizeZ,
        int ultimineMaxBlocks,
        int ultimineBlocksPerTick,
        String areaMineMaxHarvestTier,
        int maxTreeBlocks,
        int homeSelectionRadiusBlocks,
        int homeRelocationCooldownDays,
        int maxShapeDimension,
        int maxShapeRadius,
        int smartFillMaxBlocks,
        int smartFillDefaultBlocks,
        int smartFillMaxDiameter,
        int smartFillDefaultDiameter,
        int maxBatchBindingSelectionVolume,
        int maxBatchBindingSizeX,
        int maxBatchBindingSizeY,
        int maxBatchBindingSizeZ,
        int maxLinkedStorages,
        double funnelPickupRadiusBlocks,
        int workflowsMaxActivePerPlayer,
        int historyMaxEntriesPerStack,
        int historyRetentionSeconds,
        int funnelMaxEntitiesPerTick,
        int funnelMaxItemsPerTick,
        int funnelBufferMaxStacks,
        int funnelTickInterval,
        int storageDropCacheSoftCapacity,
        int buildBatchBlocksPerTick,
        int buildBatchMaxQueuedJobs,
        int taskEngineMaxUnitsPerTick,
        int taskEngineMaxUnitsPerSlice,
        long taskEngineMaxNanosPerTick,
        int defaultStoragePageSize,
        int maxStoragePageSize,
        int pageCacheMaxPlayers,
        int ae2NetworkRefreshThrottle,
        int refinedStorageNetworkRefreshThrottle,
        int diagnosticsMaxTraces,
        int diagnosticsMaxWorkflowLinks,
        int diagnosticsMaxTaskLinks) {

    public RtsServerConfigView {
        if (areaMineMaxHarvestTier == null || switch (areaMineMaxHarvestTier) {
            case "STONE", "IRON", "DIAMOND", "UNLIMITED" -> false;
            default -> true;
        }) {
            throw new IllegalArgumentException("invalid area mine harvest tier");
        }
        if (maxSelectionVolume < 1 || maxSelectionVolume > MiningLimits.MAX_VOLUME) {
            throw new IllegalArgumentException("maxSelectionVolume out of range");
        }
        if (maxSelectionSizeX < 1 || maxSelectionSizeY < 1 || maxSelectionSizeZ < 1) {
            throw new IllegalArgumentException("selection axes must be positive");
        }
        if (ultimineBlocksPerTick < 1) {
            throw new IllegalArgumentException("ultimine blocks per tick out of range");
        }
        if (workflowsMaxActivePerPlayer < 1) {
            throw new IllegalArgumentException("workflow capacity must be positive");
        }
        if (defaultStoragePageSize < 1 || maxStoragePageSize < defaultStoragePageSize) {
            throw new IllegalArgumentException("storage page sizes are inconsistent");
        }
    }

    /** 用于未进入世界时的个人端默认展示；不代表任何远端世界。 */
    public static RtsServerConfigView defaults() {
        return new RtsServerConfigView(
                0, false, false,
                false, false, 128, true, 20_000,
                MiningLimits.DEFAULT_VOLUME, 64, 64, 64, MiningLimits.DEFAULT_CHAIN_LIMIT, 32, "UNLIMITED",
                MiningLimits.DEFAULT_TREE_BLOCKS, 34, 20, 32, 32, 1_024, 512, 32, 16,
                262_144, 64, 64, 64, 200, 2.0D, 8, 3, 600,
                24, 48, 16, 2, 4_096, 64, 4, 256, 32, 8_000_000L,
                90, 180, 256, 10, 10, 1_024, 2_048, 2_048);
    }

    /** 不可编辑状态只影响写入口；读取和显示仍保留完整服务端有效值。 */
    public RtsServerConfigView readOnly() {
        return new RtsServerConfigView(revision, false, worldLoaded,
                enableSurvivalProgression, shareSurvivalProgressionWithTeams, maxActionRadiusBlocks,
                enableBlueprints, maxBlueprintBlocks, maxSelectionVolume, maxSelectionSizeX,
                maxSelectionSizeY, maxSelectionSizeZ, ultimineMaxBlocks, ultimineBlocksPerTick,
                areaMineMaxHarvestTier, maxTreeBlocks,
                homeSelectionRadiusBlocks, homeRelocationCooldownDays, maxShapeDimension, maxShapeRadius,
                smartFillMaxBlocks, smartFillDefaultBlocks, smartFillMaxDiameter, smartFillDefaultDiameter,
                maxBatchBindingSelectionVolume, maxBatchBindingSizeX, maxBatchBindingSizeY,
                maxBatchBindingSizeZ, maxLinkedStorages, funnelPickupRadiusBlocks,
                workflowsMaxActivePerPlayer, historyMaxEntriesPerStack, historyRetentionSeconds,
                funnelMaxEntitiesPerTick, funnelMaxItemsPerTick, funnelBufferMaxStacks, funnelTickInterval,
                storageDropCacheSoftCapacity, buildBatchBlocksPerTick, buildBatchMaxQueuedJobs,
                taskEngineMaxUnitsPerTick, taskEngineMaxUnitsPerSlice, taskEngineMaxNanosPerTick,
                defaultStoragePageSize, maxStoragePageSize, pageCacheMaxPlayers,
                ae2NetworkRefreshThrottle, refinedStorageNetworkRefreshThrottle,
                diagnosticsMaxTraces, diagnosticsMaxWorkflowLinks, diagnosticsMaxTaskLinks);
    }

    /**
     * 返回配置会话向客户端暴露的最小视图。
     *
     * <p>保存 ACK 和世界设置页需要的规则仍然保留；tick/slice 预算、任务队列、缓存、分页、
     * 外部网络刷新和诊断容量只属于服务端运行时，使用本地默认占位，避免把它们借由 typed
     * 配置响应复制成客户端配置镜像。服务端自身继续使用完整视图。</p>
     */
    public RtsServerConfigView clientProjection() {
        RtsServerConfigView defaults = defaults();
        return new RtsServerConfigView(revision, editable, worldLoaded,
                enableSurvivalProgression, shareSurvivalProgressionWithTeams, maxActionRadiusBlocks,
                enableBlueprints, maxBlueprintBlocks, maxSelectionVolume, maxSelectionSizeX,
                maxSelectionSizeY, maxSelectionSizeZ, ultimineMaxBlocks, defaults.ultimineBlocksPerTick(),
                areaMineMaxHarvestTier, maxTreeBlocks, homeSelectionRadiusBlocks, homeRelocationCooldownDays,
                maxShapeDimension, maxShapeRadius, smartFillMaxBlocks, smartFillDefaultBlocks,
                smartFillMaxDiameter, smartFillDefaultDiameter, maxBatchBindingSelectionVolume,
                maxBatchBindingSizeX, maxBatchBindingSizeY, maxBatchBindingSizeZ, maxLinkedStorages,
                funnelPickupRadiusBlocks, workflowsMaxActivePerPlayer, historyMaxEntriesPerStack,
                historyRetentionSeconds, defaults.funnelMaxEntitiesPerTick(),
                defaults.funnelMaxItemsPerTick(), defaults.funnelBufferMaxStacks(), defaults.funnelTickInterval(),
                defaults.storageDropCacheSoftCapacity(), defaults.buildBatchBlocksPerTick(),
                defaults.buildBatchMaxQueuedJobs(), defaults.taskEngineMaxUnitsPerTick(),
                defaults.taskEngineMaxUnitsPerSlice(), defaults.taskEngineMaxNanosPerTick(),
                defaults.defaultStoragePageSize(), defaults.maxStoragePageSize(), defaults.pageCacheMaxPlayers(),
                defaults.ae2NetworkRefreshThrottle(), defaults.refinedStorageNetworkRefreshThrottle(),
                defaults.diagnosticsMaxTraces(), defaults.diagnosticsMaxWorkflowLinks(),
                defaults.diagnosticsMaxTaskLinks());
    }
}
