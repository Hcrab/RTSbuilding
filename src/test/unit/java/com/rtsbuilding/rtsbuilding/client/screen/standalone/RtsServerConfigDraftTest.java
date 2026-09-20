package com.rtsbuilding.rtsbuilding.client.screen.standalone;

import com.rtsbuilding.rtsbuilding.common.config.RtsServerConfigChange;
import com.rtsbuilding.rtsbuilding.common.config.RtsServerConfigClientSession;
import com.rtsbuilding.rtsbuilding.common.config.RtsServerConfigUpdateResult;
import com.rtsbuilding.rtsbuilding.common.config.RtsServerConfigValidator;
import com.rtsbuilding.rtsbuilding.common.config.RtsServerConfigView;
import com.rtsbuilding.rtsbuilding.common.config.RtsServerConfigWire;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** G06A 草稿、基准 revision 和迟到响应的行为证据。 */
class RtsServerConfigDraftTest {
    @Test
    void changesKeepLargeAxisAndVolumeValuesTyped() {
        RtsServerConfigView view = RtsServerConfigView.defaults().readOnly();
        RtsServerConfigDraft draft = RtsServerConfigDraft.from(view);
        draft.setText(RtsServerConfigChange.Key.MAX_SELECTION_SIZE_X, "80");
        draft.setText(RtsServerConfigChange.Key.MAX_SELECTION_VOLUME, "4096");
        draft.setText(RtsServerConfigChange.Key.ULTIMINE_MAX_BLOCKS, "4096");

        List<RtsServerConfigChange> changes = draft.changes();
        assertEquals(0, draft.baselineRevision());
        assertTrue(draft.isDirty());
        assertEquals(80, intValue(changes, RtsServerConfigChange.Key.MAX_SELECTION_SIZE_X));
        assertEquals(4096, intValue(changes, RtsServerConfigChange.Key.MAX_SELECTION_VOLUME));
        assertEquals(4096, intValue(changes, RtsServerConfigChange.Key.ULTIMINE_MAX_BLOCKS));

        draft.accept(view);
        assertFalse(draft.isDirty());
    }

    @Test
    void invalidTextIsRejectedInsteadOfClampedToAnOldDefault() {
        RtsServerConfigDraft draft = RtsServerConfigDraft.from(RtsServerConfigView.defaults().readOnly());
        draft.setText(RtsServerConfigChange.Key.MAX_SELECTION_SIZE_X, "not-a-number");
        assertThrows(IllegalArgumentException.class, draft::changes);
    }

    @Test
    void failedResultKeepsDraftUntilSuccessfulViewIsAccepted() {
        RtsServerConfigView baseline = RtsServerConfigView.defaults().readOnly();
        RtsServerConfigDraft draft = RtsServerConfigDraft.from(baseline);
        draft.setText(RtsServerConfigChange.Key.MAX_SELECTION_SIZE_X, "80");
        assertTrue(draft.isDirty());

        RtsServerConfigView serverView = RtsServerConfigValidator.apply(baseline, draft.changes());
        RtsServerConfigUpdateResult failed = RtsServerConfigUpdateResult.revisionConflict(serverView);
        assertFalse(failed.succeeded());
        assertTrue(draft.isDirty());
        assertEquals(0, draft.baselineRevision());

        RtsServerConfigUpdateResult succeeded = RtsServerConfigUpdateResult.applied(serverView);
        assertTrue(succeeded.succeeded());
        draft.accept(succeeded.view());
        assertFalse(draft.isDirty());
        assertEquals(80, Integer.parseInt(draft.text(RtsServerConfigChange.Key.MAX_SELECTION_SIZE_X)));
    }

    @Test
    void ordinaryFieldDirectoryExcludesTechnicalServerBudgets() {
        Set<RtsServerConfigChange.Key> visible = java.util.Arrays.stream(RtsServerConfigField.values())
                .map(field -> field.key).collect(Collectors.toSet());
        assertFalse(visible.contains(RtsServerConfigChange.Key.TASK_ENGINE_MAX_UNITS_PER_TICK));
        assertFalse(visible.contains(RtsServerConfigChange.Key.DEFAULT_STORAGE_PAGE_SIZE));
        assertFalse(visible.contains(RtsServerConfigChange.Key.AE2_NETWORK_REFRESH_THROTTLE));
        assertTrue(visible.contains(RtsServerConfigChange.Key.MAX_SELECTION_SIZE_X));
    }

    @Test
    void sentSaveRevisionAndSessionRejectLateQuery() {
        RtsServerConfigClientSession session = new RtsServerConfigClientSession();
        session.begin(42L);
        session.query();
        byte[] encoded = session.update(7, List.of(new RtsServerConfigChange(
                RtsServerConfigChange.Key.MAX_SELECTION_SIZE_X,
                new RtsServerConfigChange.IntValue(80))));
        RtsServerConfigWire.Request request = RtsServerConfigWire.decodeRequest(encoded);
        assertEquals(7, request.update().request().expectedRevision());
        assertEquals(2, session.lastIssuedRequestId());

        RtsServerConfigUpdateResult result = RtsServerConfigUpdateResult.applied(
                RtsServerConfigView.defaults().readOnly());
        assertFalse(session.accept(new RtsServerConfigWire.Response(42L, 1, result)));
        assertFalse(session.accept(new RtsServerConfigWire.Response(41L, 2, result)));
        session.begin(43L);
        assertFalse(session.accept(new RtsServerConfigWire.Response(42L, 2, result)));
    }

