package com.rtsbuilding.rtsbuilding.client.screen.standalone;

import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.input.overlay.LegacyGuiGraphics;
import com.rtsbuilding.rtsbuilding.client.record.StorageEntry;
import com.rtsbuilding.rtsbuilding.client.screen.canvas.MinecraftUiCanvas;
import com.rtsbuilding.rtsbuilding.client.util.RtsClientUiUtil;
import com.rtsbuilding.rtsbuilding.uicore.bottom.BottomBarUiFormats;
import com.rtsbuilding.rtsbuilding.network.RtsPayloadRegistrar;
import com.rtsbuilding.rtsbuilding.network.storage.C2SRtsImportMenuSlotPayload;
import com.rtsbuilding.rtsbuilding.network.storage.C2SRtsLinkedPickupPayload;
import com.rtsbuilding.rtsbuilding.network.storage.C2SRtsReturnCarriedPayload;
import com.rtsbuilding.rtsbuilding.network.storage.RtsStorageSort;
import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uikit.canvas.UiChromeRenderer;
import com.rtsbuilding.rtsbuilding.uikit.theme.CraftTerminalStyle;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiColor;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ContainerWorkbench;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.ITextComponent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.awt.Rectangle;
import java.io.IOException;
import java.util.List;

/** 1.12.2 工作台容器上的 RTS 联网仓储侧栏。 */
public final class RtsCraftTerminalScreen extends GuiContainer {
    private static final ResourceLocation VANILLA_CRAFTING_BG =
            new ResourceLocation("minecraft", "textures/gui/container/crafting_table.png");
    private static final int VANILLA_BG_W = 176, LINK_PANEL_X_OFF = VANILLA_BG_W + 6, LINK_PANEL_Y_OFF = 4;
    private static final int LINK_PANEL_W = 166, LINK_PANEL_H = 158;
    private static final int LINK_SEARCH_X_OFF = 8, LINK_SEARCH_Y_OFF = 19, LINK_SEARCH_H = 12, LINK_SEARCH_CLEAR_W = 10;
    private static final int LINK_GRID_X_OFF = 8, LINK_GRID_Y_OFF = 35, LINK_COLS = 8, LINK_ROWS = 5;
    private static final int LINK_SLOT_PITCH = 20, LINK_SLOT_SIZE = 18, LINK_GRID_W = LINK_COLS * LINK_SLOT_PITCH;
    private static final int MINI_BUTTON_W = 12, MINI_BUTTON_H = 11, SORT_BUTTON_X_OFF = 40;
    private static final int DIR_BUTTON_X_OFF = SORT_BUTTON_X_OFF + 14;
    private static final int PAGE_PREV_X_OFF = LINK_PANEL_W - 40, PAGE_NEXT_X_OFF = PAGE_PREV_X_OFF + 28;
    private static final int BUTTON_ROW_Y_OFF = 7, CARRIED_IMPORT_W = 48, CARRIED_IMPORT_H = 12;
    private static final int CARRIED_IMPORT_X_OFF = LINK_PANEL_W - CARRIED_IMPORT_W - 8;
    private static final int CARRIED_IMPORT_Y_OFF = LINK_PANEL_H - CARRIED_IMPORT_H - 7;

    private final ContainerWorkbench workbench;
    private final ITextComponent terminalTitle;
    private GuiTextField searchBox;

    public RtsCraftTerminalScreen(ContainerWorkbench menu, InventoryPlayer inventory, ITextComponent title) {
        super(menu);
        this.workbench = menu;
        this.terminalTitle = title;
        this.xSize = VANILLA_BG_W + LINK_PANEL_W + 12;
        this.ySize = 166;
    }

    @Override public void initGui() {
        super.initGui();
        int panelX = this.guiLeft + LINK_PANEL_X_OFF;
        int panelY = this.guiTop + LINK_PANEL_Y_OFF;
        int searchW = LINK_GRID_W - LINK_SEARCH_CLEAR_W - 4;
        this.searchBox = new NoShadowTextField(0, this.fontRenderer, panelX + LINK_SEARCH_X_OFF + 2,
                panelY + LINK_SEARCH_Y_OFF + 2, searchW, 8);
        this.searchBox.setEnableBackgroundDrawing(false);
        this.searchBox.setCanLoseFocus(true);
        this.searchBox.setTextColor(CraftTerminalStyle.TEXT.toArgb());
        this.searchBox.setDisabledTextColour(CraftTerminalStyle.UNEDITABLE_TEXT.toArgb());
        ClientRtsController.get().setStorageSearch("");
        this.searchBox.setText("");
    }

