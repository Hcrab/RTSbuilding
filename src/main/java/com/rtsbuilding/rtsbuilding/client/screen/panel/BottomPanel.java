package com.rtsbuilding.rtsbuilding.client.screen.panel;


import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.popup.RtsCraftFeedbackPopup;
import com.rtsbuilding.rtsbuilding.client.popup.RtsCraftQuantityDialog;
import com.rtsbuilding.rtsbuilding.client.screen.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.screen.layout.BottomPanelLayoutTypes;
import com.rtsbuilding.rtsbuilding.client.screen.layout.CategoryTypes;
import com.rtsbuilding.rtsbuilding.client.screen.layout.PanelLayouts;
import com.rtsbuilding.rtsbuilding.client.state.RtsClientUiStateStore;
import com.rtsbuilding.rtsbuilding.client.util.RtsClientUiUtil;
import com.rtsbuilding.rtsbuilding.client.util.RtsCraftablesUiHelper;
import com.rtsbuilding.rtsbuilding.client.util.RtsCreativeItemCatalog;
import com.rtsbuilding.rtsbuilding.server.storage.RecentEntry;
import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.blueprint.client.BlueprintPanel;
import com.rtsbuilding.rtsbuilding.network.storage.RtsStorageSort;
import com.rtsbuilding.rtsbuilding.progression.RtsProgressionNodes;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraftforge.fml.ModList;

import java.util.*;

import static com.rtsbuilding.rtsbuilding.client.screen.BuilderScreenConstants.*;

/**
 * 搴曢儴闈㈡澘 ??鍌ㄥ瓨缃戞牸銆佸垎绫汇€佸悎鎴愩€佹祦浣撱€佽摑鍥剧殑闆嗕腑 UI??
 * <p>
 * ??{@link BuilderScreen} 缁熶竴璋冨害鐢熷懡鍛ㄦ湡??
 */
public final class BottomPanel {

    // 鈹€鈹€ 鐘舵??鈹€鈹€
    private BuilderScreen screen;
    private ClientRtsController controller;

    public BottomPanelLayoutTypes.BottomPanelTab bottomPanelTab = BottomPanelLayoutTypes.BottomPanelTab.STORAGE;
    public int pinPage = 0;
    public int categoryScroll = 0;
    public int craftScroll = 0;
    public final Set<String> expandedCategoryMods = new HashSet<>();
    public final RtsCraftQuantityDialog craftQuantityDialog = new RtsCraftQuantityDialog();

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
    private String creativeCategory = "all";
    private String creativeSearch = "";
    private int creativePage = 0;

    public void init(BuilderScreen screen, ClientRtsController controller) {
        this.screen = screen;
        this.controller = controller;
    }

    // 鈹€鈹€ 娓叉??鈹€鈹€

    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        BottomPanelLayoutTypes.BottomPanelLayout layout = resolveBottomPanelLayout();
        BottomPanelLayoutTypes.BottomPanelTab activeTab = activeBottomPanelTab();
        int bottomH = layout.panelH();
        int bottomY = layout.panelY();
        int sortX = layout.sortX();
        int sortY = layout.sortY();

        RtsClientUiUtil.drawPanelFrame(g, layout.panelX(), layout.panelY(), layout.panelW(), layout.panelH(),
                0xD014151A, 0xFF64788E, 0xFF0D1015);
        g.fill(layout.panelX() + 1, layout.panelY() + 1, layout.panelX() + layout.panelW() - 1, layout.panelY() + BOTTOM_PANEL_HEADER_H,
                0xCC1C242F);
        renderBottomPanelTabs(g, layout, mouseX, mouseY);
        int refreshX = bottomRefreshButtonX(layout);
        int refreshY = bottomGuideButtonY(layout);
        boolean refreshHover = inside(mouseX, mouseY, refreshX, refreshY, 12, 12);
        boolean refreshDirty = activeTab == BottomPanelLayoutTypes.BottomPanelTab.STORAGE
                && !this.controller.isStorageScanRunning()
                && this.controller.shouldHighlightStorageRefresh();
        int refreshBg = this.controller.isStorageScanRunning()
                ? 0xCC3F627E
                : refreshDirty ? (refreshHover ? 0xDD2FAF49 : 0xCC248C3A)
                : refreshHover ? 0xCC41576F : 0xAA2B3542;
        g.fill(refreshX, refreshY, refreshX + 12, refreshY + 12, refreshBg);
        if (refreshDirty) {
            g.fill(refreshX, refreshY, refreshX + 12, refreshY + 1, 0xFF92F7A0);
            g.fill(refreshX, refreshY + 11, refreshX + 12, refreshY + 12, 0xFF92F7A0);
            g.fill(refreshX, refreshY, refreshX + 1, refreshY + 12, 0xFF92F7A0);
            g.fill(refreshX + 11, refreshY, refreshX + 12, refreshY + 12, 0xFF92F7A0);
        }
        g.drawCenteredString(screen.font(), "R", refreshX + 6, refreshY + 2, refreshDirty ? 0xFFFFFFFF : 0xEAF4FF);
        int guideX = bottomGuideButtonX(layout);
        int guideY = bottomGuideButtonY(layout);
        boolean guideHover = inside(mouseX, mouseY, guideX, guideY, 12, 12);
        g.fill(guideX, guideY, guideX + 12, guideY + 12, guideHover ? 0xCC41576F : 0xAA2B3542);
        g.drawCenteredString(screen.font(), "i", guideX + 6, guideY + 2, 0xEAF4FF);

        if (activeTab == BottomPanelLayoutTypes.BottomPanelTab.BLUEPRINTS) {
            int contentX = layout.panelX() + BOTTOM_PANEL_PADDING;
            int contentY = layout.panelY() + BOTTOM_PANEL_HEADER_H + 4;
            int contentW = Math.max(80, layout.panelW() - BOTTOM_PANEL_PADDING * 2);
            int contentH = Math.max(24, layout.panelH() - BOTTOM_PANEL_HEADER_H - 8);
            BlueprintPanel.render(g, screen.font(), this.controller, contentX, contentY, contentW, contentH, mouseX, mouseY);
            return;
        }

        drawSortButton(g, sortX, sortY, "S");
        drawSortButton(g, sortX, sortY + SORT_BUTTON_SIZE + 4, this.controller.isStorageSortAscending() ? "A" : "D");
        g.drawString(screen.font(), sortLabel(this.controller.getStorageSort()), sortX + SORT_BUTTON_SIZE + 4, sortY + 6, 0xFFFFFF);
        drawSortButton(g, sortX + SORT_BUTTON_SIZE + 26, sortY, "+");
        drawSortButton(g, sortX + SORT_BUTTON_SIZE + 26, sortY + SORT_BUTTON_SIZE + 4, "-");
        drawCraftDock(g, mouseX, mouseY, sortX, sortY + (SORT_BUTTON_SIZE + 4) * 2);

        int categoryX = layout.categoryX();
        int categoryY = layout.categoryY();
        int categoryH = layout.categoryH();
        drawCategoryPanel(g, mouseX, mouseY, categoryX, categoryY, CATEGORY_W, categoryH);

        int storageX = layout.storageX();
        int storageY = layout.storageY();
        int storageW = layout.storageW();
        int craftPanelX = layout.craftPanelX();
        int mainStorageW = layout.mainStorageW();
        int searchW = layout.searchW();
        int searchFieldW = computeSearchFieldWidth(searchW);

        if (screen.getSearchBox() != null) {
            if (!screen.getSearchBox().isFocused()) {
                syncSearchBoxForActiveTab();
            }
            var sb = screen.getSearchBox();
            sb.setX(storageX);
            sb.setY(storageY);
            sb.setWidth(searchFieldW);
            sb.setHeight(14);
            sb.render(g, mouseX, mouseY, partialTick);
            drawSearchClearButton(g, storageX, storageY, searchW);
        }

        int pagerX = layout.pagerX();
        drawPager(g, pagerX, storageY, layout);

        renderToolArea(g, mouseX, mouseY, storageX, layout.toolY(), mainStorageW);

