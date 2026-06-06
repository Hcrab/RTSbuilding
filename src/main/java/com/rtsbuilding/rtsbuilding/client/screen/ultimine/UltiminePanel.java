package com.rtsbuilding.rtsbuilding.client.screen.ultimine;

import com.rtsbuilding.rtsbuilding.client.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.RtsClientUiUtil;
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
 * <p>The panel owns only UI-local mining settings: requested block limit,
 * chain/area mode, and the progress display. Actual mining still runs through
 * the existing controller, packets, and server storage service.
 */
public final class UltiminePanel extends RtsWindowPanel {
    private int ultimineLimit = 64;
    private int lastUltimineSentLimit = 0;
    private boolean limitEditing = false;
    private boolean limitSelectAll = false;
    private String limitDraft = "";
    private boolean sliderDragging = false;
    private UltimineMode ultimineMode = UltimineMode.CHAIN;

    @Override
    public void init(BuilderScreen screen, ClientRtsController controller) {
        super.init(screen, controller);
        this.resizable = false;
    }

    public void applyOpenState(boolean open) {
        this.open = open;
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

    public UltimineMode getMode() {
        return this.ultimineMode;
    }

    public void setMode(UltimineMode mode) {
        this.ultimineMode = mode == null ? UltimineMode.CHAIN : mode;
    }

    public void adjustLimit(int delta) {
        this.ultimineLimit = clampUltimineLimit(this.ultimineLimit + delta);
        cancelLimitEdit();
        screen.persistUiState();
    }

    public void commitEdit() {
        commitLimitEdit();
    }

    public boolean isLimitEditing() {
        return this.limitEditing;
    }

    public boolean isInsideLimitInput(double mouseX, double mouseY) {
        int inputX = this.windowX + (ULTIMINE_PANEL_W - 50) / 2;
        int inputY = contentY() + 52;
        return this.open && inside(mouseX, mouseY, inputX, inputY, 50, 12);
    }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int x = this.windowX;
        int progressY = contentY() + 32;
        int stage = this.controller.getMineProgressStage();
        String progressLabel = stage >= 0
                ? screen.text("screen.rtsbuilding.ultimine.breaking", Math.max(1, this.lastUltimineSentLimit))
                : screen.text("screen.rtsbuilding.ultimine.ready");
        g.drawString(screen.font(), progressLabel, x + 8, progressY - 12,
                stage >= 0 ? 0xB8FFB8 : 0xAFC0D3, false);
        RtsClientUiUtil.drawPanelFrame(g, x + 8, progressY, ULTIMINE_PANEL_W - 16, 12,
                0xAA101820, 0xFF647B92, 0xFF0D1117);
        int fillW = stage < 0 ? 0 : Math.min(ULTIMINE_PANEL_W - 20,
                Math.max(1, (int) (((stage + 1) / 10.0F) * (ULTIMINE_PANEL_W - 20))));
        if (fillW > 0) {
            g.fill(x + 10, progressY + 2, x + 10 + fillW, progressY + 10, 0xFF78B28C);
        }

        renderLimitInput(g);
        renderLimitSlider(g, mouseX, mouseY);
        renderModeButtons(g, mouseX, mouseY);
    }

    @Override
    protected void handleContentClick(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return;
        }
        if (isInsideLimitInput(mouseX, mouseY)) {
            beginLimitEdit();
            return;
        }
        if (this.limitEditing) {
            commitLimitEdit();
        }

        int trackX = this.windowX + 8;
        int trackY = contentY() + 72;
        int trackW = ULTIMINE_PANEL_W - 16;
        if (mouseY >= trackY - 6 && mouseY <= trackY + 10 && mouseX >= trackX && mouseX <= trackX + trackW) {
            this.sliderDragging = true;
            setLimitFromSliderX(mouseX);
            screen.persistUiState();
            return;
        }

