package com.rtsbuilding.rtsbuilding.client.screen.panel;


import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.popup.RtsCraftFeedbackPopup;
import com.rtsbuilding.rtsbuilding.client.record.CraftableEntry;
import com.rtsbuilding.rtsbuilding.client.screen.blueprint.BlueprintPanel;
import com.rtsbuilding.rtsbuilding.client.screen.layout.BottomPanelLayoutTypes;
import com.rtsbuilding.rtsbuilding.client.screen.layout.CategoryTypes;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.util.RtsCraftablesUiHelper;
import com.rtsbuilding.rtsbuilding.client.util.RtsCreativeItemCatalog;
import com.rtsbuilding.rtsbuilding.client.input.RtsModifierKeys;
import com.rtsbuilding.rtsbuilding.client.input.RtsWidgetCompat;
import com.rtsbuilding.rtsbuilding.uikit.layout.BottomPanelBrowseLayout;
import com.rtsbuilding.rtsbuilding.uikit.layout.BottomPanelBlueprintLayout;
import com.rtsbuilding.rtsbuilding.uikit.layout.BottomPanelCategoryLayout;
import com.rtsbuilding.rtsbuilding.uikit.layout.BottomPanelCraftDockLayout;
import com.rtsbuilding.rtsbuilding.uikit.layout.BottomPanelCraftLayout;
import com.rtsbuilding.rtsbuilding.uikit.layout.BottomPanelGridLayout;
import com.rtsbuilding.rtsbuilding.uikit.layout.BottomPanelHeaderLayout;
import com.rtsbuilding.rtsbuilding.uikit.layout.BottomPanelSortLayout;
import com.rtsbuilding.rtsbuilding.uikit.layout.BottomPanelToolLayout;
import com.rtsbuilding.rtsbuilding.uikit.layout.RtsMainlineLayout;
import com.rtsbuilding.rtsbuilding.uicore.bottom.BottomBarUiAction;
import com.rtsbuilding.rtsbuilding.uicore.bottom.BottomBarUiCategory;
import com.rtsbuilding.rtsbuilding.uicore.bottom.BottomBarUiReducer;
import com.rtsbuilding.rtsbuilding.uicore.bottom.BottomBarUiState;
import com.rtsbuilding.rtsbuilding.uicore.bottom.BottomBarUiTab;
import com.rtsbuilding.rtsbuilding.uicore.bottom.BottomBarUiTransition;
import com.rtsbuilding.rtsbuilding.uikit.animation.SystemUiClock;
import com.rtsbuilding.rtsbuilding.uikit.animation.UiControlAnimationRegistry;
import com.rtsbuilding.rtsbuilding.uikit.animation.UiEasing;
import com.rtsbuilding.rtsbuilding.uikit.animation.UiSelectionAnimationSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.*;

import static com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreenConstants.*;

/**
 * Bottom panel 鈥?centralised UI for storage grids, categories, crafting, fluids, and blueprints.
 * <p>
 * Lifecycle is orchestrated by {@link BuilderScreen}.
 */
public final class BottomPanel {

    // 鈹€鈹€ State 鈹€鈹€
    BuilderScreen screen;
    ClientRtsController controller;

    public BottomPanelLayoutTypes.BottomPanelTab bottomPanelTab = BottomPanelLayoutTypes.BottomPanelTab.STORAGE;
    public int pinPage = 0;
    public int categoryScroll = 0;
    public int craftScroll = 0;
    public final Set<String> expandedCategoryMods = new HashSet<>();

    public int hoveredEntry = -1;
    public int hoveredRecentEntry = -1;
    public int hoveredFluidEntry = -1;
    public int hoveredCreativeEntry = -1;
    public int hoveredCraftableEntry = -1;
    public int hoveredToolSlot = -1;
    public boolean hoveredEmptyHandSlot = false;
    public int hoveredPinIndex = -1;
    public int hoveredGuiBindingSlot = -1;
    public boolean hoveredPinPageButton = false;

