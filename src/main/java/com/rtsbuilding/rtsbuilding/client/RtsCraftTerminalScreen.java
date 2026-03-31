package com.rtsbuilding.rtsbuilding.client;

import java.util.List;

import com.rtsbuilding.rtsbuilding.network.C2SRtsImportMenuSlotPayload;
import com.rtsbuilding.rtsbuilding.network.C2SRtsLinkedPickupPayload;
import com.rtsbuilding.rtsbuilding.network.C2SRtsReturnCarriedPayload;
import com.rtsbuilding.rtsbuilding.network.RtsStorageSort;

import org.lwjgl.glfw.GLFW;

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
import net.neoforged.neoforge.network.PacketDistributor;

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
        this.searchBox.setTextColor(0xEAF2FF);
        this.searchBox.setTextColorUneditable(0xAAB8C8);
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
        guiGraphics.fill(left + 3, top + 3, left + VANILLA_BG_W - 3, top + 15, 0xB0212E3D);
        guiGraphics.hLine(left + 3, left + VANILLA_BG_W - 3, top + 15, 0xFF0F151D);
        drawPanelFrame(guiGraphics, left + 27, top + 14, 58, 58, 0x66405B78, 0xFF5B7290, 0xFF10161E);
        drawPanelFrame(guiGraphics, left + 124, top + 33, 18, 18, 0x663F5A76, 0xFF617A99, 0xFF111821);
        drawPanelFrame(guiGraphics, left + 7, top + 82, 162, 76, 0x441A222C, 0xFF4A6079, 0xFF10151C);

        renderLinkedPanel(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(this.font, "RTS Craft Terminal", this.titleLabelX, this.titleLabelY, 0xEAF2FF, false);
        guiGraphics.drawString(this.font, "Inventory", this.inventoryLabelX, this.inventoryLabelY, 0xD7E2EE, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if ((button == GLFW.GLFW_MOUSE_BUTTON_LEFT || button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) && Screen.hasShiftDown()) {
            Slot hovered = this.getSlotUnderMouse();
            if (hovered != null && hovered.hasItem()) {
                int menuSlot = this.menu.slots.indexOf(hovered);
                if (menuSlot >= 0) {
                    if (menuSlot == 0 && button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                        return true;
                    }
                    PacketDistributor.sendToServer(new C2SRtsImportMenuSlotPayload(menuSlot));
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
        if (inside(mouseX, mouseY, panelX, panelY, LINK_PANEL_W, LINK_PANEL_H)) {
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
        drawPanelFrame(guiGraphics, panelX, panelY, LINK_PANEL_W, LINK_PANEL_H, 0xCC141922, 0xFF637993, 0xFF0D1218);
        guiGraphics.fill(panelX + 1, panelY + 1, panelX + LINK_PANEL_W - 1, panelY + 16, 0xA0233345);
        guiGraphics.hLine(panelX + 1, panelX + LINK_PANEL_W - 1, panelY + 16, 0xFF0F171F);

        guiGraphics.drawString(this.font, "Linked", panelX + 6, panelY + 5, 0xEAF2FF, false);
        drawMiniButton(guiGraphics, panelX + SORT_BUTTON_X_OFF, panelY + BUTTON_ROW_Y_OFF, sortShort(controller.getStorageSort()));
        drawMiniButton(guiGraphics, panelX + DIR_BUTTON_X_OFF, panelY + BUTTON_ROW_Y_OFF, controller.isStorageSortAscending() ? "A" : "D");

        int prevX = panelX + PAGE_PREV_X_OFF;
        int nextX = panelX + PAGE_NEXT_X_OFF;
        drawMiniButton(guiGraphics, prevX, panelY + BUTTON_ROW_Y_OFF, "<");
        drawMiniButton(guiGraphics, nextX, panelY + BUTTON_ROW_Y_OFF, ">");

        String pageText = (controller.getStoragePage() + 1) + "/" + controller.getStorageTotalPages();
        int pageTextWidth = this.font.width(pageText);
        guiGraphics.drawString(this.font, pageText, panelX + LINK_PANEL_W - pageTextWidth - 44, panelY + 9, 0xD7E3F2, false);

        int importX = panelX + CARRIED_IMPORT_X_OFF;
        int importY = panelY + CARRIED_IMPORT_Y_OFF;
        int importBg = this.menu.getCarried().isEmpty() ? 0x8821262D : 0xAA2E516A;
        drawSmallButton(guiGraphics, importX, importY, CARRIED_IMPORT_W, CARRIED_IMPORT_H, "STORE", importBg);

        int searchX = panelX + LINK_SEARCH_X_OFF;
        int searchY = panelY + LINK_SEARCH_Y_OFF;
        drawPanelFrame(guiGraphics, searchX, searchY, LINK_GRID_W, LINK_SEARCH_H, 0xAA1E2731, 0xFF5E738A, 0xFF111921);
        int clearX = searchX + LINK_GRID_W - LINK_SEARCH_CLEAR_W;
        drawPanelFrame(guiGraphics, clearX, searchY, LINK_SEARCH_CLEAR_W, LINK_SEARCH_H, 0xAA2A3441, 0xFF647B95, 0xFF111921);
        String clearLabel = this.searchBox != null && !this.searchBox.getValue().isEmpty() ? "x" : ".";
        guiGraphics.drawCenteredString(this.font, clearLabel, clearX + (LINK_SEARCH_CLEAR_W / 2), searchY + 2, 0xEAF2FF);

        List<ClientRtsController.StorageEntry> entries = controller.getStorageEntries();
        int maxSlots = Math.min(entries.size(), LINK_COLS * LINK_ROWS);
        int gridX = panelX + LINK_GRID_X_OFF;
        int gridY = panelY + LINK_GRID_Y_OFF;
        for (int i = 0; i < LINK_COLS * LINK_ROWS; i++) {
            int slotX = gridX + (i % LINK_COLS) * LINK_SLOT_PITCH;
            int slotY = gridY + (i / LINK_COLS) * LINK_SLOT_PITCH;

            int slotFill = isHoveringLinkedSlot(mouseX, mouseY, i) ? 0xAA304053 : 0xAA1A212B;
            drawPanelFrame(guiGraphics, slotX, slotY, LINK_SLOT_SIZE, LINK_SLOT_SIZE, slotFill, 0xFF596D84, 0xFF11171E);
            if (i >= maxSlots) {
                continue;
            }
            ClientRtsController.StorageEntry entry = entries.get(i);
            guiGraphics.renderItem(entry.stack(), slotX + 1, slotY + 1);
            drawCountOverlay(guiGraphics, slotX, slotY, compactCount(entry.count()));
        }

        int hovered = resolveLinkedSlotIndex(mouseX, mouseY);
        if (hovered >= 0 && hovered < maxSlots) {
            ClientRtsController.StorageEntry entry = entries.get(hovered);
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

        if (inside(mouseX, mouseY, clearX, searchY, LINK_SEARCH_CLEAR_W, LINK_SEARCH_H)) {
            if (this.searchBox != null) {
                this.searchBox.setValue("");
                this.searchBox.setFocused(true);
                this.setFocused(this.searchBox);
            }
            return true;
        }

        if (inside(mouseX, mouseY, searchX, searchY, LINK_GRID_W, LINK_SEARCH_H)) {
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

        if (inside(mouseX, mouseY, sortX, rowY, MINI_BUTTON_W, MINI_BUTTON_H)) {
            controller.cycleSort();
            return true;
        }
        if (inside(mouseX, mouseY, dirX, rowY, MINI_BUTTON_W, MINI_BUTTON_H)) {
            controller.toggleSortDirection();
            return true;
        }
        if (inside(mouseX, mouseY, prevX, rowY, MINI_BUTTON_W, MINI_BUTTON_H)) {
            controller.prevPage();
            return true;
        }
        if (inside(mouseX, mouseY, nextX, rowY, MINI_BUTTON_W, MINI_BUTTON_H)) {
            controller.nextPage();
            return true;
        }

        int importX = panelX + CARRIED_IMPORT_X_OFF;
        int importY = panelY + CARRIED_IMPORT_Y_OFF;
        if (inside(mouseX, mouseY, importX, importY, CARRIED_IMPORT_W, CARRIED_IMPORT_H)) {
            return returnCarriedToLinked(button == GLFW.GLFW_MOUSE_BUTTON_RIGHT ? 1 : Integer.MAX_VALUE);
        }

        if (!this.menu.getCarried().isEmpty() && isInsideLinkedGrid(mouseX, mouseY)) {
            return returnCarriedToLinked(button == GLFW.GLFW_MOUSE_BUTTON_RIGHT ? 1 : Integer.MAX_VALUE);
        }

        int linkedIndex = resolveLinkedSlotIndex(mouseX, mouseY);
        if (linkedIndex >= 0) {
            List<ClientRtsController.StorageEntry> entries = controller.getStorageEntries();
            if (linkedIndex < entries.size()) {
                int requested = button == GLFW.GLFW_MOUSE_BUTTON_RIGHT ? 1 : Integer.MAX_VALUE;
                return pickupFromLinked(entries.get(linkedIndex), requested);
            }
            return true;
        }

        return inside(mouseX, mouseY, panelX, panelY, LINK_PANEL_W, LINK_PANEL_H);
    }

    private boolean pickupFromLinked(ClientRtsController.StorageEntry entry, int requestedAmount) {
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
        PacketDistributor.sendToServer(new C2SRtsLinkedPickupPayload(entry.itemId(), wanted));
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
        PacketDistributor.sendToServer(new C2SRtsReturnCarriedPayload(itemId.toString(), amount));

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
        if (!inside(mouseX, mouseY, gridX, gridY, gridW, gridH)) {
            return -1;
        }
        int col = Mth.floor((mouseX - gridX) / LINK_SLOT_PITCH);
        int row = Mth.floor((mouseY - gridY) / LINK_SLOT_PITCH);
        if (col < 0 || col >= LINK_COLS || row < 0 || row >= LINK_ROWS) {
            return -1;
        }
        int sx = gridX + col * LINK_SLOT_PITCH;
        int sy = gridY + row * LINK_SLOT_PITCH;
        if (!inside(mouseX, mouseY, sx, sy, LINK_SLOT_SIZE, LINK_SLOT_SIZE)) {
            return -1;
        }
        return row * LINK_COLS + col;
    }

    private boolean isInsideLinkedGrid(double mouseX, double mouseY) {
        int gridX = this.leftPos + LINK_PANEL_X_OFF + LINK_GRID_X_OFF;
        int gridY = this.topPos + LINK_PANEL_Y_OFF + LINK_GRID_Y_OFF;
        int gridH = LINK_ROWS * LINK_SLOT_PITCH;
        return inside(mouseX, mouseY, gridX, gridY, LINK_GRID_W, gridH);
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

    public ClientRtsController.StorageEntry getLinkedEntryAt(double mouseX, double mouseY) {
        int index = resolveLinkedSlotIndex(mouseX, mouseY);
        if (index < 0) {
            return null;
        }
        List<ClientRtsController.StorageEntry> entries = ClientRtsController.get().getStorageEntries();
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
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0.0F, 0.0F, 300.0F);
        guiGraphics.fill(slotX + 1, slotY + LINK_SLOT_SIZE - 8, slotX + LINK_SLOT_SIZE - 1, slotY + LINK_SLOT_SIZE - 1, 0xB0000000);
        guiGraphics.drawString(this.font, countText, slotX + 2, slotY + LINK_SLOT_SIZE - 7, 0xFFE8F4FF, true);
        guiGraphics.pose().popPose();
    }

    private void drawMiniButton(GuiGraphics guiGraphics, int x, int y, String label) {
        drawSmallButton(guiGraphics, x, y, MINI_BUTTON_W, MINI_BUTTON_H, label, 0xAA2B3642);
    }

    private void drawSmallButton(GuiGraphics guiGraphics, int x, int y, int w, int h, String label, int fill) {
        drawPanelFrame(guiGraphics, x, y, w, h, fill, 0xFF667D95, 0xFF111821);
        guiGraphics.drawCenteredString(this.font, label, x + (w / 2), y + 2, 0xFFFFFF);
    }

    private static String sortShort(RtsStorageSort sort) {
        return switch (sort) {
            case QUANTITY -> "Q";
            case MOD -> "M";
            case NAME -> "N";
        };
    }

    private static String compactCount(long value) {
        return RtsClientUiUtil.compactCount(value);
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }

    private static void drawPanelFrame(GuiGraphics guiGraphics, int x, int y, int w, int h, int fillColor, int light, int dark) {
        RtsClientUiUtil.drawPanelFrame(guiGraphics, x, y, w, h, fillColor, light, dark);
    }
}
