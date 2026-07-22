package com.rtsbuilding.rtsbuilding.client.screen.panel;

import com.rtsbuilding.rtsbuilding.client.record.CraftableEntry;
import com.rtsbuilding.rtsbuilding.client.record.FluidEntry;
import com.rtsbuilding.rtsbuilding.client.record.RecentEntry;
import com.rtsbuilding.rtsbuilding.client.record.StorageEntry;
import com.rtsbuilding.rtsbuilding.client.screen.layout.BottomPanelLayoutTypes;
import com.rtsbuilding.rtsbuilding.client.screen.layout.CategoryTypes;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.RtsPluginManagementScreen;
import com.rtsbuilding.rtsbuilding.client.util.RtsCreativeItemCatalog;
import com.rtsbuilding.rtsbuilding.uicore.bottom.BottomBarUiAction;
import com.rtsbuilding.rtsbuilding.uicore.bottom.BottomBarUiCategory;
import com.rtsbuilding.rtsbuilding.uicore.bottom.BottomBarUiEntry;
import com.rtsbuilding.rtsbuilding.uicore.bottom.BottomBarUiState;
import com.rtsbuilding.rtsbuilding.uicore.bottom.BottomBarUiTab;
import com.rtsbuilding.rtsbuilding.uicore.bottom.BottomBarUiToolSlot;
import com.rtsbuilding.rtsbuilding.uicore.bottom.BottomBarUiTransition;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

import static com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreenConstants.SLOT;
import static com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreenConstants.STORAGE_RECENT_GAP;

/**
 * 把生产控制器的真实底部终端状态映射到纯 Core，并执行 reducer 产出的命令。
 *
 * <p>本类保留 Minecraft ItemStack、玩家背包和网络副作用；Core 模型只拥有
 * 玩家看得见的稳定数据。这样离屏预览与生产 UI 可以共享同一状态结构，且
 * 不会为了截图复制或简化储存逻辑。</p>
 */
final class BottomBarUiAdapter {
    private BottomBarUiAdapter() {}

    static BottomBarUiState snapshot(BottomPanel panel,
                                     BottomPanelLayoutTypes.BottomPanelLayout layout) {
        BottomBarUiTab requested = toCore(panel.bottomPanelTab);
        BottomPanelLayoutTypes.BottomPanelTab active = panel.activeBottomPanelTab();
        List<RtsCreativeItemCatalog.CreativeEntry> creative =
                active == BottomPanelLayoutTypes.BottomPanelTab.CREATIVE
                        ? panel.creativeEntriesForCurrentFilter() : List.of();
        int creativeGridW = Math.max(SLOT, (layout.mainStorageW() - STORAGE_RECENT_GAP) / 2);
        int creativePageSize = Math.max(1, Math.max(1, creativeGridW / SLOT)
                * Math.max(1, layout.gridH() / SLOT));
        int pageCount = active == BottomPanelLayoutTypes.BottomPanelTab.CREATIVE
                ? Math.max(1, (int) Math.ceil(creative.size() / (double) creativePageSize))
                : Math.max(1, panel.controller.getStorageTotalPages());
        int page = active == BottomPanelLayoutTypes.BottomPanelTab.CREATIVE
                ? panel.creativePage : panel.controller.getStoragePage();
        String search = active == BottomPanelLayoutTypes.BottomPanelTab.CREATIVE
                ? panel.creativeSearch : panel.controller.getStorageSearch();

        return BottomBarUiState.builder()
                .requestedTab(requested)
                .access(panel.isCreativePlayer(), panel.hasBlueprintAccess())
                .pluginButtonVisible(panel.hasPluginManagementButtonSpace(layout))
                .storageStatus(panel.controller.isStorageLinked(),
                        panel.controller.isStorageScanRunning(),
                        panel.controller.shouldHighlightStorageRefresh())
                .selectedStatus(panel.selectedPlacementStatusText())
                .search(search, panel.screen.isSearchFocused())
                .page(page, pageCount)
                .sort(sortLabel(panel), panel.controller.isStorageSortAscending())
                .panelHeight(panel.panelHeight)
                .viewScroll(panel.categoryScroll, panel.craftScroll, panel.pinPage)
                .craftSearch(panel.craftSearchDraft,
                        panel.controller.getCraftablesSearch())
                .craftFlags(panel.controller.isCraftablesShowUnavailable(),
                        panel.controller.hasMoreCraftables())
                .categories(categories(panel))
                .storageEntries(storage(panel))
                .creativeEntries(creative(creative, page, creativePageSize, panel))
                .recentEntries(recent(panel))
                .fluidEntries(fluids(panel))
                .craftableEntries(craftables(panel))
                .toolSlots(tools(panel))
                .guiBindings(bindings(panel))
                .build();
    }

