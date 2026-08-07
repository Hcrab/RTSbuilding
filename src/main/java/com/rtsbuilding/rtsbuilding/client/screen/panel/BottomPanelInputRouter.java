package com.rtsbuilding.rtsbuilding.client.screen.panel;

import com.rtsbuilding.rtsbuilding.client.screen.blueprint.BlueprintPanel;
import com.rtsbuilding.rtsbuilding.client.input.RtsModifierKeys;
import com.rtsbuilding.rtsbuilding.client.input.RtsWidgetCompat;
import com.rtsbuilding.rtsbuilding.client.screen.layout.BottomPanelLayoutTypes;
import com.rtsbuilding.rtsbuilding.uicore.bottom.BottomBarUiAction;
import com.rtsbuilding.rtsbuilding.uicore.bottom.BottomBarUiCategory;
import com.rtsbuilding.rtsbuilding.uicore.bottom.BottomBarUiState;
import com.rtsbuilding.rtsbuilding.uicore.bottom.BottomBarUiTab;
import com.rtsbuilding.rtsbuilding.uicore.event.UiPointerEvent;
import com.rtsbuilding.rtsbuilding.uikit.layout.BottomPanelBlueprintLayout;
import com.rtsbuilding.rtsbuilding.uikit.layout.BottomPanelBrowseLayout;
import com.rtsbuilding.rtsbuilding.uikit.layout.BottomPanelCategoryLayout;
import com.rtsbuilding.rtsbuilding.uikit.layout.BottomPanelCraftDockLayout;
import com.rtsbuilding.rtsbuilding.uikit.layout.BottomPanelHeaderLayout;
import com.rtsbuilding.rtsbuilding.uikit.layout.BottomPanelSortLayout;
import net.minecraft.client.gui.screens.Screen;

/**
 * 搴曟爮鐢熶骇杈撳叆鐨勭獎閫傞厤鍣ㄤ笌鍞竴浼樺厛绾ц矾鐢便€? *
 * <p>鏈被鍙妸 Minecraft 鐨勬寜鍘?婊氳疆缈昏瘧鎴?{@link UiPointerEvent}锛屾寜鐓уご閮ㄣ€佹枃鏈銆? * 宸ヨ壓鍖恒€佸垎绫汇€佸伐鍏疯鍜岀綉鏍肩殑鏃㈠畾浼樺厛绾у懡涓?Kit 甯冨眬锛屽啀鎶婁笟鍔″姩浣滀氦鍥? * {@link BottomPanel#dispatchCore(BottomBarUiAction)}銆傚畠涓嶇粯鍒躲€佷笉鎸佷箙鍖栫浜屼唤鐘舵€侊紝
 * 涔熶笉缁曡繃 BottomPanel 鐙珛鎵ц reducer 鎴栧钩鍙板壇浣滅敤锛汢ottomPanel 浠嶇劧鎷ユ湁鐢熷懡鍛ㄦ湡銆? * 鎺у埗鍣ㄥ拰鐘舵€佹満銆?/p>
 */
final class BottomPanelInputRouter {
    private final BottomPanel panel;
    private final BottomPanelToolInput toolInput;
    private final BottomPanelCraftInput craftInput;
    private final BottomPanelGridInput gridInput;

    BottomPanelInputRouter(BottomPanel panel) {
        this.panel = panel;
        this.toolInput = panel.toolInput;
        this.craftInput = panel.craftInput;
        this.gridInput = panel.gridInput;
    }

    boolean mousePressed(double x, double y, int button) {
        return route(new UiPointerEvent(
                UiPointerEvent.Type.PRESS, x, y, button, 0.0D, 0.0D, 0));
    }

    boolean mouseScrolled(double x, double y, double scrollY) {
        return route(new UiPointerEvent(
                UiPointerEvent.Type.SCROLL, x, y, UiPointerEvent.NO_BUTTON,
                0.0D, scrollY, 0));
    }

    private boolean route(UiPointerEvent event) {
        BottomPanelLayoutTypes.BottomPanelLayout layout =
                panel.resolveBottomPanelLayout();
        if (!layout.contains(event.getX(), event.getY())) {
            return false;
        }
        return switch (event.getType()) {
            case PRESS -> routePress(event, layout);
            case SCROLL -> routeScroll(event, layout);
            default -> false;
        };
    }

