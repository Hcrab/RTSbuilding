package com.rtsbuilding.rtsbuilding.client.input;


import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.input.overlay.OverlayInteraction;
import com.rtsbuilding.rtsbuilding.client.popup.RtsCraftFeedbackPopup;
import com.rtsbuilding.rtsbuilding.client.popup.RtsCraftQuantityDialog;
import com.rtsbuilding.rtsbuilding.client.record.CraftableEntry;
import com.rtsbuilding.rtsbuilding.client.record.StorageEntry;
import com.rtsbuilding.rtsbuilding.client.screen.culling.RtsCullingClientState;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.RtsCraftTerminalScreen;
import com.rtsbuilding.rtsbuilding.client.util.RtsClientUiUtil;
import com.rtsbuilding.rtsbuilding.common.persist.RtsClientUiStateStore;
import com.rtsbuilding.rtsbuilding.network.storage.C2SRtsReturnCarriedPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.rtsbuilding.rtsbuilding.client.input.overlay.OverlayInputHandler.*;
import static com.rtsbuilding.rtsbuilding.client.input.overlay.OverlayInteraction.*;
import static com.rtsbuilding.rtsbuilding.client.input.overlay.OverlayLayoutHelper.*;
import static com.rtsbuilding.rtsbuilding.client.input.overlay.OverlayRenderer.*;

@EventBusSubscriber(modid = RtsbuildingMod.MODID, value = Dist.CLIENT)
public final class RtsClientInputGate {
    public static String pendingOverlayCarriedItemId = "";
    public static boolean captureLeftRelease;
    public static boolean captureRightRelease;
    public static boolean overlaySearchFocused;
    public static String overlaySearchDraft = "";
    public static boolean overlayCraftSearchFocused;
    public static String overlayCraftSearchDraft = "";
    public static boolean overlayCollapsed;
    public static boolean overlayCraftCollapsed;
    public static boolean overlayInfoOpen;
    public static int overlayCraftScroll;
    public static int overlayLastCraftablesStorageRevision = -1;
    public static final RtsCraftQuantityDialog OVERLAY_CRAFT_DIALOG = new RtsCraftQuantityDialog();
    public static Screen activeOverlayScreen;
    public static boolean overlayBootstrapRequested;
    public static boolean overlayDragging;
    public static double overlayDragOffsetX;
    public static double overlayDragOffsetY;
    public static boolean shiftImportDragging;
    public static Screen shiftImportDragScreen;
    public static final Set<Integer> shiftImportDragSlots = new HashSet<>();
    public static Screen pendingCraftRefillScreen;
    public static int pendingCraftRefillButton = -1;
    public static List<ItemStack> pendingCraftRefillBlueprint = List.of();
    public static String pendingCraftResultItemId = "";
    public static int pendingCraftResultCount;
    public static final ItemStack[] RETURN_QUEUE = new ItemStack[RETURN_SLOTS];
    public static final long[] RETURN_QUEUE_EXPIRY = new long[RETURN_SLOTS];

    static {
        Arrays.fill(RETURN_QUEUE, ItemStack.EMPTY);
    }

    private RtsClientInputGate() {
    }

