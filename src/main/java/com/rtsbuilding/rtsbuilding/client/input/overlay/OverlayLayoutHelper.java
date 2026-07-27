package com.rtsbuilding.rtsbuilding.client.input.overlay;

import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.RtsCraftTerminalScreen;
import com.rtsbuilding.rtsbuilding.common.persist.RtsClientUiStateStore;
import com.rtsbuilding.rtsbuilding.uikit.theme.ContainerOverlayStyle;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiColor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.util.math.MathHelper;

import java.awt.Rectangle;

import static com.rtsbuilding.rtsbuilding.client.input.RtsClientInputGate.overlayCollapsed;
import static com.rtsbuilding.rtsbuilding.client.input.RtsClientInputGate.overlayCraftCollapsed;

public final class OverlayLayoutHelper {
    private OverlayLayoutHelper() {
    }

    // =========================================================================
    //  Exported constants
    // =========================================================================

    public static final int OVERLAY_MARGIN = 6;
    public static final int CRAFT_PANEL_W = 104;
    public static final int CRAFT_PANEL_COLLAPSED_W = 44;
    public static final int PANEL_GAP = 5;
    public static final int STORAGE_PANEL_W = 142;
    public static final int SLOT_PITCH = 18;
    public static final int SLOT_SIZE = 16;
    public static final int STORAGE_COLS = 5;
    public static final int STORAGE_ROWS = 3;
    public static final int QUICKBAR_SLOTS = 5;
    public static final int CRAFT_COLS = 4;
    public static final int CRAFT_SLOT = 18;
    public static final int CRAFT_PITCH = 20;
    public static final int CRAFT_SEARCH_H = 12;
    public static final int CRAFT_APPLY_W = 18;
    public static final int CRAFT_TOGGLE_W = 34;
    public static final int RETURN_SLOTS = 2;
    public static final int PAGE_BUTTON_W = 14;
    public static final int PAGE_BUTTON_H = 11;
    public static final double OVERLAY_TARGET_GUI_SCALE = 3.0D;
    public static final double HIGH_SCALE_COMPACT_THRESHOLD = 3.0D;
    public static final double EXTREME_SCALE_COMPACT_THRESHOLD = 5.5D;
    public static final int STACKED_CRAFT_ROWS = 2;
    public static final int QUICKBAR_Y_OFF = 17;
    public static final int GRID_Y_OFF = QUICKBAR_Y_OFF + SLOT_SIZE + 6;
    public static final int OVERLAY_HEADER_Y = 3;
    public static final int OVERLAY_HEADER_H = 11;
    public static final int OVERLAY_CLOSE_W = 34;
    public static final int OVERLAY_COLLAPSE_W = 52;
    public static final int OVERLAY_BOTTOM_SMALL_W = 14;
    public static final int OVERLAY_BOTTOM_BUTTON_H = 12;
    public static final int OVERLAY_BOTTOM_GAP = 4;
    public static final int OVERLAY_WINDOW_TITLE_H = 16;
    public static final int OVERLAY_INFO_PANEL_W = 228;
    public static final int OVERLAY_INFO_TITLE_H = 18;
    public static final int OVERLAY_INFO_CLOSE_SIZE = 12;
    public static final int OVERLAY_SORT_X = 41;
    public static final int OVERLAY_DIR_X = OVERLAY_SORT_X + 14;
    public static final int OVERLAY_SEARCH_X = OVERLAY_DIR_X + 16;
    public static final int OVERLAY_SEARCH_CLEAR_W = 10;
    public static final int OVERLAY_SEARCH_MAX = 64;
    public static final int OVERLAY_DRAG_W = 32;
    public static final long RETURN_PREVIEW_MS = 2000L;
    public static final int INVENTORY_RTS_BUTTON_W = 70;
    public static final int INVENTORY_RTS_BUTTON_H = 14;
    public static final int INVENTORY_RTS_BUTTON_GAP = 4;

