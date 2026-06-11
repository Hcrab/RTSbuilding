package com.rtsbuilding.rtsbuilding.client.screen.layout;


import com.rtsbuilding.rtsbuilding.client.screen.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.screen.panel.BottomPanel;
import static com.rtsbuilding.rtsbuilding.client.screen.BuilderScreenConstants.BOTTOM_PANEL_HEADER_H;

/**
 * Container for bottom-panel layout data types.
 * <p>
 * Groups the panel layout parameters and the tab enum that together define
 * the bottom panel's geometry and mode selection. Both types are always
 * referenced together by {@link com.rtsbuilding.rtsbuilding.client.screen.panel.BottomPanel}
 * and {@link com.rtsbuilding.rtsbuilding.client.screen.BuilderScreen}.
 */
public final class BottomPanelLayoutTypes {

    /**
     * Bottom-panel layout parameters (immutable).
     * <p>
     * Stores pre-computed coordinates and dimensions for every sub-region
     * of the bottom panel: sort button, category panel, storage grid, craft
     * panel, search box, pager, tool row, and grid-scroll area.
     *
     * @param panelX        panel left edge
     * @param panelY        panel top edge
     * @param panelW        panel width
     * @param panelH        panel height
     * @param sortX         sort-button X
     * @param sortY         sort-button Y
     * @param categoryX     category-panel X
     * @param categoryY     category-panel Y
     * @param categoryH     category-panel height
     * @param storageX      storage-block X
     * @param storageY      storage-block Y
     * @param storageW      storage-block width
     * @param craftPanelX   craft panel X
     * @param mainStorageW  main-storage width
     * @param searchW       search-box width
     * @param pagerX        pager X
     * @param toolY         tool-row Y
     * @param gridY         storage-grid Y
     * @param gridH         storage-grid height
     * @param storageRows   number of visible storage rows
     * @param craftPanelY   craft panel Y
     * @param craftPanelH   craft panel height
     */
    public record BottomPanelLayout(
            int panelX,
            int panelY,
            int panelW,
            int panelH,
            int sortX,
            int sortY,
            int categoryX,
            int categoryY,
            int categoryH,
            int storageX,
            int storageY,
            int storageW,
            int craftPanelX,
            int mainStorageW,
            int searchW,
            int pagerX,
            int toolY,
            int gridY,
            int gridH,
            int storageRows,
            int craftPanelY,
            int craftPanelH) {

        /**
         * Returns whether the given mouse coordinates are inside the panel bounding box.
         *
         * @param mouseX current mouse X
         * @param mouseY current mouse Y
         * @return true if inside the panel
         */
        public boolean contains(double mouseX, double mouseY) {
            return mouseX >= this.panelX && mouseX <= this.panelX + this.panelW
                    && mouseY >= this.panelY && mouseY <= this.panelY + this.panelH;
        }

        /**
         * Returns whether the mouse is inside the panel's header area.
         *
         * @param mouseX current mouse X
         * @param mouseY current mouse Y
         * @return true if inside the header bar
         */
        public boolean isInsideHeader(double mouseX, double mouseY) {
            return mouseX >= this.panelX && mouseX <= this.panelX + this.panelW
                    && mouseY >= this.panelY && mouseY <= this.panelY + BOTTOM_PANEL_HEADER_H;
        }
    }

    /**
     * Bottom-panel tab selection.
     * <p>
     * Determines which sub-panel is displayed: the creative picker
     * ({@link #CREATIVE}), item-storage browser ({@link #STORAGE}), or the
     * blueprint library ({@link #BLUEPRINTS}).
     */
    public enum BottomPanelTab {
        CREATIVE,
        STORAGE,
        BLUEPRINTS
    }

    private BottomPanelLayoutTypes() {}
}
