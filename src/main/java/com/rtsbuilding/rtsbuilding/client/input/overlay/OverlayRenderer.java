package com.rtsbuilding.rtsbuilding.client.input.overlay;

import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.record.CraftableEntry;
import com.rtsbuilding.rtsbuilding.client.util.RtsClientUiUtil;
import com.rtsbuilding.rtsbuilding.common.persist.RtsClientUiStateStore;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import java.util.List;

import static com.rtsbuilding.rtsbuilding.client.input.RtsClientInputGate.*;
import static com.rtsbuilding.rtsbuilding.client.input.overlay.OverlayLayoutHelper.*;

public final class OverlayRenderer {
    private OverlayRenderer() {
    }

    // =========================================================================
    //  Craftables panel
    // =========================================================================

    public static void renderOverlayCraftablesPanel(
            GuiGraphicsExtractor g,
            Font font,
            double mouseX,
            double mouseY,
            OverlayLayout layout,
            ClientRtsController controller) {
        String header = layout.craftCollapsed() ? "Craft +" : "Craft -";
        g .text(font, trimToWidth(font, header, Math.max(8, layout.craftPanelW() - 8)),
                layout.craftPanelX() + 5, layout.craftPanelY() + 4, 0xEAF2FF, false);
        if (layout.craftCollapsed()) {
            return;
        }

        int searchBg = overlayCraftSearchFocused ? 0xAA304153 : 0xAA202731;
        drawPanelFrame(g, layout.craftSearchX(), layout.craftSearchY(), layout.craftSearchW(), CRAFT_SEARCH_H, searchBg, 0xFF5E738A, 0xFF111921);
        String searchText = overlayCraftSearchDraft == null ? "" : overlayCraftSearchDraft;
        String display = trimToWidth(font, searchText, Math.max(10, layout.craftSearchW() - 5));
        g .text(font, display, layout.craftSearchX() + 2, layout.craftSearchY() + 2, 0xEAF2FF, false);
        if (overlayCraftSearchFocused && (System.currentTimeMillis() / 300L) % 2L == 0L) {
            int caretX = layout.craftSearchX() + 2 + font.width(display) + 1;
            g.fill(caretX, layout.craftSearchY() + 2, caretX + 1, layout.craftSearchY() + CRAFT_SEARCH_H - 2, 0xFFEAF2FF);
        }

        boolean craftSearchDirty = OverlayInteraction.hasPendingOverlayCraftSearch();
        int applyBg = craftSearchDirty ? 0xAA4C6E39 : 0xAA24303A;
        drawPanelFrame(g, layout.craftApplyX(), layout.craftSearchY(), CRAFT_APPLY_W, CRAFT_SEARCH_H, applyBg, 0xFF6E8799, 0xFF111821);
        RtsClientUiUtil.drawCenteredStringNoShadow(g, font,
                "OK",
                layout.craftApplyX() + CRAFT_APPLY_W / 2,
                layout.craftSearchY() + 2,
                craftSearchDirty ? 0xFFFFFF : 0xFFB8C7D6);

        int toggleBg = controller.isCraftablesShowUnavailable() ? 0xAA5A3D2A : 0xAA2C5A41;
        drawPanelFrame(g, layout.craftToggleX(), layout.craftSearchY(), CRAFT_TOGGLE_W, CRAFT_SEARCH_H, toggleBg, 0xFF667D95, 0xFF111821);
        RtsClientUiUtil.drawCenteredStringNoShadow(g, font,
                controller.isCraftablesShowUnavailable() ? "ALL" : "MAKE",
                layout.craftToggleX() + CRAFT_TOGGLE_W / 2,
                layout.craftSearchY() + 2,
                0xFFFFFF);

        List<CraftableEntry> entries = controller.getCraftableEntries();
        int maxScroll = OverlayInteraction.maxOverlayCraftScroll(controller, layout.craftVisibleRows());
        overlayCraftScroll = Mth.clamp(overlayCraftScroll, 0, maxScroll);
        int startIndex = overlayCraftScroll * CRAFT_COLS;

        for (int row = 0; row < layout.craftVisibleRows(); row++) {
            for (int col = 0; col < CRAFT_COLS; col++) {
                int index = startIndex + row * CRAFT_COLS + col;
                int slotX = layout.craftPanelX() + 4 + col * CRAFT_PITCH;
                int slotY = layout.craftGridY() + row * CRAFT_PITCH;
                int fill = 0xAA1A212B;
                if (index < entries.size()) {
                    fill = entries.get(index).craftable() ? 0xAA214131 : 0xAA3F2323;
                }
                drawPanelFrame(g, slotX, slotY, CRAFT_SLOT, CRAFT_SLOT, fill, 0xFF596D84, 0xFF11171E);
                if (index >= entries.size()) {
                    continue;
                }

                CraftableEntry entry = entries.get(index);
                g .item(entry.stack(), slotX + 1, slotY + 1);
                if (entry.resultCount() > 1) {
                    drawSlotCountOverlay(g, font, slotX, slotY, CRAFT_SLOT, RtsClientUiUtil.compactCount(entry.resultCount()), 0xFFE8F4FF);
                }
                if (!entry.craftable()) {
                    g.fill(slotX + 1, slotY + 1, slotX + CRAFT_SLOT - 1, slotY + CRAFT_SLOT - 1, 0x44220000);
                }
                if (inside(mouseX, mouseY, slotX, slotY, CRAFT_SLOT, CRAFT_SLOT)) {
                    g.fill(slotX + 1, slotY + 1, slotX + CRAFT_SLOT - 1, slotY + CRAFT_SLOT - 1, 0x22FFFFFF);
                }
            }
        }
    }

