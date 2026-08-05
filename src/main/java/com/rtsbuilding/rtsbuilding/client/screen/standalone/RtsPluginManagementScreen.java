package com.rtsbuilding.rtsbuilding.client.screen.standalone;

import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.controller.PluginStateManager;
import com.rtsbuilding.rtsbuilding.client.plugin.RtsClientPluginCatalog;
import com.rtsbuilding.rtsbuilding.client.screen.canvas.MinecraftUiCanvas;
import com.rtsbuilding.rtsbuilding.client.input.overlay.LegacyGuiGraphics;
import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uikit.canvas.UiChromeRenderer;
import com.rtsbuilding.rtsbuilding.uikit.layout.PluginManagementLayout;
import com.rtsbuilding.rtsbuilding.uikit.theme.PluginManagementStyle;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiColor;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.resources.I18n;
import com.rtsbuilding.rtsbuilding.platform.math.MathHelper;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import org.lwjgl.input.Mouse;
import org.lwjgl.input.Keyboard;

import java.io.IOException;

import java.util.List;

/**
 * Production RTS plugin management screen.
 *
 * <p>The screen is a thin client adapter: it renders the server-synced installed
 * list, highlights plugin items in the player's inventory, and sends install or
 * uninstall requests. It does not contain install rules, slot categories, or
 * feature authority.
 */
public final class RtsPluginManagementScreen extends GuiScreen {
    private final GuiScreen parent;
    private final ClientRtsController controller = ClientRtsController.get();

    private int selectedInventorySlot = -1;
    private int hoveredInventorySlot = -1;
    private String hoveredInstalledPluginId = "";
    private ItemStack hoveredInstalledStack = null;
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

    public RtsPluginManagementScreen(GuiScreen parent) {
        this.parent = parent;
    }

    @Override
    public void initGui() {
        this.controller.requestPluginState();
        PluginManagementLayout.Layout layout = resolveLayout();
        this.buttonList.clear();
        this.buttonList.add(new GuiButton(0, layout.back.x, layout.back.y, layout.back.width, layout.back.height, I18n.format("gui.rtsbuilding.back")));
    }

