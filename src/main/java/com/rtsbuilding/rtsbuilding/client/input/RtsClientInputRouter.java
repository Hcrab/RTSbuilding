package com.rtsbuilding.rtsbuilding.client.input;

import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.RtsCraftTerminalScreen;
import com.rtsbuilding.rtsbuilding.common.persist.RtsClientUiStateStore;
import com.rtsbuilding.rtsbuilding.network.RtsPayloadRegistrar;
import com.rtsbuilding.rtsbuilding.network.storage.C2SRtsReturnCarriedPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import org.lwjgl.input.Keyboard;

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

    static boolean onScreenKeyPressed(GuiScreen screen, int keyCode, char typedChar) {
        if (!RtsClientInputPolicy.canHandleOverlayInput(screen)) {
            return false;
        }

        if (OVERLAY_CRAFT_DIALOG.isOpen()) {
            OVERLAY_CRAFT_DIALOG.keyPressed(keyCode, 0, 0);
            submitOverlayCraftDialogIfReady();
            return true;
        }

        if (!overlaySearchFocused && !overlayCraftSearchFocused) {
            return false;
        }

        boolean ctrl = GuiScreen.isCtrlKeyDown();
        boolean craftSearch = overlayCraftSearchFocused;

        if (keyCode == Keyboard.KEY_ESCAPE) {
            if (craftSearch) {
                overlayCraftSearchDraft = "";
                overlayCraftSearchFocused = false;
                applyOverlayCraftSearch();
            } else {
                overlaySearchDraft = "";
                overlaySearchFocused = false;
                ClientRtsController.get().setStorageSearch("");
            }
            return true;
        }
        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            if (craftSearch) {
                overlayCraftSearchFocused = false;
                applyOverlayCraftSearch();
            } else {
                overlaySearchFocused = false;
            }
            return true;
        }
        if (keyCode == Keyboard.KEY_BACK) {
            if (craftSearch) {
                if (!overlayCraftSearchDraft.isEmpty()) {
                    overlayCraftSearchDraft = overlayCraftSearchDraft.substring(0, overlayCraftSearchDraft.length() - 1);
                }
            } else if (!overlaySearchDraft.isEmpty()) {
                overlaySearchDraft = overlaySearchDraft.substring(0, overlaySearchDraft.length() - 1);
                ClientRtsController.get().setStorageSearch(overlaySearchDraft);
            }
            return true;
        }
        if (keyCode == Keyboard.KEY_DELETE) {
            if (craftSearch) {
                overlayCraftSearchDraft = "";
            } else {
                overlaySearchDraft = "";
                ClientRtsController.get().setStorageSearch("");
            }
            return true;
        }
        if (ctrl && keyCode == Keyboard.KEY_V) {
            String clip = GuiScreen.getClipboardString();
            if (clip != null && !clip.isEmpty()) {
                appendSearchText(clip, craftSearch);
            }
            return true;
        }

        if (!Character.isISOControl(typedChar)) {
            appendSearchText(Character.toString(typedChar), craftSearch);
        }
        return true;
    }

    static void onScreenClosing(GuiScreen screen) {
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
        if (screen instanceof BuilderScreen) {
            return;
        }
        if (screen instanceof RtsCraftTerminalScreen) {
            pendingOverlayCarriedItemId = "";
            return;
        }
        if (!(screen instanceof GuiContainer)) {
            pendingOverlayCarriedItemId = "";
            return;
        }

        if (pendingOverlayCarriedItemId == null || pendingOverlayCarriedItemId.trim().isEmpty()) {
            return;
        }

        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.player == null) {
            pendingOverlayCarriedItemId = "";
            return;
        }

        ItemStack carried = minecraft.player.inventory.getItemStack();
        if (carried.isEmpty()) {
            pendingOverlayCarriedItemId = "";
            return;
        }

        ResourceLocation carriedId = ForgeRegistries.ITEMS.getKey(carried.getItem());
        if (carriedId == null || !pendingOverlayCarriedItemId.equals(carriedId.toString())) {
            pendingOverlayCarriedItemId = "";
            return;
        }

        RtsPayloadRegistrar.sendToServer(new C2SRtsReturnCarriedPayload(
                pendingOverlayCarriedItemId, carried.getCount()));
        minecraft.player.inventory.setItemStack(ItemStack.EMPTY);
        pendingOverlayCarriedItemId = "";
    }

}
