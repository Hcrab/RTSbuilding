package com.rtsbuilding.rtsbuilding.uipreview;

import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uicore.bottom.BottomBarUiCategory;
import com.rtsbuilding.rtsbuilding.uicore.bottom.BottomBarUiEntry;
import com.rtsbuilding.rtsbuilding.uicore.bottom.BottomBarUiFormats;
import com.rtsbuilding.rtsbuilding.uicore.bottom.BottomBarUiState;
import com.rtsbuilding.rtsbuilding.uicore.bottom.BottomBarUiTab;
import com.rtsbuilding.rtsbuilding.uicore.bottom.BottomBarUiToolSlot;
import com.rtsbuilding.rtsbuilding.uicore.blueprint.BlueprintLibraryUiEntry;
import com.rtsbuilding.rtsbuilding.uicore.blueprint.BlueprintLibraryUiState;
import com.rtsbuilding.rtsbuilding.uikit.layout.BlueprintLibraryLayout;
import com.rtsbuilding.rtsbuilding.uikit.layout.RtsMainlineLayout;

import java.awt.Color;
import java.awt.image.BufferedImage;

/**
 * 使用 main 的 BottomPanel 几何和颜色绘制完整终端夹具。
 *
 * <p>物品内容来自主线插件贴图；不存在于仓库的原版/第三方物品使用受控的
 * 像素夹具，但分类、搜索、分页、快捷槽和合成布局都不是占位矩形。</p>
 */
final class UiMainlineTerminalRenderer {
    private final UiMainlineAssets assets;

    UiMainlineTerminalRenderer(UiMainlineAssets assets) {
        this.assets = assets;
    }

    void render(BufferedImageUiCanvas canvas, UiPreviewLayout layout,
                UiLanguageBundle language, UiPreviewScenario scenario) {
        RtsMainlineLayout.BottomPanel p = layout.bottom();
        UiRect panel = new UiRect(p.panelX, p.panelY, p.panelW, p.panelH);
        UiMainlinePreviewStyle.frame(canvas, panel,
                0xD014151A, 0xFF64788E, 0xFF0D1015);
        canvas.fill(new UiRect(p.panelX + 1, p.panelY + 1, p.panelW - 2,
                RtsMainlineLayout.BOTTOM_PANEL_HEADER_H - 1),
                UiMainlinePreviewStyle.color(0xCC1C242F));
        boolean blueprints = BlueprintLibraryPreviewFixtures.isBlueprintScenario(scenario.variant());
        BlueprintLibraryUiState library = blueprints
                ? BlueprintLibraryPreviewFixtures.forScenario(scenario) : null;
        BottomBarUiState core = BottomBarPreviewFixtures.forScenario(scenario, assets, language);
        drawHeader(canvas, p, language, blueprints, library, core);
        if (blueprints) {
            drawBlueprintLibrary(canvas, p, language, library);
            return;
        }
        drawSortAndDock(canvas, p, core);
        drawCategories(canvas, p, language, core);
        drawSearchPager(canvas, p, core);
        drawToolRow(canvas, p, core);
        drawStorageGrids(canvas, p, core, language);
        if (core.activeTab != BottomBarUiTab.CREATIVE) drawCraftPanel(canvas, p, core);
    }

