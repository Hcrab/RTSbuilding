package com.rtsbuilding.rtsbuilding.client.presentation.panel.downbar;

import com.rtsbuilding.rtsbuilding.client.presentation.panel.base.api.RtsPanelApi;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.base.component.EdgeResizeHandler;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.base.overlay.DownOverlayLayer;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.downbar.overlay.LeftDownOverlayLayer;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.downbar.overlay.RightDownOverlayLayer;
import com.rtsbuilding.rtsbuilding.client.presentation.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.util.render.SpriteRenderer;
import com.rtsbuilding.rtsbuilding.client.util.render.model.NineSliceRegion;
import com.rtsbuilding.rtsbuilding.client.util.render.model.TextureInfo;
import com.rtsbuilding.rtsbuilding.common.persist.PersistableProperty;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Objects;


public final class DownSidebarPanel implements RtsPanelApi {

    
    private BuilderScreen screen;

    
    private int currentHeight = DownSidebarLayoutHelper.DOWN_BAR_HEIGHT;

    
    public void setCurrentHeight(int height) {
        this.currentHeight = Math.max(8, Math.min(height, this.screen != null ? this.screen.height / 4 : 2000));
    }

    
    public int getCurrentHeight() {
        return currentHeight;
    }

    

    
    private static final ResourceLocation BORDER_TEXTURE = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/down/down_ui.png");
    
    private static final int TEX_W = 32;
    
    private static final int TEX_FILE_H = 32;
    
    private static final int STATE_H = 16;
    
    private static final int BORDER = 2;
    private static final TextureInfo DOWN_TEX_INFO = new TextureInfo(
            BORDER_TEXTURE, TEX_W, TEX_FILE_H,
            TextureInfo.ThemeLayout.HORIZONTAL_PAIR,
            TextureInfo.FilterMode.PIXEL);
    private static final NineSliceRegion DOWN_NINE_SLICE = NineSliceRegion.fullTheme(
            DOWN_TEX_INFO, STATE_H, BORDER);

    
    private final DownSidebarLayoutHelper layout = new DownSidebarLayoutHelper();

    
    private final EdgeResizeHandler resizeHandler = new EdgeResizeHandler(
            EdgeResizeHandler.Orientation.VERTICAL,
            EdgeResizeHandler.Side.LEADING,
            8);

    

    
    private final LeftDownOverlayLayer leftLayer = new LeftDownOverlayLayer();
    
    private final RightDownOverlayLayer rightLayer = new RightDownOverlayLayer();

    
    public RightDownOverlayLayer getRightLayer() { return rightLayer; }

    

    
    private int leftOverlayWidth = -1;

    
    private boolean isDraggingOverlayDivider;

    
    private int dragOverlayDividerStartX;

    
    private int dragOverlayDividerStartLeftW;

    
    private static final int OVERLAY_DIVIDER_HALF_HIT = 2;

    
    private static final int OVERLAY_MIN_SIZE = 160;

    @Override
    public void init(BuilderScreen screen) {
        this.screen = Objects.requireNonNull(screen,
                "DownSidebarPanel.init() called with null screen");
    }

    
    private int defaultLeftOverlayWidth(int totalW) {
        int gap = 1;
        return Math.max(OVERLAY_MIN_SIZE, (totalW - gap) * 8 / 21);
    }

    
    private int clampLeftOverlayWidth(int w, int totalW) {
        int gap = 1;
        int maxLeft = totalW - gap - OVERLAY_MIN_SIZE;
        return Math.max(OVERLAY_MIN_SIZE, Math.min(maxLeft, w));
    }

    
    private int resolveLeftOverlayWidth() {
        DownSidebarLayoutHelper.Rect db = layoutRect();
        if (this.leftOverlayWidth <= 0) {
            return defaultLeftOverlayWidth(db.width());
        }
        return clampLeftOverlayWidth(this.leftOverlayWidth, db.width());
    }

    

    
    private DownSidebarLayoutHelper.Rect layoutRect() {
        return layout.downBarRect(
                this.screen.width, this.screen.height, this.screen.getRightSidebarWidth(), this.currentHeight);
    }

    

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        DownSidebarLayoutHelper.Rect db = layoutRect();
        if (db.width() <= 0 || db.height() <= 0) return;

