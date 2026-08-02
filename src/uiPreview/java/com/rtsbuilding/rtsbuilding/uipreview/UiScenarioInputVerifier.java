package com.rtsbuilding.rtsbuilding.uipreview;

import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uicore.routing.KeyboardFocus;
import com.rtsbuilding.rtsbuilding.uicore.routing.PointerCapture;
import com.rtsbuilding.rtsbuilding.uicore.routing.UiEscapeStack;
import com.rtsbuilding.rtsbuilding.uicore.routing.UiLayerStack;
import com.rtsbuilding.rtsbuilding.uicore.blueprint.BlueprintUiAction;
import com.rtsbuilding.rtsbuilding.uicore.blueprint.BlueprintUiReducer;
import com.rtsbuilding.rtsbuilding.uicore.blueprint.BlueprintUiState;
import com.rtsbuilding.rtsbuilding.uicore.blueprint.BlueprintUiTransition;
import com.rtsbuilding.rtsbuilding.uicore.blueprint.BlueprintLibraryUiAction;
import com.rtsbuilding.rtsbuilding.uicore.blueprint.BlueprintLibraryUiReducer;
import com.rtsbuilding.rtsbuilding.uicore.blueprint.BlueprintLibraryUiState;
import com.rtsbuilding.rtsbuilding.uicore.blueprint.BlueprintLibraryUiTransition;
import com.rtsbuilding.rtsbuilding.uicore.topbar.TopBarUiAction;
import com.rtsbuilding.rtsbuilding.uicore.topbar.TopBarUiButtonId;
import com.rtsbuilding.rtsbuilding.uicore.topbar.TopBarUiReducer;
import com.rtsbuilding.rtsbuilding.uicore.topbar.TopBarUiState;
import com.rtsbuilding.rtsbuilding.uicore.topbar.TopBarUiTransition;
import com.rtsbuilding.rtsbuilding.uicore.settings.SettingsId;
import com.rtsbuilding.rtsbuilding.uicore.settings.SettingsSectionId;
import com.rtsbuilding.rtsbuilding.uicore.settings.SettingsUiAction;
import com.rtsbuilding.rtsbuilding.uicore.settings.SettingsUiCatalog;
import com.rtsbuilding.rtsbuilding.uicore.settings.SettingsUiReducer;
import com.rtsbuilding.rtsbuilding.uicore.settings.SettingsUiState;
import com.rtsbuilding.rtsbuilding.uicore.settings.SettingsUiTransition;
import com.rtsbuilding.rtsbuilding.uicore.settings.SettingsUiValue;
import com.rtsbuilding.rtsbuilding.uicore.bottom.BottomBarUiAction;
import com.rtsbuilding.rtsbuilding.uicore.bottom.BottomBarUiReducer;
import com.rtsbuilding.rtsbuilding.uicore.bottom.BottomBarUiState;
import com.rtsbuilding.rtsbuilding.uicore.bottom.BottomBarUiTab;
import com.rtsbuilding.rtsbuilding.uicore.bottom.BottomBarUiTransition;
import com.rtsbuilding.rtsbuilding.uicore.quickbuild.QuickBuildUiAction;
import com.rtsbuilding.rtsbuilding.uicore.quickbuild.QuickBuildUiMode;
import com.rtsbuilding.rtsbuilding.uicore.quickbuild.QuickBuildUiReducer;
import com.rtsbuilding.rtsbuilding.uicore.quickbuild.QuickBuildUiState;
import com.rtsbuilding.rtsbuilding.uicore.quickbuild.QuickBuildUiTransition;
import com.rtsbuilding.rtsbuilding.uicore.ultimine.UltimineUiAction;
import com.rtsbuilding.rtsbuilding.uicore.ultimine.UltimineUiPhase;
import com.rtsbuilding.rtsbuilding.uicore.ultimine.UltimineUiReducer;
import com.rtsbuilding.rtsbuilding.uicore.ultimine.UltimineUiState;
import com.rtsbuilding.rtsbuilding.uicore.ultimine.UltimineUiTransition;
import com.rtsbuilding.rtsbuilding.uicore.culling.CullingUiAction;
import com.rtsbuilding.rtsbuilding.uicore.culling.CullingUiDirection;
import com.rtsbuilding.rtsbuilding.uicore.culling.CullingUiReducer;
import com.rtsbuilding.rtsbuilding.uicore.culling.CullingUiState;
import com.rtsbuilding.rtsbuilding.uicore.culling.CullingUiTransition;
import com.rtsbuilding.rtsbuilding.uicore.storage.StorageUiAction;
import com.rtsbuilding.rtsbuilding.uicore.storage.StorageUiReducer;
import com.rtsbuilding.rtsbuilding.uicore.storage.StorageUiState;
import com.rtsbuilding.rtsbuilding.uicore.storage.StorageUiTransition;
import com.rtsbuilding.rtsbuilding.uicore.workflow.WorkflowUiAction;
import com.rtsbuilding.rtsbuilding.uicore.workflow.WorkflowUiReducer;
import com.rtsbuilding.rtsbuilding.uicore.workflow.WorkflowUiState;
import com.rtsbuilding.rtsbuilding.uicore.workflow.WorkflowUiTransition;
import com.rtsbuilding.rtsbuilding.uicore.guide.GuideUiAction;
import com.rtsbuilding.rtsbuilding.uicore.guide.GuideUiReducer;
import com.rtsbuilding.rtsbuilding.uicore.guide.GuideUiState;
import com.rtsbuilding.rtsbuilding.uicore.funnel.FunnelUiAction;
import com.rtsbuilding.rtsbuilding.uicore.funnel.FunnelUiReducer;
import com.rtsbuilding.rtsbuilding.uicore.funnel.FunnelUiState;
import com.rtsbuilding.rtsbuilding.uicore.craft.CraftQuantityAction;
import com.rtsbuilding.rtsbuilding.uicore.craft.CraftQuantityReducer;
import com.rtsbuilding.rtsbuilding.uicore.craft.CraftQuantityState;
import com.rtsbuilding.rtsbuilding.uicore.craft.CraftQuantityTransition;