    public String craftSearchDraft;
    public int lastCraftablesStorageRevision = -1;
    String creativeCategory = "all";
    String creativeSearch = "";
    int creativePage = 0;
    private final UiSelectionAnimationSet<BottomBarUiTab> tabAnimations =
            new UiSelectionAnimationSet<>(SystemUiClock.INSTANCE,
                    Arrays.asList(BottomBarUiTab.values()),
                    110L, UiEasing.EASE_OUT_CUBIC);
    private final UiControlAnimationRegistry<String> headerControlAnimations =
            new UiControlAnimationRegistry<>(SystemUiClock.INSTANCE, 8);
    // 输入子 owner 与绘制 owner 共享同一组 Kit 布局对象，便于契约测试和后续路由演进。
    final BottomPanelToolInput toolInput = new BottomPanelToolInput(this);
    final BottomPanelCraftInput craftInput = new BottomPanelCraftInput(this);
    final BottomPanelGridInput gridInput = new BottomPanelGridInput(this);
    private final BottomPanelInputRouter inputRouter =
            new BottomPanelInputRouter(this);

    public void init(BuilderScreen screen, ClientRtsController controller) {
        this.screen = screen;
        this.controller = controller;
    }

    // 鈹€鈹€ Rendering 鈹€鈹€

    public void render(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        BottomPanelLayoutTypes.BottomPanelLayout layout = resolveBottomPanelLayout();
        String selectedStatus = selectedPlacementStatusText();
        BottomPanelHeaderLayout header =
                resolveHeaderLayout(layout, selectedStatus);
        BottomBarUiState core = BottomBarUiAdapter.snapshot(
                this, layout, selectedStatus, header.pluginVisible);
        BottomBarUiTab activeTab = core.activeTab;
        BottomPanelHeaderRenderer.render(
                g, screen.font(), header, core, tabAnimations,
                headerControlAnimations,
                Config.isUiAnimationsEnabled(),
                Component.translatable(
                        "screen.rtsbuilding.creative.tab").getString(),
                Component.translatable(
                        "screen.rtsbuilding.storage.tab").getString(),
                Component.translatable(
                        "screen.rtsbuilding.blueprints.tab").getString(),
                Component.translatable(
                        "screen.rtsbuilding.plugins.short").getString(),
                mouseX, mouseY);

        if (activeTab == BottomBarUiTab.BLUEPRINTS) {
            BottomPanelHeaderLayout.Area content =
                    resolveBlueprintLayout(layout).content;
            BlueprintPanel.render(
                    g, screen.font(), this.controller,
                    content.x, content.y, content.width, content.height,
                    mouseX, mouseY);
            return;
        }

        int sortX = layout.sortX();
        int sortY = layout.sortY();
        BottomPanelSortLayout sortLayout = BottomPanelSortLayout.resolve(sortX, sortY);
        BottomPanelSortRenderer.render(
                g, screen.font(), sortLayout,
                core.sortLabel, core.sortAscending, mouseX, mouseY);
        BottomPanelCraftDockLayout craftDock = resolveCraftDockLayout(layout);
        this.hoveredGuiBindingSlot = BottomPanelCraftDockRenderer.render(
                g, screen.font(), core.guiBindings, this.controller,
                craftDock, mouseX, mouseY);

        BottomPanelCategoryLayout categoryLayout = resolveCategoryLayout(
                layout, core.categories.size(), core.categoryScroll);
        this.categoryScroll = categoryLayout.scroll;
        BottomPanelCategoryRenderer.render(
                g, screen.font(),
                Component.translatable("screen.rtsbuilding.storage.category"),
                core.categories, categoryLayout, mouseX, mouseY);

        int storageX = layout.storageX();
        int storageY = layout.storageY();
        int storageW = layout.storageW();
        int craftPanelX = layout.craftPanelX();
        int mainStorageW = layout.mainStorageW();
        BottomPanelBrowseLayout browseLayout = resolveBrowseLayout(layout);

        if (screen.getSearchBox() != null) {
            if (!screen.getSearchBox().isFocused()) {
                syncSearchBoxForActiveTab();
            }
            var sb = screen.getSearchBox();
            sb.setX(browseLayout.searchField.x);
            sb.setY(browseLayout.searchField.y);
            sb.setWidth(browseLayout.searchField.width);
            sb.setHeight(browseLayout.searchField.height);
            RtsWidgetCompat.render(sb, g, mouseX, mouseY, partialTick);
        }

        BottomPanelBrowseRenderer.renderControls(
                g, screen.font(), browseLayout,
                core.searchFocused, !core.search.isEmpty(),
                core.page, core.pageCount, mouseX, mouseY);

        renderToolArea(g, core, mouseX, mouseY, storageX, layout.toolY(), mainStorageW);

        int gridY = layout.gridY();
        int gridH = layout.gridH();
        int craftPanelY = layout.craftPanelY();
        int craftPanelH = layout.craftPanelH();
        if (activeTab == BottomBarUiTab.CREATIVE) {
            BottomPanelGridLayout.Layout grids = BottomPanelGridLayout.creative(
                    storageX, gridY, mainStorageW, gridH, SLOT, STORAGE_RECENT_GAP);
            BottomPanelGridLayout.GridView creativeView = gridView(
                    grids.main, core.creativeEntries.size(), 0);
            BottomPanelGridLayout.GridView recentView = gridView(
                    grids.recent, core.recentEntries.size(), 0);
            this.hoveredCreativeEntry = BottomPanelGridRenderer.renderCreative(
                    g, screen.font(), core.creativeEntries, creativeEntriesForCurrentFilter(),
                    creativeView, mouseX, mouseY);
            this.hoveredRecentEntry = BottomPanelGridRenderer.renderRecent(
                    g, screen.font(), core.recentEntries, this.controller.getRecentEntries(),
                    recentView, mouseX, mouseY);
            return;
        }
        int fluidW = getFluidStripWidth(mainStorageW);
        BottomPanelGridLayout.Layout grids = BottomPanelGridLayout.storage(
                storageX, gridY, mainStorageW, gridH, SLOT, STORAGE_RECENT_GAP, fluidW, 4);
        if (!grids.fluid.isEmpty()) {
            this.hoveredFluidEntry = BottomPanelGridRenderer.renderFluid(
                    g, screen.font(), core.fluidEntries, this.controller.getFluidEntries(),
                    gridView(grids.fluid, core.fluidEntries.size(), 0), mouseX, mouseY);
        }
        BottomPanelGridLayout.GridView storageView =
                gridView(grids.main, core.storageEntries.size(), 0);
        this.controller.updateStoragePageSize(storageView.capacity);
        this.hoveredEntry = BottomPanelGridRenderer.renderStorage(
                g, screen.font(), core.storageEntries, this.controller.getStorageEntries(),
                storageView, mouseX, mouseY, this.controller.isStorageLinked());
        this.hoveredRecentEntry = BottomPanelGridRenderer.renderRecent(
                g, screen.font(), core.recentEntries, this.controller.getRecentEntries(),
                gridView(grids.recent, core.recentEntries.size(), 0), mouseX, mouseY);
        renderCraftablesPanel(g, core, mouseX, mouseY, craftPanelX, craftPanelY, CRAFT_PANEL_W, craftPanelH, partialTick);
    }

