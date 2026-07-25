package com.rtsbuilding.rtsbuilding.client.screen.standalone;


import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.uikit.theme.StandaloneScreenStyle;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiColor;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

public final class RtsHomeScreen extends Screen {
    private static final int CONTENT_MAX_W = 560;
    private static final int ROW_H = 28;
    private static final int FOOTER_H = 36;
    private static final long TICKS_PER_GAME_DAY = 24000L;

    private final Screen parent;
    private final ClientRtsController controller = ClientRtsController.get();
    private Button homeButton;
    private int refreshTicks;

    public RtsHomeScreen(Screen parent) {
        super(Component.translatable("screen.rtsbuilding.home"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.controller.requestProgressionState();
        int actionW = footerActionWidth();
        int footerX = footerX(actionW);
        int footerY = this.height - 28;
        this.homeButton = Button.builder(homeButtonLabel(), btn -> {
            this.minecraft.setScreen(null);
            this.controller.beginHomeSelection();
        }).bounds(footerX, footerY, actionW, 20).build();
        this.homeButton.active = canUseHomeButton();
        addRenderableWidget(this.homeButton);
        addRenderableWidget(Button.builder(Component.translatable("gui.rtsbuilding.back"),
                btn -> this.minecraft.setScreen(this.parent)).bounds(footerX + actionW + 8, footerY, 80, 20).build());
    }

    @Override
    public void tick() {
        super.tick();
        if (++this.refreshTicks >= 20) {
            this.refreshTicks = 0;
            this.controller.requestProgressionState();
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderPageBackground(g);
        if (this.homeButton != null) {
            this.homeButton.setMessage(homeButtonLabel());
            this.homeButton.active = canUseHomeButton();
        }
        int contentW = contentWidth();
        int x = (this.width - contentW) / 2;
        int y = 42;

        g.drawCenteredString(this.font, Component.translatable("screen.rtsbuilding.home"),
                this.width / 2, 12, StandaloneScreenStyle.TITLE_TEXT.toArgb());
        drawInfoRow(g, x, y, contentW, Component.translatable("screen.rtsbuilding.progression.title"),
                Component.translatable(this.controller.isProgressionEnabled()
                        ? "screen.rtsbuilding.progression.survival_on"
                        : "screen.rtsbuilding.progression.survival_off"),
                StandaloneScreenStyle.progressionStatus(this.controller.isProgressionEnabled()));
        y += ROW_H + 4;
        if (this.controller.isProgressionHomeSet()) {
            BlockPos pos = this.controller.getProgressionHomePos();
            long cooldownDays = remainingHomeCooldownDays();
            drawInfoRow(g, x, y, contentW, Component.translatable("screen.rtsbuilding.home"),
                    Component.translatable("screen.rtsbuilding.home.current_with_cooldown",
                            pos.getX(), pos.getY(), pos.getZ(), cooldownDays),
                    StandaloneScreenStyle.homeStatus(cooldownDays > 0L));
            y += ROW_H + 4;
            drawInfoRow(g, x, y, contentW, Component.translatable("screen.rtsbuilding.home.dimension_label"),
                    Component.translatable("screen.rtsbuilding.home.dimension",
                            this.controller.getProgressionHomeDimension()),
                    StandaloneScreenStyle.INFO_DIMENSION);
        } else {
            drawInfoRow(g, x, y, contentW, Component.translatable("screen.rtsbuilding.home"),
                    Component.translatable("screen.rtsbuilding.home.not_set"),
                    StandaloneScreenStyle.WARNING_TEXT);
            y += ROW_H + 4;
            drawInfoRow(g, x, y, contentW, Component.translatable("screen.rtsbuilding.home.dimension_label"),
                    Component.literal("-"), StandaloneScreenStyle.INFO_EMPTY);
        }
        y += ROW_H + 4;
        drawInfoRow(g, x, y, contentW, Component.translatable("screen.rtsbuilding.home.radius_label"),
                Component.translatable("screen.rtsbuilding.home.radius",
                        this.controller.getProgressionRadiusBlocks()),
                StandaloneScreenStyle.INFO_RADIUS);
        y += ROW_H + 10;

        Component warning = Component.translatable("screen.rtsbuilding.home.warning");
        int warningHeight = 18 + Math.max(1, this.font.split(warning, contentW - 20).size()) * 10;
        int warningBottom = Math.max(y + 24, Math.min(this.height - FOOTER_H - 8, y + warningHeight));
        g.fill(x, y, x + contentW, warningBottom,
                StandaloneScreenStyle.WARNING_BACKGROUND.toArgb());
        g.hLine(x, x + contentW, y, StandaloneScreenStyle.WARNING_DIVIDER.toArgb());
        drawWrapped(g, warning, x + 10, y + 9, contentW - 20,
                StandaloneScreenStyle.WARNING_TEXT);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private boolean canChangeHome() {
        return this.controller.getProgressionHomeCooldownTicks() <= 0L;
    }

    private boolean canUseHomeButton() {
        return this.controller.isProgressionEnabled()
                && (!this.controller.isProgressionHomeSet() || canChangeHome());
    }

    private Component homeButtonLabel() {
        String labelKey = this.controller.isProgressionHomeSet()
                ? "screen.rtsbuilding.home.change"
                : "screen.rtsbuilding.home.set";
        return Component.translatable(labelKey);
    }

    private int contentWidth() {
        return Math.min(CONTENT_MAX_W, this.width - 32);
    }

    private long remainingHomeCooldownDays() {
        long ticks = Math.max(0L, this.controller.getProgressionHomeCooldownTicks());
        return ticks <= 0L ? 0L : (ticks + TICKS_PER_GAME_DAY - 1L) / TICKS_PER_GAME_DAY;
    }

    private void drawWrapped(GuiGraphics g, Component text, int x, int y, int width, UiColor color) {
        for (var line : this.font.split(text, width)) {
            g.drawString(this.font, line, x, y, color.toArgb());
            y += 10;
        }
    }

    private void drawInfoRow(GuiGraphics g, int x, int y, int width,
                             Component label, Component value, UiColor valueColor) {
        int labelW = Math.min(132, Math.max(92, width / 3));
        g.fill(x, y, x + width, y + ROW_H, StandaloneScreenStyle.INFO_ROW_BACKGROUND.toArgb());
        g.hLine(x, x + width, y, StandaloneScreenStyle.INFO_ROW_DIVIDER.toArgb());
        g.drawString(this.font, label, x + 10, y + 9, StandaloneScreenStyle.INFO_LABEL.toArgb());
        String valueText = this.font.plainSubstrByWidth(value.getString(), width - labelW - 24);
        g.drawString(this.font, Component.literal(valueText), x + labelW, y + 9,
                valueColor.toArgb());
    }

    private void renderPageBackground(GuiGraphics g) {
        g.fill(0, 0, this.width, this.height, StandaloneScreenStyle.PAGE_BACKGROUND.toArgb());
        g.fill(0, 0, this.width, 32, StandaloneScreenStyle.BAR_BACKGROUND.toArgb());
        g.fill(0, this.height - FOOTER_H, this.width, this.height,
                StandaloneScreenStyle.BAR_BACKGROUND.toArgb());
        g.hLine(0, this.width, 32, StandaloneScreenStyle.BAR_DIVIDER.toArgb());
        g.hLine(0, this.width, this.height - FOOTER_H,
                StandaloneScreenStyle.BAR_DIVIDER.toArgb());
    }

    private int footerActionWidth() {
        return Math.min(170, Math.max(118, this.width / 2 - 28));
    }

    private int footerX(int actionW) {
        return (this.width - actionW - 8 - 80) / 2;
    }
}
