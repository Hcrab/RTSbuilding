package com.rtsbuilding.rtsbuilding.client.input;

import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.input.overlay.OverlayInteraction;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.RtsCraftTerminalScreen;
import com.rtsbuilding.rtsbuilding.common.persist.RtsClientUiStateStore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import com.rtsbuilding.rtsbuilding.client.input.event.RtsScreenEvent;
import org.lwjgl.glfw.GLFW;

import static com.rtsbuilding.rtsbuilding.client.input.RtsClientInputGate.*;
import static com.rtsbuilding.rtsbuilding.client.input.overlay.OverlayInputHandler.*;
import static com.rtsbuilding.rtsbuilding.client.input.overlay.OverlayInteraction.*;
import static com.rtsbuilding.rtsbuilding.client.input.overlay.OverlayLayoutHelper.*;
import static com.rtsbuilding.rtsbuilding.client.input.overlay.OverlayRenderer.*;

/**
 * 容器叠层的鼠标路由唯一 owner。
 *
 * <p>本类只决定鼠标按下、拖拽、释放和滚轮的优先级，并调用既有副作用适配器；
 * 它不注册 NeoForge 事件、不绘制叠层，也不拥有搜索或关闭生命周期状态。</p>
 */
final class RtsClientPointerRouter {
    private RtsClientPointerRouter() {
    }

    static void onScreenMousePressed(RtsScreenEvent.MouseButtonPressed.Pre event) {
        if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT
                && event.getScreen() instanceof InventoryScreen
                && handleInventoryRtsButtonClick(event.getScreen(), event.getMouseX(), event.getMouseY())) {
            event.setCanceled(true);
            return;
        }

        if (!ClientRtsController.get().canUseStorageOverlay()) {
            return;
        }
        if (!RtsClientInputPolicy.isOverlayContainer(event.getScreen())) {
            return;
        }
        if (!RtsClientUiStateStore.isContainerOverlayEnabled()) {
            clearOverlaySearchFocus();
            OVERLAY_CRAFT_DIALOG.close();
            return;
        }

