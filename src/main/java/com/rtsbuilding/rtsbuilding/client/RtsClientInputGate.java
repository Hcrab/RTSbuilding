package com.rtsbuilding.rtsbuilding.client;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.network.craft.C2SRtsCraftRefillPayload;
import com.rtsbuilding.rtsbuilding.network.storage.C2SRtsLinkedQuickMovePayload;
import com.rtsbuilding.rtsbuilding.network.storage.C2SRtsLinkedPickupPayload;
import com.rtsbuilding.rtsbuilding.network.storage.C2SRtsImportMenuSlotPayload;
import com.rtsbuilding.rtsbuilding.network.storage.C2SRtsReturnCarriedPayload;
import com.rtsbuilding.rtsbuilding.network.storage.RtsStorageSort;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
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
    private static final int CRAFT_PANEL_W = 104;
    private static final int CRAFT_PANEL_COLLAPSED_W = 44;
    private static final int PANEL_GAP = 5;
    private static final int STORAGE_PANEL_W = 142;
    private static final int OVERLAY_W = CRAFT_PANEL_W + PANEL_GAP + STORAGE_PANEL_W;
    private static final int SLOT_PITCH = 18;
    private static final int SLOT_SIZE = 16;
    private static final int STORAGE_COLS = 5;
    private static final int STORAGE_ROWS = 3;
    private static final int QUICKBAR_SLOTS = 5;
    private static final int CRAFT_COLS = 4;
    private static final int CRAFT_SLOT = 18;
    private static final int CRAFT_PITCH = 20;
    private static final int CRAFT_SEARCH_H = 12;
    private static final int CRAFT_APPLY_W = 18;
    private static final int CRAFT_TOGGLE_W = 34;
    private static final int RETURN_SLOTS = 2;
    private static final int PAGE_BUTTON_W = 14;
    private static final int PAGE_BUTTON_H = 11;
    private static final double OVERLAY_TARGET_GUI_SCALE = 3.0D;
    private static final double HIGH_SCALE_COMPACT_THRESHOLD = 3.0D;
    private static final double EXTREME_SCALE_COMPACT_THRESHOLD = 5.5D;
    private static final int STACKED_CRAFT_ROWS = 2;
    private static final int QUICKBAR_Y_OFF = 17;
    private static final int GRID_Y_OFF = QUICKBAR_Y_OFF + SLOT_SIZE + 6;
    private static final int OVERLAY_HEADER_Y = 3;
    private static final int OVERLAY_HEADER_H = 11;
    private static final int OVERLAY_CLOSE_W = 34;
    private static final int OVERLAY_COLLAPSE_W = 52;
    private static final int OVERLAY_BOTTOM_SMALL_W = 14;
    private static final int OVERLAY_BOTTOM_BUTTON_H = 12;
    private static final int OVERLAY_BOTTOM_GAP = 4;
    private static final int OVERLAY_SORT_X = 41;
    private static final int OVERLAY_DIR_X = OVERLAY_SORT_X + 14;
    private static final int OVERLAY_SEARCH_X = OVERLAY_DIR_X + 16;
    private static final int OVERLAY_SEARCH_CLEAR_W = 10;
    private static final int OVERLAY_SEARCH_MAX = 64;
    private static final int OVERLAY_DRAG_W = 32;
    private static final long RETURN_PREVIEW_MS = 2000L;
    private static final int INVENTORY_RTS_BUTTON_W = 70;
    private static final int INVENTORY_RTS_BUTTON_H = 14;
    private static final int INVENTORY_RTS_BUTTON_GAP = 4;

    private static String pendingOverlayCarriedItemId = "";
    private static boolean captureLeftRelease;
    private static boolean captureRightRelease;
    private static boolean overlaySearchFocused;
    private static String overlaySearchDraft = "";
    private static boolean overlayCraftSearchFocused;
    private static String overlayCraftSearchDraft = "";
    private static boolean overlayCollapsed;
    private static boolean overlayCraftCollapsed;
    private static boolean overlayInfoOpen;
    private static int overlayCraftScroll;
    private static int overlayLastCraftablesStorageRevision = -1;
    private static final RtsCraftQuantityDialog OVERLAY_CRAFT_DIALOG = new RtsCraftQuantityDialog();
    private static Screen activeOverlayScreen;
    private static boolean overlayBootstrapRequested;
    private static boolean overlayDragging;
    private static double overlayDragOffsetX;
    private static double overlayDragOffsetY;
    private static boolean shiftImportDragging;
    private static Screen shiftImportDragScreen;
    private static final Set<Integer> shiftImportDragSlots = new HashSet<>();
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
    public static void onClientLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        overlayBootstrapRequested = false;
        activeOverlayScreen = null;
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
        List<ClientRtsController.StorageEntry> entries = ClientRtsController.get().getStorageEntries();
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
            drawPanelFrame(g, layout.craftPanelX(), layout.craftPanelY(), layout.craftPanelW(), layout.craftPanelH(), 0xBB141922, 0xFF637993, 0xFF0D1218);
            renderOverlayCraftablesPanel(g, minecraft.font, mouseX, mouseY, layout, controller);
        }

        drawPanelFrame(g, layout.storagePanelX(), layout.storagePanelY(), STORAGE_PANEL_W, layout.storagePanelH(), 0xBB0F1116, 0xFF637993, 0xFF0D1218);
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
        g.drawString(minecraft.font, display, layout.searchX() + 2, layout.headerY() + 2, 0xEAF2FF);
        if (overlaySearchFocused && (System.currentTimeMillis() / 300L) % 2L == 0L) {
            int caretX = layout.searchX() + 2 + minecraft.font.width(display) + 1;
            g.fill(caretX, layout.headerY() + 2, caretX + 1, layout.headerY() + OVERLAY_HEADER_H - 2, 0xFFEAF2FF);
        }
        g.fill(layout.clearX(), layout.headerY(), layout.clearX() + OVERLAY_SEARCH_CLEAR_W, layout.headerY() + OVERLAY_HEADER_H, 0xAA2A3340);
        g.drawCenteredString(minecraft.font, "x", layout.clearX() + OVERLAY_SEARCH_CLEAR_W / 2, layout.headerY() + 2,
                searchText.isEmpty() ? 0x88A0B4C8 : 0xFFFFFF);

        if (!layout.overlayCollapsed()) {
            g.fill(layout.pageX(), layout.pagePrevY(), layout.pageX() + PAGE_BUTTON_W, layout.pagePrevY() + PAGE_BUTTON_H, 0xAA2A2A2A);
            g.drawCenteredString(minecraft.font, "^", layout.pageX() + PAGE_BUTTON_W / 2, layout.pagePrevY() + 1, 0xFFFFFF);
            String pageText = (controller.getStoragePage() + 1) + "/" + controller.getStorageTotalPages();
            g.drawCenteredString(minecraft.font, pageText, layout.pageX() + PAGE_BUTTON_W / 2, layout.pageTextY(), 0xDDDDDD);
            g.fill(layout.pageX(), layout.pageNextY(), layout.pageX() + PAGE_BUTTON_W, layout.pageNextY() + PAGE_BUTTON_H, 0xAA2A2A2A);
            g.drawCenteredString(minecraft.font, "v", layout.pageX() + PAGE_BUTTON_W / 2, layout.pageNextY() + 1, 0xFFFFFF);
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
                drawSlotCountOverlay(g, minecraft.font, cx, cy, SLOT_SIZE, compactCount(entry.count()), 0xFFF7E6A8);
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
                    drawSlotCountOverlay(g, minecraft.font, cx, cy, SLOT_SIZE, compactCount(preview.getCount()), 0xFFE8F6FF);
                } else {
                    g.drawString(minecraft.font, "+", cx + 6, cy + 5, 0xAACEE1FF);
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
                ClientRtsController.CraftableEntry entry = controller.getCraftableEntries().get(hoveredCraft);
                g.renderTooltip(minecraft.font, entry.stack(), (int) mouseX, (int) mouseY);
                String detail = entry.craftable()
                        ? "Right click: choose recipe/count"
                        : entry.missingSummary();
                if (detail != null && !detail.isBlank()) {
                    g.drawString(minecraft.font,
                            detail,
                            (int) mouseX + 10,
                            (int) mouseY + 18,
                            entry.craftable() ? 0xFFAEE8AE : 0xFFFFB0B0);
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
            if (isLeftMouseDown()
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

    private static int resolveOverlayX(int screenWidth, OverlayProfile profile) {
        int minX = OVERLAY_MARGIN;
        int maxX = Math.max(minX, screenWidth - currentOverlayWidth(profile) - OVERLAY_MARGIN);
        return minX + (int) Math.round((maxX - minX) * ClientRtsController.get().getStoragePanelXNormalized());
    }

    private static int resolveOverlayY(int screenHeight, OverlayProfile profile) {
        int minY = OVERLAY_MARGIN;
        int maxY = Math.max(minY, screenHeight - overlayHeight(profile) - OVERLAY_MARGIN);
        return minY + (int) Math.round((maxY - minY) * ClientRtsController.get().getStoragePanelYNormalized());
    }

    private static OverlayLayout resolveOverlayLayout(Screen screen) {
        return resolveOverlayLayout(overlayProfile());
    }

    private static VisibleOverlayLayout resolveVisibleOverlayLayout(Screen screen) {
        if (!shouldRenderContainerOverlay(screen)) {
            return null;
        }
        OverlayProfile profile = overlayProfile();
        return new VisibleOverlayLayout(profile, resolveOverlayLayout(profile));
    }

    private static boolean shouldRenderContainerOverlay(Screen screen) {
        if (screen == null
                || screen instanceof BuilderScreen
                || screen instanceof RtsCraftTerminalScreen
                || !(screen instanceof AbstractContainerScreen<?>)) {
            return false;
        }
        return RtsClientUiStateStore.isContainerOverlayEnabled()
                && ClientRtsController.get().canUseStorageOverlay();
    }

    private static Rect2i toGuiRect(int x, int y, int w, int h, double scale) {
        int rx = (int) Math.round(x * scale);
        int ry = (int) Math.round(y * scale);
        int rw = Math.max(1, (int) Math.round(w * scale));
        int rh = Math.max(1, (int) Math.round(h * scale));
        return new Rect2i(rx, ry, rw, rh);
    }

    private static OverlayLayout resolveOverlayLayout(OverlayProfile profile) {
        int sw = overlayVirtualWidth(profile);
        int sh = overlayVirtualHeight(profile);
        int panelW = currentOverlayWidth(profile);
        int panelH = overlayHeight(profile);
        int panelX = Mth.clamp(resolveOverlayX(sw, profile), OVERLAY_MARGIN, Math.max(OVERLAY_MARGIN, sw - panelW - OVERLAY_MARGIN));
        int panelY = Mth.clamp(resolveOverlayY(sh, profile), OVERLAY_MARGIN, Math.max(OVERLAY_MARGIN, sh - panelH - OVERLAY_MARGIN));
        boolean stacked = profile.stackCraftBelow();
        boolean collapsed = overlayCollapsed;
        boolean craftCollapsed = collapsed || isCraftPanelCollapsed(profile);
        int storagePanelH = storagePanelHeight(profile);
        int craftPanelW = stacked ? STORAGE_PANEL_W : craftCollapsed ? CRAFT_PANEL_COLLAPSED_W : CRAFT_PANEL_W;
        int craftPanelH = stacked ? craftPanelHeight(profile) : storagePanelH;
        int storagePanelX = collapsed || stacked ? panelX : panelX + craftPanelW + PANEL_GAP;
        int storagePanelY = panelY;
        int craftPanelX = panelX;
        int craftPanelY = stacked ? panelY + storagePanelH + PANEL_GAP : panelY;
        int headerY = storagePanelY + OVERLAY_HEADER_Y;
        int pageX = storagePanelX + STORAGE_PANEL_W - PAGE_BUTTON_W - 6;
        int pagePrevY = storagePanelY + 3;
        int pageTextY = pagePrevY + PAGE_BUTTON_H + 2;
        int pageNextY = pageTextY + 10;
        int searchX = storagePanelX + OVERLAY_SEARCH_X;
        int searchRight = collapsed ? storagePanelX + STORAGE_PANEL_W - 6 : pageX - 4;
        int searchW = Math.max(26, searchRight - searchX);
        int clearX = searchX + searchW - OVERLAY_SEARCH_CLEAR_W;
        int craftSearchX = craftPanelX + 4;
        int craftSearchY = craftPanelY + 15;
        int craftSearchW = Math.max(24, craftPanelW - CRAFT_APPLY_W - CRAFT_TOGGLE_W - 16);
        int craftApplyX = craftSearchX + craftSearchW + 4;
        int craftToggleX = craftApplyX + CRAFT_APPLY_W + 4;
        int craftGridY = craftSearchY + CRAFT_SEARCH_H + 6;
        int craftVisibleRows = Math.max(1, (craftPanelH - (craftGridY - craftPanelY) - 6) / CRAFT_PITCH);
        return new OverlayLayout(
                sw,
                sh,
                panelX,
                panelY,
                panelW,
                panelH,
                collapsed,
                stacked,
                craftPanelX,
                craftPanelY,
                craftPanelW,
                craftPanelH,
                craftCollapsed,
                profile.storageRows(),
                storagePanelX,
                storagePanelY,
                storagePanelH,
                headerY,
                pageX,
                pagePrevY,
                pageTextY,
                pageNextY,
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

    private static int currentOverlayWidth() {
        return currentOverlayWidth(overlayProfile());
    }

    private static int currentOverlayWidth(OverlayProfile profile) {
        if (overlayCollapsed) {
            return STORAGE_PANEL_W;
        }
        if (profile.stackCraftBelow()) {
            return STORAGE_PANEL_W;
        }
        int craftW = isCraftPanelCollapsed(profile) ? CRAFT_PANEL_COLLAPSED_W : CRAFT_PANEL_W;
        return craftW + PANEL_GAP + STORAGE_PANEL_W;
    }

    private static int overlayHeight(OverlayProfile profile) {
        if (overlayCollapsed) {
            return collapsedControlsYOff() + OVERLAY_BOTTOM_BUTTON_H + 6;
        }
        if (profile.stackCraftBelow()) {
            return craftPanelHeight(profile) + PANEL_GAP + storagePanelHeight(profile);
        }
        return storagePanelHeight(profile);
    }

    private static int storagePanelHeight(OverlayProfile profile) {
        if (overlayCollapsed) {
            return collapsedControlsYOff() + OVERLAY_BOTTOM_BUTTON_H + 6;
        }
        return returnYOff(profile) + SLOT_SIZE + 6;
    }

    private static int craftPanelHeight(OverlayProfile profile) {
        if (isCraftPanelCollapsed(profile)) {
            return OVERLAY_HEADER_H + 7;
        }
        if (profile.stackCraftBelow()) {
            return 15 + CRAFT_SEARCH_H + 6 + STACKED_CRAFT_ROWS * CRAFT_PITCH + 6;
        }
        return storagePanelHeight(profile);
    }

    private static int returnLabelYOff(OverlayProfile profile) {
        return GRID_Y_OFF + profile.storageRows() * SLOT_PITCH + 2;
    }

    private static int returnYOff(OverlayProfile profile) {
        return returnLabelYOff(profile) + OVERLAY_BOTTOM_BUTTON_H + 4;
    }

    private static int collapsedControlsYOff() {
        return QUICKBAR_Y_OFF + SLOT_SIZE + 4;
    }

    private static boolean isCraftPanelCollapsed(OverlayProfile profile) {
        return overlayCraftCollapsed;
    }

    private static OverlayProfile overlayProfile() {
        double guiScale = currentGuiScale();
        boolean highScale = guiScale > HIGH_SCALE_COMPACT_THRESHOLD;
        boolean extremeScale = guiScale >= EXTREME_SCALE_COMPACT_THRESHOLD;
        double renderScale = highScale
                ? Mth.clamp(OVERLAY_TARGET_GUI_SCALE / guiScale, 0.45D, 1.0D)
                : 1.0D;
        int rows = extremeScale ? 2 : highScale ? 3 : STORAGE_ROWS;
        return new OverlayProfile(guiScale, renderScale, rows, highScale);
    }

    private static double currentGuiScale() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getWindow() == null || minecraft.getWindow().getGuiScaledWidth() <= 0) {
            return OVERLAY_TARGET_GUI_SCALE;
        }
        double scale = minecraft.getWindow().getScreenWidth() / (double) Math.max(1, minecraft.getWindow().getGuiScaledWidth());
        return scale > 0.0D && Double.isFinite(scale) ? scale : OVERLAY_TARGET_GUI_SCALE;
    }

    private static double toOverlayMouse(double value, OverlayProfile profile) {
        return value / Math.max(0.001D, profile.renderScale());
    }

    private static int overlayVirtualWidth(OverlayProfile profile) {
        Minecraft minecraft = Minecraft.getInstance();
        int width = minecraft == null || minecraft.getWindow() == null ? 1 : minecraft.getWindow().getGuiScaledWidth();
        return Math.max(1, (int) Math.round(width / Math.max(0.001D, profile.renderScale())));
    }

    private static int overlayVirtualHeight(OverlayProfile profile) {
        Minecraft minecraft = Minecraft.getInstance();
        int height = minecraft == null || minecraft.getWindow() == null ? 1 : minecraft.getWindow().getGuiScaledHeight();
        return Math.max(1, (int) Math.round(height / Math.max(0.001D, profile.renderScale())));
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

    private static void requestOverlayBootstrap(Screen screen, ClientRtsController controller) {
        if (overlayBootstrapRequested || controller == null) {
            return;
        }
        overlayBootstrapRequested = true;
        activeOverlayScreen = screen;
        endOverlayDrag();
        endShiftImportDrag();
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

    private static void syncOverlayScreen(Screen screen, ClientRtsController controller) {
        if (screen == activeOverlayScreen) {
            return;
        }
        activeOverlayScreen = screen;
        endOverlayDrag();
        endShiftImportDrag();
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

    private static void updateOverlayDrag(Screen screen, double mouseX, double mouseY, OverlayProfile profile) {
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
        String header = layout.craftCollapsed() ? "Craft +" : "Craft -";
        g.drawString(font, trimToWidth(font, header, Math.max(8, layout.craftPanelW() - 8)), layout.craftPanelX() + 5, layout.craftPanelY() + 4, 0xEAF2FF);
        if (layout.craftCollapsed()) {
            return;
        }

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

    private static void renderOverlayInfoButton(GuiGraphics g, Font font, OverlayLayout layout, double mouseX, double mouseY) {
        int bg = overlayInfoOpen || inside(mouseX, mouseY, layout.infoX(), layout.controlsY(), OVERLAY_BOTTOM_SMALL_W, OVERLAY_BOTTOM_BUTTON_H)
                ? 0xAA3E5368
                : 0xAA24303A;
        drawPanelFrame(g, layout.infoX(), layout.controlsY(), OVERLAY_BOTTOM_SMALL_W, OVERLAY_BOTTOM_BUTTON_H, bg, 0xFF6E8799, 0xFF111821);
        g.drawCenteredString(font, "i", layout.infoX() + OVERLAY_BOTTOM_SMALL_W / 2, layout.controlsY() + 2, 0xFFEAF2FF);
    }

    private static void renderOverlayShiftImportButton(GuiGraphics g, Font font, OverlayLayout layout, double mouseX, double mouseY) {
        boolean enabled = RtsClientUiStateStore.isOverlayShiftImportEnabled();
        boolean hovered = inside(mouseX, mouseY, layout.shiftImportX(), layout.returnY(), layout.shiftImportW(), SLOT_SIZE);
        int bg = enabled
                ? hovered ? 0xCC3AA156 : 0xCC2C873F
                : hovered ? 0xAA3E5368 : 0xAA24303A;
        int light = enabled ? 0xFF74E88C : 0xFF6E8799;
        int dark = enabled ? 0xFF123A1D : 0xFF111821;
        drawPanelFrame(g, layout.shiftImportX(), layout.returnY(), layout.shiftImportW(), SLOT_SIZE, bg, light, dark);
        g.drawCenteredString(
                font,
                Component.translatable("screen.rtsbuilding.overlay.shift_import_button").getString(),
                layout.shiftImportX() + layout.shiftImportW() / 2,
                layout.returnY() + 4,
                0xFFEAF2FF);
    }

    private static void renderOverlayBottomControls(
            GuiGraphics g,
            Font font,
            OverlayLayout layout) {
        drawMiniButton(g, font, layout.closeX(), layout.controlsY(), OVERLAY_CLOSE_W, OVERLAY_BOTTOM_BUTTON_H,
                Component.translatable("screen.rtsbuilding.overlay.close_button").getString());
        Component collapseLabel = Component.translatable(layout.overlayCollapsed()
                ? "screen.rtsbuilding.overlay.expand_button"
                : "screen.rtsbuilding.overlay.collapse_button");
        drawMiniButton(g, font, layout.collapseX(), layout.controlsY(), OVERLAY_COLLAPSE_W, OVERLAY_BOTTOM_BUTTON_H,
                collapseLabel.getString());
    }

    private static void renderOverlayRefreshButton(
            GuiGraphics g,
            Font font,
            OverlayLayout layout,
            double mouseX,
            double mouseY,
            ClientRtsController controller) {
        boolean hovered = inside(mouseX, mouseY, layout.refreshX(), layout.controlsY(), OVERLAY_BOTTOM_SMALL_W, OVERLAY_BOTTOM_BUTTON_H);
        int bg = controller.isStorageScanRunning()
                ? 0xAA3F627E
                : hovered ? 0xAA3E5368 : 0xAA24303A;
        drawPanelFrame(g, layout.refreshX(), layout.controlsY(), OVERLAY_BOTTOM_SMALL_W, OVERLAY_BOTTOM_BUTTON_H, bg, 0xFF6E8799, 0xFF111821);
        g.drawCenteredString(font, "R", layout.refreshX() + OVERLAY_BOTTOM_SMALL_W / 2, layout.controlsY() + 2, 0xFFEAF2FF);
    }

    private static void disableContainerOverlay() {
        RtsClientUiStateStore.setContainerOverlayEnabled(false);
        overlayInfoOpen = false;
        overlayDragging = false;
        clearOverlaySearchFocus();
        OVERLAY_CRAFT_DIALOG.close();
    }

    private static void toggleOverlayShiftImportEnabled() {
        boolean enabled = !RtsClientUiStateStore.isOverlayShiftImportEnabled();
        RtsClientUiStateStore.setOverlayShiftImportEnabled(enabled);
        if (!enabled) {
            endShiftImportDrag();
        }
    }

    private static void renderOverlayInfoPanel(GuiGraphics g, Font font, OverlayLayout layout) {
        List<Component> lines = List.of(
                Component.translatable("screen.rtsbuilding.overlay.help.move"),
                Component.translatable("screen.rtsbuilding.overlay.help.sort"),
                Component.translatable("screen.rtsbuilding.overlay.help.direction"),
                Component.translatable("screen.rtsbuilding.overlay.help.search"),
                Component.translatable("screen.rtsbuilding.overlay.help.page"),
                Component.translatable("screen.rtsbuilding.overlay.help.refresh"),
                Component.translatable("screen.rtsbuilding.overlay.help.quick_slots"),
                Component.translatable("screen.rtsbuilding.overlay.help.return"),
                Component.translatable("screen.rtsbuilding.overlay.help.craft"),
                Component.translatable("screen.rtsbuilding.overlay.help.craft_item"),
                Component.translatable("screen.rtsbuilding.overlay.help.shift_drag"),
                Component.translatable("screen.rtsbuilding.overlay.help.tooltip"));
        int panelW = 220;
        int bodyH = 0;
        for (Component line : lines) {
            bodyH += Math.max(1, font.split(line, panelW - 12).size()) * 9;
        }
        int panelH = 20 + bodyH + 8;
        int sw = layout.screenW();
        int sh = layout.screenH();
        int x = Mth.clamp(layout.storagePanelX() + STORAGE_PANEL_W - panelW, 4, Math.max(4, sw - panelW - 4));
        int y = layout.panelY() + layout.panelH() + 4;
        if (y + panelH > sh - 4) {
            y = layout.panelY() - panelH - 4;
        }
        y = Mth.clamp(y, 4, Math.max(4, sh - panelH - 4));

        drawPanelFrame(g, x, y, panelW, panelH, 0xF0182028, 0xFF7489A0, 0xFF0B1016);
        g.drawString(font, Component.translatable("screen.rtsbuilding.overlay.help.title"), x + 6, y + 6, 0xFFFFFFFF);
        int textY = y + 19;
        for (Component line : lines) {
            for (var splitLine : font.split(line, panelW - 12)) {
                g.drawString(font, splitLine, x + 6, textY, 0xFFD8E6F5);
                textY += 9;
            }
        }
    }

    private static int maxOverlayCraftScroll(ClientRtsController controller, int visibleRows) {
        int totalRows = Math.max(1, (int) Math.ceil(controller.getCraftableEntries().size() / (double) CRAFT_COLS));
        return Math.max(0, totalRows - visibleRows);
    }

    private static int resolveOverlayCraftableEntryIndex(double mouseX, double mouseY, OverlayLayout layout) {
        if (layout.craftCollapsed()) {
            return -1;
        }
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
        if (!inside(mouseX, mouseY, layout.craftPanelX(), layout.craftPanelY(), layout.craftPanelW(), layout.craftPanelH())) {
            return false;
        }
        setOverlaySearchFocused(false);
        if (inside(mouseX, mouseY, layout.craftPanelX() + 3, layout.craftPanelY() + 2, Math.max(36, layout.craftPanelW() - 6), OVERLAY_HEADER_H + 2)) {
            overlayCraftCollapsed = !overlayCraftCollapsed;
            clearOverlaySearchFocus();
            return true;
        }
        if (layout.craftCollapsed()) {
            clearOverlaySearchFocus();
            return true;
        }
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
        if (!inside(mouseX, mouseY, layout.craftPanelX(), layout.craftPanelY(), layout.craftPanelW(), layout.craftPanelH())) {
            return false;
        }
        if (layout.craftCollapsed()) {
            return true;
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
        return resolveOverlaySlotIndex(mouseX, mouseY, gridX, gridY, overlayProfile().storageRows());
    }

    private static int resolveOverlaySlotIndex(double mouseX, double mouseY, int gridX, int gridY, int storageRows) {
        if (!inside(mouseX, mouseY, gridX, gridY, STORAGE_COLS * SLOT_PITCH, storageRows * SLOT_PITCH)) {
            return -1;
        }
        int col = Mth.floor((mouseX - gridX) / SLOT_PITCH);
        int row = Mth.floor((mouseY - gridY) / SLOT_PITCH);
        if (col < 0 || col >= STORAGE_COLS || row < 0 || row >= storageRows) {
            return -1;
        }
        return row * STORAGE_COLS + col;
    }

    private static int resolveQuickbarSlotIndex(double mouseX, double mouseY, int x, int y) {
        if (!inside(mouseX, mouseY, x, y, QUICKBAR_SLOTS * SLOT_PITCH, SLOT_SIZE)) {
            return -1;
        }
        int col = Mth.floor((mouseX - x) / SLOT_PITCH);
        if (col < 0 || col >= QUICKBAR_SLOTS) {
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

        if (!canImportMenuSlot(screen, menuSlot)) {
            return false;
        }

        PacketDistributor.sendToServer(new C2SRtsImportMenuSlotPayload(menuSlot));
        return true;
    }

    private static boolean tryStartShiftImportDrag(AbstractContainerScreen<?> screen, double mouseX, double mouseY) {
        int menuSlot = resolveHoveredMenuSlot(screen, mouseX, mouseY);
        if (!canDragImportMenuSlot(screen, menuSlot)) {
            return false;
        }
        shiftImportDragging = true;
        shiftImportDragScreen = screen;
        shiftImportDragSlots.clear();
        return trySendShiftImportDragSlot(screen, menuSlot);
    }

    private static boolean tryContinueShiftImportDrag(AbstractContainerScreen<?> screen, double mouseX, double mouseY) {
        int menuSlot = resolveHoveredMenuSlot(screen, mouseX, mouseY);
        return trySendShiftImportDragSlot(screen, menuSlot);
    }

    private static boolean trySendShiftImportDragSlot(AbstractContainerScreen<?> screen, int menuSlot) {
        if (shiftImportDragSlots.contains(menuSlot) || !canDragImportMenuSlot(screen, menuSlot)) {
            return false;
        }
        shiftImportDragSlots.add(menuSlot);
        PacketDistributor.sendToServer(new C2SRtsImportMenuSlotPayload(menuSlot));
        return true;
    }

    private static boolean canDragImportMenuSlot(AbstractContainerScreen<?> screen, int menuSlot) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!canImportMenuSlot(screen, menuSlot) || minecraft.player == null || screen == null || screen.getMenu() == null) {
            return false;
        }
        Slot slot = screen.getMenu().slots.get(menuSlot);
        return isPlayerInventorySlot(slot, minecraft.player);
    }

    private static boolean canImportMenuSlot(AbstractContainerScreen<?> screen, int menuSlot) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || screen == null || screen.getMenu() == null || menuSlot < 0 || menuSlot >= screen.getMenu().slots.size()) {
            return false;
        }
        Slot slot = screen.getMenu().slots.get(menuSlot);
        if (slot == null || !slot.hasItem() || !slot.mayPickup(minecraft.player)) {
            return false;
        }
        return !isPlayerInventorySlot(slot, minecraft.player) || isInventoryOrCraftingScreen(screen);
    }

    private static void endShiftImportDrag() {
        shiftImportDragging = false;
        shiftImportDragScreen = null;
        shiftImportDragSlots.clear();
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

        ItemStack request = entry.stack().copy();
        request.setCount(1);
        PacketDistributor.sendToServer(new C2SRtsLinkedQuickMovePayload(request));
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

    private static void renderInventoryRtsButtons(GuiGraphics g, Font font, Screen screen, double mouseX, double mouseY) {
        if (!ClientRtsController.get().isProgressionEnabled()) {
            return;
        }
        ButtonLayout progression = inventoryProgressionButton(screen);
        ButtonLayout home = inventoryHomeButton(screen);
        drawMiniButton(g, font, progression.x(), progression.y(), progression.w(), progression.h(),
                Component.translatable("screen.rtsbuilding.inventory.progression_button").getString());
        drawMiniButton(g, font, home.x(), home.y(), home.w(), home.h(),
                Component.translatable("screen.rtsbuilding.inventory.home_button").getString());
    }

    private static boolean handleInventoryRtsButtonClick(Screen screen, double mouseX, double mouseY) {
        if (!ClientRtsController.get().isProgressionEnabled()) {
            return false;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ButtonLayout progression = inventoryProgressionButton(screen);
        if (inside(mouseX, mouseY, progression.x(), progression.y(), progression.w(), progression.h())) {
            minecraft.setScreen(new RtsProgressionScreen(screen));
            return true;
        }
        ButtonLayout home = inventoryHomeButton(screen);
        if (inside(mouseX, mouseY, home.x(), home.y(), home.w(), home.h())) {
            minecraft.setScreen(new RtsHomeScreen(screen));
            return true;
        }
        return false;
    }

    private static ButtonLayout inventoryProgressionButton(Screen screen) {
        int totalW = INVENTORY_RTS_BUTTON_W * 2 + INVENTORY_RTS_BUTTON_GAP;
        int x = Math.max(4, (screen.width - totalW) / 2);
        int y = 4;
        return new ButtonLayout(x, y, INVENTORY_RTS_BUTTON_W, INVENTORY_RTS_BUTTON_H);
    }

    private static ButtonLayout inventoryHomeButton(Screen screen) {
        ButtonLayout progression = inventoryProgressionButton(screen);
        return new ButtonLayout(
                progression.x() + progression.w() + INVENTORY_RTS_BUTTON_GAP,
                progression.y(),
                INVENTORY_RTS_BUTTON_W,
                INVENTORY_RTS_BUTTON_H);
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }

    private static boolean isLeftMouseDown() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft != null
                && minecraft.getWindow() != null
                && GLFW.glfwGetMouseButton(minecraft.getWindow().getWindow(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
    }

    private record ButtonLayout(int x, int y, int w, int h) {
    }

    private static String compactCount(long value) {
        return RtsClientUiUtil.compactCount(value);
    }

    private static void drawSlotCountOverlay(GuiGraphics g, net.minecraft.client.gui.Font font, int slotX, int slotY,
            int slotSize, String countText, int color) {
        RtsClientUiUtil.drawSlotCountOverlay(g, font, slotX, slotY, slotSize, countText, color);
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
        for (int i = 0; i < QUICKBAR_SLOTS; i++) {
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

    private static String storageCountDetail(ClientRtsController controller, long count) {
        return Component.translatable(
                controller.isStorageLinked()
                        ? "screen.rtsbuilding.tooltip.count_storage"
                        : "screen.rtsbuilding.tooltip.count_inventory",
                compactCount(count)).getString();
    }

    private static void selectOverlayQuickbarSlot(int index) {
        if (index < 0 || index >= QUICKBAR_SLOTS) {
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
        ItemStack request = entry.stack().copy();
        request.setCount(1);
        PacketDistributor.sendToServer(new C2SRtsLinkedPickupPayload(request, wanted));
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
            int screenW,
            int screenH,
            int panelX,
            int panelY,
            int panelW,
            int panelH,
            boolean overlayCollapsed,
            boolean stackCraftBelow,
            int craftPanelX,
            int craftPanelY,
            int craftPanelW,
            int craftPanelH,
            boolean craftCollapsed,
            int storageRows,
            int storagePanelX,
            int storagePanelY,
            int storagePanelH,
            int headerY,
            int pageX,
            int pagePrevY,
            int pageTextY,
            int pageNextY,
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
            return this.storagePanelY + QUICKBAR_Y_OFF;
        }

        private int gridX() {
            return this.storagePanelX + 6;
        }

        private int gridY() {
            if (this.overlayCollapsed) {
                return this.storagePanelY + QUICKBAR_Y_OFF;
            }
            return this.storagePanelY + GRID_Y_OFF;
        }

        private int returnX() {
            return this.storagePanelX + 6;
        }

        private int shiftImportX() {
            return this.returnX() + RETURN_SLOTS * SLOT_PITCH + OVERLAY_BOTTOM_GAP;
        }

        private int shiftImportW() {
            int right = this.storagePanelX + STORAGE_PANEL_W - 6;
            return Math.max(48, right - this.shiftImportX());
        }

        private int controlsY() {
            if (this.overlayCollapsed) {
                return this.storagePanelY + collapsedControlsYOff();
            }
            return this.storagePanelY + GRID_Y_OFF + this.storageRows * SLOT_PITCH + 2;
        }

        private int returnY() {
            return this.controlsY() + OVERLAY_BOTTOM_BUTTON_H + 4;
        }

        private int closeX() {
            return this.storagePanelX + 6;
        }

        private int collapseX() {
            return this.closeX() + OVERLAY_CLOSE_W + OVERLAY_BOTTOM_GAP;
        }

        private int refreshX() {
            return this.collapseX() + OVERLAY_COLLAPSE_W + OVERLAY_BOTTOM_GAP;
        }

        private int infoX() {
            return this.refreshX() + OVERLAY_BOTTOM_SMALL_W + OVERLAY_BOTTOM_GAP;
        }
    }

    private record OverlayProfile(double guiScale, double renderScale, int storageRows, boolean stackCraftBelow) {
    }

    private record VisibleOverlayLayout(OverlayProfile profile, OverlayLayout layout) {
    }

    public record JeiOverlayIngredient(ItemStack stack, Rect2i area) {
    }

}
