package com.rtsbuilding.rtsbuilding.client.input.overlay;

import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.screen.canvas.MinecraftUiCanvas;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.RtsCraftTerminalScreen;
import com.rtsbuilding.rtsbuilding.client.util.RtsClientUiUtil;
import com.rtsbuilding.rtsbuilding.common.persist.RtsClientUiStateStore;
import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uikit.canvas.UiCompactFrameRenderer;
import com.rtsbuilding.rtsbuilding.uikit.canvas.UiChromeRenderer;
import com.rtsbuilding.rtsbuilding.uikit.theme.ContainerOverlayStyle;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiColor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.gui.inventory.GuiContainer;
import com.rtsbuilding.rtsbuilding.platform.math.MathHelper;

import java.awt.Rectangle;
import java.util.Objects;

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

    /** JEI 4 查询覆盖层物品时使用的不可变值对象。 */
    public static final class JeiOverlayIngredient {
        private final net.minecraft.item.ItemStack stack;
        private final Rectangle area;

        public JeiOverlayIngredient(net.minecraft.item.ItemStack stack, Rectangle area) {
            this.stack = stack == null ? null : stack.copy();
            this.area = area == null ? null : new Rectangle(area);
        }

        public net.minecraft.item.ItemStack stack() {
            return com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(this.stack) ? null : this.stack.copy();
        }

        public Rectangle area() {
            return this.area == null ? null : new Rectangle(this.area);
        }
    }

    public static final class ButtonLayout {
        private final int x, y, w, h;
        public ButtonLayout(int x, int y, int w, int h) {
            this.x = x; this.y = y; this.w = w; this.h = h;
        }
        public int x() { return x; }
        public int y() { return y; }
        public int w() { return w; }
        public int h() { return h; }
        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof ButtonLayout)) return false;
            ButtonLayout value = (ButtonLayout) other;
            return x == value.x && y == value.y && w == value.w && h == value.h;
        }
        @Override public int hashCode() { return Objects.hash(x, y, w, h); }
        @Override public String toString() {
            return "ButtonLayout[x=" + x + ", y=" + y + ", w=" + w + ", h=" + h + ']';
        }
    }

    public static final class OverlayProfile {
        private final double guiScale, renderScale;
        private final int storageRows;
        private final boolean stackCraftBelow;
        public OverlayProfile(double guiScale, double renderScale, int storageRows, boolean stackCraftBelow) {
            this.guiScale = guiScale; this.renderScale = renderScale;
            this.storageRows = storageRows; this.stackCraftBelow = stackCraftBelow;
        }
        public double guiScale() { return guiScale; }
        public double renderScale() { return renderScale; }
        public int storageRows() { return storageRows; }
        public boolean stackCraftBelow() { return stackCraftBelow; }
        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof OverlayProfile)) return false;
            OverlayProfile value = (OverlayProfile) other;
            return Double.compare(guiScale, value.guiScale) == 0
                    && Double.compare(renderScale, value.renderScale) == 0
                    && storageRows == value.storageRows
                    && stackCraftBelow == value.stackCraftBelow;
        }
        @Override public int hashCode() {
            return Objects.hash(guiScale, renderScale, storageRows, stackCraftBelow);
        }
        @Override public String toString() {
            return "OverlayProfile[guiScale=" + guiScale + ", renderScale=" + renderScale
                    + ", storageRows=" + storageRows + ", stackCraftBelow=" + stackCraftBelow + ']';
        }
    }

    public static final class VisibleOverlayLayout {
        private final OverlayProfile profile;
        private final OverlayLayout layout;
        public VisibleOverlayLayout(OverlayProfile profile, OverlayLayout layout) {
            this.profile = profile; this.layout = layout;
        }
        public OverlayProfile profile() { return profile; }
        public OverlayLayout layout() { return layout; }
        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof VisibleOverlayLayout)) return false;
            VisibleOverlayLayout value = (VisibleOverlayLayout) other;
            return Objects.equals(profile, value.profile) && Objects.equals(layout, value.layout);
        }
        @Override public int hashCode() { return Objects.hash(profile, layout); }
        @Override public String toString() {
            return "VisibleOverlayLayout[profile=" + profile + ", layout=" + layout + ']';
        }
    }

    public static final class OverlayInfoRect {
        private final int x, y, w, h, closeX, closeY;
        public OverlayInfoRect(int x, int y, int w, int h, int closeX, int closeY) {
            this.x = x; this.y = y; this.w = w; this.h = h;
            this.closeX = closeX; this.closeY = closeY;
        }
        public int x() { return x; }
        public int y() { return y; }
        public int w() { return w; }
        public int h() { return h; }
        public int closeX() { return closeX; }
        public int closeY() { return closeY; }
        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof OverlayInfoRect)) return false;
            OverlayInfoRect value = (OverlayInfoRect) other;
            return x == value.x && y == value.y && w == value.w && h == value.h
                    && closeX == value.closeX && closeY == value.closeY;
        }
        @Override public int hashCode() { return Objects.hash(x, y, w, h, closeX, closeY); }
        @Override public String toString() {
            return "OverlayInfoRect[x=" + x + ", y=" + y + ", w=" + w + ", h=" + h
                    + ", closeX=" + closeX + ", closeY=" + closeY + ']';
        }
    }

    public static final class OverlayLayout {
        private final int screenW, screenH, panelX, panelY, panelW, panelH;
        private final boolean overlayCollapsed, stackCraftBelow;
        private final int craftPanelX, craftPanelY, craftPanelW, craftPanelH;
        private final boolean craftCollapsed;
        private final int storageRows, storagePanelX, storagePanelY, storagePanelH;
        private final int headerY, pageX, pagePrevY, pageTextY, pageNextY;
        private final int searchX, searchW, clearX;
        private final int craftSearchX, craftSearchY, craftSearchW, craftApplyX, craftToggleX;
        private final int craftGridY, craftVisibleRows;

        public OverlayLayout(
                int screenW, int screenH, int panelX, int panelY, int panelW, int panelH,
                boolean overlayCollapsed, boolean stackCraftBelow,
                int craftPanelX, int craftPanelY, int craftPanelW, int craftPanelH,
                boolean craftCollapsed, int storageRows,
                int storagePanelX, int storagePanelY, int storagePanelH,
                int headerY, int pageX, int pagePrevY, int pageTextY, int pageNextY,
                int searchX, int searchW, int clearX,
                int craftSearchX, int craftSearchY, int craftSearchW,
                int craftApplyX, int craftToggleX, int craftGridY, int craftVisibleRows) {
            this.screenW = screenW; this.screenH = screenH;
            this.panelX = panelX; this.panelY = panelY; this.panelW = panelW; this.panelH = panelH;
            this.overlayCollapsed = overlayCollapsed; this.stackCraftBelow = stackCraftBelow;
            this.craftPanelX = craftPanelX; this.craftPanelY = craftPanelY;
            this.craftPanelW = craftPanelW; this.craftPanelH = craftPanelH;
            this.craftCollapsed = craftCollapsed; this.storageRows = storageRows;
            this.storagePanelX = storagePanelX; this.storagePanelY = storagePanelY;
            this.storagePanelH = storagePanelH; this.headerY = headerY;
            this.pageX = pageX; this.pagePrevY = pagePrevY;
            this.pageTextY = pageTextY; this.pageNextY = pageNextY;
            this.searchX = searchX; this.searchW = searchW; this.clearX = clearX;
            this.craftSearchX = craftSearchX; this.craftSearchY = craftSearchY;
            this.craftSearchW = craftSearchW; this.craftApplyX = craftApplyX;
            this.craftToggleX = craftToggleX; this.craftGridY = craftGridY;
            this.craftVisibleRows = craftVisibleRows;
        }

        public int screenW() { return screenW; }
        public int screenH() { return screenH; }
        public int panelX() { return panelX; }
        public int panelY() { return panelY; }
        public int panelW() { return panelW; }
        public int panelH() { return panelH; }
        public boolean overlayCollapsed() { return overlayCollapsed; }
        public boolean stackCraftBelow() { return stackCraftBelow; }
        public int craftPanelX() { return craftPanelX; }
        public int craftPanelY() { return craftPanelY; }
        public int craftPanelW() { return craftPanelW; }
        public int craftPanelH() { return craftPanelH; }
        public boolean craftCollapsed() { return craftCollapsed; }
        public int storageRows() { return storageRows; }
        public int storagePanelX() { return storagePanelX; }
        public int storagePanelY() { return storagePanelY; }
        public int storagePanelH() { return storagePanelH; }
        public int headerY() { return headerY; }
        public int pageX() { return pageX; }
        public int pagePrevY() { return pagePrevY; }
        public int pageTextY() { return pageTextY; }
        public int pageNextY() { return pageNextY; }
        public int searchX() { return searchX; }
        public int searchW() { return searchW; }
        public int clearX() { return clearX; }
        public int craftSearchX() { return craftSearchX; }
        public int craftSearchY() { return craftSearchY; }
        public int craftSearchW() { return craftSearchW; }
        public int craftApplyX() { return craftApplyX; }
        public int craftToggleX() { return craftToggleX; }
        public int craftGridY() { return craftGridY; }
        public int craftVisibleRows() { return craftVisibleRows; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof OverlayLayout)) return false;
            OverlayLayout value = (OverlayLayout) other;
            return screenW == value.screenW && screenH == value.screenH
                    && panelX == value.panelX && panelY == value.panelY
                    && panelW == value.panelW && panelH == value.panelH
                    && overlayCollapsed == value.overlayCollapsed
                    && stackCraftBelow == value.stackCraftBelow
                    && craftPanelX == value.craftPanelX && craftPanelY == value.craftPanelY
                    && craftPanelW == value.craftPanelW && craftPanelH == value.craftPanelH
                    && craftCollapsed == value.craftCollapsed && storageRows == value.storageRows
                    && storagePanelX == value.storagePanelX && storagePanelY == value.storagePanelY
                    && storagePanelH == value.storagePanelH && headerY == value.headerY
                    && pageX == value.pageX && pagePrevY == value.pagePrevY
                    && pageTextY == value.pageTextY && pageNextY == value.pageNextY
                    && searchX == value.searchX && searchW == value.searchW && clearX == value.clearX
                    && craftSearchX == value.craftSearchX && craftSearchY == value.craftSearchY
                    && craftSearchW == value.craftSearchW && craftApplyX == value.craftApplyX
                    && craftToggleX == value.craftToggleX && craftGridY == value.craftGridY
                    && craftVisibleRows == value.craftVisibleRows;
        }

        @Override public int hashCode() {
            return Objects.hash(screenW, screenH, panelX, panelY, panelW, panelH,
                    overlayCollapsed, stackCraftBelow,
                    craftPanelX, craftPanelY, craftPanelW, craftPanelH, craftCollapsed,
                    storageRows, storagePanelX, storagePanelY, storagePanelH,
                    headerY, pageX, pagePrevY, pageTextY, pageNextY,
                    searchX, searchW, clearX,
                    craftSearchX, craftSearchY, craftSearchW, craftApplyX, craftToggleX,
                    craftGridY, craftVisibleRows);
        }

        @Override public String toString() {
            return "OverlayLayout[screenW=" + screenW + ", screenH=" + screenH
                    + ", panelX=" + panelX + ", panelY=" + panelY
                    + ", panelW=" + panelW + ", panelH=" + panelH
                    + ", overlayCollapsed=" + overlayCollapsed
                    + ", stackCraftBelow=" + stackCraftBelow
                    + ", craftPanelX=" + craftPanelX + ", craftPanelY=" + craftPanelY
                    + ", craftPanelW=" + craftPanelW + ", craftPanelH=" + craftPanelH
                    + ", craftCollapsed=" + craftCollapsed + ", storageRows=" + storageRows
                    + ", storagePanelX=" + storagePanelX + ", storagePanelY=" + storagePanelY
                    + ", storagePanelH=" + storagePanelH + ", headerY=" + headerY
                    + ", pageX=" + pageX + ", pagePrevY=" + pagePrevY
                    + ", pageTextY=" + pageTextY + ", pageNextY=" + pageNextY
                    + ", searchX=" + searchX + ", searchW=" + searchW
                    + ", clearX=" + clearX + ", craftSearchX=" + craftSearchX
                    + ", craftSearchY=" + craftSearchY + ", craftSearchW=" + craftSearchW
                    + ", craftApplyX=" + craftApplyX + ", craftToggleX=" + craftToggleX
                    + ", craftGridY=" + craftGridY + ", craftVisibleRows=" + craftVisibleRows + ']';
        }

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

    /**
     * 返回容器 overlay 一页能够完整画出的物品数。
     *
     * <p>分页请求必须和响应式布局使用同一份行列定义；否则服务端会切出比界面更多的条目，
     * 那些未绘制条目会在翻页时被永久跳过。收起面板只改变临时可见区域，不改变分页边界。</p>
     */
    public static int overlayStoragePageCapacity(OverlayProfile profile) {
        return STORAGE_COLS * Math.max(1, profile.storageRows());
    }

    public static double currentGuiScale() {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.displayWidth <= 0) {
            return OVERLAY_TARGET_GUI_SCALE;
        }
        ScaledResolution resolution = new ScaledResolution(minecraft, minecraft.displayWidth, minecraft.displayHeight);
        double scale = minecraft.displayWidth / (double) Math.max(1, resolution.getScaledWidth());
        return scale > 0.0D && Double.isFinite(scale) ? scale : OVERLAY_TARGET_GUI_SCALE;
    }

    public static double toOverlayMouse(double value, OverlayProfile profile) {
        return value / Math.max(0.001D, profile.renderScale());
    }

    public static int overlayVirtualWidth(OverlayProfile profile) {
        Minecraft minecraft = Minecraft.getMinecraft();
        int width = minecraft == null ? 1 : new ScaledResolution(minecraft, minecraft.displayWidth, minecraft.displayHeight).getScaledWidth();
        return Math.max(1, (int) Math.round(width / Math.max(0.001D, profile.renderScale())));
    }

    public static int overlayVirtualHeight(OverlayProfile profile) {
        Minecraft minecraft = Minecraft.getMinecraft();
        int height = minecraft == null ? 1 : new ScaledResolution(minecraft, minecraft.displayWidth, minecraft.displayHeight).getScaledHeight();
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
        UiChromeRenderer.frame(new MinecraftUiCanvas(g, font), new UiRect(x, y, w, h),
                1.0D, fillColor, light, dark);
    }

    public static void drawOverlayWindowFrame(LegacyGuiGraphics g, FontRenderer font, int x, int y, int w, int h) {
        drawPanelFrame(g, font, x, y, w, h, ContainerOverlayStyle.WINDOW_BACKGROUND,
                ContainerOverlayStyle.WINDOW_BORDER_LIGHT, ContainerOverlayStyle.WINDOW_BORDER_DARK);
        g.fill(x + 1, y + 1, x + w - 1, y + OVERLAY_WINDOW_TITLE_H,
                ContainerOverlayStyle.WINDOW_TITLE.toArgb());
    }

    public static void drawMiniButton(LegacyGuiGraphics g, FontRenderer font, int x, int y, int w, int h, String label) {
        UiCompactFrameRenderer.frame(new MinecraftUiCanvas(g, font), new UiRect(x, y, w, h),
                ContainerOverlayStyle.MINI_BUTTON_BACKGROUND,
                ContainerOverlayStyle.BUTTON_BORDER_LIGHT,
                ContainerOverlayStyle.BUTTON_BORDER_DARK);
        g.drawCenteredString(font, label, x + w / 2, y + 2, ContainerOverlayStyle.BUTTON_TEXT.toArgb());
    }

    public static void drawSlotCountOverlay(LegacyGuiGraphics g, FontRenderer font, int slotX, int slotY,
            int slotSize, String countText, UiColor color) {
        // 容器 Overlay 与 RTS 底栏必须共享同一数量覆盖层：先画物品，再在高 Z 层画
        // 深色底带和缩放后的无阴影数字。旧版在槽位平面直接画文字，1.12 RenderItem
        // 开启深度后会让后续方块模型遮住数量，且与底栏视觉逻辑分叉。
        RtsClientUiUtil.drawSlotCountOverlay(
                g, font, slotX, slotY, slotSize, countText, color.toArgb());
    }

    public static String sortShort(com.rtsbuilding.rtsbuilding.network.storage.RtsStorageSort sort) {
        if (sort == com.rtsbuilding.rtsbuilding.network.storage.RtsStorageSort.QUANTITY) return "Q";
        if (sort == com.rtsbuilding.rtsbuilding.network.storage.RtsStorageSort.MOD) return "M";
        return "N";
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
        return UiRect.contains(x, y, w, h, mouseX, mouseY);
    }
}
