package com.rtsbuilding.rtsbuilding.client.input;


import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.input.overlay.OverlayInteraction;
import com.rtsbuilding.rtsbuilding.client.input.overlay.LegacyGuiGraphics;
import com.rtsbuilding.rtsbuilding.client.popup.RtsCraftFeedbackPopup;
import com.rtsbuilding.rtsbuilding.client.popup.RtsCraftQuantityDialog;
import com.rtsbuilding.rtsbuilding.client.record.CraftableEntry;
import com.rtsbuilding.rtsbuilding.client.record.StorageEntry;
import com.rtsbuilding.rtsbuilding.client.screen.culling.RtsCullingClientState;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.RtsCraftTerminalScreen;
import com.rtsbuilding.rtsbuilding.client.util.RtsClientUiUtil;
import com.rtsbuilding.rtsbuilding.common.persist.RtsClientUiStateStore;
import com.rtsbuilding.rtsbuilding.uikit.animation.SystemUiClock;
import com.rtsbuilding.rtsbuilding.uikit.animation.UiBlink;
import com.rtsbuilding.rtsbuilding.uikit.theme.ContainerOverlayStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import net.minecraftforge.fml.relauncher.Side;

import java.util.Arrays;
import java.util.HashSet;
import java.awt.Rectangle;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static com.rtsbuilding.rtsbuilding.client.input.overlay.OverlayInputHandler.*;
import static com.rtsbuilding.rtsbuilding.client.input.overlay.OverlayInteraction.*;
import static com.rtsbuilding.rtsbuilding.client.input.overlay.OverlayLayoutHelper.*;
import static com.rtsbuilding.rtsbuilding.client.input.overlay.OverlayRenderer.*;