    public void renderCraftFeedback(GuiGraphicsExtractor g) {
        RtsCraftFeedbackPopup.render(g, screen.font(), screen.width,
                RtsMainlineLayout.TOP_H + 6, this.controller);
    }

    // 鈹€鈹€ Tab rendering 鈹€鈹€

    String selectedPlacementStatusText() {
        if (this.controller.hasSelectedFluid()) {
            return screen.text("screen.rtsbuilding.status.selected_fluid", this.controller.getSelectedFluidLabel());
        }
        if (!this.controller.getSelectedItemLabel().isEmpty()) {
            return screen.text("screen.rtsbuilding.status.selected_item", screen.selectedItemStatusLabel());
        }
        if (this.controller.isEmptyHandSelected()) {
            return screen.text("screen.rtsbuilding.status.selected_empty_hand");
        }
        return screen.text("screen.rtsbuilding.status.selected_none");
    }

    BottomPanelLayoutTypes.BottomPanelTab activeBottomPanelTab() {
        if (this.bottomPanelTab == BottomPanelLayoutTypes.BottomPanelTab.CREATIVE && !isCreativePlayer()) {
            return BottomPanelLayoutTypes.BottomPanelTab.STORAGE;
        }
        if (this.bottomPanelTab == BottomPanelLayoutTypes.BottomPanelTab.BLUEPRINTS && !hasBlueprintAccess()) {
            return BottomPanelLayoutTypes.BottomPanelTab.STORAGE;
        }
        return this.bottomPanelTab;
    }

