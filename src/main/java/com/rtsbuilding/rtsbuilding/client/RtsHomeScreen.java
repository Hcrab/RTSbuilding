package com.rtsbuilding.rtsbuilding.client;

import com.rtsbuilding.rtsbuilding.progression.RtsProgressionNodes;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

public final class RtsHomeScreen extends Screen {
    private static final int PANEL_MAX_W = 390;
    private static final int PANEL_H = 224;
    private static final int HOME_BUTTON_Y_OFFSET = 108;
    private static final int WARNING_Y_OFFSET = 136;

    private final Screen parent;
    private final ClientRtsController controller = ClientRtsController.get();
    private Button homeButton;

    public RtsHomeScreen(Screen parent) {
        super(Component.translatable("screen.rtsbuilding.home"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.controller.requestProgressionState();
        int panelW = panelWidth();
        int x = (this.width - panelW) / 2;
        int y = panelY();
        this.homeButton = Button.builder(homeButtonLabel(), btn -> {
            this.minecraft.setScreen(null);
            this.controller.beginHomeSelection();
        }).bounds(x + 10, y + HOME_BUTTON_Y_OFFSET, panelW - 20, 20).build();
        this.homeButton.active = canUseHomeButton();
        addRenderableWidget(this.homeButton);
        addRenderableWidget(Button.builder(Component.translatable("gui.rtsbuilding.back"), btn -> this.minecraft.setScreen(this.parent))
                .bounds(x + panelW - 60, y + PANEL_H - 26, 50, 18)
                .build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        int panelW = panelWidth();
        int x = (this.width - panelW) / 2;
        int y = panelY();
        if (this.homeButton != null) {
            this.homeButton.setMessage(homeButtonLabel());
            this.homeButton.active = canUseHomeButton();
        }
        g.fill(x, y, x + panelW, y + PANEL_H, 0xEE101820);
        g.hLine(x, x + panelW, y, 0xFF6E8799);
        g.hLine(x, x + panelW, y + PANEL_H, 0xFF0D1218);
        g.drawString(this.font, Component.translatable("screen.rtsbuilding.home"), x + 10, y + 10, 0xFFFFFF);
        g.drawString(this.font, Component.translatable(this.controller.isProgressionEnabled() ? "screen.rtsbuilding.progression.survival_on" : "screen.rtsbuilding.progression.survival_off"), x + 10, y + 26, 0xCFE3F7);
        if (this.controller.isProgressionHomeSet()) {
            BlockPos pos = this.controller.getProgressionHomePos();
            g.drawString(this.font, Component.translatable("screen.rtsbuilding.home.current", pos.getX(), pos.getY(), pos.getZ()), x + 10, y + 44, 0xEAF2FF);
            g.drawString(this.font, Component.translatable("screen.rtsbuilding.home.dimension", this.controller.getProgressionHomeDimension()), x + 10, y + 58, 0xBFD2E6);
        } else {
            g.drawString(this.font, Component.translatable("screen.rtsbuilding.home.not_set"), x + 10, y + 44, 0xFFE7C46A);
        }
        g.drawString(this.font, Component.translatable("screen.rtsbuilding.home.radius", this.controller.getProgressionRadiusBlocks()), x + 10, y + 76, 0xD8E6F5);
        g.drawString(this.font, Component.translatable(canRelocateHome() ? "screen.rtsbuilding.home.relocation_unlocked" : "screen.rtsbuilding.home.relocation_locked"), x + 10, y + 90, canRelocateHome() ? 0xAEE8AE : 0xFFB0B0);
        drawWrapped(g, Component.translatable("screen.rtsbuilding.home.warning").getString(), x + 10, y + WARNING_Y_OFFSET, panelW - 20, 0xFFE7C46A);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private boolean canRelocateHome() {
        return this.controller.getUnlockedProgressionNodes().contains(RtsProgressionNodes.FIELD_DEPLOYMENT.toString());
    }

    private boolean canUseHomeButton() {
        return this.controller.isProgressionEnabled()
                && (!this.controller.isProgressionHomeSet() || canRelocateHome());
    }

    private Component homeButtonLabel() {
        String labelKey = this.controller.isProgressionHomeSet()
                ? "screen.rtsbuilding.home.change"
                : "screen.rtsbuilding.home.set";
        return Component.translatable(labelKey);
    }

    private int panelWidth() {
        return Math.min(PANEL_MAX_W, this.width - 32);
    }

    private int panelY() {
        return Math.max(18, (this.height - PANEL_H) / 2);
    }

    private void drawWrapped(GuiGraphics g, String text, int x, int y, int width, int color) {
        for (var line : this.font.split(Component.literal(text), width)) {
            g.drawString(this.font, line, x, y, color);
            y += 10;
        }
    }
}
