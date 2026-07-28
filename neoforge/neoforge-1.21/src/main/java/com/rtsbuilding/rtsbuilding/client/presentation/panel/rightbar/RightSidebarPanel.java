package com.rtsbuilding.rtsbuilding.client.presentation.panel.rightbar;

import com.rtsbuilding.rtsbuilding.client.presentation.panel.base.api.RtsPanelApi;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.base.component.EdgeResizeHandler;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.base.overlay.DownOverlayLayer;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.rightbar.overlay.LowerRightOverlayLayer;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.rightbar.overlay.UpperRightOverlayLayer;
import com.rtsbuilding.rtsbuilding.client.presentation.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.util.render.SpriteRenderer;
import com.rtsbuilding.rtsbuilding.client.util.render.model.NineSliceRegion;
import com.rtsbuilding.rtsbuilding.client.util.render.model.TextureInfo;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

public final class RightSidebarPanel implements RtsPanelApi {

    
    private BuilderScreen screen;

    
    private int currentWidth = RightSidebarLayoutHelper.SIDEBAR_WIDTH;

    
    public void setCurrentWidth(int width) {
        this.currentWidth = Math.max(30, Math.min(width, this.screen != null ? this.screen.width / 4 : 2000));
    }

    
    public int getCurrentWidth() {
        return currentWidth;
    }

    

    
    private static final ResourceLocation BORDER_TEXTURE = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/right/right_ui.png");
    
    private static final int TEX_W = 32;
    
    private static final int TEX_FILE_H = 32;
    
    private static final int STATE_H = 16;
    
    private static final int BORDER = 2;
    private static final TextureInfo RIGHT_TEX_INFO = new TextureInfo(
            BORDER_TEXTURE, TEX_W, TEX_FILE_H,
            TextureInfo.ThemeLayout.HORIZONTAL_PAIR,
            TextureInfo.FilterMode.PIXEL);
    private static final NineSliceRegion RIGHT_NINE_SLICE = NineSliceRegion.fullTheme(
            RIGHT_TEX_INFO, STATE_H, BORDER);

    
    private final RightSidebarLayoutHelper layout = new RightSidebarLayoutHelper();

    
    private final EdgeResizeHandler resizeHandler = new EdgeResizeHandler(
            EdgeResizeHandler.Orientation.HORIZONTAL,
            EdgeResizeHandler.Side.LEADING,
            20);

    

    
    private final UpperRightOverlayLayer upperLayer = new UpperRightOverlayLayer();
    
    private final LowerRightOverlayLayer lowerLayer = new LowerRightOverlayLayer();

    

    
    private int upperOverlayHeight = -1;

    
    private boolean isDraggingOverlayDivider;

    
    private int dragOverlayDividerStartY;

    
    private int dragOverlayDividerStartUpperH;

    
    private static final int OVERLAY_DIVIDER_HALF_HIT = 2;

    
    private static final int OVERLAY_MIN_SIZE = 20;

    @Override
    public void init(BuilderScreen screen) {
        this.screen = Objects.requireNonNull(screen,
                "RightSidebarPanel.init() called with null screen");
    }

    
    private int defaultUpperOverlayHeight(int totalH) {
        int gap = 1;
        return Math.max(OVERLAY_MIN_SIZE, (totalH - gap) * 8 / 21);
    }

    
    private int clampUpperOverlayHeight(int h, int totalH) {
        int gap = 1;
        int maxUpper = totalH - gap - OVERLAY_MIN_SIZE;
        return Math.max(OVERLAY_MIN_SIZE, Math.min(maxUpper, h));
    }

    
    private int resolveUpperOverlayHeight() {
        RightSidebarLayoutHelper.Rect sb = layoutRect();
        if (this.upperOverlayHeight <= 0) {
            return defaultUpperOverlayHeight(sb.height());
        }
        return clampUpperOverlayHeight(this.upperOverlayHeight, sb.height());
    }

    

    
    private RightSidebarLayoutHelper.Rect layoutRect() {
        return layout.sidebarRect(
                this.screen.width, this.screen.height, this.currentWidth);
    }

    

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        RightSidebarLayoutHelper.Rect sb = layoutRect();