    private void drawHeader(BufferedImageUiCanvas canvas, RtsMainlineLayout.BottomPanel p,
                            UiLanguageBundle language, boolean blueprints,
                            BlueprintLibraryUiState library, BottomBarUiState core) {
        canvas.text("RTS", p.panelX + 8, p.panelY + 13, UiMainlinePreviewStyle.WHITE);
        int tabX = p.panelX + 38;
        if (core.creativeAccess) {
            drawTab(canvas, tabX, p.panelY + 2, 58,
                    language.text("screen.rtsbuilding.creative.tab"), !blueprints && core.activeTab == BottomBarUiTab.CREATIVE);
            tabX += 62;
        }
        drawTab(canvas, tabX, p.panelY + 2, 76,
                language.text("screen.rtsbuilding.storage.tab"), !blueprints && core.activeTab == BottomBarUiTab.STORAGE);
        tabX += 80;
        drawTab(canvas, tabX, p.panelY + 2, 86,
                language.text("screen.rtsbuilding.blueprints.tab"), blueprints);
        tabX += 96;
        String selected = blueprints && library != null && library.selectedEntry() != null
                ? library.selectedEntry().name : core.selectedStatus;
        canvas.text(canvas.trimToWidth(selected, 190), tabX, p.panelY + 13,
                UiMainlinePreviewStyle.TEXT);

        int guideX = p.panelX + p.panelW - 20;
        UiMainlinePreviewStyle.frame(canvas, new UiRect(guideX, p.panelY + 3, 12, 12),
                0xAA2B3542, 0xFF5D7287, 0xFF0D1015);
        canvas.centeredText("i", guideX + 6, p.panelY + 13, UiMainlinePreviewStyle.WHITE);
        UiMainlinePreviewStyle.frame(canvas, new UiRect(guideX - 16, p.panelY + 3, 12, 12),
                0xAA2B3542, 0xFF5D7287, 0xFF0D1015);
        canvas.centeredText("R", guideX - 10, p.panelY + 13, UiMainlinePreviewStyle.WHITE);
        UiMainlinePreviewStyle.frame(canvas, new UiRect(guideX - 94, p.panelY + 3, 72, 12),
                0xAA273441, 0xFF5D7287, 0xFF0D1015);
        canvas.centeredText(canvas.trimToWidth(language.text("screen.rtsbuilding.plugins.short"), 64),
                guideX - 58, p.panelY + 13, UiMainlinePreviewStyle.WHITE);
    }

    private void drawBlueprintLibrary(BufferedImageUiCanvas canvas, RtsMainlineLayout.BottomPanel p,
                                      UiLanguageBundle language, BlueprintLibraryUiState state) {
        int x = p.panelX + RtsMainlineLayout.BOTTOM_PANEL_PADDING;
        int y = p.panelY + RtsMainlineLayout.BOTTOM_PANEL_HEADER_H + 4;
        int w = Math.max(80, p.panelW - RtsMainlineLayout.BOTTOM_PANEL_PADDING * 2);
        int h = Math.max(24, p.panelH - RtsMainlineLayout.BOTTOM_PANEL_HEADER_H - 8);
        String folder = language.text("screen.rtsbuilding.blueprints.open_folder_short");
        String upload = language.text("screen.rtsbuilding.blueprints.import_file_short");
        String sync = language.text("screen.rtsbuilding.blueprints.sync_create_short");
        String capture = language.text(state.captureLocked
                ? "screen.rtsbuilding.blueprints.capture_active_short"
                : "screen.rtsbuilding.blueprints.capture_short");
        BlueprintLibraryLayout.TopBar top = BlueprintLibraryLayout.topBar(x, w, state.captureLocked,
                canvas.textWidth(folder), canvas.textWidth(upload), canvas.textWidth(sync),
                canvas.textWidth(capture));
        drawLibraryButton(canvas, top.folderX, y, top.folderW, folder, true);
        drawLibraryButton(canvas, top.importX, y, top.importW, upload, true);
        drawLibraryButton(canvas, top.syncX, y, top.syncW, sync, true);
        drawLibraryButton(canvas, top.captureX, y, top.captureW, capture, true);
        UiMainlinePreviewStyle.frame(canvas, new UiRect(top.searchX, y, top.searchW,
                        BlueprintLibraryLayout.SEARCH_H),
                state.searchFocused ? 0xCC09111B : 0xAA111820, 0xFF6B8095, 0xFF0C1118);
        String search = state.query.isEmpty() && !state.searchFocused
                ? language.text("screen.rtsbuilding.blueprints.search")
                : state.query + (state.searchFocused ? "_" : "");
        canvas.text(canvas.trimToWidth(search, top.searchW - 8), top.searchX + 4, y + 11,
                state.query.isEmpty() && !state.searchFocused
                        ? UiMainlinePreviewStyle.color(0x8898A8B8) : Color.WHITE);

        BlueprintLibraryLayout.Geometry g = BlueprintLibraryLayout.geometry(x, y, w, h);
        if (state.captureLocked) {
            UiMainlinePreviewStyle.frame(canvas, new UiRect(x, g.listY, w, g.listH),
                    0x8811161E, 0xFF415266, 0xFF0B0E13);
            canvas.text(language.text("screen.rtsbuilding.blueprints.capture_tool_title"),
                    x + 8, g.listY + 17, Color.WHITE);
            canvas.text(canvas.trimToWidth(language.text(
                            state.captureSaving
                                    ? "screen.rtsbuilding.blueprints.status.save_busy"
                                    : "screen.rtsbuilding.blueprints.status.capture_locked"), w - 16),
                    x + 8, g.listY + 31, UiMainlinePreviewStyle.color(0xFFFFC06C));
            return;
        }
        drawBlueprintRows(canvas, language, state, x, g.listY, g.listW, g.listH);
        drawBlueprintDetails(canvas, language, state, g.detailsX, g.listY, g.detailsW, g.listH);
        canvas.text(canvas.trimToWidth(state.status, w - 8), x + 2, g.statusY + 9,
                UiMainlinePreviewStyle.color(state.statusColor));
    }