    /** 杩斿洖鐜╁褰撳墠鏄惁鐪熺殑鐪嬪緱鍒版湇鍔＄鍌ㄥ瓨椤靛唴瀹广€?*/
    public boolean isStorageBrowserVisible() {
        return activeBottomPanelTab() == BottomPanelLayoutTypes.BottomPanelTab.STORAGE;
    }

    boolean hasBlueprintAccess() {
        return Config.areBlueprintsEnabled();
    }

    boolean isCreativePlayer() {
        Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.player != null && mc.player.isCreative();
    }

    // 鈹€鈹€ Toolbar 鈹€鈹€ hotbar / pinned slots 鈹€鈹€

    private void renderToolArea(GuiGraphicsExtractor g, BottomBarUiState core,
            int mouseX, int mouseY, int storageX, int rowY, int storageW) {
        if (Minecraft.getInstance() == null || Minecraft.getInstance().player == null) {
            return;
        }

        BottomPanelToolLayout tools = BottomPanelToolLayout.standard(
                storageX, rowY, storageW,
                this.controller.getQuickSlotCount(), this.pinPage);
        this.pinPage = tools.pinPage();
        BottomPanelToolRenderer.HoverResult hover = BottomPanelToolRenderer.render(
                g, screen.font(), core,
                Minecraft.getInstance().player.getInventory(),
                this.controller, tools, mouseX, mouseY);
        this.hoveredToolSlot = hover.hotbarIndex;
        this.hoveredEmptyHandSlot = hover.emptyHand;
        this.hoveredPinIndex = hover.pinIndex;
        this.hoveredPinPageButton = hover.pinPager;
    }

    // 鈹€鈹€ Category panel 鈹€鈹€

    static BottomPanelGridLayout.GridView gridView(
            BottomPanelGridLayout.GridArea area, int entryCount, int page) {
        return BottomPanelGridLayout.resolve(area, SLOT, SLOT - 2, entryCount, page);
    }

    // 鈹€鈹€ Crafting panel 鈹€鈹€

    private void renderCraftablesPanel(GuiGraphicsExtractor g, BottomBarUiState core,
            int mouseX, int mouseY, int x, int y, int width, int height, float partialTick) {
        syncCraftSearchValueFromController();
        List<CraftableEntry> sourceEntries = this.controller.getCraftableEntries();
        BottomPanelCraftLayout craftLayout = BottomPanelCraftLayout.resolve(
                x, y, width, height, core.craftableEntries.size(), this.craftScroll);
        this.craftScroll = craftLayout.scroll;
        this.hoveredCraftableEntry = BottomPanelCraftRenderer.render(
                g, screen.font(), screen.getCraftSearchBox(), core, sourceEntries,
                craftLayout, mouseX, mouseY, partialTick);
    }

    private void syncCraftSearchValueFromController() {
        var csb = screen.getCraftSearchBox();
        if (csb == null || csb.isFocused()) {
            return;
        }
        String expected = this.craftSearchDraft == null ? "" : this.craftSearchDraft;
        if (!expected.equals(csb.getValue())) {
            csb.setValue(expected);
        }
    }

    private static String normalizeCraftSearchDraft(String value) {
        return RtsCraftablesUiHelper.normalizeSearchDraft(value);
    }

    public void openCraftQuantityDialog(CraftableEntry entry) {
        screen.blurSearchFocus();
        screen.openCraftQuantityWindow(entry);
    }

    public void submitCraftQuantityDialogIfReady() {
        screen.submitCraftQuantityWindowIfReady();
    }

    // 鈹€鈹€ Craft dock 鈹€鈹€

    // 鈹€鈹€ Click handling 鈹€鈹€

    public boolean handleClick(double mouseX, double mouseY) {
        return inputRouter.mousePressed(mouseX, mouseY, 0);
    }

    public boolean handleRightClick(double mouseX, double mouseY) {
        return inputRouter.mousePressed(mouseX, mouseY, 1);
    }

    public boolean handleMouseScrolled(double mouseX, double mouseY, double scrollY) {
        return inputRouter.mouseScrolled(mouseX, mouseY, scrollY);
    }

