package com.rtsbuilding.rtsbuilding.client.input.overlay;

import com.rtsbuilding.rtsbuilding.uikit.theme.RtsMainlineTheme;

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
                layout.craftPanelX() + 5, layout.craftPanelY() + 4, RtsMainlineTheme.LEGACY_EAF2FF.toArgb(), false);
        if (layout.craftCollapsed()) {
            return;
        }

        int searchBg = overlayCraftSearchFocused ? RtsMainlineTheme.LEGACY_AA304153.toArgb() : RtsMainlineTheme.LEGACY_AA202731.toArgb();
        drawPanelFrame(g, layout.craftSearchX(), layout.craftSearchY(), layout.craftSearchW(), CRAFT_SEARCH_H, searchBg, RtsMainlineTheme.LEGACY_FF5E738A.toArgb(), RtsMainlineTheme.LEGACY_FF111921.toArgb());
        String searchText = overlayCraftSearchDraft == null ? "" : overlayCraftSearchDraft;
        String display = trimToWidth(font, searchText, Math.max(10, layout.craftSearchW() - 5));
        g .text(font, display, layout.craftSearchX() + 2, layout.craftSearchY() + 2, RtsMainlineTheme.LEGACY_EAF2FF.toArgb(), false);
        if (overlayCraftSearchFocused && (System.currentTimeMillis() / 300L) % 2L == 0L) {
            int caretX = layout.craftSearchX() + 2 + font.width(display) + 1;
            g.fill(caretX, layout.craftSearchY() + 2, caretX + 1, layout.craftSearchY() + CRAFT_SEARCH_H - 2, RtsMainlineTheme.LEGACY_FFEAF2FF.toArgb());
        }

        boolean craftSearchDirty = OverlayInteraction.hasPendingOverlayCraftSearch();
        int applyBg = craftSearchDirty ? RtsMainlineTheme.LEGACY_AA4C6E39.toArgb() : RtsMainlineTheme.LEGACY_AA24303A.toArgb();
        drawPanelFrame(g, layout.craftApplyX(), layout.craftSearchY(), CRAFT_APPLY_W, CRAFT_SEARCH_H, applyBg, RtsMainlineTheme.LEGACY_FF6E8799.toArgb(), RtsMainlineTheme.LEGACY_FF111821.toArgb());
        RtsClientUiUtil.drawCenteredStringNoShadow(g, font,
                "OK",
                layout.craftApplyX() + CRAFT_APPLY_W / 2,
                layout.craftSearchY() + 2,
                craftSearchDirty ? RtsMainlineTheme.LEGACY_FFFFFF.toArgb() : RtsMainlineTheme.LEGACY_FFB8C7D6.toArgb());

        int toggleBg = controller.isCraftablesShowUnavailable() ? RtsMainlineTheme.LEGACY_AA5A3D2A.toArgb() : RtsMainlineTheme.LEGACY_AA2C5A41.toArgb();
        drawPanelFrame(g, layout.craftToggleX(), layout.craftSearchY(), CRAFT_TOGGLE_W, CRAFT_SEARCH_H, toggleBg, RtsMainlineTheme.LEGACY_FF667D95.toArgb(), RtsMainlineTheme.LEGACY_FF111821.toArgb());
        RtsClientUiUtil.drawCenteredStringNoShadow(g, font,
                controller.isCraftablesShowUnavailable() ? "ALL" : "MAKE",
                layout.craftToggleX() + CRAFT_TOGGLE_W / 2,
                layout.craftSearchY() + 2,
                RtsMainlineTheme.LEGACY_FFFFFF.toArgb());

        List<CraftableEntry> entries = controller.getCraftableEntries();
        int maxScroll = OverlayInteraction.maxOverlayCraftScroll(controller, layout.craftVisibleRows());
        overlayCraftScroll = Mth.clamp(overlayCraftScroll, 0, maxScroll);
        int startIndex = overlayCraftScroll * CRAFT_COLS;

        for (int row = 0; row < layout.craftVisibleRows(); row++) {
            for (int col = 0; col < CRAFT_COLS; col++) {
                int index = startIndex + row * CRAFT_COLS + col;
                int slotX = layout.craftPanelX() + 4 + col * CRAFT_PITCH;
                int slotY = layout.craftGridY() + row * CRAFT_PITCH;
                int fill = RtsMainlineTheme.LEGACY_AA1A212B.toArgb();
                if (index < entries.size()) {
                    fill = entries.get(index).craftable() ? RtsMainlineTheme.LEGACY_AA214131.toArgb() : RtsMainlineTheme.LEGACY_AA3F2323.toArgb();
                }
                drawPanelFrame(g, slotX, slotY, CRAFT_SLOT, CRAFT_SLOT, fill, RtsMainlineTheme.LEGACY_FF596D84.toArgb(), RtsMainlineTheme.LEGACY_FF11171E.toArgb());
                if (index >= entries.size()) {
                    continue;
                }

                CraftableEntry entry = entries.get(index);
                g .item(entry.stack(), slotX + 1, slotY + 1);
                if (entry.resultCount() > 1) {
                    drawSlotCountOverlay(g, font, slotX, slotY, CRAFT_SLOT, RtsClientUiUtil.compactCount(entry.resultCount()), RtsMainlineTheme.LEGACY_FFE8F4FF.toArgb());
                }
                if (!entry.craftable()) {
                    g.fill(slotX + 1, slotY + 1, slotX + CRAFT_SLOT - 1, slotY + CRAFT_SLOT - 1, RtsMainlineTheme.LEGACY_44220000.toArgb());
                }
                if (inside(mouseX, mouseY, slotX, slotY, CRAFT_SLOT, CRAFT_SLOT)) {
                    g.fill(slotX + 1, slotY + 1, slotX + CRAFT_SLOT - 1, slotY + CRAFT_SLOT - 1, RtsMainlineTheme.LEGACY_22FFFFFF.toArgb());
                }
            }
        }
    }

    // =========================================================================
    //  Info button
    // =========================================================================

    public static void renderOverlayInfoButton(GuiGraphicsExtractor g, Font font, OverlayLayout layout, double mouseX, double mouseY) {
        int bg = overlayInfoOpen || inside(mouseX, mouseY, layout.infoX(), layout.controlsY(), OVERLAY_BOTTOM_SMALL_W, OVERLAY_BOTTOM_BUTTON_H)
                ? RtsMainlineTheme.LEGACY_AA3E5368.toArgb()
                : RtsMainlineTheme.LEGACY_AA24303A.toArgb();
        drawPanelFrame(g, layout.infoX(), layout.controlsY(), OVERLAY_BOTTOM_SMALL_W, OVERLAY_BOTTOM_BUTTON_H, bg, RtsMainlineTheme.LEGACY_FF6E8799.toArgb(), RtsMainlineTheme.LEGACY_FF111821.toArgb());
        RtsClientUiUtil.drawCenteredStringNoShadow(g, font, "i",
                layout.infoX() + OVERLAY_BOTTOM_SMALL_W / 2, layout.controlsY() + 2, RtsMainlineTheme.LEGACY_FFEAF2FF.toArgb());
    }

    // =========================================================================
    //  Shift import button
    // =========================================================================

    public static void renderOverlayShiftImportButton(GuiGraphicsExtractor g, Font font, OverlayLayout layout, double mouseX, double mouseY) {
        boolean enabled = RtsClientUiStateStore.isOverlayShiftImportEnabled();
        boolean hovered = inside(mouseX, mouseY, layout.shiftImportX(), layout.returnY(), layout.shiftImportW(), SLOT_SIZE);
        int bg = enabled
                ? hovered ? RtsMainlineTheme.LEGACY_CC3AA156.toArgb() : RtsMainlineTheme.LEGACY_CC2C873F.toArgb()
                : hovered ? RtsMainlineTheme.LEGACY_AA3E5368.toArgb() : RtsMainlineTheme.LEGACY_AA24303A.toArgb();
        int light = enabled ? RtsMainlineTheme.LEGACY_FF74E88C.toArgb() : RtsMainlineTheme.LEGACY_FF6E8799.toArgb();
        int dark = enabled ? RtsMainlineTheme.LEGACY_FF123A1D.toArgb() : RtsMainlineTheme.LEGACY_FF111821.toArgb();
        drawPanelFrame(g, layout.shiftImportX(), layout.returnY(), layout.shiftImportW(), SLOT_SIZE, bg, light, dark);
        RtsClientUiUtil.drawCenteredStringNoShadow(
                g,
                font,
                Component.translatable("screen.rtsbuilding.overlay.shift_import_button").getString(),
                layout.shiftImportX() + layout.shiftImportW() / 2,
                layout.returnY() + 4,
                RtsMainlineTheme.LEGACY_FFEAF2FF.toArgb());
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
                ? RtsMainlineTheme.LEGACY_AA3F627E.toArgb()
                : hovered ? RtsMainlineTheme.LEGACY_AA3E5368.toArgb() : RtsMainlineTheme.LEGACY_AA24303A.toArgb();
        drawPanelFrame(g, layout.refreshX(), layout.controlsY(), OVERLAY_BOTTOM_SMALL_W, OVERLAY_BOTTOM_BUTTON_H, bg, RtsMainlineTheme.LEGACY_FF6E8799.toArgb(), RtsMainlineTheme.LEGACY_FF111821.toArgb());
        RtsClientUiUtil.drawCenteredStringNoShadow(g, font, "R",
                layout.refreshX() + OVERLAY_BOTTOM_SMALL_W / 2, layout.controlsY() + 2, RtsMainlineTheme.LEGACY_FFEAF2FF.toArgb());
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

        drawPanelFrame(g, rect.x(), rect.y(), rect.w(), rect.h(), RtsMainlineTheme.LEGACY_F0182028.toArgb(), RtsMainlineTheme.LEGACY_FF7489A0.toArgb(), RtsMainlineTheme.LEGACY_FF0B1016.toArgb());
        g.fill(rect.x() + 1, rect.y() + 1, rect.x() + rect.w() - 1,
                rect.y() + OVERLAY_INFO_TITLE_H, RtsMainlineTheme.LEGACY_CC233345.toArgb());
        g .text(font, Component.translatable("screen.rtsbuilding.overlay.help.title"),
                rect.x() + 7, rect.y() + 5, RtsMainlineTheme.LEGACY_F2F7FF.toArgb(), false);
        drawPanelFrame(g, rect.closeX(), rect.closeY(), OVERLAY_INFO_CLOSE_SIZE, OVERLAY_INFO_CLOSE_SIZE,
                RtsMainlineTheme.LEGACY_CC2B3440.toArgb(), RtsMainlineTheme.LEGACY_FF7F92A8.toArgb(), RtsMainlineTheme.LEGACY_FF0D1117.toArgb());
        RtsClientUiUtil.drawCenteredStringNoShadow(g, font, "x",
                rect.closeX() + OVERLAY_INFO_CLOSE_SIZE / 2, rect.closeY() + 2, RtsMainlineTheme.LEGACY_F2F7FF.toArgb());

        int textY = rect.y() + OVERLAY_INFO_TITLE_H + 5;
        for (Component line : lines) {
            for (var splitLine : font.split(line, rect.w() - 12)) {
                g .text(font, splitLine, rect.x() + 6, textY, RtsMainlineTheme.LEGACY_FFD8E6F5.toArgb(), false);
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
            int bg = filled ? RtsMainlineTheme.LEGACY_AA253043.toArgb() : RtsMainlineTheme.LEGACY_AA1A1A1A.toArgb();
            g.fill(cx, cy, cx + SLOT_SIZE, cy + SLOT_SIZE, bg);
            g.horizontalLine(cx, cx + SLOT_SIZE, cy, RtsMainlineTheme.LEGACY_FF67758A.toArgb());
            g.horizontalLine(cx, cx + SLOT_SIZE, cy + SLOT_SIZE, RtsMainlineTheme.LEGACY_FF0C0D10.toArgb());
            g.verticalLine(cx, cy, cy + SLOT_SIZE, RtsMainlineTheme.LEGACY_FF67758A.toArgb());
            g.verticalLine(cx + SLOT_SIZE, cy, cy + SLOT_SIZE, RtsMainlineTheme.LEGACY_FF0C0D10.toArgb());

            if (!preview.isEmpty()) {
                g .item(preview, cx + 1, cy + 1);
                if (itemId.equals(controller.getSelectedItemId())) {
                    g.fill(cx + 1, cy + 1, cx + SLOT_SIZE - 1, cy + SLOT_SIZE - 1, RtsMainlineTheme.LEGACY_3340FF80.toArgb());
                }
                drawSlotCountOverlay(g, font, cx, cy, SLOT_SIZE, RtsClientUiUtil.compactCount(OverlayInteraction.resolvePinnedItemCount(itemId)), RtsMainlineTheme.LEGACY_FFF7E6A8.toArgb());
            } else {
                RtsClientUiUtil.drawCenteredStringNoShadow(g, font, Integer.toString(i + 1),
                        cx + SLOT_SIZE / 2, cy + 5, RtsMainlineTheme.LEGACY_88D0D8E4.toArgb());
            }
        }
    }
}
