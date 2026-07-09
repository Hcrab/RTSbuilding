package com.rtsbuilding.rtsbuilding.client.screen.standalone;

import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.controller.PluginStateManager;
import com.rtsbuilding.rtsbuilding.client.plugin.RtsClientPluginCatalog;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Production RTS plugin management screen.
 *
 * <p>The screen is a thin client adapter: it renders the server-synced installed
 * list, highlights plugin items in the player's inventory, and sends install or
 * uninstall requests. It does not contain install rules, slot categories, or
 * feature authority.
 */
public final class RtsPluginManagementScreen extends Screen {
    private static final int PANEL_MAX_W = 430;
    private static final int PANEL_MAX_H = 246;
    private static final int PAD = 12;
    private static final int HEADER_H = 27;
    private static final int SLOT = 18;
    private static final int INVENTORY_COLS = 9;
    private static final int INSTALLED_ROW_H = 26;

    private final Screen parent;
    private final ClientRtsController controller = ClientRtsController.get();

    private int selectedInventorySlot = -1;
    private int hoveredInventorySlot = -1;
    private String hoveredInstalledPluginId = "";
    private ItemStack hoveredInstalledStack = ItemStack.EMPTY;
    private int installedScroll;
    private int refreshFeedbackTicks;

    private int installX;
    private int installY;
    private int installW;
    private int installH;
    private int refreshX;
    private int refreshY;
    private int refreshW;
    private int refreshH;