        int modeBtnY = contentY() + 92;
        int modeBtnW = (ULTIMINE_PANEL_W - 20) / 2;
        int chainBtnX = this.windowX + 8;
        int areaBtnX = chainBtnX + modeBtnW + 4;
        if (mouseY >= modeBtnY && mouseY <= modeBtnY + 14) {
            if (mouseX >= chainBtnX && mouseX <= chainBtnX + modeBtnW) {
                setMode(UltimineMode.CHAIN);
                screen.persistUiState();
                return;
            }
            if (mouseX >= areaBtnX && mouseX <= areaBtnX + modeBtnW) {
                setMode(UltimineMode.AREA);
                screen.persistUiState();
            }
        }
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
    protected void computeDefaultPosition() {
        int y = TOP_H + 10;
        PanelLayouts.QuickBuildPanelLayout quickBuildLayout = screen.resolveQuickBuildPanelLayout();
        if (quickBuildLayout != null) {
            y = quickBuildLayout.y() + quickBuildLayout.h() + 8;
        }
        this.windowX = Math.max(4, screen.width - ULTIMINE_PANEL_W - 10);
        this.windowY = y;
    }

    @Override
    protected boolean canShowWindow() {
        return super.canShowWindow() && screen.hasProgressionNode(RtsProgressionNodes.ULTIMINE);
    }