    private boolean routePress(
            UiPointerEvent event,
            BottomPanelLayoutTypes.BottomPanelLayout layout) {
        if (event.getButton() == 0) {
            return routeLeftPress(event.getX(), event.getY(), layout);
        }
        if (event.getButton() == 1) {
            return routeRightPress(event.getX(), event.getY(), layout);
        }
        return true;
    }

    private boolean routeLeftPress(
            double mouseX,
            double mouseY,
            BottomPanelLayoutTypes.BottomPanelLayout layout) {
        BottomPanelLayoutTypes.BottomPanelTab activeTab =
                panel.activeBottomPanelTab();
        BottomPanelHeaderLayout header = panel.resolveHeaderLayout(
                layout, panel.selectedPlacementStatusText());
        BottomBarUiTab clickedTab = header.tabAt(mouseX, mouseY);
        if (clickedTab != null) {
            panel.dispatchCore(BottomBarUiAction.tab(clickedTab));
            panel.syncSearchBoxForActiveTab();
            panel.screen.blurSearchFocus();
            return true;
        }
        BottomPanelHeaderLayout.Control headerControl =
                header.controlAt(mouseX, mouseY);
        if (headerControl != null) {
            handleHeaderControl(headerControl, activeTab);
            return true;
        }
        if (header.containsHeader(mouseX, mouseY)) {
            return true;
        }
        if (activeTab == BottomPanelLayoutTypes.BottomPanelTab.BLUEPRINTS) {
            BottomPanelHeaderLayout.Area content =
                    resolveBlueprintLayout(layout).content;
            return BlueprintPanel.mouseClicked(
                    mouseX, mouseY,
                    content.x, content.y, content.width, content.height,
                    panel.controller);
        }

        BottomPanelBrowseLayout browseLayout =
                BottomPanel.resolveBrowseLayout(layout);
        if (panel.handleSearchClear(mouseX, mouseY, browseLayout)) {
            return true;
        }

        var searchBox = panel.screen.getSearchBox();
        if (searchBox != null && RtsWidgetCompat.mouseClicked(searchBox, mouseX, mouseY, 0)) {
            panel.screen.focusStorageSearchBox();
            return true;
        }

        if (activeTab != BottomPanelLayoutTypes.BottomPanelTab.CREATIVE
                && craftInput.leftPressed(mouseX, mouseY, layout)) {
            return true;
        }
        panel.screen.blurSearchFocus();

        BottomPanelSortLayout sortLayout = BottomPanelSortLayout.resolve(
                layout.sortX(), layout.sortY());
        BottomPanelSortLayout.Control sortControl =
                sortLayout.controlAt(mouseX, mouseY);
        if (sortControl != null) {
            handleSortControl(sortControl);
            return true;
        }
        if (panel.handleCraftDockPress(
                mouseX, mouseY, 0, panel.resolveCraftDockLayout(layout))) {
            return true;
        }
        if (panel.handleCategoryPress(mouseX, mouseY, layout)) {
            return true;
        }
        if (panel.routeToolLeft(mouseX, mouseY, layout)) {
            return true;
        }

        if (panel.handleBrowsePage(mouseX, mouseY, browseLayout)) {
            return true;
        }
        return gridInput.leftPressed(mouseX, mouseY, activeTab, layout);
    }

    private boolean routeRightPress(
            double mouseX,
            double mouseY,
            BottomPanelLayoutTypes.BottomPanelLayout layout) {
        BottomPanelLayoutTypes.BottomPanelTab activeTab =
                panel.activeBottomPanelTab();
        if (panel.resolveHeaderLayout(
                layout, panel.selectedPlacementStatusText())
                .containsHeader(mouseX, mouseY)) {
            return true;
        }
        if (activeTab == BottomPanelLayoutTypes.BottomPanelTab.BLUEPRINTS) {
            return true;
        }
        if (panel.handleCraftDockPress(
                mouseX, mouseY, 1, panel.resolveCraftDockLayout(layout))) {
            return true;
        }
        if (panel.routeToolRight(mouseX, mouseY, layout)) {
            return true;
        }
        if (activeTab != BottomPanelLayoutTypes.BottomPanelTab.CREATIVE
                && craftInput.rightPressed(mouseX, mouseY, layout)) {
            return true;
        }
        if (activeTab == BottomPanelLayoutTypes.BottomPanelTab.CREATIVE) {
            return true;
        }

        gridInput.rightPressedStorage(mouseX, mouseY, layout);
        return true;
    }

