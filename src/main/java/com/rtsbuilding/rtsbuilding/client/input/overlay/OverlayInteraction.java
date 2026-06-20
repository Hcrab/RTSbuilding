package com.rtsbuilding.rtsbuilding.client.input.overlay;

import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.RtsHomeScreen;
import com.rtsbuilding.rtsbuilding.client.util.RtsClientUiUtil;
import com.rtsbuilding.rtsbuilding.client.util.RtsCraftablesUiHelper;
import com.rtsbuilding.rtsbuilding.network.craft.C2SRtsCraftRefillPayload;
import com.rtsbuilding.rtsbuilding.network.storage.C2SRtsImportMenuSlotPayload;
import com.rtsbuilding.rtsbuilding.network.storage.C2SRtsLinkedPickupPayload;
import com.rtsbuilding.rtsbuilding.network.storage.C2SRtsLinkedQuickMovePayload;
import com.rtsbuilding.rtsbuilding.network.storage.C2SRtsReturnCarriedPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import com.rtsbuilding.rtsbuilding.forgecompat.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

import static com.rtsbuilding.rtsbuilding.client.input.RtsClientInputGate.*;
import static com.rtsbuilding.rtsbuilding.client.input.overlay.OverlayLayoutHelper.*;

public final class OverlayInteraction {
    private OverlayInteraction() {
    }

    // =========================================================================
    //  Slot index resolution
    // =========================================================================

    public static int resolveOverlaySlotIndex(double mouseX, double mouseY, int gridX, int gridY) {
        return resolveOverlaySlotIndex(mouseX, mouseY, gridX, gridY, overlayProfile().storageRows());
    }

