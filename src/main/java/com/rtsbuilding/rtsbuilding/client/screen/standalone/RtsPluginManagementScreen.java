package com.rtsbuilding.rtsbuilding.client.screen.standalone;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.controller.PluginStateManager;
import com.rtsbuilding.rtsbuilding.client.plugin.RtsClientPluginCatalog;
import com.rtsbuilding.rtsbuilding.client.screen.canvas.MinecraftUiCanvas;
import com.rtsbuilding.rtsbuilding.client.util.RtsClientUiUtil;
import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uikit.canvas.UiChromeRenderer;
import com.rtsbuilding.rtsbuilding.uikit.animation.SystemUiClock;
import com.rtsbuilding.rtsbuilding.uikit.animation.UiControlAnimationRegistry;
import com.rtsbuilding.rtsbuilding.uikit.animation.UiControlAnimationState;
import com.rtsbuilding.rtsbuilding.uicore.control.UiControlState;
import com.rtsbuilding.rtsbuilding.uikit.layout.PluginManagementLayout;
import com.rtsbuilding.rtsbuilding.uikit.theme.PluginManagementStyle;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiColor;
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
    private final Screen parent;
    private final ClientRtsController controller = ClientRtsController.get();

    private int selectedInventorySlot = -1;
    private int hoveredInventorySlot = -1;
    private String hoveredInstalledPluginId = "";
    private ItemStack hoveredInstalledStack = ItemStack.EMPTY;
    private int installedScroll;
    private int refreshFeedbackTicks;
    private final UiControlAnimationRegistry<String> controlAnimations =
            new UiControlAnimationRegistry<>(SystemUiClock.INSTANCE, 96);

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
        PluginManagementLayout.Layout layout = resolveLayout();
        addRenderableWidget(Button.builder(Component.translatable("gui.rtsbuilding.back"), btn -> {
                    onClose();
                })
                .bounds(layout.back.x, layout.back.y, layout.back.width, layout.back.height)
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
        MinecraftUiCanvas canvas = new MinecraftUiCanvas(g, this.font);
        renderPageBackground(canvas);
        this.hoveredInventorySlot = -1;
        this.hoveredInstalledPluginId = "";
        this.hoveredInstalledStack = ItemStack.EMPTY;

        PluginManagementLayout.Layout layout = resolveLayout();
        PluginManagementLayout.Rect panel = layout.panel;
        drawFrame(canvas, panel.x, panel.y, panel.width, panel.height,
                PluginManagementStyle.PANEL_BACKGROUND, PluginManagementStyle.PANEL_BORDER);
        canvas.fill(panel.x + PluginManagementLayout.FRAME_INSET,
                panel.y + PluginManagementLayout.FRAME_INSET,
                panel.width - PluginManagementLayout.FRAME_INSET * 2,
                PluginManagementLayout.HEADER_H - PluginManagementLayout.FRAME_INSET,
                PluginManagementStyle.HEADER_BACKGROUND);
        g.drawString(this.font, this.title, panel.x + PluginManagementLayout.PAD,
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
            if (!selected.isEmpty()) {
                g.renderItem(selected,
                        mouseX + PluginManagementLayout.CURSOR_ITEM_OFFSET,
                        mouseY + PluginManagementLayout.CURSOR_ITEM_OFFSET);
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
    protected void renderBlurredBackground(float partialTick) {
    }

    private void drawInstalledList(GuiGraphics g, MinecraftUiCanvas canvas,
                                   int x, int y, int w, int h, int mouseX, int mouseY) {
        drawFrame(canvas, x, y, w, h,
                PluginManagementStyle.SURFACE_BACKGROUND, PluginManagementStyle.SURFACE_BORDER);
        g.drawString(this.font, Component.translatable("screen.rtsbuilding.plugins.installed"),
                x + PluginManagementLayout.SURFACE_TITLE_X,
                y + PluginManagementLayout.SURFACE_TITLE_Y,
                PluginManagementStyle.PRIMARY_TEXT.toArgb(), false);
        String teamName = this.controller.getPluginTeamName();
        boolean hasTeam = teamName != null && !teamName.isBlank();
        if (hasTeam) {
            g.drawString(this.font, trim(
                            Component.translatable("screen.rtsbuilding.plugins.team", teamName)
                                    .getString(),
                            PluginManagementLayout.contentWidth(w)),
                    x + PluginManagementLayout.SURFACE_TITLE_X,
                    y + PluginManagementLayout.TEAM_TITLE_Y,
                    PluginManagementStyle.MUTED_TEXT.toArgb(), false);
        }
        List<PluginStateManager.InstalledPluginView> installed = this.controller.getInstalledPlugins();
        if (installed.isEmpty()) {
            this.installedScroll = 0;
            drawWrapped(g, Component.translatable("screen.rtsbuilding.plugins.empty"),
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
            String visibleRowId = "installed." + (i - this.installedScroll);
            double rowHover = animate(
                    visibleRowId, hover, false);
            canvas.fill(x + PluginManagementLayout.ROW_HORIZONTAL_INSET, rowY,
                    w - PluginManagementLayout.ROW_HORIZONTAL_INSET * 2,
                    PluginManagementLayout.INSTALLED_ROW_H
                            - PluginManagementLayout.ROW_BOTTOM_INSET,
                    PluginManagementStyle.rowBackground(rowHover));
            ItemStack stack = plugin.stack();
            if (!stack.isEmpty()) {
                g.renderItem(stack,
                        x + PluginManagementLayout.ROW_ITEM_X,
                        rowY + PluginManagementLayout.ROW_ITEM_Y);
            }
            String name = stack.isEmpty() ? plugin.pluginId() : stack.getHoverName().getString();
            g.drawString(this.font,
                    trim(name, w - PluginManagementLayout.ROW_NAME_RIGHT_RESERVE),
                    x + PluginManagementLayout.ROW_TEXT_X,
                    rowY + PluginManagementLayout.ROW_NAME_Y,
                    PluginManagementStyle.TITLE.toArgb(), false);
            String status = pluginStatus(plugin);
            g.drawString(this.font,
                    trim(status, w - PluginManagementLayout.ROW_STATUS_RIGHT_RESERVE),
                    x + PluginManagementLayout.ROW_TEXT_X,
                    rowY + PluginManagementLayout.ROW_STATUS_Y,
                    PluginManagementStyle.SECONDARY_TEXT.toArgb(), false);
            if (plugin.personal()) {
                int uninstallX = x + w - PluginManagementLayout.UNINSTALL_RIGHT_INSET;
                boolean uninstallHovered = inside(
                        mouseX, mouseY,
                        uninstallX,
                        rowY + PluginManagementLayout.UNINSTALL_TOP,
                        PluginManagementLayout.UNINSTALL_W,
                        PluginManagementLayout.UNINSTALL_H);
                double uninstallHover = animate(
                        visibleRowId + ".uninstall", uninstallHovered, false);
                canvas.fill(uninstallX, rowY + PluginManagementLayout.UNINSTALL_TOP,
                        PluginManagementLayout.UNINSTALL_W, PluginManagementLayout.UNINSTALL_H,
                        PluginManagementStyle.dangerBackground(uninstallHover));
                RtsClientUiUtil.drawCenteredStringNoShadow(
                        g, this.font,
                        Component.translatable("screen.rtsbuilding.plugins.uninstall"),
                        uninstallX + PluginManagementLayout.UNINSTALL_TEXT_X,
                        rowY + PluginManagementLayout.UNINSTALL_TEXT_Y,
                        PluginManagementStyle.DANGER_TEXT.toArgb());
            }
            rowY += PluginManagementLayout.INSTALLED_ROW_H;
        }
        drawInstalledScrollBar(canvas, x, y, w, h, hasTeam,
                installed.size(), visibleRows, maxScroll);
    }

    private void drawInstallArea(GuiGraphics g, MinecraftUiCanvas canvas,
                                 int x, int y, int w, int mouseX, int mouseY) {
        this.installX = x;
        this.installY = y;
        this.installW = w;
        this.installH = PluginManagementLayout.INSTALL_H;
        boolean hover = inside(mouseX, mouseY, x, y, w, this.installH);
        double installHover = animate("install", hover, false);
        drawFrame(canvas, x, y, w, this.installH,
                PluginManagementStyle.installBackground(installHover),
                PluginManagementStyle.installBorder(installHover));
        this.refreshW = PluginManagementLayout.REFRESH_W;
        this.refreshH = PluginManagementLayout.REFRESH_H;
        this.refreshX = x + w - this.refreshW - PluginManagementLayout.REFRESH_RIGHT_INSET;
        this.refreshY = y + PluginManagementLayout.REFRESH_TOP;
        boolean refreshHover = inside(mouseX, mouseY, this.refreshX, this.refreshY, this.refreshW, this.refreshH);
        double refreshHoverStrength = animate("refresh", refreshHover, false);
        UiColor refreshFill = PluginManagementStyle.refreshBackground(
                this.refreshFeedbackTicks > 0, refreshHoverStrength);
        drawFrame(canvas, this.refreshX, this.refreshY, this.refreshW, this.refreshH, refreshFill,
                PluginManagementStyle.refreshBorder(refreshHoverStrength));
        RtsClientUiUtil.drawCenteredStringNoShadow(
                g, this.font, Component.translatable("screen.rtsbuilding.plugins.refresh"),
                this.refreshX + this.refreshW / 2,
                this.refreshY + PluginManagementLayout.REFRESH_TEXT_TOP,
                PluginManagementStyle.PRIMARY_TEXT.toArgb());
        g.drawString(this.font, Component.translatable("screen.rtsbuilding.plugins.install_area"),
                x + PluginManagementLayout.CONTENT_TEXT_X,
                y + PluginManagementLayout.INSTALL_TITLE_Y,
                PluginManagementStyle.PRIMARY_TEXT.toArgb(), false);
        Component hint = this.selectedInventorySlot >= 0
                ? Component.translatable("screen.rtsbuilding.plugins.drop_to_install")
                : Component.translatable("screen.rtsbuilding.plugins.pick_hint");
        drawWrapped(g, hint,
                x + PluginManagementLayout.CONTENT_TEXT_X,
                y + PluginManagementLayout.INSTALL_HINT_Y,
                PluginManagementLayout.contentWidth(w),
                PluginManagementStyle.MUTED_TEXT.toArgb());
    }

    private void drawInventoryPlugins(GuiGraphics g, MinecraftUiCanvas canvas,
                                      PluginManagementLayout.Layout layout,
                                      int mouseX, int mouseY) {
        g.drawString(this.font, Component.translatable("screen.rtsbuilding.plugins.inventory"),
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
            UiControlAnimationState.Snapshot slotAnimation = animateState(
                    "inventory." + i, hover, selected);
            UiColor fill = PluginManagementStyle.slotBackground(
                    plugin, slotAnimation.selection());
            drawFrame(canvas, sx, sy, slot.width, slot.height, fill,
                    PluginManagementStyle.slotBorder(slotAnimation.hover()));
            if (!stack.isEmpty()) {
                g.renderItem(stack,
                        sx + PluginManagementLayout.SLOT_CONTENT_INSET,
                        sy + PluginManagementLayout.SLOT_CONTENT_INSET);
                g.renderItemDecorations(this.font, stack,
                        sx + PluginManagementLayout.SLOT_CONTENT_INSET,
                        sy + PluginManagementLayout.SLOT_CONTENT_INSET);
            }
        }
    }

    private String installedPluginAt(double mouseX, double mouseY) {
        if (!this.hoveredInstalledPluginId.isBlank()) {
            return this.hoveredInstalledPluginId;
        }
        return "";
    }

    private double animate(String stableId, boolean hovered, boolean selected) {
        return animateState(stableId, hovered, selected).hover();
    }

    private UiControlAnimationState.Snapshot animateState(
            String stableId, boolean hovered, boolean selected) {
        UiControlState state = new UiControlState(
                true, selected, false, false, "")
                .withInteraction(hovered, false, false);
        return this.controlAnimations.update(
                stableId, state, Config.isUiAnimationsEnabled());
    }

    private int installedUninstallX() {
        PluginManagementLayout.Layout layout = resolveLayout();
        return layout.installed.right() - 50;
    }

    private int installedUninstallY(String pluginId) {
        PluginManagementLayout.Layout layout = resolveLayout();
        String teamName = this.controller.getPluginTeamName();
        PluginManagementLayout.InstalledRows rows = PluginManagementLayout.installedRows(
                layout, teamName != null && !teamName.isBlank(),
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
        PluginManagementLayout.Layout layout = resolveLayout();
        PluginManagementLayout.Rect grid = layout.inventoryGrid;
        if (!inside(mouseX, mouseY, grid.x, grid.y, grid.width, grid.height)) {
            return -1;
        }
        int col = Mth.floor((mouseX - grid.x) / PluginManagementLayout.SLOT);
        int row = Mth.floor((mouseY - grid.y) / PluginManagementLayout.SLOT);
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
        int thumbH = Mth.clamp(trackH * visibleRows / Math.max(1, totalRows),
                PluginManagementLayout.SCROLL_MIN_H, trackH);
        int thumbY = contentY + (trackH - thumbH) * this.installedScroll / maxScroll;
        canvas.fill(trackX, thumbY, 3, thumbH, PluginManagementStyle.SCROLL_THUMB);
    }

    private InstalledListMetrics installedListMetrics() {
        PluginManagementLayout.Layout layout = resolveLayout();
        String teamName = this.controller.getPluginTeamName();
        PluginManagementLayout.InstalledRows rows = PluginManagementLayout.installedRows(
                layout, teamName != null && !teamName.isBlank(),
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
        return this.font.plainSubstrByWidth(text == null ? "" : text, Math.max(8, width));
    }

    private boolean inside(double x, double y, int rx, int ry, int rw, int rh) {
        return x >= rx && x < rx + rw && y >= ry && y < ry + rh;
    }

    private record InstalledListMetrics(int x, int y, int w, int h, int maxScroll) {
    }
}