import java.util.EnumMap;
import java.util.EnumSet;

/**
 * 无头场景的输入回放自检。
 *
 * <p>这里只使用 preview 夹具所有者，验证路由契约，不注入假网络、假储存或任何
 * 生产业务成功结果。</p>
 */
public final class UiScenarioInputVerifier {
    private UiScenarioInputVerifier() {
    }

    public static void verify() {
        UiLayerStack<String> layers = new UiLayerStack<String>();
        layers.register("settings", "settings", new UiRect(20, 20, 180, 150), false, true);
        layers.register("dialog", "dialog", new UiRect(80, 60, 180, 120), true, true);

        if (!"dialog".equals(layers.topmostAt(100, 80))) {
            throw new IllegalStateException("topmost modal did not receive press");
        }
        if (layers.topmostAt(30, 30) != null) {
            throw new IllegalStateException("modal did not block lower window outside its bounds");
        }

        PointerCapture<String> capture = new PointerCapture<String>();
        capture.capture(0, "dialog");
        capture.capture(1, "settings");
        if (!"dialog".equals(capture.ownerOf(0)) || !"settings".equals(capture.ownerOf(1))) {
            throw new IllegalStateException("per-button capture ownership drifted");
        }
        if (!"dialog".equals(capture.release(0)) || capture.ownerOf(1) == null) {
            throw new IllegalStateException("release did not preserve independent button capture");
        }

        KeyboardFocus<String> focus = new KeyboardFocus<String>();
        focus.request("search_box");
        if (!"search_box".equals(focus.getOwner()) || !"settings".equals(capture.ownerOf(1))) {
            throw new IllegalStateException("keyboard focus became coupled to pointer capture");
        }

        UiEscapeStack<String> escape = new UiEscapeStack<String>();
        escape.push("settings");
        escape.push("dialog");
        if (!"dialog".equals(escape.popTop()) || !"settings".equals(escape.peek())) {
            throw new IllegalStateException("Escape did not close exactly one layer");
        }
        verifyBlueprintReplay();
        verifyTopBarReplay();
        verifySettingsReplay();
        verifyBottomBarReplay();
        verifyQuickBuildReplay();
        verifyUltimineReplay();
        verifyCullingReplay();
        verifyStorageLinksReplay();
        verifyWorkflowReplay();
        verifyGuideFunnelCraftReplay();
    }

