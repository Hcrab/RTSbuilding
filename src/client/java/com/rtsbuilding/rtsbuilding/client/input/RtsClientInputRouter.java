package com.rtsbuilding.rtsbuilding.client.input;

import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.RtsCraftTerminalScreen;
import com.rtsbuilding.rtsbuilding.common.persist.RtsClientUiStateStore;
import com.rtsbuilding.rtsbuilding.network.storage.C2SRtsReturnCarriedPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import com.rtsbuilding.rtsbuilding.client.input.event.RtsScreenEvent;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import org.lwjgl.glfw.GLFW;

import static com.rtsbuilding.rtsbuilding.client.input.RtsClientInputGate.*;
import static com.rtsbuilding.rtsbuilding.client.input.overlay.OverlayInputHandler.*;
import static com.rtsbuilding.rtsbuilding.client.input.overlay.OverlayInteraction.*;

/**
 * 容器叠层的键盘、字符输入与关闭清理唯一 owner。
 *
 * <p>本类消费已经通过事件门面的输入并维护搜索/关闭语义；它不处理鼠标几何、
 * 不绘制界面，也不注册事件。真实网络与容器副作用仍由原适配器执行。</p>
 */
final class RtsClientInputRouter {
    private RtsClientInputRouter() {
    }

    static void onScreenKeyPressed(RtsScreenEvent.KeyPressed.Pre event) {
        if (!RtsClientInputPolicy.canHandleOverlayInput(event.getScreen())) {
            return;
        }

        if (OVERLAY_CRAFT_DIALOG.isOpen()) {
            OVERLAY_CRAFT_DIALOG.keyPressed(event.getKeyCode(), event.getScanCode(), event.getModifiers());
            submitOverlayCraftDialogIfReady();
            event.setCanceled(true);
            return;
        }

        if (!overlaySearchFocused && !overlayCraftSearchFocused) {
            return;
        }

        int keyCode = event.getKeyCode();
        boolean ctrl = (event.getModifiers() & GLFW.GLFW_MOD_CONTROL) != 0;
        boolean craftSearch = overlayCraftSearchFocused;

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (craftSearch) {
                overlayCraftSearchDraft = "";
                overlayCraftSearchFocused = false;
                applyOverlayCraftSearch();
            } else {
                overlaySearchDraft = "";
                overlaySearchFocused = false;
                ClientRtsController.get().setStorageSearch("");
            }
            event.setCanceled(true);
            return;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            if (craftSearch) {
                overlayCraftSearchFocused = false;
                applyOverlayCraftSearch();
            } else {
                overlaySearchFocused = false;
            }
            event.setCanceled(true);
            return;
        }
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            if (craftSearch) {
                if (!overlayCraftSearchDraft.isEmpty()) {
                    overlayCraftSearchDraft = overlayCraftSearchDraft.substring(0, overlayCraftSearchDraft.length() - 1);
                }
            } else if (!overlaySearchDraft.isEmpty()) {
                overlaySearchDraft = overlaySearchDraft.substring(0, overlaySearchDraft.length() - 1);
                ClientRtsController.get().setStorageSearch(overlaySearchDraft);
            }
            event.setCanceled(true);
            return;
        }
        if (keyCode == GLFW.GLFW_KEY_DELETE) {
            if (craftSearch) {
                overlayCraftSearchDraft = "";
            } else {
                overlaySearchDraft = "";
                ClientRtsController.get().setStorageSearch("");
            }
            event.setCanceled(true);
            return;
        }
        if (ctrl && keyCode == GLFW.GLFW_KEY_V) {
            Minecraft minecraft = Minecraft.getInstance();
            String clip = minecraft.keyboardHandler.getClipboard();
            if (clip != null && !clip.isEmpty()) {
                appendSearchText(clip, craftSearch);
            }
            event.setCanceled(true);
            return;
        }

        event.setCanceled(true);
    }

    static void onScreenCharTyped(RtsScreenEvent.CharacterTyped.Pre event) {
        if (!RtsClientInputPolicy.canHandleOverlayInput(event.getScreen())) {
            return;
        }
        if (OVERLAY_CRAFT_DIALOG.isOpen()) {
            OVERLAY_CRAFT_DIALOG.charTyped((char) event.getCodePoint(), 0);
            submitOverlayCraftDialogIfReady();
            event.setCanceled(true);
            return;
        }
        if (!overlaySearchFocused && !overlayCraftSearchFocused) {
            return;
        }
        int codePoint = event.getCodePoint();
        if (!Character.isValidCodePoint(codePoint) || Character.isISOControl(codePoint)) {
            event.setCanceled(true);
            return;
        }
        appendSearchText(new String(Character.toChars(codePoint)), overlayCraftSearchFocused);
        event.setCanceled(true);
    }

    static void onScreenClosing(RtsScreenEvent.Closing event) {
        captureLeftRelease = false;
        captureRightRelease = false;
        overlaySearchFocused = false;
        overlaySearchDraft = "";
        overlayCraftSearchFocused = false;
        overlayCraftSearchDraft = "";
        overlayInfoOpen = false;
        overlayCraftScroll = 0;
        overlayLastCraftablesStorageRevision = -1;
        activeOverlayScreen = null;
        endShiftImportDrag();
        OVERLAY_CRAFT_DIALOG.close();
        clearPendingCraftRefill();
        if (!ClientRtsController.get().canUseStorageOverlay()) {
            pendingOverlayCarriedItemId = "";
            return;
        }
        if (event.getScreen() instanceof BuilderScreen) {
            return;
        }
        if (event.getScreen() instanceof RtsCraftTerminalScreen) {
            pendingOverlayCarriedItemId = "";
            return;
        }
        if (!(event.getScreen() instanceof AbstractContainerScreen<?>)) {
            pendingOverlayCarriedItemId = "";
            return;
        }

        if (pendingOverlayCarriedItemId.isBlank()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            pendingOverlayCarriedItemId = "";
            return;
        }

        ItemStack carried = minecraft.player.containerMenu.getCarried();
        if (carried.isEmpty()) {
            pendingOverlayCarriedItemId = "";
            return;
        }

        var carriedId = BuiltInRegistries.ITEM.getKey(carried.getItem());
        if (carriedId == null || !pendingOverlayCarriedItemId.equals(carriedId.toString())) {
            pendingOverlayCarriedItemId = "";
            return;
        }

        ClientPlayNetworking.send(new C2SRtsReturnCarriedPayload(pendingOverlayCarriedItemId, carried.getCount()));
        minecraft.player.containerMenu.setCarried(ItemStack.EMPTY);
        pendingOverlayCarriedItemId = "";
    }

}