    private void drawBlueprintRows(BufferedImageUiCanvas canvas, UiLanguageBundle language,
                                   BlueprintLibraryUiState state, int x, int y, int w, int h) {
        UiMainlinePreviewStyle.frame(canvas, new UiRect(x, y, w, h),
                0x8811161E, 0xFF415266, 0xFF0B0E13);
        java.util.List<BlueprintLibraryUiEntry> filtered = state.filteredEntries();
        if (filtered.isEmpty()) {
            canvas.text(canvas.trimToWidth(language.text(state.entries.isEmpty()
                            ? "screen.rtsbuilding.blueprints.empty"
                            : "screen.rtsbuilding.blueprints.no_results"), w - 12),
                    x + 6, y + 17, UiMainlinePreviewStyle.MUTED);
            return;
        }
        int columns = BlueprintLibraryLayout.listColumns(w);
        int visibleRows = Math.max(1, h / BlueprintLibraryLayout.ROW_H);
        int scroll = Math.min(state.scrollRows,
                BlueprintLibraryLayout.maxListScroll(filtered.size(), columns, visibleRows));
        int cellW = BlueprintLibraryLayout.listCellWidth(w, columns);
        for (int row = 0; row < visibleRows; row++) {
            for (int col = 0; col < columns; col++) {
                int index = (scroll + row) * columns + col;
                if (index >= filtered.size()) break;
                BlueprintLibraryUiEntry entry = filtered.get(index);
                int cellX = x + 1 + col * (cellW + BlueprintLibraryLayout.LIST_COLUMN_GAP);
                int rowY = y + row * BlueprintLibraryLayout.ROW_H;
                boolean selected = entry.fileName.equals(state.selectedFileName);
                boolean enough = entry.buildPercent >= 100;
                int bg = selected ? 0xCC2E654B : enough ? 0x77253832 : 0x7731363E;
                if (!entry.valid()) bg = selected ? 0xCC694238 : 0x77503A36;
                canvas.fill(new UiRect(cellX, rowY + 1, cellW, BlueprintLibraryLayout.ROW_H - 2),
                        UiMainlinePreviewStyle.color(bg));
                BlueprintLibraryLayout.RowActions actions = BlueprintLibraryLayout.rowActions(
                        cellX, rowY, cellW,
                        canvas.textWidth(language.text("screen.rtsbuilding.blueprints.save_as_short")),
                        canvas.textWidth(language.text("screen.rtsbuilding.blueprints.rename")),
                        canvas.textWidth(language.text("screen.rtsbuilding.blueprints.delete")));
                int rightTextX = selected ? actions.saveX - 4 : cellX + cellW - 38;
                canvas.text(canvas.trimToWidth(entry.name, Math.max(32, rightTextX - cellX - 8)),
                        cellX + 5, rowY + 12,
                        entry.valid() ? Color.WHITE : UiMainlinePreviewStyle.color(0xFFFFB0A0));
                if (selected) {
                    if (entry.valid()) {
                        drawLibraryButton(canvas, actions.saveX, actions.buttonY, actions.saveW,
                                language.text("screen.rtsbuilding.blueprints.save_as_short"), true);
                        drawLibraryButton(canvas, actions.renameX, actions.buttonY, actions.renameW,
                                language.text("screen.rtsbuilding.blueprints.rename"), true);
                    }
                    drawLibraryButton(canvas, actions.deleteX, actions.buttonY, actions.deleteW,
                            language.text("screen.rtsbuilding.blueprints.delete"), true);
                } else {
                    canvas.text(entry.buildPercent + "%", cellX + cellW - 36, rowY + 12,
                            enough ? UiMainlinePreviewStyle.color(0xFF9BE6A5)
                                    : UiMainlinePreviewStyle.MUTED);
                }
                canvas.text(canvas.trimToWidth(entry.size, Math.max(24, cellW - 70)),
                        cellX + 5, rowY + 22, UiMainlinePreviewStyle.color(0xFF8FA2B7));
                int barX = cellX + 64;
                int barY = rowY + BlueprintLibraryLayout.ROW_H - 5;
                int barW = Math.max(12, cellX + cellW - barX - 4);
                canvas.fill(new UiRect(barX, barY, barW, 2), UiMainlinePreviewStyle.color(0xAA0C1118));
                canvas.fill(new UiRect(barX, barY, entry.buildPercent * barW / 100, 2),
                        UiMainlinePreviewStyle.color(enough ? 0xFF62D77A : 0xFFE4B04D));
            }
        }
    }