    /**
     * 缁熶竴璁╃敓浜ц緭鍏ュ厛缁忚繃 Core reducer锛屽啀鐢卞钩鍙伴€傞厤鍣ㄦ墽琛岀綉缁溿€佽儗鍖呮垨绐楀彛鍓綔鐢ㄣ€?     * BottomPanel 浠嶆槸缂栨帓 owner锛屼絾涓嶅啀鍚勮嚜鍙戞槑鍒嗛〉/鎼滅储/鍒嗙被鐘舵€佽浆绉汇€?     */
    BottomBarUiTransition dispatchCore(BottomBarUiAction action) {
        BottomBarUiState state = snapshotCore(resolveBottomPanelLayout());
        BottomBarUiTransition transition = BottomBarUiReducer.apply(state, action);
        BottomBarUiAdapter.apply(this, transition);
        return transition;
    }

    /**
     * 澶撮儴缁樺埗涓庤緭鍏ラ兘浠庡悓涓€浠?Kit 鍑犱綍蹇収璇诲彇椤电鍜屽彸渚у叆鍙ｏ紝閬垮厤绐勫睆涓嬪悇鑷帹瀵煎彲瑙佹€с€?     */
    BottomPanelHeaderLayout resolveHeaderLayout(
            BottomPanelLayoutTypes.BottomPanelLayout layout,
            String selectedStatus) {
        return BottomPanelHeaderLayout.resolve(
                layout.panelX(), layout.panelY(),
                layout.panelW(), layout.panelH(),
                isCreativePlayer(), hasBlueprintAccess(),
                screen.font().width(selectedStatus), true);
    }

    BottomBarUiState snapshotCore(
            BottomPanelLayoutTypes.BottomPanelLayout layout) {
        String selectedStatus = selectedPlacementStatusText();
        BottomPanelHeaderLayout header =
                resolveHeaderLayout(layout, selectedStatus);
        return BottomBarUiAdapter.snapshot(
                this, layout, selectedStatus, header.pluginVisible);
    }

    /** 鍒嗙被婊氬姩鍏变韩 Core 鐨勮竟鐣岄挸鍒讹紝婊氳疆涓庝笂涓嬬澶村洜姝ゅ畬鍏ㄥ悓涔夈€?*/
    // 鈹€鈹€ Internal click handling 鈹€鈹€

    public void handleStorageSearchChanged(String value) {
        dispatchCore(BottomBarUiAction.value(BottomBarUiAction.Type.SET_SEARCH,
                value == null ? "" : value));
    }

    /** 浠呬緵鐢熶骇閫傞厤鍣ㄦ墽琛?Core 宸茶瀹氱殑鎼滅储鍓綔鐢紝閬垮厤鐩戝惉鍣ㄩ€掑綊銆?*/
    void applyStorageSearchValue(String value) {
        String next = value == null ? "" : value;
        if (activeBottomPanelTab() == BottomPanelLayoutTypes.BottomPanelTab.CREATIVE) {
            this.creativeSearch = next;
            this.creativePage = 0;
            return;
        }
        this.controller.setStorageSearch(next);
    }

    void syncSearchBoxForActiveTab() {
        var sb = screen.getSearchBox();
        if (sb == null) {
            return;
        }
        String expected = activeBottomPanelTab() == BottomPanelLayoutTypes.BottomPanelTab.CREATIVE
                ? this.creativeSearch
                : this.controller.getStorageSearch();
        if (!expected.equals(sb.getValue())) {
            sb.setValue(expected);
        }
    }

    public void applyCraftSearchDraft() {
        var csb = screen.getCraftSearchBox();
        String next = normalizeCraftSearchDraft(csb == null ? this.craftSearchDraft : csb.getValue());
        this.craftSearchDraft = next;
        if (csb != null && !next.equals(csb.getValue())) {
            csb.setValue(next);
        }
        dispatchCore(BottomBarUiAction.value(BottomBarUiAction.Type.SET_CRAFT_SEARCH, next));
        dispatchCore(BottomBarUiAction.simple(BottomBarUiAction.Type.APPLY_CRAFT_SEARCH));
    }

    // 鈹€鈹€ Layout & resolution 鈹€鈹€

