package com.rtsbuilding.rtsbuilding.client.screen.layout;

import static com.rtsbuilding.rtsbuilding.client.screen.BuilderScreenConstants.CRAFT_DOCK_C_SIZE;
import static com.rtsbuilding.rtsbuilding.client.screen.BuilderScreenConstants.CRAFT_DOCK_GAP;
import static com.rtsbuilding.rtsbuilding.client.screen.BuilderScreenConstants.CRAFT_DOCK_SLOT_SIZE;

/**
 * Container for small specialised panel-layout records.
 * <p>
 * Groups together layout parameter records that belong to distinct UI
 * components (craft dock and quick-build panel) but share the same simple
 * pattern: a few integer fields plus lightweight hit-test or slot-position
 * methods.
 */
public final class PanelLayouts {

    /**
     * Craft-dock layout (the grid of 8 surrounding slots).
     * <p>
     * Computes the X/Y position of each of the 8 input/output slots
     * that ring the central 3×3 crafting grid. Slot numbering:
     * <pre>
     *   0   1   2
     *   3   C   4
     *   5   6   7
     * </pre>
     *
     * @param cX centre-X of the 3×3 crafting grid
     * @param cY centre-Y of the 3×3 crafting grid
     */
    public record CraftDockLayout(int cX, int cY) {

        /**
         * Returns the X coordinate of the given slot.
         */
        public int slotX(int slot) {
            return switch (slot) {
                case 0, 5 -> this.cX - CRAFT_DOCK_SLOT_SIZE - CRAFT_DOCK_GAP;
                case 1, 6 -> this.cX + (CRAFT_DOCK_C_SIZE - CRAFT_DOCK_SLOT_SIZE) / 2;
                case 2, 7 -> this.cX + CRAFT_DOCK_C_SIZE + CRAFT_DOCK_GAP;
                case 3 -> this.cX - CRAFT_DOCK_SLOT_SIZE - CRAFT_DOCK_GAP;
                case 4 -> this.cX + CRAFT_DOCK_C_SIZE + CRAFT_DOCK_GAP;
                default -> this.cX;
            };
        }

        /**
         * Returns the Y coordinate of the given slot.
         */
        public int slotY(int slot) {
            return switch (slot) {
                case 0, 1, 2 -> this.cY - CRAFT_DOCK_SLOT_SIZE - CRAFT_DOCK_GAP;
                case 3, 4 -> this.cY + (CRAFT_DOCK_C_SIZE - CRAFT_DOCK_SLOT_SIZE) / 2;
                case 5, 6, 7 -> this.cY + CRAFT_DOCK_C_SIZE + CRAFT_DOCK_GAP;
                default -> this.cY;
            };
        }
    }

    /**
     * Quick-build panel layout.
     * <p>
     * Position and size of the floating quick-build selection panel
     * (shape picker, fill-mode buttons, and scrollable recipe grid).
     *
     * @param x panel left edge
     * @param y panel top edge
     * @param w panel width
     * @param h panel height
     */
    public record QuickBuildPanelLayout(int x, int y, int w, int h) {

        /**
         * Returns whether the given mouse coordinates are inside the panel.
         */
        public boolean contains(double mouseX, double mouseY) {
            return mouseX >= this.x && mouseX <= this.x + this.w
                    && mouseY >= this.y && mouseY <= this.y + this.h;
        }
    }

    private PanelLayouts() {}
}
