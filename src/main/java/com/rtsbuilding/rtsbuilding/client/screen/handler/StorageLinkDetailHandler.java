package com.rtsbuilding.rtsbuilding.client.screen.handler;

import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.screen.panel.RtsWindowPanel;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.screen.storage.LinkedStoragePanel;
import com.rtsbuilding.rtsbuilding.client.screen.topbar.TopBarPanel;
import com.rtsbuilding.rtsbuilding.client.screen.topbar.TopBarTypes;
import com.rtsbuilding.rtsbuilding.client.screen.canvas.MinecraftUiCanvas;
import com.rtsbuilding.rtsbuilding.client.input.overlay.LegacyGuiGraphics;
import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uikit.canvas.UiChromeRenderer;
import com.rtsbuilding.rtsbuilding.uikit.theme.StorageLinkDetailStyle;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;

import static com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreenConstants.*;

/**
 * Handles rendering and click interactions for the storage link detail action
 * (the extended action bar that appears when hovering the LINK top-bar button).
 * <p>
 * Owned and invoked by the screen's render/click dispatch methods.
 */
public final class StorageLinkDetailHandler extends RtsWindowPanel {

    private final TopBarPanel topBarPanel;
    private final LinkedStoragePanel linkedStoragePanel;
    private String actionLabel = "";

    public StorageLinkDetailHandler(
            BuilderScreen screen,
            ClientRtsController controller,
            TopBarPanel topBarPanel,
            LinkedStoragePanel linkedStoragePanel) {
        this.screen = screen;
        this.controller = controller;
        this.topBarPanel = topBarPanel;
        this.linkedStoragePanel = linkedStoragePanel;
        this.draggable = false;
        this.resizable = false;
        this.closable = false;
    }

    // ===== Render =====

    /**
     * Updates the lightweight dropdown window below the LINK top-bar button.
     * The panel is transient: it follows the button and never records the
     * position as a user-arranged preference.
     */
    public void updateVisibility(int mouseX, int mouseY) {
        TopBarTypes.TopBarButtonLayout linkButton = findTopBarButton(TopBarTypes.TopBarButtonId.LINK);
        if (linkButton == null || !isVisible(mouseX, mouseY, linkButton)) {
            setOpen(false);
            return;
        }
        this.actionLabel = screen.text("screen.rtsbuilding.storage_links.action");
        int w = actionW(linkButton, this.actionLabel);
        int x = actionX(linkButton, this.actionLabel);
        int y = actionY();
        setTransientBounds(x, y, w, STORAGE_LINK_DETAIL_ACTION_H);
        setOpen(true);
    }

    @Override
    protected void renderContent(LegacyGuiGraphics g, int mouseX, int mouseY, float partialTick) {
        boolean hovered = isInsideWindow(mouseX, mouseY);
        int x = contentX();
        int y = contentY();
        int w = contentWidth();
        UiChromeRenderer.frame(
                new MinecraftUiCanvas(g, screen.font(), screen),
                new UiRect(x, y, w, contentHeight()),
                1.0D,
                StorageLinkDetailStyle.background(hovered),
                StorageLinkDetailStyle.border(hovered),
                StorageLinkDetailStyle.BORDER_DARK);
        g.drawCenteredString(screen.font(),
                screen.font().trimStringToWidth(this.actionLabel, Math.max(8, contentWidth() - 8)),
                contentX() + contentWidth() / 2,
                contentY() + 4,
                StorageLinkDetailStyle.TEXT.toArgb());
    }

    // ===== Click =====

    /** Handles click on the storage link detail action button. */
    @Override
    protected void handleContentClick(double mouseX, double mouseY, int button) {
        this.linkedStoragePanel.openNear(this.windowX, this.windowY + this.windowHeight + 2);
        setOpen(false);
    }

    @Override
    protected ITextComponent getTitle() {
        return new TextComponentString("");
    }

    @Override
    protected int getDefaultWidth() {
        return 80;
    }

    @Override
    protected int getDefaultHeight() {
        return STORAGE_LINK_DETAIL_ACTION_H;
    }

    @Override
    protected int getMinWindowWidth() {
        return 40;
    }

    @Override
    protected int getMinWindowHeight() {
        return STORAGE_LINK_DETAIL_ACTION_H;
    }

    @Override
    protected int getTitleBarHeight() {
        return 0;
    }

    @Override
    protected int getResizeBorderWidth() {
        return 0;
    }

    @Override
    protected boolean shouldClipContent() {
        return false;
    }

    @Override
    protected void computeDefaultPosition() {
        this.windowX = 4;
        this.windowY = TOP_H + 2;
    }

    // ===== Private helpers =====

    private TopBarTypes.TopBarButtonLayout findTopBarButton(TopBarTypes.TopBarButtonId id) {
        for (TopBarTypes.TopBarButtonLayout button : this.topBarPanel.buildTopBarButtonLayouts()) {
            if (button.id() == id) {
                return button;
            }
        }
        return null;
    }

    private boolean isVisible(int mouseX, int mouseY, TopBarTypes.TopBarButtonLayout linkButton) {
        String label = screen.text("screen.rtsbuilding.storage_links.action");
        int w = actionW(linkButton, label);
        int x = actionX(linkButton, label);
        int y = actionY();
        int bridgeX = Math.min(linkButton.x(), x);
        int bridgeRight = Math.max(linkButton.x() + linkButton.width(), x + w);
        int bridgeY = 4 + TOP_BUTTON_H;
        int bridgeH = Math.max(0, y - bridgeY);
        return UiRect.contains(linkButton.x(), 4, linkButton.width(), TOP_BUTTON_H, mouseX, mouseY)
                || UiRect.contains(x, y, w, STORAGE_LINK_DETAIL_ACTION_H, mouseX, mouseY)
                || UiRect.contains(bridgeX, bridgeY, bridgeRight - bridgeX, bridgeH, mouseX, mouseY);
    }

    private int actionX(TopBarTypes.TopBarButtonLayout linkButton, String label) {
        int w = actionW(linkButton, label);
        int centered = linkButton.x() + linkButton.width() / 2 - w / 2;
        return MathHelper.clamp(centered, 4, Math.max(4, screen.width - w - 4));
    }

    private int actionY() {
        return TOP_H + 2;
    }

    private int actionW(TopBarTypes.TopBarButtonLayout linkButton, String label) {
        int desired = Math.max(linkButton.width(), screen.font().getStringWidth(label) + 12);
        return Math.min(desired, Math.max(40, screen.width - 8));
    }

}