    public BottomPanelLayoutTypes.BottomPanelLayout resolveBottomPanelLayout() {
        RtsMainlineLayout.BottomPanel layout = RtsMainlineLayout.bottomPanel(
                screen.width, screen.height, this.panelHeight);
        this.panelHeight = layout.panelH;

        return new BottomPanelLayoutTypes.BottomPanelLayout(
                layout.panelX, layout.panelY, layout.panelW, layout.panelH,
                layout.sortX, layout.sortY, layout.craftDockX, layout.craftDockY,
                layout.categoryX, layout.categoryY, layout.categoryH,
                layout.storageX, layout.storageY, layout.storageW,
                layout.craftPanelX, layout.mainStorageW, layout.searchW, layout.pagerX,
                layout.toolY, layout.gridY, layout.gridH, layout.storageRows,
                layout.craftPanelY, layout.craftPanelH);
    }

    int panelHeight = DEFAULT_BOTTOM_H;

    public int getBottomY() {
        return resolveBottomPanelLayout().panelY();
    }

    public int getFloatingPanelAvailableHeight(int panelY) {
        return Math.max(0, getBottomY() - panelY - 6);
    }

    public boolean isInsideBottomPanel(double mouseX, double mouseY) {
        return resolveBottomPanelLayout().contains(mouseX, mouseY);
    }

    public boolean isWorldArea(double mouseX, double mouseY) {
        return mouseY > TOP_H && !isInsideBottomPanel(mouseX, mouseY);
    }

    void adjustBottomPanelSize(int direction) {
        int dynamicMaxH = Math.max(MIN_BOTTOM_H, Math.min(MAX_BOTTOM_H, screen.height - TOP_H - 16));
        int minH = Math.min(dynamicMaxH, Math.max(MIN_BOTTOM_H, minimumBottomHeightForGridRows(MIN_STORAGE_GRID_ROWS)));
        this.panelHeight = Mth.clamp(this.panelHeight + (direction * SLOT), minH, dynamicMaxH);
    }

    private int minimumBottomHeightForGridRows(int rows) {
        int gridTopOffset = BOTTOM_PANEL_HEADER_H + 4 + 17 + TOOL_AREA_H + 4;
        return gridTopOffset + BOTTOM_PANEL_PADDING + (Math.max(1, rows) * SLOT);
    }

    BottomPanelCraftDockLayout resolveCraftDockLayout(
            BottomPanelLayoutTypes.BottomPanelLayout layout) {
        return BottomPanelCraftDockLayout.resolve(
                layout.craftDockX(), layout.craftDockY(),
                this.controller.getGuiBindingCount());
    }

    static BottomPanelCategoryLayout resolveCategoryLayout(
            BottomPanelLayoutTypes.BottomPanelLayout layout,
            int totalRows,
            int scroll) {
        return BottomPanelCategoryLayout.resolve(
                layout.categoryX(), layout.categoryY(),
                BottomPanelCategoryLayout.WIDTH, layout.categoryH(),
                totalRows, scroll);
    }

    static BottomPanelBrowseLayout resolveBrowseLayout(
            BottomPanelLayoutTypes.BottomPanelLayout layout) {
        return BottomPanelBrowseLayout.resolve(
                layout.storageX(), layout.storageY(),
                layout.searchW(), layout.pagerX());
    }

    // 这些窄适配入口让生产输入与同一份 Kit 几何保持可追踪的调用边界。
    BottomBarUiTab headerTabAt(BottomPanelHeaderLayout header, double mouseX, double mouseY) {
        return header.tabAt(mouseX, mouseY);
    }

    BottomPanelHeaderLayout.Control headerControlAt(BottomPanelHeaderLayout header,
                                                      double mouseX, double mouseY) {
        return header.controlAt(mouseX, mouseY);
    }

    boolean routeToolInput(double mouseX, double mouseY,
                           BottomPanelLayoutTypes.BottomPanelLayout layout, int button) {
        return toolInput.mousePressed(mouseX, mouseY, button, layout);
    }

    boolean routeToolLeft(double mouseX, double mouseY,
                          BottomPanelLayoutTypes.BottomPanelLayout layout) {
        return toolInput.mousePressed(mouseX, mouseY, 0, layout);
    }

    boolean routeToolRight(double mouseX, double mouseY,
                           BottomPanelLayoutTypes.BottomPanelLayout layout) {
        return toolInput.mousePressed(mouseX, mouseY, 1, layout);
    }

    boolean routeCraftLeft(double mouseX, double mouseY,
                           BottomPanelLayoutTypes.BottomPanelLayout layout) {
        return craftInput.leftPressed(mouseX, mouseY, layout);
    }