        int gridY = layout.gridY();
        int gridH = layout.gridH();
        int craftPanelY = layout.craftPanelY();
        int craftPanelH = layout.craftPanelH();
        if (activeTab == BottomPanelLayoutTypes.BottomPanelTab.CREATIVE) {
            int creativeGridW = creativeGridWidth(mainStorageW);
            int recentGridX = storageX + creativeGridW + STORAGE_RECENT_GAP;
            int recentGridW = Math.max(SLOT, mainStorageW - creativeGridW - STORAGE_RECENT_GAP);
            drawCreativeGrid(g, mouseX, mouseY, storageX, gridY, creativeGridW, gridH);
            drawRecentGrid(g, mouseX, mouseY, recentGridX, gridY, recentGridW, gridH);
            return;
        }
        int fluidW = getFluidStripWidth(mainStorageW);
        int itemGridX = storageX;
        int itemGridW = mainStorageW;
        if (fluidW > 0) {
            drawFluidGrid(g, mouseX, mouseY, storageX, gridY, fluidW, gridH);
            itemGridX = storageX + fluidW + 4;
            itemGridW = Math.max(SLOT, mainStorageW - fluidW - 4);
        }
        int storageGridW = Math.max(SLOT, (itemGridW - STORAGE_RECENT_GAP) / 2);
        int recentGridX = itemGridX + storageGridW + STORAGE_RECENT_GAP;
        int recentGridW = Math.max(SLOT, itemGridW - storageGridW - STORAGE_RECENT_GAP);
        drawStorageGrid(g, mouseX, mouseY, itemGridX, gridY, storageGridW, gridH);
        drawRecentGrid(g, mouseX, mouseY, recentGridX, gridY, recentGridW, gridH);
        renderCraftablesPanel(g, mouseX, mouseY, craftPanelX, craftPanelY, CRAFT_PANEL_W, craftPanelH, partialTick);
    }

    public void renderCraftFeedback(GuiGraphics g) {
        RtsCraftFeedbackPopup.render(g, screen.font(), screen.width, this.controller);
    }

    // 鈹€鈹€ 鏍囩椤垫覆??鈹€鈹€

    private void renderBottomPanelTabs(GuiGraphics g, BottomPanelLayoutTypes.BottomPanelLayout layout, int mouseX, int mouseY) {
        int labelX = layout.panelX() + 8;
        int labelY = layout.panelY() + 5;
        g.drawString(screen.font(), "RTS", labelX, labelY, 0xF2F6FB);
        for (BottomPanelLayoutTypes.BottomPanelTab tab : visibleBottomPanelTabs()) {
            drawBottomPanelTab(g, layout, tab, bottomPanelTabLabel(tab), mouseX, mouseY);
        }
        drawSelectedPlacementStatus(g, layout);
    }

    private void drawBottomPanelTab(
            GuiGraphics g,
            BottomPanelLayoutTypes.BottomPanelLayout layout,
            BottomPanelLayoutTypes.BottomPanelTab tab,
            String label,
            int mouseX,
            int mouseY) {
        int x = bottomPanelTabX(layout, tab);
        int y = layout.panelY() + 2;
        int w = bottomPanelTabW(tab);
        boolean active = activeBottomPanelTab() == tab;
        boolean hover = inside(mouseX, mouseY, x, y, w, BOTTOM_PANEL_HEADER_H - 3);
        int fill = active ? 0xCC355B4C : hover ? 0xAA334052 : 0x8826303B;
        RtsClientUiUtil.drawPanelFrame(g, x, y, w, BOTTOM_PANEL_HEADER_H - 3, fill,
                active ? 0xFF7CCB93 : 0xFF536679, 0xFF0D1015);
        g.drawCenteredString(screen.font(), screen.trimToWidth(label, w - 8), x + w / 2, y + 4,
                active ? 0xFFFFFFFF : 0xFFD8E2EE);
    }

    private int bottomPanelTabX(BottomPanelLayoutTypes.BottomPanelLayout layout, BottomPanelLayoutTypes.BottomPanelTab tab) {
        int x = layout.panelX() + 38;
        for (BottomPanelLayoutTypes.BottomPanelTab visible : visibleBottomPanelTabs()) {
            if (visible == tab) {
                return x;
            }
            x += bottomPanelTabW(visible) + 4;
        }
        return x;
    }

    private void drawSelectedPlacementStatus(GuiGraphics g, BottomPanelLayoutTypes.BottomPanelLayout layout) {
        int x = selectedPlacementStatusX(layout);
        int w = selectedPlacementStatusW(layout);
        if (x < 0 || w <= 0) {
            return;
        }
        int y = layout.panelY() + 6;
        g.drawString(screen.font(), screen.trimToWidth(selectedPlacementStatusText(), w), x, y, 0xFFD8E2EE);
    }

    private int selectedPlacementStatusX(BottomPanelLayoutTypes.BottomPanelLayout layout) {
        List<BottomPanelLayoutTypes.BottomPanelTab> visibleTabs = visibleBottomPanelTabs();
        BottomPanelLayoutTypes.BottomPanelTab lastTab = visibleTabs.isEmpty()
                ? BottomPanelLayoutTypes.BottomPanelTab.STORAGE
                : visibleTabs.get(visibleTabs.size() - 1);
        return bottomPanelTabX(layout, lastTab) + bottomPanelTabW(lastTab) + 10;
    }

    private int selectedPlacementStatusW(BottomPanelLayoutTypes.BottomPanelLayout layout) {
        int x = selectedPlacementStatusX(layout);
        int reservedRight = 44;
        int available = Math.max(0, layout.panelX() + layout.panelW() - x - reservedRight);
        if (available <= 0) {
            return 0;
        }
        int desired = screen.font().width(selectedPlacementStatusText()) + 8;
        int max = Math.min(190, available);
        return Mth.clamp(desired, Math.min(96, max), max);
    }

    private String selectedPlacementStatusText() {
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

    private int bottomPanelTabW(BottomPanelLayoutTypes.BottomPanelTab tab) {
        if (tab == BottomPanelLayoutTypes.BottomPanelTab.CREATIVE) {
            return 58;
        }
        return tab == BottomPanelLayoutTypes.BottomPanelTab.STORAGE ? 76 : 86;
    }

    private List<BottomPanelLayoutTypes.BottomPanelTab> visibleBottomPanelTabs() {
        boolean showBlueprints = hasBlueprintAccess();
        if (isCreativePlayer()) {
            return showBlueprints
                    ? List.of(
                            BottomPanelLayoutTypes.BottomPanelTab.CREATIVE,
                            BottomPanelLayoutTypes.BottomPanelTab.STORAGE,
                            BottomPanelLayoutTypes.BottomPanelTab.BLUEPRINTS)
                    : List.of(
                            BottomPanelLayoutTypes.BottomPanelTab.CREATIVE,
                            BottomPanelLayoutTypes.BottomPanelTab.STORAGE);
        }
        return showBlueprints
                ? List.of(BottomPanelLayoutTypes.BottomPanelTab.STORAGE, BottomPanelLayoutTypes.BottomPanelTab.BLUEPRINTS)
                : List.of(BottomPanelLayoutTypes.BottomPanelTab.STORAGE);
    }

    private BottomPanelLayoutTypes.BottomPanelTab activeBottomPanelTab() {
        if (this.bottomPanelTab == BottomPanelLayoutTypes.BottomPanelTab.CREATIVE && !isCreativePlayer()) {
            return BottomPanelLayoutTypes.BottomPanelTab.STORAGE;
        }
        if (this.bottomPanelTab == BottomPanelLayoutTypes.BottomPanelTab.BLUEPRINTS && !hasBlueprintAccess()) {
            return BottomPanelLayoutTypes.BottomPanelTab.STORAGE;
        }
        return this.bottomPanelTab;
    }

    private boolean hasBlueprintAccess() {
        return Config.areBlueprintsEnabled() && screen.hasProgressionNode(RtsProgressionNodes.BLUEPRINTS);
    }

    private boolean isCreativePlayer() {
        Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.player != null && mc.player.isCreative();
    }

    private String bottomPanelTabLabel(BottomPanelLayoutTypes.BottomPanelTab tab) {
        if (tab == BottomPanelLayoutTypes.BottomPanelTab.CREATIVE) {
            return Component.translatable("screen.rtsbuilding.creative.tab").getString();
        }
        if (tab == BottomPanelLayoutTypes.BottomPanelTab.BLUEPRINTS) {
            return Component.translatable("screen.rtsbuilding.blueprints.tab").getString();
        }
        return Component.translatable("screen.rtsbuilding.storage.tab").getString();
    }

    // 鈹€鈹€ 宸ュ叿鏍?鈹溾攢鈹??鐑敭鏍?鍥哄畾浣?鈹€鈹€

    private void renderToolArea(GuiGraphics g, int mouseX, int mouseY, int storageX, int rowY, int storageW) {
        if (Minecraft.getInstance() == null || Minecraft.getInstance().player == null) {
            return;
        }

        int hotbarX = storageX;
        int hotbarW = getHotbarSlotsWidth();
        int selectedToolSlot = getSelectedToolSlot();
        int selected = (this.controller.hasSelectedItem()
                || this.controller.hasSelectedFluid()
                || this.controller.isEmptyHandSelected()
                || selectedToolSlot >= TOOL_HOTBAR_ITEM_SLOTS) ? -1 : selectedToolSlot;

        for (int i = 0; i <= EMPTY_HAND_BUTTON_INDEX; i++) {
            int cx = hotbarX + i * HOTBAR_PITCH;
            int cy = rowY;
            boolean emptyHandButton = i == EMPTY_HAND_BUTTON_INDEX;
            int bg = emptyHandButton
                    ? (this.controller.isEmptyHandSelected() ? 0xCC9B604B : 0xB06F5146)
                    : (i == selected ? 0xCC3A6E57 : 0xAA1B1E25);
            g.fill(cx, cy, cx + HOTBAR_SLOT, cy + HOTBAR_SLOT, bg);
            g.hLine(cx, cx + HOTBAR_SLOT, cy, emptyHandButton ? 0xFFFFD0B0 : 0xFF5E6874);
            g.hLine(cx, cx + HOTBAR_SLOT, cy + HOTBAR_SLOT, 0xFF0C0D10);
            g.vLine(cx, cy, cy + HOTBAR_SLOT, emptyHandButton ? 0xFFFFD0B0 : 0xFF5E6874);
            g.vLine(cx + HOTBAR_SLOT, cy, cy + HOTBAR_SLOT, 0xFF0C0D10);

            if (emptyHandButton) {
                drawEmptyHandButton(g, cx, cy);
            } else {
                var stack = Minecraft.getInstance().player.getInventory().getItem(i);
                if (!stack.isEmpty()) {
                    g.renderItem(stack, cx + 1, cy + 1);
                    g.renderItemDecorations(screen.font(), stack, cx + 1, cy + 1);
                }
            }
            if (mouseX >= cx && mouseX <= cx + HOTBAR_SLOT && mouseY >= cy && mouseY <= cy + HOTBAR_SLOT) {
                g.fill(cx + 1, cy + 1, cx + HOTBAR_SLOT - 1, cy + HOTBAR_SLOT - 1, 0x22FFFFFF);
                if (emptyHandButton) {
                    this.hoveredEmptyHandSlot = true;
                } else {
                    this.hoveredToolSlot = i;
                }
            }
        }

        int pinStartX = hotbarX + hotbarW + 12;
        int pinVisibleCells = computeVisiblePinCells(pinStartX, storageX + storageW);
        if (pinVisibleCells <= 0) {
            return;
        }

        int totalPins = this.controller.getQuickSlotCount();
        boolean usePager = shouldUsePinPager(pinVisibleCells, totalPins);
        int slotsPerPage = computePinSlotsPerPage(pinVisibleCells, totalPins);
        int pageCount = Math.max(1, (int) Math.ceil(totalPins / (double) slotsPerPage));
        this.pinPage = Mth.clamp(this.pinPage, 0, pageCount - 1);
        int pinStartIndex = this.pinPage * slotsPerPage;

        for (int cell = 0; cell < pinVisibleCells; cell++) {
            int cx = pinStartX + cell * HOTBAR_PITCH;
            int cy = rowY;
            boolean pageButton = usePager && cell == pinVisibleCells - 1;
            int pinIndex = pinStartIndex + cell;
            boolean filled = !pageButton
                    && pinIndex < totalPins
                    && !this.controller.getQuickSlotItemId(pinIndex).isBlank();
            int bg = filled ? 0xAA253043 : 0xAA1A1A1A;
            g.fill(cx, cy, cx + HOTBAR_SLOT, cy + HOTBAR_SLOT, bg);
            g.hLine(cx, cx + HOTBAR_SLOT, cy, 0xFF67758A);
            g.hLine(cx, cx + HOTBAR_SLOT, cy + HOTBAR_SLOT, 0xFF0C0D10);
            g.vLine(cx, cy, cy + HOTBAR_SLOT, 0xFF67758A);
            g.vLine(cx + HOTBAR_SLOT, cy, cy + HOTBAR_SLOT, 0xFF0C0D10);

            if (pageButton) {
                g.fill(cx + 1, cy + 1, cx + HOTBAR_SLOT - 1, cy + HOTBAR_SLOT - 1, 0xAA2C3A26);
                g.drawCenteredString(screen.font(), "+", cx + HOTBAR_SLOT / 2, cy + 5, 0xE9F7DA);
            } else if (pinIndex < totalPins) {
                var preview = this.controller.getQuickSlotPreview(pinIndex);
                if (!preview.isEmpty()) {
                    g.renderItem(preview, cx + 1, cy + 1);
                    if (this.controller.getQuickSlotItemId(pinIndex).equals(this.controller.getSelectedItemId())) {
                        g.fill(cx + 1, cy + 1, cx + HOTBAR_SLOT - 1, cy + HOTBAR_SLOT - 1, 0x3340FF80);
                    }
                    long count = resolvePinnedItemCount(this.controller.getQuickSlotItemId(pinIndex));
                    drawSlotCountOverlay(g, cx, cy, HOTBAR_SLOT, RtsClientUiUtil.compactCount(count),
                            count > 0 ? 0xFFF7E6A8 : 0xFFB4B9C3);
                } else {
                    g.drawCenteredString(screen.font(), Integer.toString(pinIndex + 1), cx + HOTBAR_SLOT / 2, cy + 5, 0x88D0D8E4);
                }
            }
            if (mouseX >= cx && mouseX <= cx + HOTBAR_SLOT && mouseY >= cy && mouseY <= cy + HOTBAR_SLOT) {
                g.fill(cx + 1, cy + 1, cx + HOTBAR_SLOT - 1, cy + HOTBAR_SLOT - 1, 0x22FFFFFF);
                if (pageButton) {
                    this.hoveredPinPageButton = true;
                } else if (pinIndex < totalPins) {
                    this.hoveredPinIndex = pinIndex;
                }
            }
        }
    }

    private void drawEmptyHandButton(GuiGraphics g, int x, int y) {
        int size = 10;
        int left = x + (HOTBAR_SLOT - size) / 2;
        int top = y + (HOTBAR_SLOT - size) / 2;
        g.fill(left, top, left + size, top + size, 0xFFFFC3A3);
    }

    // 鈹€鈹€ 鎺掑??/ 鍒嗛??/ 鎼滅??鈹€鈹€

    private void drawSortButton(GuiGraphics g, int x, int y, String label) {
        g.fill(x, y, x + SORT_BUTTON_SIZE, y + SORT_BUTTON_SIZE, 0xAA29323D);
        g.drawCenteredString(screen.font(), label, x + SORT_BUTTON_SIZE / 2, y + 4, 0xFFFFFF);
    }

    private void drawPager(GuiGraphics g, int x, int y, BottomPanelLayoutTypes.BottomPanelLayout layout) {
        int page = this.controller.getStoragePage();
        int totalPages = this.controller.getStorageTotalPages();
        if (activeBottomPanelTab() == BottomPanelLayoutTypes.BottomPanelTab.CREATIVE) {
            totalPages = creativePageCount(creativeGridWidth(layout.mainStorageW()), layout.gridH());
            this.creativePage = Mth.clamp(this.creativePage, 0, totalPages - 1);
            page = this.creativePage;
        }
        g.fill(x, y, x + 16, y + 14, 0xAA2A2A2A);
        g.drawString(screen.font(), "<", x + 5, y + 3, 0xFFFFFF);
        g.fill(x + 58, y, x + 74, y + 14, 0xAA2A2A2A);
        g.drawString(screen.font(), ">", x + 63, y + 3, 0xFFFFFF);
        g.drawString(screen.font(), (page + 1) + "/" + totalPages, x + 19, y + 3, 0xFFFFFF);
    }

    private int computeSearchFieldWidth(int searchAreaWidth) {
        return Math.max(56, searchAreaWidth - (SEARCH_CLEAR_SIZE + 2));
    }

    public int getSearchClearButtonX(int searchX, int searchAreaWidth) {
        return searchX + computeSearchFieldWidth(searchAreaWidth) + 2;
    }

    private void drawSearchClearButton(GuiGraphics g, int searchX, int searchY, int searchAreaWidth) {
        int x = getSearchClearButtonX(searchX, searchAreaWidth);
        int y = searchY + 1;
        boolean focused = screen.isSearchFocused();
        int bg = focused ? 0xAA3B4755 : 0xAA2A313B;
        g.fill(x, y, x + SEARCH_CLEAR_SIZE, y + SEARCH_CLEAR_SIZE, bg);
        g.hLine(x, x + SEARCH_CLEAR_SIZE, y, 0xFF637283);
        g.hLine(x, x + SEARCH_CLEAR_SIZE, y + SEARCH_CLEAR_SIZE, 0xFF101318);
        g.vLine(x, y, y + SEARCH_CLEAR_SIZE, 0xFF637283);
        g.vLine(x + SEARCH_CLEAR_SIZE, y, y + SEARCH_CLEAR_SIZE, 0xFF101318);
        var sb = screen.getSearchBox();
        int textColor = sb != null && !sb.getValue().isEmpty() ? 0xFFFFFF : 0x99A6B5;
        g.drawCenteredString(screen.font(), "x", x + SEARCH_CLEAR_SIZE / 2, y + 3, textColor);
    }

    // 鈹€鈹€ 鍒嗙被闈㈡澘 鈹€鈹€

    private void drawCategoryPanel(GuiGraphics g, int mouseX, int mouseY, int x, int y, int width, int height) {
        g.fill(x, y, x + width, y + height, 0x8820222A);
        g.drawCenteredString(screen.font(), Component.translatable("screen.rtsbuilding.storage.category"), x + width / 2, y + 2, 0xFFFFFF);

        int upX0 = x + width - 24;
        int upX1 = x + width - 13;
        int downX0 = x + width - 12;
        int downX1 = x + width - 2;
        int arrowY0 = y + 1;
        int arrowY1 = y + 11;
        g.fill(upX0, arrowY0, upX1, arrowY1, 0xAA2A2A2A);
        g.fill(downX0, arrowY0, downX1, arrowY1, 0xAA2A2A2A);
        g.drawCenteredString(screen.font(), "^", upX0 + 5, y + 2, 0xFFFFFF);
        g.drawCenteredString(screen.font(), "v", downX0 + 5, y + 2, 0xFFFFFF);

        int listY = y + 13;
        int listH = height - 15;
        int visible = Math.max(1, listH / CATEGORY_ROW_H);
        List<CategoryTypes.CategoryRow> rows = buildCategoryRows();
        int maxScroll = Math.max(0, rows.size() - visible);
        this.categoryScroll = Mth.clamp(this.categoryScroll, 0, maxScroll);

        for (int row = 0; row < visible; row++) {
            int index = this.categoryScroll + row;
            if (index >= rows.size()) {
                break;
            }
            CategoryTypes.CategoryRow category = rows.get(index);
            int rowY = listY + row * CATEGORY_ROW_H;
            boolean selected = category.token().equals(activeCategoryToken());
            int bg = selected ? 0xFF335E4C : 0x66343A47;
            g.fill(x + 2, rowY, x + width - 2, rowY + CATEGORY_ROW_H - 2, bg);
            int textColor = selected ? 0xFFFFFF : 0xE0E0E0;
            int labelX = x + 6 + (category.depth() * 10);
            int labelRight = x + width - 6;

            if (category.expandable()) {
                int toggleX = x + width - 12;
                int toggleY = rowY + 1;
                g.fill(toggleX, toggleY, toggleX + 9, toggleY + CATEGORY_ROW_H - 3, 0xAA2A313B);
                g.drawCenteredString(screen.font(), category.expanded() ? "-" : "+", toggleX + 4, rowY + 3, 0xFFFFFF);
                labelRight = toggleX - 3;
            }

            int availableWidth = Math.max(8, labelRight - labelX);
            int maxLabelWidth = Math.max(8, (int) Math.floor(availableWidth / CATEGORY_TEXT_SCALE));
            String label = screen.trimToWidth(category.label(), maxLabelWidth);
            int scaledTextWidth = (int) Math.ceil(screen.font().width(label) * CATEGORY_TEXT_SCALE);
            int centeredX = labelX + Math.max(0, (availableWidth - scaledTextWidth) / 2);
            int textAreaHeight = CATEGORY_ROW_H - 2;
            int scaledTextHeight = Math.max(1, (int) Math.ceil(screen.font().lineHeight * CATEGORY_TEXT_SCALE));
            int centeredY = rowY + 1 + Math.max(0, (textAreaHeight - scaledTextHeight) / 2);
            drawScaledText(g, label, centeredX, centeredY, textColor, CATEGORY_TEXT_SCALE);
        }
    }

    private void drawScaledText(GuiGraphics g, String text, int x, int y, int color, float scale) {
        if (text == null || text.isEmpty()) {
            return;
        }
        g.pose().pushPose();
        g.pose().translate(x, y, 0.0F);
        g.pose().scale(scale, scale, 1.0F);
        g.drawString(screen.font(), text, 0, 0, color, false);
        g.pose().popPose();
    }

    // 鈹€鈹€ 鍌ㄥ瓨缃戞牸 鈹€鈹€

    private void drawStorageGrid(GuiGraphics g, int mouseX, int mouseY, int x, int y, int width, int height) {
        int cols = Math.max(1, width / SLOT);
        int rows = Math.max(1, height / SLOT);
        int maxSlots = cols * rows;
        this.controller.updateStoragePageSize(maxSlots);
        List<ClientRtsController.StorageEntry> entries = this.controller.getStorageEntries();

        for (int i = 0; i < maxSlots; i++) {
            int cx = x + (i % cols) * SLOT;
            int cy = y + (i / cols) * SLOT;
            int box = SLOT - 2;
            g.fill(cx, cy, cx + box, cy + box, 0xAA111111);
            g.hLine(cx, cx + box, cy, 0xFF4A4A4A);
            g.hLine(cx, cx + box, cy + box, 0xFF1B1B1B);
            g.vLine(cx, cy, cy + box, 0xFF4A4A4A);
            g.vLine(cx + box, cy, cy + box, 0xFF1B1B1B);

            if (i < entries.size()) {
                var entry = entries.get(i);
                boolean selected = !this.controller.getSelectedItemPreview().isEmpty()
                        && net.minecraft.world.item.ItemStack.isSameItemSameTags(entry.stack(), this.controller.getSelectedItemPreview());
                if (selected) {
                    g.fill(cx + 1, cy + 1, cx + box - 1, cy + box - 1, 0x3326C56D);
                }
                g.renderItem(entry.stack(), cx + 2, cy + 2);
                drawSlotCountOverlay(g, cx, cy, box, RtsClientUiUtil.compactCount(entry.count()), 0xFFF7E6A8);

                if (mouseX >= cx && mouseX <= cx + box && mouseY >= cy && mouseY <= cy + box) {
                    this.hoveredEntry = i;
                    if (selected) {
                        g.fill(cx + 1, cy + 1, cx + box - 1, cy + box - 1, 0x3340FF80);
                    }
                }
            }
        }
        if (entries.isEmpty()) {
            renderStorageEmptyState(g, x, y, width, height);
        }
    }

    private void renderStorageEmptyState(GuiGraphics g, int x, int y, int width, int height) {
        int messageW = Math.max(24, width - 12);
        Component title = this.controller.isStorageLinked()
                ? Component.translatable("screen.rtsbuilding.storage.empty_linked")
                : Component.translatable("screen.rtsbuilding.storage.empty_unlinked");
        Component detail = this.controller.isStorageLinked()
                ? Component.translatable("screen.rtsbuilding.storage.empty_linked.detail")
                : Component.translatable("screen.rtsbuilding.storage.empty_unlinked.detail");
        int centerY = y + Math.max(8, height / 2 - 10);
        g.drawCenteredString(screen.font(), screen.trimToWidth(title.getString(), messageW), x + width / 2, centerY, 0xFFE7C46A);
        g.drawCenteredString(screen.font(), screen.trimToWidth(detail.getString(), messageW), x + width / 2, centerY + 12, 0xFFB8C7D6);
    }

    /**
     * Draws the creative picker as a client-only item source. Selecting an item here
     * only changes the RTS placement preview; it does not request storage extraction
     * and does not mutate the player's inventory.
     */
    private void drawCreativeGrid(GuiGraphics g, int mouseX, int mouseY, int x, int y, int width, int height) {
        int cols = Math.max(1, width / SLOT);
        int rows = Math.max(1, height / SLOT);
        int maxSlots = cols * rows;
        List<RtsCreativeItemCatalog.CreativeEntry> entries = creativeEntriesForCurrentFilter();
        int pageCount = Math.max(1, (int) Math.ceil(entries.size() / (double) maxSlots));
        this.creativePage = Mth.clamp(this.creativePage, 0, pageCount - 1);
        int start = this.creativePage * maxSlots;

        for (int i = 0; i < maxSlots; i++) {
            int cx = x + (i % cols) * SLOT;
            int cy = y + (i / cols) * SLOT;
            int box = SLOT - 2;
            g.fill(cx, cy, cx + box, cy + box, 0xAA11151D);
            g.hLine(cx, cx + box, cy, 0xFF596D84);
            g.hLine(cx, cx + box, cy + box, 0xFF10151B);
            g.vLine(cx, cy, cy + box, 0xFF596D84);
            g.vLine(cx + box, cy, cy + box, 0xFF10151B);

            int index = start + i;
            if (index >= entries.size()) {
                continue;
            }
            RtsCreativeItemCatalog.CreativeEntry entry = entries.get(index);
            boolean selected = !this.controller.getSelectedItemPreview().isEmpty()
                    && net.minecraft.world.item.ItemStack.isSameItemSameTags(entry.stack(), this.controller.getSelectedItemPreview());
            if (selected) {
                g.fill(cx + 1, cy + 1, cx + box - 1, cy + box - 1, 0x3326C56D);
            }
            g.renderItem(entry.stack(), cx + 2, cy + 2);
            if (inside(mouseX, mouseY, cx, cy, box, box)) {
                this.hoveredCreativeEntry = index;
                g.fill(cx + 1, cy + 1, cx + box - 1, cy + box - 1, selected ? 0x3340FF80 : 0x22FFFFFF);
            }
        }

        if (entries.isEmpty()) {
            int messageW = Math.max(24, width - 12);
            int centerY = y + Math.max(8, height / 2 - 10);
            Component title = Component.translatable("screen.rtsbuilding.creative.empty");
            Component detail = Component.translatable("screen.rtsbuilding.creative.empty.detail");
            g.drawCenteredString(screen.font(), screen.trimToWidth(title.getString(), messageW), x + width / 2, centerY, 0xFFE7C46A);
            g.drawCenteredString(screen.font(), screen.trimToWidth(detail.getString(), messageW), x + width / 2, centerY + 12, 0xFFB8C7D6);
        }
    }

    private void drawRecentGrid(GuiGraphics g, int mouseX, int mouseY, int x, int y, int width, int height) {
        int cols = Math.max(1, width / SLOT);
        int rows = Math.max(1, height / SLOT);
        int maxSlots = cols * rows;
        List<ClientRtsController.RecentEntry> entries = this.controller.getRecentEntries();

        for (int i = 0; i < maxSlots; i++) {
            int cx = x + (i % cols) * SLOT;
            int cy = y + (i / cols) * SLOT;
            int box = SLOT - 2;
            g.fill(cx, cy, cx + box, cy + box, 0xAA161C24);
            g.hLine(cx, cx + box, cy, 0xFF526171);
            g.hLine(cx, cx + box, cy + box, 0xFF10151B);
            g.vLine(cx, cy, cy + box, 0xFF526171);
            g.vLine(cx + box, cy, cy + box, 0xFF10151B);

            if (i >= entries.size()) {
                continue;
            }

            ClientRtsController.RecentEntry entry = entries.get(i);
            if (!entry.preview().isEmpty()) {
                g.renderItem(entry.preview(), cx + 2, cy + 2);
            }
            drawSlotCountOverlay(g, cx, cy, box,
                    formatRecentAmount(entry),
                    entry.fluid() ? 0xFFBEE6FF : 0xFFE8F4C0);

            if (mouseX >= cx && mouseX <= cx + box && mouseY >= cy && mouseY <= cy + box) {
                g.fill(cx + 1, cy + 1, cx + box - 1, cy + box - 1, 0x22FFFFFF);
                this.hoveredRecentEntry = i;
            }
        }
    }

    private String formatRecentAmount(ClientRtsController.RecentEntry entry) {
        if (entry == null) {
            return "";
        }
        long amount = this.controller.getRecentDisplayAmount(entry);
        return entry.fluid() ? RtsClientUiUtil.compactFluidAmount(amount) : RtsClientUiUtil.compactCount(amount);
    }

    private void drawSlotCountOverlay(GuiGraphics g, int slotX, int slotY, int box, String countText, int color) {
        RtsClientUiUtil.drawSlotCountOverlay(g, screen.font(), slotX, slotY, box, countText, color);
    }

    // 鈹€鈹€ 娴佷綋缃戞牸 鈹€鈹€

    private void drawFluidGrid(GuiGraphics g, int mouseX, int mouseY, int x, int y, int width, int height) {
        int cols = 2;
        int rows = Math.max(1, height / SLOT);
        int maxSlots = cols * rows;
        int box = SLOT - 2;
        List<ClientRtsController.FluidEntry> entries = this.controller.getFluidEntries();

        for (int i = 0; i < maxSlots; i++) {
            int cx = x + (i % cols) * SLOT;
            int cy = y + (i / cols) * SLOT;
            g.fill(cx, cy, cx + box, cy + box, 0xAA2E1E12);
            g.hLine(cx, cx + box, cy, 0xFFFFA553);
            g.hLine(cx, cx + box, cy + box, 0xFF23140A);
            g.vLine(cx, cy, cy + box, 0xFFFFA553);
            g.vLine(cx + box, cy, cy + box, 0xFF23140A);

            if (i < entries.size()) {
                var entry = entries.get(i);
                boolean selected = entry.fluidId().equals(this.controller.getSelectedFluidId());
                if (selected) {
                    g.fill(cx + 1, cy + 1, cx + box - 1, cy + box - 1, 0x3367D8FF);
                }
                if (!entry.preview().isEmpty()) {
                    g.renderItem(entry.preview(), cx + 2, cy + 2);
                }
                drawSlotCountOverlay(g, cx, cy, box, RtsClientUiUtil.compactFluidAmount(entry.amount()), 0xFFFCCB8A);

                if (mouseX >= cx && mouseX <= cx + box && mouseY >= cy && mouseY <= cy + box) {
                    this.hoveredFluidEntry = i;
                    if (selected) {
                        g.fill(cx + 1, cy + 1, cx + box - 1, cy + box - 1, 0x3340FF80);
                    }
                }
            }
        }
    }

    // 鈹€鈹€ 鍚堟垚闈㈡澘 鈹€鈹€

    private void renderCraftablesPanel(GuiGraphics g, int mouseX, int mouseY, int x, int y, int width, int height, float partialTick) {
        syncCraftSearchValueFromController();

        RtsClientUiUtil.drawPanelFrame(g, x, y, width, height, 0xAA141922, 0xFF637993, 0xFF0D1218);
        g.drawString(screen.font(), "Craft", x + 5, y + 4, 0xEAF2FF);

        int searchX = x + 4;
        int searchY = y + 15;
        int searchW = Math.max(24, width - CRAFT_PANEL_APPLY_W - CRAFT_PANEL_TOGGLE_W - 16);
        int applyX = searchX + searchW + 4;
        int toggleX = applyX + CRAFT_PANEL_APPLY_W + 4;
        int toggleY = searchY;
        boolean craftSearchDirty = hasPendingCraftSearchDraft();
        int applyBg = craftSearchDirty ? 0xAA4C6E39 : 0xAA24303A;
        int toggleBg = this.controller.isCraftablesShowUnavailable() ? 0xAA5A3D2A : 0xAA2C5A41;

        RtsClientUiUtil.drawPanelFrame(g, searchX, searchY, searchW, CRAFT_PANEL_SEARCH_H, 0xAA1E2731, 0xFF5E738A, 0xFF111921);
        if (screen.getCraftSearchBox() != null) {
            var csb = screen.getCraftSearchBox();
            csb.setX(searchX + 2);
            csb.setY(searchY + 2);
            csb.setWidth(Math.max(22, searchW - 4));
            csb.setHeight(8);
            csb.render(g, mouseX, mouseY, partialTick);
        }

        RtsClientUiUtil.drawPanelFrame(g, applyX, toggleY, CRAFT_PANEL_APPLY_W, CRAFT_PANEL_SEARCH_H, applyBg, 0xFF6E8799, 0xFF111821);
        g.drawCenteredString(screen.font(),
                "OK",
                applyX + CRAFT_PANEL_APPLY_W / 2,
                toggleY + 2,
                craftSearchDirty ? 0xFFFFFF : 0xFFB8C7D6);

        RtsClientUiUtil.drawPanelFrame(g, toggleX, toggleY, CRAFT_PANEL_TOGGLE_W, CRAFT_PANEL_SEARCH_H, toggleBg, 0xFF667D95, 0xFF111821);
        g.drawCenteredString(screen.font(),
                this.controller.isCraftablesShowUnavailable() ? "ALL" : "MAKE",
                toggleX + CRAFT_PANEL_TOGGLE_W / 2,
                toggleY + 2,
                0xFFFFFF);

        int gridY = searchY + CRAFT_PANEL_SEARCH_H + 6;
        int clampedRows = Math.max(1, (height - (gridY - y) - 6) / CRAFT_PANEL_PITCH);
        List<ClientRtsController.CraftableEntry> entries = this.controller.getCraftableEntries();
        int totalRows = Math.max(1, (int) Math.ceil(entries.size() / (double) CRAFT_PANEL_COLS));
        int maxScroll = Math.max(0, totalRows - clampedRows);
        this.craftScroll = Mth.clamp(this.craftScroll, 0, maxScroll);
        int startIndex = this.craftScroll * CRAFT_PANEL_COLS;

        for (int row = 0; row < clampedRows; row++) {
            for (int col = 0; col < CRAFT_PANEL_COLS; col++) {
                int index = startIndex + row * CRAFT_PANEL_COLS + col;
                int slotX = x + 4 + col * CRAFT_PANEL_PITCH;
                int slotY = gridY + row * CRAFT_PANEL_PITCH;
                int fill = 0xAA1A212B;
                if (index < entries.size()) {
                    ClientRtsController.CraftableEntry entry = entries.get(index);
                    fill = entry.craftable() ? 0xAA214131 : 0xAA3F2323;
                }
                RtsClientUiUtil.drawPanelFrame(g, slotX, slotY, CRAFT_PANEL_SLOT, CRAFT_PANEL_SLOT, fill, 0xFF596D84, 0xFF11171E);
                if (index >= entries.size()) {
                    continue;
                }

                ClientRtsController.CraftableEntry entry = entries.get(index);
                g.renderItem(entry.stack(), slotX + 1, slotY + 1);
                if (entry.resultCount() > 1) {
                    drawSlotCountOverlay(g, slotX, slotY, CRAFT_PANEL_SLOT, RtsClientUiUtil.compactCount(entry.resultCount()), 0xFFE8F4FF);
                }
                if (!entry.craftable()) {
                    g.fill(slotX + 1, slotY + 1, slotX + CRAFT_PANEL_SLOT - 1, slotY + CRAFT_PANEL_SLOT - 1, 0x44220000);
                }
                if (mouseX >= slotX && mouseX <= slotX + CRAFT_PANEL_SLOT && mouseY >= slotY && mouseY <= slotY + CRAFT_PANEL_SLOT) {
                    g.fill(slotX + 1, slotY + 1, slotX + CRAFT_PANEL_SLOT - 1, slotY + CRAFT_PANEL_SLOT - 1, 0x22FFFFFF);
                    this.hoveredCraftableEntry = index;
                }
            }
        }
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

    private boolean hasPendingCraftSearchDraft() {
        return !normalizeCraftSearchDraft(this.craftSearchDraft).equals(
                normalizeCraftSearchDraft(this.controller.getCraftablesSearch()));
    }

    private static String normalizeCraftSearchDraft(String value) {
        return RtsCraftablesUiHelper.normalizeSearchDraft(value);
    }

    public void openCraftQuantityDialog(ClientRtsController.CraftableEntry entry) {
        screen.blurSearchFocus();
        RtsCraftablesUiHelper.openCraftQuantityDialog(this.craftQuantityDialog, entry);
    }

    public void submitCraftQuantityDialogIfReady() {
        RtsCraftablesUiHelper.submitPendingCraftRequest(this.craftQuantityDialog, this.controller);
    }

    // 鈹€鈹€ 鍚堟垚搴曞骇 鈹€鈹€

    private void drawCraftDock(GuiGraphics g, int mouseX, int mouseY, int x, int y) {
        PanelLayouts.CraftDockLayout dock = resolveCraftDockLayout(x, y);
        if (screen.hasProgressionNode(RtsProgressionNodes.CRAFT_TERMINAL)) {
            boolean craftHovered = inside(mouseX, mouseY, dock.cX(), dock.cY(), CRAFT_DOCK_C_SIZE, CRAFT_DOCK_C_SIZE);
            int craftFill = craftHovered ? 0xCC385465 : 0xAA24303A;
            RtsClientUiUtil.drawPanelFrame(g, dock.cX(), dock.cY(), CRAFT_DOCK_C_SIZE, CRAFT_DOCK_C_SIZE, craftFill, 0xFF6E8799, 0xFF111821);
            g.drawCenteredString(screen.font(), "C", dock.cX() + CRAFT_DOCK_C_SIZE / 2, dock.cY() + 5, 0xFFFFFF);
        }

        if (!screen.hasProgressionNode(RtsProgressionNodes.REMOTE_GUI)) {
            return;
        }
        for (int slot = 0; slot < this.controller.getGuiBindingCount(); slot++) {
            int slotX = dock.slotX(slot);
            int slotY = dock.slotY(slot);
            boolean hovered = inside(mouseX, mouseY, slotX, slotY, CRAFT_DOCK_SLOT_SIZE, CRAFT_DOCK_SLOT_SIZE);
            boolean pending = screen.getPendingGuiBindSlot() == slot;
            boolean bound = this.controller.hasGuiBinding(slot);
            int fill = pending ? 0xCC2D6B47 : (bound ? 0xAA23384A : 0xAA202731);
            if (hovered) {
                fill = pending ? 0xDD377F53 : (bound ? 0xBB2C4760 : 0xBB29323D);
                this.hoveredGuiBindingSlot = slot;
            }
            RtsClientUiUtil.drawPanelFrame(g, slotX, slotY, CRAFT_DOCK_SLOT_SIZE, CRAFT_DOCK_SLOT_SIZE, fill, 0xFF698097, 0xFF0F151C);
            var preview = this.controller.getGuiBindingPreview(slot);
            if (bound && !pending && !preview.isEmpty()) {
                g.renderItem(preview, slotX + 1, slotY + 1);
            } else {
                String text = (!bound || pending) ? "+" : Integer.toString(slot + 1);
                g.drawCenteredString(screen.font(), text, slotX + CRAFT_DOCK_SLOT_SIZE / 2, slotY + 2, 0xFFFFFF);
            }
        }
    }

    // 鈹€鈹€ 鐐瑰嚮澶勭悊 鈹€鈹€

    public boolean handleClick(double mouseX, double mouseY) {
        BottomPanelLayoutTypes.BottomPanelLayout layout = resolveBottomPanelLayout();
        BottomPanelLayoutTypes.BottomPanelTab activeTab = activeBottomPanelTab();
        if (!layout.contains(mouseX, mouseY)) {
            return false;
        }

        BottomPanelLayoutTypes.BottomPanelTab clickedTab = resolveBottomPanelTabClick(layout, mouseX, mouseY);
        if (clickedTab != null) {
            this.bottomPanelTab = clickedTab;
            syncSearchBoxForActiveTab();
            screen.blurSearchFocus();
            screen.closeGearMenu();
            return true;
        }
        if (inside(mouseX, mouseY, bottomRefreshButtonX(layout), bottomGuideButtonY(layout), 12, 12)) {
            if (activeTab == BottomPanelLayoutTypes.BottomPanelTab.BLUEPRINTS) {
                BlueprintPanel.reload();
            } else if (activeTab == BottomPanelLayoutTypes.BottomPanelTab.CREATIVE) {
                this.creativePage = 0;
            } else {
                this.controller.refreshStoragePage();
            }
            screen.closeGearMenu();
            return true;
        }
        if (inside(mouseX, mouseY, bottomGuideButtonX(layout), bottomGuideButtonY(layout), 12, 12)) {
            screen.openBottomGuide(bottomGuideButtonX(layout) + 6, bottomGuideButtonY(layout));
            screen.closeGearMenu();
            return true;
        }
        if (layout.isInsideHeader(mouseX, mouseY)) {
            return true;
        }
        if (activeTab == BottomPanelLayoutTypes.BottomPanelTab.BLUEPRINTS) {
            int contentX = layout.panelX() + BOTTOM_PANEL_PADDING;
            int contentY = layout.panelY() + BOTTOM_PANEL_HEADER_H + 4;
            int contentW = Math.max(80, layout.panelW() - BOTTOM_PANEL_PADDING * 2);
            int contentH = Math.max(24, layout.panelH() - BOTTOM_PANEL_HEADER_H - 8);
            return BlueprintPanel.mouseClicked(mouseX, mouseY, contentX, contentY, contentW, contentH);
        }

        int sortX = layout.sortX();
        int sortY = layout.sortY();
        int categoryX = layout.categoryX();
        int storageX = layout.storageX();
        int mainStorageW = layout.mainStorageW();
        int searchW = layout.searchW();
        int pagerX = layout.pagerX();
        int toolY = layout.toolY();
        int gridY = layout.gridY();
        int gridH = layout.gridH();
        int craftPanelY = layout.craftPanelY();
        int craftPanelH = layout.craftPanelH();

        if (handleSearchClearClick(mouseX, mouseY, storageX, layout.storageY(), searchW)) {
            return true;
        }

        var searchBox = screen.getSearchBox();
        if (searchBox != null && searchBox.mouseClicked(mouseX, mouseY, net.minecraft.client.gui.screens.Screen.hasShiftDown() ? 0 : 0)) {
            screen.focusStorageSearchBox();
            return true;
        }

        if (activeTab != BottomPanelLayoutTypes.BottomPanelTab.CREATIVE
                && handleCraftablesPanelLeftClick(mouseX, mouseY, layout.craftPanelX(), craftPanelY, CRAFT_PANEL_W, craftPanelH)) {
            return true;
        }

        screen.blurSearchFocus();

        if (inside(mouseX, mouseY, sortX, sortY, SORT_BUTTON_SIZE, SORT_BUTTON_SIZE)) {
            this.controller.cycleSort();
            return true;
        }
        if (inside(mouseX, mouseY, sortX, sortY + SORT_BUTTON_SIZE + 4, SORT_BUTTON_SIZE, SORT_BUTTON_SIZE)) {
            this.controller.toggleSortDirection();
            return true;
        }
        int heightBtnX = sortX + SORT_BUTTON_SIZE + 26;
        if (inside(mouseX, mouseY, heightBtnX, sortY, SORT_BUTTON_SIZE, SORT_BUTTON_SIZE)) {
            adjustBottomPanelSize(1);
            return true;
        }
        if (inside(mouseX, mouseY, heightBtnX, sortY + SORT_BUTTON_SIZE + 4, SORT_BUTTON_SIZE, SORT_BUTTON_SIZE)) {
            adjustBottomPanelSize(-1);
            return true;
        }
        if (handleCraftDockClick(mouseX, mouseY, 0, sortX, sortY + (SORT_BUTTON_SIZE + 4) * 2)) {
            return true;
        }

        int categoryY = layout.categoryY();
        int upX0 = categoryX + CATEGORY_W - 24;
        int downX0 = categoryX + CATEGORY_W - 12;
        if (inside(mouseX, mouseY, upX0, categoryY + 1, 11, 10)) {
            shiftCategoryScroll(-1);
            return true;
        }
        if (inside(mouseX, mouseY, downX0, categoryY + 1, 10, 10)) {
            shiftCategoryScroll(1);
            return true;
        }

        CategoryTypes.CategoryClick categoryClick = resolveClickedCategoryAction(mouseX, mouseY);
        if (categoryClick != null) {
            if (activeTab == BottomPanelLayoutTypes.BottomPanelTab.CREATIVE) {
                this.creativeCategory = categoryClick.categoryToken();
                this.creativePage = 0;
                return true;
            }
            if (categoryClick.toggleExpandOnly()) {
                toggleCategoryExpansion(categoryClick.modNamespace());
                return true;
            }
            this.controller.setStorageCategory(categoryClick.categoryToken());
            if (categoryClick.modNamespace() != null && !categoryClick.modNamespace().isBlank()) {
                this.expandedCategoryMods.add(categoryClick.modNamespace());
            }
            return true;
        }

        if (handleToolRowClick(mouseX, mouseY, storageX, toolY, mainStorageW)) {
            return true;
        }

        if (inside(mouseX, mouseY, pagerX, layout.storageY(), 16, 14)) {
            if (activeTab == BottomPanelLayoutTypes.BottomPanelTab.CREATIVE) {
                this.creativePage = Mth.clamp(this.creativePage - 1, 0, creativePageCount(creativeGridWidth(mainStorageW), gridH) - 1);
            } else {
                this.controller.prevPage();
            }
            return true;
        }
        if (inside(mouseX, mouseY, pagerX + 58, layout.storageY(), 16, 14)) {
            if (activeTab == BottomPanelLayoutTypes.BottomPanelTab.CREATIVE) {
                this.creativePage = Mth.clamp(this.creativePage + 1, 0, creativePageCount(creativeGridWidth(mainStorageW), gridH) - 1);
            } else {
                this.controller.nextPage();
            }
            return true;
        }

        if (activeTab == BottomPanelLayoutTypes.BottomPanelTab.CREATIVE) {
            int creativeGridW = creativeGridWidth(mainStorageW);
            int recentGridX = storageX + creativeGridW + STORAGE_RECENT_GAP;
            int recentGridW = Math.max(SLOT, mainStorageW - creativeGridW - STORAGE_RECENT_GAP);
            int creativeIndex = resolveClickedCreativeEntry(mouseX, mouseY, storageX, gridY, creativeGridW, gridH);
            if (creativeIndex >= 0) {
                RtsCreativeItemCatalog.CreativeEntry entry = getCreativeEntryForTooltip(creativeIndex);
                if (entry != null) {
                    this.controller.selectItemForPlacement(entry.itemId(), entry.label(), entry.stack());
                }
                return true;
            }
            int recentIndex = resolveClickedRecentEntry(mouseX, mouseY, recentGridX, gridY, recentGridW, gridH);
            if (recentIndex >= 0) {
                this.controller.selectRecentEntry(recentIndex);
                return true;
            }
            return true;
        }

        int fluidW = getFluidStripWidth(mainStorageW);
        if (fluidW > 0) {
            int fluidIndex = resolveClickedFluid(mouseX, mouseY, storageX, gridY, fluidW, gridH);
            if (fluidIndex >= 0) {
                this.controller.selectFluidEntry(fluidIndex);
                return true;
            }
        }

        int itemGridX = fluidW > 0 ? storageX + fluidW + 4 : storageX;
        int itemGridW = fluidW > 0 ? Math.max(SLOT, mainStorageW - fluidW - 4) : mainStorageW;
        int storageGridW = Math.max(SLOT, (itemGridW - STORAGE_RECENT_GAP) / 2);
        int recentGridX = itemGridX + storageGridW + STORAGE_RECENT_GAP;
        int recentGridW = Math.max(SLOT, itemGridW - storageGridW - STORAGE_RECENT_GAP);
        int entryIndex = resolveClickedEntry(mouseX, mouseY, itemGridX, gridY, storageGridW, gridH);
        if (entryIndex >= 0) {
            this.controller.selectStorageEntry(entryIndex);
            return true;
        }
        int recentIndex = resolveClickedRecentEntry(mouseX, mouseY, recentGridX, gridY, recentGridW, gridH);
        if (recentIndex >= 0) {
            this.controller.selectRecentEntry(recentIndex);
            return true;
        }
        return true;
    }

    public boolean handleRightClick(double mouseX, double mouseY) {
        BottomPanelLayoutTypes.BottomPanelLayout layout = resolveBottomPanelLayout();
        BottomPanelLayoutTypes.BottomPanelTab activeTab = activeBottomPanelTab();
        if (!layout.contains(mouseX, mouseY)) {
            return false;
        }
        if (layout.isInsideHeader(mouseX, mouseY)) {
            return true;
        }
        if (activeTab == BottomPanelLayoutTypes.BottomPanelTab.BLUEPRINTS) {
            return true;
        }

        int storageX = layout.storageX();
        int sortX = layout.sortX();
        int sortY = layout.sortY();
        int mainStorageW = layout.mainStorageW();
        int toolY = layout.toolY();
        int gridY = layout.gridY();
        int gridH = layout.gridH();

        if (handleCraftDockClick(mouseX, mouseY, 1, sortX, sortY + (SORT_BUTTON_SIZE + 4) * 2)) {
            return true;
        }

        if (handleToolRowRightClick(mouseX, mouseY, storageX, toolY, mainStorageW)) {
            return true;
        }

        if (activeTab != BottomPanelLayoutTypes.BottomPanelTab.CREATIVE
                && handleCraftablesPanelRightClick(mouseX, mouseY, layout.craftPanelX(), layout.craftPanelY(), CRAFT_PANEL_W, layout.craftPanelH())) {
            return true;
        }

        if (activeTab == BottomPanelLayoutTypes.BottomPanelTab.CREATIVE) {
            return true;
        }

        int fluidW = getFluidStripWidth(mainStorageW);
        int itemGridX = fluidW > 0 ? storageX + fluidW + 4 : storageX;
        int itemGridW = fluidW > 0 ? Math.max(SLOT, mainStorageW - fluidW - 4) : mainStorageW;
        int storageGridW = Math.max(SLOT, (itemGridW - STORAGE_RECENT_GAP) / 2);

        int entryIndex = resolveClickedEntry(mouseX, mouseY, itemGridX, gridY, storageGridW, gridH);
        if (entryIndex >= 0 && entryIndex < this.controller.getStorageEntries().size()) {
            this.controller.storeFluidFromStorageItem(this.controller.getStorageEntries().get(entryIndex).itemId());
            return true;
        }
        return true;
    }

    public boolean handleMouseScrolled(double mouseX, double mouseY, double scrollY) {
        BottomPanelLayoutTypes.BottomPanelLayout layout = resolveBottomPanelLayout();
        BottomPanelLayoutTypes.BottomPanelTab activeTab = activeBottomPanelTab();
        if (activeTab == BottomPanelLayoutTypes.BottomPanelTab.BLUEPRINTS) {
            int contentX = layout.panelX() + BOTTOM_PANEL_PADDING;
            int contentY = layout.panelY() + BOTTOM_PANEL_HEADER_H + 4;
            int contentW = Math.max(80, layout.panelW() - BOTTOM_PANEL_PADDING * 2);
            int contentH = Math.max(24, layout.panelH() - BOTTOM_PANEL_HEADER_H - 8);
            BlueprintPanel.mouseScrolled(mouseX, mouseY, scrollY, contentX, contentY, contentW, contentH);
            return true;
        }
        if (activeTab == BottomPanelLayoutTypes.BottomPanelTab.CREATIVE) {
            if (isInsideCategoryList(mouseX, mouseY)) {
                shiftCategoryScroll(scrollY > 0.0D ? -1 : 1);
                return true;
            }
            int delta = scrollY > 0.0D ? -1 : 1;
            this.creativePage = Mth.clamp(
                    this.creativePage + delta,
                    0,
                    creativePageCount(creativeGridWidth(layout.mainStorageW()), layout.gridH()) - 1);
            return true;
        }
        if (inside(mouseX, mouseY, layout.craftPanelX(), layout.craftPanelY(), CRAFT_PANEL_W, layout.craftPanelH())) {
            int visibleRows = layout.storageRows();
            int totalRows = Math.max(1, (int) Math.ceil(this.controller.getCraftableEntries().size() / (double) CRAFT_PANEL_COLS));
            int maxScroll = Math.max(0, totalRows - visibleRows);
            int delta = scrollY > 0.0D ? -1 : 1;
            this.craftScroll = Mth.clamp(this.craftScroll + delta, 0, maxScroll);
            if (delta > 0 && this.craftScroll >= maxScroll && this.controller.hasMoreCraftables()) {
                this.controller.requestMoreCraftables();
            }
            return true;
        }
        if (isInsideCategoryList(mouseX, mouseY)) {
            shiftCategoryScroll(scrollY > 0.0D ? -1 : 1);
        }
        return true;
    }

    // 鈹€鈹€ 鍐呴儴鐐瑰嚮澶勭??鈹€鈹€

    private boolean handleCraftDockClick(double mouseX, double mouseY, int button, int x, int y) {
        PanelLayouts.CraftDockLayout dock = resolveCraftDockLayout(x, y);
        if (screen.hasProgressionNode(RtsProgressionNodes.CRAFT_TERMINAL)
                && inside(mouseX, mouseY, dock.cX(), dock.cY(), CRAFT_DOCK_C_SIZE, CRAFT_DOCK_C_SIZE)) {
            this.controller.openCraftTerminal();
            return true;
        }

        if (!screen.hasProgressionNode(RtsProgressionNodes.REMOTE_GUI)) {
            return false;
        }
        for (int slot = 0; slot < this.controller.getGuiBindingCount(); slot++) {
            int slotX = dock.slotX(slot);
            int slotY = dock.slotY(slot);
            if (!inside(mouseX, mouseY, slotX, slotY, CRAFT_DOCK_SLOT_SIZE, CRAFT_DOCK_SLOT_SIZE)) {
                continue;
            }

            if (button == 0) {
                int pendingSlot = screen.getPendingGuiBindSlot();
                if (pendingSlot == slot) {
                    screen.clearPendingGuiBind();
                } else if (this.controller.hasGuiBinding(slot)) {
                    screen.clearPendingGuiBind();
                    this.controller.openGuiBinding(slot);
                } else {
                    screen.setPendingGuiBindSlot(slot);
                }
                return true;
            }

            if (button == 1) {
                if (net.minecraft.client.gui.screens.Screen.hasShiftDown()) {
                    if (screen.getPendingGuiBindSlot() == slot) {
                        screen.clearPendingGuiBind();
                    }
                    this.controller.clearGuiBinding(slot);
                    return true;
                }
                int pendingSlot = screen.getPendingGuiBindSlot();
                screen.setPendingGuiBindSlot(pendingSlot == slot ? -1 : slot);
                return true;
            }

            return true;
        }
        return false;
    }

    private boolean handleSearchClearClick(double mouseX, double mouseY, int searchX, int searchY, int searchAreaWidth) {
        var sb = screen.getSearchBox();
        if (sb == null) {
            return false;
        }
        int clearX = getSearchClearButtonX(searchX, searchAreaWidth);
        int clearY = searchY + 1;
        if (!inside(mouseX, mouseY, clearX, clearY, SEARCH_CLEAR_SIZE, SEARCH_CLEAR_SIZE)) {
            return false;
        }
        sb.setValue("");
        handleStorageSearchChanged("");
        screen.blurSearchFocus();
        return true;
    }

    public void handleStorageSearchChanged(String value) {
        String next = value == null ? "" : value;
        if (activeBottomPanelTab() == BottomPanelLayoutTypes.BottomPanelTab.CREATIVE) {
            this.creativeSearch = next;
            this.creativePage = 0;
            return;
        }
        this.controller.setStorageSearch(next);
    }

    private void syncSearchBoxForActiveTab() {
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

    private boolean handleToolRowClick(double mouseX, double mouseY, int storageX, int rowY, int storageW) {
        if (Minecraft.getInstance() == null || Minecraft.getInstance().player == null) {
            return false;
        }
        if (!inside(mouseX, mouseY, storageX, rowY, storageW, TOOL_AREA_H)) {
            return false;
        }

        int hotbarX = storageX;
        int hotbarW = getHotbarSlotsWidth();
        if (inside(mouseX, mouseY, hotbarX, rowY, hotbarW, HOTBAR_SLOT)) {
            int index = (int) ((mouseX - hotbarX) / HOTBAR_PITCH);
            if (index >= 0 && index <= EMPTY_HAND_BUTTON_INDEX) {
                int slotX = hotbarX + index * HOTBAR_PITCH;
                if (mouseX <= slotX + HOTBAR_SLOT) {
                    if (index == EMPTY_HAND_BUTTON_INDEX) {
                        this.controller.selectEmptyHand();
                        return true;
                    }
                    var stack = Minecraft.getInstance().player.getInventory().getItem(index);
                    if (net.minecraft.client.gui.screens.Screen.hasShiftDown()
                            && RtsClientUiStateStore.isOverlayShiftImportEnabled()
                            && !stack.isEmpty()) {
                        this.controller.storeHotbarSlotToLinked(index);
                        return true;
                    }
                    setSelectedToolSlot(index);
                    this.controller.clearPlacementSelectionPreserveMode();
                    return true;
                }
            }
        }

        int pinStartX = hotbarX + hotbarW + 12;
        int pinVisibleCells = computeVisiblePinCells(pinStartX, storageX + storageW);
        if (pinVisibleCells <= 0 || !inside(mouseX, mouseY, pinStartX, rowY, pinVisibleCells * HOTBAR_PITCH, HOTBAR_SLOT)) {
            return true;
        }

        int cell = (int) ((mouseX - pinStartX) / HOTBAR_PITCH);
        if (cell < 0 || cell >= pinVisibleCells) {
            return true;
        }
        int slotX = pinStartX + cell * HOTBAR_PITCH;
        if (mouseX > slotX + HOTBAR_SLOT) {
            return true;
        }

        int totalPins = this.controller.getQuickSlotCount();
        boolean usePager = shouldUsePinPager(pinVisibleCells, totalPins);
        int slotsPerPage = computePinSlotsPerPage(pinVisibleCells, totalPins);
        int pageCount = Math.max(1, (int) Math.ceil(totalPins / (double) slotsPerPage));
        this.pinPage = Mth.clamp(this.pinPage, 0, pageCount - 1);

        if (usePager && cell == pinVisibleCells - 1) {
            this.pinPage = (this.pinPage + 1) % pageCount;
            return true;
        }

        int pinIndex = this.pinPage * slotsPerPage + cell;
        if (pinIndex < 0 || pinIndex >= totalPins) {
            return true;
        }

        if (net.minecraft.client.gui.screens.Screen.hasShiftDown()) {
            this.controller.clearQuickSlot(pinIndex);
            return true;
        }
        this.controller.selectQuickSlot(pinIndex);
        return true;
    }

    private boolean handleToolRowRightClick(double mouseX, double mouseY, int storageX, int rowY, int storageW) {
        if (Minecraft.getInstance() == null || Minecraft.getInstance().player == null) {
            return false;
        }
        if (!inside(mouseX, mouseY, storageX, rowY, storageW, TOOL_AREA_H)) {
            return false;
        }

        int hotbarX = storageX;
        int hotbarW = getHotbarSlotsWidth();
        if (inside(mouseX, mouseY, hotbarX, rowY, hotbarW, HOTBAR_SLOT)) {
            int index = (int) ((mouseX - hotbarX) / HOTBAR_PITCH);
            if (index >= 0 && index <= EMPTY_HAND_BUTTON_INDEX) {
                int slotX = hotbarX + index * HOTBAR_PITCH;
                if (mouseX <= slotX + HOTBAR_SLOT) {
                    if (index == EMPTY_HAND_BUTTON_INDEX) {
                        this.controller.selectEmptyHand();
                        return true;
                    }
                    this.controller.storeFluidFromToolSlot(index);
                    return true;
                }
            }
        }

        int pinStartX = hotbarX + hotbarW + 12;
        int pinVisibleCells = computeVisiblePinCells(pinStartX, storageX + storageW);
        if (pinVisibleCells <= 0 || !inside(mouseX, mouseY, pinStartX, rowY, pinVisibleCells * HOTBAR_PITCH, HOTBAR_SLOT)) {
            return true;
        }

        int cell = (int) ((mouseX - pinStartX) / HOTBAR_PITCH);
        if (cell < 0 || cell >= pinVisibleCells) {
            return true;
        }

        int slotX = pinStartX + cell * HOTBAR_PITCH;
        if (mouseX > slotX + HOTBAR_SLOT) {
            return true;
        }

        int totalPins = this.controller.getQuickSlotCount();
        boolean usePager = shouldUsePinPager(pinVisibleCells, totalPins);
        int slotsPerPage = computePinSlotsPerPage(pinVisibleCells, totalPins);
        int pageCount = Math.max(1, (int) Math.ceil(totalPins / (double) slotsPerPage));
        this.pinPage = Mth.clamp(this.pinPage, 0, pageCount - 1);

        if (usePager && cell == pinVisibleCells - 1) {
            this.pinPage = (this.pinPage + 1) % pageCount;
            return true;
        }

        int pinIndex = this.pinPage * slotsPerPage + cell;
        if (pinIndex < 0 || pinIndex >= totalPins) {
            return true;
        }

        String itemId = this.controller.getQuickSlotItemId(pinIndex);
        if (itemId != null && !itemId.isBlank()) {
            this.controller.storeFluidFromPinnedItem(itemId);
        }
        return true;
    }

    private boolean handleCraftablesPanelLeftClick(double mouseX, double mouseY, int x, int y, int width, int height) {
        if (!inside(mouseX, mouseY, x, y, width, height)) {
            return false;
        }
        var searchBox = screen.getSearchBox();
        if (searchBox != null && searchBox.isFocused()) {
            searchBox.setFocused(false);
        }

        int searchX = x + 4;
        int searchY = y + 15;
        int searchW = Math.max(24, width - CRAFT_PANEL_APPLY_W - CRAFT_PANEL_TOGGLE_W - 16);
        int applyX = searchX + searchW + 4;
        int toggleX = applyX + CRAFT_PANEL_APPLY_W + 4;

        var craftSearchBox = screen.getCraftSearchBox();
        if (craftSearchBox != null && craftSearchBox.mouseClicked(mouseX, mouseY, 0)) {
            screen.focusCraftSearchBox();
            return true;
        }
        if (inside(mouseX, mouseY, applyX, searchY, CRAFT_PANEL_APPLY_W, CRAFT_PANEL_SEARCH_H)) {
            applyCraftSearchDraft();
            screen.blurSearchFocus();
            return true;
        }
        if (inside(mouseX, mouseY, toggleX, searchY, CRAFT_PANEL_TOGGLE_W, CRAFT_PANEL_SEARCH_H)) {
            this.controller.toggleCraftablesShowUnavailable();
            return true;
        }
        return true;
    }

    public void applyCraftSearchDraft() {
        var csb = screen.getCraftSearchBox();
        String next = normalizeCraftSearchDraft(csb == null ? this.craftSearchDraft : csb.getValue());
        this.craftSearchDraft = next;
        if (csb != null && !next.equals(csb.getValue())) {
            csb.setValue(next);
        }
        this.craftScroll = 0;
        this.controller.setCraftablesSearch(next);
    }

    private boolean handleCraftablesPanelRightClick(double mouseX, double mouseY, int x, int y, int width, int height) {
        int entryIndex = resolveCraftableEntryIndex(mouseX, mouseY, x, y, width, height);
        if (entryIndex < 0 || entryIndex >= this.controller.getCraftableEntries().size()) {
            return inside(mouseX, mouseY, x, y, width, height);
        }
        ClientRtsController.CraftableEntry entry = this.controller.getCraftableEntries().get(entryIndex);
        if (!entry.craftable()) {
            return true;
        }
        openCraftQuantityDialog(entry);
        return true;
    }

    private int resolveCraftableEntryIndex(double mouseX, double mouseY, int x, int y, int width, int height) {
        int searchY = y + 15;
        int gridY = searchY + CRAFT_PANEL_SEARCH_H + 6;
        int visibleRows = Math.max(1, (height - (gridY - y) - 6) / CRAFT_PANEL_PITCH);
        List<ClientRtsController.CraftableEntry> entries = this.controller.getCraftableEntries();
        int totalRows = Math.max(1, (int) Math.ceil(entries.size() / (double) CRAFT_PANEL_COLS));
        int maxScroll = Math.max(0, totalRows - visibleRows);
        this.craftScroll = Mth.clamp(this.craftScroll, 0, maxScroll);

        if (!inside(mouseX, mouseY, x + 4, gridY, CRAFT_PANEL_COLS * CRAFT_PANEL_PITCH, visibleRows * CRAFT_PANEL_PITCH)) {
            return -1;
        }

        int col = (int) ((mouseX - (x + 4)) / CRAFT_PANEL_PITCH);
        int row = (int) ((mouseY - gridY) / CRAFT_PANEL_PITCH);
        if (col < 0 || col >= CRAFT_PANEL_COLS || row < 0 || row >= visibleRows) {
            return -1;
        }
        int slotX = x + 4 + col * CRAFT_PANEL_PITCH;
        int slotY = gridY + row * CRAFT_PANEL_PITCH;
        if (!inside(mouseX, mouseY, slotX, slotY, CRAFT_PANEL_SLOT, CRAFT_PANEL_SLOT)) {
            return -1;
        }

        int index = this.craftScroll * CRAFT_PANEL_COLS + row * CRAFT_PANEL_COLS + col;
        return index < entries.size() ? index : -1;
    }

    // 鈹€鈹€ 甯冨眬涓庤В鏋?鈹€鈹€

    public BottomPanelLayoutTypes.BottomPanelLayout resolveBottomPanelLayout() {
        int dynamicMaxH = Math.max(MIN_BOTTOM_H, Math.min(MAX_BOTTOM_H, screen.height - TOP_H - 16));
        int minH = Math.min(dynamicMaxH, Math.max(MIN_BOTTOM_H, minimumBottomHeightForGridRows(MIN_STORAGE_GRID_ROWS)));
        int maxH = Math.max(minH, dynamicMaxH);

        this.panelHeight = Mth.clamp(this.panelHeight, minH, maxH);

        int panelX = 0;
        int panelY = screen.height - this.panelHeight;
        int panelW = screen.width;
        int panelH = this.panelHeight;
        int contentX = BOTTOM_PANEL_PADDING;
        int contentY = panelY + BOTTOM_PANEL_HEADER_H + 4;
        int sortX = contentX;
        int sortY = contentY + 2;
        int categoryX = sortX + 58;
        int categoryY = contentY;
        int categoryH = Math.max(24, panelY + panelH - BOTTOM_PANEL_PADDING - categoryY);
        int storageX = categoryX + CATEGORY_W + 10;
        int storageY = contentY;
        int storageW = Math.max(120, panelW - BOTTOM_PANEL_PADDING - storageX);
        int craftPanelX = storageX + Math.max(120, storageW - CRAFT_PANEL_W);
        int mainStorageW = Math.max(120, craftPanelX - storageX - CRAFT_PANEL_GAP);
        int searchW = Math.max(72, mainStorageW - 82);
        int pagerX = Math.min(storageX + searchW + 4, craftPanelX - 80);
        searchW = Math.max(56, pagerX - storageX - 4);
        int toolY = storageY + 17;
        int gridY = toolY + TOOL_AREA_H + 4;
        int gridH = Math.max(SLOT, panelY + panelH - BOTTOM_PANEL_PADDING - gridY);
        int storageRows = Math.max(1, gridH / SLOT);
        int craftPanelY = storageY;
        int craftPanelH = Math.max(CRAFT_PANEL_SEARCH_H + CRAFT_PANEL_SLOT + 27, panelY + panelH - BOTTOM_PANEL_PADDING - craftPanelY);

        return new BottomPanelLayoutTypes.BottomPanelLayout(
                panelX, panelY, panelW, panelH,
                sortX, sortY,
                categoryX, categoryY, categoryH,
                storageX, storageY, storageW,
                craftPanelX, mainStorageW, searchW, pagerX,
                toolY, gridY, gridH, storageRows,
                craftPanelY, craftPanelH);
    }

    private int panelHeight = DEFAULT_BOTTOM_H;

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

    public boolean isInsideCategoryList(double mouseX, double mouseY) {
        BottomPanelLayoutTypes.BottomPanelLayout layout = resolveBottomPanelLayout();
        int listY = layout.categoryY() + 13;
        int listH = layout.categoryH() - 15;
        return inside(mouseX, mouseY, layout.categoryX() + 2, listY, CATEGORY_W - 4, listH);
    }

    private void shiftCategoryScroll(int delta) {
        int visible = Math.max(1, (getBottomHeight() - 15) / CATEGORY_ROW_H);
        int maxScroll = Math.max(0, buildCategoryRows().size() - visible);
        this.categoryScroll = Mth.clamp(this.categoryScroll + delta, 0, maxScroll);
    }

    private int getBottomHeight() {
        return resolveBottomPanelLayout().panelH();
    }

    private void adjustBottomPanelSize(int direction) {
        int dynamicMaxH = Math.max(MIN_BOTTOM_H, Math.min(MAX_BOTTOM_H, screen.height - TOP_H - 16));
        int minH = Math.min(dynamicMaxH, Math.max(MIN_BOTTOM_H, minimumBottomHeightForGridRows(MIN_STORAGE_GRID_ROWS)));
        this.panelHeight = Mth.clamp(this.panelHeight + (direction * SLOT), minH, dynamicMaxH);
    }

    private int minimumBottomHeightForGridRows(int rows) {
        int gridTopOffset = BOTTOM_PANEL_HEADER_H + 4 + 17 + TOOL_AREA_H + 4;
        return gridTopOffset + BOTTOM_PANEL_PADDING + (Math.max(1, rows) * SLOT);
    }

    private PanelLayouts.CraftDockLayout resolveCraftDockLayout(int x, int y) {
        int cX = x + 14;
        int cY = y + CRAFT_DOCK_SLOT_SIZE + CRAFT_DOCK_GAP;
        return new PanelLayouts.CraftDockLayout(cX, cY);
    }

    private BottomPanelLayoutTypes.BottomPanelTab resolveBottomPanelTabClick(BottomPanelLayoutTypes.BottomPanelLayout layout, double mouseX, double mouseY) {
        for (BottomPanelLayoutTypes.BottomPanelTab tab : visibleBottomPanelTabs()) {
            if (inside(mouseX, mouseY, bottomPanelTabX(layout, tab), layout.panelY() + 2, bottomPanelTabW(tab), BOTTOM_PANEL_HEADER_H - 3)) {
                return tab;
            }
        }
        return null;
    }

    private int bottomGuideButtonX(BottomPanelLayoutTypes.BottomPanelLayout layout) {
        return layout.panelX() + layout.panelW() - 20;
    }

    private int bottomRefreshButtonX(BottomPanelLayoutTypes.BottomPanelLayout layout) {
        return bottomGuideButtonX(layout) - 16;
    }

    private int bottomGuideButtonY(BottomPanelLayoutTypes.BottomPanelLayout layout) {
        return layout.panelY() + 3;
    }

    // 鈹€鈹€ 鍒嗙被鏋勫缓 鈹€鈹€

    private List<CategoryTypes.CategoryRow> buildCategoryRows() {
        if (activeBottomPanelTab() == BottomPanelLayoutTypes.BottomPanelTab.CREATIVE) {
            List<CategoryTypes.CategoryRow> rows = new ArrayList<>();
            for (RtsCreativeItemCatalog.CreativeCategory category : RtsCreativeItemCatalog.get().categories()) {
                String label = "all".equals(category.token())
                        ? Component.translatable("screen.rtsbuilding.creative.all").getString()
                        : category.label();
                rows.add(new CategoryTypes.CategoryRow(category.token(), label, 0, false, false, ""));
            }
            return rows;
        }

        List<CategoryTypes.CategoryRow> rows = new ArrayList<>();
        rows.add(new CategoryTypes.CategoryRow(
                CATEGORY_ALL,
                Component.translatable("screen.rtsbuilding.creative.all").getString(),
                0,
                false,
                false,
                ""));

        Map<String, Set<String>> modToTabs = new HashMap<>();
        Set<String> mods = new HashSet<>();

        for (String raw : this.controller.getStorageCategories()) {
            String category = normalizeCategoryToken(raw);
            if (category.isEmpty() || CATEGORY_ALL.equals(category)) {
                continue;
            }
            if (category.startsWith(CATEGORY_MOD_PREFIX)) {
                String mod = category.substring(CATEGORY_MOD_PREFIX.length());
                if (!mod.isBlank()) {
                    mods.add(mod);
                    modToTabs.computeIfAbsent(mod, ignored -> new HashSet<>());
                }
                continue;
            }
            if (category.startsWith(CATEGORY_TAB_PREFIX)) {
                String payload = category.substring(CATEGORY_TAB_PREFIX.length());
                int split = payload.indexOf('|');
                if (split <= 0 || split >= payload.length() - 1) {
                    continue;
                }
                String mod = payload.substring(0, split);
                String tab = payload.substring(split + 1);
                if (mod.isBlank() || tab.isBlank()) {
                    continue;
                }
                mods.add(mod);
                modToTabs.computeIfAbsent(mod, ignored -> new HashSet<>()).add(tab);
                continue;
            }

            mods.add(category);
            modToTabs.computeIfAbsent(category, ignored -> new HashSet<>());
        }

        String selected = normalizeCategoryToken(this.controller.getStorageCategory());
        if (selected.startsWith(CATEGORY_TAB_PREFIX)) {
            String payload = selected.substring(CATEGORY_TAB_PREFIX.length());
            int split = payload.indexOf('|');
            if (split > 0) {
                this.expandedCategoryMods.add(payload.substring(0, split));
            }
        }

        List<String> orderedMods = new ArrayList<>(mods);
        orderedMods.sort(BottomPanel::compareNamespace);

        for (String mod : orderedMods) {
            List<String> tabs = new ArrayList<>(modToTabs.getOrDefault(mod, Set.of()));
            tabs.sort(BottomPanel::compareTabKey);
            boolean expandable = !tabs.isEmpty();
            boolean expanded = expandable && this.expandedCategoryMods.contains(mod);
            rows.add(new CategoryTypes.CategoryRow(encodeModCategory(mod), formatModLabel(mod), 0, expandable, expanded, mod));
            if (!expanded) {
                continue;
            }
            for (String tab : tabs) {
                rows.add(new CategoryTypes.CategoryRow(encodeTabCategory(mod, tab), formatTabLabel(tab), 1, false, false, mod));
            }
        }

        return rows;
    }

    private String activeCategoryToken() {
        return activeBottomPanelTab() == BottomPanelLayoutTypes.BottomPanelTab.CREATIVE
                ? this.creativeCategory
                : this.controller.getStorageCategory();
    }

    private List<RtsCreativeItemCatalog.CreativeEntry> creativeEntriesForCurrentFilter() {
        return RtsCreativeItemCatalog.get().entries(this.creativeCategory, this.creativeSearch);
    }

    private int creativePageCount(int width, int height) {
        int cols = Math.max(1, width / SLOT);
        int rows = Math.max(1, height / SLOT);
        int maxSlots = Math.max(1, cols * rows);
        return Math.max(1, (int) Math.ceil(creativeEntriesForCurrentFilter().size() / (double) maxSlots));
    }

    private int creativeGridWidth(int mainStorageW) {
        return Math.max(SLOT, (mainStorageW - STORAGE_RECENT_GAP) / 2);
    }

    public RtsCreativeItemCatalog.CreativeEntry getCreativeEntryForTooltip(int index) {
        List<RtsCreativeItemCatalog.CreativeEntry> entries = creativeEntriesForCurrentFilter();
        return index >= 0 && index < entries.size() ? entries.get(index) : null;
    }

    private void toggleCategoryExpansion(String modNamespace) {
        if (modNamespace == null || modNamespace.isBlank()) {
            return;
        }
        if (this.expandedCategoryMods.contains(modNamespace)) {
            this.expandedCategoryMods.remove(modNamespace);
        } else {
            this.expandedCategoryMods.add(modNamespace);
        }
    }

    private static String normalizeCategoryToken(String token) {
        if (token == null) {
            return CATEGORY_ALL;
        }
        String value = token.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty()) {
            return CATEGORY_ALL;
        }
        return value;
    }

    private static String encodeModCategory(String modNamespace) {
        return CATEGORY_MOD_PREFIX + modNamespace;
    }

    private static String encodeTabCategory(String modNamespace, String tabKey) {
        return CATEGORY_TAB_PREFIX + modNamespace + "|" + tabKey;
    }

    private static int compareNamespace(String a, String b) {
        if ("minecraft".equals(a)) {
            return "minecraft".equals(b) ? 0 : -1;
        }
        if ("minecraft".equals(b)) {
            return 1;
        }
        return a.compareToIgnoreCase(b);
    }

    private static int compareTabKey(String a, String b) {
        String aName = formatTabLabel(a);
        String bName = formatTabLabel(b);
        int byLabel = aName.compareToIgnoreCase(bName);
        return byLabel != 0 ? byLabel : a.compareToIgnoreCase(b);
    }

    private static String formatModLabel(String modNamespace) {
        try {
            return ModList.get().getModContainerById(modNamespace)
                    .map(container -> container.getModInfo().getDisplayName())
                    .filter(label -> label != null && !label.isBlank())
                    .orElseGet(() -> humanizeToken(modNamespace));
        } catch (RuntimeException | LinkageError ignored) {
            return humanizeToken(modNamespace);
        }
    }

    private static String formatTabLabel(String tabKey) {
        ResourceLocation key = ResourceLocation.tryParse(tabKey);
        if (key != null) {
            try {
                CreativeModeTab tab = BuiltInRegistries.CREATIVE_MODE_TAB.get(key);
                if (tab != null) {
                    String label = tab.getDisplayName().getString();
                    if (label != null && !label.isBlank()) {
                        return label;
                    }
                }
            } catch (RuntimeException | LinkageError ignored) {
                // Fall through to a readable token when a modded creative tab misbehaves.
            }
        }
        String path = key == null ? tabKey : key.getPath();
        return humanizeToken(path);
    }

    private static String humanizeToken(String token) {
        if (token == null || token.isBlank()) {
            return "";
        }
        String normalized = token.replace('_', ' ').replace('-', ' ').trim();
        if (normalized.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(normalized.length());
        boolean upper = true;
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            if (c == ' ') {
                sb.append(c);
                upper = true;
            } else if (upper) {
                sb.append(Character.toUpperCase(c));
                upper = false;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    // 鈹€鈹€ 鐐瑰嚮鍧愭爣瑙ｆ??鈹€鈹€

    private int resolveClickedCreativeEntry(double mouseX, double mouseY, int x, int y, int width, int height) {
        int cols = Math.max(1, width / SLOT);
        int rows = Math.max(1, height / SLOT);
        int maxSlots = cols * rows;
        if (!inside(mouseX, mouseY, x, y, cols * SLOT, rows * SLOT)) {
            return -1;
        }
        int col = (int) ((mouseX - x) / SLOT);
        int row = (int) ((mouseY - y) / SLOT);
        int index = this.creativePage * maxSlots + row * cols + col;
        return index < creativeEntriesForCurrentFilter().size() ? index : -1;
    }

    private int resolveClickedEntry(double mouseX, double mouseY, int x, int y, int width, int height) {
        int cols = Math.max(1, width / SLOT);
        int rows = Math.max(1, height / SLOT);
        if (!inside(mouseX, mouseY, x, y, cols * SLOT, rows * SLOT)) {
            return -1;
        }
        int col = (int) ((mouseX - x) / SLOT);
        int row = (int) ((mouseY - y) / SLOT);
        int index = row * cols + col;
        return index < this.controller.getStorageEntries().size() ? index : -1;
    }

    private int resolveClickedRecentEntry(double mouseX, double mouseY, int x, int y, int width, int height) {
        int cols = Math.max(1, width / SLOT);
        int rows = Math.max(1, height / SLOT);
        if (!inside(mouseX, mouseY, x, y, cols * SLOT, rows * SLOT)) {
            return -1;
        }
        int col = (int) ((mouseX - x) / SLOT);
        int row = (int) ((mouseY - y) / SLOT);
        int index = row * cols + col;
        return index < this.controller.getRecentEntries().size() ? index : -1;
    }

    private int resolveClickedFluid(double mouseX, double mouseY, int x, int y, int width, int height) {
        int cols = 2;
        int rows = Math.max(1, height / SLOT);
        if (!inside(mouseX, mouseY, x, y, cols * SLOT, rows * SLOT)) {
            return -1;
        }
        int col = (int) ((mouseX - x) / SLOT);
        int row = (int) ((mouseY - y) / SLOT);
        int index = row * cols + col;
        return index < this.controller.getFluidEntries().size() ? index : -1;
    }

    private CategoryTypes.CategoryClick resolveClickedCategoryAction(double mouseX, double mouseY) {
        BottomPanelLayoutTypes.BottomPanelLayout layout = resolveBottomPanelLayout();
        int categoryX = layout.categoryX();
        int categoryY = layout.categoryY();
        int listY = categoryY + 13;
        int listH = layout.categoryH() - 15;

        if (!inside(mouseX, mouseY, categoryX + 2, listY, CATEGORY_W - 4, listH)) {
            return null;
        }

        int visible = Math.max(1, listH / CATEGORY_ROW_H);
        int row = (int) ((mouseY - listY) / CATEGORY_ROW_H);
        if (row < 0 || row >= visible) {
            return null;
        }

        List<CategoryTypes.CategoryRow> rows = buildCategoryRows();
        int index = this.categoryScroll + row;
        if (index < 0 || index >= rows.size()) {
            return null;
        }

        CategoryTypes.CategoryRow clicked = rows.get(index);
        if (clicked.expandable()) {
            int rowY = listY + row * CATEGORY_ROW_H;
            int toggleX = categoryX + CATEGORY_W - 12;
            if (inside(mouseX, mouseY, toggleX, rowY + 1, 9, CATEGORY_ROW_H - 3)) {
                return new CategoryTypes.CategoryClick(clicked.token(), clicked.modNamespace(), true);
            }
        }
        return new CategoryTypes.CategoryClick(clicked.token(), clicked.modNamespace(), false);
    }

    // 鈹€鈹€ Pin / 宸ュ叿鏍忚緟??鈹€鈹€

    private long resolvePinnedItemCount(String itemId) {
        return this.controller.getStorageTotalCount(itemId);
    }

    private int getSelectedToolSlot() {
        if (Minecraft.getInstance() == null || Minecraft.getInstance().player == null) {
            return 0;
        }
        return Mth.clamp(Minecraft.getInstance().player.getInventory().selected, 0, 8);
    }

    private void setSelectedToolSlot(int slot) {
        if (Minecraft.getInstance() == null || Minecraft.getInstance().player == null) {
            return;
        }
        Minecraft.getInstance().player.getInventory().selected = Mth.clamp(slot, 0, 8);
    }

    private int getHotbarSlotsWidth() {
        return HOTBAR_PITCH * (TOOL_HOTBAR_ITEM_SLOTS + 1) - (HOTBAR_PITCH - HOTBAR_SLOT);
    }

    private int getFluidStripWidth(int storageWidth) {
        int wanted = SLOT * 2;
        if (storageWidth < wanted + SLOT * 3) {
            return 0;
        }
        return wanted;
    }

    private int computeVisiblePinCells(int pinStartX, int rightBoundExclusive) {
        int visible = 0;
        for (int i = 0; i < this.controller.getQuickSlotCount(); i++) {
            int cx = pinStartX + i * HOTBAR_PITCH;
            if (cx + HOTBAR_SLOT > rightBoundExclusive) {
                break;
            }
            visible++;
        }
        return visible;
    }

    private boolean shouldUsePinPager(int visibleCells, int totalPins) {
        return visibleCells >= 2 && totalPins > visibleCells;
    }

    private int computePinSlotsPerPage(int visibleCells, int totalPins) {
        if (visibleCells <= 0) {
            return 1;
        }
        if (shouldUsePinPager(visibleCells, totalPins)) {
            return Math.max(1, visibleCells - 1);
        }
        return visibleCells;
    }

    // 鈹€鈹€ 鎺掑簭鏍囩 鈹€鈹€

    private static String sortLabel(RtsStorageSort sort) {
        return switch (sort) {
            case QUANTITY -> "Qty";
            case MOD -> "Mod";
            case NAME -> "Name";
        };
    }

    // 鈹€鈹€ 宸ュ??鈹€鈹€

    private static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }

    public void syncCraftablesPanelState() {
        if (this.lastCraftablesStorageRevision != this.controller.getStorageRevision()) {
            this.lastCraftablesStorageRevision = this.controller.getStorageRevision();
            this.controller.requestCraftables();
        }
        syncCraftSearchValueFromController();
    }
}
