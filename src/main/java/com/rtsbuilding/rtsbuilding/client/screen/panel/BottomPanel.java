package com.rtsbuilding.rtsbuilding.client.screen.panel;


import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.popup.RtsCraftFeedbackPopup;
import com.rtsbuilding.rtsbuilding.client.input.overlay.LegacyGuiGraphics;
import com.rtsbuilding.rtsbuilding.client.record.CraftableEntry;
import com.rtsbuilding.rtsbuilding.client.screen.blueprint.BlueprintPanel;
import com.rtsbuilding.rtsbuilding.client.screen.layout.BottomPanelLayoutTypes;
import com.rtsbuilding.rtsbuilding.client.screen.layout.CategoryTypes;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.util.RtsCraftablesUiHelper;
import com.rtsbuilding.rtsbuilding.client.util.RtsCreativeItemCatalog;
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
import com.rtsbuilding.rtsbuilding.uicore.bottom.BottomBarUiReducer;
import com.rtsbuilding.rtsbuilding.uicore.bottom.BottomBarUiState;
import com.rtsbuilding.rtsbuilding.uicore.bottom.BottomBarUiTab;
import com.rtsbuilding.rtsbuilding.uicore.bottom.BottomBarUiTransition;
import com.rtsbuilding.rtsbuilding.uikit.animation.SystemUiClock;
import com.rtsbuilding.rtsbuilding.uikit.animation.UiEasing;
import com.rtsbuilding.rtsbuilding.uikit.animation.UiSelectionAnimationSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.TextComponentTranslation;

import java.util.*;

import static com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreenConstants.*;

/**
 * Bottom panel — centralised UI for storage grids, categories, crafting, fluids, and blueprints.
 * <p>
 * Lifecycle is orchestrated by {@link BuilderScreen}.
 */
public final class BottomPanel {

    // ── State ──
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
    private final BottomPanelInputRouter inputRouter =
            new BottomPanelInputRouter(this);

    public void init(BuilderScreen screen, ClientRtsController controller) {
        this.screen = screen;
        this.controller = controller;
    }

    // ── Rendering ──

