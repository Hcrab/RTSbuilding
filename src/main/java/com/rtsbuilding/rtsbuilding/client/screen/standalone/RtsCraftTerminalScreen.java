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
 * RTS 合成终端界面 — AE2 风格布局。
 */
public final class RtsCraftTerminalScreen extends AbstractContainerScreen<RtsCraftTerminalMenu> {

    static final int TERMINAL_W = 195;
    static final int HEADER_H = 17;
    static final int ROW_H = 18;
    static final int MIN_ROWS = 3;
    static final int MAX_ROWS = 6;
    static final int COLS = 9;
    static final int SLOT = 18;
    static final int GRID_LEFT = 7;
    static final int GRID_TOP = HEADER_H;
    static final int CRAFT_H = 84;

    // 搜索栏布局（宽度缩小为模式/固定按钮留空间）
    private static final int SEARCH_X = 80, SEARCH_Y = 4, SEARCH_W = 60, SEARCH_H = 12;
    // 搜索模式按钮
    private static final int MODE_BTN_X = 144, MODE_BTN_Y = 3, MODE_BTN_W = 10, MODE_BTN_H = 12;
    // 保留搜索按钮
    private static final int PIN_BTN_X = 155, PIN_BTN_Y = 3, PIN_BTN_W = 10, PIN_BTN_H = 12;
    // 高度 +/- 按钮
    private static final int ROW_PLUS_X = 168, ROW_MINUS_X = 168;
    private static final int ROW_PLUS_Y = 2, ROW_MINUS_Y = 10;
    private static final int ROW_BTN_W = 8, ROW_BTN_H = 6;

    private static final int TB_W = 15, TB_H = 13;
    private static final int SORT_BTN_Y = 1, DIR_BTN_Y = 16;
    private static final int SB_X = 175;

    // Colours
    private static final int C_HEADER    = 0xC8212E3D, C_ROW_EVEN = 0xC8141922, C_ROW_ODD = 0xC81A202D;
    private static final int C_CRAFT_BG  = 0xC810151B;
    private static final int C_BORDER_LT = 0xFF5A6E88, C_BORDER_RB = 0xFF0D1218;
    private static final int C_SEPARATOR = 0xFF0F151D, C_SEP_HI = 0xFF4A5E76;
    private static final int C_HOVER     = 0x883A4E65, C_BTN_BG = 0xAA2B3642, C_BTN_HOVER = 0xAA3F5268;
    private static final int C_TEXT      = 0xFFEAF2FF, C_SEARCH_BG = 0xAA1E2731;

    /** 合成终端可见存储行数（3~6） */
    private int rowCount;
    /** 由 rowCount 动态计算的布局尺寸 */
    private int storageH;
    private int terminalH;
    private int screenH;
    private int craftBase;
    private int gridT;
    private int resultY;
    private int clrY;

    private EditBox searchBox;
    private final Scrollbar scrollbar = new Scrollbar();
    private int scrollRow;
    private long lastStorageRevision = -1;
    /** 上一次同步给 JEI 的搜索文本（避免循环同步） */
    private String lastSyncedJeiSearch = "";

    public RtsCraftTerminalScreen(RtsCraftTerminalMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.rowCount = ClientRtsController.get().getCraftTerminalRows();
        recalcDimensions();
        this.imageWidth = TERMINAL_W;
        this.imageHeight = this.screenH;
        this.inventoryLabelY = this.terminalH + 4;
        this.titleLabelX = 8;
        this.titleLabelY = 6;
    }