    private void drawBlueprintDetails(BufferedImageUiCanvas canvas, UiLanguageBundle language,
                                      BlueprintLibraryUiState state, int x, int y, int w, int h) {
        UiMainlinePreviewStyle.frame(canvas, new UiRect(x, y, w, h),
                0x8811161E, 0xFF415266, 0xFF0B0E13);
        BlueprintLibraryUiEntry entry = state.selectedEntry();
        if (entry == null) {
            canvas.text(canvas.trimToWidth(language.text("screen.rtsbuilding.blueprints.select_hint"), w - 12),
                    x + 6, y + 17, UiMainlinePreviewStyle.MUTED);
            return;
        }
        canvas.text(canvas.trimToWidth(entry.name, w - 12), x + 6, y + 15, Color.WHITE);
        canvas.text(canvas.trimToWidth(entry.format + "  " + entry.size, w - 12),
                x + 6, y + 27, UiMainlinePreviewStyle.MUTED);
        if (!entry.valid()) {
            canvas.text(canvas.trimToWidth(entry.error, w - 12), x + 6,
                    Math.min(y + 40, y + h - 7), UiMainlinePreviewStyle.color(0xFFFFA0A0));
            return;
        }
        int buildable = entry.blockCount * entry.buildPercent / 100;
        String material = language.format("screen.rtsbuilding.blueprints.materials_progress",
                entry.buildPercent, buildable, entry.blockCount);
        canvas.text(canvas.trimToWidth(material, w - 12), x + 6, y + 40,
                UiMainlinePreviewStyle.color(entry.buildPercent >= 100 ? 0xFF8EEA9B : 0xFFFFC06C));
        int progressW = Math.max(36, w - 12);
        canvas.fill(new UiRect(x + 6, y + 44, progressW, 4), UiMainlinePreviewStyle.color(0xAA0C1118));
        canvas.fill(new UiRect(x + 6, y + 44, entry.buildPercent * progressW / 100, 4),
                UiMainlinePreviewStyle.color(entry.buildPercent >= 100 ? 0xFF62D77A : 0xFFE4B04D));
    }

    private void drawLibraryButton(BufferedImageUiCanvas canvas, int x, int y, int w,
                                   String label, boolean enabled) {
        UiMainlinePreviewStyle.frame(canvas, new UiRect(x, y, w, BlueprintLibraryLayout.BUTTON_H),
                enabled ? 0xAA29323D : 0xAA202630,
                enabled ? 0xFF5E738A : 0xFF47515D, 0xFF10151B);
        canvas.centeredText(canvas.trimToWidth(label, Math.max(8, w - 8)),
                x + w / 2.0D, y + 11,
                enabled ? Color.WHITE : UiMainlinePreviewStyle.MUTED);
    }

