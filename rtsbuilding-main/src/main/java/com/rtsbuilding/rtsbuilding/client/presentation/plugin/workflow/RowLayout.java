package com.rtsbuilding.rtsbuilding.client.presentation.plugin.workflow;

public record RowLayout(int slotIdx, int entryId, int toggleBtnX, int deleteBtnX, int btnY, int rowY, int rowH) {
    private static final int BTN_SIZE = 14;

    public boolean containsToggle(int px, int py) {
        return px >= toggleBtnX && px < toggleBtnX + BTN_SIZE
                && py >= btnY && py < btnY + BTN_SIZE;
    }

    public boolean containsDelete(int px, int py) {
        return px >= deleteBtnX && px < deleteBtnX + BTN_SIZE
                && py >= btnY && py < btnY + BTN_SIZE;
    }
}