    private static void verifyGuideFunnelCraftReplay() {
        GuideUiState guide = GuidePreviewFixtures.forScenario(
                UiPreviewScenario.firstBatch().get(50));
        if (GuideUiReducer.apply(guide,
                new GuideUiAction(GuideUiAction.Type.SELECT_TOPIC, 6)).page != 6) {
            throw new IllegalStateException("guide topic replay drifted");
        }
        UiMainlineAssets assets = new UiMainlineAssets();
        FunnelUiState funnel = FunnelPreviewFixtures.forScenario(
                UiPreviewScenario.firstBatch().get(54), assets, 8);
        if (funnel.totalEntries != 2000
                || FunnelUiReducer.apply(funnel, FunnelUiAction.toggle()).panelVisible
                || FunnelUiReducer.apply(funnel, FunnelUiAction.hover(1)).hoveredSourceIndex != 1) {
            throw new IllegalStateException("funnel bounded/toggle replay drifted");
        }
        CraftQuantityState craft = CraftQuantityPreviewFixtures.forScenario(
                UiPreviewScenario.firstBatch().get(55), 2);
        craft = CraftQuantityReducer.apply(craft, CraftQuantityAction.text("42")).state;
        if (craft.quantity != 164 || CraftQuantityReducer.apply(craft,
                CraftQuantityAction.simple(CraftQuantityAction.Type.CONFIRM)).command
                != CraftQuantityTransition.Command.CONFIRM) {
            throw new IllegalStateException("craft quantity input/confirm replay drifted");
        }
    }

    private static void verifyWorkflowReplay() {
        UiPreviewScenario scenario = UiPreviewScenario.firstBatch().get(49);
        UiMainlineAssets assets = new UiMainlineAssets();
        WorkflowUiState state = WorkflowPreviewFixtures.forScenario(
                scenario, assets.language(scenario.language()));
        if (state.rows.size() != 3 || !state.pendingJobs) {
            throw new IllegalStateException("workflow mixed fixture drifted");
        }
        if (WorkflowUiReducer.apply(state, WorkflowUiAction.of(
                WorkflowUiAction.Type.TOGGLE_PAUSED, 101)).command
                != WorkflowUiTransition.Command.TOGGLE_PAUSED
                || WorkflowUiReducer.apply(state, WorkflowUiAction.of(
                WorkflowUiAction.Type.RESUME_SUSPENDED, 103)).command
                != WorkflowUiTransition.Command.SCAN_RESUME_BLUEPRINT
                || WorkflowUiReducer.apply(state, WorkflowUiAction.of(
                WorkflowUiAction.Type.DELETE, 102)).state.rows.size() != 2) {
            throw new IllegalStateException("workflow action replay drifted");
        }
    }

    private static void verifyStorageLinksReplay(){
        UiMainlineAssets assets=new UiMainlineAssets();
        StorageUiState state=StoragePreviewFixtures.forScenario(
                UiPreviewScenario.firstBatch().get(44),assets);
        if(state.totalRows!=2000||state.visibleEntries.size()!=4||state.scroll!=1996)
            throw new IllegalStateException("storage links bounded fixture drifted");
        String key=state.visibleEntries.get(0).stableKey;
        if(StorageUiReducer.apply(state,StorageUiAction.key(
                StorageUiAction.Type.TOGGLE_EXTRACT,key)).command!=StorageUiTransition.Command.TOGGLE_EXTRACT
                ||StorageUiReducer.apply(state,StorageUiAction.key(
                StorageUiAction.Type.UNLINK,key)).state.totalRows!=1999)
            throw new IllegalStateException("storage links row action replay drifted");
    }

