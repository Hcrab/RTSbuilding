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
import com.rtsbuilding.rtsbuilding.uikit.animation.FixedUiClock;
import com.rtsbuilding.rtsbuilding.uikit.animation.UiBlink;
import com.rtsbuilding.rtsbuilding.uikit.canvas.BlueprintLibraryChromeRenderer;
import com.rtsbuilding.rtsbuilding.uikit.layout.BlueprintLibraryLayout;
import com.rtsbuilding.rtsbuilding.uikit.layout.BottomPanelBlueprintLayout;
import com.rtsbuilding.rtsbuilding.uikit.layout.BottomPanelBrowseLayout;
import com.rtsbuilding.rtsbuilding.uikit.layout.BottomPanelCategoryLayout;
import com.rtsbuilding.rtsbuilding.uikit.layout.BottomPanelCraftDockLayout;
import com.rtsbuilding.rtsbuilding.uikit.layout.BottomPanelCraftLayout;
import com.rtsbuilding.rtsbuilding.uikit.layout.BottomPanelGridLayout;
import com.rtsbuilding.rtsbuilding.uikit.layout.BottomPanelHeaderLayout;
import com.rtsbuilding.rtsbuilding.uikit.layout.BottomPanelSortLayout;
import com.rtsbuilding.rtsbuilding.uikit.layout.BottomPanelToolLayout;
import com.rtsbuilding.rtsbuilding.uikit.layout.RtsMainlineLayout;
import com.rtsbuilding.rtsbuilding.uikit.theme.BottomPanelBrowseStyle;
import com.rtsbuilding.rtsbuilding.uikit.theme.BottomPanelCategoryStyle;
import com.rtsbuilding.rtsbuilding.uikit.theme.BottomPanelCraftDockStyle;
import com.rtsbuilding.rtsbuilding.uikit.theme.BottomPanelCraftStyle;
import com.rtsbuilding.rtsbuilding.uikit.theme.BottomPanelGridStyle;
import com.rtsbuilding.rtsbuilding.uikit.theme.BottomPanelHeaderStyle;
import com.rtsbuilding.rtsbuilding.uikit.theme.BottomPanelSortStyle;
import com.rtsbuilding.rtsbuilding.uikit.theme.BottomPanelToolStyle;
import com.rtsbuilding.rtsbuilding.uikit.theme.BlueprintLibraryStyle;

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
        boolean blueprints = BlueprintLibraryPreviewFixtures.isBlueprintScenario(scenario.variant());
        BlueprintLibraryUiState library = blueprints
                ? BlueprintLibraryPreviewFixtures.forScenario(scenario) : null;
        BottomBarUiState core = BottomBarPreviewFixtures.forScenario(scenario, assets, language);
        BottomBarUiState visibleState = blueprints
                ? core.toBuilder().requestedTab(BottomBarUiTab.BLUEPRINTS).build()
                : core;
        drawHeader(canvas, p, language, visibleState);
        if (blueprints) {
            drawBlueprintLibrary(canvas, p, language, library);
            return;
        }
        drawSortAndDock(canvas, p, core);
        drawCategories(canvas, p, language, core);
        drawSearchPager(canvas, p, core, scenario);
        drawToolRow(canvas, p, core);
        drawStorageGrids(canvas, p, core, language);
        if (core.activeTab != BottomBarUiTab.CREATIVE) drawCraftPanel(canvas, p, core);
    }

    private void drawHeader(BufferedImageUiCanvas canvas, RtsMainlineLayout.BottomPanel p,
                            UiLanguageBundle language, BottomBarUiState state) {
        BottomPanelHeaderLayout header = BottomPanelHeaderLayout.resolve(
                p.panelX, p.panelY, p.panelW, p.panelH,
                state.creativeAccess, state.blueprintAccess,
                canvas.textWidth(state.selectedStatus),
                state.pluginButtonVisible);
        frame(canvas, header.panel,
                BottomPanelHeaderStyle.PANEL_BACKGROUND.toArgb(),
                BottomPanelHeaderStyle.PANEL_BORDER_LIGHT.toArgb(),
                BottomPanelHeaderStyle.PANEL_BORDER_DARK.toArgb());
        canvas.fill(rect(header.header),
                UiMainlinePreviewStyle.color(
                        BottomPanelHeaderStyle.HEADER_BACKGROUND));
        canvas.text("RTS", header.logoX(), header.logoY(),
                BottomPanelHeaderStyle.LOGO_TEXT);

        for (BottomPanelHeaderLayout.TabArea tab : header.tabs) {
            boolean active = state.activeTab == tab.tab;
            frame(canvas, tab.area,
                    BottomPanelHeaderStyle.tabBackground(active, false).toArgb(),
                    BottomPanelHeaderStyle.tabBorder(active).toArgb(),
                    BottomPanelHeaderStyle.PANEL_BORDER_DARK.toArgb());
            String label = language.text(tab.tab == BottomBarUiTab.CREATIVE
                    ? "screen.rtsbuilding.creative.tab"
                    : tab.tab == BottomBarUiTab.BLUEPRINTS
                            ? "screen.rtsbuilding.blueprints.tab"
                            : "screen.rtsbuilding.storage.tab");
            canvas.centeredText(
                    canvas.trimToWidth(
                            label,
                            tab.area.width
                                    - BottomPanelHeaderLayout.TAB_TEXT_INSET * 2),
                    tab.area.x + tab.area.width / 2.0D,
                    tab.area.y + 12,
                    UiMainlinePreviewStyle.color(
                            BottomPanelHeaderStyle.tabText(active)));
        }

        if (header.selectedStatus.width > 0) {
            canvas.text(
                    canvas.trimToWidth(
                            state.selectedStatus,
                            header.selectedStatus.width),
                    header.selectedStatus.x,
                    header.selectedStatus.y,
                    BottomPanelHeaderStyle.STATUS_TEXT);
        }

        boolean refreshDirty = state.activeTab == BottomBarUiTab.STORAGE
                && !state.storageScanning
                && state.refreshHighlighted;
        frame(canvas, header.refresh,
                BottomPanelHeaderStyle.refreshBackground(
                        state.storageScanning, refreshDirty, false).toArgb(),
                BottomPanelHeaderStyle.refreshBorder(refreshDirty).toArgb(),
                (refreshDirty
                        ? BottomPanelHeaderStyle.refreshBorder(true)
                        : BottomPanelHeaderStyle.PANEL_BORDER_DARK).toArgb());
        canvas.centeredText(
                "R",
                header.refresh.x + header.refresh.width / 2.0D,
                header.refresh.y + 10,
                UiMainlinePreviewStyle.color(
                        refreshDirty
                                ? BottomPanelHeaderStyle.TAB_ACTIVE_TEXT
                                : BottomPanelHeaderStyle.ACTION_TEXT));
        frame(canvas, header.guide,
                BottomPanelHeaderStyle.actionBackground(false).toArgb(),
                BottomPanelHeaderStyle.ACTION_BORDER.toArgb(),
                BottomPanelHeaderStyle.PANEL_BORDER_DARK.toArgb());
        canvas.centeredText(
                "i",
                header.guide.x + header.guide.width / 2.0D,
                header.guide.y + 10,
                UiMainlinePreviewStyle.color(
                        BottomPanelHeaderStyle.ACTION_TEXT));
        if (header.pluginVisible) {
            frame(canvas, header.plugin,
                    BottomPanelHeaderStyle.pluginBackground(false).toArgb(),
                    BottomPanelHeaderStyle.ACTION_BORDER.toArgb(),
                    BottomPanelHeaderStyle.PANEL_BORDER_DARK.toArgb());
            canvas.centeredText(
                    canvas.trimToWidth(
                            language.text("screen.rtsbuilding.plugins.short"),
                            header.plugin.width
                                    - BottomPanelHeaderLayout.TAB_TEXT_INSET * 2),
                    header.plugin.x + header.plugin.width / 2.0D,
                    header.plugin.y + 10,
                    UiMainlinePreviewStyle.color(
                            BottomPanelHeaderStyle.PLUGIN_TEXT));
        }
    }

    private void drawBlueprintLibrary(BufferedImageUiCanvas canvas, RtsMainlineLayout.BottomPanel p,
                                      UiLanguageBundle language, BlueprintLibraryUiState state) {
        BottomPanelBlueprintLayout blueprint = BottomPanelBlueprintLayout.resolve(
                p.panelX, p.panelY, p.panelW, p.panelH);
        int x = blueprint.content.x;
        int y = blueprint.content.y;
        int w = blueprint.content.width;
        int h = blueprint.content.height;
        String folder = language.text("screen.rtsbuilding.blueprints.open_folder_short");
        String upload = language.text("screen.rtsbuilding.blueprints.import_file_short");
        String sync = language.text("screen.rtsbuilding.blueprints.sync_create_short");
        String capture = language.text(state.captureLocked
                ? "screen.rtsbuilding.blueprints.capture_active_short"
                : "screen.rtsbuilding.blueprints.capture_short");
        BlueprintLibraryLayout.Geometry geometry =
                BlueprintLibraryLayout.geometry(x, y, w, h);
        BlueprintLibraryLayout.TopBar top = BlueprintLibraryLayout.topBar(x, w, state.captureLocked,
                canvas.textWidth(folder), canvas.textWidth(upload), canvas.textWidth(sync),
                canvas.textWidth(capture));
        BlueprintLibraryLayout.ActionTextWidths actionWidths =
                new BlueprintLibraryLayout.ActionTextWidths(
                        canvas.textWidth(language.text(
                                "screen.rtsbuilding.blueprints.save_as_short")),
                        canvas.textWidth(language.text(
                                "screen.rtsbuilding.blueprints.rename")),
                        canvas.textWidth(language.text(
                                "screen.rtsbuilding.blueprints.delete")));
        BlueprintLibraryChromeRenderer.renderTopBar(
                canvas,
                geometry,
                top,
                state.searchFocused,
                Double.NEGATIVE_INFINITY,
                Double.NEGATIVE_INFINITY);
        drawLibraryButtonText(canvas, top.folderBounds(y), folder);
        drawLibraryButtonText(canvas, top.importBounds(y), upload);
        drawLibraryButtonText(canvas, top.syncBounds(y), sync);
        drawLibraryButtonText(canvas, top.captureBounds(y), capture);
        String search = state.query.isEmpty() && !state.searchFocused
                ? language.text("screen.rtsbuilding.blueprints.search")
                : state.query + (state.searchFocused ? "_" : "");
        canvas.text(canvas.trimToWidth(search, top.searchW - 8), top.searchX + 4, y + 11,
                state.query.isEmpty() && !state.searchFocused
                        ? UiMainlinePreviewStyle.color(
                                BlueprintLibraryStyle.SEARCH_PLACEHOLDER_TEXT)
                        : UiMainlinePreviewStyle.color(
                                BlueprintLibraryStyle.SEARCH_TEXT));

        BlueprintLibraryChromeRenderer.renderBodyFrames(
                canvas,
                geometry,
                state.captureLocked);
        if (state.captureLocked) {
            canvas.text(language.text("screen.rtsbuilding.blueprints.capture_tool_title"),
                    x + BlueprintLibraryLayout.CAPTURE_TEXT_X,
                    geometry.listY
                            + BlueprintLibraryLayout.CAPTURE_TITLE_Y + 9,
                    UiMainlinePreviewStyle.color(
                            BlueprintLibraryStyle.PRIMARY_TEXT));
            canvas.text(canvas.trimToWidth(language.text(
                            state.captureSaving
                                    ? "screen.rtsbuilding.blueprints.status.save_busy"
                                    : "screen.rtsbuilding.blueprints.status.capture_locked"), w - 16),
                    x + BlueprintLibraryLayout.CAPTURE_TEXT_X,
                    geometry.listY
                            + BlueprintLibraryLayout.CAPTURE_STATUS_Y + 9,
                    UiMainlinePreviewStyle.color(
                            BlueprintLibraryStyle.CAPTURE_WARNING_TEXT));
            return;
        }
        drawBlueprintRows(
                canvas,
                language,
                state,
                geometry,
                actionWidths);
        drawBlueprintDetails(
                canvas,
                language,
                state,
                geometry);
        canvas.text(canvas.trimToWidth(state.status, w - 8),
                x + BlueprintLibraryLayout.STATUS_TEXT_X,
                geometry.statusY + 9,
                UiMainlinePreviewStyle.color(state.statusColor));
    }

    private void drawBlueprintRows(BufferedImageUiCanvas canvas, UiLanguageBundle language,
                                   BlueprintLibraryUiState state,
                                   BlueprintLibraryLayout.Geometry geometry,
                                   BlueprintLibraryLayout.ActionTextWidths actionWidths) {
        int x = geometry.x;
        int y = geometry.listY;
        int w = geometry.listW;
        java.util.List<BlueprintLibraryUiEntry> filtered = state.filteredEntries();
        if (filtered.isEmpty()) {
            canvas.text(canvas.trimToWidth(language.text(state.entries.isEmpty()
                            ? "screen.rtsbuilding.blueprints.empty"
                            : "screen.rtsbuilding.blueprints.no_results"), w - 12),
                    x + BlueprintLibraryLayout.FRAME_TEXT_X,
                    y + BlueprintLibraryLayout.EMPTY_TEXT_Y + 9,
                    UiMainlinePreviewStyle.color(
                            BlueprintLibraryStyle.SECONDARY_TEXT));
            return;
        }
        BlueprintLibraryLayout.VisibleWindow window =
                BlueprintLibraryLayout.visibleWindow(
                        filtered.size(),
                        state.scrollRows,
                        geometry.listW,
                        geometry.listH);
        for (int row = 0; row < window.visibleRows; row++) {
            for (int col = 0; col < window.columns; col++) {
                int index = (window.scrollRows + row)
                        * window.columns + col;
                if (index >= filtered.size()) break;
                BlueprintLibraryUiEntry entry = filtered.get(index);
                BlueprintLibraryLayout.RowGeometry rowGeometry =
                        BlueprintLibraryLayout.rowGeometry(
                                x,
                                y,
                                w,
                                row,
                                col,
                                actionWidths);
                int cellX = (int) rowGeometry.hitBounds.getX();
                int rowY = (int) rowGeometry.hitBounds.getY();
                int cellW = (int) rowGeometry.hitBounds.getWidth();
                boolean selected = entry.fileName.equals(state.selectedFileName);
                boolean enough = entry.buildPercent >= 100;
                BlueprintLibraryChromeRenderer.renderRow(
                        canvas,
                        rowGeometry,
                        entry,
                        selected,
                        selected,
                        Double.NEGATIVE_INFINITY,
                        Double.NEGATIVE_INFINITY);
                int rightTextX = selected
                        ? (int) rowGeometry.save.getX() - 4
                        : cellX + cellW
                                - BlueprintLibraryLayout.ROW_PERCENT_RIGHT;
                canvas.text(canvas.trimToWidth(entry.name, Math.max(32, rightTextX - cellX - 8)),
                        cellX + BlueprintLibraryLayout.ROW_NAME_X,
                        rowY + BlueprintLibraryLayout.ROW_NAME_Y + 8,
                        UiMainlinePreviewStyle.color(
                                entry.valid()
                                        ? BlueprintLibraryStyle.ROW_NAME_TEXT
                                        : BlueprintLibraryStyle.ROW_INVALID_TEXT));
                if (selected) {
                    if (entry.valid()) {
                        drawLibraryButtonText(
                                canvas,
                                rowGeometry.save,
                                language.text(
                                        "screen.rtsbuilding.blueprints.save_as_short"));
                        drawLibraryButtonText(
                                canvas,
                                rowGeometry.rename,
                                language.text(
                                        "screen.rtsbuilding.blueprints.rename"));
                    }
                    drawLibraryButtonText(
                            canvas,
                            rowGeometry.delete,
                            language.text(
                                    "screen.rtsbuilding.blueprints.delete"));
                } else {
                    canvas.text(entry.buildPercent + "%",
                            cellX + cellW
                                    - BlueprintLibraryLayout.ROW_PERCENT_RIGHT,
                            rowY + BlueprintLibraryLayout.ROW_NAME_Y + 8,
                            UiMainlinePreviewStyle.color(
                                    enough
                                            ? BlueprintLibraryStyle
                                                    .ROW_PERCENT_READY_TEXT
                                            : BlueprintLibraryStyle
                                                    .ROW_PERCENT_TEXT));
                }
                canvas.text(canvas.trimToWidth(entry.size, Math.max(24, cellW - 70)),
                        cellX + BlueprintLibraryLayout.ROW_NAME_X,
                        rowY + BlueprintLibraryLayout.ROW_SIZE_Y + 8,
                        UiMainlinePreviewStyle.color(
                                BlueprintLibraryStyle.ROW_SIZE_TEXT));
            }
        }
    }

    private void drawBlueprintDetails(BufferedImageUiCanvas canvas, UiLanguageBundle language,
                                      BlueprintLibraryUiState state,
                                      BlueprintLibraryLayout.Geometry geometry) {
        int x = geometry.detailsX;
        int y = geometry.listY;
        int w = geometry.detailsW;
        int h = geometry.listH;
        BlueprintLibraryUiEntry entry = state.selectedEntry();
        if (entry == null) {
            canvas.text(canvas.trimToWidth(language.text("screen.rtsbuilding.blueprints.select_hint"), w - 12),
                    x + BlueprintLibraryLayout.FRAME_TEXT_X,
                    y + BlueprintLibraryLayout.EMPTY_TEXT_Y + 9,
                    UiMainlinePreviewStyle.color(
                            BlueprintLibraryStyle.SECONDARY_TEXT));
            return;
        }
        canvas.text(canvas.trimToWidth(entry.name, w - 12),
                x + BlueprintLibraryLayout.FRAME_TEXT_X,
                y + BlueprintLibraryLayout.DETAILS_NAME_Y + 9,
                UiMainlinePreviewStyle.color(
                        BlueprintLibraryStyle.PRIMARY_TEXT));
        boolean invalidEntryHasThreeTextLines =
                BlueprintLibraryLayout.invalidDetailsShowMeta(h, 9);
        if (entry.valid() || invalidEntryHasThreeTextLines) {
            canvas.text(canvas.trimToWidth(entry.format + "  " + entry.size, w - 12),
                    x + BlueprintLibraryLayout.FRAME_TEXT_X,
                    y + BlueprintLibraryLayout.DETAILS_META_Y + 9,
                    UiMainlinePreviewStyle.color(
                            BlueprintLibraryStyle.SECONDARY_TEXT));
        }
        if (!entry.valid()) {
            canvas.text(canvas.trimToWidth(entry.error, w - 12),
                    x + BlueprintLibraryLayout.FRAME_TEXT_X,
                    y + BlueprintLibraryLayout.invalidDetailsTextY(h, 9) + 9,
                    UiMainlinePreviewStyle.color(
                            BlueprintLibraryStyle.INVALID_TEXT));
            return;
        }
        canvas.text(canvas.trimToWidth(entry.materialSummary, w - 12),
                x + BlueprintLibraryLayout.FRAME_TEXT_X,
                y + BlueprintLibraryLayout.DETAILS_SUMMARY_Y + 9,
                UiMainlinePreviewStyle.color(
                        entry.buildPercent >= 100
                                ? BlueprintLibraryStyle.READY_TEXT
                                : BlueprintLibraryStyle.WARNING_TEXT));
        BlueprintLibraryChromeRenderer.renderDetailsProgress(
                canvas,
                BlueprintLibraryLayout.detailsGeometry(geometry),
                entry);
    }

    private void drawLibraryButtonText(
        BufferedImageUiCanvas canvas,
            UiRect bounds,
            String label) {
        canvas.centeredText(canvas.trimToWidth(
                        label,
                        Math.max(
                                8,
                                (int) bounds.getWidth() - 8)),
                bounds.getX() + bounds.getWidth() / 2.0D,
                bounds.getY() + 11,
                UiMainlinePreviewStyle.color(
                        BlueprintLibraryStyle.BUTTON_TEXT));
    }

    private static void frame(
            BufferedImageUiCanvas canvas,
            BottomPanelHeaderLayout.Area area,
            int background,
            int light,
            int dark) {
        UiMainlinePreviewStyle.frame(
                canvas, rect(area), background, light, dark);
    }

    private static UiRect rect(BottomPanelHeaderLayout.Area area) {
        return new UiRect(area.x, area.y, area.width, area.height);
    }

    private void drawSortAndDock(BufferedImageUiCanvas canvas, RtsMainlineLayout.BottomPanel p,
                                 BottomBarUiState state) {
        BottomPanelSortLayout sort = BottomPanelSortLayout.resolve(p.sortX, p.sortY);
        drawSortButton(canvas, sort.cycleSort, "S");
        drawSortButton(
                canvas, sort.toggleDirection,
                state.sortAscending ? "A" : "D");
        drawSortButton(canvas, sort.increaseHeight, "+");
        drawSortButton(canvas, sort.decreaseHeight, "-");
        canvas.text(
                state.sortLabel,
                sort.labelX(),
                sort.labelY() + 6,
                UiMainlinePreviewStyle.color(BottomPanelSortStyle.LABEL_TEXT));

        BottomPanelCraftDockLayout dock = BottomPanelCraftDockLayout.resolve(
                p.craftDockX, p.craftDockY,
                BottomPanelCraftDockLayout.MAX_BINDING_COUNT);
        UiMainlinePreviewStyle.frame(canvas,
                new UiRect(dock.craftButton.x, dock.craftButton.y,
                        dock.craftButton.width, dock.craftButton.height),
                BottomPanelCraftDockStyle.craftBackground(false).toArgb(),
                BottomPanelCraftDockStyle.CRAFT_BORDER_LIGHT.toArgb(),
                BottomPanelCraftDockStyle.CRAFT_BORDER_DARK.toArgb());
        canvas.centeredText("C",
                dock.craftButton.x + dock.craftButton.width / 2.0D,
                dock.craftButton.y + 13,
                UiMainlinePreviewStyle.color(BottomPanelCraftDockStyle.TEXT));
        for (int slot = 0; slot < dock.bindingCount; slot++) {
            BottomBarUiToolSlot binding = findGuiBinding(state, slot);
            boolean pending = binding != null && binding.pending;
            boolean bound = binding != null && binding.bound;
            int slotX = dock.slotX(slot);
            int slotY = dock.slotY(slot);
            UiMainlinePreviewStyle.frame(canvas,
                    new UiRect(slotX, slotY,
                            BottomPanelCraftDockLayout.BINDING_SLOT_SIZE,
                            BottomPanelCraftDockLayout.BINDING_SLOT_SIZE),
                    BottomPanelCraftDockStyle.slotBackground(
                            pending, bound, false).toArgb(),
                    BottomPanelCraftDockStyle.SLOT_BORDER_LIGHT.toArgb(),
                    BottomPanelCraftDockStyle.SLOT_BORDER_DARK.toArgb());
            if (bound && !pending && binding != null && !binding.itemId.isEmpty()) {
                drawItem(canvas, binding.itemId, slotX + 1, slotY + 1, 8);
            } else {
                canvas.centeredText(!bound || pending ? "+" : "•",
                        slotX + BottomPanelCraftDockLayout.BINDING_SLOT_SIZE / 2.0D,
                        slotY + 8,
                        UiMainlinePreviewStyle.color(BottomPanelCraftDockStyle.TEXT));
            }
        }
    }

    private void drawCategories(BufferedImageUiCanvas canvas, RtsMainlineLayout.BottomPanel p,
                                UiLanguageBundle language, BottomBarUiState state) {
        BottomPanelCategoryLayout layout = BottomPanelCategoryLayout.resolve(
                p.categoryX, p.categoryY, BottomPanelCategoryLayout.WIDTH,
                p.categoryH, state.categories.size(), state.categoryScroll);
        canvas.fill(rect(layout.panel),
                UiMainlinePreviewStyle.color(BottomPanelCategoryStyle.PANEL_BACKGROUND));
        canvas.centeredText(language.text("screen.rtsbuilding.storage.category"),
                layout.panel.x + layout.panel.width / 2.0D,
                layout.panel.y + 11,
                UiMainlinePreviewStyle.color(BottomPanelCategoryStyle.TITLE_TEXT));
        canvas.fill(rect(layout.scrollUp),
                UiMainlinePreviewStyle.color(BottomPanelCategoryStyle.SCROLL_BUTTON_BACKGROUND));
        canvas.fill(rect(layout.scrollDown),
                UiMainlinePreviewStyle.color(BottomPanelCategoryStyle.SCROLL_BUTTON_BACKGROUND));
        canvas.centeredText("^", layout.scrollUp.x + layout.scrollUp.width / 2.0D,
                layout.scrollUp.y + 9,
                UiMainlinePreviewStyle.color(BottomPanelCategoryStyle.TITLE_TEXT));
        canvas.centeredText("v", layout.scrollDown.x + layout.scrollDown.width / 2.0D,
                layout.scrollDown.y + 9,
                UiMainlinePreviewStyle.color(BottomPanelCategoryStyle.TITLE_TEXT));

        int to = Math.min(state.categories.size(), layout.scroll + layout.visibleCount());
        for (int index = layout.scroll; index < to; index++) {
            BottomBarUiCategory row = state.categories.get(index);
            BottomPanelCategoryLayout.Area rowArea = layout.rowArea(index);
            canvas.fill(rect(rowArea), UiMainlinePreviewStyle.color(
                    BottomPanelCategoryStyle.rowBackground(row.selected)));
            int labelLeft = layout.panel.x + BottomPanelCategoryLayout.TEXT_LEFT_INSET
                    + row.depth * BottomPanelCategoryLayout.DEPTH_INDENT;
            int labelRight = layout.panel.x + layout.panel.width
                    - BottomPanelCategoryLayout.TEXT_LEFT_INSET;
            if (row.expandable) {
                BottomPanelCategoryLayout.Area toggle = layout.toggleArea(index);
                canvas.fill(rect(toggle), UiMainlinePreviewStyle.color(
                        BottomPanelCategoryStyle.TOGGLE_BACKGROUND));
                canvas.centeredText(row.expanded ? "-" : "+",
                        toggle.x + toggle.width / 2.0D,
                        toggle.y + toggle.height,
                        UiMainlinePreviewStyle.color(BottomPanelCategoryStyle.TITLE_TEXT));
                labelRight = toggle.x - 3;
            }
            final int textLeft = labelLeft;
            final int textWidth = Math.max(8, labelRight - labelLeft);
            final String label = canvas.trimToWidth(row.label,
                    Math.max(8, (int) Math.floor(
                            textWidth / BottomPanelCategoryLayout.TEXT_SCALE)));
            final Color textColor = UiMainlinePreviewStyle.color(
                    BottomPanelCategoryStyle.rowText(row.selected));
            canvas.withFontSize(8.0F, new Runnable() {
                @Override
                public void run() {
                    canvas.centeredText(label,
                            textLeft + textWidth / 2.0D,
                            rowArea.y + rowArea.height - 1,
                            textColor);
                }
            });
        }
    }

    private void drawSearchPager(BufferedImageUiCanvas canvas, RtsMainlineLayout.BottomPanel p,
                                 BottomBarUiState state, UiPreviewScenario scenario) {
        BottomPanelBrowseLayout layout = BottomPanelBrowseLayout.resolve(
                p.storageX, p.storageY, p.searchW, p.pagerX);
        UiMainlinePreviewStyle.frame(canvas,
                rect(layout.searchField),
                BottomPanelBrowseStyle.searchBackground(state.searchFocused).toArgb(),
                BottomPanelBrowseStyle.SEARCH_BORDER_LIGHT.toArgb(),
                BottomPanelBrowseStyle.SEARCH_BORDER_DARK.toArgb());
        String value = state.search.isEmpty() ? "Search" : state.search;
        canvas.text(value, layout.searchField.x + 4, layout.searchField.y + 11,
                state.search.isEmpty()
                        ? UiMainlinePreviewStyle.color(BottomPanelBrowseStyle.PLACEHOLDER_TEXT)
                        : UiMainlinePreviewStyle.color(BottomPanelBrowseStyle.TEXT));
        if (scenario.variant() == UiPreviewScenario.Variant.ANIMATION_CARET_DAMAGE
                && UiBlink.caretVisible(new FixedUiClock(150L))) {
            int caretX = layout.searchField.x + 4 + canvas.textWidth(value) + 1;
            canvas.fill(new UiRect(caretX, layout.searchField.y + 2, 1,
                            Math.max(1, layout.searchField.height - 4)),
                    BottomPanelBrowseStyle.TEXT);
        }
        UiMainlinePreviewStyle.frame(
                canvas,
                rect(layout.clearSearch),
                BottomPanelBrowseStyle.clearBackground(state.searchFocused).toArgb(),
                BottomPanelBrowseStyle.CLEAR_BORDER_LIGHT.toArgb(),
                BottomPanelBrowseStyle.CLEAR_BORDER_DARK.toArgb());
        canvas.centeredText("x",
                layout.clearSearch.x + layout.clearSearch.width / 2.0D,
                layout.clearSearch.y + layout.clearSearch.height - 2,
                UiMainlinePreviewStyle.color(
                        BottomPanelBrowseStyle.clearText(!state.search.isEmpty())));
        canvas.fill(rect(layout.previousPage),
                UiMainlinePreviewStyle.color(BottomPanelBrowseStyle.PAGE_BUTTON_BACKGROUND));
        canvas.fill(rect(layout.nextPage),
                UiMainlinePreviewStyle.color(BottomPanelBrowseStyle.PAGE_BUTTON_BACKGROUND));
        canvas.centeredText("<",
                layout.previousPage.x + layout.previousPage.width / 2.0D,
                layout.previousPage.y + layout.previousPage.height - 3,
                UiMainlinePreviewStyle.color(BottomPanelBrowseStyle.TEXT));
        canvas.text((state.page + 1) + "/" + state.pageCount,
                layout.pageTextX(), layout.previousPage.y + 11,
                UiMainlinePreviewStyle.color(BottomPanelBrowseStyle.TEXT));
        canvas.centeredText(">",
                layout.nextPage.x + layout.nextPage.width / 2.0D,
                layout.nextPage.y + layout.nextPage.height - 3,
                UiMainlinePreviewStyle.color(BottomPanelBrowseStyle.TEXT));
    }

    private void drawToolRow(BufferedImageUiCanvas canvas, RtsMainlineLayout.BottomPanel p,
                             BottomBarUiState state) {
        int totalPins = countToolSlots(state, BottomBarUiToolSlot.Kind.PINNED);
        BottomPanelToolLayout layout = BottomPanelToolLayout.standard(
                p.storageX, p.toolY, p.mainStorageW, totalPins, state.pinPage);
        int size = layout.slotSize();
        for (int cell = 0; cell < layout.hotbarCellCount(); cell++) {
            int x = layout.hotbarCellX(cell);
            boolean emptyHand = cell == BottomPanelToolLayout.EMPTY_HAND_INDEX;
            BottomBarUiToolSlot slot = findToolSlot(
                    state,
                    emptyHand ? BottomBarUiToolSlot.Kind.EMPTY_HAND
                            : BottomBarUiToolSlot.Kind.HOTBAR,
                    cell);
            UiMainlinePreviewStyle.frame(
                    canvas,
                    new UiRect(x, layout.y(), size, size),
                    BottomPanelToolStyle.hotbarBackground(
                            emptyHand, slot != null && slot.selected).toArgb(),
                    BottomPanelToolStyle.hotbarBorderLight(emptyHand).toArgb(),
                    BottomPanelToolStyle.BORDER_DARK.toArgb());
            if (emptyHand) {
                int markSize = 10;
                int inset = (size - markSize) / 2;
                canvas.fill(
                        new UiRect(x + inset, layout.y() + inset, markSize, markSize),
                        UiMainlinePreviewStyle.color(BottomPanelToolStyle.EMPTY_HAND_MARK));
            } else if (slot != null && !slot.itemId.isEmpty()) {
                drawItem(canvas, slot.itemId, x + 1, layout.y() + 1, 16);
            }
        }

        for (int cell = 0; cell < layout.visiblePinCells(); cell++) {
            int x = layout.pinCellX(cell);
            boolean pager = layout.isPinPagerCell(cell);
            int pinIndex = layout.pinIndexForCell(cell);
            BottomBarUiToolSlot pin = findToolSlot(
                    state, BottomBarUiToolSlot.Kind.PINNED, pinIndex);
            boolean filled = !pager && pin != null && !pin.itemId.isEmpty();
            UiMainlinePreviewStyle.frame(
                    canvas,
                    new UiRect(x, layout.y(), size, size),
                    BottomPanelToolStyle.pinBackground(filled).toArgb(),
                    BottomPanelToolStyle.PIN_BORDER_LIGHT.toArgb(),
                    BottomPanelToolStyle.BORDER_DARK.toArgb());
            if (pager) {
                canvas.fill(
                        new UiRect(x + 1, layout.y() + 1, size - 2, size - 2),
                        UiMainlinePreviewStyle.color(BottomPanelToolStyle.PIN_PAGER_OVERLAY));
                canvas.centeredText(
                        "+", x + size / 2.0D, layout.y() + size - 5,
                        UiMainlinePreviewStyle.color(BottomPanelToolStyle.PIN_PAGER_TEXT));
            } else if (filled) {
                drawItem(canvas, pin.itemId, x + 1, layout.y() + 1, 16);
                if (pin.selected) {
                    canvas.fill(
                            new UiRect(x + 1, layout.y() + 1, size - 2, size - 2),
                            UiMainlinePreviewStyle.color(BottomPanelToolStyle.SELECTED_OVERLAY));
                }
                drawCount(
                        canvas, x, layout.y(),
                        BottomBarUiFormats.compactCount(pin.amount),
                        BottomPanelToolStyle.pinCount(pin.amount).toArgb());
            } else if (pinIndex >= 0) {
                canvas.centeredText(
                        Integer.toString(pinIndex + 1),
                        x + size / 2.0D,
                        layout.y() + size - 5,
                        UiMainlinePreviewStyle.color(BottomPanelToolStyle.PIN_INDEX_TEXT));
            }
        }
    }

    private void drawStorageGrids(BufferedImageUiCanvas canvas, RtsMainlineLayout.BottomPanel p,
                                  BottomBarUiState state, UiLanguageBundle language) {
        if (state.activeTab == BottomBarUiTab.CREATIVE) {
            BottomPanelGridLayout.Layout grids = BottomPanelGridLayout.creative(
                    p.storageX, p.gridY, p.mainStorageW, p.gridH, 22, 6);
            drawEntryGrid(canvas, state.creativeEntries,
                    BottomPanelGridLayout.resolve(grids.main, 22, 20,
                            state.creativeEntries.size(), 0),
                    BottomPanelGridStyle.CREATIVE);
            drawEntryGrid(canvas, state.recentEntries,
                    BottomPanelGridLayout.resolve(grids.recent, 22, 20,
                            state.recentEntries.size(), 0),
                    BottomPanelGridStyle.RECENT);
            return;
        }

        int fluidW = p.mainStorageW >= 44 + 66 ? 44 : 0;
        BottomPanelGridLayout.Layout grids = BottomPanelGridLayout.storage(
                p.storageX, p.gridY, p.mainStorageW, p.gridH, 22, 6, fluidW, 4);
        if (!grids.fluid.isEmpty()) {
            drawEntryGrid(canvas, state.fluidEntries,
                    BottomPanelGridLayout.resolve(grids.fluid, 22, 20,
                            state.fluidEntries.size(), 0),
                    BottomPanelGridStyle.FLUID);
        }
        BottomPanelGridLayout.GridView storageView = BottomPanelGridLayout.resolve(
                grids.main, 22, 20, state.storageEntries.size(), 0);
        drawEntryGrid(canvas, state.storageEntries, storageView,
                BottomPanelGridStyle.STORAGE);
        drawEntryGrid(canvas, state.recentEntries,
                BottomPanelGridLayout.resolve(grids.recent, 22, 20,
                        state.recentEntries.size(), 0),
                BottomPanelGridStyle.RECENT);
        if (state.storageEntries.isEmpty()) {
            String key = state.storageLinked
                    ? "screen.rtsbuilding.storage.empty_linked"
                    : "screen.rtsbuilding.storage.empty_unlinked";
            double centerX = grids.main.x + grids.main.width / 2.0D;
            int centerY = grids.main.y + Math.max(8, grids.main.height / 2 - 10);
            canvas.centeredText(language.text(key), centerX, centerY + 8,
                    UiMainlinePreviewStyle.color(BottomPanelGridStyle.EMPTY_TITLE));
            canvas.centeredText(language.text(key + ".detail"), centerX, centerY + 20,
                    UiMainlinePreviewStyle.color(BottomPanelGridStyle.EMPTY_DETAIL));
        }
    }

    /** 只遍历当前屏幕可见格；2000 项压力场景不会扫描不可见尾部。 */
    private void drawEntryGrid(BufferedImageUiCanvas canvas, java.util.List<BottomBarUiEntry> entries,
                               BottomPanelGridLayout.GridView view,
                               BottomPanelGridStyle.Visual style) {
        canvas.recordScannedItems(Math.min(view.capacity,
                Math.max(0, entries.size() - view.startIndex)));
        for (int row = 0; row < view.rows; row++) {
            for (int column = 0; column < view.columns; column++) {
                int slotX = view.slotX(column);
                int slotY = view.slotY(row);
                int index = view.entryIndex(row, column);
                BottomBarUiEntry entry = index >= 0 && index < entries.size()
                        ? entries.get(index) : null;
                UiMainlinePreviewStyle.frame(canvas,
                        new UiRect(slotX, slotY, view.slotExtent, view.slotExtent),
                        style.background.toArgb(), style.borderLight.toArgb(),
                        style.borderDark.toArgb());
                if (entry == null) continue;
                if (entry.selected) {
                    canvas.fill(new UiRect(slotX + 1, slotY + 1,
                                    view.slotExtent - 2, view.slotExtent - 2),
                            UiMainlinePreviewStyle.color(style.selectedOverlay));
                }
                drawItem(canvas, entry.id, slotX + 2, slotY + 2, 16);
                String amount = entry.kind == BottomBarUiEntry.Kind.FLUID
                        || entry.kind == BottomBarUiEntry.Kind.RECENT_FLUID
                        ? BottomBarUiFormats.compactFluidAmount(entry.amount)
                        : BottomBarUiFormats.compactCount(entry.amount);
                int countColor = entry.kind == BottomBarUiEntry.Kind.RECENT_FLUID
                        ? BottomPanelGridStyle.RECENT_FLUID_COUNT.toArgb()
                        : style.countText.toArgb();
                drawCount(canvas, slotX, slotY, amount, countColor);
            }
        }
    }

    private void drawCraftPanel(BufferedImageUiCanvas canvas, RtsMainlineLayout.BottomPanel p,
                                BottomBarUiState state) {
        BottomPanelCraftLayout layout = BottomPanelCraftLayout.resolve(
                p.craftPanelX, p.craftPanelY, RtsMainlineLayout.CRAFT_PANEL_W,
                p.craftPanelH, state.craftableEntries.size(), state.craftScroll);
        UiMainlinePreviewStyle.frame(canvas,
                rect(layout.panel),
                BottomPanelCraftStyle.PANEL_BACKGROUND.toArgb(),
                BottomPanelCraftStyle.PANEL_BORDER_LIGHT.toArgb(),
                BottomPanelCraftStyle.PANEL_BORDER_DARK.toArgb());
        canvas.text("Craft", layout.panel.x + 5, layout.panel.y + 11,
                UiMainlinePreviewStyle.color(BottomPanelCraftStyle.TITLE));
        UiMainlinePreviewStyle.frame(canvas, rect(layout.search),
                BottomPanelCraftStyle.SEARCH_BACKGROUND.toArgb(),
                BottomPanelCraftStyle.SEARCH_BORDER_LIGHT.toArgb(),
                BottomPanelCraftStyle.SEARCH_BORDER_DARK.toArgb());
        if (!state.craftSearchDraft.isEmpty()) canvas.text(state.craftSearchDraft,
                layout.search.x + 2, layout.search.y + 10, Color.WHITE);
        drawCraftButton(canvas, layout.apply, "OK",
                BottomPanelCraftStyle.applyBackground(state.craftSearchDirty()).toArgb(),
                BottomPanelCraftStyle.BUTTON_BORDER_LIGHT.toArgb());
        drawCraftButton(canvas, layout.toggle,
                state.craftShowUnavailable ? "ALL" : "MAKE",
                BottomPanelCraftStyle.toggleBackground(state.craftShowUnavailable).toArgb(),
                BottomPanelCraftStyle.TOGGLE_BORDER_LIGHT.toArgb());

        for (int row = 0; row < layout.visibleRows; row++) {
            for (int col = 0; col < RtsMainlineLayout.CRAFT_PANEL_COLS; col++) {
                int index = layout.startIndex + row * RtsMainlineLayout.CRAFT_PANEL_COLS + col;
                int x = layout.slotX(col);
                int y = layout.slotY(row);
                BottomBarUiEntry entry = index < state.craftableEntries.size()
                        ? state.craftableEntries.get(index) : null;
                UiMainlinePreviewStyle.frame(canvas,
                        new UiRect(x, y, RtsMainlineLayout.CRAFT_PANEL_SLOT,
                                RtsMainlineLayout.CRAFT_PANEL_SLOT),
                        BottomPanelCraftStyle.slotBackground(
                                entry != null, entry != null && entry.available).toArgb(),
                        BottomPanelCraftStyle.SLOT_BORDER_LIGHT.toArgb(),
                        BottomPanelCraftStyle.SLOT_BORDER_DARK.toArgb());
                if (entry != null) drawItem(canvas, entry.id, x + 1, y + 1, 16);
            }
        }
    }

    private void drawCraftButton(BufferedImageUiCanvas canvas,
                                 BottomPanelCraftLayout.Area area,
                                 String label, int background, int lightBorder) {
        UiMainlinePreviewStyle.frame(canvas, rect(area), background, lightBorder,
                BottomPanelCraftStyle.BUTTON_BORDER_DARK.toArgb());
        canvas.centeredText(label, area.x + area.width / 2.0D, area.y + area.height - 2,
                UiMainlinePreviewStyle.color(BottomPanelCraftStyle.BUTTON_TEXT));
    }

    private static UiRect rect(BottomPanelCraftLayout.Area area) {
        return new UiRect(area.x, area.y, area.width, area.height);
    }

    private static UiRect rect(BottomPanelCategoryLayout.Area area) {
        return new UiRect(area.x, area.y, area.width, area.height);
    }

    private static UiRect rect(BottomPanelBrowseLayout.Area area) {
        return new UiRect(area.x, area.y, area.width, area.height);
    }

    private void drawSortButton(
            BufferedImageUiCanvas canvas,
            BottomPanelSortLayout.Area area,
            String label) {
        UiMainlinePreviewStyle.frame(
                canvas,
                new UiRect(area.x, area.y, area.width, area.height),
                BottomPanelSortStyle.BUTTON_BACKGROUND.toArgb(),
                BottomPanelSortStyle.BUTTON_BORDER_LIGHT.toArgb(),
                BottomPanelSortStyle.BUTTON_BORDER_DARK.toArgb());
        canvas.centeredText(
                label,
                area.x + area.width / 2.0D,
                area.y + area.height - 3,
                UiMainlinePreviewStyle.color(BottomPanelSortStyle.BUTTON_TEXT));
    }

    private void drawItem(BufferedImageUiCanvas canvas, String itemId,
                          int x, int y, int size) {
        String name = itemId == null ? "" : itemId;
        int colon = name.indexOf(':');
        if (colon >= 0) name = name.substring(colon + 1);
        BufferedImage image = assets.item(name);
        canvas.image(image, new UiRect(x, y, size, size));
    }

    private static BottomBarUiToolSlot findToolSlot(BottomBarUiState state,
            BottomBarUiToolSlot.Kind kind, int sourceIndex) {
        for (BottomBarUiToolSlot slot : state.toolSlots) {
            if (slot.kind == kind && slot.sourceIndex == sourceIndex) return slot;
        }
        return null;
    }

    private static int countToolSlots(
            BottomBarUiState state, BottomBarUiToolSlot.Kind kind) {
        int count = 0;
        for (BottomBarUiToolSlot slot : state.toolSlots) {
            if (slot.kind == kind) {
                count++;
            }
        }
        return count;
    }

    private static BottomBarUiToolSlot findGuiBinding(
            BottomBarUiState state, int sourceIndex) {
        for (BottomBarUiToolSlot slot : state.guiBindings) {
            if (slot.kind == BottomBarUiToolSlot.Kind.GUI_BINDING
                    && slot.sourceIndex == sourceIndex) {
                return slot;
            }
        }
        return null;
    }

    private void drawCount(BufferedImageUiCanvas canvas, int x, int y, String count) {
        drawCount(canvas, x, y, count, BottomPanelGridStyle.STORAGE.countText.toArgb());
    }

    private void drawCount(BufferedImageUiCanvas canvas, int x, int y,
                           String count, int color) {
        canvas.withFontSize(7.0F, new Runnable() {
            @Override
            public void run() {
                canvas.text(count, x + 20 - canvas.textWidth(count), y + 20,
                        UiMainlinePreviewStyle.color(color));
            }
        });
    }
}
