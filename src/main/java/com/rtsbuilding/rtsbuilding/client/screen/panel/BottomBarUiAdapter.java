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
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
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
                                     BottomPanelLayoutTypes.BottomPanelLayout layout,
                                     String selectedStatus,
                                     boolean pluginButtonVisible) {
        BottomBarUiTab requested = toCore(panel.bottomPanelTab);
        BottomPanelLayoutTypes.BottomPanelTab active = panel.activeBottomPanelTab();
        List<RtsCreativeItemCatalog.CreativeEntry> creative =
                active == BottomPanelLayoutTypes.BottomPanelTab.CREATIVE
                        ? panel.creativeEntriesForCurrentFilter()
                        : Collections.<RtsCreativeItemCatalog.CreativeEntry>emptyList();
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
                .pluginButtonVisible(pluginButtonVisible)
                .storageStatus(panel.controller.isStorageLinked(),
                        panel.controller.isStorageScanRunning(),
                        panel.controller.shouldHighlightStorageRefresh())
                .selectedStatus(selectedStatus)
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
            case REFRESH:
                if (transition.state.activeTab == BottomBarUiTab.CREATIVE) {
                    RtsCreativeItemCatalog.get().forceRefresh();
                    panel.creativePage = 0;
                } else if (transition.state.activeTab == BottomBarUiTab.STORAGE) {
                    panel.controller.refreshStoragePage();
                }
                break;
            case OPEN_GUIDE:
                BottomPanelLayoutTypes.BottomPanelLayout layout = panel.resolveBottomPanelLayout();
                panel.screen.openBottomGuide(layout.panelX() + layout.panelW() - 14,
                        layout.panelY() + 3);
                break;
            case OPEN_PLUGINS:
                panel.controller.requestPluginState();
                Minecraft.getMinecraft().displayGuiScreen(new RtsPluginManagementScreen(panel.screen));
                break;
            case SET_SEARCH:
            case CLEAR_SEARCH:
                panel.applyStorageSearchValue(transition.state.search);
                break;
            case PREVIOUS_PAGE:
                if (transition.state.activeTab == BottomBarUiTab.CREATIVE) panel.creativePage = transition.state.page;
                else panel.controller.prevPage();
                break;
            case NEXT_PAGE:
                if (transition.state.activeTab == BottomBarUiTab.CREATIVE) panel.creativePage = transition.state.page;
                else panel.controller.nextPage();
                break;
            case CYCLE_SORT: panel.controller.cycleSort(); break;
            case TOGGLE_SORT_DIRECTION: panel.controller.toggleSortDirection(); break;
            case SELECT_STORAGE: panel.controller.selectStorageEntry(action.index); break;
            case SELECT_RECENT: panel.controller.selectRecentEntry(action.index); break;
            case SELECT_FLUID: panel.controller.selectFluidEntry(action.index); break;
            case SELECT_CREATIVE: selectCreative(panel, action.index); break;
            case SELECT_EMPTY_HAND: panel.controller.selectEmptyHand(); break;
            case SELECT_TOOL:
                panel.setSelectedToolSlot(action.index);
                panel.controller.clearPlacementSelectionPreserveMode();
                break;
            case IMPORT_HOTBAR: panel.controller.storeHotbarSlotToLinked(action.index); break;
            case STORE_FLUID_TOOL: panel.controller.storeFluidFromToolSlot(action.index); break;
            case SELECT_PIN: panel.controller.selectQuickSlot(action.index); break;
            case CLEAR_PIN: panel.controller.clearQuickSlot(action.index); break;
            case STORE_FLUID_PIN:
                String id = panel.controller.getQuickSlotItemId(action.index);
                if (!isBlank(id)) panel.controller.storeFluidFromPinnedItem(id);
                break;
            case OPEN_CRAFT_TERMINAL:
                panel.screen.persistUiState();
                panel.controller.openCraftTerminal();
                break;
            case OPEN_CRAFT_QUANTITY:
                if (action.index >= 0 && action.index < panel.controller.getCraftableEntries().size()) {
                    panel.openCraftQuantityDialog(panel.controller.getCraftableEntries().get(action.index));
                }
                break;
            case SELECT_GUI_BINDING: selectGuiBinding(panel, action.index); break;
            case TOGGLE_GUI_BINDING_PENDING:
                panel.screen.setPendingGuiBindSlot(
                    panel.screen.getPendingGuiBindSlot() == action.index ? -1 : action.index);
                break;
            case CLEAR_GUI_BINDING:
                if (panel.screen.getPendingGuiBindSlot() == action.index) panel.screen.clearPendingGuiBind();
                panel.controller.clearGuiBinding(action.index);
                break;
            case SELECT_CATEGORY: selectCategory(panel, action.index, false); break;
            case TOGGLE_CATEGORY: selectCategory(panel, action.index, true); break;
            case APPLY_CRAFT_SEARCH:
                panel.controller.setCraftablesSearch(transition.state.craftSearchApplied);
                break;
            case TOGGLE_CRAFT_UNAVAILABLE: panel.controller.toggleCraftablesShowUnavailable(); break;
            default: break;
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
                    !com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(selected) && sameItemAndTag(entry.stack(), selected), true));
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
                    !com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(selected) && sameItemAndTag(entry.stack(), selected), true));
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
        Minecraft mc = Minecraft.getMinecraft();
        int selectedHotbar = mc != null && mc.thePlayer != null ? mc.thePlayer.inventory.currentItem : -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc != null && mc.thePlayer != null
                    ? mc.thePlayer.inventory.getStackInSlot(i) : null;
            result.add(new BottomBarUiToolSlot(BottomBarUiToolSlot.Kind.HOTBAR, i,
                    itemId(stack), com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(stack) ? "" : stack.getDisplayName(),
                    com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.count(stack),
                    i == selectedHotbar && !panel.controller.hasSelectedItem()
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
        if (!isBlank(row.modNamespace())) panel.expandedCategoryMods.add(row.modNamespace());
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
        switch (panel.controller.getStorageSort()) {
            case QUANTITY: return "Qty";
            case MOD: return "Mod";
            case NAME: return "Name";
            default: return "Name";
        }
    }

    private static BottomBarUiTab toCore(BottomPanelLayoutTypes.BottomPanelTab tab) {
        switch (tab) {
            case CREATIVE: return BottomBarUiTab.CREATIVE;
            case BLUEPRINTS: return BottomBarUiTab.BLUEPRINTS;
            case STORAGE: return BottomBarUiTab.STORAGE;
            default: return BottomBarUiTab.STORAGE;
        }
    }

    private static BottomPanelLayoutTypes.BottomPanelTab fromCore(BottomBarUiTab tab) {
        switch (tab) {
            case CREATIVE: return BottomPanelLayoutTypes.BottomPanelTab.CREATIVE;
            case BLUEPRINTS: return BottomPanelLayoutTypes.BottomPanelTab.BLUEPRINTS;
            case STORAGE: return BottomPanelLayoutTypes.BottomPanelTab.STORAGE;
            default: return BottomPanelLayoutTypes.BottomPanelTab.STORAGE;
        }
    }

    private static String itemId(ItemStack stack) {
        if (stack == null || com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(stack)) return "";
        ResourceLocation id = com.rtsbuilding.rtsbuilding.platform.registry.RtsRegistries.ITEMS.getNameForObject(stack.getItem());
        return id == null ? "" : id.toString();
    }

    private static boolean sameItemAndTag(ItemStack first, ItemStack second) {
        return com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.areItemsEqual(first, second) && ItemStack.areItemStackTagsEqual(first, second);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