    @Test
    void queryAfterSaveDoesNotRejectThePendingSaveAck() {
        RtsServerConfigClientSession session = new RtsServerConfigClientSession();
        session.begin(42L);
        session.query();
        byte[] encoded = session.update(0, List.of(new RtsServerConfigChange(
                RtsServerConfigChange.Key.MAX_SELECTION_SIZE_X,
                new RtsServerConfigChange.IntValue(80))));
        RtsServerConfigWire.Request request = RtsServerConfigWire.decodeRequest(encoded);
        assertEquals(2, request.update().requestId());

        // 屏幕 resize/重建可能触发一次查询；请求号 3 不能吞掉保存请求号 2 的 ACK。
        session.query();
        RtsServerConfigUpdateResult result = RtsServerConfigUpdateResult.applied(
                RtsServerConfigView.defaults().readOnly());
        assertTrue(session.accept(new RtsServerConfigWire.Response(42L, 2, result)));
    }

    @Test
    void higherRevisionBroadcastDoesNotSwallowThePendingSaveAck() {
        RtsServerConfigClientSession session = new RtsServerConfigClientSession();
        session.begin(42L);
        RtsServerConfigView baseline = RtsServerConfigView.defaults().readOnly();
        List<RtsServerConfigChange> firstChanges = List.of(new RtsServerConfigChange(
                RtsServerConfigChange.Key.MAX_SELECTION_SIZE_X,
                new RtsServerConfigChange.IntValue(80)));
        RtsServerConfigView saveView = RtsServerConfigValidator.apply(baseline, firstChanges);
        byte[] encoded = session.update(0, firstChanges);
        int saveRequestId = RtsServerConfigWire.decodeRequest(encoded).update().requestId();

        RtsServerConfigView broadcastView = withRevision(RtsServerConfigValidator.apply(saveView, List.of(
                new RtsServerConfigChange(
                        RtsServerConfigChange.Key.MAX_SELECTION_SIZE_Y,
                        new RtsServerConfigChange.IntValue(81)))), 2);
        assertTrue(session.accept(new RtsServerConfigWire.Response(
                42L, 0, RtsServerConfigUpdateResult.applied(broadcastView))));
        session.query();

        // 广播已经领先时，保存 ACK 仍须被 UI 识别为本次 pending 的结束信号，
        // 但不能把 current 镜像回退到 ACK 的较低 revision。
        assertTrue(session.accept(new RtsServerConfigWire.Response(
                42L, saveRequestId, RtsServerConfigUpdateResult.applied(saveView))));
        assertEquals(broadcastView.revision(), session.current().revision());
    }

    private static RtsServerConfigView withRevision(RtsServerConfigView view, int revision) {
        return new RtsServerConfigView(
                revision, view.editable(), view.worldLoaded(),
                view.enableSurvivalProgression(), view.shareSurvivalProgressionWithTeams(),
                view.maxActionRadiusBlocks(), view.enableBlueprints(), view.maxBlueprintBlocks(),
                view.maxSelectionVolume(), view.maxSelectionSizeX(), view.maxSelectionSizeY(),
                view.maxSelectionSizeZ(), view.ultimineMaxBlocks(), view.ultimineBlocksPerTick(),
                view.areaMineMaxHarvestTier(), view.maxTreeBlocks(),
                view.homeSelectionRadiusBlocks(), view.homeRelocationCooldownDays(),
                view.maxShapeDimension(), view.maxShapeRadius(), view.smartFillMaxBlocks(),
                view.smartFillDefaultBlocks(), view.smartFillMaxDiameter(),
                view.smartFillDefaultDiameter(), view.maxBatchBindingSelectionVolume(),
                view.maxBatchBindingSizeX(), view.maxBatchBindingSizeY(),
                view.maxBatchBindingSizeZ(), view.maxLinkedStorages(),
                view.funnelPickupRadiusBlocks(), view.workflowsMaxActivePerPlayer(),
                view.historyMaxEntriesPerStack(), view.historyRetentionSeconds(),
                view.funnelMaxEntitiesPerTick(), view.funnelMaxItemsPerTick(),
                view.funnelBufferMaxStacks(), view.funnelTickInterval(),
                view.storageDropCacheSoftCapacity(), view.buildBatchBlocksPerTick(),
                view.buildBatchMaxQueuedJobs(), view.taskEngineMaxUnitsPerTick(),
                view.taskEngineMaxUnitsPerSlice(), view.taskEngineMaxNanosPerTick(),
                view.defaultStoragePageSize(), view.maxStoragePageSize(),
                view.pageCacheMaxPlayers(), view.ae2NetworkRefreshThrottle(),
                view.refinedStorageNetworkRefreshThrottle(), view.diagnosticsMaxTraces(),
                view.diagnosticsMaxWorkflowLinks(), view.diagnosticsMaxTaskLinks());
    }

    private static int intValue(List<RtsServerConfigChange> changes, RtsServerConfigChange.Key key) {
        return ((RtsServerConfigChange.IntValue) changes.stream()
                .filter(change -> change.key() == key).findFirst().orElseThrow().value()).value();
    }
}
