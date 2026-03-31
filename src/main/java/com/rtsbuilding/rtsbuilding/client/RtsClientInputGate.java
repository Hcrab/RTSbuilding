package com.rtsbuilding.rtsbuilding.client;

import java.util.ArrayList;
import java.util.List;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.network.C2SRtsCraftRefillPayload;
import com.rtsbuilding.rtsbuilding.network.C2SRtsLinkedQuickMovePayload;
import com.rtsbuilding.rtsbuilding.network.C2SRtsLinkedPickupPayload;
import com.rtsbuilding.rtsbuilding.network.C2SRtsImportMenuSlotPayload;
import com.rtsbuilding.rtsbuilding.network.C2SRtsReturnCarriedPayload;
import com.rtsbuilding.rtsbuilding.network.RtsStorageSort;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = RtsbuildingMod.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class RtsClientInputGate {
    private static final int OVERLAY_MARGIN = 6;
    private static final int CRAFT_PANEL_W = 106;
    private static final int PANEL_GAP = 6;
    private static final int STORAGE_PANEL_W = 212;
    private static final int OVERLAY_W = CRAFT_PANEL_W + PANEL_GAP + STORAGE_PANEL_W;
    private static final int SLOT_PITCH = 20;
    private static final int SLOT_SIZE = 18;
    private static final int COLS = 9;
    private static final int ROWS = 4;
    private static final int CRAFT_COLS = 4;
    private static final int CRAFT_SLOT = 18;
    private static final int CRAFT_PITCH = 20;
    private static final int CRAFT_SEARCH_H = 12;
    private static final int CRAFT_APPLY_W = 16;
    private static final int CRAFT_TOGGLE_W = 30;
    private static final int RETURN_SLOTS = 5;
    private static final int QUICKBAR_Y_OFF = 17;
    private static final int GRID_Y_OFF = QUICKBAR_Y_OFF + SLOT_SIZE + 6;
    private static final int RETURN_LABEL_Y_OFF = GRID_Y_OFF + ROWS * SLOT_PITCH + 2;
    private static final int RETURN_Y_OFF = RETURN_LABEL_Y_OFF + 9;
    private static final int OVERLAY_H = RETURN_Y_OFF + SLOT_SIZE + 6;
    private static final int OVERLAY_HEADER_Y = 3;
    private static final int OVERLAY_HEADER_H = 11;
    private static final int OVERLAY_SORT_X = 40;
    private static final int OVERLAY_DIR_X = OVERLAY_SORT_X + 14;
    private static final int OVERLAY_SEARCH_X = OVERLAY_DIR_X + 16;
    private static final int OVERLAY_SEARCH_CLEAR_W = 10;
    private static final int OVERLAY_SEARCH_MAX = 64;
    private static final int OVERLAY_DRAG_W = 32;
    private static final long RETURN_PREVIEW_MS = 2000L;

    private static String pendingOverlayCarriedItemId = "";
    private static boolean captureLeftRelease;
    private static boolean captureRightRelease;
    private static boolean overlaySearchFocused;
    private static String overlaySearchDraft = "";
    private static boolean overlayCraftSearchFocused;
    private static String overlayCraftSearchDraft = "";
    private static int overlayCraftScroll;
    private static int overlayLastCraftablesStorageRevision = -1;
    private static final RtsCraftQuantityDialog OVERLAY_CRAFT_DIALOG = new RtsCraftQuantityDialog();
    private static Screen activeOverlayScreen;
    private static boolean overlayDragging;
    private static double overlayDragOffsetX;
    private static double overlayDragOffsetY;
    private static Screen pendingCraftRefillScreen;
    private static int pendingCraftRefillButton = -1;
    private static List<String> pendingCraftRefillBlueprint = List.of();
    private static String pendingCraftResultItemId = "";
    private static int pendingCraftResultCount;
    private static final ItemStack[] RETURN_QUEUE = new ItemStack[RETURN_SLOTS];
    private static final long[] RETURN_QUEUE_EXPIRY = new long[RETURN_SLOTS];

    static {
        for (int i = 0; i < RETURN_SLOTS; i++) {
            RETURN_QUEUE[i] = ItemStack.EMPTY;
        }
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

        syncOverlayScreen(event.getScreen());
        ClientRtsController controller = ClientRtsController.get();
        if (!controller.canUseStorageOverlay()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        GuiGraphics g = event.getGuiGraphics();
        OverlayLayout layout = resolveOverlayLayout(event.getScreen());
        syncOverlaySearchDrafts(controller);
        syncOverlayCraftables(controller);

        drawPanelFrame(g, layout.craftPanelX(), layout.panelY(), CRAFT_PANEL_W, OVERLAY_H, 0xBB141922, 0xFF637993, 0xFF0D1218);
        renderOverlayCraftablesPanel(g, minecraft.font, event.getMouseX(), event.getMouseY(), layout, controller);

        drawPanelFrame(g, layout.storagePanelX(), layout.panelY(), STORAGE_PANEL_W, OVERLAY_H, 0xBB0F1116, 0xFF637993, 0xFF0D1218);
        drawMiniButton(g, minecraft.font, layout.dragX(), layout.headerY(), OVERLAY_DRAG_W, OVERLAY_HEADER_H, "MOVE");
        g.drawString(minecraft.font, "RTS", layout.storagePanelX() + 44, layout.panelY() + 4, 0xFFFFFF);

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
        g.drawString(minecraft.font, display, layout.searchX() + 2, layout.headerY() + 2, 0xEAF2FF);
        if (overlaySearchFocused && (System.currentTimeMillis() / 300L) % 2L == 0L) {
            int caretX = layout.searchX() + 2 + minecraft.font.width(display) + 1;
            g.fill(caretX, layout.headerY() + 2, caretX + 1, layout.headerY() + OVERLAY_HEADER_H - 2, 0xFFEAF2FF);
        }
        g.fill(layout.clearX(), layout.headerY(), layout.clearX() + OVERLAY_SEARCH_CLEAR_W, layout.headerY() + OVERLAY_HEADER_H, 0xAA2A3340);
        g.drawCenteredString(minecraft.font, "x", layout.clearX() + OVERLAY_SEARCH_CLEAR_W / 2, layout.headerY() + 2,
                searchText.isEmpty() ? 0x88A0B4C8 : 0xFFFFFF);

        g.fill(layout.pageX(), layout.panelY() + 3, layout.pageX() + 12, layout.panelY() + 14, 0xAA2A2A2A);
        g.drawString(minecraft.font, "<", layout.pageX() + 4, layout.panelY() + 5, 0xFFFFFF);
        g.fill(layout.pageX() + 46, layout.panelY() + 3, layout.pageX() + 58, layout.panelY() + 14, 0xAA2A2A2A);
        g.drawString(minecraft.font, ">", layout.pageX() + 50, layout.panelY() + 5, 0xFFFFFF);
        g.drawString(minecraft.font, (controller.getStoragePage() + 1) + "/" + controller.getStorageTotalPages(), layout.pageX() + 15, layout.panelY() + 5, 0xDDDDDD);

        renderQuickbar(g, minecraft.font, layout.quickbarX(), layout.quickbarY());

        var entries = controller.getStorageEntries();
        int maxSlots = Math.min(entries.size(), COLS * ROWS);
        for (int i = 0; i < COLS * ROWS; i++) {
            int cx = layout.gridX() + (i % COLS) * SLOT_PITCH;
            int cy = layout.gridY() + (i / COLS) * SLOT_PITCH;
            g.fill(cx, cy, cx + SLOT_SIZE, cy + SLOT_SIZE, 0xAA131313);
            if (i < maxSlots) {
                var entry = entries.get(i);
                g.renderItem(entry.stack(), cx + 1, cy + 1);
                drawSlotCountOverlay(g, minecraft.font, cx, cy, SLOT_SIZE, compactCount(entry.count()), 0xFFF7E6A8);
            }
        }

        pruneReturnQueue();
        g.drawString(minecraft.font, "Return (drop to import)", layout.returnX(), layout.panelY() + RETURN_LABEL_Y_OFF, 0xD5E7FF);
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
                drawSlotCountOverlay(g, minecraft.font, cx, cy, SLOT_SIZE, compactCount(preview.getCount()), 0xFFE8F6FF);
            } else {
                g.drawString(minecraft.font, "+", cx + 6, cy + 5, 0xAACEE1FF);
            }
        }

        if (!OVERLAY_CRAFT_DIALOG.isOpen()) {
            int hoveredStorage = resolveOverlaySlotIndex(event.getMouseX(), event.getMouseY(), layout.gridX(), layout.gridY());
            if (hoveredStorage >= 0 && hoveredStorage < maxSlots) {
                var entry = entries.get(hoveredStorage);
                g.renderTooltip(minecraft.font, entry.stack(), (int) event.getMouseX(), (int) event.getMouseY());
                g.drawString(minecraft.font, "x" + entry.count(), (int) event.getMouseX() + 10, (int) event.getMouseY() + 18, 0xFFFFAA);
            }

            int hoveredCraft = resolveOverlayCraftableEntryIndex(event.getMouseX(), event.getMouseY(), layout);
            if (hoveredCraft >= 0 && hoveredCraft < controller.getCraftableEntries().size()) {
                ClientRtsController.CraftableEntry entry = controller.getCraftableEntries().get(hoveredCraft);
                g.renderTooltip(minecraft.font, entry.stack(), (int) event.getMouseX(), (int) event.getMouseY());
                String detail = entry.craftable()
                        ? "Right click: choose recipe/count"
                        : entry.missingSummary();
                if (detail != null && !detail.isBlank()) {
                    g.drawString(minecraft.font,
                            detail,
                            (int) event.getMouseX() + 10,
                            (int) event.getMouseY() + 18,
                            entry.craftable() ? 0xFFAEE8AE : 0xFFFFB0B0);
                }
            }
        }

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
        OverlayLayout layout = resolveOverlayLayout(event.getScreen());
        double mx = event.getMouseX();
        double my = event.getMouseY();
        capturePendingCraftRefill((AbstractContainerScreen<?>) event.getScreen(), mx, my, event.getButton());
        if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (inside(mx, my, layout.dragX(), layout.headerY(), OVERLAY_DRAG_W, OVERLAY_HEADER_H)) {
                beginOverlayDrag(mx, my, layout);
                captureLeftRelease = true;
                event.setCanceled(true);
                return;
            }
            if (Screen.hasShiftDown()) {
                if (tryImportHoveredMenuSlot((AbstractContainerScreen<?>) event.getScreen(), mx, my, event.getButton())) {
                    captureLeftRelease = true;
                    event.setCanceled(true);
                    return;
                }
                if (tryQuickMoveOverlayEntry((AbstractContainerScreen<?>) event.getScreen(), mx, my)) {
                    captureLeftRelease = true;
                    event.setCanceled(true);
                    return;
                }
            }
            if (!inside(mx, my, layout.panelX(), layout.panelY(), OVERLAY_W, OVERLAY_H)) {
                clearOverlaySearchFocus();
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
            if (inside(mx, my, layout.pageX(), layout.panelY() + 3, 12, 11)) {
                ClientRtsController.get().prevPage();
                captureLeftRelease = true;
                event.setCanceled(true);
                return;
            }
            if (inside(mx, my, layout.pageX() + 46, layout.panelY() + 3, 12, 11)) {
                ClientRtsController.get().nextPage();
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

            int idx = resolveOverlaySlotIndex(mx, my, layout.gridX(), layout.gridY());
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
            if (Screen.hasShiftDown()) {
                if (tryImportHoveredMenuSlot((AbstractContainerScreen<?>) event.getScreen(), mx, my, event.getButton())) {
                    captureRightRelease = true;
                    event.setCanceled(true);
                    return;
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

            int idx = resolveOverlaySlotIndex(mx, my, layout.gridX(), layout.gridY());
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
        if (!overlayDragging
                || !ClientRtsController.get().canUseStorageOverlay()
                || event.getScreen() instanceof BuilderScreen
                || event.getScreen() instanceof RtsCraftTerminalScreen
                || !(event.getScreen() instanceof AbstractContainerScreen<?>)) {
            return;
        }
        updateOverlayDrag(event.getScreen(), event.getMouseX(), event.getMouseY());
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onScreenMouseReleased(ScreenEvent.MouseButtonReleased.Pre event) {
        if (!ClientRtsController.get().canUseStorageOverlay()
                || event.getScreen() instanceof BuilderScreen
                || event.getScreen() instanceof RtsCraftTerminalScreen
                || !(event.getScreen() instanceof AbstractContainerScreen<?>)) {
            endOverlayDrag();
            captureLeftRelease = false;
            captureRightRelease = false;
            return;
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

        if (OVERLAY_CRAFT_DIALOG.isOpen()) {
            OVERLAY_CRAFT_DIALOG.mouseScrolled(event.getScrollDeltaY());
            event.setCanceled(true);
            return;
        }

        OverlayLayout layout = resolveOverlayLayout(event.getScreen());
        if (!inside(event.getMouseX(), event.getMouseY(), layout.panelX(), layout.panelY(), OVERLAY_W, OVERLAY_H)) {
            return;
        }

        if (inside(event.getMouseX(), event.getMouseY(), layout.craftPanelX(), layout.panelY(), CRAFT_PANEL_W, OVERLAY_H)) {
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
        overlayCraftScroll = 0;
        overlayLastCraftablesStorageRevision = -1;
        activeOverlayScreen = null;
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

    private static int resolveOverlayX(int screenWidth) {
        int minX = OVERLAY_MARGIN;
        int maxX = Math.max(minX, screenWidth - OVERLAY_W - OVERLAY_MARGIN);
        return minX + (int) Math.round((maxX - minX) * ClientRtsController.get().getStoragePanelXNormalized());
    }

    private static int resolveOverlayY(int screenHeight) {
        int minY = OVERLAY_MARGIN;
        int maxY = Math.max(minY, screenHeight - OVERLAY_H - OVERLAY_MARGIN);
        return minY + (int) Math.round((maxY - minY) * ClientRtsController.get().getStoragePanelYNormalized());
    }

    private static OverlayLayout resolveOverlayLayout(Screen screen) {
        Minecraft minecraft = Minecraft.getInstance();
        int sw = minecraft.getWindow().getGuiScaledWidth();
        int sh = minecraft.getWindow().getGuiScaledHeight();
        int panelX = Mth.clamp(resolveOverlayX(sw), OVERLAY_MARGIN, Math.max(OVERLAY_MARGIN, sw - OVERLAY_W - OVERLAY_MARGIN));
        int panelY = Mth.clamp(resolveOverlayY(sh), OVERLAY_MARGIN, Math.max(OVERLAY_MARGIN, sh - OVERLAY_H - OVERLAY_MARGIN));
        int craftPanelX = panelX;
        int storagePanelX = craftPanelX + CRAFT_PANEL_W + PANEL_GAP;
        int headerY = panelY + OVERLAY_HEADER_Y;
        int pageX = storagePanelX + STORAGE_PANEL_W - 58;
        int searchX = storagePanelX + OVERLAY_SEARCH_X;
        int searchW = Math.max(26, pageX - searchX - 3);
        int clearX = searchX + searchW - OVERLAY_SEARCH_CLEAR_W;
        int craftSearchX = craftPanelX + 4;
        int craftSearchY = panelY + 15;
        int craftSearchW = Math.max(24, CRAFT_PANEL_W - CRAFT_APPLY_W - CRAFT_TOGGLE_W - 16);
        int craftApplyX = craftSearchX + craftSearchW + 4;
        int craftToggleX = craftApplyX + CRAFT_APPLY_W + 4;
        int craftGridY = craftSearchY + CRAFT_SEARCH_H + 6;
        int craftVisibleRows = Math.max(1, (OVERLAY_H - (craftGridY - panelY) - 6) / CRAFT_PITCH);
        return new OverlayLayout(
                panelX,
                panelY,
                craftPanelX,
                storagePanelX,
                headerY,
                pageX,
                searchX,
                searchW,
                clearX,
                craftSearchX,
                craftSearchY,
                craftSearchW,
                craftApplyX,
                craftToggleX,
                craftGridY,
                craftVisibleRows);
    }

    private static void drawPanelFrame(GuiGraphics g, int x, int y, int w, int h, int fillColor, int light, int dark) {
        RtsClientUiUtil.drawPanelFrame(g, x, y, w, h, fillColor, light, dark);
    }

    private static void drawMiniButton(GuiGraphics g, Font font, int x, int y, int w, int h, String label) {
        g.fill(x, y, x + w, y + h, 0xAA2B3642);
        g.hLine(x, x + w, y, 0xFF667D95);
        g.hLine(x, x + w, y + h, 0xFF111821);
        g.vLine(x, y, y + h, 0xFF667D95);
        g.vLine(x + w, y, y + h, 0xFF111821);
        g.drawCenteredString(font, label, x + w / 2, y + 2, 0xFFFFFF);
    }

    private static String sortShort(RtsStorageSort sort) {
        return switch (sort) {
            case QUANTITY -> "Q";
            case MOD -> "M";
            case NAME -> "N";
        };
    }

    private static String trimToWidth(Font font, String text, int maxWidth) {
        return RtsClientUiUtil.trimToWidth(font, text, maxWidth);
    }

    private static void clearOverlaySearchFocus() {
        overlaySearchFocused = false;
        overlayCraftSearchFocused = false;
    }

    private static void setOverlaySearchFocused(boolean focused) {
        overlaySearchFocused = focused;
        if (focused) {
            overlayCraftSearchFocused = false;
        }
    }

    private static void setOverlayCraftSearchFocused(boolean focused) {
        overlayCraftSearchFocused = focused;
        if (focused) {
            overlaySearchFocused = false;
        }
    }

    private static void syncOverlaySearchDrafts(ClientRtsController controller) {
        if (!overlaySearchFocused) {
            overlaySearchDraft = controller.getStorageSearch();
        }
    }

    private static void syncOverlayCraftables(ClientRtsController controller) {
        if (overlayLastCraftablesStorageRevision != controller.getStorageRevision()) {
            overlayLastCraftablesStorageRevision = controller.getStorageRevision();
            overlayCraftScroll = 0;
            controller.requestCraftables();
        }
    }

    private static void appendSearchText(String raw, boolean craftSearch) {
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

    private static void syncOverlayScreen(Screen screen) {
        if (screen == activeOverlayScreen) {
            return;
        }
        activeOverlayScreen = screen;
        endOverlayDrag();
        clearOverlaySearchFocus();
        overlaySearchDraft = "";
        overlayCraftSearchDraft = "";
        overlayCraftScroll = 0;
        overlayLastCraftablesStorageRevision = -1;
        OVERLAY_CRAFT_DIALOG.close();

        ClientRtsController controller = ClientRtsController.get();
        if (!controller.getStorageSearch().isEmpty()) {
            controller.setStorageSearch("");
        } else {
            controller.requestStoragePage(controller.getStoragePage());
        }

        if (!controller.getCraftablesSearch().isEmpty()) {
            controller.setCraftablesSearch("");
        } else if (controller.isCraftablesShowUnavailable()) {
            controller.setCraftablesShowUnavailable(false);
        } else {
            controller.requestCraftables();
        }
    }

    private static void capturePendingCraftRefill(AbstractContainerScreen<?> screen, double mouseX, double mouseY, int button) {
        clearPendingCraftRefill();
        if (screen == null || Screen.hasShiftDown()) {
            return;
        }
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT && button != GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            return;
        }
        if (!(screen.getMenu() instanceof CraftingMenu menu)) {
            return;
        }
        int slotIndex = resolveHoveredMenuSlot(screen, mouseX, mouseY);
        if (slotIndex != 0) {
            return;
        }

        List<String> blueprint = new ArrayList<>(9);
        for (int i = 0; i < 9; i++) {
            Slot slot = menu.getSlot(1 + i);
            ItemStack stack = slot == null ? ItemStack.EMPTY : slot.getItem();
            var itemId = stack.isEmpty() ? null : BuiltInRegistries.ITEM.getKey(stack.getItem());
            blueprint.add(itemId == null ? "" : itemId.toString());
        }

        Slot resultSlot = menu.getSlot(0);
        ItemStack result = resultSlot == null ? ItemStack.EMPTY : resultSlot.getItem();
        var resultId = result.isEmpty() ? null : BuiltInRegistries.ITEM.getKey(result.getItem());

        pendingCraftRefillScreen = screen;
        pendingCraftRefillButton = button;
        pendingCraftRefillBlueprint = blueprint;
        pendingCraftResultItemId = resultId == null ? "" : resultId.toString();
        pendingCraftResultCount = result.isEmpty() ? 0 : result.getCount();
    }

    private static void trySendPendingCraftRefill(Screen screen, int button) {
        if (pendingCraftRefillScreen != screen
                || pendingCraftRefillButton != button
                || pendingCraftRefillBlueprint.size() != 9) {
            clearPendingCraftRefill();
            return;
        }
        PacketDistributor.sendToServer(new C2SRtsCraftRefillPayload(
                new ArrayList<>(pendingCraftRefillBlueprint),
                pendingCraftResultItemId,
                pendingCraftResultCount));
        clearPendingCraftRefill();
    }

    private static void clearPendingCraftRefill() {
        pendingCraftRefillScreen = null;
        pendingCraftRefillButton = -1;
        pendingCraftRefillBlueprint = List.of();
        pendingCraftResultItemId = "";
        pendingCraftResultCount = 0;
    }

    private static void beginOverlayDrag(double mouseX, double mouseY, OverlayLayout layout) {
        overlayDragging = true;
        overlayDragOffsetX = mouseX - layout.panelX();
        overlayDragOffsetY = mouseY - layout.panelY();
    }

    private static void updateOverlayDrag(Screen screen, double mouseX, double mouseY) {
        Minecraft minecraft = Minecraft.getInstance();
        int sw = minecraft.getWindow().getGuiScaledWidth();
        int sh = minecraft.getWindow().getGuiScaledHeight();
        int minX = OVERLAY_MARGIN;
        int maxX = Math.max(minX, sw - OVERLAY_W - OVERLAY_MARGIN);
        int minY = OVERLAY_MARGIN;
        int maxY = Math.max(minY, sh - OVERLAY_H - OVERLAY_MARGIN);
        int panelX = Mth.clamp((int) Math.round(mouseX - overlayDragOffsetX), minX, maxX);
        int panelY = Mth.clamp((int) Math.round(mouseY - overlayDragOffsetY), minY, maxY);

        ClientRtsController controller = ClientRtsController.get();
        controller.updateStoragePanelLayout(
                normalizeBetween(panelX, minX, maxX),
                normalizeBetween(panelY, minY, maxY),
                controller.getStoragePanelWidthNormalized(),
                controller.getStoragePanelHeightNormalized());
    }

    private static void endOverlayDrag() {
        overlayDragging = false;
    }

    private static double normalizeBetween(int value, int min, int max) {
        if (max <= min) {
            return 0.0D;
        }
        return Mth.clamp((value - (double) min) / (double) (max - min), 0.0D, 1.0D);
    }

    private static void renderOverlayCraftablesPanel(
            GuiGraphics g,
            Font font,
            double mouseX,
            double mouseY,
            OverlayLayout layout,
            ClientRtsController controller) {
        g.drawString(font, "Craft", layout.craftPanelX() + 5, layout.panelY() + 4, 0xEAF2FF);

        int searchBg = overlayCraftSearchFocused ? 0xAA304153 : 0xAA202731;
        drawPanelFrame(g, layout.craftSearchX(), layout.craftSearchY(), layout.craftSearchW(), CRAFT_SEARCH_H, searchBg, 0xFF5E738A, 0xFF111921);
        String searchText = overlayCraftSearchDraft == null ? "" : overlayCraftSearchDraft;
        String display = trimToWidth(font, searchText, Math.max(10, layout.craftSearchW() - 5));
        g.drawString(font, display, layout.craftSearchX() + 2, layout.craftSearchY() + 2, 0xEAF2FF);
        if (overlayCraftSearchFocused && (System.currentTimeMillis() / 300L) % 2L == 0L) {
            int caretX = layout.craftSearchX() + 2 + font.width(display) + 1;
            g.fill(caretX, layout.craftSearchY() + 2, caretX + 1, layout.craftSearchY() + CRAFT_SEARCH_H - 2, 0xFFEAF2FF);
        }

        boolean craftSearchDirty = hasPendingOverlayCraftSearch();
        int applyBg = craftSearchDirty ? 0xAA4C6E39 : 0xAA24303A;
        drawPanelFrame(g, layout.craftApplyX(), layout.craftSearchY(), CRAFT_APPLY_W, CRAFT_SEARCH_H, applyBg, 0xFF6E8799, 0xFF111821);
        g.drawCenteredString(font,
                "OK",
                layout.craftApplyX() + CRAFT_APPLY_W / 2,
                layout.craftSearchY() + 2,
                craftSearchDirty ? 0xFFFFFF : 0xFFB8C7D6);

        int toggleBg = controller.isCraftablesShowUnavailable() ? 0xAA5A3D2A : 0xAA2C5A41;
        drawPanelFrame(g, layout.craftToggleX(), layout.craftSearchY(), CRAFT_TOGGLE_W, CRAFT_SEARCH_H, toggleBg, 0xFF667D95, 0xFF111821);
        g.drawCenteredString(font,
                controller.isCraftablesShowUnavailable() ? "ALL" : "MAKE",
                layout.craftToggleX() + CRAFT_TOGGLE_W / 2,
                layout.craftSearchY() + 2,
                0xFFFFFF);

        List<ClientRtsController.CraftableEntry> entries = controller.getCraftableEntries();
        int maxScroll = maxOverlayCraftScroll(controller, layout.craftVisibleRows());
        overlayCraftScroll = Mth.clamp(overlayCraftScroll, 0, maxScroll);
        int startIndex = overlayCraftScroll * CRAFT_COLS;

        for (int row = 0; row < layout.craftVisibleRows(); row++) {
            for (int col = 0; col < CRAFT_COLS; col++) {
                int index = startIndex + row * CRAFT_COLS + col;
                int slotX = layout.craftPanelX() + 4 + col * CRAFT_PITCH;
                int slotY = layout.craftGridY() + row * CRAFT_PITCH;
                int fill = 0xAA1A212B;
                if (index < entries.size()) {
                    fill = entries.get(index).craftable() ? 0xAA214131 : 0xAA3F2323;
                }
                drawPanelFrame(g, slotX, slotY, CRAFT_SLOT, CRAFT_SLOT, fill, 0xFF596D84, 0xFF11171E);
                if (index >= entries.size()) {
                    continue;
                }

                ClientRtsController.CraftableEntry entry = entries.get(index);
                g.renderItem(entry.stack(), slotX + 1, slotY + 1);
                if (entry.resultCount() > 1) {
                    drawSlotCountOverlay(g, font, slotX, slotY, CRAFT_SLOT, compactCount(entry.resultCount()), 0xFFE8F4FF);
                }
                if (!entry.craftable()) {
                    g.fill(slotX + 1, slotY + 1, slotX + CRAFT_SLOT - 1, slotY + CRAFT_SLOT - 1, 0x44220000);
                }
                if (inside(mouseX, mouseY, slotX, slotY, CRAFT_SLOT, CRAFT_SLOT)) {
                    g.fill(slotX + 1, slotY + 1, slotX + CRAFT_SLOT - 1, slotY + CRAFT_SLOT - 1, 0x22FFFFFF);
                }
            }
        }
    }

    private static int maxOverlayCraftScroll(ClientRtsController controller, int visibleRows) {
        int totalRows = Math.max(1, (int) Math.ceil(controller.getCraftableEntries().size() / (double) CRAFT_COLS));
        return Math.max(0, totalRows - visibleRows);
    }

    private static int resolveOverlayCraftableEntryIndex(double mouseX, double mouseY, OverlayLayout layout) {
        overlayCraftScroll = Mth.clamp(overlayCraftScroll, 0, maxOverlayCraftScroll(ClientRtsController.get(), layout.craftVisibleRows()));
        if (!inside(mouseX, mouseY, layout.craftPanelX() + 4, layout.craftGridY(), CRAFT_COLS * CRAFT_PITCH, layout.craftVisibleRows() * CRAFT_PITCH)) {
            return -1;
        }

        int col = Mth.floor((mouseX - (layout.craftPanelX() + 4)) / CRAFT_PITCH);
        int row = Mth.floor((mouseY - layout.craftGridY()) / CRAFT_PITCH);
        if (col < 0 || col >= CRAFT_COLS || row < 0 || row >= layout.craftVisibleRows()) {
            return -1;
        }

        int slotX = layout.craftPanelX() + 4 + col * CRAFT_PITCH;
        int slotY = layout.craftGridY() + row * CRAFT_PITCH;
        if (!inside(mouseX, mouseY, slotX, slotY, CRAFT_SLOT, CRAFT_SLOT)) {
            return -1;
        }

        int index = overlayCraftScroll * CRAFT_COLS + row * CRAFT_COLS + col;
        return index < ClientRtsController.get().getCraftableEntries().size() ? index : -1;
    }

    private static boolean handleOverlayCraftLeftClick(double mouseX, double mouseY, OverlayLayout layout) {
        if (!inside(mouseX, mouseY, layout.craftPanelX(), layout.panelY(), CRAFT_PANEL_W, OVERLAY_H)) {
            return false;
        }
        setOverlaySearchFocused(false);
        if (inside(mouseX, mouseY, layout.craftSearchX(), layout.craftSearchY(), layout.craftSearchW(), CRAFT_SEARCH_H)) {
            setOverlayCraftSearchFocused(true);
            return true;
        }
        if (inside(mouseX, mouseY, layout.craftApplyX(), layout.craftSearchY(), CRAFT_APPLY_W, CRAFT_SEARCH_H)) {
            applyOverlayCraftSearch();
            clearOverlaySearchFocus();
            return true;
        }
        if (inside(mouseX, mouseY, layout.craftToggleX(), layout.craftSearchY(), CRAFT_TOGGLE_W, CRAFT_SEARCH_H)) {
            clearOverlaySearchFocus();
            ClientRtsController.get().toggleCraftablesShowUnavailable();
            return true;
        }
        clearOverlaySearchFocus();
        return true;
    }

    private static boolean handleOverlayCraftRightClick(double mouseX, double mouseY, OverlayLayout layout) {
        if (!inside(mouseX, mouseY, layout.craftPanelX(), layout.panelY(), CRAFT_PANEL_W, OVERLAY_H)) {
            return false;
        }
        int index = resolveOverlayCraftableEntryIndex(mouseX, mouseY, layout);
        if (index < 0 || index >= ClientRtsController.get().getCraftableEntries().size()) {
            return true;
        }
        ClientRtsController.CraftableEntry entry = ClientRtsController.get().getCraftableEntries().get(index);
        if (!entry.craftable()) {
            return true;
        }
        clearOverlaySearchFocus();
        RtsCraftablesUiHelper.openCraftQuantityDialog(OVERLAY_CRAFT_DIALOG, entry);
        return true;
    }

    private static void submitOverlayCraftDialogIfReady() {
        RtsCraftablesUiHelper.submitPendingCraftRequest(OVERLAY_CRAFT_DIALOG, ClientRtsController.get());
    }

    private static void applyOverlayCraftSearch() {
        overlayCraftSearchDraft = normalizeOverlayCraftSearchDraft(overlayCraftSearchDraft);
        overlayCraftScroll = 0;
        ClientRtsController.get().setCraftablesSearch(overlayCraftSearchDraft);
    }

    private static boolean hasPendingOverlayCraftSearch() {
        return !normalizeOverlayCraftSearchDraft(overlayCraftSearchDraft)
                .equals(normalizeOverlayCraftSearchDraft(ClientRtsController.get().getCraftablesSearch()));
    }

    private static String normalizeOverlayCraftSearchDraft(String value) {
        return RtsCraftablesUiHelper.normalizeSearchDraft(value);
    }

    private static int resolveOverlaySlotIndex(double mouseX, double mouseY, int gridX, int gridY) {
        if (!inside(mouseX, mouseY, gridX, gridY, COLS * SLOT_PITCH, ROWS * SLOT_PITCH)) {
            return -1;
        }
        int col = Mth.floor((mouseX - gridX) / SLOT_PITCH);
        int row = Mth.floor((mouseY - gridY) / SLOT_PITCH);
        if (col < 0 || col >= COLS || row < 0 || row >= ROWS) {
            return -1;
        }
        return row * COLS + col;
    }

    private static int resolveQuickbarSlotIndex(double mouseX, double mouseY, int x, int y) {
        if (!inside(mouseX, mouseY, x, y, COLS * SLOT_PITCH, SLOT_SIZE)) {
            return -1;
        }
        int col = Mth.floor((mouseX - x) / SLOT_PITCH);
        if (col < 0 || col >= COLS) {
            return -1;
        }
        int slotX = x + col * SLOT_PITCH;
        return mouseX <= slotX + SLOT_SIZE ? col : -1;
    }

    private static boolean tryImportHoveredMenuSlot(AbstractContainerScreen<?> screen, double mouseX, double mouseY, int button) {
        int menuSlot = resolveHoveredMenuSlot(screen, mouseX, mouseY);
        if (menuSlot < 0) {
            return false;
        }

        // Unified behavior: crafting result slot uses Shift + LMB only.
        if (menuSlot == 0 && button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return true;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || screen.getMenu() == null || menuSlot >= screen.getMenu().slots.size()) {
            return false;
        }
        Slot slot = screen.getMenu().slots.get(menuSlot);
        if (slot == null || !slot.hasItem() || !slot.mayPickup(minecraft.player)) {
            return false;
        }
        if (isPlayerInventorySlot(slot, minecraft.player) && !isInventoryOrCraftingScreen(screen)) {
            return false;
        }

        PacketDistributor.sendToServer(new C2SRtsImportMenuSlotPayload(menuSlot));
        return true;
    }

    private static boolean tryQuickMoveOverlayEntry(AbstractContainerScreen<?> screen, double mouseX, double mouseY) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !minecraft.player.containerMenu.getCarried().isEmpty()) {
            return false;
        }
        OverlayLayout layout = resolveOverlayLayout(screen);
        int idx = resolveOverlaySlotIndex(mouseX, mouseY, layout.gridX(), layout.gridY());
        if (idx < 0 || idx >= ClientRtsController.get().getStorageEntries().size()) {
            return false;
        }

        var entry = ClientRtsController.get().getStorageEntries().get(idx);
        if (entry == null || entry.stack().isEmpty()) {
            return false;
        }

        PacketDistributor.sendToServer(new C2SRtsLinkedQuickMovePayload(entry.itemId()));
        ClientRtsController.get().selectStorageEntry(idx);
        pendingOverlayCarriedItemId = "";
        return true;
    }

    private static int resolveHoveredMenuSlot(AbstractContainerScreen<?> screen, double mouseX, double mouseY) {
        if (screen == null || screen.getMenu() == null) {
            return -1;
        }
        Slot hovered = screen.getSlotUnderMouse();
        if (hovered != null && hovered.hasItem()) {
            return screen.getMenu().slots.indexOf(hovered);
        }
        for (int i = 0; i < screen.getMenu().slots.size(); i++) {
            Slot slot = screen.getMenu().slots.get(i);
            int sx = screen.getGuiLeft() + slot.x;
            int sy = screen.getGuiTop() + slot.y;
            if (inside(mouseX, mouseY, sx, sy, SLOT_SIZE, SLOT_SIZE) && slot.hasItem()) {
                return i;
            }
        }
        return -1;
    }

    private static int resolveReturnSlotIndex(double mouseX, double mouseY, int x, int y) {
        if (!inside(mouseX, mouseY, x, y, RETURN_SLOTS * SLOT_PITCH, SLOT_SIZE)) {
            return -1;
        }
        int col = Mth.floor((mouseX - x) / SLOT_PITCH);
        if (col < 0 || col >= RETURN_SLOTS) {
            return -1;
        }
        int cx = x + col * SLOT_PITCH;
        return mouseX <= cx + SLOT_SIZE ? col : -1;
    }

    private static boolean isPlayerInventorySlot(Slot slot, net.minecraft.world.entity.player.Player player) {
        return slot != null && player != null && slot.container == player.getInventory();
    }

    private static boolean isInventoryOrCraftingScreen(Screen screen) {
        return screen instanceof InventoryScreen || screen instanceof CraftingScreen;
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }

    private static String compactCount(long value) {
        return RtsClientUiUtil.compactCount(value);
    }

    private static void drawSlotCountOverlay(GuiGraphics g, net.minecraft.client.gui.Font font, int slotX, int slotY,
            int slotSize, String countText, int color) {
        g.pose().pushPose();
        g.pose().translate(0.0F, 0.0F, 300.0F);
        g.fill(slotX + 1, slotY + slotSize - 8, slotX + slotSize - 1, slotY + slotSize - 1, 0xB0000000);
        g.drawString(font, countText, slotX + 2, slotY + slotSize - 7, color, true);
        g.pose().popPose();
    }

    private static void applyLocalCarriedPreview(ItemStack pickedPrototype, int requested) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || pickedPrototype.isEmpty()) {
            return;
        }

        ItemStack carried = minecraft.player.containerMenu.getCarried();
        int wanted = Math.max(1, requested);
        if (carried.isEmpty()) {
            ItemStack preview = pickedPrototype.copy();
            preview.setCount(Math.min(wanted, preview.getMaxStackSize()));
            minecraft.player.containerMenu.setCarried(preview);
            return;
        }

        if (!ItemStack.isSameItemSameComponents(carried, pickedPrototype)) {
            return;
        }
        int grow = Math.min(wanted, carried.getMaxStackSize() - carried.getCount());
        if (grow <= 0) {
            return;
        }
        carried.grow(grow);
        minecraft.player.containerMenu.setCarried(carried);
    }

    private static void renderQuickbar(GuiGraphics g, Font font, int x, int y) {
        ClientRtsController controller = ClientRtsController.get();
        for (int i = 0; i < COLS; i++) {
            int cx = x + i * SLOT_PITCH;
            int cy = y;
            ItemStack preview = controller.getQuickSlotPreview(i);
            String itemId = controller.getQuickSlotItemId(i);
            boolean filled = itemId != null && !itemId.isBlank();
            int bg = filled ? 0xAA253043 : 0xAA1A1A1A;
            g.fill(cx, cy, cx + SLOT_SIZE, cy + SLOT_SIZE, bg);
            g.hLine(cx, cx + SLOT_SIZE, cy, 0xFF67758A);
            g.hLine(cx, cx + SLOT_SIZE, cy + SLOT_SIZE, 0xFF0C0D10);
            g.vLine(cx, cy, cy + SLOT_SIZE, 0xFF67758A);
            g.vLine(cx + SLOT_SIZE, cy, cy + SLOT_SIZE, 0xFF0C0D10);

            if (!preview.isEmpty()) {
                g.renderItem(preview, cx + 1, cy + 1);
                if (itemId.equals(controller.getSelectedItemId())) {
                    g.fill(cx + 1, cy + 1, cx + SLOT_SIZE - 1, cy + SLOT_SIZE - 1, 0x3340FF80);
                }
                drawSlotCountOverlay(g, font, cx, cy, SLOT_SIZE, compactCount(resolvePinnedItemCount(itemId)), 0xFFF7E6A8);
            } else {
                g.drawCenteredString(font, Integer.toString(i + 1), cx + SLOT_SIZE / 2, cy + 5, 0x88D0D8E4);
            }
        }
    }

    private static long resolvePinnedItemCount(String itemId) {
        return ClientRtsController.get().getStorageTotalCount(itemId);
    }

    private static void selectOverlayQuickbarSlot(int index) {
        if (index < 0 || index >= COLS) {
            return;
        }
        ClientRtsController.get().selectQuickSlot(index);
    }

    private static boolean tryPickupFromOverlay(int index, int requestedAmount) {
        if (index < 0 || index >= ClientRtsController.get().getStorageEntries().size()) {
            return false;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return false;
        }

        var entry = ClientRtsController.get().getStorageEntries().get(index);
        ItemStack carried = minecraft.player.containerMenu.getCarried();
        int wanted = requestedFromCarried(carried, entry.stack(), requestedAmount);
        if (wanted <= 0) {
            return false;
        }

        applyLocalCarriedPreview(entry.stack(), wanted);
        PacketDistributor.sendToServer(new C2SRtsLinkedPickupPayload(entry.itemId(), wanted));
        ClientRtsController.get().selectStorageEntry(index);
        pendingOverlayCarriedItemId = entry.itemId();
        return true;
    }

    private static boolean tryDepositCarriedToLinked(int requestedAmount) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return false;
        }
        ItemStack carried = minecraft.player.containerMenu.getCarried();
        if (carried.isEmpty()) {
            return false;
        }
        var itemId = BuiltInRegistries.ITEM.getKey(carried.getItem());
        if (itemId == null) {
            return false;
        }

        int amount = Math.max(1, Math.min(requestedAmount, carried.getCount()));
        PacketDistributor.sendToServer(new C2SRtsReturnCarriedPayload(itemId.toString(), amount));

        ItemStack preview = carried.copy();
        preview.setCount(amount);
        enqueueReturnPreview(preview);

        carried.shrink(amount);
        if (carried.isEmpty()) {
            minecraft.player.containerMenu.setCarried(ItemStack.EMPTY);
        } else {
            minecraft.player.containerMenu.setCarried(carried);
        }

        pendingOverlayCarriedItemId = "";
        return true;
    }

    private static int requestedFromCarried(ItemStack carried, ItemStack target, int requestedAmount) {
        int requested = requestedAmount <= 0 ? 1 : requestedAmount;
        if (carried.isEmpty()) {
            return Math.min(requested, target.getMaxStackSize());
        }
        if (!ItemStack.isSameItemSameComponents(carried, target)) {
            return 0;
        }
        return Math.min(requested, carried.getMaxStackSize() - carried.getCount());
    }

    private static void enqueueReturnPreview(ItemStack stack) {
        pruneReturnQueue();
        int slot = -1;
        for (int i = 0; i < RETURN_SLOTS; i++) {
            if (RETURN_QUEUE[i].isEmpty()) {
                slot = i;
                break;
            }
        }
        if (slot < 0) {
            for (int i = 1; i < RETURN_SLOTS; i++) {
                RETURN_QUEUE[i - 1] = RETURN_QUEUE[i];
                RETURN_QUEUE_EXPIRY[i - 1] = RETURN_QUEUE_EXPIRY[i];
            }
            slot = RETURN_SLOTS - 1;
        }
        RETURN_QUEUE[slot] = stack.copy();
        RETURN_QUEUE_EXPIRY[slot] = System.currentTimeMillis() + RETURN_PREVIEW_MS;
    }

    private static void pruneReturnQueue() {
        long now = System.currentTimeMillis();
        for (int i = 0; i < RETURN_SLOTS; i++) {
            if (!RETURN_QUEUE[i].isEmpty() && now >= RETURN_QUEUE_EXPIRY[i]) {
                RETURN_QUEUE[i] = ItemStack.EMPTY;
                RETURN_QUEUE_EXPIRY[i] = 0L;
            }
        }

        int write = 0;
        for (int read = 0; read < RETURN_SLOTS; read++) {
            if (RETURN_QUEUE[read].isEmpty()) {
                continue;
            }
            if (write != read) {
                RETURN_QUEUE[write] = RETURN_QUEUE[read];
                RETURN_QUEUE_EXPIRY[write] = RETURN_QUEUE_EXPIRY[read];
                RETURN_QUEUE[read] = ItemStack.EMPTY;
                RETURN_QUEUE_EXPIRY[read] = 0L;
            }
            write++;
        }
    }

    private record OverlayLayout(
            int panelX,
            int panelY,
            int craftPanelX,
            int storagePanelX,
            int headerY,
            int pageX,
            int searchX,
            int searchW,
            int clearX,
            int craftSearchX,
            int craftSearchY,
            int craftSearchW,
            int craftApplyX,
            int craftToggleX,
            int craftGridY,
            int craftVisibleRows) {
        private int dragX() {
            return this.storagePanelX + 6;
        }

        private int sortX() {
            return this.storagePanelX + OVERLAY_SORT_X;
        }

        private int dirX() {
            return this.storagePanelX + OVERLAY_DIR_X;
        }

        private int quickbarX() {
            return this.storagePanelX + 6;
        }

        private int quickbarY() {
            return this.panelY + QUICKBAR_Y_OFF;
        }

        private int gridX() {
            return this.storagePanelX + 6;
        }

        private int gridY() {
            return this.panelY + GRID_Y_OFF;
        }

        private int returnX() {
            return this.storagePanelX + 6;
        }

        private int returnY() {
            return this.panelY + RETURN_Y_OFF;
        }
    }

}