    private boolean routeScroll(
            UiPointerEvent event,
            BottomPanelLayoutTypes.BottomPanelLayout layout) {
        double mouseX = event.getX();
        double mouseY = event.getY();
        double scrollY = event.getDeltaY();
        BottomPanelLayoutTypes.BottomPanelTab activeTab =
                panel.activeBottomPanelTab();
        if (activeTab == BottomPanelLayoutTypes.BottomPanelTab.BLUEPRINTS) {
            return panel.handleBlueprintScroll(mouseX, mouseY, scrollY, layout);
        }
        if (activeTab == BottomPanelLayoutTypes.BottomPanelTab.CREATIVE) {
            if (isInsideCategoryList(mouseX, mouseY, layout)) {
                panel.scrollCategories(scrollY > 0.0D ? -1 : 1, layout);
                return true;
            }
            panel.dispatchCore(BottomBarUiAction.simple(scrollY > 0.0D
                    ? BottomBarUiAction.Type.PREVIOUS_PAGE
                    : BottomBarUiAction.Type.NEXT_PAGE));
            return true;
        }

        if (craftInput.mouseScrolled(mouseX, mouseY, scrollY, layout)) {
            return true;
        }
        if (isInsideCategoryList(mouseX, mouseY, layout)) {
            panel.scrollCategories(scrollY > 0.0D ? -1 : 1, layout);
            return true;
        }
        if (storageBrowseArea(layout).contains(mouseX, mouseY)) {
            if (scrollY > 0.0D) {
                panel.dispatchCore(BottomBarUiAction.simple(
                        BottomBarUiAction.Type.PREVIOUS_PAGE));
            } else if (scrollY < 0.0D) {
                panel.dispatchCore(BottomBarUiAction.simple(
                        BottomBarUiAction.Type.NEXT_PAGE));
            }
        }
        return true;
    }

    private void handleHeaderControl(
            BottomPanelHeaderLayout.Control control,
            BottomPanelLayoutTypes.BottomPanelTab activeTab) {
        switch (control) {
            case REFRESH -> {
                if (activeTab
                        == BottomPanelLayoutTypes.BottomPanelTab.BLUEPRINTS) {
                    BlueprintPanel.reload();
                } else {
                    panel.dispatchCore(BottomBarUiAction.simple(
                            BottomBarUiAction.Type.REFRESH));
                }
            }
            case OPEN_GUIDE -> panel.dispatchCore(BottomBarUiAction.simple(
                    BottomBarUiAction.Type.OPEN_GUIDE));
            case OPEN_PLUGINS -> panel.dispatchCore(BottomBarUiAction.simple(
                    BottomBarUiAction.Type.OPEN_PLUGINS));
        }
    }

    private void handleSortControl(BottomPanelSortLayout.Control control) {
        switch (control) {
            case CYCLE_SORT -> panel.dispatchCore(BottomBarUiAction.simple(
                    BottomBarUiAction.Type.CYCLE_SORT));
            case TOGGLE_DIRECTION -> panel.dispatchCore(
                    BottomBarUiAction.simple(
                            BottomBarUiAction.Type.TOGGLE_SORT_DIRECTION));
            case INCREASE_HEIGHT -> panel.adjustBottomPanelSize(1);
            case DECREASE_HEIGHT -> panel.adjustBottomPanelSize(-1);
        }
    }

    private boolean isInsideCategoryList(
            double mouseX,
            double mouseY,
            BottomPanelLayoutTypes.BottomPanelLayout layout) {
        return BottomPanel.resolveCategoryLayout(layout, 0, 0)
                .list.contains(mouseX, mouseY);
    }

    private static BottomPanelBlueprintLayout resolveBlueprintLayout(
            BottomPanelLayoutTypes.BottomPanelLayout layout) {
        return BottomPanelBlueprintLayout.resolve(
                layout.panelX(), layout.panelY(),
                layout.panelW(), layout.panelH());
    }

    private static BottomPanelHeaderLayout.Area storageBrowseArea(
            BottomPanelLayoutTypes.BottomPanelLayout layout) {
        return BottomPanelHeaderLayout.area(
                layout.storageX(), layout.storageY(),
                layout.mainStorageW(),
                layout.gridY() + layout.gridH() - layout.storageY());
    }
}