    public RtsPluginManagementScreen(Screen parent) {
        super(Component.translatable("screen.rtsbuilding.plugins"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.controller.requestPluginState();
        addRenderableWidget(Button.builder(Component.translatable("gui.rtsbuilding.back"), btn -> {
                    onClose();
                })
                .bounds(this.width - 86, this.height - 28, 74, 20)
                .build());
    }

    @Override
    public void tick() {
        super.tick();
        if (this.refreshFeedbackTicks > 0) {
            this.refreshFeedbackTicks--;
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderPageBackground(g);
        this.hoveredInventorySlot = -1;
        this.hoveredInstalledPluginId = "";
        this.hoveredInstalledStack = ItemStack.EMPTY;

        Layout layout = resolveLayout();
        drawFrame(g, layout.x(), layout.y(), layout.w(), layout.h(), 0xEF111820, 0xFF6C8197);
        g.fill(layout.x() + 1, layout.y() + 1, layout.x() + layout.w() - 1,
                layout.y() + HEADER_H, 0xEE1A2430);
        g.drawString(this.font, this.title, layout.x() + PAD, layout.y() + 10, 0xFFFFFFFF, false);

        int leftX = layout.x() + PAD;
        int leftY = layout.y() + HEADER_H + 8;
        int leftW = Math.min(184, (layout.w() - PAD * 3) / 2);
        int rightX = leftX + leftW + PAD;
        int rightW = layout.x() + layout.w() - PAD - rightX;

        drawInstalledList(g, leftX, leftY, leftW, layout.h() - HEADER_H - 48, mouseX, mouseY);
        drawInstallArea(g, rightX, leftY, rightW, mouseX, mouseY);
        drawInventoryPlugins(g, rightX, leftY + 60, rightW, mouseX, mouseY);

        if (this.selectedInventorySlot >= 0) {
            ItemStack selected = inventoryStack(this.selectedInventorySlot);
            if (!selected.isEmpty()) {
                g.renderItem(selected, mouseX + 8, mouseY + 8);
            }
        }

        if (this.hoveredInventorySlot >= 0) {
            ItemStack hovered = inventoryStack(this.hoveredInventorySlot);
            if (!hovered.isEmpty()) {
                g.renderTooltip(this.font, hovered, mouseX, mouseY);
            }
        } else if (!this.hoveredInstalledStack.isEmpty()) {
            g.renderTooltip(this.font, this.hoveredInstalledStack, mouseX, mouseY);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (inside(mouseX, mouseY, this.refreshX, this.refreshY, this.refreshW, this.refreshH)) {
            this.controller.requestPluginState();
            this.refreshFeedbackTicks = 12;
            return true;
        }
        if (inside(mouseX, mouseY, this.installX, this.installY, this.installW, this.installH)
                && this.selectedInventorySlot >= 0) {
            installSelectedSlot();
            return true;
        }

        String installedId = installedPluginAt(mouseX, mouseY);
        if (!installedId.isBlank() && isPersonalInstalledPlugin(installedId)) {
            if (Screen.hasShiftDown() || inside(mouseX, mouseY,
                    installedUninstallX(), installedUninstallY(installedId), 44, 16)) {
                this.controller.uninstallPlugin(installedId);
                this.controller.requestPluginState();
                return true;
            }
        }

        int inventorySlot = inventorySlotAt(mouseX, mouseY);
        if (inventorySlot >= 0) {
            ItemStack stack = inventoryStack(inventorySlot);
            if (RtsClientPluginCatalog.isPluginItem(stack)) {
                if (Screen.hasShiftDown()) {
                    this.controller.installPluginFromInventorySlot(inventorySlot);
                    this.controller.requestPluginState();
                    this.selectedInventorySlot = -1;
                } else {
                    this.selectedInventorySlot = inventorySlot;
                }
                return true;
            }
        }

        this.selectedInventorySlot = -1;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0
                && this.selectedInventorySlot >= 0
                && inside(mouseX, mouseY, this.installX, this.installY, this.installW, this.installH)) {
            installSelectedSlot();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        InstalledListMetrics metrics = installedListMetrics();
        if (inside(mouseX, mouseY, metrics.x(), metrics.y(), metrics.w(), metrics.h())
                && metrics.maxScroll() > 0) {
            int delta = scrollY > 0.0D ? -1 : 1;
            this.installedScroll = Mth.clamp(this.installedScroll + delta, 0, metrics.maxScroll());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollY);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    protected void renderBlurredBackground(float partialTick) {
    }

    private void drawInstalledList(GuiGraphics g, int x, int y, int w, int h, int mouseX, int mouseY) {
        drawFrame(g, x, y, w, h, 0xCC17202A, 0xFF43566B);
        g.drawString(this.font, Component.translatable("screen.rtsbuilding.plugins.installed"),
                x + 7, y + 7, 0xFFEAF2FF, false);
        String teamName = this.controller.getPluginTeamName();
        boolean hasTeam = teamName != null && !teamName.isBlank();
        if (hasTeam) {
            g.drawString(this.font, trim(Component.translatable("screen.rtsbuilding.plugins.team", teamName).getString(), w - 16),
                    x + 7, y + 18, 0xFF9FB0C2, false);
        }
        List<PluginStateManager.InstalledPluginView> installed = this.controller.getInstalledPlugins();
        if (installed.isEmpty()) {
            this.installedScroll = 0;
            drawWrapped(g, Component.translatable("screen.rtsbuilding.plugins.empty"),
                    x + 8, y + (hasTeam ? 38 : 28), w - 16, 0xFF9FB0C2);
            return;
        }

        int rowY = y + (hasTeam ? 34 : 24);
        int visibleRows = Math.max(1, (y + h - 4 - rowY) / INSTALLED_ROW_H);
        int maxScroll = Math.max(0, installed.size() - visibleRows);
        this.installedScroll = Mth.clamp(this.installedScroll, 0, maxScroll);
        for (int i = this.installedScroll; i < installed.size(); i++) {
            PluginStateManager.InstalledPluginView plugin = installed.get(i);
            if (rowY + INSTALLED_ROW_H > y + h - 4) {
                break;
            }
            boolean hover = inside(mouseX, mouseY, x + 4, rowY, w - 8, INSTALLED_ROW_H - 2);
            if (hover) {
                this.hoveredInstalledPluginId = plugin.pluginId();
                this.hoveredInstalledStack = plugin.stack();
            }
            g.fill(x + 4, rowY, x + w - 4, rowY + INSTALLED_ROW_H - 2,
                    hover ? 0xAA2A3846 : 0x88202B36);
            ItemStack stack = plugin.stack();
            if (!stack.isEmpty()) {
                g.renderItem(stack, x + 7, rowY + 4);
            }
            String name = stack.isEmpty() ? plugin.pluginId() : stack.getHoverName().getString();
            g.drawString(this.font, trim(name, w - 76), x + 28, rowY + 5, 0xFFFFFFFF, false);
            String status = pluginStatus(plugin);
            g.drawString(this.font, trim(status, w - 82), x + 28, rowY + 16, 0xFFB8C7D6, false);
            if (plugin.personal()) {
                int uninstallX = x + w - 50;
                g.fill(uninstallX, rowY + 5, uninstallX + 44, rowY + 21, 0xCC3A2630);
                g.drawCenteredString(this.font, Component.translatable("screen.rtsbuilding.plugins.uninstall"),
                        uninstallX + 22, rowY + 9, 0xFFFFD4D4);
            }
            rowY += INSTALLED_ROW_H;
        }
        drawInstalledScrollBar(g, x, y, w, h, hasTeam, installed.size(), visibleRows, maxScroll);
    }

    private void drawInstallArea(GuiGraphics g, int x, int y, int w, int mouseX, int mouseY) {
        this.installX = x;
        this.installY = y;
        this.installW = w;
        this.installH = 46;
        boolean hover = inside(mouseX, mouseY, x, y, w, this.installH);
        drawFrame(g, x, y, w, this.installH, hover ? 0xCC243341 : 0xBB17202A,
                hover ? 0xFF85A7C5 : 0xFF4B5F73);
        this.refreshW = 52;
        this.refreshH = 16;
        this.refreshX = x + w - this.refreshW - 7;
        this.refreshY = y + 5;
        boolean refreshHover = inside(mouseX, mouseY, this.refreshX, this.refreshY, this.refreshW, this.refreshH);
        int refreshFill = this.refreshFeedbackTicks > 0 ? 0xCC2F5B45 : refreshHover ? 0xCC2B4055 : 0xAA1D2A37;
        drawFrame(g, this.refreshX, this.refreshY, this.refreshW, this.refreshH, refreshFill,
                refreshHover ? 0xFF9FC7E6 : 0xFF5C7188);
        g.drawCenteredString(this.font, Component.translatable("screen.rtsbuilding.plugins.refresh"),
                this.refreshX + this.refreshW / 2, this.refreshY + 4, 0xFFEAF2FF);
        g.drawString(this.font, Component.translatable("screen.rtsbuilding.plugins.install_area"),
                x + 8, y + 7, 0xFFEAF2FF, false);
        Component hint = this.selectedInventorySlot >= 0
                ? Component.translatable("screen.rtsbuilding.plugins.drop_to_install")
                : Component.translatable("screen.rtsbuilding.plugins.pick_hint");
        drawWrapped(g, hint, x + 8, y + 22, w - 16, 0xFF9FB0C2);
    }

    private void drawInventoryPlugins(GuiGraphics g, int x, int y, int w, int mouseX, int mouseY) {
        int gridW = INVENTORY_COLS * SLOT;
        int gridX = x + Math.max(0, (w - gridW) / 2);
        g.drawString(this.font, Component.translatable("screen.rtsbuilding.plugins.inventory"),
                x, y, 0xFFEAF2FF, false);
        int slotY = y + 14;
        int[] slots = displayedInventorySlots();
        for (int i = 0; i < slots.length; i++) {
            int inventorySlot = slots[i];
            int sx = gridX + (i % INVENTORY_COLS) * SLOT;
            int sy = slotY + (i / INVENTORY_COLS) * SLOT;
            ItemStack stack = inventoryStack(inventorySlot);
            boolean plugin = RtsClientPluginCatalog.isPluginItem(stack);
            boolean selected = inventorySlot == this.selectedInventorySlot;
            boolean hover = inside(mouseX, mouseY, sx, sy, SLOT, SLOT);
            if (hover) {
                this.hoveredInventorySlot = inventorySlot;
            }
            int fill = selected ? 0xCC2F6B47 : plugin ? 0xAA25364A : 0x77313A45;
            drawFrame(g, sx, sy, SLOT, SLOT, fill, hover ? 0xFF9FB8D3 : 0xFF46576A);
            if (!stack.isEmpty()) {
                g.renderItem(stack, sx + 1, sy + 1);
                g.renderItemDecorations(this.font, stack, sx + 1, sy + 1);
            }
        }
    }

    private String installedPluginAt(double mouseX, double mouseY) {
        if (!this.hoveredInstalledPluginId.isBlank()) {
            return this.hoveredInstalledPluginId;
        }
        return "";
    }

    private int installedUninstallX() {
        Layout layout = resolveLayout();
        int leftX = layout.x() + PAD;
        int leftW = Math.min(184, (layout.w() - PAD * 3) / 2);
        return leftX + leftW - 50;
    }

    private int installedUninstallY(String pluginId) {
        Layout layout = resolveLayout();
        String teamName = this.controller.getPluginTeamName();
        int rowY = layout.y() + HEADER_H + 8 + (teamName == null || teamName.isBlank() ? 24 : 34);
        int index = 0;
        for (PluginStateManager.InstalledPluginView plugin : this.controller.getInstalledPlugins()) {
            if (plugin.pluginId().equals(pluginId)) {
                int visibleIndex = index - this.installedScroll;
                return visibleIndex >= 0 ? rowY + visibleIndex * INSTALLED_ROW_H + 5 : -1000;
            }
            index++;
        }
        return -1000;
    }

    private String pluginStatus(PluginStateManager.InstalledPluginView plugin) {
        if (plugin.radiusBlocks() > 0 && plugin.personal()) {
            return Component.translatable("screen.rtsbuilding.plugins.radius", plugin.radiusBlocks()).getString();
        }
        if (plugin.radiusBlocks() > 0) {
            return plugin.ownerName().isBlank()
                    ? Component.translatable("screen.rtsbuilding.plugins.team_radius", plugin.radiusBlocks()).getString()
                    : Component.translatable("screen.rtsbuilding.plugins.team_radius_by",
                            plugin.ownerName(), plugin.radiusBlocks()).getString();
        }
        if (plugin.personal()) {
            return Component.translatable("screen.rtsbuilding.plugins.active").getString();
        }
        return plugin.ownerName().isBlank()
                ? Component.translatable("screen.rtsbuilding.plugins.team_shared").getString()
                : Component.translatable("screen.rtsbuilding.plugins.team_shared_by", plugin.ownerName()).getString();
    }

    private boolean isPersonalInstalledPlugin(String pluginId) {
        for (PluginStateManager.InstalledPluginView plugin : this.controller.getInstalledPlugins()) {
            if (plugin.pluginId().equals(pluginId)) {
                return plugin.personal();
            }
        }
        return false;
    }

    private int inventorySlotAt(double mouseX, double mouseY) {
        Layout layout = resolveLayout();
        int leftW = Math.min(184, (layout.w() - PAD * 3) / 2);
        int rightX = layout.x() + PAD + leftW + PAD;
        int rightW = layout.x() + layout.w() - PAD - rightX;
        int gridW = INVENTORY_COLS * SLOT;
        int gridX = rightX + Math.max(0, (rightW - gridW) / 2);
        int gridY = layout.y() + HEADER_H + 8 + 60 + 14;
        if (!inside(mouseX, mouseY, gridX, gridY, gridW, 4 * SLOT)) {
            return -1;
        }
        int col = Mth.floor((mouseX - gridX) / SLOT);
        int row = Mth.floor((mouseY - gridY) / SLOT);
        int index = row * INVENTORY_COLS + col;
        int[] slots = displayedInventorySlots();
        return index >= 0 && index < slots.length ? slots[index] : -1;
    }

    private void installSelectedSlot() {
        if (this.selectedInventorySlot < 0) {
            return;
        }
        this.controller.installPluginFromInventorySlot(this.selectedInventorySlot);
        this.controller.requestPluginState();
        this.selectedInventorySlot = -1;
    }

    private ItemStack inventoryStack(int slot) {
        if (this.minecraft == null || this.minecraft.player == null) {
            return ItemStack.EMPTY;
        }
        Inventory inventory = this.minecraft.player.getInventory();
        if (slot < 0 || slot >= inventory.items.size()) {
            return ItemStack.EMPTY;
        }
        return inventory.items.get(slot);
    }

    private int[] displayedInventorySlots() {
        int[] slots = new int[36];
        int out = 0;
        for (int slot = 9; slot < 36; slot++) {
            slots[out++] = slot;
        }
        for (int slot = 0; slot < 9; slot++) {
            slots[out++] = slot;
        }
        return slots;
    }

    private void drawWrapped(GuiGraphics g, Component text, int x, int y, int width, int color) {
        for (var line : this.font.split(text, width)) {
            g.drawString(this.font, line, x, y, color, false);
            y += 10;
        }
    }

    private void drawFrame(GuiGraphics g, int x, int y, int w, int h, int fill, int border) {
        g.fill(x, y, x + w, y + h, fill);
        g.hLine(x, x + w, y, border);
        g.hLine(x, x + w, y + h, 0xFF0B1016);
        g.vLine(x, y, y + h, border);
        g.vLine(x + w, y, y + h, 0xFF0B1016);
    }

    private void drawInstalledScrollBar(GuiGraphics g, int x, int y, int w, int h, boolean hasTeam,
            int totalRows, int visibleRows, int maxScroll) {
        if (maxScroll <= 0) {
            return;
        }
        int contentY = y + (hasTeam ? 34 : 24);
        int trackH = Math.max(12, y + h - 6 - contentY);
        int trackX = x + w - 8;
        g.fill(trackX, contentY, trackX + 3, contentY + trackH, 0x66334455);
        int thumbH = Mth.clamp(trackH * visibleRows / Math.max(1, totalRows), 12, trackH);
        int thumbY = contentY + (trackH - thumbH) * this.installedScroll / maxScroll;
        g.fill(trackX, thumbY, trackX + 3, thumbY + thumbH, 0xFF8FA8C3);
    }

    private InstalledListMetrics installedListMetrics() {
        Layout layout = resolveLayout();
        int leftX = layout.x() + PAD;
        int leftY = layout.y() + HEADER_H + 8;
        int leftW = Math.min(184, (layout.w() - PAD * 3) / 2);
        int leftH = layout.h() - HEADER_H - 48;
        String teamName = this.controller.getPluginTeamName();
        int rowY = leftY + (teamName == null || teamName.isBlank() ? 24 : 34);
        int visibleRows = Math.max(1, (leftY + leftH - 4 - rowY) / INSTALLED_ROW_H);
        int maxScroll = Math.max(0, this.controller.getInstalledPlugins().size() - visibleRows);
        return new InstalledListMetrics(leftX, leftY, leftW, leftH, maxScroll);
    }

    private void renderPageBackground(GuiGraphics g) {
        g.fill(0, 0, this.width, this.height, 0xD80D1117);
    }

    private Layout resolveLayout() {
        int w = Math.min(PANEL_MAX_W, Math.max(300, this.width - 20));
        int h = Math.min(PANEL_MAX_H, Math.max(214, this.height - 42));
        return new Layout((this.width - w) / 2, Math.max(10, (this.height - h) / 2 - 6), w, h);
    }

    private String trim(String text, int width) {
        return this.font.plainSubstrByWidth(text == null ? "" : text, Math.max(8, width));
    }

    private boolean inside(double x, double y, int rx, int ry, int rw, int rh) {
        return x >= rx && x < rx + rw && y >= ry && y < ry + rh;
    }

    private record Layout(int x, int y, int w, int h) {
    }

    private record InstalledListMetrics(int x, int y, int w, int h, int maxScroll) {
    }
}