        if (OVERLAY_CRAFT_DIALOG.isOpen()) {
            captureLeftRelease = false;
            captureRightRelease = false;
            OVERLAY_CRAFT_DIALOG.mouseClicked(
                    event.getMouseX(),
                    event.getMouseY(),
                    event.getButton(),
                    Minecraft.getInstance().getWindow().getGuiScaledWidth(),
                    Minecraft.getInstance().getWindow().getGuiScaledHeight());
            submitOverlayCraftDialogIfReady();
            event.setCanceled(true);
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        OverlayProfile profile = overlayProfile();
        OverlayLayout layout = resolveOverlayLayout(profile);
        double rawMx = event.getMouseX();
        double rawMy = event.getMouseY();
        double mx = toOverlayMouse(rawMx, profile);
        double my = toOverlayMouse(rawMy, profile);
        capturePendingCraftRefill((AbstractContainerScreen<?>) event.getScreen(), rawMx, rawMy, event.getButton());
        if (overlayInfoOpen) {
            OverlayInfoRect infoRect = resolveOverlayInfoRect(minecraft.font, layout);
            if (inside(mx, my, infoRect.x(), infoRect.y(), infoRect.w(), infoRect.h())) {
                if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT
                        && inside(mx, my, infoRect.closeX(), infoRect.closeY(),
                                OVERLAY_INFO_CLOSE_SIZE, OVERLAY_INFO_CLOSE_SIZE)) {
                    overlayInfoOpen = false;
                }
                clearOverlaySearchFocus();
                if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                    captureLeftRelease = true;
                } else if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                    captureRightRelease = true;
                }
                event.setCanceled(true);
                return;
            }
        }
        if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (inside(mx, my, layout.dragX(), layout.headerY(), OVERLAY_DRAG_W, OVERLAY_HEADER_H)) {
                beginOverlayDrag(mx, my, layout);
                captureLeftRelease = true;
                event.setCanceled(true);
                return;
            }
            if (inside(mx, my, layout.closeX(), layout.controlsY(), OVERLAY_CLOSE_W, OVERLAY_BOTTOM_BUTTON_H)) {
                disableContainerOverlay();
                captureLeftRelease = true;
                event.setCanceled(true);
                return;
            }
            if (inside(mx, my, layout.collapseX(), layout.controlsY(), OVERLAY_COLLAPSE_W, OVERLAY_BOTTOM_BUTTON_H)) {
                overlayCollapsed = !overlayCollapsed;
                overlayInfoOpen = false;
                clearOverlaySearchFocus();
                captureLeftRelease = true;
                event.setCanceled(true);
                return;
            }
            if (Screen.hasShiftDown()) {
                if (RtsClientUiStateStore.isOverlayShiftImportEnabled()) {
                    if (tryStartShiftImportDrag((AbstractContainerScreen<?>) event.getScreen(), rawMx, rawMy)) {
                        captureLeftRelease = true;
                        event.setCanceled(true);
                        return;
                    }
                    if (tryImportHoveredMenuSlot((AbstractContainerScreen<?>) event.getScreen(), rawMx, rawMy, event.getButton())) {
                        captureLeftRelease = true;
                        event.setCanceled(true);
                        return;
                    }
                }
                if (tryQuickMoveOverlayEntry((AbstractContainerScreen<?>) event.getScreen(), mx, my)) {
                    captureLeftRelease = true;
                    event.setCanceled(true);
                    return;
                }
            }
            if (!inside(mx, my, layout.panelX(), layout.panelY(), layout.panelW(), layout.panelH())) {
                clearOverlaySearchFocus();
                return;
            }
            if (layout.overlayCollapsed()) {
                if (inside(mx, my, layout.sortX(), layout.headerY(), 12, OVERLAY_HEADER_H)) {
                    ClientRtsController.get().cycleSort();
                    captureLeftRelease = true;
                    event.setCanceled(true);
                    return;
                }
                if (inside(mx, my, layout.dirX(), layout.headerY(), 12, OVERLAY_HEADER_H)) {
                    ClientRtsController.get().toggleSortDirection();
                    captureLeftRelease = true;
                    event.setCanceled(true);
                    return;
                }
                if (inside(mx, my, layout.clearX(), layout.headerY(), OVERLAY_SEARCH_CLEAR_W, OVERLAY_HEADER_H)) {
                    overlaySearchDraft = "";
                    clearOverlaySearchFocus();
                    ClientRtsController.get().setStorageSearch("");
                    captureLeftRelease = true;
                    event.setCanceled(true);
                    return;
                }
                if (inside(mx, my, layout.searchX(), layout.headerY(), layout.searchW(), OVERLAY_HEADER_H)) {
                    setOverlaySearchFocused(true);
                    overlaySearchDraft = ClientRtsController.get().getStorageSearch();
                    captureLeftRelease = true;
                    event.setCanceled(true);
                    return;
                }
                if (inside(mx, my, layout.refreshX(), layout.controlsY(), OVERLAY_BOTTOM_SMALL_W, OVERLAY_BOTTOM_BUTTON_H)) {
                    clearOverlaySearchFocus();
                    ClientRtsController.get().refreshStoragePage();
                    captureLeftRelease = true;
                    event.setCanceled(true);
                    return;
                }
                if (inside(mx, my, layout.infoX(), layout.controlsY(), OVERLAY_BOTTOM_SMALL_W, OVERLAY_BOTTOM_BUTTON_H)) {
                    clearOverlaySearchFocus();
                    overlayInfoOpen = !overlayInfoOpen;
                    captureLeftRelease = true;
                    event.setCanceled(true);
                    return;
                }
                clearOverlaySearchFocus();
                int idx = resolveOverlaySlotIndex(mx, my, layout.gridX(), layout.gridY(), 1);
                if (!minecraft.player.containerMenu.getCarried().isEmpty()
                        && idx >= 0
                        && tryDepositCarriedToLinked(Integer.MAX_VALUE)) {
                    captureLeftRelease = true;
                    event.setCanceled(true);
                    return;
                }
                if (tryPickupFromOverlay(idx, Integer.MAX_VALUE)) {
                    captureLeftRelease = true;
                    event.setCanceled(true);
                    return;
                }
                captureLeftRelease = true;
                event.setCanceled(true);
                return;
            }
            if (handleOverlayCraftLeftClick(mx, my, layout)) {
                captureLeftRelease = true;
                event.setCanceled(true);
                return;
            }
            if (inside(mx, my, layout.sortX(), layout.headerY(), 12, OVERLAY_HEADER_H)) {
                ClientRtsController.get().cycleSort();
                captureLeftRelease = true;
                event.setCanceled(true);
                return;
            }
            if (inside(mx, my, layout.dirX(), layout.headerY(), 12, OVERLAY_HEADER_H)) {
                ClientRtsController.get().toggleSortDirection();
                captureLeftRelease = true;
                event.setCanceled(true);
                return;
            }
            if (inside(mx, my, layout.clearX(), layout.headerY(), OVERLAY_SEARCH_CLEAR_W, OVERLAY_HEADER_H)) {
                overlaySearchDraft = "";
                clearOverlaySearchFocus();
                ClientRtsController.get().setStorageSearch("");
                captureLeftRelease = true;
                event.setCanceled(true);
                return;
            }
            if (inside(mx, my, layout.searchX(), layout.headerY(), layout.searchW(), OVERLAY_HEADER_H)) {
                setOverlaySearchFocused(true);
                overlaySearchDraft = ClientRtsController.get().getStorageSearch();
                captureLeftRelease = true;
                event.setCanceled(true);
                return;
            }
            clearOverlaySearchFocus();
            int quickbarIdx = resolveQuickbarSlotIndex(mx, my, layout.quickbarX(), layout.quickbarY());
            if (quickbarIdx >= 0) {
                selectOverlayQuickbarSlot(quickbarIdx);
                captureLeftRelease = true;
                event.setCanceled(true);
                return;
            }
            if (inside(mx, my, layout.pageX(), layout.pagePrevY(), PAGE_BUTTON_W, PAGE_BUTTON_H)) {
                ClientRtsController.get().prevPage();
                captureLeftRelease = true;
                event.setCanceled(true);
                return;
            }
            if (inside(mx, my, layout.pageX(), layout.pageNextY(), PAGE_BUTTON_W, PAGE_BUTTON_H)) {
                ClientRtsController.get().nextPage();
                captureLeftRelease = true;
                event.setCanceled(true);
                return;
            }
            if (inside(mx, my, layout.refreshX(), layout.controlsY(), OVERLAY_BOTTOM_SMALL_W, OVERLAY_BOTTOM_BUTTON_H)) {
                ClientRtsController.get().refreshStoragePage();
                captureLeftRelease = true;
                event.setCanceled(true);
                return;
            }
            if (inside(mx, my, layout.infoX(), layout.controlsY(), OVERLAY_BOTTOM_SMALL_W, OVERLAY_BOTTOM_BUTTON_H)) {
                overlayInfoOpen = !overlayInfoOpen;
                captureLeftRelease = true;
                event.setCanceled(true);
                return;
            }
            if (inside(mx, my, layout.shiftImportX(), layout.returnY(), layout.shiftImportW(), SLOT_SIZE)) {
                toggleOverlayShiftImportEnabled();
                captureLeftRelease = true;
                event.setCanceled(true);
                return;
            }

            int returnIdx = resolveReturnSlotIndex(mx, my, layout.returnX(), layout.returnY());
            if (returnIdx >= 0) {
                tryDepositCarriedToLinked(Integer.MAX_VALUE);
                captureLeftRelease = true;
                event.setCanceled(true);
                return;
            }

            int idx = resolveOverlaySlotIndex(mx, my, layout.gridX(), layout.gridY(), layout.storageRows());
            if (!minecraft.player.containerMenu.getCarried().isEmpty()
                    && idx >= 0
                    && tryDepositCarriedToLinked(Integer.MAX_VALUE)) {
                captureLeftRelease = true;
                event.setCanceled(true);
                return;
            }
            if (tryPickupFromOverlay(idx, Integer.MAX_VALUE)) {
                captureLeftRelease = true;
                event.setCanceled(true);
            }
            return;
        }

        if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            if (layout.overlayCollapsed()) {
                if (!inside(mx, my, layout.panelX(), layout.panelY(), layout.panelW(), layout.panelH())) {
                    clearOverlaySearchFocus();
                    return;
                }
                int idx = resolveOverlaySlotIndex(mx, my, layout.gridX(), layout.gridY(), 1);
                if (!minecraft.player.containerMenu.getCarried().isEmpty()
                        && idx >= 0
                        && tryDepositCarriedToLinked(1)) {
                    captureRightRelease = true;
                    event.setCanceled(true);
                    return;
                }
                if (tryPickupFromOverlay(idx, 1)) {
                    captureRightRelease = true;
                    event.setCanceled(true);
                    return;
                }
                captureRightRelease = true;
                event.setCanceled(true);
                return;
            }
            if (Screen.hasShiftDown()) {
                if (RtsClientUiStateStore.isOverlayShiftImportEnabled()) {
                    if (tryImportHoveredMenuSlot((AbstractContainerScreen<?>) event.getScreen(), rawMx, rawMy, event.getButton())) {
                        captureRightRelease = true;
                        event.setCanceled(true);
                        return;
                    }
                }
                if (tryQuickMoveOverlayEntry((AbstractContainerScreen<?>) event.getScreen(), mx, my)) {
                    captureRightRelease = true;
                    event.setCanceled(true);
                    return;
                }
            }

            if (handleOverlayCraftRightClick(mx, my, layout)) {
                captureRightRelease = true;
                event.setCanceled(true);
                return;
            }

            int returnIdx = resolveReturnSlotIndex(mx, my, layout.returnX(), layout.returnY());
            if (returnIdx >= 0) {
                tryDepositCarriedToLinked(1);
                captureRightRelease = true;
                event.setCanceled(true);
                return;
            }

            int idx = resolveOverlaySlotIndex(mx, my, layout.gridX(), layout.gridY(), layout.storageRows());
            if (!minecraft.player.containerMenu.getCarried().isEmpty()
                    && idx >= 0
                    && tryDepositCarriedToLinked(1)) {
                captureRightRelease = true;
                event.setCanceled(true);
                return;
            }
            if (tryPickupFromOverlay(idx, 1)) {
                captureRightRelease = true;
                event.setCanceled(true);
            }
        }
    }

    static void onScreenMouseDragged(RtsScreenEvent.MouseDragged.Pre event) {
        if (shiftImportDragging) {
            if (OverlayInteraction.isLeftMouseDown()
                    && Screen.hasShiftDown()
                    && RtsClientUiStateStore.isOverlayShiftImportEnabled()
                    && ClientRtsController.get().canUseStorageOverlay()
                    && event.getScreen() == shiftImportDragScreen
                    && RtsClientInputPolicy.isOverlayContainer(event.getScreen())
                    && event.getScreen() instanceof AbstractContainerScreen<?> screen) {
                tryContinueShiftImportDrag(screen, event.getMouseX(), event.getMouseY());
            } else {
                endShiftImportDrag();
            }
            event.setCanceled(true);
            return;
        }
        if (!overlayDragging
                || !ClientRtsController.get().canUseStorageOverlay()
                || !RtsClientUiStateStore.isContainerOverlayEnabled()
                || !RtsClientInputPolicy.isOverlayContainer(event.getScreen())) {
            return;
        }
        OverlayProfile profile = overlayProfile();
        updateOverlayDrag(event.getScreen(), toOverlayMouse(event.getMouseX(), profile), toOverlayMouse(event.getMouseY(), profile), profile);
        event.setCanceled(true);
    }

    static void onScreenMouseReleased(RtsScreenEvent.MouseButtonReleased.Pre event) {
        if (!RtsClientInputPolicy.canHandleOverlayInput(event.getScreen())) {
            endOverlayDrag();
            endShiftImportDrag();
            captureLeftRelease = false;
            captureRightRelease = false;
            return;
        }

        if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            endShiftImportDrag();
        }

        if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT && overlayDragging) {
            endOverlayDrag();
            captureLeftRelease = false;
            event.setCanceled(true);
            return;
        }

        if (OVERLAY_CRAFT_DIALOG.isOpen()) {
            captureLeftRelease = false;
            captureRightRelease = false;
            event.setCanceled(true);
            return;
        }

        if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT && captureLeftRelease) {
            captureLeftRelease = false;
            event.setCanceled(true);
            return;
        }

        if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_RIGHT && captureRightRelease) {
            captureRightRelease = false;
            event.setCanceled(true);
            return;
        }

        if (event.getButton() != GLFW.GLFW_MOUSE_BUTTON_LEFT && event.getButton() != GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            return;
        }

        trySendPendingCraftRefill(event.getScreen(), event.getButton());

        // Click-to-pick / click-to-return is handled on mouse press so the carried item does not snap back on release.
    }

    static void onScreenMouseScrolled(RtsScreenEvent.MouseScrolled.Pre event) {
        if (!RtsClientInputPolicy.canHandleOverlayInput(event.getScreen())) {
            return;
        }

        if (OVERLAY_CRAFT_DIALOG.isOpen()) {
            OVERLAY_CRAFT_DIALOG.mouseScrolled(event.getScrollDeltaY());
            event.setCanceled(true);
            return;
        }

        OverlayProfile profile = overlayProfile();
        double mx = toOverlayMouse(event.getMouseX(), profile);
        double my = toOverlayMouse(event.getMouseY(), profile);
        OverlayLayout layout = resolveOverlayLayout(profile);
        if (!inside(mx, my, layout.panelX(), layout.panelY(), layout.panelW(), layout.panelH())) {
            return;
        }

        if (!layout.craftCollapsed() && inside(mx, my, layout.craftPanelX(), layout.craftPanelY(), layout.craftPanelW(), layout.craftPanelH())) {
            int maxScroll = maxOverlayCraftScroll(ClientRtsController.get(), layout.craftVisibleRows());
            if (event.getScrollDeltaY() > 0.0D) {
                overlayCraftScroll = Math.max(0, overlayCraftScroll - 1);
            } else if (event.getScrollDeltaY() < 0.0D) {
                overlayCraftScroll = Math.min(maxScroll, overlayCraftScroll + 1);
                if (overlayCraftScroll >= maxScroll && ClientRtsController.get().hasMoreCraftables()) {
                    ClientRtsController.get().requestMoreCraftables();
                }
            }
        } else if (event.getScrollDeltaY() > 0.0D) {
            ClientRtsController.get().prevPage();
        } else if (event.getScrollDeltaY() < 0.0D) {
            ClientRtsController.get().nextPage();
        }
        event.setCanceled(true);
    }


}
