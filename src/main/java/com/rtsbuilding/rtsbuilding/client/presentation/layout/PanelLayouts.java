package com.rtsbuilding.rtsbuilding.client.presentation.layout;

import static com.rtsbuilding.rtsbuilding.client.presentation.standalone.BuilderScreenConstants.*;


public final class PanelLayouts {

    
    public record CraftDockLayout(int cX, int cY) {

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

        public int slotY(int slot) {
            return switch (slot) {
                case 0, 1, 2 -> this.cY - CRAFT_DOCK_SLOT_SIZE - CRAFT_DOCK_GAP;
                case 3, 4 -> this.cY + (CRAFT_DOCK_C_SIZE - CRAFT_DOCK_SLOT_SIZE) / 2;
                case 5, 6, 7 -> this.cY + CRAFT_DOCK_C_SIZE + CRAFT_DOCK_GAP;
                default -> this.cY;
            };
        }
    }

    
    public record QuickBuildPanelLayout(int x, int y, int w, int h) {

        public boolean contains(double mouseX, double mouseY) {
            return mouseX >= this.x && mouseX <= this.x + this.w
                    && mouseY >= this.y && mouseY <= this.y + this.h;
        }
    }

    private PanelLayouts() {}
}