    // =========================================================================
    //  Info button
    // =========================================================================

    public static void renderOverlayInfoButton(GuiGraphicsExtractor g, Font font, OverlayLayout layout, double mouseX, double mouseY) {
        int bg = overlayInfoOpen || inside(mouseX, mouseY, layout.infoX(), layout.controlsY(), OVERLAY_BOTTOM_SMALL_W, OVERLAY_BOTTOM_BUTTON_H)
                ? 0xAA3E5368
                : 0xAA24303A;
        drawPanelFrame(g, layout.infoX(), layout.controlsY(), OVERLAY_BOTTOM_SMALL_W, OVERLAY_BOTTOM_BUTTON_H, bg, 0xFF6E8799, 0xFF111821);
        RtsClientUiUtil.drawCenteredStringNoShadow(g, font, "i",
                layout.infoX() + OVERLAY_BOTTOM_SMALL_W / 2, layout.controlsY() + 2, 0xFFEAF2FF);
    }

    // =========================================================================
    //  Shift import button
    // =========================================================================

    public static void renderOverlayShiftImportButton(GuiGraphicsExtractor g, Font font, OverlayLayout layout, double mouseX, double mouseY) {
        boolean enabled = RtsClientUiStateStore.isOverlayShiftImportEnabled();
        boolean hovered = inside(mouseX, mouseY, layout.shiftImportX(), layout.returnY(), layout.shiftImportW(), SLOT_SIZE);
        int bg = enabled
                ? hovered ? 0xCC3AA156 : 0xCC2C873F
                : hovered ? 0xAA3E5368 : 0xAA24303A;
        int light = enabled ? 0xFF74E88C : 0xFF6E8799;
        int dark = enabled ? 0xFF123A1D : 0xFF111821;
        drawPanelFrame(g, layout.shiftImportX(), layout.returnY(), layout.shiftImportW(), SLOT_SIZE, bg, light, dark);
        RtsClientUiUtil.drawCenteredStringNoShadow(
                g,
                font,
                Component.translatable("screen.rtsbuilding.overlay.shift_import_button").getString(),
                layout.shiftImportX() + layout.shiftImportW() / 2,
                layout.returnY() + 4,
                0xFFEAF2FF);
    }

