package com.rtsbuilding.rtsbuilding.client.input.overlay;

import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.record.CraftableEntry;
import com.rtsbuilding.rtsbuilding.client.screen.canvas.MinecraftUiCanvas;
import com.rtsbuilding.rtsbuilding.client.util.RtsClientUiUtil;
import com.rtsbuilding.rtsbuilding.common.persist.RtsClientUiStateStore;
import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uikit.animation.SystemUiClock;
import com.rtsbuilding.rtsbuilding.uikit.animation.UiBlink;
import com.rtsbuilding.rtsbuilding.uikit.canvas.UiChromeRenderer;
import com.rtsbuilding.rtsbuilding.uikit.theme.BottomPanelCraftStyle;
import com.rtsbuilding.rtsbuilding.uikit.theme.ContainerOverlayStyle;
import com.rtsbuilding.rtsbuilding.uikit.theme.RtsMainlineTheme;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiColor;
import net.minecraft.client.gui.Font;
import com.rtsbuilding.rtsbuilding.client.screen.canvas.RtsGuiContext;
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
            RtsGuiContext g,
            Font font,
            double mouseX,
            double mouseY,
            OverlayLayout layout,
            ClientRtsController controller) {
        MinecraftUiCanvas canvas = new MinecraftUiCanvas(g, font);
        String header = layout.craftCollapsed() ? "Craft +" : "Craft -";
        g.drawString(font, trimToWidth(font, header, Math.max(8, layout.craftPanelW() - 8)),
                layout.craftPanelX() + 5, layout.craftPanelY() + 4,
                ContainerOverlayStyle.SEARCH_TEXT.toArgb(), false);
        if (layout.craftCollapsed()) {
            return;
        }

        drawPanelFrame(canvas, layout.craftSearchX(), layout.craftSearchY(),
                layout.craftSearchW(), CRAFT_SEARCH_H,
                ContainerOverlayStyle.searchBackground(overlayCraftSearchFocused),
                BottomPanelCraftStyle.SEARCH_BORDER_LIGHT,
                BottomPanelCraftStyle.SEARCH_BORDER_DARK);
        String searchText = overlayCraftSearchDraft == null ? "" : overlayCraftSearchDraft;
        String display = trimToWidth(font, searchText, Math.max(10, layout.craftSearchW() - 5));
        g.drawString(font, display, layout.craftSearchX() + 2, layout.craftSearchY() + 2,
                ContainerOverlayStyle.SEARCH_TEXT.toArgb(), false);
        if (overlayCraftSearchFocused && UiBlink.caretVisible(SystemUiClock.INSTANCE)) {
            int caretX = layout.craftSearchX() + 2 + font.width(display) + 1;
            g.fill(caretX, layout.craftSearchY() + 2, caretX + 1,
                    layout.craftSearchY() + CRAFT_SEARCH_H - 2,
                    ContainerOverlayStyle.SEARCH_TEXT.toArgb());
        }

        boolean craftSearchDirty = OverlayInteraction.hasPendingOverlayCraftSearch();
        drawPanelFrame(canvas, layout.craftApplyX(), layout.craftSearchY(),
                CRAFT_APPLY_W, CRAFT_SEARCH_H,
                BottomPanelCraftStyle.applyBackground(craftSearchDirty),
                BottomPanelCraftStyle.BUTTON_BORDER_LIGHT,
                BottomPanelCraftStyle.BUTTON_BORDER_DARK);
        RtsClientUiUtil.drawCenteredStringNoShadow(g, font,
                "OK",
                layout.craftApplyX() + CRAFT_APPLY_W / 2,
                layout.craftSearchY() + 2,
                (craftSearchDirty
                        ? BottomPanelCraftStyle.BUTTON_TEXT
                        : BottomPanelCraftStyle.BUTTON_TEXT_IDLE).toArgb());

        drawPanelFrame(canvas, layout.craftToggleX(), layout.craftSearchY(),
                CRAFT_TOGGLE_W, CRAFT_SEARCH_H,
                BottomPanelCraftStyle.toggleBackground(controller.isCraftablesShowUnavailable()),
                BottomPanelCraftStyle.TOGGLE_BORDER_LIGHT,
                BottomPanelCraftStyle.BUTTON_BORDER_DARK);
        RtsClientUiUtil.drawCenteredStringNoShadow(g, font,
                controller.isCraftablesShowUnavailable() ? "ALL" : "MAKE",
                layout.craftToggleX() + CRAFT_TOGGLE_W / 2,
                layout.craftSearchY() + 2,
                BottomPanelCraftStyle.BUTTON_TEXT.toArgb());

        List<CraftableEntry> entries = controller.getCraftableEntries();
        int maxScroll = OverlayInteraction.maxOverlayCraftScroll(controller, layout.craftVisibleRows());
        overlayCraftScroll = Mth.clamp(overlayCraftScroll, 0, maxScroll);
        int startIndex = overlayCraftScroll * CRAFT_COLS;

        for (int row = 0; row < layout.craftVisibleRows(); row++) {
            for (int col = 0; col < CRAFT_COLS; col++) {
                int index = startIndex + row * CRAFT_COLS + col;
                int slotX = layout.craftPanelX() + 4 + col * CRAFT_PITCH;
                int slotY = layout.craftGridY() + row * CRAFT_PITCH;
                boolean present = index < entries.size();
                boolean available = present && entries.get(index).craftable();
                drawPanelFrame(canvas, slotX, slotY, CRAFT_SLOT, CRAFT_SLOT,
                        BottomPanelCraftStyle.slotBackground(present, available),
                        BottomPanelCraftStyle.SLOT_BORDER_LIGHT,
                        BottomPanelCraftStyle.SLOT_BORDER_DARK);
                if (index >= entries.size()) {
                    continue;
                }

                CraftableEntry entry = entries.get(index);
                g.renderItem(entry.stack(), slotX + 1, slotY + 1);
                if (entry.resultCount() > 1) {
                    drawSlotCountOverlay(g, font, slotX, slotY, CRAFT_SLOT,
                            RtsClientUiUtil.compactCount(entry.resultCount()),
                            BottomPanelCraftStyle.SLOT_COUNT);
                }
                if (!entry.craftable()) {
                    g.fill(slotX + 1, slotY + 1, slotX + CRAFT_SLOT - 1,
                            slotY + CRAFT_SLOT - 1,
                            BottomPanelCraftStyle.SLOT_UNAVAILABLE_OVERLAY.toArgb());
                }
                if (inside(mouseX, mouseY, slotX, slotY, CRAFT_SLOT, CRAFT_SLOT)) {
                    g.fill(slotX + 1, slotY + 1, slotX + CRAFT_SLOT - 1,
                            slotY + CRAFT_SLOT - 1,
                            BottomPanelCraftStyle.SLOT_HOVER_OVERLAY.toArgb());
                }
            }
        }
    }

    // =========================================================================
    //  Info button
    // =========================================================================

    public static void renderOverlayInfoButton(RtsGuiContext g, Font font, OverlayLayout layout, double mouseX, double mouseY) {
        boolean active = overlayInfoOpen || inside(mouseX, mouseY, layout.infoX(),
                layout.controlsY(), OVERLAY_BOTTOM_SMALL_W, OVERLAY_BOTTOM_BUTTON_H);
        drawPanelFrame(g, font, layout.infoX(), layout.controlsY(),
                OVERLAY_BOTTOM_SMALL_W, OVERLAY_BOTTOM_BUTTON_H,
                ContainerOverlayStyle.controlBackground(active),
                ContainerOverlayStyle.BUTTON_BORDER_LIGHT,
                ContainerOverlayStyle.BUTTON_BORDER_DARK);
        RtsClientUiUtil.drawCenteredStringNoShadow(g, font, "i",
                layout.infoX() + OVERLAY_BOTTOM_SMALL_W / 2, layout.controlsY() + 2,
                ContainerOverlayStyle.SEARCH_TEXT.toArgb());
    }

    // =========================================================================
    //  Shift import button
    // =========================================================================

    public static void renderOverlayShiftImportButton(RtsGuiContext g, Font font, OverlayLayout layout, double mouseX, double mouseY) {
        boolean enabled = RtsClientUiStateStore.isOverlayShiftImportEnabled();
        boolean hovered = inside(mouseX, mouseY, layout.shiftImportX(), layout.returnY(), layout.shiftImportW(), SLOT_SIZE);
        UiColor light = enabled
                ? ContainerOverlayStyle.SHIFT_IMPORT_BORDER_LIGHT
                : ContainerOverlayStyle.BUTTON_BORDER_LIGHT;
        UiColor dark = enabled
                ? ContainerOverlayStyle.SHIFT_IMPORT_BORDER_DARK
                : ContainerOverlayStyle.BUTTON_BORDER_DARK;
        drawPanelFrame(g, font, layout.shiftImportX(), layout.returnY(),
                layout.shiftImportW(), SLOT_SIZE,
                ContainerOverlayStyle.shiftImportBackground(enabled, hovered),
                light, dark);
        RtsClientUiUtil.drawCenteredStringNoShadow(
                g,
                font,
                Component.translatable("screen.rtsbuilding.overlay.shift_import_button").getString(),
                layout.shiftImportX() + layout.shiftImportW() / 2,
                layout.returnY() + 4,
                ContainerOverlayStyle.SEARCH_TEXT.toArgb());
    }

    // =========================================================================
    //  Bottom controls (close / collapse)
    // =========================================================================

    public static void renderOverlayBottomControls(
            RtsGuiContext g,
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
            RtsGuiContext g,
            Font font,
            OverlayLayout layout,
            double mouseX,
            double mouseY,
            ClientRtsController controller) {
        boolean hovered = inside(mouseX, mouseY, layout.refreshX(), layout.controlsY(), OVERLAY_BOTTOM_SMALL_W, OVERLAY_BOTTOM_BUTTON_H);
        drawPanelFrame(g, font, layout.refreshX(), layout.controlsY(),
                OVERLAY_BOTTOM_SMALL_W, OVERLAY_BOTTOM_BUTTON_H,
                ContainerOverlayStyle.refreshBackground(
                        controller.isStorageScanRunning(), hovered),
                ContainerOverlayStyle.BUTTON_BORDER_LIGHT,
                ContainerOverlayStyle.BUTTON_BORDER_DARK);
        RtsClientUiUtil.drawCenteredStringNoShadow(g, font, "R",
                layout.refreshX() + OVERLAY_BOTTOM_SMALL_W / 2, layout.controlsY() + 2,
                ContainerOverlayStyle.SEARCH_TEXT.toArgb());
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

    public static void renderOverlayInfoPanel(RtsGuiContext g, Font font, OverlayLayout layout) {
        OverlayInfoRect rect = resolveOverlayInfoRect(font, layout);
        List<Component> lines = overlayInfoLines();

        UiChromeRenderer.frame(new MinecraftUiCanvas(g, font),
                new UiRect(rect.x(), rect.y(), rect.w(), rect.h()), 1.0D,
                ContainerOverlayStyle.WINDOW_BACKGROUND,
                ContainerOverlayStyle.WINDOW_BORDER_LIGHT,
                ContainerOverlayStyle.WINDOW_BORDER_DARK);
        g.fill(rect.x() + 1, rect.y() + 1, rect.x() + rect.w() - 1,
                rect.y() + OVERLAY_INFO_TITLE_H,
                ContainerOverlayStyle.WINDOW_TITLE.toArgb());
        g.drawString(font, Component.translatable("screen.rtsbuilding.overlay.help.title"),
                rect.x() + 7, rect.y() + 5,
                ContainerOverlayStyle.WINDOW_TITLE_TEXT.toArgb(), false);
        drawPanelFrame(g, font, rect.closeX(), rect.closeY(),
                OVERLAY_INFO_CLOSE_SIZE, OVERLAY_INFO_CLOSE_SIZE,
                ContainerOverlayStyle.INFO_CLOSE_BACKGROUND,
                ContainerOverlayStyle.INFO_CLOSE_BORDER_LIGHT,
                RtsMainlineTheme.WINDOW_BORDER_DARK);
        RtsClientUiUtil.drawCenteredStringNoShadow(g, font, "x",
                rect.closeX() + OVERLAY_INFO_CLOSE_SIZE / 2, rect.closeY() + 2,
                ContainerOverlayStyle.WINDOW_TITLE_TEXT.toArgb());

        int textY = rect.y() + OVERLAY_INFO_TITLE_H + 5;
        for (Component line : lines) {
            for (var splitLine : font.split(line, rect.w() - 12)) {
                g.drawString(font, splitLine, rect.x() + 6, textY,
                        ContainerOverlayStyle.INFO_BODY_TEXT.toArgb(), false);
                textY += 9;
            }
        }
    }

    // =========================================================================
    //  Quickbar rendering
    // =========================================================================

    public static void renderQuickbar(RtsGuiContext g, Font font, int x, int y) {
        ClientRtsController controller = ClientRtsController.get();
        MinecraftUiCanvas canvas = new MinecraftUiCanvas(g, font);
        for (int i = 0; i < QUICKBAR_SLOTS; i++) {
            int cx = x + i * SLOT_PITCH;
            int cy = y;
            ItemStack preview = controller.getQuickSlotPreview(i);
            String itemId = controller.getQuickSlotItemId(i);
            boolean filled = itemId != null && !itemId.isBlank();
            drawPanelFrame(canvas, cx, cy, SLOT_SIZE, SLOT_SIZE,
                    filled
                            ? ContainerOverlayStyle.QUICK_SLOT_FILLED
                            : ContainerOverlayStyle.QUICK_SLOT_EMPTY,
                    ContainerOverlayStyle.QUICK_SLOT_BORDER_LIGHT,
                    ContainerOverlayStyle.QUICK_SLOT_BORDER_DARK);

            if (!preview.isEmpty()) {
                g.renderItem(preview, cx + 1, cy + 1);
                if (itemId.equals(controller.getSelectedItemId())) {
                    g.fill(cx + 1, cy + 1, cx + SLOT_SIZE - 1, cy + SLOT_SIZE - 1,
                            ContainerOverlayStyle.QUICK_SLOT_SELECTED.toArgb());
                }
                drawSlotCountOverlay(g, font, cx, cy, SLOT_SIZE,
                        RtsClientUiUtil.compactCount(
                                OverlayInteraction.resolvePinnedItemCount(itemId)),
                        ContainerOverlayStyle.STORAGE_COUNT);
            } else {
                RtsClientUiUtil.drawCenteredStringNoShadow(g, font, Integer.toString(i + 1),
                        cx + SLOT_SIZE / 2, cy + 5,
                        ContainerOverlayStyle.QUICK_SLOT_EMPTY_TEXT.toArgb());
            }
        }
    }
}
