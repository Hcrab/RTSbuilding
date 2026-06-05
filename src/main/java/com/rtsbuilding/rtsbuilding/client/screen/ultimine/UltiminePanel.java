package com.rtsbuilding.rtsbuilding.client.screen.ultimine;

import com.rtsbuilding.rtsbuilding.client.screen.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.util.RtsClientUiUtil;
import com.rtsbuilding.rtsbuilding.client.screen.layout.PanelLayouts;
import com.rtsbuilding.rtsbuilding.client.screen.panel.RtsWindowPanel;
import com.rtsbuilding.rtsbuilding.progression.RtsProgressionNodes;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import static com.rtsbuilding.rtsbuilding.client.screen.BuilderScreenConstants.*;

/**
 * Movable Ultimine settings window.
 *
 * <p>This class owns only the small Ultimine UI state: open state, limit editing,
 * and progress display. It deliberately leaves mining execution, packets, camera
 * input, and storage/container overlays to the existing controller and screen
 * paths so the window migration does not alter gameplay behavior.
 */
public final class UltiminePanel extends RtsWindowPanel {
    private int ultimineLimit = 64;
    private boolean ultimineLimitEditing = false;
    private boolean ultimineLimitSelectAll = false;
    private String ultimineLimitDraft = "";
    private int lastUltimineSentLimit = 0;

    @Override
    public void init(BuilderScreen screen, ClientRtsController controller) {
        super.init(screen, controller);
        this.resizable = false;
    }

    public void applyOpenState(boolean open) {
        this.open = open;
        if (!open) {
            cancelUltimineLimitEdit();
        }
    }

    public int getLimit() {
        return this.ultimineLimit;
    }

    public void setLimit(int limit) {
        this.ultimineLimit = clampUltimineLimit(limit);
    }

    public int getLastSentLimit() {
        return this.lastUltimineSentLimit;
    }

    public void setLastSentLimit(int limit) {
        this.lastUltimineSentLimit = limit;
    }

    public void adjustLimit(int delta) {
        adjustUltimineLimit(delta);
    }

    public void commitEdit() {
        commitUltimineLimitEdit();
    }

    public boolean isLimitEditing() {
        return this.ultimineLimitEditing;
    }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int x = this.windowX;
        int rowY = contentY() + 12;
        g.drawString(screen.font(), Component.translatable("screen.rtsbuilding.ultimine.blocks"), x + 8, rowY, 0xD8E3EE);
        RtsClientUiUtil.drawPanelFrame(g, x + 8, rowY + 12, 24, 18, 0xAA1C232D, 0xFF647B92, 0xFF0D1117);
        g.drawCenteredString(screen.font(), "-", x + 20, rowY + 17, 0xFFFFFF);
        RtsClientUiUtil.drawPanelFrame(g, x + 38, rowY + 12, 58, 18, 0xAA243547, 0xFF647B92, 0xFF0D1117);
        if (this.ultimineLimitEditing) {
            g.fill(x + 40, rowY + 14, x + 94, rowY + 28, 0x552D82C8);
        }
        String limitText = this.ultimineLimitEditing ? this.ultimineLimitDraft : Integer.toString(this.ultimineLimit);
        if (limitText.isEmpty()) {
            limitText = "_";
        }
        g.drawCenteredString(screen.font(), limitText, x + 67, rowY + 17, this.ultimineLimitEditing ? 0xFFFFFF : 0xF2F7FF);
        RtsClientUiUtil.drawPanelFrame(g, x + 102, rowY + 12, 24, 18, 0xAA1C232D, 0xFF647B92, 0xFF0D1117);
        g.drawCenteredString(screen.font(), "+", x + 114, rowY + 17, 0xFFFFFF);
        RtsClientUiUtil.drawPanelFrame(g, x + 132, rowY + 12, 42, 18, 0xAA1C232D, 0xFF647B92, 0xFF0D1117);
        g.drawCenteredString(screen.font(), "MIN", x + 153, rowY + 17, 0xFFFFFF);
        RtsClientUiUtil.drawPanelFrame(g, x + 180, rowY + 12, 48, 18, 0xAA1C232D, 0xFF647B92, 0xFF0D1117);
        g.drawCenteredString(screen.font(), "MAX", x + 204, rowY + 17, 0xFFFFFF);