        int srcY = resizeHandler.isActive() ? STATE_H : 0;
        SpriteRenderer.drawNineSlice(g, RIGHT_NINE_SLICE.withTheme().withVOffset(srcY),
                sb.x(), sb.y(), sb.width(), sb.height());
    }

    
    @Override
    public void renderOverlays(GuiGraphics g, int mouseX, int mouseY) {
        RightSidebarLayoutHelper.Rect sb = layoutRect();
        
        int ox = sb.x() + 1;
        int ow = sb.width() - 1;
        if (ow <= 0) return;

        int totalH = sb.height();
        int gap = 1;
        
        int upperH = resolveUpperOverlayHeight();

        
        upperLayer.setLastMousePos(mouseX, mouseY);
        upperLayer.setBounds(ox, sb.y(), ow, upperH);
        upperLayer.render(g, isDraggingOverlayDivider || isMouseInLayer(upperLayer, mouseX, mouseY));

        
        int bottomY = sb.y() + upperH + gap;
        int lowerH = totalH - upperH - gap;
        if (lowerH > 0) {
            lowerLayer.setLastMousePos(mouseX, mouseY);
            lowerLayer.setBounds(ox, bottomY, ow, lowerH);
            lowerLayer.render(g, isDraggingOverlayDivider || isMouseInLayer(lowerLayer, mouseX, mouseY));
        }
    }

    
    private boolean isMouseInLayer(DownOverlayLayer layer, int mouseX, int mouseY) {
        if (this.screen == null || this.screen.isMouseOverUI(mouseX, mouseY)) return false;
        return layer.contains(mouseX, mouseY);
    }

    
    public boolean isMouseOverOverlayDivider(int mx, int my) {
        return isMouseOverRightOverlayDivider(mx, my);
    }

    
    private boolean isMouseOverRightOverlayDivider(int mx, int my) {
        RightSidebarLayoutHelper.Rect sb = layoutRect();
        if (sb.width() <= 0 || sb.height() <= 0) return false;
        
        int ox = sb.x() + 1;
        int ow = sb.width() - 1;
        if (mx < ox || mx >= ox + ow) return false;
        int divY = overlayDividerY();
        return my >= divY - OVERLAY_DIVIDER_HALF_HIT && my < divY + OVERLAY_DIVIDER_HALF_HIT + 1;
    }

    
    private int overlayDividerY() {
        RightSidebarLayoutHelper.Rect sb = layoutRect();
        int totalH = sb.height();
        if (totalH <= 0) return 0;
        int upperH = resolveUpperOverlayHeight();
        return sb.y() + upperH;
    }

    

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        int mx = (int) mouseX;
        int my = (int) mouseY;
        
        if (upperLayer.contains(mx, my) && upperLayer.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (lowerLayer.contains(mx, my) && lowerLayer.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        
        if (isMouseOverRightOverlayDivider(mx, my)) {
            isDraggingOverlayDivider = true;
            dragOverlayDividerStartY = my;
            dragOverlayDividerStartUpperH = resolveUpperOverlayHeight();
            return true;
        }
        RightSidebarLayoutHelper.Rect sb = layoutRect();
        return resizeHandler.tryBegin(mouseX, mouseY,
                sb.x(), sb.y(), sb.height(),
                currentWidth, this.screen.width);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        if (isDraggingOverlayDivider) {
            isDraggingOverlayDivider = false;
            return true;
        }
        if (resizeHandler.isActive()) {
            resizeHandler.end();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button != 0) return false;
        if (isDraggingOverlayDivider) {
            int my = (int) mouseY;
            int deltaY = my - dragOverlayDividerStartY;
            int newUpperH = dragOverlayDividerStartUpperH + deltaY;
            RightSidebarLayoutHelper.Rect sb = layoutRect();
            int totalH = sb.height();
            if (totalH > 0) {
                this.upperOverlayHeight = clampUpperOverlayHeight(newUpperH, totalH);
            }
            return true;
        }
        if (!resizeHandler.isActive()) return false;
        this.currentWidth = resizeHandler.computeNewSize(mouseX);
        return true;
    }

    

    
    public void resetOverlayDividerDrag() {
        isDraggingOverlayDivider = false;
    }

    
    public boolean isMouseOverLeftEdge(int mx, int my) {
        RightSidebarLayoutHelper.Rect sb = layoutRect();
        return resizeHandler.isOverEdge(mx, my, sb.x(), sb.y(), sb.height());
    }
}
