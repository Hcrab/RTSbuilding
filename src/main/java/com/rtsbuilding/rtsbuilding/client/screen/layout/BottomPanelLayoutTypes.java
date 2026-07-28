package com.rtsbuilding.rtsbuilding.client.screen.layout;

import java.util.Objects;

/**
 * Container for bottom-panel layout data types.
 * <p>
 * Groups the panel layout parameters and the tab enum that together define
 * the bottom panel's geometry and mode selection. Both types are always
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
     * @param craftDockX    craft-dock ring left edge
     * @param craftDockY    craft-dock ring top edge
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
    public static final class BottomPanelLayout {
        private final int panelX, panelY, panelW, panelH;
        private final int sortX, sortY, craftDockX, craftDockY;
        private final int categoryX, categoryY, categoryH;
        private final int storageX, storageY, storageW, craftPanelX;
        private final int mainStorageW, searchW, pagerX, toolY;
        private final int gridY, gridH, storageRows, craftPanelY, craftPanelH;

        public BottomPanelLayout(
                int panelX, int panelY, int panelW, int panelH,
                int sortX, int sortY, int craftDockX, int craftDockY,
                int categoryX, int categoryY, int categoryH,
                int storageX, int storageY, int storageW, int craftPanelX,
                int mainStorageW, int searchW, int pagerX, int toolY,
                int gridY, int gridH, int storageRows, int craftPanelY, int craftPanelH) {
            this.panelX = panelX; this.panelY = panelY; this.panelW = panelW; this.panelH = panelH;
            this.sortX = sortX; this.sortY = sortY;
            this.craftDockX = craftDockX; this.craftDockY = craftDockY;
            this.categoryX = categoryX; this.categoryY = categoryY; this.categoryH = categoryH;
            this.storageX = storageX; this.storageY = storageY; this.storageW = storageW;
            this.craftPanelX = craftPanelX; this.mainStorageW = mainStorageW;
            this.searchW = searchW; this.pagerX = pagerX; this.toolY = toolY;
            this.gridY = gridY; this.gridH = gridH; this.storageRows = storageRows;
            this.craftPanelY = craftPanelY; this.craftPanelH = craftPanelH;
        }

        public int panelX() { return panelX; }
        public int panelY() { return panelY; }
        public int panelW() { return panelW; }
        public int panelH() { return panelH; }
        public int sortX() { return sortX; }
        public int sortY() { return sortY; }
        public int craftDockX() { return craftDockX; }
        public int craftDockY() { return craftDockY; }
        public int categoryX() { return categoryX; }
        public int categoryY() { return categoryY; }
        public int categoryH() { return categoryH; }
        public int storageX() { return storageX; }
        public int storageY() { return storageY; }
        public int storageW() { return storageW; }
        public int craftPanelX() { return craftPanelX; }
        public int mainStorageW() { return mainStorageW; }
        public int searchW() { return searchW; }
        public int pagerX() { return pagerX; }
        public int toolY() { return toolY; }
        public int gridY() { return gridY; }
        public int gridH() { return gridH; }
        public int storageRows() { return storageRows; }
        public int craftPanelY() { return craftPanelY; }
        public int craftPanelH() { return craftPanelH; }

        /**
         * Returns whether the given mouse coordinates are inside the panel bounding box.
         *
         * @param mouseX current mouse X
         * @param mouseY current mouse Y
         * @return true if inside the panel
         */
        public boolean contains(double mouseX, double mouseY) {
            return mouseX >= this.panelX && mouseX < this.panelX + this.panelW
                    && mouseY >= this.panelY && mouseY < this.panelY + this.panelH;
        }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof BottomPanelLayout)) return false;
            BottomPanelLayout value = (BottomPanelLayout) other;
            return panelX == value.panelX && panelY == value.panelY
                    && panelW == value.panelW && panelH == value.panelH
                    && sortX == value.sortX && sortY == value.sortY
                    && craftDockX == value.craftDockX && craftDockY == value.craftDockY
                    && categoryX == value.categoryX && categoryY == value.categoryY
                    && categoryH == value.categoryH && storageX == value.storageX
                    && storageY == value.storageY && storageW == value.storageW
                    && craftPanelX == value.craftPanelX && mainStorageW == value.mainStorageW
                    && searchW == value.searchW && pagerX == value.pagerX && toolY == value.toolY
                    && gridY == value.gridY && gridH == value.gridH
                    && storageRows == value.storageRows
                    && craftPanelY == value.craftPanelY && craftPanelH == value.craftPanelH;
        }

        @Override public int hashCode() {
            return Objects.hash(panelX, panelY, panelW, panelH, sortX, sortY,
                    craftDockX, craftDockY, categoryX, categoryY, categoryH,
                    storageX, storageY, storageW, craftPanelX, mainStorageW,
                    searchW, pagerX, toolY, gridY, gridH, storageRows,
                    craftPanelY, craftPanelH);
        }

        @Override public String toString() {
            return "BottomPanelLayout[panelX=" + panelX + ", panelY=" + panelY
                    + ", panelW=" + panelW + ", panelH=" + panelH
                    + ", sortX=" + sortX + ", sortY=" + sortY
                    + ", craftDockX=" + craftDockX + ", craftDockY=" + craftDockY
                    + ", categoryX=" + categoryX + ", categoryY=" + categoryY
                    + ", categoryH=" + categoryH + ", storageX=" + storageX
                    + ", storageY=" + storageY + ", storageW=" + storageW
                    + ", craftPanelX=" + craftPanelX + ", mainStorageW=" + mainStorageW
                    + ", searchW=" + searchW + ", pagerX=" + pagerX
                    + ", toolY=" + toolY + ", gridY=" + gridY
                    + ", gridH=" + gridH + ", storageRows=" + storageRows
                    + ", craftPanelY=" + craftPanelY + ", craftPanelH=" + craftPanelH + ']';
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