    // =========================================================================
    //  Records
    // =========================================================================

    public record JeiOverlayIngredient(net.minecraft.item.ItemStack stack, Rectangle area) {
    }

    public record ButtonLayout(int x, int y, int w, int h) {
    }

    public record OverlayProfile(double guiScale, double renderScale, int storageRows, boolean stackCraftBelow) {
    }

    public record VisibleOverlayLayout(OverlayProfile profile, OverlayLayout layout) {
    }

    public record OverlayInfoRect(int x, int y, int w, int h, int closeX, int closeY) {
    }

    public record OverlayLayout(
            int screenW,
            int screenH,
            int panelX,
            int panelY,
            int panelW,
            int panelH,
            boolean overlayCollapsed,
            boolean stackCraftBelow,
            int craftPanelX,
            int craftPanelY,
            int craftPanelW,
            int craftPanelH,
            boolean craftCollapsed,
            int storageRows,
            int storagePanelX,
            int storagePanelY,
            int storagePanelH,
            int headerY,
            int pageX,
            int pagePrevY,
            int pageTextY,
            int pageNextY,
            int searchX,
            int searchW,
            int clearX,
            int craftSearchX,
            int craftSearchY,
            int craftSearchW,
            int craftApplyX,
            int craftToggleX,
            int craftGridY,
            int craftVisibleRows) {

        public int dragX() {
            return this.storagePanelX + 6;
        }

        public int sortX() {
            return this.storagePanelX + OVERLAY_SORT_X;
        }

        public int dirX() {
            return this.storagePanelX + OVERLAY_DIR_X;
        }

        public int quickbarX() {
            return this.storagePanelX + 6;
        }

        public int quickbarY() {
            return this.storagePanelY + QUICKBAR_Y_OFF;
        }

        public int gridX() {
            return this.storagePanelX + 6;
        }

        public int gridY() {
            if (this.overlayCollapsed) {
                return this.storagePanelY + QUICKBAR_Y_OFF;
            }
            return this.storagePanelY + GRID_Y_OFF;
        }

        public int returnX() {
            return this.storagePanelX + 6;
        }

        public int shiftImportX() {
            return this.returnX() + RETURN_SLOTS * SLOT_PITCH + OVERLAY_BOTTOM_GAP;
        }

        public int shiftImportW() {
            int right = this.storagePanelX + STORAGE_PANEL_W - 6;
            return Math.max(48, right - this.shiftImportX());
        }

        public int controlsY() {
            if (this.overlayCollapsed) {
                return this.storagePanelY + collapsedControlsYOff();
            }
            return this.storagePanelY + GRID_Y_OFF + this.storageRows * SLOT_PITCH + 2;
        }

        public int returnY() {
            return this.controlsY() + OVERLAY_BOTTOM_BUTTON_H + 4;
        }

        public int closeX() {
            return this.storagePanelX + 6;
        }

        public int collapseX() {
            return this.closeX() + OVERLAY_CLOSE_W + OVERLAY_BOTTOM_GAP;
        }

        public int refreshX() {
            return this.collapseX() + OVERLAY_COLLAPSE_W + OVERLAY_BOTTOM_GAP;
        }

        public int infoX() {
            return this.refreshX() + OVERLAY_BOTTOM_SMALL_W + OVERLAY_BOTTOM_GAP;
        }
    }

    // =========================================================================
    //  Layout resolution
    // =========================================================================

    public static OverlayProfile overlayProfile() {
        double guiScale = currentGuiScale();
        boolean highScale = guiScale > HIGH_SCALE_COMPACT_THRESHOLD;
        boolean extremeScale = guiScale >= EXTREME_SCALE_COMPACT_THRESHOLD;
        double renderScale = highScale
                ? MathHelper.clamp(OVERLAY_TARGET_GUI_SCALE / guiScale, 0.45D, 1.0D)
                : 1.0D;
        int rows = extremeScale ? 2 : highScale ? 3 : STORAGE_ROWS;
        return new OverlayProfile(guiScale, renderScale, rows, highScale);
    }