    boolean routeCraftRight(double mouseX, double mouseY,
                            BottomPanelLayoutTypes.BottomPanelLayout layout) {
        return craftInput.rightPressed(mouseX, mouseY, layout);
    }

    boolean routeCraftScroll(double mouseX, double mouseY, double scrollY,
                             BottomPanelLayoutTypes.BottomPanelLayout layout) {
        return craftInput.mouseScrolled(mouseX, mouseY, scrollY, layout);
    }

    boolean routeGridLeft(double mouseX, double mouseY,
                          BottomPanelLayoutTypes.BottomPanelTab activeTab,
                          BottomPanelLayoutTypes.BottomPanelLayout layout) {
        return gridInput.leftPressed(mouseX, mouseY, activeTab, layout);
    }

    void routeGridRightStorage(double mouseX, double mouseY,
                               BottomPanelLayoutTypes.BottomPanelLayout layout) {
        gridInput.rightPressedStorage(mouseX, mouseY, layout);
    }

    boolean handleCraftDockPress(double mouseX, double mouseY, int button,
                                 BottomPanelCraftDockLayout dock) {
        if (dock.craftButton.contains(mouseX, mouseY)) {
            dispatchCore(BottomBarUiAction.simple(
                    BottomBarUiAction.Type.OPEN_CRAFT_TERMINAL));
            return true;
        }
        int slot = dock.slotIndexAt(mouseX, mouseY);
        if (slot < 0) {
            return false;
        }
        if (button == 0) {
            dispatchCore(BottomBarUiAction.index(
                    BottomBarUiAction.Type.SELECT_GUI_BINDING, slot));
        } else if (button == 1 && RtsModifierKeys.isShiftDown()) {
            dispatchCore(BottomBarUiAction.index(
                    BottomBarUiAction.Type.CLEAR_GUI_BINDING, slot));
        } else if (button == 1) {
            dispatchCore(BottomBarUiAction.index(
                    BottomBarUiAction.Type.TOGGLE_GUI_BINDING_PENDING, slot));
        }
        return true;
    }

    boolean handleCategoryPress(double mouseX, double mouseY,
                                BottomPanelLayoutTypes.BottomPanelLayout layout) {
        BottomBarUiState state = snapshotCore(layout);
        BottomPanelCategoryLayout categoryLayout =
                resolveCategoryLayout(layout, state.categories.size(), state.categoryScroll);
        if (categoryLayout.scrollUp.contains(mouseX, mouseY)) {
            scrollCategories(-1, layout);
            return true;
        }
        if (categoryLayout.scrollDown.contains(mouseX, mouseY)) {
            scrollCategories(1, layout);
            return true;
        }
        int categoryIndex = categoryLayout.categoryIndexAt(mouseX, mouseY);
        if (categoryIndex < 0) {
            return false;
        }
        BottomBarUiCategory category = state.categories.get(categoryIndex);
        boolean toggle = category.expandable
                && categoryLayout.toggleArea(categoryIndex).contains(mouseX, mouseY);
        dispatchCore(BottomBarUiAction.index(toggle
                ? BottomBarUiAction.Type.TOGGLE_CATEGORY
                : BottomBarUiAction.Type.SELECT_CATEGORY, categoryIndex));
        return true;
    }

    boolean handleSearchClear(double mouseX, double mouseY,
                              BottomPanelBrowseLayout browseLayout) {
        var searchBox = screen.getSearchBox();
        if (searchBox == null
                || !browseLayout.clearSearch.contains(mouseX, mouseY)) {
            return false;
        }
        searchBox.setValue("");
        dispatchCore(BottomBarUiAction.simple(BottomBarUiAction.Type.CLEAR_SEARCH));
        screen.blurSearchFocus();
        return true;
    }

    boolean handleBrowsePage(double mouseX, double mouseY,
                             BottomPanelBrowseLayout browseLayout) {
        if (browseLayout.previousPage.contains(mouseX, mouseY)) {
            dispatchCore(BottomBarUiAction.simple(
                    BottomBarUiAction.Type.PREVIOUS_PAGE));
            return true;
        }
        if (browseLayout.nextPage.contains(mouseX, mouseY)) {
            dispatchCore(BottomBarUiAction.simple(
                    BottomBarUiAction.Type.NEXT_PAGE));
            return true;
        }
        return false;
    }