    // =========================================================================
    //  Bottom controls (close / collapse)
    // =========================================================================

    public static void renderOverlayBottomControls(
            GuiGraphicsExtractor g,
            Font font,
            OverlayLayout layout) {
        drawMiniButton(g, font, layout.closeX(), layout.controlsY(), OVERLAY_CLOSE_W, OVERLAY_BOTTOM_BUTTON_H,
                Component.translatable("screen.rtsbuilding.overlay.close_button").getString());
        Component collapseLabel = Component.translatable(layout.overlayCollapsed()
                ? "screen.rtsbuilding.overlay.expand_button"
                : "screen.rtsbuilding.overlay.collapse_button");
        drawMiniButton(g, font, layout.collapseX(), layout.controlsY(), OVERLAY_COLLAPSE_W, OVERLAY_BOTTOM_BUTTON_H,
                collapseLabel.getString());
    }

    // =========================================================================
    //  Refresh button
    // =========================================================================

    public static void renderOverlayRefreshButton(
            GuiGraphicsExtractor g,
            Font font,
            OverlayLayout layout,
            double mouseX,
            double mouseY,
            ClientRtsController controller) {
        boolean hovered = inside(mouseX, mouseY, layout.refreshX(), layout.controlsY(), OVERLAY_BOTTOM_SMALL_W, OVERLAY_BOTTOM_BUTTON_H);
        int bg = controller.isStorageScanRunning()
                ? 0xAA3F627E
                : hovered ? 0xAA3E5368 : 0xAA24303A;
        drawPanelFrame(g, layout.refreshX(), layout.controlsY(), OVERLAY_BOTTOM_SMALL_W, OVERLAY_BOTTOM_BUTTON_H, bg, 0xFF6E8799, 0xFF111821);
        RtsClientUiUtil.drawCenteredStringNoShadow(g, font, "R",
                layout.refreshX() + OVERLAY_BOTTOM_SMALL_W / 2, layout.controlsY() + 2, 0xFFEAF2FF);
    }

    // =========================================================================
    //  Info panel
    // =========================================================================

    public static List<Component> overlayInfoLines() {
        return List.of(
                Component.translatable("screen.rtsbuilding.overlay.help.move"),
                Component.translatable("screen.rtsbuilding.overlay.help.sort"),
                Component.translatable("screen.rtsbuilding.overlay.help.direction"),
                Component.translatable("screen.rtsbuilding.overlay.help.search"),
                Component.translatable("screen.rtsbuilding.overlay.help.page"),
                Component.translatable("screen.rtsbuilding.overlay.help.refresh"),
                Component.translatable("screen.rtsbuilding.overlay.help.quick_slots"),
                Component.translatable("screen.rtsbuilding.overlay.help.return"),
                Component.translatable("screen.rtsbuilding.overlay.help.craft"),
                Component.translatable("screen.rtsbuilding.overlay.help.craft_item"),
                Component.translatable("screen.rtsbuilding.overlay.help.shift_drag"),
                Component.translatable("screen.rtsbuilding.overlay.help.tooltip"));
    }

    public static OverlayInfoRect resolveOverlayInfoRect(Font font, OverlayLayout layout) {
        List<Component> lines = overlayInfoLines();
        int panelW = OVERLAY_INFO_PANEL_W;
        int bodyH = 0;
        for (Component line : lines) {
            bodyH += Math.max(1, font.split(line, panelW - 12).size()) * 9;
        }
        int panelH = OVERLAY_INFO_TITLE_H + bodyH + 12;
        int sw = layout.screenW();
        int sh = layout.screenH();
        int x = Mth.clamp(layout.storagePanelX() + STORAGE_PANEL_W - panelW, 4, Math.max(4, sw - panelW - 4));
        int y = layout.panelY() + layout.panelH() + 4;
        if (y + panelH > sh - 4) {
            y = layout.panelY() - panelH - 4;
        }
        y = Mth.clamp(y, 4, Math.max(4, sh - panelH - 4));
        int closeX = x + panelW - OVERLAY_INFO_CLOSE_SIZE - 4;
        int closeY = y + 3;
        return new OverlayInfoRect(x, y, panelW, panelH, closeX, closeY);
    }