    @Override
    protected void onClose() {
        screen.persistUiState();
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && this.sliderDragging) {
            setLimitFromSliderX(mouseX);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.sliderDragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.limitEditing) {
            return handleLimitKeyPressed(keyCode);
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    protected boolean handleWindowCharTyped(char codePoint, int modifiers) {
        if (!this.limitEditing) {
            return false;
        }
        if (Character.isDigit(codePoint) && this.limitDraft.length() < 4) {
            if (this.limitSelectAll) {
                this.limitDraft = "";
                this.limitSelectAll = false;
            }
            this.limitDraft += codePoint;
        }
        return true;
    }

    public boolean handleKeyPressed(int keyCode) {
        return handleLimitKeyPressed(keyCode);
    }

    public boolean handleCharTyped(char codePoint) {
        return handleWindowCharTyped(codePoint, 0);
    }

    private void renderLimitInput(GuiGraphics g) {
        int inputX = this.windowX + (ULTIMINE_PANEL_W - 50) / 2;
        int inputY = contentY() + 52;
        g.fill(inputX, inputY, inputX + 50, inputY + 12, 0xFF0D1117);
        g.fill(inputX + 1, inputY + 1, inputX + 49, inputY + 11, 0xFF1A2330);
        String displayText = this.limitEditing ? this.limitDraft : Integer.toString(this.ultimineLimit);
        if (displayText.isEmpty()) {
            displayText = "_";
        }
        boolean showCursor = this.limitEditing && (System.currentTimeMillis() / 500L % 2L == 0L);
        String text = displayText + (showCursor ? "|" : "");
        RtsClientUiUtil.drawCenteredStringNoShadow(g, screen.font(), text,
                inputX + 25, inputY + (12 - screen.font().lineHeight) / 2, 0xF2F7FF);
    }

    private void renderLimitSlider(GuiGraphics g, int mouseX, int mouseY) {
        int trackX = this.windowX + 8;
        int trackY = contentY() + 72;
        int trackW = ULTIMINE_PANEL_W - 16;
        double fraction = (this.ultimineLimit - ULTIMINE_MIN_LIMIT)
                / (double) (ULTIMINE_MAX_LIMIT - ULTIMINE_MIN_LIMIT);
        int knobX = trackX + (int) Math.round(fraction * trackW);
        g.fill(trackX, trackY, trackX + trackW, trackY + 4, 0xFF07090D);
        g.fill(trackX + 1, trackY + 1, trackX + trackW - 1, trackY + 3, 0xFF313946);
        g.fill(trackX + 1, trackY + 1, knobX, trackY + 3, 0xFF5FE36C);
        boolean hoverSlider = !this.sliderDragging && mouseY >= trackY - 6 && mouseY <= trackY + 10
                && mouseX >= knobX - 4 && mouseX <= knobX + 5;
        int knobColor = this.sliderDragging ? 0xFF8AFF8A : (hoverSlider ? 0xFF6AFF7A : 0xFF5FE36C);
        g.fill(knobX - 3, trackY - 5, knobX + 4, trackY + 8, knobColor);
        g.drawString(screen.font(), Integer.toString(ULTIMINE_MIN_LIMIT), trackX, trackY + 10, 0xB5C1CE, false);
        g.drawString(screen.font(), Integer.toString(ULTIMINE_MAX_LIMIT), trackX + trackW - 20, trackY + 10, 0xB5C1CE, false);
    }

    private void renderModeButtons(GuiGraphics g, int mouseX, int mouseY) {
        int modeBtnY = contentY() + 92;
        int modeBtnW = (ULTIMINE_PANEL_W - 20) / 2;
        int chainBtnX = this.windowX + 8;
        int areaBtnX = chainBtnX + modeBtnW + 4;
        renderModeButton(g, mouseX, mouseY, chainBtnX, modeBtnY, modeBtnW,
                screen.text("screen.rtsbuilding.ultimine.mode_chain"), this.ultimineMode == UltimineMode.CHAIN);
        renderModeButton(g, mouseX, mouseY, areaBtnX, modeBtnY, modeBtnW,
                screen.text("screen.rtsbuilding.ultimine.mode_area"), this.ultimineMode == UltimineMode.AREA);
    }

    private void renderModeButton(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w, String label, boolean active) {
        boolean hover = inside(mouseX, mouseY, x, y, w, 14);
        int bg = active ? 0xFF5FE36C : (hover ? 0xFF2A3A4A : 0xFF1A2330);
        int border = active ? 0xFF5FE36C : (hover ? 0xFF647B92 : 0xFF313946);
        g.fill(x, y, x + w, y + 14, border);
        g.fill(x + 1, y + 1, x + w - 1, y + 13, bg);
        RtsClientUiUtil.drawCenteredStringNoShadow(g, screen.font(), label, x + w / 2, y + 3,
                active ? 0xFF0D1117 : 0xB5C1CE);
    }

    private boolean handleLimitKeyPressed(int keyCode) {
        if (!this.limitEditing) {
            return false;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            commitLimitEdit();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            cancelLimitEdit();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            if (this.limitSelectAll) {
                this.limitDraft = "";
                this.limitSelectAll = false;
            } else if (!this.limitDraft.isEmpty()) {
                this.limitDraft = this.limitDraft.substring(0, this.limitDraft.length() - 1);
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DELETE) {
            this.limitDraft = "";
            this.limitSelectAll = false;
            return true;
        }
        return true;
    }

    private void beginLimitEdit() {
        this.limitDraft = Integer.toString(this.ultimineLimit);
        this.limitEditing = true;
        this.limitSelectAll = true;
        screen.blurSearchFocus();
    }

    private void commitLimitEdit() {
        if (!this.limitEditing) {
            return;
        }
        try {
            if (!this.limitDraft.isBlank()) {
                this.ultimineLimit = clampUltimineLimit(Integer.parseInt(this.limitDraft));
            }
        } catch (NumberFormatException ignored) {
        }
        cancelLimitEdit();
        screen.persistUiState();
    }

    private void cancelLimitEdit() {
        this.limitEditing = false;
        this.limitSelectAll = false;
        this.limitDraft = "";
    }

    private void setLimitFromSliderX(double mouseX) {
        int trackX = this.windowX + 8;
        int trackW = ULTIMINE_PANEL_W - 16;
        double fraction = Mth.clamp((mouseX - trackX) / (double) trackW, 0.0D, 1.0D);
        this.ultimineLimit = clampUltimineLimit((int) Math.round(
                ULTIMINE_MIN_LIMIT + fraction * (ULTIMINE_MAX_LIMIT - ULTIMINE_MIN_LIMIT)));
    }

    private static int clampUltimineLimit(int value) {
        return Math.max(ULTIMINE_MIN_LIMIT, Math.min(ULTIMINE_MAX_LIMIT, value));
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }
}