    boolean handleBlueprintScroll(double mouseX, double mouseY, double scrollY,
                                  BottomPanelLayoutTypes.BottomPanelLayout layout) {
        BottomPanelHeaderLayout.Area content = resolveBlueprintLayout(layout).content;
        BlueprintPanel.mouseScrolled(mouseX, mouseY, scrollY,
                content.x, content.y, content.width, content.height, controller);
        return true;
    }

    void scrollCategories(int delta,
                          BottomPanelLayoutTypes.BottomPanelLayout layout) {
        BottomPanelCategoryLayout categories = resolveCategoryLayout(
                layout, buildCategoryRows().size(), categoryScroll);
        dispatchCore(BottomBarUiAction.delta(
                BottomBarUiAction.Type.SCROLL_CATEGORY, delta, categories.maxScroll));
    }

    private static BottomPanelBlueprintLayout resolveBlueprintLayout(
            BottomPanelLayoutTypes.BottomPanelLayout layout) {
        return BottomPanelBlueprintLayout.resolve(
                layout.panelX(), layout.panelY(),
                layout.panelW(), layout.panelH());
    }

    // 鈹€鈹€ Category building 鈹€鈹€

    List<CategoryTypes.CategoryRow> buildCategoryRows() {
        String allLabel = Component.translatable("screen.rtsbuilding.creative.all").getString();
        if (activeBottomPanelTab() == BottomPanelLayoutTypes.BottomPanelTab.CREATIVE) {
            return BottomPanelCategoryBuilder.creativeRows(
                    this.creativeCategory,
                    this.expandedCategoryMods,
                    allLabel,
                    RtsCreativeItemCatalog.get().categories());
        }
        return BottomPanelCategoryBuilder.storageRows(
                this.controller.getStorageCategories(),
                this.controller.getStorageCategory(),
                this.expandedCategoryMods,
                allLabel);
    }

    private String activeCategoryToken() {
        return activeBottomPanelTab() == BottomPanelLayoutTypes.BottomPanelTab.CREATIVE
                ? this.creativeCategory
                : this.controller.getStorageCategory();
    }

    List<RtsCreativeItemCatalog.CreativeEntry> creativeEntriesForCurrentFilter() {
        return RtsCreativeItemCatalog.get().entries(this.creativeCategory, this.creativeSearch);
    }

    private int creativePageCount(int width, int height) {
        int cols = Math.max(1, width / SLOT);
        int rows = Math.max(1, height / SLOT);
        int maxSlots = Math.max(1, cols * rows);
        return Math.max(1, (int) Math.ceil(creativeEntriesForCurrentFilter().size() / (double) maxSlots));
    }

    public RtsCreativeItemCatalog.CreativeEntry getCreativeEntryForTooltip(int index) {
        List<RtsCreativeItemCatalog.CreativeEntry> entries = creativeEntriesForCurrentFilter();
        return index >= 0 && index < entries.size() ? entries.get(index) : null;
    }

    void toggleCategoryExpansion(String modNamespace) {
        if (modNamespace == null || modNamespace.isBlank()) {
            return;
        }
        if (this.expandedCategoryMods.contains(modNamespace)) {
            this.expandedCategoryMods.remove(modNamespace);
        } else {
            this.expandedCategoryMods.add(modNamespace);
        }
    }

    // 鈹€鈹€ Pin / toolbar helpers 鈹€鈹€

    void setSelectedToolSlot(int slot) {
        if (Minecraft.getInstance() == null || Minecraft.getInstance().player == null) {
            return;
        }
        Minecraft.getInstance().player.getInventory().setSelectedSlot(Mth.clamp(slot, 0, 8));
    }

    int getFluidStripWidth(int storageWidth) {
        int wanted = SLOT * 2;
        if (storageWidth < wanted + SLOT * 3) {
            return 0;
        }
        return wanted;
    }

    // 鈹€鈹€ Sort label 鈹€鈹€

    // 鈹€鈹€ Utilities 鈹€鈹€

    public void syncCraftablesPanelState() {
        if (this.lastCraftablesStorageRevision != this.controller.getStorageRevision()) {
            this.lastCraftablesStorageRevision = this.controller.getStorageRevision();
            this.controller.requestCraftables();
        }
        syncCraftSearchValueFromController();
    }
}