        int srcY = resizeHandler.isActive() ? STATE_H : 0;
        SpriteRenderer.drawNineSlice(g, DOWN_NINE_SLICE.withTheme().withVOffset(srcY),
                db.x(), db.y(), db.width(), db.height());
    }

    
    @Override
    public void renderOverlays(GuiGraphics g, int mouseX, int mouseY) {
        DownSidebarLayoutHelper.Rect db = layoutRect();
        
        int oy = db.y() + 1;
        int oh = db.height() - 1;
        if (db.width() <= 0 || oh <= 0) return;

        int totalW = db.width();
        int gap = 1;

        
        int leftW = resolveLeftOverlayWidth();

        
        leftLayer.setBounds(db.x(), oy, leftW, oh);
        leftLayer.setDividerDragging(isDraggingOverlayDivider);
        leftLayer.setLastMousePos(mouseX, mouseY);
        leftLayer.render(g, isDraggingOverlayDivider || isMouseInLayer(leftLayer, mouseX, mouseY));

        
        int rightX = db.x() + leftW + gap;
        int rightW = totalW - leftW - gap;
        if (rightW > 0) {
            rightLayer.setBounds(rightX, oy, rightW, oh);
            rightLayer.setDividerDragging(isDraggingOverlayDivider);
            rightLayer.setLastMousePos(mouseX, mouseY);
            rightLayer.render(g, isDraggingOverlayDivider || isMouseInLayer(rightLayer, mouseX, mouseY));
        }
    }

    
    private boolean isMouseInLayer(DownOverlayLayer layer, int mouseX, int mouseY) {
        if (this.screen == null || this.screen.isMouseOverUI(mouseX, mouseY)) return false;
        return layer.contains(mouseX, mouseY);
    }

    
    public boolean isMouseOverOverlayDivider(int mx, int my) {
        return isMouseOverDownOverlayDivider(mx, my);
    }

    
    private boolean isMouseOverDownOverlayDivider(int mx, int my) {
        DownSidebarLayoutHelper.Rect db = layoutRect();
        if (db.width() <= 0 || db.height() <= 0) return false;
        
        if (my < db.y() + 1 || my >= db.y() + db.height() - 1) return false;
        int divX = overlayDividerX();
        return mx >= divX - OVERLAY_DIVIDER_HALF_HIT && mx < divX + OVERLAY_DIVIDER_HALF_HIT + 1;
    }

    
    private int overlayDividerX() {
        DownSidebarLayoutHelper.Rect db = layoutRect();
        int totalW = db.width();
        if (totalW <= 0) return 0;
        int leftW = resolveLeftOverlayWidth();
        
        return db.x() + 1 + leftW;
    }

    

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        int mx = (int) mouseX;
        int my = (int) mouseY;
        
        if (leftLayer.contains(mx, my) && leftLayer.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        
        if ((rightLayer.contains(mx, my) || rightLayer.isMouseOverPopup(mx, my)) && rightLayer.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        
        if (isMouseOverDownOverlayDivider(mx, my)) {
            isDraggingOverlayDivider = true;
            dragOverlayDividerStartX = mx;
            dragOverlayDividerStartLeftW = resolveLeftOverlayWidth();
            return true;
        }
        DownSidebarLayoutHelper.Rect db = layoutRect();
        return resizeHandler.tryBegin(mouseY, mouseX,
                db.y(), db.x(), db.width(),
                currentHeight, this.screen.height);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int mx = (int) mouseX;
        int my = (int) mouseY;
        if (leftLayer.contains(mx, my)) {
            return leftLayer.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        if (rightLayer.contains(mx, my) || rightLayer.isMouseOverPopup(mx, my)) {
            return rightLayer.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (leftLayer.keyPressed(keyCode, scanCode, modifiers)) return true;
        if (rightLayer.keyPressed(keyCode, scanCode, modifiers)) return true;
        return false;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (leftLayer.charTyped(codePoint, modifiers)) return true;
        if (rightLayer.charTyped(codePoint, modifiers)) return true;
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        
        if (leftLayer.mouseReleased(mouseX, mouseY, button)) return true;
        if (rightLayer.mouseReleased(mouseX, mouseY, button)) return true;
        if (isDraggingOverlayDivider) {
            isDraggingOverlayDivider = false;
            this.screen.persistUiState();
            return true;
        }
        if (resizeHandler.isActive()) {
            resizeHandler.end();
            this.screen.persistUiState();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button != 0) return false;
        
        if (leftLayer.mouseDragged(mouseX, mouseY, button, dragX, dragY)) return true;
        if (rightLayer.mouseDragged(mouseX, mouseY, button, dragX, dragY)) return true;
        if (isDraggingOverlayDivider) {
            int mx = (int) mouseX;
            int deltaX = mx - dragOverlayDividerStartX;
            int newLeftW = dragOverlayDividerStartLeftW + deltaX;
            DownSidebarLayoutHelper.Rect db = layoutRect();
            int totalW = db.width();
            if (totalW > 0) {
                this.leftOverlayWidth = clampLeftOverlayWidth(newLeftW, totalW);
            }
            return true;
        }
        if (!resizeHandler.isActive()) return false;
        this.currentHeight = resizeHandler.computeNewSize(mouseY);
        return true;
    }

    

    
    public void resetOverlayDividerDrag() {
        isDraggingOverlayDivider = false;
    }

    
    public boolean isMouseOverTopEdge(int mx, int my) {
        DownSidebarLayoutHelper.Rect db = layoutRect();
        return resizeHandler.isOverEdge(my, mx, db.y(), db.x(), db.width());
    }

    @Override
    public List<PersistableProperty> persistableProperties() {
        String pk = "downSidebar";
        return List.of(
                PersistableProperty.intField(
                        pk + ".height",
                        s -> s.downSidebarHeight,
                        (s, v) -> s.downSidebarHeight = v,
                        () -> this.currentHeight,
                        v -> this.currentHeight = v),
                PersistableProperty.intField(
                        pk + ".leftOverlayWidth",
                        s -> s.downSidebarLeftOverlayW,
                        (s, v) -> s.downSidebarLeftOverlayW = v,
                        () -> this.leftOverlayWidth,
                        v -> this.leftOverlayWidth = v)
        );
    }
}