    /** 根据当前 rowCount 重新计算所有动态布局常量 */
    private void recalcDimensions() {
        this.storageH = HEADER_H + this.rowCount * ROW_H;
        this.terminalH = this.storageH + CRAFT_H;
        this.screenH = this.terminalH + 4 + 90;
        this.craftBase = this.storageH;
        this.gridT = this.craftBase + 18;
        this.resultY = this.craftBase + 23;
        this.clrY = this.terminalH - 18;
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
        this.searchBox.setResponder(v -> {
            String val = v == null ? "" : v;
            ClientRtsController.get().setStorageSearch(val);
            syncSearchToJei(val);
        });
        this.addRenderableWidget(this.searchBox);

        // 恢复搜索文本（固定模式或保留模式时不清空）
        if (!ClientRtsController.get().isCraftTerminalSearchPinned()) {
            ClientRtsController.get().setStorageSearch("");
        } else {
            String saved = ClientRtsController.get().getStorageSearch();
            if (saved != null && !saved.isEmpty()) {
                this.searchBox.setValue(saved);
            }
        }

        this.scrollbar.setPosition(this.leftPos + SB_X, this.topPos + HEADER_H);
        this.scrollbar.setHeight(this.rowCount * ROW_H);
        updateScrollbarRange();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        syncSearch();
        syncSearchFromJei();
        this.renderBackground(g, mx, my, pt);
        super.render(g, mx, my, pt);
        int l = this.leftPos, t = this.topPos;
        renderCraftingOverlays(g, l, t, mx, my);
        int idx = resolveStorageSlotIndex(mx, my);
        List<StorageEntry> entries = ClientRtsController.get().getStorageEntries();
        if (idx >= 0 && idx < entries.size())
            g.renderTooltip(this.font, entries.get(idx).stack(), mx, my);
        // 新增按钮 tooltip
        renderButtonTooltips(g, l, t, mx, my);
        this.renderTooltip(g, mx, my);
        int rev = ClientRtsController.get().getStorageRevision();
        if (rev != lastStorageRevision) { lastStorageRevision = rev; updateScrollbarRange(); }
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        int l = this.leftPos, t = this.topPos;
        renderFrame(g, l, t, mx, my);
        renderStorage(g, l, t, mx, my);
        scrollbar.render(g);
        renderCraftingBg(g, l, t);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        g.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, C_TEXT, false);
        g.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, 0xFF404040, false);
    }

    // ── Background ─────────────────────────────────────────────────────

    private void renderFrame(GuiGraphics g, int l, int t, int mx, int my) {
        g.fill(l, t, l + TERMINAL_W, t + HEADER_H, C_HEADER);
        g.hLine(l, l + TERMINAL_W - 1, t + HEADER_H, C_SEPARATOR);
        for (int r = 0; r < this.rowCount; r++)
            g.fill(l, t + HEADER_H + r * ROW_H, l + TERMINAL_W, t + HEADER_H + (r + 1) * ROW_H,
                    (r & 1) == 0 ? C_ROW_EVEN : C_ROW_ODD);
        rect(g, l, t, TERMINAL_W, this.terminalH, C_BORDER_LT, C_BORDER_RB);

        // 搜索框背景
        g.fill(l + SEARCH_X, t + SEARCH_Y, l + SEARCH_X + SEARCH_W, t + SEARCH_Y + SEARCH_H, C_SEARCH_BG);
        rect(g, l + SEARCH_X, t + SEARCH_Y, SEARCH_W, SEARCH_H, C_SEP_HI, C_BORDER_RB);
        if (!this.searchBox.getValue().isEmpty())
            g.drawString(this.font, "x", l + SEARCH_X + SEARCH_W - 10 + 1, t + SEARCH_Y + 2, 0xFF8899BB, false);

        // 搜索模式按钮
        boolean modeHv = insideGui(mx, my, l + MODE_BTN_X, t + MODE_BTN_Y, MODE_BTN_W, MODE_BTN_H);
        rect(g, l + MODE_BTN_X, t + MODE_BTN_Y, MODE_BTN_W, MODE_BTN_H, C_BORDER_LT, C_BORDER_RB);
        g.fill(l + MODE_BTN_X + 1, t + MODE_BTN_Y + 1, l + MODE_BTN_X + MODE_BTN_W - 1,
                t + MODE_BTN_Y + MODE_BTN_H - 1, modeHv ? C_BTN_HOVER : C_BTN_BG);
        String modeLabel = searchModeLabel();
        if (!modeLabel.isEmpty())
            g.drawCenteredString(this.font, modeLabel, l + MODE_BTN_X + MODE_BTN_W / 2,
                    t + MODE_BTN_Y + 1, C_TEXT);

        // 保留搜索按钮
        boolean pinHv = insideGui(mx, my, l + PIN_BTN_X, t + PIN_BTN_Y, PIN_BTN_W, PIN_BTN_H);
        rect(g, l + PIN_BTN_X, t + PIN_BTN_Y, PIN_BTN_W, PIN_BTN_H, C_BORDER_LT, C_BORDER_RB);
        boolean pinned = ClientRtsController.get().isCraftTerminalSearchPinned();
        g.fill(l + PIN_BTN_X + 1, t + PIN_BTN_Y + 1, l + PIN_BTN_X + PIN_BTN_W - 1,
                t + PIN_BTN_Y + PIN_BTN_H - 1, pinned ? C_BTN_HOVER : (pinHv ? C_BTN_HOVER : C_BTN_BG));
        g.drawCenteredString(this.font, "*", l + PIN_BTN_X + PIN_BTN_W / 2,
                t + PIN_BTN_Y + 1, pinned ? C_TEXT : 0xFF556677);

        // 高度 +/- 按钮
        renderRowBtn(g, l + ROW_PLUS_X, t + ROW_PLUS_Y, "+", this.rowCount < MAX_ROWS);
        renderRowBtn(g, l + ROW_MINUS_X, t + ROW_MINUS_Y, "-", this.rowCount > MIN_ROWS);
    }

    private void renderRowBtn(GuiGraphics g, int x, int y, String label, boolean enabled) {
        rect(g, x, y, ROW_BTN_W, ROW_BTN_H, C_BORDER_LT, C_BORDER_RB);
        g.fill(x + 1, y + 1, x + ROW_BTN_W - 1, y + ROW_BTN_H - 1, enabled ? C_BTN_BG : 0xAA121820);
        int color = enabled ? C_TEXT : 0xFF555555;
        g.drawCenteredString(this.font, label, x + ROW_BTN_W / 2, y - 1, color);
    }

    /** 渲染搜索模式/固定/高度按钮的 tooltip */
    private void renderButtonTooltips(GuiGraphics g, int l, int t, int mx, int my) {
        if (insideGui(mx, my, l + MODE_BTN_X, t + MODE_BTN_Y, MODE_BTN_W, MODE_BTN_H)) {
            String key = switch (ClientRtsController.get().getCraftTerminalSearchMode()) {
                case AUTO_SEARCH -> "screen.rtsbuilding.craft_terminal.search_mode.auto";
                case JEI_AUTO_SEARCH -> "screen.rtsbuilding.craft_terminal.search_mode.jei_auto";
                default -> "screen.rtsbuilding.craft_terminal.search_mode.standard";
            };
            g.renderTooltip(this.font, Component.translatable(key), mx, my);
        }
        if (insideGui(mx, my, l + PIN_BTN_X, t + PIN_BTN_Y, PIN_BTN_W, PIN_BTN_H)) {
            String pinKey = ClientRtsController.get().isCraftTerminalSearchPinned()
                    ? "screen.rtsbuilding.craft_terminal.search_pin.on"
                    : "screen.rtsbuilding.craft_terminal.search_pin.off";
            g.renderTooltip(this.font, Component.translatable("screen.rtsbuilding.craft_terminal.search_pin")
                    .append(": ").append(Component.translatable(pinKey)), mx, my);
        }
    }

    // ── Storage grid ──────────────────────────────────────────────

    private void renderStorage(GuiGraphics g, int l, int t, int mx, int my) {
        List<StorageEntry> entries = ClientRtsController.get().getStorageEntries();
        int totalRows = Math.max(1, (entries.size() + COLS - 1) / COLS);
        int start = scrollRow * COLS;
        int maxScroll = Math.max(0, totalRows - this.rowCount);
        scrollbar.setRange(0, Math.max(0, maxScroll), Math.max(1, this.rowCount / 2));

        for (int row = 0; row < this.rowCount; row++) {
            for (int col = 0; col < COLS; col++) {
                int ei = start + row * COLS + col;
                int sx = l + GRID_LEFT + col * SLOT;
                int sy = t + GRID_TOP + row * ROW_H;
                if (insideGui(mx, my, sx, sy, SLOT, SLOT))
                    g.fill(sx, sy, sx + SLOT, sy + SLOT, C_HOVER);
                if (ei >= entries.size()) continue;
                StorageEntry e = entries.get(ei);
                g.renderItem(e.stack(), sx + 1, sy + 1);
                RtsClientUiUtil.drawSlotCountOverlay(g, this.font, sx, sy, SLOT,
                        RtsClientUiUtil.compactCount(e.count()), 0xFFE8F4FF);
            }
        }
    }

    private void updateScrollbarRange() {
        var entries = ClientRtsController.get().getStorageEntries();
        int tRows = Math.max(1, (entries.size() + COLS - 1) / COLS);
        scrollbar.setRange(0, Math.max(0, tRows - this.rowCount), Math.max(1, this.rowCount / 2));
    }

    // ── Crafting area ───────────────────────────────────────────────

    private void renderCraftingBg(GuiGraphics g, int l, int t) {
        int cy = t + this.storageH;
        g.fill(l, cy, l + TERMINAL_W, cy + CRAFT_H, C_CRAFT_BG);
        g.hLine(l, l + TERMINAL_W, cy - 1, C_SEPARATOR);
        g.hLine(l, l + TERMINAL_W, cy, C_SEP_HI);
    }

    private void renderCraftingOverlays(GuiGraphics g, int l, int t, int mx, int my) {
        rect(g, l + 23, t + this.gridT - 3, 62, 62, C_BORDER_LT, C_BORDER_RB);
        rect(g, l + 132, t + this.resultY - 2, 22, 22, C_BORDER_LT, C_BORDER_RB);

        Slot rs = this.menu.getSlot(0);
        if (rs != null && rs.getItem().isEmpty()) {
            ItemStack local = computeLocalResult();
            if (!local.isEmpty()) {
                g.renderItem(local, l + 134 + 2, t + this.resultY + 2);
                g.renderItemDecorations(this.font, local, l + 134 + 2, t + this.resultY + 2);
            }
        }

        // 清空按钮
        boolean hs = insideGui(mx, my, l + 81, t + this.clrY, 8, 12);
        rect(g, l + 81, t + this.clrY, 8, 12, C_BORDER_LT, C_BORDER_RB);
        g.fill(l + 82, t + this.clrY + 1, l + 89 - 1, t + this.clrY + 12 - 1, hs ? C_BTN_HOVER : C_BTN_BG);
        if (hs) g.renderTooltip(this.font, Component.translatable("screen.rtsbuilding.clear_to_storage"), mx, my);

        boolean hp = insideGui(mx, my, l + 91, t + this.clrY, 8, 12);
        rect(g, l + 91, t + this.clrY, 8, 12, C_BORDER_LT, C_BORDER_RB);
        g.fill(l + 92, t + this.clrY + 1, l + 99 - 1, t + this.clrY + 12 - 1, hp ? C_BTN_HOVER : C_BTN_BG);
        if (hp) g.renderTooltip(this.font, Component.translatable("screen.rtsbuilding.clear_to_player"), mx, my);

        renderToolbarBtn(g, l - TB_W - 3, t + SORT_BTN_Y, sortLabel(), mx, my);
        renderToolbarBtn(g, l - TB_W - 3, t + DIR_BTN_Y, dirLabel(), mx, my);
    }

    private void renderToolbarBtn(GuiGraphics g, int x, int y, String label, int mx, int my) {
        boolean hv = insideGui(mx, my, x, y, TB_W, TB_H);
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
            // 清空按钮
            if (insideGui(mx, my, l + 81, t + this.clrY, 8, 12)) {
                PacketDistributor.sendToServer(new C2SRtsClearCraftingGridPayload(false)); return true;
            }
            if (insideGui(mx, my, l + 91, t + this.clrY, 8, 12)) {
                PacketDistributor.sendToServer(new C2SRtsClearCraftingGridPayload(true)); return true;
            }
            // 排序按钮
            if (insideGui(mx, my, l - TB_W - 3, t + SORT_BTN_Y, TB_W, TB_H)) {
                ClientRtsController.get().cycleSort(); return true;
            }
            if (insideGui(mx, my, l - TB_W - 3, t + DIR_BTN_Y, TB_W, TB_H)) {
                ClientRtsController.get().toggleSortDirection(); return true;
            }
            // 搜索模式按钮
            if (insideGui(mx, my, l + MODE_BTN_X, t + MODE_BTN_Y, MODE_BTN_W, MODE_BTN_H)) {
                cycleSearchMode(); return true;
            }
            // 保留搜索按钮
            if (insideGui(mx, my, l + PIN_BTN_X, t + PIN_BTN_Y, PIN_BTN_W, PIN_BTN_H)) {
                ClientRtsController.get().setCraftTerminalSearchPinned(
                        !ClientRtsController.get().isCraftTerminalSearchPinned());
                return true;
            }
            // 高度 +/- 按钮
            if (insideGui(mx, my, l + ROW_PLUS_X, t + ROW_PLUS_Y, ROW_BTN_W, ROW_BTN_H)) {
                if (this.rowCount < MAX_ROWS) changeRowCount(this.rowCount + 1);
                return true;
            }
            if (insideGui(mx, my, l + ROW_MINUS_X, t + ROW_MINUS_Y, ROW_BTN_W, ROW_BTN_H)) {
                if (this.rowCount > MIN_ROWS) changeRowCount(this.rowCount - 1);
                return true;
            }
        }

        // 搜索栏右键 → 清空，不改变焦点状态
        if (btn == GLFW.GLFW_MOUSE_BUTTON_RIGHT
                && insideGui(mx, my, l + SEARCH_X, t + SEARCH_Y, SEARCH_W, SEARCH_H)) {
            boolean wasFocused = this.searchBox.isFocused();
            this.searchBox.setValue("");
            ClientRtsController.get().setStorageSearch("");
            syncSearchToJei("");
            if (wasFocused) {
                this.searchBox.setFocused(true);
                this.setFocused(this.searchBox);
            }
            return true;
        }

        // 搜索栏左键
        if (insideGui(mx, my, l + SEARCH_X, t + SEARCH_Y, SEARCH_W, SEARCH_H)) {
            // 鼠标持有物品 → 填入物品自定义名称
            ItemStack carried = this.menu.getCarried();
            if (btn == GLFW.GLFW_MOUSE_BUTTON_LEFT && !carried.isEmpty()) {
                String name = carried.getHoverName().getString();
                this.searchBox.setValue(name);
                ClientRtsController.get().setStorageSearch(name);
                syncSearchToJei(name);
                this.searchBox.setFocused(true);
                this.setFocused(this.searchBox);
                return true;
            }
            // 清除 "x" 按钮
            if (btn == GLFW.GLFW_MOUSE_BUTTON_LEFT
                    && insideGui(mx, my, l + SEARCH_X + SEARCH_W - 10, t + SEARCH_Y, 10, SEARCH_H)) {
                this.searchBox.setValue("");
                ClientRtsController.get().setStorageSearch("");
                syncSearchToJei("");
                this.searchBox.setFocused(true);
                this.setFocused(this.searchBox);
                return true;
            }
            // 正常聚焦搜索栏
            this.searchBox.setFocused(true);
            this.setFocused(this.searchBox);
            return true;
        }

        // Shift+click 菜单槽 → 导入到关联存储
        if (Screen.hasShiftDown() && (btn == 0 || btn == 1)) {
            Slot hovered = this.getSlotUnderMouse();
            if (hovered != null && hovered.hasItem()) {
                int si = this.menu.slots.indexOf(hovered);
                if (si >= 0) { PacketDistributor.sendToServer(new C2SRtsImportMenuSlotPayload(si)); return true; }
            }
        }

        // Space+click 玩家背包/快捷栏 → 批量存入
        if (space && btn == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            Slot hs = this.getSlotUnderMouse();
            if (hs != null && hs.container == this.menu.getSlot(10).container) {
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

        // 存储网格点击
        if (btn == 0 || btn == 1) {
            if (this.searchBox.isFocused() && !insideGui(mx, my, l + SEARCH_X, t + SEARCH_Y, SEARCH_W, SEARCH_H)) {
                this.searchBox.setFocused(false); this.setFocused(null);
            }
            int idx = resolveStorageSlotIndex(mx, my);
            if (idx >= 0) {
                var entries = ClientRtsController.get().getStorageEntries();
                if (idx < entries.size()) {
                    StorageEntry entry = entries.get(idx);

                    // Shift+左键 → 拿取一组到背包
                    if (Screen.hasShiftDown() && btn == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                        int count = Math.min(entry.stack().getMaxStackSize(),
                                (int) Math.min(entry.count(), Integer.MAX_VALUE));
                        PacketDistributor.sendToServer(new C2SRtsBulkStorageOpPayload(
                                (byte)0, entry.itemId(), count));
                        return true;
                    }

                    // Space+左键 → 拿取全部到背包
                    if (space && btn == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                        int total = (int) Math.min(entry.count(), Integer.MAX_VALUE);
                        PacketDistributor.sendToServer(new C2SRtsBulkStorageOpPayload(
                                (byte)0, entry.itemId(), total));
                        return true;
                    }

                    if (!this.menu.getCarried().isEmpty()) {
                        returnCarried(btn == 1 ? 1 : Integer.MAX_VALUE);
                    } else {
                        pickUp(entry, btn == 1 ? 1 : Integer.MAX_VALUE);
                    }
                    return true;
                }
            }
            // 点击网格空白区域 → 存入手持物品
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
        if (isInsideGrid(mx, my) || insideGui(mx, my, this.leftPos + SB_X, this.topPos + HEADER_H, 12, this.rowCount * ROW_H)) {
            scrollbar.mouseScrolled(mx, my, sx, sy); this.scrollRow = scrollbar.getCurrentScroll(); return true;
        }
        if (insideGui(mx, my, this.leftPos, this.topPos, TERMINAL_W, this.terminalH)) {
            scrollbar.mouseScrolled(mx, my, sx, sy); this.scrollRow = scrollbar.getCurrentScroll(); return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mod) {
        if (this.searchBox.isFocused()) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                this.searchBox.setValue("");
                ClientRtsController.get().setStorageSearch("");
                syncSearchToJei("");
                this.searchBox.setFocused(false);
                this.setFocused(null);
                return true;
            }
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                this.searchBox.setFocused(false); this.setFocused(null); return true;
            }
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

    public Rect2i getTerminalArea() {
        return new Rect2i(this.leftPos, this.topPos, TERMINAL_W, this.terminalH);
    }

    public StorageEntry getStorageEntryAt(double mx, double my) {
        int i = resolveStorageSlotIndex(mx, my);
        if (i < 0) return null;
        var e = ClientRtsController.get().getStorageEntries();
        return i < e.size() ? e.get(i) : null;
    }

    public Rect2i getStorageSlotAreaAt(double mx, double my) {
        for (int r = 0; r < this.rowCount; r++)
            for (int c = 0; c < COLS; c++) {
                int sx = this.leftPos + GRID_LEFT + c * SLOT, sy = this.topPos + GRID_TOP + r * ROW_H;
                if (insideGui(mx, my, sx, sy, SLOT, SLOT)) return new Rect2i(sx, sy, SLOT, SLOT);
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

    // ── Search mode ────────────────────────────────────────────────────

    /** 循环切换搜索模式（跳过 JEI 模式若 JEI 未装） */
    private void cycleSearchMode() {
        var ctrl = ClientRtsController.get();
        var current = ctrl.getCraftTerminalSearchMode();
        var next = current.next();
        // 若 JEI 未加载，跳过 JEI 相关模式
        if (!isJeiAvailable()) {
            while (next == ClientRtsController.CraftTerminalSearchMode.AUTO_SEARCH
                    || next == ClientRtsController.CraftTerminalSearchMode.JEI_AUTO_SEARCH) {
                next = next.next();
            }
        }
        ctrl.setCraftTerminalSearchMode(next);
    }

    private static boolean isJeiAvailable() {
        try {
            return com.rtsbuilding.rtsbuilding.compat.jei.RtsJeiPlugin.getJeiRuntime() != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** 将当前搜索文本同步到 JEI 搜索框（AUTO_SEARCH 模式下） */
    private void syncSearchToJei(String value) {
        if (!isJeiAvailable()) return;
        var mode = ClientRtsController.get().getCraftTerminalSearchMode();
        if (mode != ClientRtsController.CraftTerminalSearchMode.AUTO_SEARCH) return;
        if (value.equals(this.lastSyncedJeiSearch)) return;
        this.lastSyncedJeiSearch = value;
        try {
            var runtime = com.rtsbuilding.rtsbuilding.compat.jei.RtsJeiPlugin.getJeiRuntime();
            if (runtime != null && runtime.getIngredientFilter() != null) {
                runtime.getIngredientFilter().setFilterText(value);
            }
        } catch (Throwable ignored) {
        }
    }

    /** 从 JEI 搜索框同步搜索文本（AUTO_SEARCH 和 JEI_AUTO_SEARCH 模式下） */
    private void syncSearchFromJei() {
        if (!isJeiAvailable()) return;
        var mode = ClientRtsController.get().getCraftTerminalSearchMode();
        if (mode != ClientRtsController.CraftTerminalSearchMode.AUTO_SEARCH
                && mode != ClientRtsController.CraftTerminalSearchMode.JEI_AUTO_SEARCH) return;
        try {
            var runtime = com.rtsbuilding.rtsbuilding.compat.jei.RtsJeiPlugin.getJeiRuntime();
            if (runtime != null && runtime.getIngredientFilter() != null) {
                String jeiText = runtime.getIngredientFilter().getFilterText();
                if (jeiText == null) jeiText = "";
                if (jeiText.equals(this.lastSyncedJeiSearch)) return;
                this.lastSyncedJeiSearch = jeiText;
                if (this.searchBox != null && !this.searchBox.isFocused()
                        && !jeiText.equals(this.searchBox.getValue())) {
                    this.searchBox.setValue(jeiText);
                    ClientRtsController.get().setStorageSearch(jeiText);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    // ── Row count ──────────────────────────────────────────────────────

    private void changeRowCount(int newRows) {
        ClientRtsController.get().setCraftTerminalRows(newRows);
        this.rowCount = newRows;
        recalcDimensions();
        this.imageHeight = this.screenH;
        this.inventoryLabelY = this.terminalH + 4;
        // 重新初始化屏幕布局
        this.init(this.minecraft, this.width, this.height);
    }

    // ── Helpers ────────────────────────────────────────────────────────

    private int resolveStorageSlotIndex(double mx, double my) {
        for (int r = 0; r < this.rowCount; r++)
            for (int c = 0; c < COLS; c++) {
                int sx = this.leftPos + GRID_LEFT + c * SLOT, sy = this.topPos + GRID_TOP + r * ROW_H;
                if (insideGui(mx, my, sx, sy, SLOT, SLOT)) return scrollRow * COLS + r * COLS + c;
            }
        return -1;
    }

    private boolean isInsideGrid(double mx, double my) {
        return insideGui(mx, my, this.leftPos + GRID_LEFT, this.topPos + GRID_TOP,
                COLS * SLOT, this.rowCount * ROW_H);
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

    private String searchModeLabel() {
        return switch (ClientRtsController.get().getCraftTerminalSearchMode()) {
            case AUTO_SEARCH -> "A";
            case JEI_AUTO_SEARCH -> "J";
            default -> "S";
        };
    }

    private static boolean isSpaceDown() {
        long w = net.minecraft.client.Minecraft.getInstance().getWindow().getWindow();
        return GLFW.glfwGetKey(w, GLFW.GLFW_KEY_SPACE) == GLFW.GLFW_PRESS;
    }

    private static void rect(GuiGraphics g, int x, int y, int w, int h, int lt, int rb) {
        g.hLine(x, x + w - 1, y, lt); g.vLine(x, y, y + h - 1, lt);
        g.hLine(x, x + w - 1, y + h - 1, rb); g.vLine(x + w - 1, y, y + h - 1, rb);
    }

    /** 基于屏幕坐标的碰撞检测（mx, my 为屏幕绝对坐标） */
    private static boolean insideGui(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }
}