    static void apply(BottomPanel panel, BottomBarUiTransition transition) {
        if (transition == null || transition.command == BottomBarUiTransition.Command.NONE) return;
        BottomBarUiAction action = transition.action;
        panel.bottomPanelTab = fromCore(transition.state.requestedTab);
        panel.categoryScroll = transition.state.categoryScroll;
        panel.craftScroll = transition.state.craftScroll;
        panel.pinPage = transition.state.pinPage;
        panel.panelHeight = transition.state.panelHeight;
        panel.craftSearchDraft = transition.state.craftSearchDraft;
        if (transition.command == BottomBarUiTransition.Command.APPLY_VIEW_STATE) return;
        switch (action.type) {
            case REFRESH -> {
                if (transition.state.activeTab == BottomBarUiTab.CREATIVE) {
                    RtsCreativeItemCatalog.get().forceRefresh();
                    panel.creativePage = 0;
                } else if (transition.state.activeTab == BottomBarUiTab.STORAGE) {
                    panel.controller.refreshStoragePage();
                }
            }
            case OPEN_GUIDE -> {
                BottomPanelLayoutTypes.BottomPanelLayout layout = panel.resolveBottomPanelLayout();
                panel.screen.openBottomGuide(layout.panelX() + layout.panelW() - 14,
                        layout.panelY() + 3);
            }
            case OPEN_PLUGINS -> {
                panel.controller.requestPluginState();
                Minecraft.getInstance().setScreen(new RtsPluginManagementScreen(panel.screen));
            }
            case SET_SEARCH, CLEAR_SEARCH -> panel.applyStorageSearchValue(transition.state.search);
            case PREVIOUS_PAGE -> {
                if (transition.state.activeTab == BottomBarUiTab.CREATIVE) panel.creativePage = transition.state.page;
                else panel.controller.prevPage();
            }
            case NEXT_PAGE -> {
                if (transition.state.activeTab == BottomBarUiTab.CREATIVE) panel.creativePage = transition.state.page;
                else panel.controller.nextPage();
            }
            case CYCLE_SORT -> panel.controller.cycleSort();
            case TOGGLE_SORT_DIRECTION -> panel.controller.toggleSortDirection();
            case SELECT_STORAGE -> panel.controller.selectStorageEntry(action.index);
            case SELECT_RECENT -> panel.controller.selectRecentEntry(action.index);
            case SELECT_FLUID -> panel.controller.selectFluidEntry(action.index);
            case SELECT_CREATIVE -> selectCreative(panel, action.index);
            case SELECT_EMPTY_HAND -> panel.controller.selectEmptyHand();
            case SELECT_TOOL -> {
                panel.setSelectedToolSlot(action.index);
                panel.controller.clearPlacementSelectionPreserveMode();
            }
            case IMPORT_HOTBAR -> panel.controller.storeHotbarSlotToLinked(action.index);
            case STORE_FLUID_TOOL -> panel.controller.storeFluidFromToolSlot(action.index);
            case SELECT_PIN -> panel.controller.selectQuickSlot(action.index);
            case CLEAR_PIN -> panel.controller.clearQuickSlot(action.index);
            case STORE_FLUID_PIN -> {
                String id = panel.controller.getQuickSlotItemId(action.index);
                if (id != null && !id.isBlank()) panel.controller.storeFluidFromPinnedItem(id);
            }
            case OPEN_CRAFT_TERMINAL -> {
                panel.screen.persistUiState();
                panel.controller.openCraftTerminal();
            }
            case OPEN_CRAFT_QUANTITY -> {
                if (action.index >= 0 && action.index < panel.controller.getCraftableEntries().size()) {
                    panel.openCraftQuantityDialog(panel.controller.getCraftableEntries().get(action.index));
                }
            }
            case SELECT_GUI_BINDING -> selectGuiBinding(panel, action.index);
            case TOGGLE_GUI_BINDING_PENDING -> panel.screen.setPendingGuiBindSlot(
                    panel.screen.getPendingGuiBindSlot() == action.index ? -1 : action.index);
            case CLEAR_GUI_BINDING -> {
                if (panel.screen.getPendingGuiBindSlot() == action.index) panel.screen.clearPendingGuiBind();
                panel.controller.clearGuiBinding(action.index);
            }
            case SELECT_CATEGORY -> selectCategory(panel, action.index, false);
            case TOGGLE_CATEGORY -> selectCategory(panel, action.index, true);
            case APPLY_CRAFT_SEARCH -> panel.controller.setCraftablesSearch(transition.state.craftSearchApplied);
            case TOGGLE_CRAFT_UNAVAILABLE -> panel.controller.toggleCraftablesShowUnavailable();
            default -> { }
        }
    }