    private static void verifyCullingReplay() {
        CullingUiState draft = CullingPreviewFixtures.forScenario(
                UiPreviewScenario.firstBatch().get(39));
        if (CullingUiReducer.apply(draft, CullingUiAction.height(4)).state.previewHeight != 16
                || CullingUiReducer.apply(draft,
                CullingUiAction.simple(CullingUiAction.Type.CONFIRM_DRAFT)).command
                != CullingUiTransition.Command.CONFIRM_DRAFT) {
            throw new IllegalStateException("culling draft height/confirm replay drifted");
        }
        CullingUiState selected = CullingPreviewFixtures.forScenario(
                UiPreviewScenario.firstBatch().get(40));
        if (CullingUiReducer.apply(selected,
                CullingUiAction.handle(CullingUiDirection.EAST, 2)).command
                != CullingUiTransition.Command.RESIZE_HANDLE
                || CullingUiReducer.apply(selected,
                CullingUiAction.simple(CullingUiAction.Type.DELETE_SELECTED)).state.boxCount != 2) {
            throw new IllegalStateException("culling handle/delete replay drifted");
        }
    }

    private static void verifyUltimineReplay() {
        UiPreviewScenario chainScenario = UiPreviewScenario.firstBatch().get(35);
        UltimineUiState preview = UltiminePreviewFixtures.forScenario(chainScenario);
        UltimineUiTransition confirmed = UltimineUiReducer.apply(preview,
                UltimineUiAction.confirmPreview());
        if (confirmed.command != UltimineUiTransition.Command.START_CHAIN
                || confirmed.state.phase != UltimineUiPhase.CONFIRMED
                || confirmed.state.confirmedBlocks != 96) {
            throw new IllegalStateException("ultimine preview confirmation replay drifted");
        }
        UltimineUiState running = UltimineUiReducer.apply(confirmed.state,
                UltimineUiAction.progress(72, 160)).state;
        if (running.phase != UltimineUiPhase.RUNNING || running.remaining() != 88) {
            throw new IllegalStateException("ultimine server progress replay drifted");
        }
    }

    private static void verifyQuickBuildReplay() {
        UiPreviewScenario buildScenario = UiPreviewScenario.firstBatch().get(34);
        UiMainlineAssets assets = new UiMainlineAssets();
        QuickBuildUiState build = QuickBuildPreviewFixtures.forScenario(buildScenario,
                assets.language(buildScenario.language()));
        QuickBuildUiTransition destroy = QuickBuildUiReducer.apply(build,
                QuickBuildUiAction.mode(QuickBuildUiMode.DESTROY));
        if (destroy.command != QuickBuildUiTransition.Command.SELECT_MODE
                || destroy.state.mode != QuickBuildUiMode.DESTROY) {
            throw new IllegalStateException("quick build mode replay drifted");
        }
        UiPreviewScenario chainScenario = UiPreviewScenario.firstBatch().get(35);
        QuickBuildUiState chain = QuickBuildPreviewFixtures.forScenario(chainScenario,
                assets.language(chainScenario.language()));
        if (QuickBuildUiReducer.apply(chain, QuickBuildUiAction.limit(9999)).state.chainLimit != 512) {
            throw new IllegalStateException("quick build chain limit clamp drifted");
        }
        UiPreviewScenario lockedScenario = UiPreviewScenario.firstBatch().get(36);
        QuickBuildUiState locked = QuickBuildPreviewFixtures.forScenario(lockedScenario,
                assets.language(lockedScenario.language()));
        if (QuickBuildUiReducer.apply(locked,
                QuickBuildUiAction.mode(QuickBuildUiMode.DESTROY)).command
                != QuickBuildUiTransition.Command.NONE) {
            throw new IllegalStateException("quick build destroy lock replay drifted");
        }
    }

