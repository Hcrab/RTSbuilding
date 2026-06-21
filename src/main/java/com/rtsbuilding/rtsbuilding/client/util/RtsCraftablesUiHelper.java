package com.rtsbuilding.rtsbuilding.client.util;

import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.popup.RtsCraftQuantityDialog;


public final class RtsCraftablesUiHelper {
    private RtsCraftablesUiHelper() {
    }

    public static void openCraftQuantityDialog(RtsCraftQuantityDialog dialog, ClientRtsController.CraftableEntry entry) {
        if (dialog == null || entry == null || !entry.craftable()) {
            return;
        }
        dialog.open(
                entry.stack().getHoverName().getString(),
                entry.stack(),
                entry.recipeOptions(),
                1);
    }

    public static void submitPendingCraftRequest(RtsCraftQuantityDialog dialog, ClientRtsController controller) {
        if (dialog == null || controller == null) {
            return;
        }
        RtsCraftQuantityDialog.Request request = dialog.consumePendingRequest();
        if (request == null) {
            return;
        }
        controller.craftRecipeToLinked(request.recipeId(), request.craftCount());
    }

    public static String normalizeSearchDraft(String value) {
        return value == null ? "" : value.trim();
    }
}
