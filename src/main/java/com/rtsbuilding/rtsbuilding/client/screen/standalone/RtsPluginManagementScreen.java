package com.rtsbuilding.rtsbuilding.client.screen.standalone;

import com.rtsbuilding.rtsbuilding.uikit.theme.RtsMainlineTheme;
import com.rtsbuilding.rtsbuilding.uikit.layout.RtsMainlineLayout;

import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.controller.PluginStateManager;
import com.rtsbuilding.rtsbuilding.client.plugin.RtsClientPluginCatalog;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
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
                .bounds(this.width - RtsMainlineLayout.D86, this.height - RtsMainlineLayout.D28, 74, 20)
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
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        renderPageBackground(g);
        this.hoveredInventorySlot = -1;
        this.hoveredInstalledPluginId = "";
        this.hoveredInstalledStack = ItemStack.EMPTY;

        Layout layout = resolveLayout();
        drawFrame(g, layout.x(), layout.y(), layout.w(), layout.h(), RtsMainlineTheme.LEGACY_EF111820.toArgb(), RtsMainlineTheme.LEGACY_FF6C8197.toArgb());
        g.fill(layout.x() + 1, layout.y() + 1, layout.x() + layout.w() - 1,
                layout.y() + HEADER_H, RtsMainlineTheme.LEGACY_EE1A2430.toArgb());
        g .text(this.font, this.title, layout.x() + PAD, layout.y() + 10, RtsMainlineTheme.LEGACY_FFFFFFFF.toArgb(), false);

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
                g .item(selected, mouseX + RtsMainlineLayout.D8, mouseY + RtsMainlineLayout.D8);
            }
        }

        if (this.hoveredInventorySlot >= 0) {
            ItemStack hovered = inventoryStack(this.hoveredInventorySlot);
            if (!hovered.isEmpty()) {
                g .setTooltipForNextFrame(this.font, hovered, mouseX, mouseY);
            }
        } else if (!this.hoveredInstalledStack.isEmpty()) {
            g .setTooltipForNextFrame(this.font, this.hoveredInstalledStack, mouseX, mouseY);
        }
        super.extractRenderState(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        if (button != 0) {
            return super.mouseClicked(event, doubleClick);
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
            if (com.rtsbuilding.rtsbuilding.client.input.RtsModifierKeys.isShiftDown() || inside(mouseX, mouseY,
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
                if (com.rtsbuilding.rtsbuilding.client.input.RtsModifierKeys.isShiftDown()) {
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
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        if (button == 0
                && this.selectedInventorySlot >= 0
                && inside(mouseX, mouseY, this.installX, this.installY, this.installW, this.installH)) {
            installSelectedSlot();
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        InstalledListMetrics metrics = installedListMetrics();
        if (inside(mouseX, mouseY, metrics.x(), metrics.y(), metrics.w(), metrics.h())
                && metrics.maxScroll() > 0) {
            int delta = scrollY > 0.0D ? -1 : 1;
            this.installedScroll = Mth.clamp(this.installedScroll + delta, 0, metrics.maxScroll());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
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

    @Override
    protected void extractBlurredBackground(GuiGraphicsExtractor graphics) {
    }

    private void drawInstalledList(GuiGraphicsExtractor g, int x, int y, int w, int h, int mouseX, int mouseY) {
        drawFrame(g, x, y, w, h, RtsMainlineTheme.LEGACY_CC17202A.toArgb(), RtsMainlineTheme.LEGACY_FF43566B.toArgb());
        g .text(this.font, Component.translatable("screen.rtsbuilding.plugins.installed"),
                x + RtsMainlineLayout.D7, y + RtsMainlineLayout.D7, RtsMainlineTheme.LEGACY_FFEAF2FF.toArgb(), false);
        String teamName = this.controller.getPluginTeamName();
        boolean hasTeam = teamName != null && !teamName.isBlank();
        if (hasTeam) {
            g .text(this.font, trim(Component.translatable("screen.rtsbuilding.plugins.team", teamName).getString(), w - RtsMainlineLayout.D16),
                    x + RtsMainlineLayout.D7, y + RtsMainlineLayout.D18, RtsMainlineTheme.LEGACY_FF9FB0C2.toArgb(), false);
        }
        List<PluginStateManager.InstalledPluginView> installed = this.controller.getInstalledPlugins();
        if (installed.isEmpty()) {
            this.installedScroll = 0;
            drawWrapped(g, Component.translatable("screen.rtsbuilding.plugins.empty"),
                    x + RtsMainlineLayout.D8, y + (hasTeam ? RtsMainlineLayout.D38 : RtsMainlineLayout.D28), w - RtsMainlineLayout.D16, RtsMainlineTheme.LEGACY_FF9FB0C2.toArgb());
            return;
        }

        int rowY = y + (hasTeam ? 34 : 24);
        int visibleRows = Math.max(1, (y + h - RtsMainlineLayout.D4 - rowY) / INSTALLED_ROW_H);
        int maxScroll = Math.max(0, installed.size() - visibleRows);
        this.installedScroll = Mth.clamp(this.installedScroll, 0, maxScroll);
        for (int i = this.installedScroll; i < installed.size(); i++) {
            PluginStateManager.InstalledPluginView plugin = installed.get(i);
            if (rowY + INSTALLED_ROW_H > y + h - RtsMainlineLayout.D4) {
                break;
            }
            boolean hover = inside(mouseX, mouseY, x + RtsMainlineLayout.D4, rowY, w - RtsMainlineLayout.D8, INSTALLED_ROW_H - RtsMainlineLayout.D2);
            if (hover) {
                this.hoveredInstalledPluginId = plugin.pluginId();
                this.hoveredInstalledStack = plugin.stack();
            }
            g.fill(x + RtsMainlineLayout.D4, rowY, x + w - RtsMainlineLayout.D4, rowY + INSTALLED_ROW_H - RtsMainlineLayout.D2,
                    hover ? RtsMainlineTheme.LEGACY_AA2A3846.toArgb() : RtsMainlineTheme.LEGACY_88202B36.toArgb());
            ItemStack stack = plugin.stack();
            if (!stack.isEmpty()) {
                g .item(stack, x + RtsMainlineLayout.D7, rowY + RtsMainlineLayout.D4);
            }
            String name = stack.isEmpty() ? plugin.pluginId() : stack.getHoverName().getString();
            g .text(this.font, trim(name, w - RtsMainlineLayout.D76), x + RtsMainlineLayout.D28, rowY + RtsMainlineLayout.D5, RtsMainlineTheme.LEGACY_FFFFFFFF.toArgb(), false);
            String status = pluginStatus(plugin);
            g .text(this.font, trim(status, w - RtsMainlineLayout.D82), x + RtsMainlineLayout.D28, rowY + RtsMainlineLayout.D16, RtsMainlineTheme.LEGACY_FFB8C7D6.toArgb(), false);
            if (plugin.personal()) {
                int uninstallX = x + w - RtsMainlineLayout.D50;
                g.fill(uninstallX, rowY + 5, uninstallX + 44, rowY + 21, RtsMainlineTheme.LEGACY_CC3A2630.toArgb());
                g .centeredText(this.font, Component.translatable("screen.rtsbuilding.plugins.uninstall"),
                        uninstallX + 22, rowY + 9, RtsMainlineTheme.LEGACY_FFFFD4D4.toArgb());
            }
            rowY += INSTALLED_ROW_H;
        }
        drawInstalledScrollBar(g, x, y, w, h, hasTeam, installed.size(), visibleRows, maxScroll);
    }

    private void drawInstallArea(GuiGraphicsExtractor g, int x, int y, int w, int mouseX, int mouseY) {
        this.installX = x;
        this.installY = y;
        this.installW = w;
        this.installH = 46;
        boolean hover = inside(mouseX, mouseY, x, y, w, this.installH);
        drawFrame(g, x, y, w, this.installH, hover ? RtsMainlineTheme.LEGACY_CC243341.toArgb() : RtsMainlineTheme.LEGACY_BB17202A.toArgb(),
                hover ? RtsMainlineTheme.LEGACY_FF85A7C5.toArgb() : RtsMainlineTheme.LEGACY_FF4B5F73.toArgb());
        this.refreshW = 52;
        this.refreshH = 16;
        this.refreshX = x + w - this.refreshW - 7;
        this.refreshY = y + RtsMainlineLayout.D5;
        boolean refreshHover = inside(mouseX, mouseY, this.refreshX, this.refreshY, this.refreshW, this.refreshH);
        int refreshFill = this.refreshFeedbackTicks > 0 ? RtsMainlineTheme.LEGACY_CC2F5B45.toArgb() : refreshHover ? RtsMainlineTheme.LEGACY_CC2B4055.toArgb() : RtsMainlineTheme.LEGACY_AA1D2A37.toArgb();
        drawFrame(g, this.refreshX, this.refreshY, this.refreshW, this.refreshH, refreshFill,
                refreshHover ? RtsMainlineTheme.LEGACY_FF9FC7E6.toArgb() : RtsMainlineTheme.LEGACY_FF5C7188.toArgb());
        g .centeredText(this.font, Component.translatable("screen.rtsbuilding.plugins.refresh"),
                this.refreshX + this.refreshW / 2, this.refreshY + 4, RtsMainlineTheme.LEGACY_FFEAF2FF.toArgb());
        g .text(this.font, Component.translatable("screen.rtsbuilding.plugins.install_area"),
                x + RtsMainlineLayout.D8, y + RtsMainlineLayout.D7, RtsMainlineTheme.LEGACY_FFEAF2FF.toArgb(), false);
        Component hint = this.selectedInventorySlot >= 0
                ? Component.translatable("screen.rtsbuilding.plugins.drop_to_install")
                : Component.translatable("screen.rtsbuilding.plugins.pick_hint");
        drawWrapped(g, hint, x + RtsMainlineLayout.D8, y + RtsMainlineLayout.D22, w - RtsMainlineLayout.D16, RtsMainlineTheme.LEGACY_FF9FB0C2.toArgb());
    }

    private void drawInventoryPlugins(GuiGraphicsExtractor g, int x, int y, int w, int mouseX, int mouseY) {
        int gridW = INVENTORY_COLS * SLOT;
        int gridX = x + Math.max(0, (w - gridW) / 2);
        g .text(this.font, Component.translatable("screen.rtsbuilding.plugins.inventory"),
                x, y, RtsMainlineTheme.LEGACY_FFEAF2FF.toArgb(), false);
        int slotY = y + RtsMainlineLayout.D14;
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
            int fill = selected ? RtsMainlineTheme.LEGACY_CC2F6B47.toArgb() : plugin ? RtsMainlineTheme.LEGACY_AA25364A.toArgb() : RtsMainlineTheme.LEGACY_77313A45.toArgb();
            drawFrame(g, sx, sy, SLOT, SLOT, fill, hover ? RtsMainlineTheme.LEGACY_FF9FB8D3.toArgb() : RtsMainlineTheme.LEGACY_FF46576A.toArgb());
            if (!stack.isEmpty()) {
                g .item(stack, sx + 1, sy + 1);
                g .itemDecorations(this.font, stack, sx + 1, sy + 1);
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
        if (slot < 0 || slot >= inventory.getNonEquipmentItems().size()) {
            return ItemStack.EMPTY;
        }
        return inventory.getNonEquipmentItems().get(slot);
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

    private void drawWrapped(GuiGraphicsExtractor g, Component text, int x, int y, int width, int color) {
        for (var line : this.font.split(text, width)) {
            g .text(this.font, line, x, y, color, false);
            y += 10;
        }
    }

    private void drawFrame(GuiGraphicsExtractor g, int x, int y, int w, int h, int fill, int border) {
        g.fill(x, y, x + w, y + h, fill);
        g.horizontalLine(x, x + w, y, border);
        g.horizontalLine(x, x + w, y + h, RtsMainlineTheme.LEGACY_FF0B1016.toArgb());
        g.verticalLine(x, y, y + h, border);
        g.verticalLine(x + w, y, y + h, RtsMainlineTheme.LEGACY_FF0B1016.toArgb());
    }

    private void drawInstalledScrollBar(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean hasTeam,
            int totalRows, int visibleRows, int maxScroll) {
        if (maxScroll <= 0) {
            return;
        }
        int contentY = y + (hasTeam ? 34 : 24);
        int trackH = Math.max(12, y + h - RtsMainlineLayout.D6 - contentY);
        int trackX = x + w - RtsMainlineLayout.D8;
        g.fill(trackX, contentY, trackX + 3, contentY + trackH, RtsMainlineTheme.LEGACY_66334455.toArgb());
        int thumbH = Mth.clamp(trackH * visibleRows / Math.max(1, totalRows), 12, trackH);
        int thumbY = contentY + (trackH - thumbH) * this.installedScroll / maxScroll;
        g.fill(trackX, thumbY, trackX + 3, thumbY + thumbH, RtsMainlineTheme.LEGACY_FF8FA8C3.toArgb());
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

    private void renderPageBackground(GuiGraphicsExtractor g) {
        g.fill(0, 0, this.width, this.height, RtsMainlineTheme.LEGACY_D80D1117.toArgb());
    }

    private Layout resolveLayout() {
        int w = Math.min(PANEL_MAX_W, Math.max(300, this.width - RtsMainlineLayout.D20));
        int h = Math.min(PANEL_MAX_H, Math.max(214, this.height - RtsMainlineLayout.D42));
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