    private static void verifyBottomBarReplay() {
        UiMainlineAssets assets = new UiMainlineAssets();
        UiPreviewScenario storageScenario = UiPreviewScenario.firstBatch().get(8);
        BottomBarUiState state = BottomBarPreviewFixtures.forScenario(storageScenario,
                assets, assets.language(storageScenario.language()));
        if (state.storageEntries.size() != 2000 || state.activeTab != BottomBarUiTab.STORAGE) {
            throw new IllegalStateException("bottom storage fixture lost the formal 2000-entry state");
        }
        BottomBarUiTransition search = BottomBarUiReducer.apply(state,
                BottomBarUiAction.value(BottomBarUiAction.Type.SET_SEARCH, "range"));
        BottomBarUiTransition page = BottomBarUiReducer.apply(search.state,
                BottomBarUiAction.simple(BottomBarUiAction.Type.NEXT_PAGE));
        BottomBarUiTransition category = BottomBarUiReducer.apply(page.state,
                BottomBarUiAction.index(BottomBarUiAction.Type.TOGGLE_CATEGORY, 1));
        if (!"range".equals(search.state.search)
                || page.state.page != 1
                || category.state.categories.get(1).expanded
                || search.command != BottomBarUiTransition.Command.EXECUTE) {
            throw new IllegalStateException("bottom search/page/category replay drifted");
        }

        UiPreviewScenario creativeScenario = UiPreviewScenario.firstBatch().get(14);
        BottomBarUiState creative = BottomBarPreviewFixtures.forScenario(creativeScenario,
                assets, assets.language(creativeScenario.language()));
        if (creative.activeTab != BottomBarUiTab.CREATIVE
                || creative.creativeEntries.isEmpty()) {
            throw new IllegalStateException("creative catalog state lost its formal entries");
        }
    }

    private static void verifySettingsReplay() {
        EnumMap<SettingsId, SettingsUiValue> values = new EnumMap<SettingsId, SettingsUiValue>(SettingsId.class);
        values.put(SettingsId.AUTO_STORE, SettingsUiValue.toggle(true));
        values.put(SettingsId.STORAGE_AUTO_REFRESH, SettingsUiValue.toggle(true));
        SettingsUiState state = SettingsUiCatalog.create(values,
                EnumSet.noneOf(SettingsSectionId.class),
                EnumSet.of(SettingsId.STORAGE_AUTO_REFRESH),
                EnumSet.noneOf(SettingsId.class), 0);
        SettingsUiTransition section = SettingsUiReducer.apply(state,
                SettingsUiAction.section(SettingsSectionId.HELPERS));
        SettingsUiTransition hint = SettingsUiReducer.apply(section.state,
                SettingsUiAction.setting(SettingsUiAction.Type.TOGGLE_HINT,
                        SettingsId.STORAGE_AUTO_REFRESH));
        SettingsUiTransition toggle = SettingsUiReducer.apply(hint.state,
                SettingsUiAction.setting(SettingsUiAction.Type.TOGGLE_VALUE,
                        SettingsId.AUTO_STORE));
        if (!toggle.state.section(SettingsSectionId.HELPERS).expanded
                || !toggle.state.row(SettingsId.STORAGE_AUTO_REFRESH).hintExpanded
                || toggle.state.row(SettingsId.AUTO_STORE).active
                || toggle.command != SettingsUiTransition.Command.TOGGLE_VALUE) {
            throw new IllegalStateException("settings section/hint/toggle replay drifted");
        }
    }