    private static List<BottomBarUiCategory> categories(BottomPanel panel) {
        List<BottomBarUiCategory> result = new ArrayList<>();
        String selected = panel.activeBottomPanelTab() == BottomPanelLayoutTypes.BottomPanelTab.CREATIVE
                ? panel.creativeCategory : panel.controller.getStorageCategory();
        for (CategoryTypes.CategoryRow row : panel.buildCategoryRows()) {
            result.add(new BottomBarUiCategory(row.token(), row.label(), row.depth(),
                    row.expandable(), row.expanded(), row.modNamespace(),
                    row.token().equals(selected)));
        }
        return result;
    }

    private static List<BottomBarUiEntry> storage(BottomPanel panel) {
        List<BottomBarUiEntry> result = new ArrayList<>();
        ItemStack selected = panel.controller.getSelectedItemPreview();
        int i = 0;
        for (StorageEntry entry : panel.controller.getStorageEntries()) {
            result.add(new BottomBarUiEntry(BottomBarUiEntry.Kind.STORAGE, i++,
                    entry.itemId(), entry.name(), entry.count(), 0,
                    !selected.isEmpty() && ItemStack.isSameItemSameComponents(entry.stack(), selected), true));
        }
        return result;
    }

    /**
     * 创造目录可能包含整套大型整合包物品；生产快照只转换当前页，sourceIndex
     * 保留过滤后全列表索引，点击仍能精确回到真实 ItemStack。
     */
    private static List<BottomBarUiEntry> creative(
            List<RtsCreativeItemCatalog.CreativeEntry> entries, int page,
            int pageSize, BottomPanel panel) {
        List<BottomBarUiEntry> result = new ArrayList<>();
        ItemStack selected = panel.controller.getSelectedItemPreview();
        int from = Math.max(0, Math.min(entries.size(), page * Math.max(1, pageSize)));
        int to = Math.min(entries.size(), from + Math.max(1, pageSize));
        for (int i = from; i < to; i++) {
            RtsCreativeItemCatalog.CreativeEntry entry = entries.get(i);
            result.add(new BottomBarUiEntry(BottomBarUiEntry.Kind.CREATIVE, i,
                    entry.itemId(), entry.label(), 0, 0,
                    !selected.isEmpty() && ItemStack.isSameItemSameComponents(entry.stack(), selected), true));
        }
        return result;
    }

    private static List<BottomBarUiEntry> recent(BottomPanel panel) {
        List<BottomBarUiEntry> result = new ArrayList<>();
        List<RecentEntry> entries = panel.controller.getRecentEntries();
        for (int i = 0; i < entries.size(); i++) {
            RecentEntry entry = entries.get(i);
            result.add(new BottomBarUiEntry(entry.fluid()
                    ? BottomBarUiEntry.Kind.RECENT_FLUID : BottomBarUiEntry.Kind.RECENT_ITEM,
                    i, entry.id(), entry.label(), panel.controller.getRecentDisplayAmount(entry),
                    entry.capacity(), false, true));
        }
        return result;
    }

    private static List<BottomBarUiEntry> fluids(BottomPanel panel) {
        List<BottomBarUiEntry> result = new ArrayList<>();
        String selected = panel.controller.getSelectedFluidId();
        List<FluidEntry> entries = panel.controller.getFluidEntries();
        for (int i = 0; i < entries.size(); i++) {
            FluidEntry entry = entries.get(i);
            result.add(new BottomBarUiEntry(BottomBarUiEntry.Kind.FLUID, i,
                    entry.fluidId(), entry.label(), entry.amount(), entry.capacity(),
                    entry.fluidId().equals(selected), true));
        }
        return result;
    }

    private static List<BottomBarUiEntry> craftables(BottomPanel panel) {
        List<BottomBarUiEntry> result = new ArrayList<>();
        List<CraftableEntry> entries = panel.controller.getCraftableEntries();
        for (int i = 0; i < entries.size(); i++) {
            CraftableEntry entry = entries.get(i);
            result.add(new BottomBarUiEntry(BottomBarUiEntry.Kind.CRAFTABLE, i,
                    entry.itemId(), entry.name(), entry.resultCount(), 0,
                    false, entry.craftable()));
        }
        return result;
    }