    @Override
    public void updateScreen() {
        if (this.refreshFeedbackTicks > 0) {
            this.refreshFeedbackTicks--;
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTick) {
        LegacyGuiGraphics g = new LegacyGuiGraphics(this.mc, this.width, this.height);
        MinecraftUiCanvas canvas = new MinecraftUiCanvas(g, this.fontRendererObj);
        renderPageBackground(canvas);
        this.hoveredInventorySlot = -1;
        this.hoveredInstalledPluginId = "";
        this.hoveredInstalledStack = null;

        PluginManagementLayout.Layout layout = resolveLayout();
        PluginManagementLayout.Rect panel = layout.panel;
        drawFrame(canvas, panel.x, panel.y, panel.width, panel.height,
                PluginManagementStyle.PANEL_BACKGROUND, PluginManagementStyle.PANEL_BORDER);
        canvas.fill(panel.x + PluginManagementLayout.FRAME_INSET,
                panel.y + PluginManagementLayout.FRAME_INSET,
                panel.width - PluginManagementLayout.FRAME_INSET * 2,
                PluginManagementLayout.HEADER_H - PluginManagementLayout.FRAME_INSET,
                PluginManagementStyle.HEADER_BACKGROUND);
        g.drawString(this.fontRendererObj, I18n.format("screen.rtsbuilding.plugins"), panel.x + PluginManagementLayout.PAD,
                panel.y + PluginManagementLayout.HEADER_TITLE_TOP,
                PluginManagementStyle.TITLE.toArgb(), false);

        PluginManagementLayout.Rect installed = layout.installed;
        drawInstalledList(g, canvas, installed.x, installed.y,
                installed.width, installed.height, mouseX, mouseY);
        PluginManagementLayout.Rect install = layout.install;
        drawInstallArea(g, canvas, install.x, install.y, install.width, mouseX, mouseY);
        drawInventoryPlugins(g, canvas, layout, mouseX, mouseY);

        if (this.selectedInventorySlot >= 0) {
            ItemStack selected = inventoryStack(this.selectedInventorySlot);
            if (!com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(selected)) {
                g.renderItem(selected,
                        mouseX + PluginManagementLayout.CURSOR_ITEM_OFFSET,
                        mouseY + PluginManagementLayout.CURSOR_ITEM_OFFSET);
            }
        }

        if (this.hoveredInventorySlot >= 0) {
            ItemStack hovered = inventoryStack(this.hoveredInventorySlot);
            if (!com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(hovered)) {
                g.renderTooltip(hovered, mouseX, mouseY);
            }
        } else if (!com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(this.hoveredInstalledStack)) {
            g.renderTooltip(this.hoveredInstalledStack, mouseX, mouseY);
        }
        super.drawScreen(mouseX, mouseY, partialTick);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        if (button != 0) {
            super.mouseClicked(mouseX, mouseY, button);
            return;
        }
        if (inside(mouseX, mouseY, this.refreshX, this.refreshY, this.refreshW, this.refreshH)) {
            this.controller.requestPluginState();
            this.refreshFeedbackTicks = 12;
            return;
        }
        if (inside(mouseX, mouseY, this.installX, this.installY, this.installW, this.installH)
                && this.selectedInventorySlot >= 0) {
            installSelectedSlot();
            return;
        }

        String installedId = installedPluginAt(mouseX, mouseY);
        if (!installedId.trim().isEmpty() && isPersonalInstalledPlugin(installedId)) {
            if (GuiScreen.isShiftKeyDown() || inside(mouseX, mouseY,
                    installedUninstallX(), installedUninstallY(installedId), 44, 16)) {
                this.controller.uninstallPlugin(installedId);
                this.controller.requestPluginState();
                return;
            }
        }

        int inventorySlot = inventorySlotAt(mouseX, mouseY);
        if (inventorySlot >= 0) {
            ItemStack stack = inventoryStack(inventorySlot);
            if (RtsClientPluginCatalog.isPluginItem(stack)) {
                if (GuiScreen.isShiftKeyDown()) {
                    this.controller.installPluginFromInventorySlot(inventorySlot);
                    this.controller.requestPluginState();
                    this.selectedInventorySlot = -1;
                } else {
                    this.selectedInventorySlot = inventorySlot;
                }
                return;
            }
        }

        this.selectedInventorySlot = -1;
        super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void mouseMovedOrUp(int mouseX, int mouseY, int button) {
        if (button == 0
                && this.selectedInventorySlot >= 0
                && inside(mouseX, mouseY, this.installX, this.installY, this.installW, this.installH)) {
            installSelectedSlot();
            return;
        }
        super.mouseMovedOrUp(mouseX, mouseY, button);
    }

    boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        InstalledListMetrics metrics = installedListMetrics();
        if (inside(mouseX, mouseY, metrics.x(), metrics.y(), metrics.w(), metrics.h())
                && metrics.maxScroll() > 0) {
            int delta = scrollY > 0.0D ? -1 : 1;
            this.installedScroll = MathHelper.clamp(this.installedScroll + delta, 0, metrics.maxScroll());
            return true;
        }
        return false;
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            ScaledResolution resolution = new ScaledResolution(this.mc, this.mc.displayWidth, this.mc.displayHeight);
            double x = Mouse.getEventX() * resolution.getScaledWidth() / (double) this.mc.displayWidth;
            double y = resolution.getScaledHeight()
                    - Mouse.getEventY() * resolution.getScaledHeight() / (double) this.mc.displayHeight - 1;
            mouseScrolled(x, y, wheel > 0 ? 1.0D : -1.0D);
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 0 && this.mc != null) this.mc.displayGuiScreen(this.parent);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) this.mc.displayGuiScreen(this.parent);
        else super.keyTyped(typedChar, keyCode);
    }

    @Override public boolean doesGuiPauseGame() { return false; }

    private void drawInstalledList(LegacyGuiGraphics g, MinecraftUiCanvas canvas,
                                   int x, int y, int w, int h, int mouseX, int mouseY) {
        drawFrame(canvas, x, y, w, h,
                PluginManagementStyle.SURFACE_BACKGROUND, PluginManagementStyle.SURFACE_BORDER);
        g.drawString(this.fontRendererObj, I18n.format("screen.rtsbuilding.plugins.installed"),
                x + PluginManagementLayout.SURFACE_TITLE_X,
                y + PluginManagementLayout.SURFACE_TITLE_Y,
                PluginManagementStyle.PRIMARY_TEXT.toArgb(), false);
        String teamName = this.controller.getPluginTeamName();
        boolean hasTeam = teamName != null && !teamName.trim().isEmpty();
        if (hasTeam) {
            g.drawString(this.fontRendererObj, trim(
                            I18n.format("screen.rtsbuilding.plugins.team", teamName),
                            PluginManagementLayout.contentWidth(w)),
                    x + PluginManagementLayout.SURFACE_TITLE_X,
                    y + PluginManagementLayout.TEAM_TITLE_Y,
                    PluginManagementStyle.MUTED_TEXT.toArgb(), false);
        }
        List<PluginStateManager.InstalledPluginView> installed = this.controller.getInstalledPlugins();
        if (installed.isEmpty()) {
            this.installedScroll = 0;
            drawWrapped(g, I18n.format("screen.rtsbuilding.plugins.empty"),
                    x + PluginManagementLayout.CONTENT_TEXT_X,
                    y + (hasTeam ? PluginManagementLayout.EMPTY_WITH_TEAM_Y
                            : PluginManagementLayout.EMPTY_WITHOUT_TEAM_Y),
                    PluginManagementLayout.contentWidth(w),
                    PluginManagementStyle.MUTED_TEXT.toArgb());
            return;
        }

        PluginManagementLayout.Layout resolvedLayout = resolveLayout();
        PluginManagementLayout.InstalledRows rows = PluginManagementLayout.installedRows(
                resolvedLayout, hasTeam, installed.size(), this.installedScroll);
        PluginManagementLayout.Rect installedBounds = resolvedLayout.installed;
        int rowY = rows.firstRowY;
        int visibleRows = rows.visibleRows;
        int maxScroll = rows.maxScroll;
        this.installedScroll = rows.scroll;
        for (int i = this.installedScroll; i < installed.size(); i++) {
            PluginStateManager.InstalledPluginView plugin = installed.get(i);
            if (!PluginManagementLayout.installedRowFits(installedBounds, rowY)) {
                break;
            }
            boolean hover = inside(mouseX, mouseY,
                    x + PluginManagementLayout.ROW_HORIZONTAL_INSET, rowY,
                    w - PluginManagementLayout.ROW_HORIZONTAL_INSET * 2,
                    PluginManagementLayout.INSTALLED_ROW_H
                            - PluginManagementLayout.ROW_BOTTOM_INSET);
            if (hover) {
                this.hoveredInstalledPluginId = plugin.pluginId();
                this.hoveredInstalledStack = plugin.stack();
            }
            canvas.fill(x + PluginManagementLayout.ROW_HORIZONTAL_INSET, rowY,
                    w - PluginManagementLayout.ROW_HORIZONTAL_INSET * 2,
                    PluginManagementLayout.INSTALLED_ROW_H
                            - PluginManagementLayout.ROW_BOTTOM_INSET,
                    hover ? PluginManagementStyle.ROW_HOVER : PluginManagementStyle.ROW_BACKGROUND);
            ItemStack stack = plugin.stack();
            if (!com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(stack)) {
                g.renderItem(stack,
                        x + PluginManagementLayout.ROW_ITEM_X,
                        rowY + PluginManagementLayout.ROW_ITEM_Y);
            }
            String name = com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(stack) ? plugin.pluginId() : stack.getDisplayName();
            g.drawString(this.fontRendererObj,
                    trim(name, w - PluginManagementLayout.ROW_NAME_RIGHT_RESERVE),
                    x + PluginManagementLayout.ROW_TEXT_X,
                    rowY + PluginManagementLayout.ROW_NAME_Y,
                    PluginManagementStyle.TITLE.toArgb(), false);
            String status = pluginStatus(plugin);
            g.drawString(this.fontRendererObj,
                    trim(status, w - PluginManagementLayout.ROW_STATUS_RIGHT_RESERVE),
                    x + PluginManagementLayout.ROW_TEXT_X,
                    rowY + PluginManagementLayout.ROW_STATUS_Y,
                    PluginManagementStyle.SECONDARY_TEXT.toArgb(), false);
            if (plugin.personal()) {
                int uninstallX = x + w - PluginManagementLayout.UNINSTALL_RIGHT_INSET;
                canvas.fill(uninstallX, rowY + PluginManagementLayout.UNINSTALL_TOP,
                        PluginManagementLayout.UNINSTALL_W, PluginManagementLayout.UNINSTALL_H,
                        PluginManagementStyle.DANGER_BACKGROUND);
                g.drawCenteredString(this.fontRendererObj, I18n.format("screen.rtsbuilding.plugins.uninstall"), uninstallX + PluginManagementLayout.UNINSTALL_TEXT_X, rowY + PluginManagementLayout.UNINSTALL_TEXT_Y, PluginManagementStyle.DANGER_TEXT.toArgb());
            }
            rowY += PluginManagementLayout.INSTALLED_ROW_H;
        }
        drawInstalledScrollBar(canvas, x, y, w, h, hasTeam,
                installed.size(), visibleRows, maxScroll);
    }

    private void drawInstallArea(LegacyGuiGraphics g, MinecraftUiCanvas canvas,
                                 int x, int y, int w, int mouseX, int mouseY) {
        this.installX = x;
        this.installY = y;
        this.installW = w;
        this.installH = PluginManagementLayout.INSTALL_H;
        boolean hover = inside(mouseX, mouseY, x, y, w, this.installH);
        drawFrame(canvas, x, y, w, this.installH,
                hover ? PluginManagementStyle.INSTALL_HOVER : PluginManagementStyle.INSTALL_BACKGROUND,
                hover ? PluginManagementStyle.INSTALL_HOVER_BORDER : PluginManagementStyle.INSTALL_BORDER);
        this.refreshW = PluginManagementLayout.REFRESH_W;
        this.refreshH = PluginManagementLayout.REFRESH_H;
        this.refreshX = x + w - this.refreshW - PluginManagementLayout.REFRESH_RIGHT_INSET;
        this.refreshY = y + PluginManagementLayout.REFRESH_TOP;
        boolean refreshHover = inside(mouseX, mouseY, this.refreshX, this.refreshY, this.refreshW, this.refreshH);
        UiColor refreshFill = this.refreshFeedbackTicks > 0
                ? PluginManagementStyle.REFRESH_SUCCESS
                : refreshHover ? PluginManagementStyle.REFRESH_HOVER
                : PluginManagementStyle.REFRESH_BACKGROUND;
        drawFrame(canvas, this.refreshX, this.refreshY, this.refreshW, this.refreshH, refreshFill,
                refreshHover ? PluginManagementStyle.REFRESH_HOVER_BORDER
                        : PluginManagementStyle.REFRESH_BORDER);
        g.drawCenteredString(this.fontRendererObj, I18n.format("screen.rtsbuilding.plugins.refresh"), this.refreshX + this.refreshW / 2, this.refreshY + PluginManagementLayout.REFRESH_TEXT_TOP, PluginManagementStyle.PRIMARY_TEXT.toArgb());
        g.drawString(this.fontRendererObj, I18n.format("screen.rtsbuilding.plugins.install_area"),
                x + PluginManagementLayout.CONTENT_TEXT_X,
                y + PluginManagementLayout.INSTALL_TITLE_Y,
                PluginManagementStyle.PRIMARY_TEXT.toArgb(), false);
        String hint = this.selectedInventorySlot >= 0
                ? I18n.format("screen.rtsbuilding.plugins.drop_to_install")
                : I18n.format("screen.rtsbuilding.plugins.pick_hint");
        drawWrapped(g, hint,
                x + PluginManagementLayout.CONTENT_TEXT_X,
                y + PluginManagementLayout.INSTALL_HINT_Y,
                PluginManagementLayout.contentWidth(w),
                PluginManagementStyle.MUTED_TEXT.toArgb());
    }

    private void drawInventoryPlugins(LegacyGuiGraphics g, MinecraftUiCanvas canvas,
                                      PluginManagementLayout.Layout layout,
                                      int mouseX, int mouseY) {
        g.drawString(this.fontRendererObj, I18n.format("screen.rtsbuilding.plugins.inventory"),
                layout.inventoryTitleX, layout.inventoryTitleY,
                PluginManagementStyle.PRIMARY_TEXT.toArgb(), false);
        int[] slots = displayedInventorySlots();
        for (int i = 0; i < slots.length; i++) {
            int inventorySlot = slots[i];
            PluginManagementLayout.Rect slot = PluginManagementLayout.inventorySlot(layout, i);
            int sx = slot.x;
            int sy = slot.y;
            ItemStack stack = inventoryStack(inventorySlot);
            boolean plugin = RtsClientPluginCatalog.isPluginItem(stack);
            boolean selected = inventorySlot == this.selectedInventorySlot;
            boolean hover = inside(mouseX, mouseY, sx, sy, slot.width, slot.height);
            if (hover) {
                this.hoveredInventorySlot = inventorySlot;
            }
            UiColor fill = selected ? PluginManagementStyle.SLOT_SELECTED
                    : plugin ? PluginManagementStyle.SLOT_PLUGIN : PluginManagementStyle.SLOT_BACKGROUND;
            drawFrame(canvas, sx, sy, slot.width, slot.height, fill,
                    hover ? PluginManagementStyle.SLOT_HOVER_BORDER : PluginManagementStyle.SLOT_BORDER);
            if (!com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(stack)) {
                g.renderItem(stack,
                        sx + PluginManagementLayout.SLOT_CONTENT_INSET,
                        sy + PluginManagementLayout.SLOT_CONTENT_INSET);
            }
        }
    }

    private String installedPluginAt(double mouseX, double mouseY) {
        if (!this.hoveredInstalledPluginId.trim().isEmpty()) {
            return this.hoveredInstalledPluginId;
        }
        return "";
    }

    private int installedUninstallX() {
        PluginManagementLayout.Layout layout = resolveLayout();
        return layout.installed.right() - 50;
    }

    private int installedUninstallY(String pluginId) {
        PluginManagementLayout.Layout layout = resolveLayout();
        String teamName = this.controller.getPluginTeamName();
        PluginManagementLayout.InstalledRows rows = PluginManagementLayout.installedRows(
                layout, teamName != null && !teamName.trim().isEmpty(),
                this.controller.getInstalledPlugins().size(), this.installedScroll);
        int rowY = rows.firstRowY;
        int index = 0;
        for (PluginStateManager.InstalledPluginView plugin : this.controller.getInstalledPlugins()) {
            if (plugin.pluginId().equals(pluginId)) {
                int visibleIndex = index - this.installedScroll;
                return visibleIndex >= 0
                        ? rowY + visibleIndex * PluginManagementLayout.INSTALLED_ROW_H + 5 : -1000;
            }
            index++;
        }
        return -1000;
    }

    private String pluginStatus(PluginStateManager.InstalledPluginView plugin) {
        if (plugin.radiusBlocks() > 0 && plugin.personal()) {
            return I18n.format("screen.rtsbuilding.plugins.radius", plugin.radiusBlocks());
        }
        if (plugin.radiusBlocks() > 0) {
            return plugin.ownerName().trim().isEmpty()
                    ? I18n.format("screen.rtsbuilding.plugins.team_radius", plugin.radiusBlocks())
                    : I18n.format("screen.rtsbuilding.plugins.team_radius_by",
                            plugin.ownerName(), plugin.radiusBlocks());
        }
        if (plugin.personal()) {
            return I18n.format("screen.rtsbuilding.plugins.active");
        }
        return plugin.ownerName().trim().isEmpty()
                ? I18n.format("screen.rtsbuilding.plugins.team_shared")
                : I18n.format("screen.rtsbuilding.plugins.team_shared_by", plugin.ownerName());
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
        PluginManagementLayout.Layout layout = resolveLayout();
        PluginManagementLayout.Rect grid = layout.inventoryGrid;
        if (!inside(mouseX, mouseY, grid.x, grid.y, grid.width, grid.height)) {
            return -1;
        }
        int col = MathHelper.floor((mouseX - grid.x) / PluginManagementLayout.SLOT);
        int row = MathHelper.floor((mouseY - grid.y) / PluginManagementLayout.SLOT);
        int index = row * PluginManagementLayout.INVENTORY_COLS + col;
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
        if (this.mc == null || this.mc.thePlayer == null) {
            return null;
        }
        InventoryPlayer inventory = this.mc.thePlayer.inventory;
        if (slot < 0 || slot >= inventory.mainInventory.length) {
            return null;
        }
        return inventory.mainInventory[slot];
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

    private void drawWrapped(LegacyGuiGraphics g, String text, int x, int y, int width, int color) {
        for (String line : this.fontRendererObj.listFormattedStringToWidth(text, width)) {
            g.drawString(this.fontRendererObj, line, x, y, color, false);
            y += 10;
        }
    }

    private void drawFrame(MinecraftUiCanvas canvas, int x, int y, int w, int h,
                           UiColor fill, UiColor border) {
        UiChromeRenderer.frame(canvas, new UiRect(x, y, w, h), 1.0D,
                fill, border, PluginManagementStyle.DARK_BORDER);
    }

    private void drawInstalledScrollBar(MinecraftUiCanvas canvas,
            int x, int y, int w, int h, boolean hasTeam,
            int totalRows, int visibleRows, int maxScroll) {
        if (maxScroll <= 0) {
            return;
        }
        int contentY = y + (hasTeam ? 34 : 24);
        int trackH = Math.max(PluginManagementLayout.SCROLL_MIN_H,
                y + h - PluginManagementLayout.SCROLL_BOTTOM_INSET - contentY);
        int trackX = x + w - PluginManagementLayout.SCROLL_RIGHT_INSET;
        canvas.fill(trackX, contentY, 3, trackH, PluginManagementStyle.SCROLL_TRACK);
        int thumbH = MathHelper.clamp(trackH * visibleRows / Math.max(1, totalRows),
                PluginManagementLayout.SCROLL_MIN_H, trackH);
        int thumbY = contentY + (trackH - thumbH) * this.installedScroll / maxScroll;
        canvas.fill(trackX, thumbY, 3, thumbH, PluginManagementStyle.SCROLL_THUMB);
    }

    private InstalledListMetrics installedListMetrics() {
        PluginManagementLayout.Layout layout = resolveLayout();
        String teamName = this.controller.getPluginTeamName();
        PluginManagementLayout.InstalledRows rows = PluginManagementLayout.installedRows(
                layout, teamName != null && !teamName.trim().isEmpty(),
                this.controller.getInstalledPlugins().size(), this.installedScroll);
        PluginManagementLayout.Rect installed = layout.installed;
        return new InstalledListMetrics(installed.x, installed.y,
                installed.width, installed.height, rows.maxScroll);
    }

    private void renderPageBackground(MinecraftUiCanvas canvas) {
        canvas.fill(0, 0, this.width, this.height, PluginManagementStyle.PAGE_BACKGROUND);
    }

    private PluginManagementLayout.Layout resolveLayout() {
        return PluginManagementLayout.resolve(this.width, this.height);
    }

    private String trim(String text, int width) {
        return this.fontRendererObj.trimStringToWidth(text == null ? "" : text, Math.max(8, width));
    }

    private boolean inside(double x, double y, int rx, int ry, int rw, int rh) {
        return x >= rx && x < rx + rw && y >= ry && y < ry + rh;
    }

    private static final class InstalledListMetrics {
        private final int x, y, w, h, maxScroll;
        private InstalledListMetrics(int x, int y, int w, int h, int maxScroll) {
            this.x = x; this.y = y; this.w = w; this.h = h; this.maxScroll = maxScroll;
        }
        int x() { return x; }
        int y() { return y; }
        int w() { return w; }
        int h() { return h; }
        int maxScroll() { return maxScroll; }
    }
}