    private static void verifyTopBarReplay() {
        TopBarUiState state = TopBarPreviewFixtures.forScenario(
                UiPreviewScenario.firstBatch().get(15));
        if (state.mode != TopBarUiState.Mode.FUNNEL) {
            throw new IllegalStateException("top bar fixture lost contextual funnel mode");
        }
        TopBarUiTransition mode = TopBarUiReducer.apply(state,
                TopBarUiAction.click(TopBarUiButtonId.ROTATE));
        if (mode.command != TopBarUiTransition.Command.ROTATE
                || mode.state.mode != TopBarUiState.Mode.ROTATE) {
            throw new IllegalStateException("top bar mode click replay drifted");
        }
        TopBarUiTransition chunk = TopBarUiReducer.apply(mode.state,
                TopBarUiAction.click(TopBarUiButtonId.CHUNK_VIEW));
        if (chunk.command != TopBarUiTransition.Command.CHUNK_VIEW
                || !chunk.state.button(TopBarUiButtonId.CHUNK_VIEW).active) {
            throw new IllegalStateException("top bar toggle replay drifted");
        }
    }

    private static void verifyBlueprintReplay() {
        BlueprintUiState placement = BlueprintPreviewFixtures.forScenario(
                UiPreviewScenario.firstBatch().get(12));
        BlueprintUiState nudged = BlueprintUiReducer.apply(placement,
                BlueprintUiAction.vector(BlueprintUiAction.Type.NUDGE_ANCHOR, 1, -2, 3)).state;
        if (!nudged.isPinned() || nudged.anchor.x != placement.anchor.x + 1
                || BlueprintUiReducer.apply(nudged,
                        BlueprintUiAction.simple(BlueprintUiAction.Type.BUILD)).command
                        != BlueprintUiTransition.Command.BUILD) {
            throw new IllegalStateException("blueprint placement replay drifted");
        }

        BlueprintUiState capture = BlueprintPreviewFixtures.forScenario(
                UiPreviewScenario.firstBatch().get(18));
        capture = BlueprintUiReducer.apply(capture,
                BlueprintUiAction.simple(BlueprintUiAction.Type.ACCEPT_CAPTURE_POINT)).state;
        if (capture.mode != BlueprintUiState.Mode.CAPTURE_READY) {
            throw new IllegalStateException("blueprint second-point replay did not become ready");
        }
        capture = BlueprintUiReducer.apply(capture,
                BlueprintUiAction.simple(BlueprintUiAction.Type.SAVE_CAPTURE)).state;
        capture = BlueprintUiReducer.apply(capture,
                BlueprintUiAction.text(BlueprintUiAction.Type.SET_NAME_DRAFT, "harbour")).state;
        capture = BlueprintUiReducer.apply(capture,
                BlueprintUiAction.simple(BlueprintUiAction.Type.CONFIRM_NAME)).state;
        if (capture.mode != BlueprintUiState.Mode.CAPTURE_SAVING || capture.nameWindowOpen) {
            throw new IllegalStateException("blueprint capture naming replay did not start saving");
        }

        BlueprintUiState rename = BlueprintPreviewFixtures.forScenario(
                UiPreviewScenario.firstBatch().get(23));
        rename = BlueprintUiReducer.apply(rename,
                BlueprintUiAction.text(BlueprintUiAction.Type.APPEND_NAME_CHAR, "N")).state;
        if (!"N".equals(rename.nameDraft)) {
            throw new IllegalStateException("rename selected-text replacement replay drifted");
        }

        BlueprintLibraryUiState library = BlueprintLibraryPreviewFixtures.forScenario(
                UiPreviewScenario.firstBatch().get(24));
        library = BlueprintLibraryUiReducer.apply(library,
                BlueprintLibraryUiAction.text(BlueprintLibraryUiAction.Type.SET_QUERY, "wind")).state;
        if (library.filteredEntries().size() != 1) {
            throw new IllegalStateException("blueprint library search replay drifted");
        }
        String file = library.filteredEntries().get(0).fileName;
        BlueprintLibraryUiTransition selection = BlueprintLibraryUiReducer.apply(library,
                BlueprintLibraryUiAction.text(BlueprintLibraryUiAction.Type.SELECT_ENTRY, file));
        if (selection.command != BlueprintLibraryUiTransition.Command.SELECT_ENTRY
                || !file.equals(selection.state.selectedFileName)) {
            throw new IllegalStateException("blueprint library selection replay drifted");
        }
    }
}