    public void render(LegacyGuiGraphics g, int mouseX, int mouseY, float partialTick) {
        BottomPanelLayoutTypes.BottomPanelLayout layout = resolveBottomPanelLayout();
        String selectedStatus = selectedPlacementStatusText();
        BottomPanelHeaderLayout header =
                resolveHeaderLayout(layout, selectedStatus);
        BottomBarUiState core = BottomBarUiAdapter.snapshot(
                this, layout, selectedStatus, header.pluginVisible);
        BottomBarUiTab activeTab = core.activeTab;
        BottomPanelHeaderRenderer.render(
                g, screen.font(), header, core, tabAnimations,
                Config.isUiAnimationsEnabled(),
                translated("screen.rtsbuilding.creative.tab"),
                translated("screen.rtsbuilding.storage.tab"),
                translated("screen.rtsbuilding.blueprints.tab"),
                translated("screen.rtsbuilding.plugins.short"),
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

        BottomPanelSortLayout sortLayout = BottomPanelSortLayout.resolve(
                layout.sortX(), layout.sortY());
        BottomPanelSortRenderer.render(
                g, screen.font(), sortLayout,
                core.sortLabel, core.sortAscending);
        BottomPanelCraftDockLayout craftDock = resolveCraftDockLayout(layout);
        this.hoveredGuiBindingSlot = BottomPanelCraftDockRenderer.render(
                g, screen.font(), core.guiBindings, this.controller,
                craftDock, mouseX, mouseY);

        BottomPanelCategoryLayout categoryLayout = resolveCategoryLayout(
                layout, core.categories.size(), core.categoryScroll);
        this.categoryScroll = categoryLayout.scroll;
        BottomPanelCategoryRenderer.render(
                g, screen.font(),
                translated("screen.rtsbuilding.storage.category"),
                core.categories, categoryLayout);

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
            GuiTextField sb = screen.getSearchBox();
            sb.x = browseLayout.searchField.x;
            sb.y = browseLayout.searchField.y;
            sb.width = browseLayout.searchField.width;
            sb.height = browseLayout.searchField.height;
            sb.drawTextBox();
        }

        BottomPanelBrowseRenderer.renderControls(
                g, screen.font(), browseLayout,
                core.searchFocused, !core.search.isEmpty(),
                core.page, core.pageCount);

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

    public void renderCraftFeedback(LegacyGuiGraphics g) {
        RtsCraftFeedbackPopup.render(g, screen.font(), screen.width,
                RtsMainlineLayout.TOP_H + 6, this.controller);
    }

    // ── Tab rendering ──

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

    /** 返回玩家当前是否真的看得到服务端储存页内容。 */
    public boolean isStorageBrowserVisible() {
        return activeBottomPanelTab() == BottomPanelLayoutTypes.BottomPanelTab.STORAGE;
    }

    boolean hasBlueprintAccess() {
        return Config.areBlueprintsEnabled();
    }

    boolean isCreativePlayer() {
        Minecraft mc = Minecraft.getMinecraft();
        return mc != null && mc.player != null && mc.player.capabilities.isCreativeMode;
    }

    // ── Toolbar ── hotbar / pinned slots ──

    private void renderToolArea(LegacyGuiGraphics g, BottomBarUiState core,
            int mouseX, int mouseY, int storageX, int rowY, int storageW) {
        if (Minecraft.getMinecraft() == null || Minecraft.getMinecraft().player == null) {
            return;
        }

        BottomPanelToolLayout tools = BottomPanelToolLayout.standard(
                storageX, rowY, storageW,
                this.controller.getQuickSlotCount(), this.pinPage);
        this.pinPage = tools.pinPage();
        BottomPanelToolRenderer.HoverResult hover = BottomPanelToolRenderer.render(
                g, screen.font(), core,
                Minecraft.getMinecraft().player.inventory,
                this.controller, tools, mouseX, mouseY);
        this.hoveredToolSlot = hover.hotbarIndex;
        this.hoveredEmptyHandSlot = hover.emptyHand;
        this.hoveredPinIndex = hover.pinIndex;
        this.hoveredPinPageButton = hover.pinPager;
    }

    // ── Category panel ──

    static BottomPanelGridLayout.GridView gridView(
            BottomPanelGridLayout.GridArea area, int entryCount, int page) {
        return BottomPanelGridLayout.resolve(area, SLOT, SLOT - 2, entryCount, page);
    }

    // ── Crafting panel ──

    private void renderCraftablesPanel(LegacyGuiGraphics g, BottomBarUiState core,
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
        GuiTextField csb = screen.getCraftSearchBox();
        if (csb == null || csb.isFocused()) {
            return;
        }
        String expected = this.craftSearchDraft == null ? "" : this.craftSearchDraft;
        if (!expected.equals(csb.getText())) {
            csb.setText(expected);
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

    // ── Craft dock ──

    // ── Click handling ──

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
     * 统一让生产输入先经过 Core reducer，再由平台适配器执行网络、背包或窗口副作用。
     * BottomPanel 仍是编排 owner，但不再各自发明分页/搜索/分类状态转移。
     */
    BottomBarUiTransition dispatchCore(BottomBarUiAction action) {
        BottomBarUiState state = snapshotCore(resolveBottomPanelLayout());
        BottomBarUiTransition transition = BottomBarUiReducer.apply(state, action);
        BottomBarUiAdapter.apply(this, transition);
        return transition;
    }

    /**
     * 头部绘制与输入都从同一份 Kit 几何快照读取页签和右侧入口，避免窄屏下各自推导可见性。
     */
    BottomPanelHeaderLayout resolveHeaderLayout(
            BottomPanelLayoutTypes.BottomPanelLayout layout,
            String selectedStatus) {
        return BottomPanelHeaderLayout.resolve(
                layout.panelX(), layout.panelY(),
                layout.panelW(), layout.panelH(),
                isCreativePlayer(), hasBlueprintAccess(),
                screen.font().getStringWidth(selectedStatus), true);
    }

    BottomBarUiState snapshotCore(
            BottomPanelLayoutTypes.BottomPanelLayout layout) {
        String selectedStatus = selectedPlacementStatusText();
        BottomPanelHeaderLayout header =
                resolveHeaderLayout(layout, selectedStatus);
        return BottomBarUiAdapter.snapshot(
                this, layout, selectedStatus, header.pluginVisible);
    }

    /** 分类滚动共享 Core 的边界钳制，滚轮与上下箭头因此完全同义。 */
    // ── Internal click handling ──

    public void handleStorageSearchChanged(String value) {
        dispatchCore(BottomBarUiAction.value(BottomBarUiAction.Type.SET_SEARCH,
                value == null ? "" : value));
    }

    /** 仅供生产适配器执行 Core 已裁定的搜索副作用，避免监听器递归。 */
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
        GuiTextField sb = screen.getSearchBox();
        if (sb == null) {
            return;
        }
        String expected = activeBottomPanelTab() == BottomPanelLayoutTypes.BottomPanelTab.CREATIVE
                ? this.creativeSearch
                : this.controller.getStorageSearch();
        if (!expected.equals(sb.getText())) {
            sb.setText(expected);
        }
    }

    public void applyCraftSearchDraft() {
        GuiTextField csb = screen.getCraftSearchBox();
        String next = normalizeCraftSearchDraft(csb == null ? this.craftSearchDraft : csb.getText());
        this.craftSearchDraft = next;
        if (csb != null && !next.equals(csb.getText())) {
            csb.setText(next);
        }
        dispatchCore(BottomBarUiAction.value(BottomBarUiAction.Type.SET_CRAFT_SEARCH, next));
        dispatchCore(BottomBarUiAction.simple(BottomBarUiAction.Type.APPLY_CRAFT_SEARCH));
    }

    // ── Layout & resolution ──

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
        this.panelHeight = MathHelper.clamp(this.panelHeight + (direction * SLOT), minH, dynamicMaxH);
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

    private static BottomPanelBlueprintLayout resolveBlueprintLayout(
            BottomPanelLayoutTypes.BottomPanelLayout layout) {
        return BottomPanelBlueprintLayout.resolve(
                layout.panelX(), layout.panelY(),
                layout.panelW(), layout.panelH());
    }

    // ── Category building ──

    List<CategoryTypes.CategoryRow> buildCategoryRows() {
        String allLabel = translated("screen.rtsbuilding.creative.all");
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
        if (isBlank(modNamespace)) {
            return;
        }
        if (this.expandedCategoryMods.contains(modNamespace)) {
            this.expandedCategoryMods.remove(modNamespace);
        } else {
            this.expandedCategoryMods.add(modNamespace);
        }
    }

    // ── Pin / toolbar helpers ──

    void setSelectedToolSlot(int slot) {
        if (Minecraft.getMinecraft() == null || Minecraft.getMinecraft().player == null) {
            return;
        }
        Minecraft.getMinecraft().player.inventory.currentItem = MathHelper.clamp(slot, 0, 8);
    }

    int getFluidStripWidth(int storageWidth) {
        int wanted = SLOT * 2;
        if (storageWidth < wanted + SLOT * 3) {
            return 0;
        }
        return wanted;
    }

    // ── Sort label ──

    // ── Utilities ──

    public void syncCraftablesPanelState() {
        if (this.lastCraftablesStorageRevision != this.controller.getStorageRevision()) {
            this.lastCraftablesStorageRevision = this.controller.getStorageRevision();
            this.controller.requestCraftables();
        }
        syncCraftSearchValueFromController();
    }

    private static String translated(String key) {
        return new TextComponentTranslation(key).getFormattedText();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
