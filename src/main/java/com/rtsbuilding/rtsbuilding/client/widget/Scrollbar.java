package com.rtsbuilding.rtsbuilding.client.widget;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * AE2-style vertical scrollbar for the RTS crafting terminal storage grid.
 * Uses vanilla creative inventory scroller textures.
 */
public final class Scrollbar {
    private static final ResourceLocation SCROLLER_SPRITE =
            ResourceLocation.withDefaultNamespace("container/creative_inventory/scroller");
    private static final ResourceLocation SCROLLER_DISABLED_SPRITE =
            ResourceLocation.withDefaultNamespace("container/creative_inventory/scroller_disabled");

    private int displayX;
    private int displayY;
    private int height = 16;
    private int pageSize = 1;
    private int maxScroll;
    private int minScroll;
    private int currentScroll;
    private boolean dragging;
    private int dragYOffset;
    private boolean visible = true;

    public Scrollbar() {
    }

    public void setPosition(int x, int y) {
        this.displayX = x;
        this.displayY = y;
    }

    public void setHeight(int height) {
        this.height = Math.max(16, height);
    }

    public void setRange(int min, int max, int pageSize) {
        this.minScroll = min;
        this.maxScroll = max;
        this.pageSize = pageSize;
        if (this.minScroll > this.maxScroll) {
            this.maxScroll = this.minScroll;
        }
        applyRange();
    }

    public int getCurrentScroll() {
        return this.currentScroll;
    }

    public void setCurrentScroll(int scroll) {
        this.currentScroll = scroll;
        applyRange();
    }

    public int getRange() {
        return this.maxScroll - this.minScroll;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public boolean isVisible() {
        return this.visible;
    }

    /**
     * Returns the Y offset of the scrollbar handle within the track.
     */
    private int getHandleYOffset() {
        if (getRange() == 0) {
            return 0;
        }
        int availableHeight = this.height - getHandleHeight();
        return (this.currentScroll - this.minScroll) * availableHeight / getRange();
    }

    /**
     * Returns the Y position corresponding to a given scroll value from a
     * mouse position relative to the top of the track.
     */
    private int getScrollFromMouseY(double mouseY) {
        double handleUpperEdgeY = mouseY - this.displayY - this.dragYOffset;
        double availableHeight = this.height - getHandleHeight();
        double position = Mth.clamp(handleUpperEdgeY / availableHeight, 0.0, 1.0);
        return this.minScroll + (int) Math.round(position * getRange());
    }

    /**
     * Handle a mouse click on the scrollbar. Returns true if the event was consumed.
     */
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!this.visible || button != 0 || getRange() == 0) {
            return false;
        }
        if (!isInside(mouseX, mouseY)) {
            return false;
        }

        this.dragging = false;
        int relY = (int) mouseY - displayY;
        int handleYOffset = getHandleYOffset();

        if (relY < handleYOffset) {
            pageUp();
        } else if (relY < handleYOffset + getHandleHeight()) {
            this.dragging = true;
            this.dragYOffset = relY - handleYOffset;
        } else {
            pageDown();
        }
        return true;
    }

    /**
     * Handle mouse drag on the scrollbar. Returns true if the event was consumed.
     */
    public boolean mouseDragged(double mouseX, double mouseY, int button) {
        if (!this.visible || getRange() == 0 || !this.dragging) {
            return false;
        }
        this.currentScroll = getScrollFromMouseY(mouseY);
        applyRange();
        return true;
    }

    /**
     * Handle mouse release. Returns true if the event was consumed.
     */
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            this.dragging = false;
        }
        return false;
    }

    /**
     * Handle mouse scroll on the scrollbar. Returns true if the event was consumed.
     */
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!this.visible || getRange() == 0) {
            return false;
        }
        double delta = Math.max(Math.min(-scrollY, 1.0), -1.0);
        this.currentScroll += (int) delta * this.pageSize;
        applyRange();
        return true;
    }

    public void pageUp() {
        this.currentScroll -= this.pageSize;
        applyRange();
    }

    public void pageDown() {
        this.currentScroll += this.pageSize;
        applyRange();
    }

    /**
     * Draw the scrollbar handle at its current position.
     */
    public void render(GuiGraphics guiGraphics) {
        if (!this.visible) {
            return;
        }

        int yOffset;
        ResourceLocation sprite;
        if (getRange() == 0) {
            yOffset = 0;
            sprite = SCROLLER_DISABLED_SPRITE;
        } else {
            yOffset = getHandleYOffset();
            sprite = SCROLLER_SPRITE;
        }

        guiGraphics.blitSprite(sprite, this.displayX, this.displayY + yOffset, getHandleWidth(), getHandleHeight());
    }

    private int getHandleWidth() {
        return 12;
    }

    private int getHandleHeight() {
        return 15;
    }

    private boolean isInside(double mouseX, double mouseY) {
        return mouseX >= this.displayX && mouseX <= this.displayX + getHandleWidth()
                && mouseY >= this.displayY && mouseY <= this.displayY + this.height;
    }

    private void applyRange() {
        this.currentScroll = Mth.clamp(this.currentScroll, this.minScroll, this.maxScroll);
    }
}