    public static void renderOverlayInfoPanel(GuiGraphicsExtractor g, Font font, OverlayLayout layout) {
        OverlayInfoRect rect = resolveOverlayInfoRect(font, layout);
        List<Component> lines = overlayInfoLines();

        drawPanelFrame(g, rect.x(), rect.y(), rect.w(), rect.h(), 0xF0182028, 0xFF7489A0, 0xFF0B1016);
        g.fill(rect.x() + 1, rect.y() + 1, rect.x() + rect.w() - 1,
                rect.y() + OVERLAY_INFO_TITLE_H, 0xCC233345);
        g .text(font, Component.translatable("screen.rtsbuilding.overlay.help.title"),
                rect.x() + 7, rect.y() + 5, 0xF2F7FF, false);
        drawPanelFrame(g, rect.closeX(), rect.closeY(), OVERLAY_INFO_CLOSE_SIZE, OVERLAY_INFO_CLOSE_SIZE,
                0xCC2B3440, 0xFF7F92A8, 0xFF0D1117);
        RtsClientUiUtil.drawCenteredStringNoShadow(g, font, "x",
                rect.closeX() + OVERLAY_INFO_CLOSE_SIZE / 2, rect.closeY() + 2, 0xF2F7FF);

        int textY = rect.y() + OVERLAY_INFO_TITLE_H + 5;
        for (Component line : lines) {
            for (var splitLine : font.split(line, rect.w() - 12)) {
                g .text(font, splitLine, rect.x() + 6, textY, 0xFFD8E6F5, false);
                textY += 9;
            }
        }
    }

    // =========================================================================
    //  Quickbar rendering
    // =========================================================================

    public static void renderQuickbar(GuiGraphicsExtractor g, Font font, int x, int y) {
        ClientRtsController controller = ClientRtsController.get();
        for (int i = 0; i < QUICKBAR_SLOTS; i++) {
            int cx = x + i * SLOT_PITCH;
            int cy = y;
            ItemStack preview = controller.getQuickSlotPreview(i);
            String itemId = controller.getQuickSlotItemId(i);
            boolean filled = itemId != null && !itemId.isBlank();
            int bg = filled ? 0xAA253043 : 0xAA1A1A1A;
            g.fill(cx, cy, cx + SLOT_SIZE, cy + SLOT_SIZE, bg);
            g.horizontalLine(cx, cx + SLOT_SIZE, cy, 0xFF67758A);
            g.horizontalLine(cx, cx + SLOT_SIZE, cy + SLOT_SIZE, 0xFF0C0D10);
            g.verticalLine(cx, cy, cy + SLOT_SIZE, 0xFF67758A);
            g.verticalLine(cx + SLOT_SIZE, cy, cy + SLOT_SIZE, 0xFF0C0D10);

            if (!preview.isEmpty()) {
                g .item(preview, cx + 1, cy + 1);
                if (itemId.equals(controller.getSelectedItemId())) {
                    g.fill(cx + 1, cy + 1, cx + SLOT_SIZE - 1, cy + SLOT_SIZE - 1, 0x3340FF80);
                }
                drawSlotCountOverlay(g, font, cx, cy, SLOT_SIZE, RtsClientUiUtil.compactCount(OverlayInteraction.resolvePinnedItemCount(itemId)), 0xFFF7E6A8);
            } else {
                RtsClientUiUtil.drawCenteredStringNoShadow(g, font, Integer.toString(i + 1),
                        cx + SLOT_SIZE / 2, cy + 5, 0x88D0D8E4);
            }
        }
    }
}