@Mod.EventBusSubscriber(modid = RtsbuildingMod.MODID, value = Side.CLIENT)
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
    public static GuiScreen activeOverlayScreen;
    public static boolean overlayBootstrapRequested;
    public static boolean overlayDragging;
    public static double overlayDragOffsetX;
    public static double overlayDragOffsetY;
    public static boolean shiftImportDragging;
    public static GuiScreen shiftImportDragScreen;
    public static final Set<Integer> shiftImportDragSlots = new HashSet<>();
    public static GuiScreen pendingCraftRefillScreen;
    public static int pendingCraftRefillButton = -1;
    public static List<ItemStack> pendingCraftRefillBlueprint = java.util.Collections.emptyList();
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
    public static void onInteractionMouse(MouseEvent event) {
        if (Minecraft.getMinecraft().currentScreen == null
                && ClientRtsController.get().isEnabled()
                && (event.getButton() == 0 || event.getButton() == 1)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRenderGuiLayer(RenderGameOverlayEvent.Pre event) {
        if (!ClientRtsController.get().isEnabled()) {
            return;
        }

        if (event.getType() == RenderGameOverlayEvent.ElementType.CROSSHAIRS
                || event.getType() == RenderGameOverlayEvent.ElementType.HOTBAR) {
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
    public static void onClientLoggingIn(FMLNetworkEvent.ClientConnectedToServerEvent event) {
        // 登录也主动清一次，覆盖崩服或异常断线时未完整收到退出事件的情况。
        RtsCullingClientState.resetForWorldChange();
    }

    @SubscribeEvent
    public static void onClientLoggingOut(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        overlayBootstrapRequested = false;
        activeOverlayScreen = null;
        RtsCullingClientState.resetForWorldChange();
        // Clear stale workflow data so it does not linger in the UI
        // when the player joins a different world (save).
        ClientRtsController.get().clearWorkflowData();
    }

    public static List<Rectangle> getJeiOverlayExtraAreas(GuiScreen screen) {
        VisibleOverlayLayout visible = resolveVisibleOverlayLayout(screen);
        if (visible == null) {
            return Collections.emptyList();
        }
        return Collections.singletonList(toGuiRect(
                visible.layout().panelX(),
                visible.layout().panelY(),
                visible.layout().panelW(),
                visible.layout().panelH(),
                visible.profile().renderScale()));
    }

    public static JeiOverlayIngredient getJeiOverlayIngredientUnderMouse(double mouseX, double mouseY) {
        Minecraft minecraft = Minecraft.getMinecraft();
        VisibleOverlayLayout visible = resolveVisibleOverlayLayout(minecraft == null ? null : minecraft.currentScreen);
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
    public static void onScreenRenderPost(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (event.getGui() instanceof BuilderScreen) {
            return;
        }
        if (event.getGui() instanceof RtsCraftTerminalScreen) {
            return;
        }
        if (!(event.getGui() instanceof GuiContainer)) {
            return;
        }

        Minecraft minecraft = Minecraft.getMinecraft();
        ScaledResolution resolution = new ScaledResolution(minecraft);
        LegacyGuiGraphics g = new LegacyGuiGraphics(minecraft, resolution.getScaledWidth(), resolution.getScaledHeight());
        if (event.getGui() instanceof GuiInventory) {
            renderInventoryRtsButtons(minecraft.fontRenderer, event.getGui(), event.getMouseX(), event.getMouseY());
        }
        if (!RtsClientUiStateStore.isContainerOverlayEnabled()) {
            clearOverlaySearchFocus();
            OVERLAY_CRAFT_DIALOG.close();
            return;
        }

        ClientRtsController controller = ClientRtsController.get();
        if (!controller.canUseStorageOverlay()) {
            requestOverlayBootstrap(event.getGui(), controller);
            return;
        }
        syncOverlayScreen(event.getGui(), controller);

        OverlayProfile profile = overlayProfile();
        double mouseX = toOverlayMouse(event.getMouseX(), profile);
        double mouseY = toOverlayMouse(event.getMouseY(), profile);
        OverlayLayout layout = resolveOverlayLayout(profile);
        syncOverlaySearchDrafts(controller);
        syncOverlayCraftables(controller);

        g.pushPose();
        g.scale((float) profile.renderScale(), (float) profile.renderScale(), 1.0F);

        if (!layout.overlayCollapsed()) {
            drawOverlayWindowFrame(g, minecraft.fontRenderer, layout.craftPanelX(),
                    layout.craftPanelY(), layout.craftPanelW(), layout.craftPanelH());
            renderOverlayCraftablesPanel(g, minecraft.fontRenderer, mouseX, mouseY, layout, controller);
        }

        drawOverlayWindowFrame(g, minecraft.fontRenderer, layout.storagePanelX(),
                layout.storagePanelY(), STORAGE_PANEL_W, layout.storagePanelH());
        drawMiniButton(g, minecraft.fontRenderer, layout.dragX(), layout.headerY(), OVERLAY_DRAG_W, OVERLAY_HEADER_H,
                I18n.format("screen.rtsbuilding.overlay.drag_button"));
        drawMiniButton(g, minecraft.fontRenderer, layout.sortX(), layout.headerY(), 12, OVERLAY_HEADER_H, sortShort(controller.getStorageSort()));
        drawMiniButton(g, minecraft.fontRenderer, layout.dirX(), layout.headerY(), 12, OVERLAY_HEADER_H,
                controller.isStorageSortAscending() ? "A" : "D");

        drawPanelFrame(g, minecraft.fontRenderer, layout.searchX(), layout.headerY(),
                layout.searchW(), OVERLAY_HEADER_H,
                ContainerOverlayStyle.searchBackground(overlaySearchFocused),
                ContainerOverlayStyle.SEARCH_BORDER_LIGHT,
                ContainerOverlayStyle.SEARCH_BORDER_DARK);

        String searchText = overlaySearchDraft == null ? "" : overlaySearchDraft;
        String display = trimToWidth(minecraft.fontRenderer, searchText, Math.max(8, layout.searchW() - OVERLAY_SEARCH_CLEAR_W - 5));
        g.drawString(minecraft.fontRenderer, display, layout.searchX() + 2,
                layout.headerY() + 2,
                ContainerOverlayStyle.SEARCH_TEXT.toArgb(), false);
        if (overlaySearchFocused && UiBlink.caretVisible(SystemUiClock.INSTANCE)) {
            int caretX = layout.searchX() + 2 + minecraft.fontRenderer.getStringWidth(display) + 1;
            g.fill(caretX, layout.headerY() + 2, caretX + 1,
                    layout.headerY() + OVERLAY_HEADER_H - 2,
                    ContainerOverlayStyle.SEARCH_TEXT.toArgb());
        }
        g.fill(layout.clearX(), layout.headerY(),
                layout.clearX() + OVERLAY_SEARCH_CLEAR_W,
                layout.headerY() + OVERLAY_HEADER_H,
                ContainerOverlayStyle.SEARCH_CLEAR_BACKGROUND.toArgb());
        g.drawCenteredString(minecraft.fontRenderer, "x",
                layout.clearX() + OVERLAY_SEARCH_CLEAR_W / 2, layout.headerY() + 2,
                (searchText.isEmpty()
                        ? ContainerOverlayStyle.SEARCH_CLEAR_EMPTY
                        : ContainerOverlayStyle.BUTTON_TEXT).toArgb());

        if (!layout.overlayCollapsed()) {
            g.fill(layout.pageX(), layout.pagePrevY(),
                    layout.pageX() + PAGE_BUTTON_W,
                    layout.pagePrevY() + PAGE_BUTTON_H,
                    ContainerOverlayStyle.PAGE_BACKGROUND.toArgb());
            g.drawCenteredString(minecraft.fontRenderer, "^",
                    layout.pageX() + PAGE_BUTTON_W / 2, layout.pagePrevY() + 1,
                    ContainerOverlayStyle.BUTTON_TEXT.toArgb());
            String pageText = (controller.getStoragePage() + 1) + "/" + controller.getStorageTotalPages();
            g.drawCenteredString(minecraft.fontRenderer, pageText,
                    layout.pageX() + PAGE_BUTTON_W / 2, layout.pageTextY(),
                    ContainerOverlayStyle.PAGE_TEXT.toArgb());
            g.fill(layout.pageX(), layout.pageNextY(),
                    layout.pageX() + PAGE_BUTTON_W,
                    layout.pageNextY() + PAGE_BUTTON_H,
                    ContainerOverlayStyle.PAGE_BACKGROUND.toArgb());
            g.drawCenteredString(minecraft.fontRenderer, "v",
                    layout.pageX() + PAGE_BUTTON_W / 2, layout.pageNextY() + 1,
                    ContainerOverlayStyle.BUTTON_TEXT.toArgb());
        }

        if (!layout.overlayCollapsed()) {
            renderQuickbar(g, minecraft.fontRenderer, layout.quickbarX(), layout.quickbarY());
        }

        List<StorageEntry> entries = controller.getStorageEntries();
        int visibleStorageRows = layout.overlayCollapsed() ? 1 : layout.storageRows();
        int visibleStorageSlots = STORAGE_COLS * visibleStorageRows;
        int maxSlots = Math.min(entries.size(), visibleStorageSlots);
        for (int i = 0; i < visibleStorageSlots; i++) {
            int cx = layout.gridX() + (i % STORAGE_COLS) * SLOT_PITCH;
            int cy = layout.gridY() + (i / STORAGE_COLS) * SLOT_PITCH;
            g.fill(cx, cy, cx + SLOT_SIZE, cy + SLOT_SIZE,
                    ContainerOverlayStyle.STORAGE_SLOT.toArgb());
            if (i < maxSlots) {
                StorageEntry entry = entries.get(i);
                g.renderItem(entry.stack(), cx + 1, cy + 1);
                drawSlotCountOverlay(g, minecraft.fontRenderer, cx, cy, SLOT_SIZE,
                        RtsClientUiUtil.compactCount(entry.count()),
                        ContainerOverlayStyle.STORAGE_COUNT);
            }
        }

        pruneReturnQueue();
        if (!layout.overlayCollapsed()) {
            for (int i = 0; i < RETURN_SLOTS; i++) {
                int cx = layout.returnX() + i * SLOT_PITCH;
                int cy = layout.returnY();
                drawPanelFrame(g, minecraft.fontRenderer, cx, cy, SLOT_SIZE, SLOT_SIZE,
                        ContainerOverlayStyle.RETURN_SLOT,
                        ContainerOverlayStyle.RETURN_BORDER_LIGHT,
                        ContainerOverlayStyle.RETURN_BORDER_DARK);

                ItemStack preview = RETURN_QUEUE[i];
                if (!preview.isEmpty()) {
                    g.renderItem(preview, cx + 1, cy + 1);
                    drawSlotCountOverlay(g, minecraft.fontRenderer, cx, cy, SLOT_SIZE,
                            RtsClientUiUtil.compactCount(preview.getCount()),
                            ContainerOverlayStyle.RETURN_COUNT);
                } else {
                    g.drawString(minecraft.fontRenderer, "+", cx + 6, cy + 5,
                            ContainerOverlayStyle.RETURN_EMPTY_TEXT.toArgb(), false);
                }
            }
        }
        renderOverlayBottomControls(g, minecraft.fontRenderer, layout);
        renderOverlayRefreshButton(g, minecraft.fontRenderer, layout, mouseX, mouseY, controller);
        renderOverlayInfoButton(g, minecraft.fontRenderer, layout, mouseX, mouseY);
        if (!layout.overlayCollapsed()) {
            renderOverlayShiftImportButton(g, minecraft.fontRenderer, layout, mouseX, mouseY);
        }

        if (!OVERLAY_CRAFT_DIALOG.isOpen()) {
            int hoveredStorage = resolveOverlaySlotIndex(mouseX, mouseY, layout.gridX(), layout.gridY(), visibleStorageRows);
            if (hoveredStorage >= 0 && hoveredStorage < maxSlots) {
                StorageEntry entry = entries.get(hoveredStorage);
                g.renderTooltip(entry.stack(), (int) mouseX, (int) mouseY);
                g.drawString(
                        minecraft.fontRenderer,
                        storageCountDetail(controller, entry.count()),
                        (int) mouseX + 10,
                        (int) mouseY + 18,
                        ContainerOverlayStyle.TOOLTIP_COUNT.toArgb());
            }

            int hoveredCraft = resolveOverlayCraftableEntryIndex(mouseX, mouseY, layout);
            if (hoveredCraft >= 0 && hoveredCraft < controller.getCraftableEntries().size()) {
                CraftableEntry entry = controller.getCraftableEntries().get(hoveredCraft);
                g.renderTooltip(entry.stack(), (int) mouseX, (int) mouseY);
                String detail = entry.craftable()
                        ? "Right click: choose recipe/count"
                        : entry.missingSummary();
                if (detail != null && !detail.trim().isEmpty()) {
                    g.drawString(minecraft.fontRenderer,
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
                    g.renderTooltip(preview, (int) mouseX, (int) mouseY);
                    g.drawString(minecraft.fontRenderer,
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
                    g.renderTooltip(preview, (int) mouseX, (int) mouseY);
                }
            }
        }
        if (overlayInfoOpen) {
            renderOverlayInfoPanel(g, minecraft.fontRenderer, layout);
        }

        g.popPose();

        if (OVERLAY_CRAFT_DIALOG.isOpen()) {
            OVERLAY_CRAFT_DIALOG.render(
                    g,
                    minecraft.fontRenderer,
                    resolution.getScaledWidth(),
                    resolution.getScaledHeight(),
                    (int) event.getMouseX(),
                    (int) event.getMouseY());
        }
        RtsCraftFeedbackPopup.render(
                g,
                minecraft.fontRenderer,
                resolution.getScaledWidth(),
                controller);

    }

}
