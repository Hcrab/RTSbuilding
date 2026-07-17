package com.rtsbuilding.rtsbuilding.client.input.overlay;

import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.common.persist.RtsClientUiStateStore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.util.Mth;

import static com.rtsbuilding.rtsbuilding.client.input.RtsClientInputGate.*;
import static com.rtsbuilding.rtsbuilding.client.input.overlay.OverlayLayoutHelper.*;

public final class OverlayInputHandler {
    private OverlayInputHandler() {
    }

    // =========================================================================
    //  Search focus
    // =========================================================================

    public static void clearOverlaySearchFocus() {
        overlaySearchFocused = false;
        overlayCraftSearchFocused = false;
    }

    public static void setOverlaySearchFocused(boolean focused) {
        overlaySearchFocused = focused;
        if (focused) {
            overlayCraftSearchFocused = false;
        }
    }

    public static void setOverlayCraftSearchFocused(boolean focused) {
        overlayCraftSearchFocused = focused;
        if (focused) {
            overlaySearchFocused = false;
        }
    }

    // =========================================================================
    //  Sync helpers
    // =========================================================================

    public static void syncOverlaySearchDrafts(ClientRtsController controller) {
        if (!overlaySearchFocused) {
            overlaySearchDraft = controller.getStorageSearch();
        }
    }

    public static void requestOverlayBootstrap(Screen screen, ClientRtsController controller) {
        if (overlayBootstrapRequested || controller == null) {
            return;
        }
        overlayBootstrapRequested = true;
        activeOverlayScreen = screen;
        endOverlayDrag();
        OverlayInteraction.endShiftImportDrag();
        clearOverlaySearchFocus();
        overlaySearchDraft = "";
        overlayCraftSearchDraft = "";
        overlayInfoOpen = false;
        overlayCraftScroll = 0;
        overlayLastCraftablesStorageRevision = controller.getStorageRevision();
        OVERLAY_CRAFT_DIALOG.close();
        if (!controller.getStorageSearch().isEmpty()) {
            controller.setStorageSearch("");
        } else {
            controller.refreshStoragePage();
        }
    }

    public static void syncOverlayCraftables(ClientRtsController controller) {
        if (overlayLastCraftablesStorageRevision != controller.getStorageRevision()) {
            overlayLastCraftablesStorageRevision = controller.getStorageRevision();
            overlayCraftScroll = 0;
            controller.requestCraftables();
        }
    }

    public static void appendSearchText(String raw, boolean craftSearch) {
        if (raw == null || raw.isEmpty()) {
            return;
        }
        String current = craftSearch ? overlayCraftSearchDraft : overlaySearchDraft;
        StringBuilder sb = new StringBuilder(current == null ? "" : current);
        for (int i = 0; i < raw.length() && sb.length() < OVERLAY_SEARCH_MAX; i++) {
            char ch = raw.charAt(i);
            if (Character.isISOControl(ch)) {
                continue;
            }
            sb.append(ch);
        }
        String next = sb.toString();
        if (craftSearch) {
            if (!next.equals(overlayCraftSearchDraft)) {
                overlayCraftSearchDraft = next;
            }
        } else if (!next.equals(overlaySearchDraft)) {
            overlaySearchDraft = next;
            ClientRtsController.get().setStorageSearch(overlaySearchDraft);
        }
    }

    public static void syncOverlayScreen(Screen screen, ClientRtsController controller) {
        if (screen == activeOverlayScreen) {
            return;
        }
        activeOverlayScreen = screen;
        endOverlayDrag();
        OverlayInteraction.endShiftImportDrag();
        clearOverlaySearchFocus();
        overlaySearchDraft = "";
        overlayCraftSearchDraft = "";
        overlayInfoOpen = false;
        overlayCraftScroll = 0;
        overlayLastCraftablesStorageRevision = -1;
        OVERLAY_CRAFT_DIALOG.close();

        if (!controller.getStorageSearch().isEmpty()) {
            controller.setStorageSearch("");
        } else {
            controller.refreshStoragePage();
        }

        boolean craftablesRequestSent = false;
        if (!controller.getCraftablesSearch().isEmpty()) {
            controller.setCraftablesSearch("");
            craftablesRequestSent = true;
        } else if (controller.isCraftablesShowUnavailable()) {
            controller.setCraftablesShowUnavailable(false);
            craftablesRequestSent = true;
        }
        if (craftablesRequestSent) {
            overlayLastCraftablesStorageRevision = controller.getStorageRevision();
        }
    }

    // =========================================================================
    //  Overlay drag
    // =========================================================================

    public static void beginOverlayDrag(double mouseX, double mouseY, OverlayLayout layout) {
        overlayDragging = true;
        overlayDragOffsetX = mouseX - layout.panelX();
        overlayDragOffsetY = mouseY - layout.panelY();
    }

    public static void updateOverlayDrag(Screen screen, double mouseX, double mouseY, OverlayProfile profile) {
        int sw = overlayVirtualWidth(profile);
        int sh = overlayVirtualHeight(profile);
        int minX = OVERLAY_MARGIN;
        int maxX = Math.max(minX, sw - currentOverlayWidth(profile) - OVERLAY_MARGIN);
        int minY = OVERLAY_MARGIN;
        int maxY = Math.max(minY, sh - overlayHeight(profile) - OVERLAY_MARGIN);
        int panelX = Mth.clamp((int) Math.round(mouseX - overlayDragOffsetX), minX, maxX);
        int panelY = Mth.clamp((int) Math.round(mouseY - overlayDragOffsetY), minY, maxY);

        ClientRtsController controller = ClientRtsController.get();
        controller.updateStoragePanelLayout(
                normalizeBetween(panelX, minX, maxX),
                normalizeBetween(panelY, minY, maxY),
                controller.getStoragePanelWidthNormalized(),
                controller.getStoragePanelHeightNormalized());
    }

    public static void endOverlayDrag() {
        overlayDragging = false;
    }

    // =========================================================================
    //  State toggles
    // =========================================================================

    public static void disableContainerOverlay() {
        RtsClientUiStateStore.setContainerOverlayEnabled(false);
        overlayInfoOpen = false;
        overlayDragging = false;
        clearOverlaySearchFocus();
        OVERLAY_CRAFT_DIALOG.close();
    }

    public static void toggleOverlayShiftImportEnabled() {
        boolean enabled = !RtsClientUiStateStore.isOverlayShiftImportEnabled();
        RtsClientUiStateStore.setOverlayShiftImportEnabled(enabled);
        if (!enabled) {
            OverlayInteraction.endShiftImportDrag();
        }
    }

    public static boolean isLeftMouseDown() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft != null
                && minecraft.getWindow() != null
                && org.lwjgl.glfw.GLFW.glfwGetMouseButton(minecraft.getWindow().handle(), org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
    }
}