    private void drawTab(BufferedImageUiCanvas canvas, int x, int y, int w,
                         String label, boolean active) {
        UiMainlinePreviewStyle.frame(canvas, new UiRect(x, y, w,
                        RtsMainlineLayout.BOTTOM_PANEL_HEADER_H - 3),
                active ? 0xCC355B4C : 0x8826303B,
                active ? 0xFF7CCB93 : 0xFF536679, 0xFF0D1015);
        canvas.centeredText(canvas.trimToWidth(label, w - 8), x + w / 2.0D,
                y + 12, active ? Color.WHITE : UiMainlinePreviewStyle.TEXT);
    }

    private void drawSortAndDock(BufferedImageUiCanvas canvas, RtsMainlineLayout.BottomPanel p,
                                 BottomBarUiState state) {
        drawSmallButton(canvas, p.sortX, p.sortY, "S", 16);
        drawSmallButton(canvas, p.sortX, p.sortY + 20, state.sortAscending ? "A" : "D", 16);
        drawSmallButton(canvas, p.sortX + 42, p.sortY, "+", 16);
        drawSmallButton(canvas, p.sortX + 42, p.sortY + 20, "-", 16);
        canvas.text(state.sortLabel, p.sortX + 20, p.sortY + 12, UiMainlinePreviewStyle.TEXT);

        int cX = p.sortX + 14;
        int cY = p.sortY + 52;
        UiMainlinePreviewStyle.frame(canvas, new UiRect(cX, cY, 18, 18),
                0xAA24303A, 0xFF6E8799, 0xFF111821);
        canvas.centeredText("C", cX + 9, cY + 13, Color.WHITE);
        int[][] slots = new int[][] {{-12,-12},{4,-12},{20,-12},{-12,4},{20,4},{-12,20},{4,20},{20,20}};
        for (int i = 0; i < slots.length; i++) {
            BottomBarUiToolSlot binding = i < state.guiBindings.size() ? state.guiBindings.get(i) : null;
            UiMainlinePreviewStyle.frame(canvas,
                    new UiRect(cX + slots[i][0], cY + slots[i][1], 10, 10),
                    binding != null && binding.bound ? 0xAA23384A : 0xAA202731,
                    0xFF698097, 0xFF0F151C);
            canvas.centeredText(binding != null && binding.bound ? "•" : Integer.toString(i + 1),
                    cX + slots[i][0] + 5, cY + slots[i][1] + 8,
                    UiMainlinePreviewStyle.TEXT);
        }
    }

    private void drawCategories(BufferedImageUiCanvas canvas, RtsMainlineLayout.BottomPanel p,
                                UiLanguageBundle language, BottomBarUiState state) {
        UiRect box = new UiRect(p.categoryX, p.categoryY, 124, p.categoryH);
        UiMainlinePreviewStyle.frame(canvas, box, 0xAA141922, 0xFF637993, 0xFF0D1218);
        canvas.centeredText(language.text("screen.rtsbuilding.storage.category"),
                p.categoryX + 62, p.categoryY + 11, Color.WHITE);
        canvas.text("^", p.categoryX + 104, p.categoryY + 10, Color.WHITE);
        canvas.text("v", p.categoryX + 115, p.categoryY + 10, Color.WHITE);
        java.util.List<BottomBarUiCategory> rows = state.visibleCategories(
                Math.max(1, (p.categoryH - 15) / 11));
        int visible = rows.size();
        for (int i = 0; i < visible; i++) {
            BottomBarUiCategory row = rows.get(i);
            int y = p.categoryY + 14 + i * 11;
            if (row.selected) canvas.fill(new UiRect(p.categoryX + 2, y, 120, 10),
                    UiMainlinePreviewStyle.color(0xAA355B4C));
            String prefix = row.expandable ? (row.expanded ? "- " : "+ ") : "";
            int inset = row.depth * 10;
            canvas.centeredText(canvas.trimToWidth(prefix + row.label, 112 - inset),
                    p.categoryX + 62 + inset / 2.0D, y + 9,
                    row.selected ? UiMainlinePreviewStyle.color(0xFFDFF7E5) : UiMainlinePreviewStyle.TEXT);
        }
    }

