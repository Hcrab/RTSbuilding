package com.rtsbuilding.rtsbuilding.client.presentation.panel.base.component;

import com.mojang.math.Axis;
import com.rtsbuilding.rtsbuilding.client.util.animate.AnimFloat;
import com.rtsbuilding.rtsbuilding.client.util.render.SdfRenderer;
import com.rtsbuilding.rtsbuilding.client.util.render.TextRenderer;
import com.rtsbuilding.rtsbuilding.client.util.theme.ThemeManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public class CollapsibleSection {
    private static final int SECTION_HEADER_H = 22;
    private static final int FOLD_BTN_SIZE = 16;

    private static final int SECTION_BG_COLOR = 0xFF636363;
    private static final int SECTION_BORDER_DEFAULT = 0xFF000000;
    private static final int SECTION_BORDER_HOVER = 0xFF384565;

    

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
        int bgH = SECTION_HEADER_H + (int)(this.contentFullHeight * this.cachedProgress);
        int radius = 6;
        int borderWidth = 1;
        int borderColor = lerpArgb(SECTION_BORDER_DEFAULT, SECTION_BORDER_HOVER, t);

        SdfRenderer.drawBorderedRoundedRect(g, x, y, sectionWidth, bgH, radius,
                borderColor, SECTION_BG_COLOR, borderWidth);
    }

    private static int lerpArgb(int from, int to, float t) {
        if (t <= 0.005f) return from;
        if (t >= 0.995f) return to;
        int a = lerpComp((from >> 24) & 0xFF, (to >> 24) & 0xFF, t);
        int r = lerpComp((from >> 16) & 0xFF, (to >> 16) & 0xFF, t);
        int g = lerpComp((from >> 8) & 0xFF, (to >> 8) & 0xFF, t);
        int b = lerpComp(from & 0xFF, to & 0xFF, t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int lerpComp(int from, int to, float t) {
        return Math.round(from + (to - from) * t);
    }

    
    private void renderArrow(GuiGraphics g, int x, int y) {
        int ax = x + ARROW_X_OFFSET;
        int ay = y + ARROW_Y_OFFSET;
        g.pose().pushPose();
        float halfBtn = FOLD_BTN_SIZE / 2.0f;
        g.pose().translate(ax + halfBtn, ay + halfBtn, 0);
        g.pose().mulPose(Axis.ZP.rotationDegrees(this.arrowAnim.get() * 90.0f));
        g.pose().scale(2f / 3f, 2f / 3f, 1f);
        SdfRenderer.drawChevron(g, (int)-halfBtn, (int)-halfBtn, FOLD_BTN_SIZE, FOLD_BTN_SIZE,
                ThemeManager.getTextColor());
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
