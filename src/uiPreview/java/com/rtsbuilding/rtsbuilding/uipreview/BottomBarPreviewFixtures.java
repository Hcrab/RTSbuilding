package com.rtsbuilding.rtsbuilding.uipreview;

import com.rtsbuilding.rtsbuilding.uicore.bottom.BottomBarUiCategory;
import com.rtsbuilding.rtsbuilding.uicore.bottom.BottomBarUiEntry;
import com.rtsbuilding.rtsbuilding.uicore.bottom.BottomBarUiState;
import com.rtsbuilding.rtsbuilding.uicore.bottom.BottomBarUiTab;
import com.rtsbuilding.rtsbuilding.uicore.bottom.BottomBarUiToolSlot;

import java.util.ArrayList;
import java.util.List;

/**
 * 底部终端的确定性 Core 快照夹具。
 *
 * <p>条目 ID 只从主线真实物品贴图目录读取；本类只组合“服务器已返回这些条目”
 * 的状态，不拥有布局、绘制或生产逻辑。2000 项场景会重复正式 ID 并改变数量，
 * 仅用于验证可见范围有界，绝不会进入生产 source set。</p>
 */
final class BottomBarPreviewFixtures {
    private BottomBarPreviewFixtures() {}

    static BottomBarUiState forScenario(UiPreviewScenario scenario,
                                        UiMainlineAssets assets,
                                        UiLanguageBundle language) {
        List<String> formalItems = assets.itemNames();
        boolean creative = scenario.variant() == UiPreviewScenario.Variant.CREATIVE_CATALOG;
        boolean empty = scenario.variant() == UiPreviewScenario.Variant.EMPTY_LOADING_FAILED_DISABLED;
        int count = empty ? 0 : Math.max(1, scenario.storageCount());
        List<BottomBarUiEntry> storage = entries(formalItems, count,
                BottomBarUiEntry.Kind.STORAGE, true);
        List<BottomBarUiEntry> creativeEntries = entries(formalItems,
                creative ? formalItems.size() : 0, BottomBarUiEntry.Kind.CREATIVE, false);
        List<BottomBarUiEntry> recent = entries(formalItems, empty ? 0 : 8,
                BottomBarUiEntry.Kind.RECENT_ITEM, false);
        List<BottomBarUiEntry> fluids = new ArrayList<BottomBarUiEntry>();
        if (!empty && !creative) {
            fluids.add(new BottomBarUiEntry(BottomBarUiEntry.Kind.FLUID, 0,
                    formalId(formalItems.get(0)), "Water", 128_000L, 256_000L, false, true));
            fluids.add(new BottomBarUiEntry(BottomBarUiEntry.Kind.FLUID, 1,
                    formalId(formalItems.get(1 % formalItems.size())), "Lava", 32_000L, 64_000L, true, true));
        }
        List<BottomBarUiEntry> craftables = entries(formalItems, empty ? 0 : 12,
                BottomBarUiEntry.Kind.CRAFTABLE, false);
        List<BottomBarUiCategory> categories = categories(language, creative);
        List<BottomBarUiToolSlot> tools = tools(formalItems);
        List<BottomBarUiToolSlot> bindings = bindings(formalItems);
        int pageCount = Math.max(1, (int) Math.ceil(count / 48.0D));
        return BottomBarUiState.builder()
                .requestedTab(creative ? BottomBarUiTab.CREATIVE : BottomBarUiTab.STORAGE)
                .access(creative, true).pluginButtonVisible(true)
                .storageStatus(!empty, empty, scenario.variant() == UiPreviewScenario.Variant.STORAGE_2000)
                .selectedStatus(empty ? language.text("screen.rtsbuilding.status.selected_none")
                        : language.format("screen.rtsbuilding.status.selected_item", formalItems.get(0)))
                .search(creative ? "" : scenario.variant() == UiPreviewScenario.Variant.SCROLL_CAPTURE ? "range" : "", false)
                .page(0, creative ? 1 : pageCount).sort("Qty", false)
                .panelHeight(110).viewScroll(0, 0, 0)
                .craftSearch("", "").craftFlags(false, false)
                .categories(categories).storageEntries(storage).creativeEntries(creativeEntries)
                .recentEntries(recent).fluidEntries(fluids).craftableEntries(craftables)
                .toolSlots(tools).guiBindings(bindings).build();
    }

    private static List<BottomBarUiEntry> entries(List<String> names, int count,
                                                   BottomBarUiEntry.Kind kind,
                                                   boolean firstSelected) {
        List<BottomBarUiEntry> result = new ArrayList<BottomBarUiEntry>();
        for (int i = 0; i < count; i++) {
            String name = names.get(i % names.size());
            long amount = kind == BottomBarUiEntry.Kind.CRAFTABLE ? 1 + i % 4 : 12L + (long) i * 7L;
            result.add(new BottomBarUiEntry(kind, i, formalId(name), name,
                    amount, 0L, firstSelected && i == 0,
                    kind != BottomBarUiEntry.Kind.CRAFTABLE || i % 5 != 4));
        }
        return result;
    }

    private static List<BottomBarUiCategory> categories(UiLanguageBundle language, boolean creative) {
        List<BottomBarUiCategory> rows = new ArrayList<BottomBarUiCategory>();
        rows.add(new BottomBarUiCategory("all",
                language.text("screen.rtsbuilding.creative.all"), 0, false, false, "", true));
        rows.add(new BottomBarUiCategory("mod|rtsbuilding", "RTSBuilding", 0,
                true, true, "rtsbuilding", false));
        rows.add(new BottomBarUiCategory("tab|rtsbuilding|rtsbuilding:main", creative
                ? language.text("screen.rtsbuilding.creative.tab")
                : language.text("screen.rtsbuilding.storage.tab"),
                1, false, false, "rtsbuilding", false));
        return rows;
    }

    private static List<BottomBarUiToolSlot> tools(List<String> names) {
        List<BottomBarUiToolSlot> result = new ArrayList<BottomBarUiToolSlot>();
        for (int i = 0; i < 9; i++) {
            String name = names.get(i % names.size());
            result.add(new BottomBarUiToolSlot(BottomBarUiToolSlot.Kind.HOTBAR, i,
                    formalId(name), name, i + 1, i == 0, false, false));
        }
        result.add(new BottomBarUiToolSlot(BottomBarUiToolSlot.Kind.EMPTY_HAND, 9,
                "", "", 0, false, false, false));
        for (int i = 0; i < 12; i++) {
            String name = names.get((i + 4) % names.size());
            result.add(new BottomBarUiToolSlot(BottomBarUiToolSlot.Kind.PINNED, i,
                    formalId(name), name, 64L * (i + 1), false, false, false));
        }
        return result;
    }

    private static List<BottomBarUiToolSlot> bindings(List<String> names) {
        List<BottomBarUiToolSlot> result = new ArrayList<BottomBarUiToolSlot>();
        for (int i = 0; i < 8; i++) {
            String name = names.get((i + 2) % names.size());
            result.add(new BottomBarUiToolSlot(BottomBarUiToolSlot.Kind.GUI_BINDING, i,
                    formalId(name), name, 0, false, i < 2, false));
        }
        return result;
    }

    private static String formalId(String textureName) {
        return "rtsbuilding:" + textureName;
    }
}