    private void drawSearchPager(BufferedImageUiCanvas canvas, RtsMainlineLayout.BottomPanel p,
                                 BottomBarUiState state) {
        int searchFieldW = Math.max(56, p.searchW - 14);
        UiMainlinePreviewStyle.frame(canvas,
                new UiRect(p.storageX, p.storageY, searchFieldW, 14),
                0xAA1A222C, 0xFF5C6F84, 0xFF0C1015);
        String value = state.search.isEmpty() ? "Search" : state.search;
        canvas.text(value, p.storageX + 4, p.storageY + 11,
                state.search.isEmpty() ? UiMainlinePreviewStyle.color(0xFF73859A) : Color.WHITE);
        drawSmallButton(canvas, p.storageX + p.searchW - 12, p.storageY, "x", 12);
        drawSmallButton(canvas, p.pagerX, p.storageY, "<", 16);
        canvas.text((state.page + 1) + "/" + state.pageCount,
                p.pagerX + 20, p.storageY + 11, Color.WHITE);
        drawSmallButton(canvas, p.pagerX + 58, p.storageY, ">", 16);
    }

    private void drawToolRow(BufferedImageUiCanvas canvas, RtsMainlineLayout.BottomPanel p,
                             BottomBarUiState state) {
        java.util.List<BottomBarUiToolSlot> hotbar = toolSlots(state, BottomBarUiToolSlot.Kind.HOTBAR);
        BottomBarUiToolSlot empty = findToolSlot(state, BottomBarUiToolSlot.Kind.EMPTY_HAND, 9);
        int visibleCells = Math.min(10, hotbar.size() + 1);
        for (int i = 0; i < visibleCells; i++) {
            int x = p.storageX + i * 20;
            boolean emptyHand = i == 9;
            BottomBarUiToolSlot slot = emptyHand ? empty : hotbar.get(i);
            UiMainlinePreviewStyle.slot(canvas, x, p.toolY, 18, slot != null && slot.selected);
            if (emptyHand) {
                canvas.fill(new UiRect(x + 4, p.toolY + 4, 10, 10),
                        UiMainlinePreviewStyle.color(0xFFFFC3A3));
            } else if (slot != null && !slot.itemId.isEmpty()) {
                drawItem(canvas, slot.itemId, x + 1, p.toolY + 1, 16);
            }
        }
        int pinX = p.storageX + visibleCells * 20 + 12;
        java.util.List<BottomBarUiToolSlot> pinSlots = toolSlots(state, BottomBarUiToolSlot.Kind.PINNED);
        int visiblePins = Math.max(0, (p.storageX + p.mainStorageW - pinX) / 20);
        boolean pager = visiblePins >= 2 && pinSlots.size() > visiblePins;
        int actualPins = pager ? visiblePins - 1 : visiblePins;
        int start = state.pinPage * Math.max(1, actualPins);
        for (int i = 0; i < visiblePins; i++) {
            UiMainlinePreviewStyle.slot(canvas, pinX + i * 20, p.toolY, 18, false);
            if (pager && i == visiblePins - 1) {
                canvas.centeredText("+", pinX + i * 20 + 9, p.toolY + 13, UiMainlinePreviewStyle.TEXT);
            } else if (start + i < pinSlots.size()) {
                BottomBarUiToolSlot slot = pinSlots.get(start + i);
                drawItem(canvas, slot.itemId, pinX + i * 20 + 1, p.toolY + 1, 16);
                drawCount(canvas, pinX + i * 20, p.toolY, BottomBarUiFormats.compactCount(slot.amount));
            }
        }
    }