    public static double currentGuiScale() {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.displayWidth <= 0) {
            return OVERLAY_TARGET_GUI_SCALE;
        }
        ScaledResolution resolution = new ScaledResolution(minecraft);
        double scale = minecraft.displayWidth / (double) Math.max(1, resolution.getScaledWidth());
        return scale > 0.0D && Double.isFinite(scale) ? scale : OVERLAY_TARGET_GUI_SCALE;
    }

    public static double toOverlayMouse(double value, OverlayProfile profile) {
        return value / Math.max(0.001D, profile.renderScale());
    }

    public static int overlayVirtualWidth(OverlayProfile profile) {
        Minecraft minecraft = Minecraft.getMinecraft();
        int width = minecraft == null ? 1 : new ScaledResolution(minecraft).getScaledWidth();
        return Math.max(1, (int) Math.round(width / Math.max(0.001D, profile.renderScale())));
    }

    public static int overlayVirtualHeight(OverlayProfile profile) {
        Minecraft minecraft = Minecraft.getMinecraft();
        int height = minecraft == null ? 1 : new ScaledResolution(minecraft).getScaledHeight();
        return Math.max(1, (int) Math.round(height / Math.max(0.001D, profile.renderScale())));
    }

    public static Rectangle toGuiRect(int x, int y, int w, int h, double scale) {
        int rx = (int) Math.round(x * scale);
        int ry = (int) Math.round(y * scale);
        int rw = Math.max(1, (int) Math.round(w * scale));
        int rh = Math.max(1, (int) Math.round(h * scale));
        return new Rectangle(rx, ry, rw, rh);
    }

    public static int resolveOverlayX(int screenWidth, OverlayProfile profile) {
        int minX = OVERLAY_MARGIN;
        int maxX = Math.max(minX, screenWidth - currentOverlayWidth(profile) - OVERLAY_MARGIN);
        return minX + (int) Math.round((maxX - minX) * ClientRtsController.get().getStoragePanelXNormalized());
    }

    public static int resolveOverlayY(int screenHeight, OverlayProfile profile) {
        int minY = OVERLAY_MARGIN;
        int maxY = Math.max(minY, screenHeight - overlayHeight(profile) - OVERLAY_MARGIN);
        return minY + (int) Math.round((maxY - minY) * ClientRtsController.get().getStoragePanelYNormalized());
    }

    public static OverlayLayout resolveOverlayLayout(GuiScreen screen) {
        return resolveOverlayLayout(overlayProfile());
    }

    public static VisibleOverlayLayout resolveVisibleOverlayLayout(GuiScreen screen) {
        if (!shouldRenderContainerOverlay(screen)) {
            return null;
        }
        OverlayProfile profile = overlayProfile();
        return new VisibleOverlayLayout(profile, resolveOverlayLayout(profile));
    }

    public static boolean shouldRenderContainerOverlay(GuiScreen screen) {
        if (screen == null
                || screen instanceof BuilderScreen
                || screen instanceof RtsCraftTerminalScreen
                || !(screen instanceof GuiContainer)) {
            return false;
        }
        return RtsClientUiStateStore.isContainerOverlayEnabled()
                && ClientRtsController.get().canUseStorageOverlay();
    }

    public static OverlayLayout resolveOverlayLayout(OverlayProfile profile) {
        int sw = overlayVirtualWidth(profile);
        int sh = overlayVirtualHeight(profile);
        int panelW = currentOverlayWidth(profile);
        int panelH = overlayHeight(profile);
        int panelX = MathHelper.clamp(resolveOverlayX(sw, profile), OVERLAY_MARGIN, Math.max(OVERLAY_MARGIN, sw - panelW - OVERLAY_MARGIN));
        int panelY = MathHelper.clamp(resolveOverlayY(sh, profile), OVERLAY_MARGIN, Math.max(OVERLAY_MARGIN, sh - panelH - OVERLAY_MARGIN));
        boolean stacked = profile.stackCraftBelow();
        boolean collapsed = overlayCollapsed;
        boolean craftCollapsed = collapsed || isCraftPanelCollapsed(profile);
        int storagePanelH = storagePanelHeight(profile);
        int craftPanelW = stacked ? STORAGE_PANEL_W : craftCollapsed ? CRAFT_PANEL_COLLAPSED_W : CRAFT_PANEL_W;
        int craftPanelH = stacked ? craftPanelHeight(profile) : storagePanelH;
        int storagePanelX = collapsed || stacked ? panelX : panelX + craftPanelW + PANEL_GAP;
        int storagePanelY = panelY;
        int craftPanelX = panelX;
        int craftPanelY = stacked ? panelY + storagePanelH + PANEL_GAP : panelY;
        int headerY = storagePanelY + OVERLAY_HEADER_Y;
        int pageX = storagePanelX + STORAGE_PANEL_W - PAGE_BUTTON_W - 6;
        int pagePrevY = storagePanelY + 3;
        int pageTextY = pagePrevY + PAGE_BUTTON_H + 2;
        int pageNextY = pageTextY + 10;
        int searchX = storagePanelX + OVERLAY_SEARCH_X;
        int searchRight = collapsed ? storagePanelX + STORAGE_PANEL_W - 6 : pageX - 4;
        int searchW = Math.max(26, searchRight - searchX);
        int clearX = searchX + searchW - OVERLAY_SEARCH_CLEAR_W;
        int craftSearchX = craftPanelX + 4;
        int craftSearchY = craftPanelY + 15;
        int craftSearchW = Math.max(24, craftPanelW - CRAFT_APPLY_W - CRAFT_TOGGLE_W - 16);
        int craftApplyX = craftSearchX + craftSearchW + 4;
        int craftToggleX = craftApplyX + CRAFT_APPLY_W + 4;
        int craftGridY = craftSearchY + CRAFT_SEARCH_H + 6;
        int craftVisibleRows = Math.max(1, (craftPanelH - (craftGridY - craftPanelY) - 6) / CRAFT_PITCH);
        return new OverlayLayout(
                sw, sh, panelX, panelY, panelW, panelH, collapsed, stacked,
                craftPanelX, craftPanelY, craftPanelW, craftPanelH, craftCollapsed,
                profile.storageRows(),
                storagePanelX, storagePanelY, storagePanelH,
                headerY, pageX, pagePrevY, pageTextY, pageNextY,
                searchX, searchW, clearX,
                craftSearchX, craftSearchY, craftSearchW, craftApplyX, craftToggleX,
                craftGridY, craftVisibleRows);
    }

    // =========================================================================
    //  Dimension helpers
    // =========================================================================

    public static int currentOverlayWidth() {
        return currentOverlayWidth(overlayProfile());
    }

    public static int currentOverlayWidth(OverlayProfile profile) {
        if (overlayCollapsed) {
            return STORAGE_PANEL_W;
        }
        if (profile.stackCraftBelow()) {
            return STORAGE_PANEL_W;
        }
        int craftW = isCraftPanelCollapsed(profile) ? CRAFT_PANEL_COLLAPSED_W : CRAFT_PANEL_W;
        return craftW + PANEL_GAP + STORAGE_PANEL_W;
    }

    public static int overlayHeight(OverlayProfile profile) {
        if (overlayCollapsed) {
            return collapsedControlsYOff() + OVERLAY_BOTTOM_BUTTON_H + 6;
        }
        if (profile.stackCraftBelow()) {
            return craftPanelHeight(profile) + PANEL_GAP + storagePanelHeight(profile);
        }
        return storagePanelHeight(profile);
    }

    public static int storagePanelHeight(OverlayProfile profile) {
        if (overlayCollapsed) {
            return collapsedControlsYOff() + OVERLAY_BOTTOM_BUTTON_H + 6;
        }
        return returnYOff(profile) + SLOT_SIZE + 6;
    }

    public static int craftPanelHeight(OverlayProfile profile) {
        if (isCraftPanelCollapsed(profile)) {
            return OVERLAY_HEADER_H + 7;
        }
        if (profile.stackCraftBelow()) {
            return 15 + CRAFT_SEARCH_H + 6 + STACKED_CRAFT_ROWS * CRAFT_PITCH + 6;
        }
        return storagePanelHeight(profile);
    }

    public static int returnLabelYOff(OverlayProfile profile) {
        return GRID_Y_OFF + profile.storageRows() * SLOT_PITCH + 2;
    }

    public static int returnYOff(OverlayProfile profile) {
        return returnLabelYOff(profile) + OVERLAY_BOTTOM_BUTTON_H + 4;
    }

    public static int collapsedControlsYOff() {
        return QUICKBAR_Y_OFF + SLOT_SIZE + 4;
    }

    public static boolean isCraftPanelCollapsed(OverlayProfile profile) {
        return overlayCraftCollapsed;
    }

    // =========================================================================
    //  Drawing helpers
    // =========================================================================

    public static void drawPanelFrame(LegacyGuiGraphics g, FontRenderer font, int x, int y, int w, int h,
                                      UiColor fillColor, UiColor light, UiColor dark) {
        g.fill(x, y, x + w, y + h, fillColor.toArgb());
        g.fill(x, y, x + w, y + 1, light.toArgb());
        g.fill(x, y, x + 1, y + h, light.toArgb());
        g.fill(x, y + h - 1, x + w, y + h, dark.toArgb());
        g.fill(x + w - 1, y, x + w, y + h, dark.toArgb());
    }

    public static void drawOverlayWindowFrame(LegacyGuiGraphics g, FontRenderer font, int x, int y, int w, int h) {
        drawPanelFrame(g, font, x, y, w, h, ContainerOverlayStyle.WINDOW_BACKGROUND,
                ContainerOverlayStyle.WINDOW_BORDER_LIGHT, ContainerOverlayStyle.WINDOW_BORDER_DARK);
        g.fill(x + 1, y + 1, x + w - 1, y + OVERLAY_WINDOW_TITLE_H,
                ContainerOverlayStyle.WINDOW_TITLE.toArgb());
    }

    public static void drawMiniButton(LegacyGuiGraphics g, FontRenderer font, int x, int y, int w, int h, String label) {
        drawPanelFrame(g, font, x, y, w, h,
                ContainerOverlayStyle.MINI_BUTTON_BACKGROUND,
                ContainerOverlayStyle.BUTTON_BORDER_LIGHT,
                ContainerOverlayStyle.BUTTON_BORDER_DARK);
        g.drawCenteredString(font, label, x + w / 2, y + 2, ContainerOverlayStyle.BUTTON_TEXT.toArgb());
    }

    public static void drawSlotCountOverlay(LegacyGuiGraphics g, FontRenderer font, int slotX, int slotY,
            int slotSize, String countText, UiColor color) {
        if (countText == null || countText.isEmpty()) return;
        int x = slotX + slotSize - font.getStringWidth(countText) - 1;
        g.drawString(font, countText, x, slotY + slotSize - 8, color.toArgb());
    }

    public static String sortShort(com.rtsbuilding.rtsbuilding.network.storage.RtsStorageSort sort) {
        return switch (sort) {
            case QUANTITY -> "Q";
            case MOD -> "M";
            case NAME -> "N";
        };
    }

    public static String trimToWidth(FontRenderer font, String text, int maxWidth) {
        return font.trimStringToWidth(text == null ? "" : text, Math.max(0, maxWidth), false);
    }

    public static double normalizeBetween(int value, int min, int max) {
        if (max <= min) {
            return 0.0D;
        }
        return MathHelper.clamp((value - (double) min) / (double) (max - min), 0.0D, 1.0D);
    }

    public static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }
}