    public static int resolveOverlaySlotIndex(double mouseX, double mouseY, int gridX, int gridY, int storageRows) {
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

    public static int resolveQuickbarSlotIndex(double mouseX, double mouseY, int x, int y) {
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

    public static int resolveReturnSlotIndex(double mouseX, double mouseY, int x, int y) {
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

    public static int resolveOverlayCraftableEntryIndex(double mouseX, double mouseY, OverlayLayoutHelper.OverlayLayout layout) {
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

    public static int maxOverlayCraftScroll(ClientRtsController controller, int visibleRows) {
        int totalRows = Math.max(1, (int) Math.ceil(controller.getCraftableEntries().size() / (double) CRAFT_COLS));
        return Math.max(0, totalRows - visibleRows);
    }

    // =========================================================================
    //  Pickup & deposit
    // =========================================================================

    public static void selectOverlayQuickbarSlot(int index) {
        if (index < 0 || index >= QUICKBAR_SLOTS) {
            return;
        }
        ClientRtsController.get().selectQuickSlot(index);
    }

    public static boolean tryPickupFromOverlay(int index, int requestedAmount) {
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

    public static boolean tryDepositCarriedToLinked(int requestedAmount) {
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

    public static int requestedFromCarried(ItemStack carried, ItemStack target, int requestedAmount) {
        int requested = requestedAmount <= 0 ? 1 : requestedAmount;
        if (carried.isEmpty()) {
            return Math.min(requested, target.getMaxStackSize());
        }
        if (!ItemStack.isSameItemSameTags(carried, target)) {
            return 0;
        }
        return Math.min(requested, carried.getMaxStackSize() - carried.getCount());
    }

    public static void applyLocalCarriedPreview(ItemStack pickedPrototype, int requested) {
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
        if (!ItemStack.isSameItemSameTags(carried, pickedPrototype)) {
            return;
        }
        int grow = Math.min(wanted, carried.getMaxStackSize() - carried.getCount());
        if (grow <= 0) {
            return;
        }
        carried.grow(grow);
        minecraft.player.containerMenu.setCarried(carried);
    }

    // =========================================================================
    //  Menu slot import & shift drag
    // =========================================================================

    public static int resolveHoveredMenuSlot(AbstractContainerScreen<?> screen, double mouseX, double mouseY) {
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

    public static boolean tryImportHoveredMenuSlot(AbstractContainerScreen<?> screen, double mouseX, double mouseY, int button) {
        int menuSlot = resolveHoveredMenuSlot(screen, mouseX, mouseY);
        if (menuSlot < 0) {
            return false;
        }
        if (menuSlot == 0 && button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return true;
        }
        if (!canImportMenuSlot(screen, menuSlot)) {
            return false;
        }
        PacketDistributor.sendToServer(new C2SRtsImportMenuSlotPayload(menuSlot));
        return true;
    }

    public static boolean tryStartShiftImportDrag(AbstractContainerScreen<?> screen, double mouseX, double mouseY) {
        int menuSlot = resolveHoveredMenuSlot(screen, mouseX, mouseY);
        if (!canDragImportMenuSlot(screen, menuSlot)) {
            return false;
        }
        shiftImportDragging = true;
        shiftImportDragScreen = screen;
        shiftImportDragSlots.clear();
        return trySendShiftImportDragSlot(screen, menuSlot);
    }

    public static boolean tryContinueShiftImportDrag(AbstractContainerScreen<?> screen, double mouseX, double mouseY) {
        int menuSlot = resolveHoveredMenuSlot(screen, mouseX, mouseY);
        return trySendShiftImportDragSlot(screen, menuSlot);
    }

    public static boolean trySendShiftImportDragSlot(AbstractContainerScreen<?> screen, int menuSlot) {
        if (shiftImportDragSlots.contains(menuSlot) || !canDragImportMenuSlot(screen, menuSlot)) {
            return false;
        }
        shiftImportDragSlots.add(menuSlot);
        PacketDistributor.sendToServer(new C2SRtsImportMenuSlotPayload(menuSlot));
        return true;
    }

    public static boolean canDragImportMenuSlot(AbstractContainerScreen<?> screen, int menuSlot) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!canImportMenuSlot(screen, menuSlot) || minecraft.player == null || screen == null || screen.getMenu() == null) {
            return false;
        }
        Slot slot = screen.getMenu().slots.get(menuSlot);
        return isPlayerInventorySlot(slot, minecraft.player);
    }

    public static boolean canImportMenuSlot(AbstractContainerScreen<?> screen, int menuSlot) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || screen == null || screen.getMenu() == null
                || menuSlot < 0 || menuSlot >= screen.getMenu().slots.size()) {
            return false;
        }
        Slot slot = screen.getMenu().slots.get(menuSlot);
        if (slot == null || !slot.hasItem() || !slot.mayPickup(minecraft.player)) {
            return false;
        }
        return !isPlayerInventorySlot(slot, minecraft.player) || isInventoryOrCraftingScreen(screen);
    }

    public static void endShiftImportDrag() {
        shiftImportDragging = false;
        shiftImportDragScreen = null;
        shiftImportDragSlots.clear();
    }

    public static boolean isPlayerInventorySlot(Slot slot, net.minecraft.world.entity.player.Player player) {
        return slot != null && player != null && slot.container == player.getInventory();
    }

    public static boolean isInventoryOrCraftingScreen(Screen screen) {
        return screen instanceof InventoryScreen || screen instanceof CraftingScreen;
    }

    // =========================================================================
    //  Quick-move overlay entry
    // =========================================================================

    public static boolean tryQuickMoveOverlayEntry(AbstractContainerScreen<?> screen, double mouseX, double mouseY) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !minecraft.player.containerMenu.getCarried().isEmpty()) {
            return false;
        }
        OverlayLayoutHelper.OverlayLayout layout = OverlayLayoutHelper.resolveOverlayLayout(screen);
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

    // =========================================================================
    //  Craft refill
    // =========================================================================

    public static void capturePendingCraftRefill(AbstractContainerScreen<?> screen, double mouseX, double mouseY, int button) {
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
        List<ItemStack> blueprint = new ArrayList<>(9);
        for (int i = 0; i < 9; i++) {
            Slot slot = menu.getSlot(1 + i);
            ItemStack stack = slot == null ? ItemStack.EMPTY : slot.getItem();
            blueprint.add(stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1));
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

    public static void trySendPendingCraftRefill(Screen screen, int button) {
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

    public static void clearPendingCraftRefill() {
        pendingCraftRefillScreen = null;
        pendingCraftRefillButton = -1;
        pendingCraftRefillBlueprint = List.of();
        pendingCraftResultItemId = "";
        pendingCraftResultCount = 0;
    }

    // =========================================================================
    //  Return queue
    // =========================================================================

    public static void enqueueReturnPreview(ItemStack stack) {
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

    public static void pruneReturnQueue() {
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

    // =========================================================================
    //  Inventory RTS buttons
    // =========================================================================

    public static void renderInventoryRtsButtons(net.minecraft.client.gui.GuiGraphics g, net.minecraft.client.gui.Font font, Screen screen, double mouseX, double mouseY) {
        if (!ClientRtsController.get().isProgressionEnabled()) {
            return;
        }
        OverlayLayoutHelper.ButtonLayout home = inventoryHomeButton(screen);
        drawMiniButton(g, font, home.x(), home.y(), home.w(), home.h(),
                Component.translatable("screen.rtsbuilding.inventory.home_button").getString());
    }

    public static boolean handleInventoryRtsButtonClick(Screen screen, double mouseX, double mouseY) {
        if (!ClientRtsController.get().isProgressionEnabled()) {
            return false;
        }
        Minecraft minecraft = Minecraft.getInstance();
        OverlayLayoutHelper.ButtonLayout home = inventoryHomeButton(screen);
        if (inside(mouseX, mouseY, home.x(), home.y(), home.w(), home.h())) {
            minecraft.setScreen(new RtsHomeScreen(screen));
            return true;
        }
        return false;
    }

    public static OverlayLayoutHelper.ButtonLayout inventoryHomeButton(Screen screen) {
        int x = Math.max(4, (screen.width - INVENTORY_RTS_BUTTON_W) / 2);
        int y = 4;
        return new OverlayLayoutHelper.ButtonLayout(x, y, INVENTORY_RTS_BUTTON_W, INVENTORY_RTS_BUTTON_H);
    }

    // =========================================================================
    //  Craft search
    // =========================================================================

    public static void submitOverlayCraftDialogIfReady() {
        if (OVERLAY_CRAFT_DIALOG != null) {
            RtsCraftablesUiHelper.submitPendingCraftRequest(OVERLAY_CRAFT_DIALOG, ClientRtsController.get());
        }
    }

    public static boolean handleOverlayCraftLeftClick(double mouseX, double mouseY, OverlayLayoutHelper.OverlayLayout layout) {
        if (!inside(mouseX, mouseY, layout.craftPanelX(), layout.craftPanelY(), layout.craftPanelW(), layout.craftPanelH())) {
            return false;
        }
        OverlayInputHandler.clearOverlaySearchFocus();
        if (inside(mouseX, mouseY, layout.craftPanelX() + 3, layout.craftPanelY() + 2,
                Math.max(36, layout.craftPanelW() - 6), OVERLAY_HEADER_H + 2)) {
            overlayCraftCollapsed = !overlayCraftCollapsed;
            OverlayInputHandler.clearOverlaySearchFocus();
            return true;
        }
        if (layout.craftCollapsed()) {
            OverlayInputHandler.clearOverlaySearchFocus();
            return true;
        }
        if (inside(mouseX, mouseY, layout.craftSearchX(), layout.craftSearchY(), layout.craftSearchW(), CRAFT_SEARCH_H)) {
            OverlayInputHandler.setOverlayCraftSearchFocused(true);
            return true;
        }
        if (inside(mouseX, mouseY, layout.craftApplyX(), layout.craftSearchY(), CRAFT_APPLY_W, CRAFT_SEARCH_H)) {
            applyOverlayCraftSearch();
            OverlayInputHandler.clearOverlaySearchFocus();
            return true;
        }
        if (inside(mouseX, mouseY, layout.craftToggleX(), layout.craftSearchY(), CRAFT_TOGGLE_W, CRAFT_SEARCH_H)) {
            OverlayInputHandler.clearOverlaySearchFocus();
            ClientRtsController.get().toggleCraftablesShowUnavailable();
            return true;
        }
        OverlayInputHandler.clearOverlaySearchFocus();
        return true;
    }

    public static boolean handleOverlayCraftRightClick(double mouseX, double mouseY, OverlayLayoutHelper.OverlayLayout layout) {
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
        var entry = ClientRtsController.get().getCraftableEntries().get(index);
        if (!entry.craftable()) {
            return true;
        }
        OverlayInputHandler.clearOverlaySearchFocus();
        RtsCraftablesUiHelper.openCraftQuantityDialog(OVERLAY_CRAFT_DIALOG, entry);
        return true;
    }

    public static void applyOverlayCraftSearch() {
        overlayCraftSearchDraft = normalizeOverlayCraftSearchDraft(overlayCraftSearchDraft);
        overlayCraftScroll = 0;
        ClientRtsController.get().setCraftablesSearch(overlayCraftSearchDraft);
    }

    public static boolean hasPendingOverlayCraftSearch() {
        return !normalizeOverlayCraftSearchDraft(overlayCraftSearchDraft)
                .equals(normalizeOverlayCraftSearchDraft(ClientRtsController.get().getCraftablesSearch()));
    }

    public static String normalizeOverlayCraftSearchDraft(String value) {
        return RtsCraftablesUiHelper.normalizeSearchDraft(value);
    }

    // =========================================================================
    //  Utility
    // =========================================================================

    public static long resolvePinnedItemCount(String itemId) {
        return ClientRtsController.get().getStorageTotalCount(itemId);
    }

    public static String storageCountDetail(ClientRtsController controller, long count) {
        return net.minecraft.network.chat.Component.translatable(
                controller.isStorageLinked()
                        ? "screen.rtsbuilding.tooltip.count_storage"
                        : "screen.rtsbuilding.tooltip.count_inventory",
                RtsClientUiUtil.compactCount(count)).getString();
    }

    public static boolean isLeftMouseDown() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft != null
                && minecraft.getWindow() != null
                && org.lwjgl.glfw.GLFW.glfwGetMouseButton(minecraft.getWindow().getWindow(), org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
    }
}
