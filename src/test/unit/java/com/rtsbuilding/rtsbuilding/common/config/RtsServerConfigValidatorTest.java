package com.rtsbuilding.rtsbuilding.common.config;

import com.rtsbuilding.rtsbuilding.common.mining.MiningLimits;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/** 纯 typed 校验回归：权限、revision、NO_CHANGE、宽范围与冻结硬边界。 */
class RtsServerConfigValidatorTest {
    @Test
    void permissionAndRevisionAreRejectedBeforeApplyingChanges() {
        RtsServerConfigView current = editableView();
        RtsServerConfigUpdateRequest request = request(current.revision(),
                new RtsServerConfigChange(RtsServerConfigChange.Key.ENABLE_BLUEPRINTS,
                        new RtsServerConfigChange.BooleanValue(!current.enableBlueprints())));

        assertEquals("server configuration is read-only",
                RtsServerConfigValidator.validate(current, request, false));
        assertEquals("configuration revision changed",
                RtsServerConfigValidator.validate(current,
                        new RtsServerConfigUpdateRequest(current.revision() - 1, request.changes()), true));
    }

    @Test
    void unchangedCandidateIsTheNoChangePathAndNeedsNoSave() {
        RtsServerConfigView current = editableView();
        RtsServerConfigUpdateRequest request = request(current.revision(),
                new RtsServerConfigChange(RtsServerConfigChange.Key.HISTORY_RETENTION_SECONDS,
                        new RtsServerConfigChange.IntValue(current.historyRetentionSeconds())));

        assertNull(RtsServerConfigValidator.validate(current, request, true));
        RtsServerConfigView candidate = RtsServerConfigValidator.apply(current, request.changes());
        assertEquals(current, candidate);
    }

    @Test
    void widePositiveValuesAreAcceptedWhileFrozenBoundsAndCombinationsFail() {
        RtsServerConfigView current = editableView();
        RtsServerConfigUpdateRequest wide = new RtsServerConfigUpdateRequest(current.revision(), List.of(
                new RtsServerConfigChange(RtsServerConfigChange.Key.HISTORY_RETENTION_SECONDS,
                        new RtsServerConfigChange.IntValue(Integer.MAX_VALUE)),
                new RtsServerConfigChange(RtsServerConfigChange.Key.FUNNEL_TICK_INTERVAL,
                        new RtsServerConfigChange.IntValue(Integer.MAX_VALUE)),
                new RtsServerConfigChange(RtsServerConfigChange.Key.HOME_SELECTION_RADIUS_BLOCKS,
                        new RtsServerConfigChange.IntValue(Integer.MAX_VALUE)),
                new RtsServerConfigChange(RtsServerConfigChange.Key.MAX_SHAPE_DIMENSION,
                        new RtsServerConfigChange.IntValue(Integer.MAX_VALUE)),
                new RtsServerConfigChange(RtsServerConfigChange.Key.MAX_SHAPE_RADIUS,
                        new RtsServerConfigChange.IntValue(Integer.MAX_VALUE)),
                new RtsServerConfigChange(RtsServerConfigChange.Key.TASK_ENGINE_MAX_NANOS_PER_TICK,
                        new RtsServerConfigChange.LongValue(Long.MAX_VALUE))));
        assertNull(RtsServerConfigValidator.validate(current, wide, true));

        RtsServerConfigUpdateRequest volume = request(current.revision(),
                new RtsServerConfigChange(RtsServerConfigChange.Key.MAX_SELECTION_VOLUME,
                        new RtsServerConfigChange.IntValue(MiningLimits.MAX_VOLUME + 1)));
        assertEquals("maxSelectionVolume out of range",
                RtsServerConfigValidator.validate(current, volume, true));

        RtsServerConfigUpdateRequest pages = request(current.revision(),
                new RtsServerConfigChange(RtsServerConfigChange.Key.MAX_STORAGE_PAGE_SIZE,
                        new RtsServerConfigChange.IntValue(current.defaultStoragePageSize() - 1)));
        assertNotNull(RtsServerConfigValidator.validate(current, pages, true));
    }

    @Test
    void treeDefaultsAndTechnicalMaximumStayExplicit() {
        RtsServerConfigView defaults = RtsServerConfigView.defaults();
        assertEquals(MiningLimits.DEFAULT_TREE_BLOCKS, defaults.maxTreeBlocks());
        assertEquals(MiningLimits.MAX_TREE_BLOCKS, MiningLimits.MAX_VOLUME);
    }

    private static RtsServerConfigUpdateRequest request(int revision, RtsServerConfigChange change) {
        return new RtsServerConfigUpdateRequest(revision, List.of(change));
    }

    private static RtsServerConfigView editableView() {
        RtsServerConfigView d = RtsServerConfigView.defaults();
        return new RtsServerConfigView(11, true, true,
                d.enableSurvivalProgression(), d.shareSurvivalProgressionWithTeams(),
                d.maxActionRadiusBlocks(), d.enableBlueprints(), d.maxBlueprintBlocks(),
                d.maxSelectionVolume(), d.maxSelectionSizeX(), d.maxSelectionSizeY(), d.maxSelectionSizeZ(),
                d.ultimineMaxBlocks(), d.ultimineBlocksPerTick(), d.areaMineMaxHarvestTier(), d.maxTreeBlocks(),
                d.homeSelectionRadiusBlocks(), d.homeRelocationCooldownDays(), d.maxShapeDimension(), d.maxShapeRadius(),
                d.smartFillMaxBlocks(), d.smartFillDefaultBlocks(), d.smartFillMaxDiameter(), d.smartFillDefaultDiameter(),
                d.maxBatchBindingSelectionVolume(), d.maxBatchBindingSizeX(), d.maxBatchBindingSizeY(), d.maxBatchBindingSizeZ(),
                d.maxLinkedStorages(), d.funnelPickupRadiusBlocks(), d.workflowsMaxActivePerPlayer(),
                d.historyMaxEntriesPerStack(), d.historyRetentionSeconds(), d.funnelMaxEntitiesPerTick(),
                d.funnelMaxItemsPerTick(), d.funnelBufferMaxStacks(), d.funnelTickInterval(),
                d.storageDropCacheSoftCapacity(), d.buildBatchBlocksPerTick(), d.buildBatchMaxQueuedJobs(),
                d.taskEngineMaxUnitsPerTick(), d.taskEngineMaxUnitsPerSlice(), d.taskEngineMaxNanosPerTick(),
                d.defaultStoragePageSize(), d.maxStoragePageSize(), d.pageCacheMaxPlayers(),
                d.ae2NetworkRefreshThrottle(), d.refinedStorageNetworkRefreshThrottle(),
                d.diagnosticsMaxTraces(), d.diagnosticsMaxWorkflowLinks(), d.diagnosticsMaxTaskLinks());
    }
}
