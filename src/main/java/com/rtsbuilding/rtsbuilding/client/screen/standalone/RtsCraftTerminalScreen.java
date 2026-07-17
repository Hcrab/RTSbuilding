package com.rtsbuilding.rtsbuilding.client.screen.standalone;

import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.record.StorageEntry;
import com.rtsbuilding.rtsbuilding.client.util.RtsClientUiUtil;
import com.rtsbuilding.rtsbuilding.client.widget.Scrollbar;
import com.rtsbuilding.rtsbuilding.network.craft.C2SRtsClearCraftingGridPayload;
import com.rtsbuilding.rtsbuilding.network.storage.C2SRtsBulkStorageOpPayload;
import com.rtsbuilding.rtsbuilding.network.storage.C2SRtsImportMenuSlotPayload;
import com.rtsbuilding.rtsbuilding.network.storage.C2SRtsLinkedPickupPayload;
import com.rtsbuilding.rtsbuilding.network.storage.C2SRtsReturnCarriedPayload;
import com.rtsbuilding.rtsbuilding.network.storage.RtsStorageSort;
import com.rtsbuilding.rtsbuilding.server.menu.RtsCraftTerminalMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * RTS crafting terminal screen — AE2-style layout.
 */
public final class RtsCraftTerminalScreen extends AbstractContainerScreen<RtsCraftTerminalMenu> {

    static final int TERMINAL_W = 195;
    static final int HEADER_H = 17;
    static final int ROW_H = 18;
    static final int ROWS = 6;
    static final int COLS = 9;
    static final int SLOT = 18;
    static final int GRID_LEFT = 7;
    static final int GRID_TOP = HEADER_H;
    static final int STORAGE_H = HEADER_H + ROWS * ROW_H;
    static final int CRAFT_H = 84;
    static final int TERMINAL_H = STORAGE_H + CRAFT_H;
    static final int SCREEN_H = TERMINAL_H + 4 + 90;

    private static final int SEARCH_X = 80, SEARCH_Y = 4, SEARCH_W = 89, SEARCH_H = 12;
    private static final int TB_W = 15, TB_H = 13;
    private static final int SORT_BTN_Y = 1, DIR_BTN_Y = 16;
    private static final int SB_X = 175, SB_Y = HEADER_H;
    private static final int CRAFT_BASE = STORAGE_H;
    private static final int GRID_L = 26, GRID_T = CRAFT_BASE + 18;
    private static final int RESULT_X = 134, RESULT_Y = CRAFT_BASE + 23;
    private static final int CLR_Y = TERMINAL_H - 18;
    private static final int CLR_S_X = 81, CLR_P_X = 91, CLR_W = 8, CLR_H = 12;

    // Colours
    private static final int C_HEADER    = 0xC8212E3D, C_ROW_EVEN = 0xC8141922, C_ROW_ODD = 0xC81A202D;
    private static final int C_CRAFT_BG  = 0xC810151B;
    private static final int C_BORDER_LT = 0xFF5A6E88, C_BORDER_RB = 0xFF0D1218;
    private static final int C_SEPARATOR = 0xFF0F151D, C_SEP_HI = 0xFF4A5E76;
    private static final int C_HOVER     = 0x883A4E65, C_BTN_BG = 0xAA2B3642, C_BTN_HOVER = 0xAA3F5268;
    private static final int C_TEXT      = 0xFFEAF2FF, C_SEARCH_BG = 0xAA1E2731;

    private EditBox searchBox;
    private final Scrollbar scrollbar = new Scrollbar();
    private int scrollRow;
    private long lastStorageRevision = -1;

    public RtsCraftTerminalScreen(RtsCraftTerminalMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = TERMINAL_W;
        this.imageHeight = SCREEN_H;
        this.inventoryLabelY = TERMINAL_H + 4;
        this.titleLabelX = 8;
        this.titleLabelY = 6;
    }

