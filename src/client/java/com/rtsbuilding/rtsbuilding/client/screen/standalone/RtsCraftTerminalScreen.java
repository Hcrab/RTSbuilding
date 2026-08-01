package com.rtsbuilding.rtsbuilding.client.screen.standalone;


import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.record.StorageEntry;
import com.rtsbuilding.rtsbuilding.client.screen.canvas.MinecraftUiCanvas;
import com.rtsbuilding.rtsbuilding.client.util.RtsClientUiUtil;
import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uikit.canvas.UiChromeRenderer;
import com.rtsbuilding.rtsbuilding.uikit.theme.CraftTerminalStyle;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiColor;
import com.rtsbuilding.rtsbuilding.network.storage.C2SRtsImportMenuSlotPayload;
import com.rtsbuilding.rtsbuilding.network.storage.C2SRtsLinkedPickupPayload;
import com.rtsbuilding.rtsbuilding.network.storage.C2SRtsReturnCarriedPayload;
import com.rtsbuilding.rtsbuilding.network.storage.RtsStorageSort;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public final class RtsCraftTerminalScreen extends AbstractContainerScreen<CraftingMenu> {
    private static final ResourceLocation VANILLA_CRAFTING_BG =
            ResourceLocation.withDefaultNamespace("textures/gui/container/crafting_table.png");
    private static final int VANILLA_BG_W = 176;
    private static final int LINK_PANEL_X_OFF = VANILLA_BG_W + 6;
    private static final int LINK_PANEL_Y_OFF = 4;
    private static final int LINK_PANEL_W = 166;
    private static final int LINK_PANEL_H = 158;
    private static final int LINK_SEARCH_X_OFF = 8;
    private static final int LINK_SEARCH_Y_OFF = 19;
    private static final int LINK_SEARCH_H = 12;
    private static final int LINK_SEARCH_CLEAR_W = 10;
    private static final int LINK_GRID_X_OFF = 8;
    private static final int LINK_GRID_Y_OFF = 35;
    private static final int LINK_COLS = 8;
    private static final int LINK_ROWS = 5;
    private static final int LINK_SLOT_PITCH = 20;
    private static final int LINK_SLOT_SIZE = 18;
    private static final int LINK_GRID_W = LINK_COLS * LINK_SLOT_PITCH;
    private static final int MINI_BUTTON_W = 12;
    private static final int MINI_BUTTON_H = 11;
    private static final int SORT_BUTTON_X_OFF = 40;
    private static final int DIR_BUTTON_X_OFF = SORT_BUTTON_X_OFF + 14;
    private static final int PAGE_PREV_X_OFF = LINK_PANEL_W - 40;
    private static final int PAGE_NEXT_X_OFF = PAGE_PREV_X_OFF + 28;
    private static final int BUTTON_ROW_Y_OFF = 7;
    private static final int CARRIED_IMPORT_W = 48;
    private static final int CARRIED_IMPORT_H = 12;
    private static final int CARRIED_IMPORT_X_OFF = LINK_PANEL_W - CARRIED_IMPORT_W - 8;
    private static final int CARRIED_IMPORT_Y_OFF = LINK_PANEL_H - CARRIED_IMPORT_H - 7;

    private EditBox searchBox;

    public RtsCraftTerminalScreen(CraftingMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = VANILLA_BG_W + LINK_PANEL_W + 12;
        this.imageHeight = 166;
        this.inventoryLabelY = this.imageHeight - 90;
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX = 28;
        this.titleLabelY = 6;

        int panelX = this.leftPos + LINK_PANEL_X_OFF;
        int panelY = this.topPos + LINK_PANEL_Y_OFF;
        int searchX = panelX + LINK_SEARCH_X_OFF + 2;
        int searchY = panelY + LINK_SEARCH_Y_OFF + 2;
        int searchW = LINK_GRID_W - LINK_SEARCH_CLEAR_W - 4;
        this.searchBox = new EditBox(this.font, searchX, searchY, searchW, 8, Component.literal("Search"));
        this.searchBox.setBordered(false);
        this.searchBox.setCanLoseFocus(true);
        this.searchBox.setTextColor(CraftTerminalStyle.TEXT.toArgb());
        this.searchBox.setTextColorUneditable(CraftTerminalStyle.UNEDITABLE_TEXT.toArgb());
        ClientRtsController.get().setStorageSearch("");
        this.searchBox.setValue("");
        this.searchBox.setResponder(this::onSearchChanged);
        this.addRenderableWidget(this.searchBox);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        syncSearchValueFromController();
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderCraftResultFallback(guiGraphics);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int left = this.leftPos;
        int top = this.topPos;

        guiGraphics.blit(VANILLA_CRAFTING_BG, left, top, 0, 0, VANILLA_BG_W, this.imageHeight);
        guiGraphics.fill(left + 3, top + 3, left + VANILLA_BG_W - 3, top + 15,
                CraftTerminalStyle.VANILLA_TITLE_BACKGROUND.toArgb());
        guiGraphics.hLine(left + 3, left + VANILLA_BG_W - 3, top + 15,
                CraftTerminalStyle.VANILLA_TITLE_DIVIDER.toArgb());
        drawPanelFrame(guiGraphics, left + 27, top + 14, 58, 58,
                CraftTerminalStyle.CRAFT_GRID_BACKGROUND,
                CraftTerminalStyle.CRAFT_GRID_BORDER_LIGHT,
                CraftTerminalStyle.CRAFT_GRID_BORDER_DARK);
        drawPanelFrame(guiGraphics, left + 124, top + 33, 18, 18,
                CraftTerminalStyle.RESULT_BACKGROUND,
                CraftTerminalStyle.RESULT_BORDER_LIGHT,
                CraftTerminalStyle.RESULT_BORDER_DARK);
        drawPanelFrame(guiGraphics, left + 7, top + 82, 162, 76,
                CraftTerminalStyle.INVENTORY_BACKGROUND,
                CraftTerminalStyle.INVENTORY_BORDER_LIGHT,
                CraftTerminalStyle.INVENTORY_BORDER_DARK);

        renderLinkedPanel(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(this.font, "RTS Craft Terminal", this.titleLabelX, this.titleLabelY,
                CraftTerminalStyle.TEXT.toArgb(), false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if ((button == GLFW.GLFW_MOUSE_BUTTON_LEFT || button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) && Screen.hasShiftDown()) {
            Slot hovered = this.hoveredSlot;
            if (hovered != null && hovered.hasItem()) {
                int menuSlot = this.menu.slots.indexOf(hovered);
                if (menuSlot >= 0) {
                    if (menuSlot == 0 && button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                        return true;
                    }
                    ClientPlayNetworking.send(new C2SRtsImportMenuSlotPayload(menuSlot));
                    return true;
                }
            }
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT || button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            if (handleLinkedPanelClick(mouseX, mouseY, button)) {
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int panelX = this.leftPos + LINK_PANEL_X_OFF;
        int panelY = this.topPos + LINK_PANEL_Y_OFF;
        if (UiRect.contains(panelX, panelY, LINK_PANEL_W, LINK_PANEL_H, mouseX, mouseY)) {
            if (scrollY > 0.0D) {
                ClientRtsController.get().prevPage();
                return true;
            }
            if (scrollY < 0.0D) {
                ClientRtsController.get().nextPage();
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.searchBox != null && this.searchBox.isFocused()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                this.searchBox.setValue("");
                this.searchBox.setFocused(false);
                this.setFocused(null);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                this.searchBox.setFocused(false);
                this.setFocused(null);
                return true;
            }
            this.searchBox.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (this.searchBox != null && this.searchBox.isFocused()) {
            this.searchBox.charTyped(codePoint, modifiers);
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    private void renderLinkedPanel(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        ClientRtsController controller = ClientRtsController.get();

        int panelX = this.leftPos + LINK_PANEL_X_OFF;
        int panelY = this.topPos + LINK_PANEL_Y_OFF;
        drawPanelFrame(guiGraphics, panelX, panelY, LINK_PANEL_W, LINK_PANEL_H,
                CraftTerminalStyle.LINK_BACKGROUND, CraftTerminalStyle.LINK_BORDER_LIGHT,
                CraftTerminalStyle.LINK_BORDER_DARK);
        guiGraphics.fill(panelX + 1, panelY + 1, panelX + LINK_PANEL_W - 1, panelY + 16,
                CraftTerminalStyle.LINK_TITLE_BACKGROUND.toArgb());
        guiGraphics.hLine(panelX + 1, panelX + LINK_PANEL_W - 1, panelY + 16,
                CraftTerminalStyle.LINK_TITLE_DIVIDER.toArgb());

        guiGraphics.drawString(this.font, "Linked", panelX + 6, panelY + 5,
                CraftTerminalStyle.TEXT.toArgb(), false);
        drawMiniButton(guiGraphics, panelX + SORT_BUTTON_X_OFF, panelY + BUTTON_ROW_Y_OFF, sortShort(controller.getStorageSort()));
        drawMiniButton(guiGraphics, panelX + DIR_BUTTON_X_OFF, panelY + BUTTON_ROW_Y_OFF, controller.isStorageSortAscending() ? "A" : "D");

        int prevX = panelX + PAGE_PREV_X_OFF;
        int nextX = panelX + PAGE_NEXT_X_OFF;
        drawMiniButton(guiGraphics, prevX, panelY + BUTTON_ROW_Y_OFF, "<");
        drawMiniButton(guiGraphics, nextX, panelY + BUTTON_ROW_Y_OFF, ">");

        String pageText = (controller.getStoragePage() + 1) + "/" + controller.getStorageTotalPages();
        int pageTextWidth = this.font.width(pageText);
        guiGraphics.drawString(this.font, pageText, panelX + LINK_PANEL_W - pageTextWidth - 44,
                panelY + 9, CraftTerminalStyle.MUTED_TEXT.toArgb(), false);

        int importX = panelX + CARRIED_IMPORT_X_OFF;
        int importY = panelY + CARRIED_IMPORT_Y_OFF;
        UiColor importBg = CraftTerminalStyle.importBackground(!this.menu.getCarried().isEmpty());
        drawSmallButton(guiGraphics, importX, importY, CARRIED_IMPORT_W, CARRIED_IMPORT_H, "STORE", importBg);

        int searchX = panelX + LINK_SEARCH_X_OFF;
        int searchY = panelY + LINK_SEARCH_Y_OFF;
        drawPanelFrame(guiGraphics, searchX, searchY, LINK_GRID_W, LINK_SEARCH_H,
                CraftTerminalStyle.SEARCH_BACKGROUND, CraftTerminalStyle.SEARCH_BORDER_LIGHT,
                CraftTerminalStyle.SEARCH_BORDER_DARK);
        int clearX = searchX + LINK_GRID_W - LINK_SEARCH_CLEAR_W;
        drawPanelFrame(guiGraphics, clearX, searchY, LINK_SEARCH_CLEAR_W, LINK_SEARCH_H,
                CraftTerminalStyle.CLEAR_BACKGROUND, CraftTerminalStyle.CLEAR_BORDER_LIGHT,
                CraftTerminalStyle.SEARCH_BORDER_DARK);
        String clearLabel = this.searchBox != null && !this.searchBox.getValue().isEmpty() ? "x" : ".";
        guiGraphics.drawCenteredString(this.font, clearLabel, clearX + (LINK_SEARCH_CLEAR_W / 2),
                searchY + 2, CraftTerminalStyle.TEXT.toArgb());

        List<StorageEntry> entries = controller.getStorageEntries();
        int maxSlots = Math.min(entries.size(), LINK_COLS * LINK_ROWS);
        int gridX = panelX + LINK_GRID_X_OFF;
        int gridY = panelY + LINK_GRID_Y_OFF;
        for (int i = 0; i < LINK_COLS * LINK_ROWS; i++) {
            int slotX = gridX + (i % LINK_COLS) * LINK_SLOT_PITCH;
            int slotY = gridY + (i / LINK_COLS) * LINK_SLOT_PITCH;

            UiColor slotFill = CraftTerminalStyle.slotBackground(isHoveringLinkedSlot(mouseX, mouseY, i));
            drawPanelFrame(guiGraphics, slotX, slotY, LINK_SLOT_SIZE, LINK_SLOT_SIZE, slotFill,
                    CraftTerminalStyle.SLOT_BORDER_LIGHT, CraftTerminalStyle.SLOT_BORDER_DARK);
            if (i >= maxSlots) {
                continue;
            }
            StorageEntry entry = entries.get(i);
            guiGraphics.renderItem(entry.stack(), slotX + 1, slotY + 1);
            drawCountOverlay(guiGraphics, slotX, slotY, RtsClientUiUtil.compactCount(entry.count()));
        }

        int hovered = resolveLinkedSlotIndex(mouseX, mouseY);
        if (hovered >= 0 && hovered < maxSlots) {
            StorageEntry entry = entries.get(hovered);
            guiGraphics.renderTooltip(this.font, entry.stack(), (int) mouseX, (int) mouseY);
        }
    }

    private boolean handleLinkedPanelClick(double mouseX, double mouseY, int button) {
        ClientRtsController controller = ClientRtsController.get();
        int panelX = this.leftPos + LINK_PANEL_X_OFF;
        int panelY = this.topPos + LINK_PANEL_Y_OFF;
        int searchX = panelX + LINK_SEARCH_X_OFF;
        int searchY = panelY + LINK_SEARCH_Y_OFF;
        int clearX = searchX + LINK_GRID_W - LINK_SEARCH_CLEAR_W;

        if (UiRect.contains(clearX, searchY, LINK_SEARCH_CLEAR_W, LINK_SEARCH_H, mouseX, mouseY)) {
            if (this.searchBox != null) {
                this.searchBox.setValue("");
                this.searchBox.setFocused(true);
                this.setFocused(this.searchBox);
            }
            return true;
        }

        if (UiRect.contains(searchX, searchY, LINK_GRID_W, LINK_SEARCH_H, mouseX, mouseY)) {
            if (this.searchBox != null) {
                this.searchBox.setFocused(true);
                this.setFocused(this.searchBox);
            }
            return true;
        }

        if (this.searchBox != null && this.searchBox.isFocused()) {
            this.searchBox.setFocused(false);
            this.setFocused(null);
        }

        int sortX = panelX + SORT_BUTTON_X_OFF;
        int dirX = panelX + DIR_BUTTON_X_OFF;
        int prevX = panelX + PAGE_PREV_X_OFF;
        int nextX = panelX + PAGE_NEXT_X_OFF;
        int rowY = panelY + BUTTON_ROW_Y_OFF;

        if (UiRect.contains(sortX, rowY, MINI_BUTTON_W, MINI_BUTTON_H, mouseX, mouseY)) {
            controller.cycleSort();
            return true;
        }
        if (UiRect.contains(dirX, rowY, MINI_BUTTON_W, MINI_BUTTON_H, mouseX, mouseY)) {
            controller.toggleSortDirection();
            return true;
        }
        if (UiRect.contains(prevX, rowY, MINI_BUTTON_W, MINI_BUTTON_H, mouseX, mouseY)) {
            controller.prevPage();
            return true;
        }
        if (UiRect.contains(nextX, rowY, MINI_BUTTON_W, MINI_BUTTON_H, mouseX, mouseY)) {
            controller.nextPage();
            return true;
        }

        int importX = panelX + CARRIED_IMPORT_X_OFF;
        int importY = panelY + CARRIED_IMPORT_Y_OFF;
        if (UiRect.contains(importX, importY, CARRIED_IMPORT_W, CARRIED_IMPORT_H, mouseX, mouseY)) {
            return returnCarriedToLinked(button == GLFW.GLFW_MOUSE_BUTTON_RIGHT ? 1 : Integer.MAX_VALUE);
        }

        if (!this.menu.getCarried().isEmpty() && isInsideLinkedGrid(mouseX, mouseY)) {
            return returnCarriedToLinked(button == GLFW.GLFW_MOUSE_BUTTON_RIGHT ? 1 : Integer.MAX_VALUE);
        }

        int linkedIndex = resolveLinkedSlotIndex(mouseX, mouseY);
        if (linkedIndex >= 0) {
            List<StorageEntry> entries = controller.getStorageEntries();
            if (linkedIndex < entries.size()) {
                int requested = button == GLFW.GLFW_MOUSE_BUTTON_RIGHT ? 1 : Integer.MAX_VALUE;
                return pickupFromLinked(entries.get(linkedIndex), requested);
            }
            return true;
        }

        return UiRect.contains(panelX, panelY, LINK_PANEL_W, LINK_PANEL_H, mouseX, mouseY);
    }

    private boolean pickupFromLinked(StorageEntry entry, int requestedAmount) {
        if (entry == null || entry.stack().isEmpty()) {
            return false;
        }
        ItemStack carried = this.menu.getCarried();
        int requested = requestedAmount <= 0 ? 1 : requestedAmount;
        int wanted;
        if (carried.isEmpty()) {
            wanted = Math.min(requested, entry.stack().getMaxStackSize());
        } else {
            if (!ItemStack.isSameItemSameComponents(carried, entry.stack())) {
                return false;
            }
            wanted = Math.min(requested, carried.getMaxStackSize() - carried.getCount());
        }
        if (wanted <= 0) {
            return false;
        }

        applyLocalCarriedPreview(entry.stack(), wanted);
        ItemStack request = entry.stack().copy();
        request.setCount(1);
        ClientPlayNetworking.send(new C2SRtsLinkedPickupPayload(request, wanted));
        return true;
    }

    private boolean returnCarriedToLinked(int requestedAmount) {
        ItemStack carried = this.menu.getCarried();
        if (carried.isEmpty()) {
            return false;
        }
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(carried.getItem());
        if (itemId == null) {
            return false;
        }
        int amount = Math.max(1, Math.min(requestedAmount, carried.getCount()));
        ClientPlayNetworking.send(new C2SRtsReturnCarriedPayload(itemId.toString(), amount));

        carried.shrink(amount);
        this.menu.setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried);
        return true;
    }

    private void applyLocalCarriedPreview(ItemStack pickedPrototype, int requested) {
        if (pickedPrototype == null || pickedPrototype.isEmpty()) {
            return;
        }
        ItemStack carried = this.menu.getCarried();
        int wanted = Math.max(1, requested);
        if (carried.isEmpty()) {
            ItemStack preview = pickedPrototype.copy();
            preview.setCount(Math.min(wanted, preview.getMaxStackSize()));
            this.menu.setCarried(preview);
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
        this.menu.setCarried(carried);
    }

    private int resolveLinkedSlotIndex(double mouseX, double mouseY) {
        int gridX = this.leftPos + LINK_PANEL_X_OFF + LINK_GRID_X_OFF;
        int gridY = this.topPos + LINK_PANEL_Y_OFF + LINK_GRID_Y_OFF;
        int gridW = LINK_COLS * LINK_SLOT_PITCH;
        int gridH = LINK_ROWS * LINK_SLOT_PITCH;
        if (!UiRect.contains(gridX, gridY, gridW, gridH, mouseX, mouseY)) {
            return -1;
        }
        int col = Mth.floor((mouseX - gridX) / LINK_SLOT_PITCH);
        int row = Mth.floor((mouseY - gridY) / LINK_SLOT_PITCH);
        if (col < 0 || col >= LINK_COLS || row < 0 || row >= LINK_ROWS) {
            return -1;
        }
        int sx = gridX + col * LINK_SLOT_PITCH;
        int sy = gridY + row * LINK_SLOT_PITCH;
        if (!UiRect.contains(sx, sy, LINK_SLOT_SIZE, LINK_SLOT_SIZE, mouseX, mouseY)) {
            return -1;
        }
        return row * LINK_COLS + col;
    }

    private boolean isInsideLinkedGrid(double mouseX, double mouseY) {
        int gridX = this.leftPos + LINK_PANEL_X_OFF + LINK_GRID_X_OFF;
        int gridY = this.topPos + LINK_PANEL_Y_OFF + LINK_GRID_Y_OFF;
        int gridH = LINK_ROWS * LINK_SLOT_PITCH;
        return UiRect.contains(gridX, gridY, LINK_GRID_W, gridH, mouseX, mouseY);
    }

    private boolean isHoveringLinkedSlot(double mouseX, double mouseY, int index) {
        int resolved = resolveLinkedSlotIndex(mouseX, mouseY);
        return resolved == index;
    }

    private void onSearchChanged(String value) {
        String next = value == null ? "" : value;
        ClientRtsController controller = ClientRtsController.get();
        if (!next.equals(controller.getStorageSearch())) {
            controller.setStorageSearch(next);
        }
    }

    private void syncSearchValueFromController() {
        if (this.searchBox == null || this.searchBox.isFocused()) {
            return;
        }
        String expected = ClientRtsController.get().getStorageSearch();
        if (expected == null) {
            expected = "";
        }
        if (!expected.equals(this.searchBox.getValue())) {
            this.searchBox.setValue(expected);
        }
    }

    public Rect2i getLinkedPanelArea() {
        return new Rect2i(this.leftPos + LINK_PANEL_X_OFF, this.topPos + LINK_PANEL_Y_OFF, LINK_PANEL_W, LINK_PANEL_H);
    }

    public StorageEntry getLinkedEntryAt(double mouseX, double mouseY) {
        int index = resolveLinkedSlotIndex(mouseX, mouseY);
        if (index < 0) {
            return null;
        }
        List<StorageEntry> entries = ClientRtsController.get().getStorageEntries();
        if (index >= entries.size()) {
            return null;
        }
        return entries.get(index);
    }

    public Rect2i getLinkedSlotAreaAt(double mouseX, double mouseY) {
        int index = resolveLinkedSlotIndex(mouseX, mouseY);
        if (index < 0) {
            return null;
        }
        int gridX = this.leftPos + LINK_PANEL_X_OFF + LINK_GRID_X_OFF;
        int gridY = this.topPos + LINK_PANEL_Y_OFF + LINK_GRID_Y_OFF;
        int col = index % LINK_COLS;
        int row = index / LINK_COLS;
        int slotX = gridX + col * LINK_SLOT_PITCH;
        int slotY = gridY + row * LINK_SLOT_PITCH;
        return new Rect2i(slotX, slotY, LINK_SLOT_SIZE, LINK_SLOT_SIZE);
    }

    private void renderCraftResultFallback(GuiGraphics guiGraphics) {
        if (this.menu == null || this.menu.slots.isEmpty()) {
            return;
        }
        Slot resultSlot = this.menu.getSlot(0);
        if (resultSlot == null) {
            return;
        }
        int slotX = this.leftPos + resultSlot.x;
        int slotY = this.topPos + resultSlot.y;
        ItemStack result = resultSlot.getItem();
        if (result.isEmpty()) {
            result = resolveLocalCraftPreview();
        }
        if (result.isEmpty()) {
            return;
        }
        guiGraphics.renderItem(result, slotX, slotY);
        guiGraphics.renderItemDecorations(this.font, result, slotX, slotY);
    }

    private ItemStack resolveLocalCraftPreview() {
        Minecraft minecraft = this.minecraft;
        if (minecraft == null || minecraft.level == null || this.menu == null) {
            return ItemStack.EMPTY;
        }

        java.util.List<ItemStack> inputs = new java.util.ArrayList<>(9);
        boolean anyNonEmpty = false;
        for (int i = 0; i < 9; i++) {
            Slot slot = this.menu.getSlot(1 + i);
            ItemStack stack = slot == null ? ItemStack.EMPTY : slot.getItem();
            if (stack.isEmpty()) {
                inputs.add(ItemStack.EMPTY);
                continue;
            }
            inputs.add(stack.copyWithCount(1));
            anyNonEmpty = true;
        }
        if (!anyNonEmpty) {
            return ItemStack.EMPTY;
        }

        CraftingInput input = CraftingInput.of(3, 3, inputs);
        java.util.Optional<net.minecraft.world.item.crafting.RecipeHolder<CraftingRecipe>> recipe =
                minecraft.level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, minecraft.level);
        if (recipe.isEmpty()) {
            return ItemStack.EMPTY;
        }

        ItemStack result = recipe.get().value().assemble(input, minecraft.level.registryAccess());
        if (result.isEmpty()) {
            result = recipe.get().value().getResultItem(minecraft.level.registryAccess());
        }
        return result.isEmpty() ? ItemStack.EMPTY : result.copy();
    }

    private void drawCountOverlay(GuiGraphics guiGraphics, int slotX, int slotY, String countText) {
        RtsClientUiUtil.drawSlotCountOverlay(guiGraphics, this.font, slotX, slotY, LINK_SLOT_SIZE,
                countText, CraftTerminalStyle.COUNT_TEXT.toArgb());
    }

    private void drawMiniButton(GuiGraphics guiGraphics, int x, int y, String label) {
        drawSmallButton(guiGraphics, x, y, MINI_BUTTON_W, MINI_BUTTON_H, label,
                CraftTerminalStyle.MINI_BUTTON_BACKGROUND);
    }

    private void drawSmallButton(GuiGraphics guiGraphics, int x, int y, int w, int h,
                                 String label, UiColor fill) {
        drawPanelFrame(guiGraphics, x, y, w, h, fill,
                CraftTerminalStyle.BUTTON_BORDER_LIGHT, CraftTerminalStyle.BUTTON_BORDER_DARK);
        guiGraphics.drawCenteredString(this.font, label, x + (w / 2), y + 2,
                CraftTerminalStyle.BUTTON_TEXT.toArgb());
    }

    private static String sortShort(RtsStorageSort sort) {
        return switch (sort) {
            case QUANTITY -> "Q";
            case MOD -> "M";
            case NAME -> "N";
        };
    }

    private void drawPanelFrame(GuiGraphics guiGraphics, int x, int y, int w, int h,
                                UiColor fillColor, UiColor light, UiColor dark) {
        UiChromeRenderer.frame(new MinecraftUiCanvas(guiGraphics, this.font),
                new UiRect(x, y, w, h), 1.0D, fillColor, light, dark);
    }
}