    private static List<BottomBarUiToolSlot> tools(BottomPanel panel) {
        List<BottomBarUiToolSlot> result = new ArrayList<>();
        Minecraft mc = Minecraft.getInstance();
        int selectedHotbar = mc != null && mc.player != null ? mc.player.getInventory().selected : -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc != null && mc.player != null
                    ? mc.player.getInventory().getItem(i) : ItemStack.EMPTY;
            result.add(new BottomBarUiToolSlot(BottomBarUiToolSlot.Kind.HOTBAR, i,
                    itemId(stack), stack.isEmpty() ? "" : stack.getHoverName().getString(),
                    stack.getCount(), i == selectedHotbar && !panel.controller.hasSelectedItem()
                            && !panel.controller.hasSelectedFluid() && !panel.controller.isEmptyHandSelected(),
                    false, false));
        }
        result.add(new BottomBarUiToolSlot(BottomBarUiToolSlot.Kind.EMPTY_HAND, 9,
                "", "", 0, panel.controller.isEmptyHandSelected(), false, false));
        for (int i = 0; i < panel.controller.getQuickSlotCount(); i++) {
            String id = panel.controller.getQuickSlotItemId(i);
            result.add(new BottomBarUiToolSlot(BottomBarUiToolSlot.Kind.PINNED, i,
                    id, panel.controller.getQuickSlotLabel(i), panel.controller.getStorageTotalCount(id),
                    id.equals(panel.controller.getSelectedItemId()), false, false));
        }
        return result;
    }

    private static List<BottomBarUiToolSlot> bindings(BottomPanel panel) {
        List<BottomBarUiToolSlot> result = new ArrayList<>();
        for (int i = 0; i < panel.controller.getGuiBindingCount(); i++) {
            result.add(new BottomBarUiToolSlot(BottomBarUiToolSlot.Kind.GUI_BINDING, i,
                    itemId(panel.controller.getGuiBindingPreview(i)), panel.controller.getGuiBindingLabel(i),
                    0, false, panel.controller.hasGuiBinding(i),
                    panel.screen.getPendingGuiBindSlot() == i));
        }
        return result;
    }

    private static void selectCreative(BottomPanel panel, int index) {
        List<RtsCreativeItemCatalog.CreativeEntry> entries = panel.creativeEntriesForCurrentFilter();
        if (index < 0 || index >= entries.size()) return;
        RtsCreativeItemCatalog.CreativeEntry entry = entries.get(index);
        panel.controller.selectItemForPlacement(entry.itemId(), entry.label(), entry.stack());
    }

    private static void selectCategory(BottomPanel panel, int index, boolean toggle) {
        List<CategoryTypes.CategoryRow> rows = panel.buildCategoryRows();
        if (index < 0 || index >= rows.size()) return;
        CategoryTypes.CategoryRow row = rows.get(index);
        if (toggle) {
            panel.toggleCategoryExpansion(row.modNamespace());
            return;
        }
        if (panel.activeBottomPanelTab() == BottomPanelLayoutTypes.BottomPanelTab.CREATIVE) {
            panel.creativeCategory = row.token();
            panel.creativePage = 0;
        } else {
            panel.controller.setStorageCategory(row.token());
        }
        if (!row.modNamespace().isBlank()) panel.expandedCategoryMods.add(row.modNamespace());
    }

    private static void selectGuiBinding(BottomPanel panel, int index) {
        if (index < 0 || index >= panel.controller.getGuiBindingCount()) return;
        int pending = panel.screen.getPendingGuiBindSlot();
        if (pending == index) {
            panel.screen.clearPendingGuiBind();
        } else if (panel.controller.hasGuiBinding(index)) {
            panel.screen.clearPendingGuiBind();
            panel.controller.openGuiBinding(index);
        } else {
            panel.screen.setPendingGuiBindSlot(index);
        }
    }

    private static String sortLabel(BottomPanel panel) {
        return switch (panel.controller.getStorageSort()) {
            case QUANTITY -> "Qty";
            case MOD -> "Mod";
            case NAME -> "Name";
        };
    }

    private static BottomBarUiTab toCore(BottomPanelLayoutTypes.BottomPanelTab tab) {
        return switch (tab) {
            case CREATIVE -> BottomBarUiTab.CREATIVE;
            case BLUEPRINTS -> BottomBarUiTab.BLUEPRINTS;
            case STORAGE -> BottomBarUiTab.STORAGE;
        };
    }

    private static BottomPanelLayoutTypes.BottomPanelTab fromCore(BottomBarUiTab tab) {
        return switch (tab) {
            case CREATIVE -> BottomPanelLayoutTypes.BottomPanelTab.CREATIVE;
            case BLUEPRINTS -> BottomPanelLayoutTypes.BottomPanelTab.BLUEPRINTS;
            case STORAGE -> BottomPanelLayoutTypes.BottomPanelTab.STORAGE;
        };
    }

    private static String itemId(ItemStack stack) {
        return stack == null || stack.isEmpty() ? ""
                : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }
}