    @SubscribeEvent
    public static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
        if (ClientRtsController.get().isEnabled()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRenderGuiLayer(RenderGuiLayerEvent.Pre event) {
        if (!ClientRtsController.get().isEnabled()) {
            return;
        }

        if (event.getName().equals(VanillaGuiLayers.CROSSHAIR)
                || event.getName().equals(VanillaGuiLayers.HOTBAR)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        if (ClientRtsController.get().isEnabled()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onClientLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        // 登录也主动清一次，覆盖崩服或异常断线时未完整收到退出事件的情况。
        RtsCullingClientState.resetForWorldChange();
    }

    @SubscribeEvent
    public static void onClientLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        overlayBootstrapRequested = false;
        activeOverlayScreen = null;
        RtsCullingClientState.resetForWorldChange();
        // Clear stale workflow data so it does not linger in the UI
        // when the player joins a different world (save).
        ClientRtsController.get().clearWorkflowData();
    }

    public static List<Rect2i> getJeiOverlayExtraAreas(Screen screen) {
        VisibleOverlayLayout visible = resolveVisibleOverlayLayout(screen);
        if (visible == null) {
            return List.of();
        }
        return List.of(toGuiRect(
                visible.layout().panelX(),
                visible.layout().panelY(),
                visible.layout().panelW(),
                visible.layout().panelH(),
                visible.profile().renderScale()));
    }

    public static JeiOverlayIngredient getJeiOverlayIngredientUnderMouse(double mouseX, double mouseY) {
        Minecraft minecraft = Minecraft.getInstance();
        VisibleOverlayLayout visible = resolveVisibleOverlayLayout(minecraft == null ? null : minecraft.screen);
        if (visible == null || visible.layout().overlayCollapsed()) {
            return null;
        }
        double scale = Math.max(0.001D, visible.profile().renderScale());
        double overlayMouseX = mouseX / scale;
        double overlayMouseY = mouseY / scale;
        OverlayLayout layout = visible.layout();
        int index = resolveOverlaySlotIndex(overlayMouseX, overlayMouseY, layout.gridX(), layout.gridY(), layout.storageRows());
        if (index < 0) {
            return null;
        }
        List<StorageEntry> entries = ClientRtsController.get().getStorageEntries();
        if (index >= entries.size()) {
            return null;
        }
        ItemStack stack = entries.get(index).stack();
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        int slotX = layout.gridX() + (index % STORAGE_COLS) * SLOT_PITCH;
        int slotY = layout.gridY() + (index / STORAGE_COLS) * SLOT_PITCH;
        return new JeiOverlayIngredient(stack.copy(), toGuiRect(slotX, slotY, SLOT_SIZE, SLOT_SIZE, scale));
    }

    @SubscribeEvent
    public static void onScreenRenderPost(ScreenEvent.Render.Post event) {
        if (event.getScreen() instanceof BuilderScreen) {
            return;
        }
        if (event.getScreen() instanceof RtsCraftTerminalScreen) {
            return;
        }
        if (!(event.getScreen() instanceof AbstractContainerScreen<?>)) {
            return;
        }

        if (event.getScreen() instanceof InventoryScreen) {
            renderInventoryRtsButtons(event.getGuiGraphics(), Minecraft.getInstance().font, event.getScreen(), event.getMouseX(), event.getMouseY());
        }
        if (!RtsClientUiStateStore.isContainerOverlayEnabled()) {
            clearOverlaySearchFocus();
            OVERLAY_CRAFT_DIALOG.close();
            return;
        }

        ClientRtsController controller = ClientRtsController.get();
        if (!controller.canUseStorageOverlay()) {
            requestOverlayBootstrap(event.getScreen(), controller);
            return;
        }
        syncOverlayScreen(event.getScreen(), controller);

        Minecraft minecraft = Minecraft.getInstance();
        GuiGraphics g = event.getGuiGraphics();
        OverlayProfile profile = overlayProfile();
        double mouseX = toOverlayMouse(event.getMouseX(), profile);
        double mouseY = toOverlayMouse(event.getMouseY(), profile);
        OverlayLayout layout = resolveOverlayLayout(profile);
        syncOverlaySearchDrafts(controller);
        syncOverlayCraftables(controller);

        g.pose().pushPose();
        g.pose().scale((float) profile.renderScale(), (float) profile.renderScale(), 1.0F);

        if (!layout.overlayCollapsed()) {
            drawOverlayWindowFrame(g, layout.craftPanelX(), layout.craftPanelY(), layout.craftPanelW(), layout.craftPanelH());
            renderOverlayCraftablesPanel(g, minecraft.font, mouseX, mouseY, layout, controller);
        }

        drawOverlayWindowFrame(g, layout.storagePanelX(), layout.storagePanelY(), STORAGE_PANEL_W, layout.storagePanelH());
        drawMiniButton(g, minecraft.font, layout.dragX(), layout.headerY(), OVERLAY_DRAG_W, OVERLAY_HEADER_H,
                Component.translatable("screen.rtsbuilding.overlay.drag_button").getString());
        drawMiniButton(g, minecraft.font, layout.sortX(), layout.headerY(), 12, OVERLAY_HEADER_H, sortShort(controller.getStorageSort()));
        drawMiniButton(g, minecraft.font, layout.dirX(), layout.headerY(), 12, OVERLAY_HEADER_H,
                controller.isStorageSortAscending() ? "A" : "D");

        int searchBg = overlaySearchFocused ? 0xAA304153 : 0xAA202731;
        g.fill(layout.searchX(), layout.headerY(), layout.searchX() + layout.searchW(), layout.headerY() + OVERLAY_HEADER_H, searchBg);
        g.hLine(layout.searchX(), layout.searchX() + layout.searchW(), layout.headerY(), 0xFF61758A);
        g.hLine(layout.searchX(), layout.searchX() + layout.searchW(), layout.headerY() + OVERLAY_HEADER_H, 0xFF10161D);
        g.vLine(layout.searchX(), layout.headerY(), layout.headerY() + OVERLAY_HEADER_H, 0xFF61758A);
        g.vLine(layout.searchX() + layout.searchW(), layout.headerY(), layout.headerY() + OVERLAY_HEADER_H, 0xFF10161D);

        String searchText = overlaySearchDraft == null ? "" : overlaySearchDraft;
        String display = trimToWidth(minecraft.font, searchText, Math.max(8, layout.searchW() - OVERLAY_SEARCH_CLEAR_W - 5));
        g.drawString(minecraft.font, display, layout.searchX() + 2, layout.headerY() + 2, 0xEAF2FF, false);
        if (overlaySearchFocused && (System.currentTimeMillis() / 300L) % 2L == 0L) {
            int caretX = layout.searchX() + 2 + minecraft.font.width(display) + 1;
            g.fill(caretX, layout.headerY() + 2, caretX + 1, layout.headerY() + OVERLAY_HEADER_H - 2, 0xFFEAF2FF);
        }
        g.fill(layout.clearX(), layout.headerY(), layout.clearX() + OVERLAY_SEARCH_CLEAR_W, layout.headerY() + OVERLAY_HEADER_H, 0xAA2A3340);
        RtsClientUiUtil.drawCenteredStringNoShadow(g, minecraft.font, "x",
                layout.clearX() + OVERLAY_SEARCH_CLEAR_W / 2, layout.headerY() + 2,
                searchText.isEmpty() ? 0x88A0B4C8 : 0xFFFFFF);

        if (!layout.overlayCollapsed()) {
            g.fill(layout.pageX(), layout.pagePrevY(), layout.pageX() + PAGE_BUTTON_W, layout.pagePrevY() + PAGE_BUTTON_H, 0xAA2A2A2A);
            RtsClientUiUtil.drawCenteredStringNoShadow(g, minecraft.font, "^",
                    layout.pageX() + PAGE_BUTTON_W / 2, layout.pagePrevY() + 1, 0xFFFFFF);
            String pageText = (controller.getStoragePage() + 1) + "/" + controller.getStorageTotalPages();
            RtsClientUiUtil.drawCenteredStringNoShadow(g, minecraft.font, pageText,
                    layout.pageX() + PAGE_BUTTON_W / 2, layout.pageTextY(), 0xDDDDDD);
            g.fill(layout.pageX(), layout.pageNextY(), layout.pageX() + PAGE_BUTTON_W, layout.pageNextY() + PAGE_BUTTON_H, 0xAA2A2A2A);
            RtsClientUiUtil.drawCenteredStringNoShadow(g, minecraft.font, "v",
                    layout.pageX() + PAGE_BUTTON_W / 2, layout.pageNextY() + 1, 0xFFFFFF);
        }

        if (!layout.overlayCollapsed()) {
            renderQuickbar(g, minecraft.font, layout.quickbarX(), layout.quickbarY());
        }

        var entries = controller.getStorageEntries();
        int visibleStorageRows = layout.overlayCollapsed() ? 1 : layout.storageRows();
        int visibleStorageSlots = STORAGE_COLS * visibleStorageRows;
        int maxSlots = Math.min(entries.size(), visibleStorageSlots);
        for (int i = 0; i < visibleStorageSlots; i++) {
            int cx = layout.gridX() + (i % STORAGE_COLS) * SLOT_PITCH;
            int cy = layout.gridY() + (i / STORAGE_COLS) * SLOT_PITCH;
            g.fill(cx, cy, cx + SLOT_SIZE, cy + SLOT_SIZE, 0xAA131313);
            if (i < maxSlots) {
                var entry = entries.get(i);
                g.renderItem(entry.stack(), cx + 1, cy + 1);
                drawSlotCountOverlay(g, minecraft.font, cx, cy, SLOT_SIZE, RtsClientUiUtil.compactCount(entry.count()), 0xFFF7E6A8);
            }
        }

        pruneReturnQueue();
        if (!layout.overlayCollapsed()) {
            for (int i = 0; i < RETURN_SLOTS; i++) {
                int cx = layout.returnX() + i * SLOT_PITCH;
                int cy = layout.returnY();
                g.fill(cx, cy, cx + SLOT_SIZE, cy + SLOT_SIZE, 0xAA20262E);
                g.hLine(cx, cx + SLOT_SIZE, cy, 0xFF4E5A67);
                g.hLine(cx, cx + SLOT_SIZE, cy + SLOT_SIZE, 0xFF161A20);
                g.vLine(cx, cy, cy + SLOT_SIZE, 0xFF4E5A67);
                g.vLine(cx + SLOT_SIZE, cy, cy + SLOT_SIZE, 0xFF161A20);

                ItemStack preview = RETURN_QUEUE[i];
                if (!preview.isEmpty()) {
                    g.renderItem(preview, cx + 1, cy + 1);
                    drawSlotCountOverlay(g, minecraft.font, cx, cy, SLOT_SIZE, RtsClientUiUtil.compactCount(preview.getCount()), 0xFFE8F6FF);
                } else {
                    g.drawString(minecraft.font, "+", cx + 6, cy + 5, 0xAACEE1FF, false);
                }
            }
        }
        renderOverlayBottomControls(g, minecraft.font, layout);
        renderOverlayRefreshButton(g, minecraft.font, layout, mouseX, mouseY, controller);
        renderOverlayInfoButton(g, minecraft.font, layout, mouseX, mouseY);
        if (!layout.overlayCollapsed()) {
            renderOverlayShiftImportButton(g, minecraft.font, layout, mouseX, mouseY);
        }

        if (!OVERLAY_CRAFT_DIALOG.isOpen()) {
            int hoveredStorage = resolveOverlaySlotIndex(mouseX, mouseY, layout.gridX(), layout.gridY(), visibleStorageRows);
            if (hoveredStorage >= 0 && hoveredStorage < maxSlots) {
                var entry = entries.get(hoveredStorage);
                g.renderTooltip(minecraft.font, entry.stack(), (int) mouseX, (int) mouseY);
                g.drawString(
                        minecraft.font,
                        storageCountDetail(controller, entry.count()),
                        (int) mouseX + 10,
                        (int) mouseY + 18,
                        0xFFFFAA);
            }

            int hoveredCraft = resolveOverlayCraftableEntryIndex(mouseX, mouseY, layout);
            if (hoveredCraft >= 0 && hoveredCraft < controller.getCraftableEntries().size()) {
                CraftableEntry entry = controller.getCraftableEntries().get(hoveredCraft);
                g.renderTooltip(minecraft.font, entry.stack(), (int) mouseX, (int) mouseY);
                String detail = entry.craftable()
                        ? "Right click: choose recipe/count"
                        : entry.missingSummary();
                if (detail != null && !detail.isBlank()) {
                    g.drawString(minecraft.font,
                            detail,
                            (int) mouseX + 10,
                            (int) mouseY + 18,
                            entry.craftable() ? 0xFFAEE8AE : 0xFFFFB0B0,
                            false);
                }
            }

            int hoveredQuick = layout.overlayCollapsed() ? -1 : resolveQuickbarSlotIndex(mouseX, mouseY, layout.quickbarX(), layout.quickbarY());
            if (hoveredQuick >= 0) {
                ItemStack preview = controller.getQuickSlotPreview(hoveredQuick);
                String itemId = controller.getQuickSlotItemId(hoveredQuick);
                if (!preview.isEmpty()) {
                    g.renderTooltip(minecraft.font, preview, (int) mouseX, (int) mouseY);
                    g.drawString(minecraft.font,
                            "x" + (itemId == null ? 0 : resolvePinnedItemCount(itemId)),
                            (int) mouseX + 10,
                            (int) mouseY + 18,
                            0xFFFFAA);
                }
            }

            int hoveredReturn = resolveReturnSlotIndex(mouseX, mouseY, layout.returnX(), layout.returnY());
            if (hoveredReturn >= 0) {
                ItemStack preview = RETURN_QUEUE[hoveredReturn];
                if (!preview.isEmpty()) {
                    g.renderTooltip(minecraft.font, preview, (int) mouseX, (int) mouseY);
                }
            }
        }
        if (overlayInfoOpen) {
            renderOverlayInfoPanel(g, minecraft.font, layout);
        }

        g.pose().popPose();

        if (OVERLAY_CRAFT_DIALOG.isOpen()) {
            OVERLAY_CRAFT_DIALOG.render(
                    g,
                    minecraft.font,
                    minecraft.getWindow().getGuiScaledWidth(),
                    minecraft.getWindow().getGuiScaledHeight(),
                    (int) event.getMouseX(),
                    (int) event.getMouseY());
        }
        RtsCraftFeedbackPopup.render(
                g,
                minecraft.font,
                minecraft.getWindow().getGuiScaledWidth(),
                controller);

    }

    @SubscribeEvent
    public static void onScreenMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT
                && event.getScreen() instanceof InventoryScreen
                && handleInventoryRtsButtonClick(event.getScreen(), event.getMouseX(), event.getMouseY())) {
            event.setCanceled(true);
            return;
        }

        if (!ClientRtsController.get().canUseStorageOverlay()) {
            return;
        }
        if (event.getScreen() instanceof BuilderScreen) {
            return;
        }
        if (event.getScreen() instanceof RtsCraftTerminalScreen) {
            return;
        }
        if (!(event.getScreen() instanceof AbstractContainerScreen<?>)) {
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

    @SubscribeEvent
    public static void onScreenMouseDragged(ScreenEvent.MouseDragged.Pre event) {
        if (shiftImportDragging) {
            if (OverlayInteraction.isLeftMouseDown()
                    && Screen.hasShiftDown()
                    && RtsClientUiStateStore.isOverlayShiftImportEnabled()
                    && ClientRtsController.get().canUseStorageOverlay()
                    && event.getScreen() == shiftImportDragScreen
                    && event.getScreen() instanceof AbstractContainerScreen<?> screen
                    && !(event.getScreen() instanceof BuilderScreen)
                    && !(event.getScreen() instanceof RtsCraftTerminalScreen)) {
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
                || event.getScreen() instanceof BuilderScreen
                || event.getScreen() instanceof RtsCraftTerminalScreen
                || !(event.getScreen() instanceof AbstractContainerScreen<?>)) {
            return;
        }
        OverlayProfile profile = overlayProfile();
        updateOverlayDrag(event.getScreen(), toOverlayMouse(event.getMouseX(), profile), toOverlayMouse(event.getMouseY(), profile), profile);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onScreenMouseReleased(ScreenEvent.MouseButtonReleased.Pre event) {
        if (!ClientRtsController.get().canUseStorageOverlay()
                || !RtsClientUiStateStore.isContainerOverlayEnabled()
                || event.getScreen() instanceof BuilderScreen
                || event.getScreen() instanceof RtsCraftTerminalScreen
                || !(event.getScreen() instanceof AbstractContainerScreen<?>)) {
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

    @SubscribeEvent
    public static void onScreenMouseScrolled(ScreenEvent.MouseScrolled.Pre event) {
        if (!ClientRtsController.get().canUseStorageOverlay()
                || !RtsClientUiStateStore.isContainerOverlayEnabled()) {
            return;
        }
        if (event.getScreen() instanceof BuilderScreen) {
            return;
        }
        if (event.getScreen() instanceof RtsCraftTerminalScreen) {
            return;
        }
        if (!(event.getScreen() instanceof AbstractContainerScreen<?>)) {
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

    @SubscribeEvent
    public static void onScreenKeyPressed(ScreenEvent.KeyPressed.Pre event) {
        if (!ClientRtsController.get().canUseStorageOverlay()
                || !RtsClientUiStateStore.isContainerOverlayEnabled()
                || event.getScreen() instanceof BuilderScreen
                || event.getScreen() instanceof RtsCraftTerminalScreen
                || !(event.getScreen() instanceof AbstractContainerScreen<?>)) {
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

    @SubscribeEvent
    public static void onScreenCharTyped(ScreenEvent.CharacterTyped.Pre event) {
        if (!ClientRtsController.get().canUseStorageOverlay()
                || !RtsClientUiStateStore.isContainerOverlayEnabled()
                || event.getScreen() instanceof BuilderScreen
                || event.getScreen() instanceof RtsCraftTerminalScreen
                || !(event.getScreen() instanceof AbstractContainerScreen<?>)) {
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

    @SubscribeEvent
    public static void onScreenClosing(ScreenEvent.Closing event) {
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

        PacketDistributor.sendToServer(new C2SRtsReturnCarriedPayload(pendingOverlayCarriedItemId, carried.getCount()));
        minecraft.player.containerMenu.setCarried(ItemStack.EMPTY);
        pendingOverlayCarriedItemId = "";
    }

}