        int stage = this.controller.getMineProgressStage();
        int progressY = contentY() + 62;
        String progressLabel = stage >= 0
                ? screen.text("screen.rtsbuilding.ultimine.breaking", Math.max(1, this.lastUltimineSentLimit))
                : screen.text("screen.rtsbuilding.ultimine.ready");
        g.drawString(screen.font(), progressLabel, x + 8, progressY - 12, stage >= 0 ? 0xB8FFB8 : 0xAFC0D3);
        RtsClientUiUtil.drawPanelFrame(g, x + 8, progressY, ULTIMINE_PANEL_W - 16, 12, 0xAA101820, 0xFF647B92, 0xFF0D1117);
        int fillW = stage < 0 ? 0 : Math.min(ULTIMINE_PANEL_W - 20, Math.max(1, (int) (((stage + 1) / 10.0F) * (ULTIMINE_PANEL_W - 20))));
        if (fillW > 0) {
            g.fill(x + 10, progressY + 2, x + 10 + fillW, progressY + 10, 0xFF78B28C);
        }
    }

    @Override
    protected boolean handleContentClick(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return true;
        }
        int x = this.windowX;
        int rowY = contentY() + 12;
        if (inside(mouseX, mouseY, x + 8, rowY + 12, 24, 18)) {
            adjustUltimineLimit(screen.hasShiftDown() ? -16 : -1);
            return true;
        }
        if (inside(mouseX, mouseY, x + 102, rowY + 12, 24, 18)) {
            adjustUltimineLimit(screen.hasShiftDown() ? 16 : 1);
            return true;
        }
        if (inside(mouseX, mouseY, x + 38, rowY + 12, 58, 18)) {
            beginUltimineLimitEdit();
            return true;
        }
        if (inside(mouseX, mouseY, x + 132, rowY + 12, 42, 18)) {
            this.ultimineLimit = ULTIMINE_MIN_LIMIT;
            cancelUltimineLimitEdit();
            screen.persistUiState();
            return true;
        }
        if (inside(mouseX, mouseY, x + 180, rowY + 12, 48, 18)) {
            this.ultimineLimit = ULTIMINE_MAX_LIMIT;
            cancelUltimineLimitEdit();
            screen.persistUiState();
            return true;
        }
        return true;
    }

    public boolean isInsideLimitInput(double mouseX, double mouseY) {
        if (!isOpen() || !screen.hasProgressionNode(RtsProgressionNodes.ULTIMINE) || !hasInitializedBounds()) {
            return false;
        }
        int rowY = contentY() + 12;
        return inside(mouseX, mouseY, this.windowX + 38, rowY + 12, 58, 18);
    }

    public boolean handleKeyPressed(int keyCode) {
        if (!this.ultimineLimitEditing) {
            return false;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            commitUltimineLimitEdit();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            cancelUltimineLimitEdit();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            if (this.ultimineLimitSelectAll) {
                this.ultimineLimitDraft = "";
                this.ultimineLimitSelectAll = false;
            } else if (!this.ultimineLimitDraft.isEmpty()) {
                this.ultimineLimitDraft = this.ultimineLimitDraft.substring(0, this.ultimineLimitDraft.length() - 1);
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DELETE) {
            this.ultimineLimitDraft = "";
            this.ultimineLimitSelectAll = false;
            return true;
        }
        return true;
    }

    public boolean handleCharTyped(char codePoint) {
        if (!this.ultimineLimitEditing) {
            return false;
        }
        if (!Character.isDigit(codePoint)) {
            return true;
        }
        if (this.ultimineLimitSelectAll) {
            this.ultimineLimitDraft = "";
            this.ultimineLimitSelectAll = false;
        }
        if (this.ultimineLimitDraft.length() < 3) {
            this.ultimineLimitDraft += codePoint;
        }
        return true;
    }

    @Override
    protected Component getTitle() {
        return Component.translatable("screen.rtsbuilding.ultimine.title");
    }

    @Override
    protected int getDefaultWidth() {
        return ULTIMINE_PANEL_W;
    }

    @Override
    protected int getDefaultHeight() {
        return ULTIMINE_PANEL_H;
    }

    @Override
    protected int getMinWindowWidth() {
        return ULTIMINE_PANEL_W;
    }

    @Override
    protected int getMinWindowHeight() {
        return ULTIMINE_PANEL_H;
    }

    @Override
    protected boolean canShowWindow() {
        return screen.hasProgressionNode(RtsProgressionNodes.ULTIMINE);
    }

    @Override
    protected void computeDefaultPosition() {
        int y = TOP_H + 10;
        PanelLayouts.QuickBuildPanelLayout quickBuildLayout = screen.resolveQuickBuildPanelLayout();
        if (quickBuildLayout != null) {
            y = quickBuildLayout.y() + quickBuildLayout.h() + 8;
        }
        int maxX = Math.max(4, screen.width - ULTIMINE_PANEL_W - 4);
        this.windowX = Mth.clamp(screen.width - ULTIMINE_PANEL_W - 10, 4, maxX);
        this.windowY = y;
    }

    @Override
    protected void onClose() {
        cancelUltimineLimitEdit();
        screen.persistUiState();
    }

    @Override
    protected void onBoundsChanged() {
        screen.persistUiState();
    }

    private void adjustUltimineLimit(int delta) {
        this.ultimineLimit = clampUltimineLimit(this.ultimineLimit + delta);
        cancelUltimineLimitEdit();
        screen.persistUiState();
    }

    private void beginUltimineLimitEdit() {
        this.ultimineLimitDraft = Integer.toString(this.ultimineLimit);
        this.ultimineLimitEditing = true;
        this.ultimineLimitSelectAll = true;
        screen.blurSearchFocus();
    }

    private void commitUltimineLimitEdit() {
        if (!this.ultimineLimitEditing) {
            return;
        }
        try {
            if (!this.ultimineLimitDraft.isBlank()) {
                this.ultimineLimit = clampUltimineLimit(Integer.parseInt(this.ultimineLimitDraft));
            }
        } catch (NumberFormatException ignored) {
        }
        cancelUltimineLimitEdit();
        screen.persistUiState();
    }

    private void cancelUltimineLimitEdit() {
        this.ultimineLimitEditing = false;
        this.ultimineLimitSelectAll = false;
        this.ultimineLimitDraft = "";
    }

    private static int clampUltimineLimit(int value) {
        return Math.max(ULTIMINE_MIN_LIMIT, Math.min(ULTIMINE_MAX_LIMIT, value));
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }
}
