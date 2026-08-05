package com.rtsbuilding.rtsbuilding.client.screen.standalone;

import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.uikit.theme.StandaloneScreenStyle;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiColor;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import com.rtsbuilding.rtsbuilding.platform.math.BlockPos;
import org.lwjgl.input.Keyboard;

import java.io.IOException;
import java.util.List;

/** 1.12.2 的 RTS 家园信息页。页面只发起客户端命令，不持有服务端实现类。 */
public final class RtsHomeScreen extends GuiScreen {
    private static final int HOME_BUTTON = 0;
    private static final int BACK_BUTTON = 1;
    private static final int CONTENT_MAX_W = 560;
    private static final int ROW_H = 28;
    private static final int FOOTER_H = 36;
    private static final long TICKS_PER_GAME_DAY = 24000L;

    private final GuiScreen parent;
    private final ClientRtsController controller = ClientRtsController.get();
    private GuiButton homeButton;
    private int refreshTicks;

    public RtsHomeScreen(GuiScreen parent) {
        this.parent = parent;
    }

    @Override
    public void initGui() {
        this.buttonList.clear();
        this.controller.requestProgressionState();
        int actionW = footerActionWidth();
        int footerX = footerX(actionW);
        int footerY = this.height - 28;
        this.homeButton = new GuiButton(HOME_BUTTON, footerX, footerY, actionW, 20, homeButtonLabel());
        this.homeButton.enabled = canUseHomeButton();
        this.buttonList.add(this.homeButton);
        this.buttonList.add(new GuiButton(BACK_BUTTON, footerX + actionW + 8, footerY, 80, 20,
                I18n.format("gui.rtsbuilding.back")));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == HOME_BUTTON && button.enabled) {
            this.mc.displayGuiScreen(null);
            this.controller.beginHomeSelection();
        } else if (button.id == BACK_BUTTON) {
            this.mc.displayGuiScreen(this.parent);
        }
    }

    @Override
    public void updateScreen() {
        if (++this.refreshTicks >= 20) {
            this.refreshTicks = 0;
            this.controller.requestProgressionState();
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        renderPageBackground();
        if (this.homeButton != null) {
            this.homeButton.displayString = homeButtonLabel();
            this.homeButton.enabled = canUseHomeButton();
        }
        int contentW = contentWidth();
        int x = (this.width - contentW) / 2;
        int y = 42;
        drawCenteredString(this.fontRendererObj, I18n.format("screen.rtsbuilding.home"), this.width / 2, 12,
                StandaloneScreenStyle.TITLE_TEXT.toArgb());
        drawInfoRow(x, y, contentW, I18n.format("screen.rtsbuilding.progression.title"),
                I18n.format(this.controller.isProgressionEnabled()
                        ? "screen.rtsbuilding.progression.survival_on"
                        : "screen.rtsbuilding.progression.survival_off"),
                StandaloneScreenStyle.progressionStatus(this.controller.isProgressionEnabled()));
        y += ROW_H + 4;
        if (this.controller.isProgressionHomeSet()) {
            BlockPos pos = this.controller.getProgressionHomePos();
            long cooldownDays = remainingHomeCooldownDays();
            drawInfoRow(x, y, contentW, I18n.format("screen.rtsbuilding.home"),
                    I18n.format("screen.rtsbuilding.home.current_with_cooldown",
                            pos.getX(), pos.getY(), pos.getZ(), cooldownDays),
                    StandaloneScreenStyle.homeStatus(cooldownDays > 0L));
            y += ROW_H + 4;
            drawInfoRow(x, y, contentW, I18n.format("screen.rtsbuilding.home.dimension_label"),
                    I18n.format("screen.rtsbuilding.home.dimension", this.controller.getProgressionHomeDimension()),
                    StandaloneScreenStyle.INFO_DIMENSION);
        } else {
            drawInfoRow(x, y, contentW, I18n.format("screen.rtsbuilding.home"),
                    I18n.format("screen.rtsbuilding.home.not_set"), StandaloneScreenStyle.WARNING_TEXT);
            y += ROW_H + 4;
            drawInfoRow(x, y, contentW, I18n.format("screen.rtsbuilding.home.dimension_label"), "-",
                    StandaloneScreenStyle.INFO_EMPTY);
        }
        y += ROW_H + 4;
        drawInfoRow(x, y, contentW, I18n.format("screen.rtsbuilding.home.radius_label"),
                I18n.format("screen.rtsbuilding.home.radius", this.controller.getProgressionRadiusBlocks()),
                StandaloneScreenStyle.INFO_RADIUS);
        y += ROW_H + 10;

        String warning = I18n.format("screen.rtsbuilding.home.warning");
        List<String> lines = this.fontRendererObj.listFormattedStringToWidth(warning, contentW - 20);
        int warningHeight = 18 + Math.max(1, lines.size()) * 10;
        int warningBottom = Math.max(y + 24, Math.min(this.height - FOOTER_H - 8, y + warningHeight));
        drawRect(x, y, x + contentW, warningBottom, StandaloneScreenStyle.WARNING_BACKGROUND.toArgb());
        drawRect(x, y, x + contentW, y + 1, StandaloneScreenStyle.WARNING_DIVIDER.toArgb());
        drawWrapped(lines, x + 10, y + 9, StandaloneScreenStyle.WARNING_TEXT);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override public boolean doesGuiPauseGame() { return false; }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) this.mc.displayGuiScreen(this.parent);
        else super.keyTyped(typedChar, keyCode);
    }

    private boolean canUseHomeButton() {
        return this.controller.isProgressionEnabled()
                && (!this.controller.isProgressionHomeSet()
                || this.controller.getProgressionHomeCooldownTicks() <= 0L);
    }

    private String homeButtonLabel() {
        return I18n.format(this.controller.isProgressionHomeSet()
                ? "screen.rtsbuilding.home.change" : "screen.rtsbuilding.home.set");
    }

    private int contentWidth() { return Math.min(CONTENT_MAX_W, this.width - 32); }

    private long remainingHomeCooldownDays() {
        long ticks = Math.max(0L, this.controller.getProgressionHomeCooldownTicks());
        return ticks <= 0L ? 0L : (ticks + TICKS_PER_GAME_DAY - 1L) / TICKS_PER_GAME_DAY;
    }

    private void drawWrapped(List<String> lines, int x, int y, UiColor color) {
        for (String line : lines) {
            this.fontRendererObj.drawString(line, x, y, color.toArgb(), false);
            y += 10;
        }
    }

    private void drawInfoRow(int x, int y, int width, String label, String value, UiColor valueColor) {
        int labelW = Math.min(132, Math.max(92, width / 3));
        drawRect(x, y, x + width, y + ROW_H, StandaloneScreenStyle.INFO_ROW_BACKGROUND.toArgb());
        drawRect(x, y, x + width, y + 1, StandaloneScreenStyle.INFO_ROW_DIVIDER.toArgb());
        this.fontRendererObj.drawString(label, x + 10, y + 9, StandaloneScreenStyle.INFO_LABEL.toArgb(), false);
        String valueText = this.fontRendererObj.trimStringToWidth(value, width - labelW - 24);
        this.fontRendererObj.drawString(valueText, x + labelW, y + 9, valueColor.toArgb(), false);
    }

    private void renderPageBackground() {
        drawRect(0, 0, this.width, this.height, StandaloneScreenStyle.PAGE_BACKGROUND.toArgb());
        drawRect(0, 0, this.width, 32, StandaloneScreenStyle.BAR_BACKGROUND.toArgb());
        drawRect(0, this.height - FOOTER_H, this.width, this.height, StandaloneScreenStyle.BAR_BACKGROUND.toArgb());
        drawRect(0, 32, this.width, 33, StandaloneScreenStyle.BAR_DIVIDER.toArgb());
        drawRect(0, this.height - FOOTER_H, this.width, this.height - FOOTER_H + 1,
                StandaloneScreenStyle.BAR_DIVIDER.toArgb());
    }

    private int footerActionWidth() { return Math.min(170, Math.max(118, this.width / 2 - 28)); }
    private int footerX(int actionW) { return (this.width - actionW - 8 - 80) / 2; }
}
