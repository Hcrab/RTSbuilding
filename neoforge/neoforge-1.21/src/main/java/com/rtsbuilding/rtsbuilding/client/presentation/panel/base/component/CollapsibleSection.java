package com.rtsbuilding.rtsbuilding.client.presentation.panel.base.component;

import com.mojang.math.Axis;
import com.rtsbuilding.rtsbuilding.client.util.animate.AnimFloat;
import com.rtsbuilding.rtsbuilding.client.util.render.CrossFadeRenderer;
import com.rtsbuilding.rtsbuilding.client.util.render.SpriteRenderer;
import com.rtsbuilding.rtsbuilding.client.util.render.TextRenderer;
import com.rtsbuilding.rtsbuilding.client.util.render.model.NineSliceRegion;
import com.rtsbuilding.rtsbuilding.client.util.render.model.SpriteRegion;
import com.rtsbuilding.rtsbuilding.client.util.render.model.TextureInfo;
import com.rtsbuilding.rtsbuilding.client.util.theme.ThemeManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class CollapsibleSection {
    

    private static final int SECTION_HEADER_H = 22;

    

    private static final ResourceLocation FOLD_TEXTURE = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/base/base_ui/base_ui_3.png");
    private static final int FOLD_TEX_W = 32;
    private static final int FOLD_TEX_FILE_H = 32;
    private static final int FOLD_TEX_STATE_H = 16;
    private static final int FOLD_BORDER = 4;
    private static final TextureInfo FOLD_TEX_INFO = new TextureInfo(
            FOLD_TEXTURE, FOLD_TEX_W, FOLD_TEX_FILE_H,
            TextureInfo.ThemeLayout.HORIZONTAL_PAIR,
            TextureInfo.FilterMode.PIXEL);
    private static final NineSliceRegion FOLD_NINE_SLICE = NineSliceRegion.fullTheme(
            FOLD_TEX_INFO, FOLD_TEX_STATE_H, FOLD_BORDER);
    private static final int FOLD_BTN_SIZE = 16;

    

    private static final ResourceLocation FOLD_ARROW_TEXTURE = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/base/arrow.png");
    
    private static final int FOLD_ARROW_TEX_W = 512;
    
    private static final int FOLD_ARROW_TEX_FILE_W = 1024;
    private static final int FOLD_ARROW_TEX_FILE_H = 512;
    private static final int FOLD_ARROW_STATE_H = 512;

    private static final TextureInfo FOLD_ARROW_TEX_INFO = new TextureInfo(
            FOLD_ARROW_TEXTURE, FOLD_ARROW_TEX_FILE_W, FOLD_ARROW_TEX_FILE_H,
            TextureInfo.ThemeLayout.HORIZONTAL_PAIR,
            TextureInfo.FilterMode.PIXEL);

    

    private static final int ARROW_X_OFFSET = 5;
    private static final int ARROW_Y_OFFSET = 3;
    private static final int TITLE_X_OFFSET = 23;
    private static final int TITLE_Y_OFFSET = 7;
    private static final int TITLE_WIDTH_SUB = 42;

    

    private boolean expanded;
    private final String titleKey;
    
    private String cachedTitle;

    
    private final AnimFloat arrowAnim = AnimFloat.hover();
    
    private final AnimFloat hoverState = AnimFloat.hover();
    
    private final AnimFloat contentAnim = AnimFloat.expand();
    private int contentFullHeight;
    private float cachedProgress;

    

    public CollapsibleSection(String titleKey) {
        this.titleKey = titleKey;
    }

    public boolean isExpanded() {
        return this.expanded;
    }

    public void setExpanded(boolean expanded) {
        if (this.expanded != expanded) {
            this.expanded = expanded;
            this.arrowAnim.target(this.expanded ? 1.0f : 0.0f);
            this.contentAnim.target(this.expanded ? 1.0f : 0.0f);
        }
    }

    public void toggle() {
        this.expanded = !this.expanded;
        this.arrowAnim.target(this.expanded ? 1.0f : 0.0f);
        this.contentAnim.target(this.expanded ? 1.0f : 0.0f);
    }

    

    
    public void drawHeader(GuiGraphics g, int mouseX, int mouseY, int x, int y, int sectionWidth, int contentHeight) {
        this.contentFullHeight = contentHeight;
        this.cachedProgress = contentAnim.get();
        updateHoverState(mouseX, mouseY, x, y, sectionWidth, contentHeight);
        renderHoverBackground(g, x, y, sectionWidth);
        renderArrow(g, x, y);
        renderTitle(g, x, y, sectionWidth);
    }

    
    private void updateHoverState(int mouseX, int mouseY, int x, int y, int sectionWidth, int contentHeight) {
        int detectH = this.expanded && contentHeight > 0 ? SECTION_HEADER_H + contentHeight : SECTION_HEADER_H;
        this.hoverState.track(isMouseOver(mouseX, mouseY, x, y, sectionWidth, detectH));
    }

    
    private void renderHoverBackground(GuiGraphics g, int x, int y, int sectionWidth) {
        float t = this.hoverState.get();
        CrossFadeRenderer.render(g, t,
                () -> renderStateBackground(g, x, y, sectionWidth, 0),
                () -> renderStateBackground(g, x, y, sectionWidth, FOLD_TEX_STATE_H));
    }

    
    private void renderStateBackground(GuiGraphics g, int x, int y, int sectionWidth, int vOffset) {
        
        
        int bgH = SECTION_HEADER_H + (int)(this.contentFullHeight * this.cachedProgress);
        SpriteRenderer.drawNineSlice(g, FOLD_NINE_SLICE.withTheme().withVOffset(vOffset),
                x, y, sectionWidth, bgH);
    }

    
    private void renderArrow(GuiGraphics g, int x, int y) {

        g.pose().pushPose();
        g.pose().translate(x + ARROW_X_OFFSET, y + ARROW_Y_OFFSET, 0);
        
        float halfBtn = FOLD_BTN_SIZE / 2.0f;
        g.pose().translate(halfBtn, halfBtn, 0);
        g.pose().mulPose(Axis.ZP.rotationDegrees((1.0f + this.arrowAnim.get()) * 90.0f));
        g.pose().translate(-halfBtn, -halfBtn, 0);
        SpriteRegion arrowRegion = new SpriteRegion(FOLD_ARROW_TEX_INFO, 0, 0, FOLD_ARROW_TEX_W, FOLD_ARROW_STATE_H)
                .withTheme();
        SpriteRenderer.drawSprite(g, arrowRegion, 0, 0, FOLD_BTN_SIZE, FOLD_BTN_SIZE);
        g.pose().popPose();
    }

    
    private void renderTitle(GuiGraphics g, int x, int y, int sectionWidth) {
        if (cachedTitle == null) {
            cachedTitle = Component.translatable(this.titleKey).getString();
        }
        int maxTitleWidth = Math.max(8, sectionWidth - TITLE_WIDTH_SUB);
        TextRenderer.draw(g, TextRenderer.trimToWidth(Minecraft.getInstance().font, cachedTitle, maxTitleWidth),
                x + TITLE_X_OFFSET, y + TITLE_Y_OFFSET,
                ThemeManager.getTextColor());
    }

    

    
    public boolean isHeaderClicked(double mouseX, double mouseY, int x, int y, int sectionWidth) {
        return isMouseOver(mouseX, mouseY, x, y, sectionWidth, SECTION_HEADER_H);
    }

    

    
    public float getContentProgress() {
        return this.cachedProgress;
    }

    
    public int totalHeight(int contentHeight) {
        return SECTION_HEADER_H + (int) (contentHeight * getContentProgress());
    }

    
    public static int headerHeight() {
        return SECTION_HEADER_H;
    }

    

    

    private static boolean isMouseOver(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }
}
