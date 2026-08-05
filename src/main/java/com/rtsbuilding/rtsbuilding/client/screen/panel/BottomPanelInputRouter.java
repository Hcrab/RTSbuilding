package com.rtsbuilding.rtsbuilding.client.screen.panel;

import com.rtsbuilding.rtsbuilding.client.screen.blueprint.BlueprintPanel;
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
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;

/**
 * 底栏生产输入的窄适配器与唯一优先级路由。
 *
 * <p>本类只把 Minecraft 的按压/滚轮翻译成 {@link UiPointerEvent}，按照头部、文本框、
 * 工艺区、分类、工具行和网格的既定优先级命中 Kit 布局，再把业务动作交回
 * {@link BottomPanel#dispatchCore(BottomBarUiAction)}。它不绘制、不持久化第二份状态，
 * 也不绕过 BottomPanel 独立执行 reducer 或平台副作用；BottomPanel 仍然拥有生命周期、
 * 控制器和状态机。</p>
 */
final class BottomPanelInputRouter {
    private final BottomPanel panel;
    private final BottomPanelToolInput toolInput;
    private final BottomPanelCraftInput craftInput;
    private final BottomPanelGridInput gridInput;

    BottomPanelInputRouter(BottomPanel panel) {
        this.panel = panel;
        this.toolInput = new BottomPanelToolInput(panel);
        this.craftInput = new BottomPanelCraftInput(panel);
        this.gridInput = new BottomPanelGridInput(panel);
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
        switch (event.getType()) {
            case PRESS:
                return routePress(event, layout);
            case SCROLL:
                return routeScroll(event, layout);
            default:
                return false;
        }
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
        if (handleSearchClear(mouseX, mouseY, browseLayout)) {
            return true;
        }

        GuiTextField searchBox = panel.screen.getSearchBox();
        if (searchBox != null && contains(searchBox, mouseX, mouseY)) {
            searchBox.mouseClicked((int) mouseX, (int) mouseY, 0);
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
        if (handleCraftDockPress(
                mouseX, mouseY, 0, panel.resolveCraftDockLayout(layout))) {
            return true;
        }
        if (handleCategoryPress(mouseX, mouseY, layout)) {
            return true;
        }
        if (toolInput.mousePressed(mouseX, mouseY, 0, layout)) {
            return true;
        }

        if (browseLayout.previousPage.contains(mouseX, mouseY)) {
            panel.dispatchCore(BottomBarUiAction.simple(
                    BottomBarUiAction.Type.PREVIOUS_PAGE));
            return true;
        }
        if (browseLayout.nextPage.contains(mouseX, mouseY)) {
            panel.dispatchCore(BottomBarUiAction.simple(
                    BottomBarUiAction.Type.NEXT_PAGE));
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
        if (handleCraftDockPress(
                mouseX, mouseY, 1, panel.resolveCraftDockLayout(layout))) {
            return true;
        }
        if (toolInput.mousePressed(mouseX, mouseY, 1, layout)) {
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
            BottomPanelHeaderLayout.Area content =
                    resolveBlueprintLayout(layout).content;
            BlueprintPanel.mouseScrolled(
                    mouseX, mouseY, scrollY,
                    content.x, content.y, content.width, content.height,
                    panel.controller);
            return true;
        }
        if (activeTab == BottomPanelLayoutTypes.BottomPanelTab.CREATIVE) {
            if (isInsideCategoryList(mouseX, mouseY, layout)) {
                scrollCategories(scrollY > 0.0D ? -1 : 1, layout);
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
            scrollCategories(scrollY > 0.0D ? -1 : 1, layout);
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
            case REFRESH:
                if (activeTab
                        == BottomPanelLayoutTypes.BottomPanelTab.BLUEPRINTS) {
                    BlueprintPanel.reload();
                } else {
                    panel.dispatchCore(BottomBarUiAction.simple(
                            BottomBarUiAction.Type.REFRESH));
                }
                break;
            case OPEN_GUIDE:
                panel.dispatchCore(BottomBarUiAction.simple(
                        BottomBarUiAction.Type.OPEN_GUIDE));
                break;
            case OPEN_PLUGINS:
                panel.dispatchCore(BottomBarUiAction.simple(
                        BottomBarUiAction.Type.OPEN_PLUGINS));
                break;
            default:
                break;
        }
    }

    private void handleSortControl(BottomPanelSortLayout.Control control) {
        switch (control) {
            case CYCLE_SORT:
                panel.dispatchCore(BottomBarUiAction.simple(
                        BottomBarUiAction.Type.CYCLE_SORT));
                break;
            case TOGGLE_DIRECTION:
                panel.dispatchCore(BottomBarUiAction.simple(
                        BottomBarUiAction.Type.TOGGLE_SORT_DIRECTION));
                break;
            case INCREASE_HEIGHT:
                panel.adjustBottomPanelSize(1);
                break;
            case DECREASE_HEIGHT:
                panel.adjustBottomPanelSize(-1);
                break;
            default:
                break;
        }
    }

    private boolean handleSearchClear(
            double mouseX,
            double mouseY,
            BottomPanelBrowseLayout browseLayout) {
        GuiTextField searchBox = panel.screen.getSearchBox();
        if (searchBox == null
                || !browseLayout.clearSearch.contains(mouseX, mouseY)) {
            return false;
        }
        searchBox.setText("");
        panel.dispatchCore(BottomBarUiAction.simple(
                BottomBarUiAction.Type.CLEAR_SEARCH));
        panel.screen.blurSearchFocus();
        return true;
    }

    private boolean handleCraftDockPress(
            double mouseX,
            double mouseY,
            int button,
            BottomPanelCraftDockLayout dock) {
        if (dock.craftButton.contains(mouseX, mouseY)) {
            panel.dispatchCore(BottomBarUiAction.simple(
                    BottomBarUiAction.Type.OPEN_CRAFT_TERMINAL));
            return true;
        }
        int slot = dock.slotIndexAt(mouseX, mouseY);
        if (slot < 0) {
            return false;
        }
        if (button == 0) {
            panel.dispatchCore(BottomBarUiAction.index(
                    BottomBarUiAction.Type.SELECT_GUI_BINDING, slot));
        } else if (button == 1 && isShiftDown()) {
            panel.dispatchCore(BottomBarUiAction.index(
                    BottomBarUiAction.Type.CLEAR_GUI_BINDING, slot));
        } else if (button == 1) {
            panel.dispatchCore(BottomBarUiAction.index(
                    BottomBarUiAction.Type.TOGGLE_GUI_BINDING_PENDING, slot));
        }
        return true;
    }

    private boolean handleCategoryPress(
            double mouseX,
            double mouseY,
            BottomPanelLayoutTypes.BottomPanelLayout layout) {
        BottomBarUiState state = panel.snapshotCore(layout);
        BottomPanelCategoryLayout categoryLayout =
                BottomPanel.resolveCategoryLayout(
                        layout, state.categories.size(), state.categoryScroll);
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
                && categoryLayout.toggleArea(categoryIndex)
                        .contains(mouseX, mouseY);
        panel.dispatchCore(BottomBarUiAction.index(toggle
                ? BottomBarUiAction.Type.TOGGLE_CATEGORY
                : BottomBarUiAction.Type.SELECT_CATEGORY, categoryIndex));
        return true;
    }

    private void scrollCategories(
            int delta,
            BottomPanelLayoutTypes.BottomPanelLayout layout) {
        BottomPanelCategoryLayout categories =
                BottomPanel.resolveCategoryLayout(
                        layout, panel.buildCategoryRows().size(),
                        panel.categoryScroll);
        panel.dispatchCore(BottomBarUiAction.delta(
                BottomBarUiAction.Type.SCROLL_CATEGORY,
                delta, categories.maxScroll));
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

    private static boolean contains(GuiTextField field, double mouseX, double mouseY) {
        return mouseX >= field.xPosition && mouseX < field.xPosition + field.width
                && mouseY >= field.yPosition && mouseY < field.yPosition + field.height;
    }

    private static boolean isShiftDown() {
        return Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)
                || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
    }
}
