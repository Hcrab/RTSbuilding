package com.rtsbuilding.rtsbuilding.client.screen.layout;

import static com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreenConstants.BOTTOM_PANEL_HEADER_H;

/**
 * Container for bottom-panel layout data types.
 * <p>
 * Groups the panel layout parameters and the tab enum that together define
 * the bottom panel's geometry and mode selection.
 */
public final class BottomPanelLayoutTypes {

    /**
     * Bottom-panel layout parameters (immutable).
     * <p>
     * Stores pre-computed coordinates and dimensions for every sub-region
     * of the bottom panel: sort button, category panel, storage grid, craft
     * panel, search box, pager, tool row, and grid-scroll area.
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

        public boolean contains(double mouseX, double mouseY) {
            return mouseX >= this.panelX && mouseX <= this.panelX + this.panelW
                    && mouseY >= this.panelY && mouseY <= this.panelY + this.panelH;
        }

        public boolean isInsideHeader(double mouseX, double mouseY) {
            return mouseX >= this.panelX && mouseX <= this.panelX + this.panelW
                    && mouseY >= this.panelY && mouseY <= this.panelY + BOTTOM_PANEL_HEADER_H;
        }
    }

    public enum BottomPanelTab {
        CREATIVE,
        STORAGE,
        BLUEPRINTS
    }

    private BottomPanelLayoutTypes() {}
}