    private void drawStorageGrids(BufferedImageUiCanvas canvas, RtsMainlineLayout.BottomPanel p,
                                  BottomBarUiState state, UiLanguageBundle language) {
        int rows = Math.max(1, p.gridH / 22);
        if (state.activeTab == BottomBarUiTab.CREATIVE) {
            int creativeW = Math.max(22, (p.mainStorageW - 6) / 2);
            int recentX = p.storageX + creativeW + 6;
            int recentW = Math.max(22, p.mainStorageW - creativeW - 6);
            drawEntryGrid(canvas, state.creativeEntries, 0,
                    p.storageX, p.gridY, creativeW, p.gridH, false, 0xAA11151D);
            drawEntryGrid(canvas, state.recentEntries, 0,
                    recentX, p.gridY, recentW, p.gridH, false, 0xAA161C24);
            return;
        }

        int fluidW = p.mainStorageW >= 44 + 66 ? 44 : 0;
        int itemX = p.storageX;
        int itemW = p.mainStorageW;
        if (fluidW > 0) {
            drawEntryGrid(canvas, state.fluidEntries, 0,
                    p.storageX, p.gridY, fluidW, p.gridH, true, 0xAA2E1E12);
            itemX += fluidW + 4;
            itemW = Math.max(22, p.mainStorageW - fluidW - 4);
        }
        int storageW = Math.max(22, (itemW - 6) / 2);
        int recentX = itemX + storageW + 6;
        int recentW = Math.max(22, itemW - storageW - 6);
        drawEntryGrid(canvas, state.storageEntries, 0,
                itemX, p.gridY, storageW, p.gridH, false, 0xAA111111);
        drawEntryGrid(canvas, state.recentEntries, 0,
                recentX, p.gridY, recentW, p.gridH, false, 0xAA161C24);
        if (state.storageEntries.isEmpty()) {
            String key = state.storageLinked
                    ? "screen.rtsbuilding.storage.empty_linked"
                    : "screen.rtsbuilding.storage.empty_unlinked";
            canvas.centeredText(language.text(key), itemX + storageW / 2.0D,
                    p.gridY + Math.max(14, rows * 11), UiMainlinePreviewStyle.color(0xFFE7C46A));
            canvas.centeredText(language.text(key + ".detail"), itemX + storageW / 2.0D,
                    p.gridY + Math.max(26, rows * 11 + 12), UiMainlinePreviewStyle.MUTED);
        }
    }

    /** 只遍历当前屏幕可见格；2000 项压力场景不会扫描不可见尾部。 */
    private void drawEntryGrid(BufferedImageUiCanvas canvas, java.util.List<BottomBarUiEntry> entries,
                               int page, int x, int y, int width, int height,
                               boolean fluid, int slotColor) {
        int cols = Math.max(1, width / 22);
        int rows = Math.max(1, height / 22);
        int capacity = cols * rows;
        int start = Math.max(0, page) * capacity;
        canvas.recordScannedItems(Math.min(capacity, Math.max(0, entries.size() - start)));
        for (int i = 0; i < capacity; i++) {
            int slotX = x + (i % cols) * 22;
            int slotY = y + (i / cols) * 22;
            int index = start + i;
            BottomBarUiEntry entry = index < entries.size() ? entries.get(index) : null;
            UiMainlinePreviewStyle.frame(canvas, new UiRect(slotX, slotY, 20, 20),
                    slotColor, fluid ? 0xFFFFA553 : 0xFF526171, 0xFF10151B);
            if (entry == null) continue;
            if (entry.selected) canvas.fill(new UiRect(slotX + 1, slotY + 1, 18, 18),
                    UiMainlinePreviewStyle.color(0x3326C56D));
            drawItem(canvas, entry.id, slotX + 2, slotY + 2, 16);
            String amount = entry.kind == BottomBarUiEntry.Kind.FLUID
                    || entry.kind == BottomBarUiEntry.Kind.RECENT_FLUID
                    ? BottomBarUiFormats.compactFluidAmount(entry.amount)
                    : BottomBarUiFormats.compactCount(entry.amount);
            drawCount(canvas, slotX, slotY, amount);
        }
    }

