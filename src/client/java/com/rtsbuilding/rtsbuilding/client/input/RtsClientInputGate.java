package com.rtsbuilding.rtsbuilding.client.input;


import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.input.event.RtsScreenEvent;
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
import com.rtsbuilding.rtsbuilding.uikit.animation.SystemUiClock;
import com.rtsbuilding.rtsbuilding.uikit.animation.UiBlink;
import com.rtsbuilding.rtsbuilding.uikit.theme.ContainerOverlayStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import org.lwjgl.glfw.GLFW;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.rtsbuilding.rtsbuilding.client.input.overlay.OverlayInputHandler.*;
import static com.rtsbuilding.rtsbuilding.client.input.overlay.OverlayInteraction.*;
import static com.rtsbuilding.rtsbuilding.client.input.overlay.OverlayLayoutHelper.*;
import static com.rtsbuilding.rtsbuilding.client.input.overlay.OverlayRenderer.*;

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

    public static boolean suppressVanillaInteractions() {
        return ClientRtsController.get().isEnabled();
    }

    public static boolean suppressVanillaHudElements() {
        return ClientRtsController.get().isEnabled();
    }

    public static boolean suppressHandRendering() {
        return ClientRtsController.get().isEnabled();
    }

    public static void onClientLoggingIn() {
        // 登录也主动清一次，覆盖崩服或异常断线时未完整收到退出事件的情况。
        RtsCullingClientState.resetForWorldChange();
    }

    public static void onClientLoggingOut() {
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

    public static void onScreenRenderPost(RtsScreenEvent.Render.Post event) {
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
            drawOverlayWindowFrame(g, minecraft.font, layout.craftPanelX(),
                    layout.craftPanelY(), layout.craftPanelW(), layout.craftPanelH());
            renderOverlayCraftablesPanel(g, minecraft.font, mouseX, mouseY, layout, controller);
        }

        drawOverlayWindowFrame(g, minecraft.font, layout.storagePanelX(),
                layout.storagePanelY(), STORAGE_PANEL_W, layout.storagePanelH());
        drawMiniButton(g, minecraft.font, layout.dragX(), layout.headerY(), OVERLAY_DRAG_W, OVERLAY_HEADER_H,
                Component.translatable("screen.rtsbuilding.overlay.drag_button").getString());
        drawMiniButton(g, minecraft.font, layout.sortX(), layout.headerY(), 12, OVERLAY_HEADER_H, sortShort(controller.getStorageSort()));
        drawMiniButton(g, minecraft.font, layout.dirX(), layout.headerY(), 12, OVERLAY_HEADER_H,
                controller.isStorageSortAscending() ? "A" : "D");

        drawPanelFrame(g, minecraft.font, layout.searchX(), layout.headerY(),
                layout.searchW(), OVERLAY_HEADER_H,
                ContainerOverlayStyle.searchBackground(overlaySearchFocused),
                ContainerOverlayStyle.SEARCH_BORDER_LIGHT,
                ContainerOverlayStyle.SEARCH_BORDER_DARK);

        String searchText = overlaySearchDraft == null ? "" : overlaySearchDraft;
        String display = trimToWidth(minecraft.font, searchText, Math.max(8, layout.searchW() - OVERLAY_SEARCH_CLEAR_W - 5));
        g.drawString(minecraft.font, display, layout.searchX() + 2,
                layout.headerY() + 2,
                ContainerOverlayStyle.SEARCH_TEXT.toArgb(), false);
        if (overlaySearchFocused && UiBlink.caretVisible(SystemUiClock.INSTANCE)) {
            int caretX = layout.searchX() + 2 + minecraft.font.width(display) + 1;
            g.fill(caretX, layout.headerY() + 2, caretX + 1,
                    layout.headerY() + OVERLAY_HEADER_H - 2,
                    ContainerOverlayStyle.SEARCH_TEXT.toArgb());
        }
        g.fill(layout.clearX(), layout.headerY(),
                layout.clearX() + OVERLAY_SEARCH_CLEAR_W,
                layout.headerY() + OVERLAY_HEADER_H,
                ContainerOverlayStyle.SEARCH_CLEAR_BACKGROUND.toArgb());
        RtsClientUiUtil.drawCenteredStringNoShadow(g, minecraft.font, "x",
                layout.clearX() + OVERLAY_SEARCH_CLEAR_W / 2, layout.headerY() + 2,
                (searchText.isEmpty()
                        ? ContainerOverlayStyle.SEARCH_CLEAR_EMPTY
                        : ContainerOverlayStyle.BUTTON_TEXT).toArgb());

        if (!layout.overlayCollapsed()) {
            g.fill(layout.pageX(), layout.pagePrevY(),
                    layout.pageX() + PAGE_BUTTON_W,
                    layout.pagePrevY() + PAGE_BUTTON_H,
                    ContainerOverlayStyle.PAGE_BACKGROUND.toArgb());
            RtsClientUiUtil.drawCenteredStringNoShadow(g, minecraft.font, "^",
                    layout.pageX() + PAGE_BUTTON_W / 2, layout.pagePrevY() + 1,
                    ContainerOverlayStyle.BUTTON_TEXT.toArgb());
            String pageText = (controller.getStoragePage() + 1) + "/" + controller.getStorageTotalPages();
            RtsClientUiUtil.drawCenteredStringNoShadow(g, minecraft.font, pageText,
                    layout.pageX() + PAGE_BUTTON_W / 2, layout.pageTextY(),
                    ContainerOverlayStyle.PAGE_TEXT.toArgb());
            g.fill(layout.pageX(), layout.pageNextY(),
                    layout.pageX() + PAGE_BUTTON_W,
                    layout.pageNextY() + PAGE_BUTTON_H,
                    ContainerOverlayStyle.PAGE_BACKGROUND.toArgb());
            RtsClientUiUtil.drawCenteredStringNoShadow(g, minecraft.font, "v",
                    layout.pageX() + PAGE_BUTTON_W / 2, layout.pageNextY() + 1,
                    ContainerOverlayStyle.BUTTON_TEXT.toArgb());
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
            g.fill(cx, cy, cx + SLOT_SIZE, cy + SLOT_SIZE,
                    ContainerOverlayStyle.STORAGE_SLOT.toArgb());
            if (i < maxSlots) {
                var entry = entries.get(i);
                g.renderItem(entry.stack(), cx + 1, cy + 1);
                drawSlotCountOverlay(g, minecraft.font, cx, cy, SLOT_SIZE,
                        RtsClientUiUtil.compactCount(entry.count()),
                        ContainerOverlayStyle.STORAGE_COUNT);
            }
        }

        pruneReturnQueue();
        if (!layout.overlayCollapsed()) {
            for (int i = 0; i < RETURN_SLOTS; i++) {
                int cx = layout.returnX() + i * SLOT_PITCH;
                int cy = layout.returnY();
                drawPanelFrame(g, minecraft.font, cx, cy, SLOT_SIZE, SLOT_SIZE,
                        ContainerOverlayStyle.RETURN_SLOT,
                        ContainerOverlayStyle.RETURN_BORDER_LIGHT,
                        ContainerOverlayStyle.RETURN_BORDER_DARK);

                ItemStack preview = RETURN_QUEUE[i];
                if (!preview.isEmpty()) {
                    g.renderItem(preview, cx + 1, cy + 1);
                    drawSlotCountOverlay(g, minecraft.font, cx, cy, SLOT_SIZE,
                            RtsClientUiUtil.compactCount(preview.getCount()),
                            ContainerOverlayStyle.RETURN_COUNT);
                } else {
                    g.drawString(minecraft.font, "+", cx + 6, cy + 5,
                            ContainerOverlayStyle.RETURN_EMPTY_TEXT.toArgb(), false);
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
                        ContainerOverlayStyle.TOOLTIP_COUNT.toArgb());
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
                            (entry.craftable()
                                    ? ContainerOverlayStyle.TOOLTIP_CRAFTABLE
                                    : ContainerOverlayStyle.TOOLTIP_MISSING).toArgb(),
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
                            ContainerOverlayStyle.TOOLTIP_COUNT.toArgb());
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

    public static void onScreenMousePressed(RtsScreenEvent.MouseButtonPressed.Pre event) {
        RtsClientPointerRouter.onScreenMousePressed(event);
    }

    public static void onScreenMouseDragged(RtsScreenEvent.MouseDragged.Pre event) {
        RtsClientPointerRouter.onScreenMouseDragged(event);
    }

    public static void onScreenMouseReleased(RtsScreenEvent.MouseButtonReleased.Pre event) {
        RtsClientPointerRouter.onScreenMouseReleased(event);
    }

    public static void onScreenMouseScrolled(RtsScreenEvent.MouseScrolled.Pre event) {
        RtsClientPointerRouter.onScreenMouseScrolled(event);
    }

    public static void onScreenKeyPressed(RtsScreenEvent.KeyPressed.Pre event) {
        RtsClientInputRouter.onScreenKeyPressed(event);
    }

    public static void onScreenCharTyped(RtsScreenEvent.CharacterTyped.Pre event) {
        RtsClientInputRouter.onScreenCharTyped(event);
    }

    public static void onScreenClosing(RtsScreenEvent.Closing event) {
        RtsClientInputRouter.onScreenClosing(event);
    }

}