    @Override public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        syncSearchValueFromController();
        drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partialTicks);
        renderCraftResultFallback();
        if (this.searchBox != null) this.searchBox.drawTextBox();
        renderHoveredLinkedTooltip(mouseX, mouseY);
        renderHoveredToolTip(mouseX, mouseY);
    }

    @Override protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        LegacyGuiGraphics g = graphics();
        this.mc.getTextureManager().bindTexture(VANILLA_CRAFTING_BG);
        GlStateManager.color(1F, 1F, 1F, 1F);
        drawTexturedModalRect(this.guiLeft, this.guiTop, 0, 0, VANILLA_BG_W, this.ySize);
        g.fill(this.guiLeft + 3, this.guiTop + 3, this.guiLeft + VANILLA_BG_W - 3, this.guiTop + 15,
                CraftTerminalStyle.VANILLA_TITLE_BACKGROUND.toArgb());
        g.fill(this.guiLeft + 3, this.guiTop + 15, this.guiLeft + VANILLA_BG_W - 3, this.guiTop + 16,
                CraftTerminalStyle.VANILLA_TITLE_DIVIDER.toArgb());
        drawPanelFrame(g, this.guiLeft + 27, this.guiTop + 14, 58, 58, CraftTerminalStyle.CRAFT_GRID_BACKGROUND,
                CraftTerminalStyle.CRAFT_GRID_BORDER_LIGHT, CraftTerminalStyle.CRAFT_GRID_BORDER_DARK);
        drawPanelFrame(g, this.guiLeft + 124, this.guiTop + 33, 18, 18, CraftTerminalStyle.RESULT_BACKGROUND,
                CraftTerminalStyle.RESULT_BORDER_LIGHT, CraftTerminalStyle.RESULT_BORDER_DARK);
        drawPanelFrame(g, this.guiLeft + 7, this.guiTop + 82, 162, 76, CraftTerminalStyle.INVENTORY_BACKGROUND,
                CraftTerminalStyle.INVENTORY_BORDER_LIGHT, CraftTerminalStyle.INVENTORY_BORDER_DARK);
        renderLinkedPanel(g, mouseX, mouseY);
    }

    @Override protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        String title = this.terminalTitle == null ? "RTS Craft Terminal" : this.terminalTitle.getUnformattedText();
        this.fontRenderer.drawString(title, 28, 6, CraftTerminalStyle.TEXT.toArgb(), false);
    }

    @Override protected void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
        Slot hovered = getSlotUnderMouse();
        if ((button == 0 || button == 1) && GuiScreen.isShiftKeyDown() && hovered != null
                && hovered.getHasStack()) {
            int menuSlot = this.inventorySlots.inventorySlots.indexOf(hovered);
            if (menuSlot >= 0) {
                if (menuSlot != 0 || button == 0) RtsPayloadRegistrar.sendToServer(new C2SRtsImportMenuSlotPayload(menuSlot));
                return;
            }
        }
        if ((button == 0 || button == 1) && handleLinkedPanelClick(mouseX, mouseY, button)) return;
        if (this.searchBox != null) this.searchBox.mouseClicked(mouseX, mouseY, button);
        super.mouseClicked(mouseX, mouseY, button);
    }

    @Override protected void mouseReleased(int mouseX, int mouseY, int state) {
        super.mouseReleased(mouseX, mouseY, state);
    }

    @Override public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) return;
        ScaledResolution sr = new ScaledResolution(this.mc);
        double x = Mouse.getEventX() * sr.getScaledWidth() / (double) this.mc.displayWidth;
        double y = sr.getScaledHeight() - Mouse.getEventY() * sr.getScaledHeight() / (double) this.mc.displayHeight - 1;
        mouseScrolled(x, y, wheel > 0 ? 1D : -1D);
    }

    boolean mouseScrolled(double mouseX, double mouseY, double wheel) {
        int panelX = this.guiLeft + LINK_PANEL_X_OFF, panelY = this.guiTop + LINK_PANEL_Y_OFF;
        if (!UiRect.contains(panelX, panelY, LINK_PANEL_W, LINK_PANEL_H, mouseX, mouseY)) return false;
        if (wheel > 0) ClientRtsController.get().prevPage();
        else if (wheel < 0) ClientRtsController.get().nextPage();
        return true;
    }

    @Override protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (this.searchBox != null && this.searchBox.isFocused()) {
            if (keyCode == Keyboard.KEY_ESCAPE) {
                this.searchBox.setText(""); this.searchBox.setFocused(false); return;
            }
            if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
                this.searchBox.setFocused(false); return;
            }
            if (this.searchBox.textboxKeyTyped(typedChar, keyCode)) onSearchChanged(this.searchBox.getText());
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    private void renderLinkedPanel(LegacyGuiGraphics g, int mouseX, int mouseY) {
        ClientRtsController controller = ClientRtsController.get();
        int panelX = guiLeft + LINK_PANEL_X_OFF, panelY = guiTop + LINK_PANEL_Y_OFF;
        drawPanelFrame(g, panelX, panelY, LINK_PANEL_W, LINK_PANEL_H, CraftTerminalStyle.LINK_BACKGROUND,
                CraftTerminalStyle.LINK_BORDER_LIGHT, CraftTerminalStyle.LINK_BORDER_DARK);
        g.fill(panelX + 1, panelY + 1, panelX + LINK_PANEL_W - 1, panelY + 16,
                CraftTerminalStyle.LINK_TITLE_BACKGROUND.toArgb());
        g.fill(panelX + 1, panelY + 16, panelX + LINK_PANEL_W - 1, panelY + 17,
                CraftTerminalStyle.LINK_TITLE_DIVIDER.toArgb());
        g.drawString(fontRenderer, "Linked", panelX + 6, panelY + 5, CraftTerminalStyle.TEXT.toArgb(), false);
        drawMiniButton(g, panelX + SORT_BUTTON_X_OFF, panelY + BUTTON_ROW_Y_OFF, sortShort(controller.getStorageSort()));
        drawMiniButton(g, panelX + DIR_BUTTON_X_OFF, panelY + BUTTON_ROW_Y_OFF,
                controller.isStorageSortAscending() ? "A" : "D");
        drawMiniButton(g, panelX + PAGE_PREV_X_OFF, panelY + BUTTON_ROW_Y_OFF, "<");
        drawMiniButton(g, panelX + PAGE_NEXT_X_OFF, panelY + BUTTON_ROW_Y_OFF, ">");
        String page = (controller.getStoragePage() + 1) + "/" + controller.getStorageTotalPages();
        g.drawString(fontRenderer, page, panelX + LINK_PANEL_W - fontRenderer.getStringWidth(page) - 44,
                panelY + 9, CraftTerminalStyle.MUTED_TEXT.toArgb(), false);
        ItemStack carried = carried();
        drawSmallButton(g, panelX + CARRIED_IMPORT_X_OFF, panelY + CARRIED_IMPORT_Y_OFF,
                CARRIED_IMPORT_W, CARRIED_IMPORT_H, "STORE", CraftTerminalStyle.importBackground(!carried.isEmpty()));

        int searchX = panelX + LINK_SEARCH_X_OFF, searchY = panelY + LINK_SEARCH_Y_OFF;
        drawPanelFrame(g, searchX, searchY, LINK_GRID_W, LINK_SEARCH_H, CraftTerminalStyle.SEARCH_BACKGROUND,
                CraftTerminalStyle.SEARCH_BORDER_LIGHT, CraftTerminalStyle.SEARCH_BORDER_DARK);
        int clearX = searchX + LINK_GRID_W - LINK_SEARCH_CLEAR_W;
        drawPanelFrame(g, clearX, searchY, LINK_SEARCH_CLEAR_W, LINK_SEARCH_H, CraftTerminalStyle.CLEAR_BACKGROUND,
                CraftTerminalStyle.CLEAR_BORDER_LIGHT, CraftTerminalStyle.SEARCH_BORDER_DARK);
        g.drawCenteredString(fontRenderer, searchBox != null && !searchBox.getText().isEmpty() ? "x" : ".",
                clearX + LINK_SEARCH_CLEAR_W / 2, searchY + 2, CraftTerminalStyle.TEXT.toArgb());

        List<StorageEntry> entries = controller.getStorageEntries();
        int maxSlots = Math.min(entries.size(), LINK_COLS * LINK_ROWS);
        int gridX = panelX + LINK_GRID_X_OFF, gridY = panelY + LINK_GRID_Y_OFF;
        for (int i = 0; i < LINK_COLS * LINK_ROWS; i++) {
            int sx = gridX + i % LINK_COLS * LINK_SLOT_PITCH, sy = gridY + i / LINK_COLS * LINK_SLOT_PITCH;
            drawPanelFrame(g, sx, sy, LINK_SLOT_SIZE, LINK_SLOT_SIZE,
                    CraftTerminalStyle.slotBackground(isHoveringLinkedSlot(mouseX, mouseY, i)),
                    CraftTerminalStyle.SLOT_BORDER_LIGHT, CraftTerminalStyle.SLOT_BORDER_DARK);
            if (i < maxSlots) {
                StorageEntry entry = entries.get(i);
                g.renderItem(entry.stack(), sx + 1, sy + 1);
                drawCountOverlay(g, sx, sy, BottomBarUiFormats.compactCount(entry.count()));
            }
        }
    }

    private void renderHoveredLinkedTooltip(int mouseX, int mouseY) {
        StorageEntry entry = getLinkedEntryAt(mouseX, mouseY);
        if (entry != null && !entry.stack().isEmpty()) graphics().renderTooltip(entry.stack(), mouseX, mouseY);
    }

    private boolean handleLinkedPanelClick(double mouseX, double mouseY, int button) {
        ClientRtsController controller = ClientRtsController.get();
        int panelX = guiLeft + LINK_PANEL_X_OFF, panelY = guiTop + LINK_PANEL_Y_OFF;
        int searchX = panelX + LINK_SEARCH_X_OFF, searchY = panelY + LINK_SEARCH_Y_OFF;
        int clearX = searchX + LINK_GRID_W - LINK_SEARCH_CLEAR_W;
        if (UiRect.contains(clearX, searchY, LINK_SEARCH_CLEAR_W, LINK_SEARCH_H, mouseX, mouseY)) {
            searchBox.setText(""); searchBox.setFocused(true); onSearchChanged(""); return true;
        }
        if (UiRect.contains(searchX, searchY, LINK_GRID_W, LINK_SEARCH_H, mouseX, mouseY)) {
            searchBox.setFocused(true); return true;
        }
        if (searchBox != null) searchBox.setFocused(false);
        int rowY = panelY + BUTTON_ROW_Y_OFF;
        if (UiRect.contains(panelX + SORT_BUTTON_X_OFF, rowY, MINI_BUTTON_W, MINI_BUTTON_H, mouseX, mouseY)) { controller.cycleSort(); return true; }
        if (UiRect.contains(panelX + DIR_BUTTON_X_OFF, rowY, MINI_BUTTON_W, MINI_BUTTON_H, mouseX, mouseY)) { controller.toggleSortDirection(); return true; }
        if (UiRect.contains(panelX + PAGE_PREV_X_OFF, rowY, MINI_BUTTON_W, MINI_BUTTON_H, mouseX, mouseY)) { controller.prevPage(); return true; }
        if (UiRect.contains(panelX + PAGE_NEXT_X_OFF, rowY, MINI_BUTTON_W, MINI_BUTTON_H, mouseX, mouseY)) { controller.nextPage(); return true; }
        if (UiRect.contains(panelX + CARRIED_IMPORT_X_OFF, panelY + CARRIED_IMPORT_Y_OFF,
                CARRIED_IMPORT_W, CARRIED_IMPORT_H, mouseX, mouseY)) return returnCarriedToLinked(button == 1 ? 1 : Integer.MAX_VALUE);
        if (!carried().isEmpty() && isInsideLinkedGrid(mouseX, mouseY))
            return returnCarriedToLinked(button == 1 ? 1 : Integer.MAX_VALUE);
        int linked = resolveLinkedSlotIndex(mouseX, mouseY);
        if (linked >= 0) {
            List<StorageEntry> entries = controller.getStorageEntries();
            return linked >= entries.size() || pickupFromLinked(entries.get(linked), button == 1 ? 1 : Integer.MAX_VALUE);
        }
        return UiRect.contains(panelX, panelY, LINK_PANEL_W, LINK_PANEL_H, mouseX, mouseY);
    }

    private boolean pickupFromLinked(StorageEntry entry, int requestedAmount) {
        if (entry == null || entry.stack().isEmpty()) return false;
        ItemStack carried = carried();
        int requested = requestedAmount <= 0 ? 1 : requestedAmount, wanted;
        if (carried.isEmpty()) wanted = Math.min(requested, entry.stack().getMaxStackSize());
        else {
            if (!sameStack(carried, entry.stack())) return false;
            wanted = Math.min(requested, carried.getMaxStackSize() - carried.getCount());
        }
        if (wanted <= 0) return false;
        applyLocalCarriedPreview(entry.stack(), wanted);
        ItemStack request = entry.stack().copy(); request.setCount(1);
        RtsPayloadRegistrar.sendToServer(new C2SRtsLinkedPickupPayload(request, wanted));
        return true;
    }

    private boolean returnCarriedToLinked(int requestedAmount) {
        ItemStack carried = carried();
        if (carried.isEmpty()) return false;
        ResourceLocation id = Item.REGISTRY.getNameForObject(carried.getItem());
        if (id == null) return false;
        int amount = Math.max(1, Math.min(requestedAmount, carried.getCount()));
        RtsPayloadRegistrar.sendToServer(new C2SRtsReturnCarriedPayload(id.toString(), amount));
        carried.shrink(amount); setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried); return true;
    }

    private void applyLocalCarriedPreview(ItemStack prototype, int requested) {
        if (prototype == null || prototype.isEmpty()) return;
        ItemStack carried = carried();
        if (carried.isEmpty()) {
            ItemStack preview = prototype.copy(); preview.setCount(Math.min(requested, preview.getMaxStackSize())); setCarried(preview); return;
        }
        if (!sameStack(carried, prototype)) return;
        int grow = Math.min(requested, carried.getMaxStackSize() - carried.getCount());
        if (grow > 0) { carried.grow(grow); setCarried(carried); }
    }

    private ItemStack carried() { return this.mc == null || this.mc.player == null ? ItemStack.EMPTY : this.mc.player.inventory.getItemStack(); }
    private void setCarried(ItemStack stack) { if (this.mc != null && this.mc.player != null) this.mc.player.inventory.setItemStack(stack); }
    private static boolean sameStack(ItemStack a, ItemStack b) { return ItemStack.areItemsEqual(a, b) && ItemStack.areItemStackTagsEqual(a, b); }

    private int resolveLinkedSlotIndex(double mouseX, double mouseY) {
        int gridX = guiLeft + LINK_PANEL_X_OFF + LINK_GRID_X_OFF, gridY = guiTop + LINK_PANEL_Y_OFF + LINK_GRID_Y_OFF;
        if (!UiRect.contains(gridX, gridY, LINK_GRID_W, LINK_ROWS * LINK_SLOT_PITCH, mouseX, mouseY)) return -1;
        int col = MathHelper.floor((mouseX - gridX) / LINK_SLOT_PITCH), row = MathHelper.floor((mouseY - gridY) / LINK_SLOT_PITCH);
        int sx = gridX + col * LINK_SLOT_PITCH, sy = gridY + row * LINK_SLOT_PITCH;
        return UiRect.contains(sx, sy, LINK_SLOT_SIZE, LINK_SLOT_SIZE, mouseX, mouseY) ? row * LINK_COLS + col : -1;
    }
    private boolean isInsideLinkedGrid(double x, double y) { return UiRect.contains(guiLeft + LINK_PANEL_X_OFF + LINK_GRID_X_OFF,
            guiTop + LINK_PANEL_Y_OFF + LINK_GRID_Y_OFF, LINK_GRID_W, LINK_ROWS * LINK_SLOT_PITCH, x, y); }
    private boolean isHoveringLinkedSlot(double x, double y, int i) { return resolveLinkedSlotIndex(x, y) == i; }
    private void onSearchChanged(String value) {
        String next = value == null ? "" : value;
        if (!next.equals(ClientRtsController.get().getStorageSearch())) ClientRtsController.get().setStorageSearch(next);
    }
    private void syncSearchValueFromController() {
        if (searchBox == null || searchBox.isFocused()) return;
        String expected = ClientRtsController.get().getStorageSearch(); if (expected == null) expected = "";
        if (!expected.equals(searchBox.getText())) searchBox.setText(expected);
    }

    public Rectangle getLinkedPanelArea() { return new Rectangle(guiLeft + LINK_PANEL_X_OFF, guiTop + LINK_PANEL_Y_OFF, LINK_PANEL_W, LINK_PANEL_H); }
    public StorageEntry getLinkedEntryAt(double x, double y) {
        int index = resolveLinkedSlotIndex(x, y); List<StorageEntry> entries = ClientRtsController.get().getStorageEntries();
        return index >= 0 && index < entries.size() ? entries.get(index) : null;
    }
    public Rectangle getLinkedSlotAreaAt(double x, double y) {
        int i = resolveLinkedSlotIndex(x, y); if (i < 0) return null;
        return new Rectangle(guiLeft + LINK_PANEL_X_OFF + LINK_GRID_X_OFF + i % LINK_COLS * LINK_SLOT_PITCH,
                guiTop + LINK_PANEL_Y_OFF + LINK_GRID_Y_OFF + i / LINK_COLS * LINK_SLOT_PITCH, LINK_SLOT_SIZE, LINK_SLOT_SIZE);
    }

    private void renderCraftResultFallback() {
        if (this.inventorySlots == null || this.inventorySlots.inventorySlots.isEmpty()) return;
        Slot slot = this.inventorySlots.getSlot(0); if (slot == null || slot.getHasStack()) return;
        // ContainerWorkbench 的服务端结果同步仍是权威；本地没有结果时不伪造物品，避免配方表版本差异造成幽灵产物。
    }

    private void drawCountOverlay(LegacyGuiGraphics g, int x, int y, String count) {
        RtsClientUiUtil.drawSlotCountOverlay(
                g, fontRenderer, x, y, LINK_SLOT_SIZE, count,
                CraftTerminalStyle.COUNT_TEXT.toArgb());
    }
    private void drawMiniButton(LegacyGuiGraphics g, int x, int y, String label) { drawSmallButton(g, x, y, MINI_BUTTON_W, MINI_BUTTON_H, label, CraftTerminalStyle.MINI_BUTTON_BACKGROUND); }
    private void drawSmallButton(LegacyGuiGraphics g, int x, int y, int w, int h, String label, UiColor fill) {
        drawPanelFrame(g, x, y, w, h, fill, CraftTerminalStyle.BUTTON_BORDER_LIGHT, CraftTerminalStyle.BUTTON_BORDER_DARK);
        g.drawCenteredString(fontRenderer, label, x + w / 2, y + 2, CraftTerminalStyle.BUTTON_TEXT.toArgb());
    }
    private static String sortShort(RtsStorageSort sort) {
        if (sort == RtsStorageSort.QUANTITY) return "Q"; if (sort == RtsStorageSort.MOD) return "M"; return "N";
    }
    private void drawPanelFrame(LegacyGuiGraphics g, int x, int y, int w, int h, UiColor fill, UiColor light, UiColor dark) {
        UiChromeRenderer.frame(new MinecraftUiCanvas(g, fontRenderer), new UiRect(x, y, w, h), 1D, fill, light, dark);
    }
    private LegacyGuiGraphics graphics() { return new LegacyGuiGraphics(this.mc, this.width, this.height); }

    /** 保留原版文本输入与快捷键，仅把 1.12 固定阴影的文字绘制替换掉。 */
    private static final class NoShadowTextField extends GuiTextField {
        private final net.minecraft.client.gui.FontRenderer font;
        private final int drawX, drawY, drawW, drawH;
        private NoShadowTextField(int id, net.minecraft.client.gui.FontRenderer font, int x, int y, int w, int h) {
            super(id, font, x, y, w, h); this.font = font; this.drawX = x; this.drawY = y; this.drawW = w; this.drawH = h;
        }
        @Override public void drawTextBox() {
            if (!getVisible()) return;
            String visible = font.trimStringToWidth(getText(), Math.max(1, drawW - 2), true);
            int textY = drawY + Math.max(0, (drawH - 8) / 2);
            font.drawString(visible, drawX, textY, CraftTerminalStyle.INPUT_TEXT.toArgb(), false);
            if (isFocused()) Gui.drawRect(drawX + font.getStringWidth(visible) + 1, textY - 1,
                    drawX + font.getStringWidth(visible) + 2, textY + 9,
                    CraftTerminalStyle.INPUT_CURSOR.toArgb());
        }
    }
}