    private void drawCraftPanel(BufferedImageUiCanvas canvas, RtsMainlineLayout.BottomPanel p,
                                BottomBarUiState state) {
        UiMainlinePreviewStyle.frame(canvas,
                new UiRect(p.craftPanelX, p.craftPanelY, 126, p.craftPanelH),
                0xAA141922, 0xFF637993, 0xFF0D1218);
        canvas.text("Craft", p.craftPanelX + 5, p.craftPanelY + 11, UiMainlinePreviewStyle.WHITE);
        int searchY = p.craftPanelY + 15;
        UiMainlinePreviewStyle.frame(canvas, new UiRect(p.craftPanelX + 4, searchY, 50, 12),
                0xAA1E2731, 0xFF5E738A, 0xFF111921);
        if (!state.craftSearchDraft.isEmpty()) canvas.text(state.craftSearchDraft,
                p.craftPanelX + 6, searchY + 10, Color.WHITE);
        drawSmallButton(canvas, p.craftPanelX + 58, searchY, "OK", 18);
        UiMainlinePreviewStyle.frame(canvas, new UiRect(p.craftPanelX + 80, searchY, 38, 12),
                state.craftShowUnavailable ? 0xAA5A3D2A : 0xAA2C5A41,
                0xFF667D95, 0xFF111821);
        canvas.centeredText(state.craftShowUnavailable ? "ALL" : "MAKE", p.craftPanelX + 99, searchY + 10,
                UiMainlinePreviewStyle.color(0xFFD9F5E2));
        int gridY = searchY + 18;
        int rows = Math.max(1, Math.min(3, (p.craftPanelH - (gridY - p.craftPanelY) - 6) / 20));
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < 4; col++) {
                int index = row * 4 + col;
                int x = p.craftPanelX + 4 + col * 20;
                int y = gridY + row * 20;
                BottomBarUiEntry entry = index < state.craftableEntries.size()
                        ? state.craftableEntries.get(index) : null;
                UiMainlinePreviewStyle.frame(canvas, new UiRect(x, y, 18, 18),
                        entry != null && !entry.available ? 0xAA3F2323 : 0xAA1B2530,
                        0xFF596D84, 0xFF11171E);
                if (entry != null) drawItem(canvas, entry.id, x + 1, y + 1, 16);
            }
        }
    }

    private void drawSmallButton(BufferedImageUiCanvas canvas, int x, int y,
                                 String label, int size) {
        UiMainlinePreviewStyle.frame(canvas, new UiRect(x, y, size, Math.min(size, 16)),
                0xAA29323D, 0xFF5E738A, 0xFF10151B);
        canvas.centeredText(label, x + size / 2.0D, y + Math.min(size, 16) - 3,
                Color.WHITE);
    }

    private void drawItem(BufferedImageUiCanvas canvas, String itemId,
                          int x, int y, int size) {
        String name = itemId == null ? "" : itemId;
        int colon = name.indexOf(':');
        if (colon >= 0) name = name.substring(colon + 1);
        BufferedImage image = assets.item(name);
        canvas.image(image, new UiRect(x, y, size, size));
    }

    private static java.util.List<BottomBarUiToolSlot> toolSlots(
            BottomBarUiState state, BottomBarUiToolSlot.Kind kind) {
        java.util.List<BottomBarUiToolSlot> result = new java.util.ArrayList<BottomBarUiToolSlot>();
        for (BottomBarUiToolSlot slot : state.toolSlots) if (slot.kind == kind) result.add(slot);
        return result;
    }

    private static BottomBarUiToolSlot findToolSlot(BottomBarUiState state,
            BottomBarUiToolSlot.Kind kind, int sourceIndex) {
        for (BottomBarUiToolSlot slot : state.toolSlots) {
            if (slot.kind == kind && slot.sourceIndex == sourceIndex) return slot;
        }
        return null;
    }

    private void drawCount(BufferedImageUiCanvas canvas, int x, int y, String count) {
        canvas.withFontSize(7.0F, new Runnable() {
            @Override
            public void run() {
                canvas.text(count, x + 20 - canvas.textWidth(count), y + 20,
                        UiMainlinePreviewStyle.color(0xFFF7E6A8));
            }
        });
    }
}