    @Override
    protected void init() {
        super.init();
        int sx = this.leftPos + SEARCH_X + 2, sy = this.topPos + SEARCH_Y + 2;
        this.searchBox = new EditBox(this.font, sx, sy, SEARCH_W - 12, 8, Component.literal("Search"));
        this.searchBox.setBordered(false);
        this.searchBox.setCanLoseFocus(true);
        this.searchBox.setTextColor(0xEAF2FF);
        this.searchBox.setTextColorUneditable(0x8899AA);
        this.searchBox.setResponder(v -> ClientRtsController.get().setStorageSearch(v == null ? "" : v));
        this.addRenderableWidget(this.searchBox);
        ClientRtsController.get().setStorageSearch("");
        this.scrollbar.setPosition(this.leftPos + SB_X, this.topPos + SB_Y);
        this.scrollbar.setHeight(ROWS * ROW_H);
        updateScrollbarRange();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        syncSearch();
        this.renderBackground(g, mx, my, pt);
        super.render(g, mx, my, pt);
        int l = this.leftPos, t = this.topPos;
        renderCraftingOverlays(g, l, t, mx, my);
        int idx = resolveStorageSlotIndex(mx, my);
        List<StorageEntry> entries = ClientRtsController.get().getStorageEntries();
        if (idx >= 0 && idx < entries.size())
            g.renderTooltip(this.font, entries.get(idx).stack(), mx, my);
        this.renderTooltip(g, mx, my);
        // Refresh scrollbar when storage data changes
        int rev = ClientRtsController.get().getStorageRevision();
        if (rev != lastStorageRevision) { lastStorageRevision = rev; updateScrollbarRange(); }
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        int l = this.leftPos, t = this.topPos;
        renderFrame(g, l, t);
        renderStorage(g, l, t, mx, my);
        scrollbar.render(g);
        renderCraftingBg(g, l, t);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        g.drawString(this.font, "RTS Craft Terminal", this.titleLabelX, this.titleLabelY, C_TEXT, false);
        g.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, 0xFF404040, false);
    }

    // ── Background ─────────────────────────────────────────────────────

    private void renderFrame(GuiGraphics g, int l, int t) {
        g.fill(l, t, l + TERMINAL_W, t + HEADER_H, C_HEADER);
        g.hLine(l, l + TERMINAL_W - 1, t + HEADER_H, C_SEPARATOR);
        for (int r = 0; r < ROWS; r++)
            g.fill(l, t + HEADER_H + r * ROW_H, l + TERMINAL_W, t + HEADER_H + (r + 1) * ROW_H,
                    (r & 1) == 0 ? C_ROW_EVEN : C_ROW_ODD);
        rect(g, l, t, TERMINAL_W, TERMINAL_H, C_BORDER_LT, C_BORDER_RB);
        // Search box bg
        g.fill(l + SEARCH_X, t + SEARCH_Y, l + SEARCH_X + SEARCH_W, t + SEARCH_Y + SEARCH_H, C_SEARCH_BG);
        rect(g, l + SEARCH_X, t + SEARCH_Y, SEARCH_W, SEARCH_H, C_SEP_HI, C_BORDER_RB);
        if (!this.searchBox.getValue().isEmpty())
            g.drawString(this.font, "x", l + SEARCH_X + SEARCH_W - 10 + 3, t + SEARCH_Y + 2, 0xFF8899BB, false);
    }

    // ── Storage grid (with count overlay) ──────────────────────────────

    private void renderStorage(GuiGraphics g, int l, int t, int mx, int my) {
        List<StorageEntry> entries = ClientRtsController.get().getStorageEntries();
        int totalRows = Math.max(1, (entries.size() + COLS - 1) / COLS);
        int start = scrollRow * COLS;
        int maxScroll = Math.max(0, totalRows - ROWS);
        scrollbar.setRange(0, Math.max(0, maxScroll), Math.max(1, ROWS / 2));

        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                int ei = start + row * COLS + col;
                int sx = l + GRID_LEFT + col * SLOT;
                int sy = t + GRID_TOP + row * ROW_H;
                if (inside(mx, my, sx, sy, SLOT, SLOT))
                    g.fill(sx, sy, sx + SLOT, sy + SLOT, C_HOVER);
                if (ei >= entries.size()) continue;
                StorageEntry e = entries.get(ei);
                g.renderItem(e.stack(), sx + 1, sy + 1);
                // Draw count overlay
                RtsClientUiUtil.drawSlotCountOverlay(g, this.font, sx, sy, SLOT,
                        RtsClientUiUtil.compactCount(e.count()), 0xFFE8F4FF);
            }
        }
    }

    private void updateScrollbarRange() {
        var entries = ClientRtsController.get().getStorageEntries();
        int tRows = Math.max(1, (entries.size() + COLS - 1) / COLS);
        scrollbar.setRange(0, Math.max(0, tRows - ROWS), Math.max(1, ROWS / 2));
    }

    // ── Crafting area (background, before slots) ───────────────────────

    private void renderCraftingBg(GuiGraphics g, int l, int t) {
        int cy = t + STORAGE_H;
        g.fill(l, cy, l + TERMINAL_W, cy + CRAFT_H, C_CRAFT_BG);
        g.hLine(l, l + TERMINAL_W, cy - 1, C_SEPARATOR);
        g.hLine(l, l + TERMINAL_W, cy, C_SEP_HI);
    }

    // ── Crafting overlays (on top of slots) ────────────────────────────

    private void renderCraftingOverlays(GuiGraphics g, int l, int t, int mx, int my) {
        // Grid decorative frame
        rect(g, l + GRID_L - 3, t + GRID_T - 3, 62, 62, C_BORDER_LT, C_BORDER_RB);
        // Result slot decorative frame
        rect(g, l + RESULT_X - 2, t + RESULT_Y - 2, 22, 22, C_BORDER_LT, C_BORDER_RB);
        // Only render local preview when result slot is empty (parent handles it otherwise)
        Slot rs = this.menu.getSlot(0);
        if (rs != null && rs.getItem().isEmpty()) {
            ItemStack local = computeLocalResult();
            if (!local.isEmpty()) {
                g.renderItem(local, l + RESULT_X + 2, t + RESULT_Y + 2);
                g.renderItemDecorations(this.font, local, l + RESULT_X + 2, t + RESULT_Y + 2);
            }
        }
        // Clear buttons with translatable tooltips
        boolean hs = inside(mx, my, l + CLR_S_X, t + CLR_Y, CLR_W, CLR_H);
        rect(g, l + CLR_S_X, t + CLR_Y, CLR_W, CLR_H, C_BORDER_LT, C_BORDER_RB);
        g.fill(l + CLR_S_X + 1, t + CLR_Y + 1, l + CLR_S_X + CLR_W - 1, t + CLR_Y + CLR_H - 1, hs ? C_BTN_HOVER : C_BTN_BG);
        if (hs) g.renderTooltip(this.font, Component.translatable("screen.rtsbuilding.clear_to_storage"), mx, my);

        boolean hp = inside(mx, my, l + CLR_P_X, t + CLR_Y, CLR_W, CLR_H);
        rect(g, l + CLR_P_X, t + CLR_Y, CLR_W, CLR_H, C_BORDER_LT, C_BORDER_RB);
        g.fill(l + CLR_P_X + 1, t + CLR_Y + 1, l + CLR_P_X + CLR_W - 1, t + CLR_Y + CLR_H - 1, hp ? C_BTN_HOVER : C_BTN_BG);
        if (hp) g.renderTooltip(this.font, Component.translatable("screen.rtsbuilding.clear_to_player"), mx, my);

        renderToolbarBtn(g, l - TB_W - 3, t + SORT_BTN_Y, sortLabel(), mx, my);
        renderToolbarBtn(g, l - TB_W - 3, t + DIR_BTN_Y, dirLabel(), mx, my);
    }

    private void renderToolbarBtn(GuiGraphics g, int x, int y, String label, int mx, int my) {
        boolean hv = inside(mx, my, x, y, TB_W, TB_H);
        rect(g, x, y, TB_W, TB_H, C_BORDER_LT, C_BORDER_RB);
        g.fill(x + 1, y + 1, x + TB_W - 1, y + TB_H - 1, hv ? C_BTN_HOVER : C_BTN_BG);
        g.drawCenteredString(this.font, label, x + TB_W / 2, y + (TB_H - 8) / 2, C_TEXT);
    }

    private ItemStack computeLocalResult() {
        if (this.minecraft == null || this.minecraft.level == null) return ItemStack.EMPTY;
        List<ItemStack> in = new java.util.ArrayList<>(9);
        boolean any = false;
        for (int i = 0; i < 9; i++) {
            Slot s = this.menu.getSlot(1 + i);
            ItemStack st = s == null ? ItemStack.EMPTY : s.getItem();
            in.add(st.isEmpty() ? ItemStack.EMPTY : st.copyWithCount(1));
            if (!st.isEmpty()) any = true;
        }
        if (!any) return ItemStack.EMPTY;
        CraftingInput ci = CraftingInput.of(3, 3, in);
        var rh = this.minecraft.level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, ci, this.minecraft.level);
        if (rh.isEmpty()) return ItemStack.EMPTY;
        ItemStack r = rh.get().value().assemble(ci, this.minecraft.level.registryAccess());
        if (r.isEmpty()) r = rh.get().value().getResultItem(this.minecraft.level.registryAccess());
        return r.isEmpty() ? ItemStack.EMPTY : r.copy();
    }

    // ── Mouse ──────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        int l = this.leftPos, t = this.topPos;
        if (scrollbar.mouseClicked(mx, my, btn)) return true;
        boolean space = Screen.hasControlDown() || isSpaceDown();

        if (btn == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (inside(mx, my, l + CLR_S_X, t + CLR_Y, CLR_W, CLR_H)) {
                PacketDistributor.sendToServer(new C2SRtsClearCraftingGridPayload(false)); return true;
            }
            if (inside(mx, my, l + CLR_P_X, t + CLR_Y, CLR_W, CLR_H)) {
                PacketDistributor.sendToServer(new C2SRtsClearCraftingGridPayload(true)); return true;
            }
            if (inside(mx, my, l - TB_W - 3, t + SORT_BTN_Y, TB_W, TB_H)) {
                ClientRtsController.get().cycleSort(); return true;
            }
            if (inside(mx, my, l - TB_W - 3, t + DIR_BTN_Y, TB_W, TB_H)) {
                ClientRtsController.get().toggleSortDirection(); return true;
            }
        }

        // Search
        if (inside(mx, my, l + SEARCH_X + SEARCH_W - 10, t + SEARCH_Y, 10, SEARCH_H)) {
            this.searchBox.setValue(""); ClientRtsController.get().setStorageSearch("");
            this.searchBox.setFocused(true); this.setFocused(this.searchBox); return true;
        }
        if (inside(mx, my, l + SEARCH_X, t + SEARCH_Y, SEARCH_W, SEARCH_H)) {
            this.searchBox.setFocused(true); this.setFocused(this.searchBox); return true;
        }

        // Shift+click slot → import to linked storage
        if (Screen.hasShiftDown() && (btn == 0 || btn == 1)) {
            Slot hovered = this.getSlotUnderMouse();
            if (hovered != null && hovered.hasItem()) {
                int si = this.menu.slots.indexOf(hovered);
                if (si >= 0) { PacketDistributor.sendToServer(new C2SRtsImportMenuSlotPayload(si)); return true; }
            }
        }

        // Space+click on player inventory / hotbar → bulk deposit
        if (space && btn == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            Slot hs = this.getSlotUnderMouse();
            if (hs != null && hs.container == this.menu.getSlot(10).container) {
                // Check if it's hotbar (slots 37-45 in menu) or main inventory (slots 10-36)
                int menuIdx = this.menu.slots.indexOf(hs);
                if (menuIdx >= 37 && menuIdx <= 45) {
                    PacketDistributor.sendToServer(new C2SRtsBulkStorageOpPayload((byte)2, "", 0));
                    return true;
                } else if (menuIdx >= 10 && menuIdx <= 36) {
                    PacketDistributor.sendToServer(new C2SRtsBulkStorageOpPayload((byte)1, "", 0));
                    return true;
                }
            }
        }

        // Storage grid clicks
        if (btn == 0 || btn == 1) {
            if (this.searchBox.isFocused() && !inside(mx, my, l + SEARCH_X, t + SEARCH_Y, SEARCH_W, SEARCH_H)) {
                this.searchBox.setFocused(false); this.setFocused(null);
            }
            int idx = resolveStorageSlotIndex(mx, my);
            if (idx >= 0) {
                var entries = ClientRtsController.get().getStorageEntries();
                if (idx < entries.size()) {
                    StorageEntry entry = entries.get(idx);
                    if (space && btn == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                        // Space+click: take all to inventory
                        int total = (int) Math.min(entry.count(), Integer.MAX_VALUE);
                        PacketDistributor.sendToServer(new C2SRtsBulkStorageOpPayload(
                                (byte)0, entry.itemId(), total));
                        return true;
                    }
                    if (!this.menu.getCarried().isEmpty()) {
                        // Carried item → deposit
                        returnCarried(btn == 1 ? 1 : Integer.MAX_VALUE);
                    } else {
                        // Pickup
                        pickUp(entry, btn == 1 ? 1 : Integer.MAX_VALUE);
                    }
                    return true;
                }
            }
            // Click empty grid area with carried item → deposit
            if (!this.menu.getCarried().isEmpty() && isInsideGrid(mx, my)) {
                returnCarried(btn == 1 ? 1 : Integer.MAX_VALUE);
                return true;
            }
        }

        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (scrollbar.mouseDragged(mx, my, btn)) { this.scrollRow = scrollbar.getCurrentScroll(); return true; }
        return super.mouseDragged(mx, my, btn, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int btn) {
        scrollbar.mouseReleased(mx, my, btn);
        return super.mouseReleased(mx, my, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (isInsideGrid(mx, my) || inside(mx, my, this.leftPos + SB_X, this.topPos + SB_Y, 12, ROWS * ROW_H)) {
            scrollbar.mouseScrolled(mx, my, sx, sy); this.scrollRow = scrollbar.getCurrentScroll(); return true;
        }
        if (inside(mx, my, this.leftPos, this.topPos, TERMINAL_W, TERMINAL_H)) {
            scrollbar.mouseScrolled(mx, my, sx, sy); this.scrollRow = scrollbar.getCurrentScroll(); return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mod) {
        if (this.searchBox.isFocused()) {
            if (key == GLFW.GLFW_KEY_ESCAPE) { this.searchBox.setValue(""); this.searchBox.setFocused(false); this.setFocused(null); return true; }
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) { this.searchBox.setFocused(false); this.setFocused(null); return true; }
            this.searchBox.keyPressed(key, scan, mod); return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) { this.onClose(); return true; }
        return super.keyPressed(key, scan, mod);
    }

    @Override
    public boolean charTyped(char cp, int mod) {
        if (this.searchBox.isFocused()) { this.searchBox.charTyped(cp, mod); return true; }
        return super.charTyped(cp, mod);
    }

    // ── JEI helpers ────────────────────────────────────────────────────

    public Rect2i getTerminalArea() { return new Rect2i(this.leftPos, this.topPos, TERMINAL_W, TERMINAL_H); }

    public StorageEntry getStorageEntryAt(double mx, double my) {
        int i = resolveStorageSlotIndex(mx, my);
        if (i < 0) return null;
        var e = ClientRtsController.get().getStorageEntries();
        return i < e.size() ? e.get(i) : null;
    }

    public Rect2i getStorageSlotAreaAt(double mx, double my) {
        for (int r = 0; r < ROWS; r++)
            for (int c = 0; c < COLS; c++) {
                int sx = this.leftPos + GRID_LEFT + c * SLOT, sy = this.topPos + GRID_TOP + r * ROW_H;
                if (inside(mx, my, sx, sy, SLOT, SLOT)) return new Rect2i(sx, sy, SLOT, SLOT);
            }
        return null;
    }

    // ── Storage interaction ────────────────────────────────────────────

    private void pickUp(StorageEntry e, int amt) {
        if (e == null || e.stack().isEmpty()) return;
        ItemStack c = this.menu.getCarried();
        int want;
        if (c.isEmpty()) want = Math.min(amt, e.stack().getMaxStackSize());
        else {
            if (!ItemStack.isSameItemSameComponents(c, e.stack())) return;
            want = Math.min(amt, c.getMaxStackSize() - c.getCount());
        }
        if (want <= 0) return;
        applyPreview(e.stack(), want);
        ItemStack r = e.stack().copy(); r.setCount(1);
        PacketDistributor.sendToServer(new C2SRtsLinkedPickupPayload(r, want));
    }

    private void returnCarried(int amt) {
        ItemStack c = this.menu.getCarried();
        if (c.isEmpty()) return;
        var id = BuiltInRegistries.ITEM.getKey(c.getItem());
        if (id == null) return;
        int a = Math.max(1, Math.min(amt, c.getCount()));
        ItemStack toSend = c.copyWithCount(a);
        PacketDistributor.sendToServer(new C2SRtsReturnCarriedPayload(id.toString(), a, toSend));
        c.shrink(a);
        this.menu.setCarried(c.isEmpty() ? ItemStack.EMPTY : c);
    }

    private void applyPreview(ItemStack proto, int want) {
        ItemStack c = this.menu.getCarried();
        if (c.isEmpty()) {
            ItemStack p = proto.copy(); p.setCount(Math.min(want, p.getMaxStackSize())); this.menu.setCarried(p);
        } else if (ItemStack.isSameItemSameComponents(c, proto)) {
            int g = Math.min(want, c.getMaxStackSize() - c.getCount());
            if (g > 0) { c.grow(g); this.menu.setCarried(c); }
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────

    private int resolveStorageSlotIndex(double mx, double my) {
        for (int r = 0; r < ROWS; r++)
            for (int c = 0; c < COLS; c++) {
                int sx = this.leftPos + GRID_LEFT + c * SLOT, sy = this.topPos + GRID_TOP + r * ROW_H;
                if (inside(mx, my, sx, sy, SLOT, SLOT)) return scrollRow * COLS + r * COLS + c;
            }
        return -1;
    }

    private boolean isInsideGrid(double mx, double my) {
        return inside(mx, my, this.leftPos + GRID_LEFT, this.topPos + GRID_TOP, COLS * SLOT, ROWS * ROW_H);
    }

    private void syncSearch() {
        if (this.searchBox == null || this.searchBox.isFocused()) return;
        String exp = ClientRtsController.get().getStorageSearch();
        if (exp == null) exp = "";
        if (!exp.equals(this.searchBox.getValue())) this.searchBox.setValue(exp);
    }

    private String sortLabel() {
        return switch (ClientRtsController.get().getStorageSort()) {
            case QUANTITY -> "Q"; case MOD -> "M"; case NAME -> "N";
        };
    }

    private String dirLabel() { return ClientRtsController.get().isStorageSortAscending() ? "A" : "D"; }

    private static boolean isSpaceDown() {
        long w = net.minecraft.client.Minecraft.getInstance().getWindow().getWindow();
        return org.lwjgl.glfw.GLFW.glfwGetKey(w, GLFW.GLFW_KEY_SPACE) == GLFW.GLFW_PRESS;
    }

    private static void rect(GuiGraphics g, int x, int y, int w, int h, int lt, int rb) {
        g.hLine(x, x + w - 1, y, lt); g.vLine(x, y, y + h - 1, lt);
        g.hLine(x, x + w - 1, y + h - 1, rb); g.vLine(x + w - 1, y, y + h - 1, rb);
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }
}
