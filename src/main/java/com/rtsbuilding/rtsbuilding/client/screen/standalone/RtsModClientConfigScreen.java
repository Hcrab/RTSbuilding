package com.rtsbuilding.rtsbuilding.client.screen.standalone;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uikit.theme.StandaloneScreenStyle;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

public final class RtsModClientConfigScreen extends Screen {
    private static final int CONTENT_MAX_W = 720;
    private static final int HEADER_H = 40;
    private static final int FOOTER_H = 40;
    private static final int OPTION_ROW_H = 38;
    private static final int SECTION_H = 18;
    private static final int SCROLL_STEP = 24;

    private final Screen parent;

    private boolean developerMode = Config.isDeveloperModeEnabled();
    private boolean inventoryRtsButtonEnabled = Config.isInventoryRtsButtonEnabled();
    private int scroll;

    public RtsModClientConfigScreen(Screen parent) {
        super(Component.translatable("config.rtsbuilding.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        rebuildConfigWidgets();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderPageBackground(g);
        g.drawCenteredString(this.font, this.title, this.width / 2, 14,
                StandaloneScreenStyle.TITLE_TEXT.toArgb());
        drawGeneralPage(g);
        drawScrollbar(g);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            saveAndClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (insideViewport(mouseX, mouseY)) {
            int next = Mth.clamp(this.scroll - (int) Math.signum(scrollY) * SCROLL_STEP, 0, maxScroll());
            if (next != this.scroll) {
                setFocused(null);
                this.scroll = next;
                rebuildConfigWidgets();
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void renderBlurredBackground(float partialTick) {
    }

    private void rebuildConfigWidgets() {
        clearWidgets();
        this.scroll = Mth.clamp(this.scroll, 0, maxScroll());
        addGeneralWidgets();
        addFooterButtons();
    }

    private void addGeneralWidgets() {
        int x = contentX();
        int width = contentWidth();
        int controlW = controlWidth(width);
        int controlX = x + width - controlW - 10;
        int y = viewportTop() - this.scroll + SECTION_H;

        if (fullyVisible(y, OPTION_ROW_H)) {
            addRenderableWidget(Button.builder(Component.translatable(this.inventoryRtsButtonEnabled
                    ? "config.rtsbuilding.enabled"
                    : "config.rtsbuilding.disabled"), btn -> {
                this.inventoryRtsButtonEnabled = !this.inventoryRtsButtonEnabled;
                rebuildConfigWidgets();
            }).bounds(controlX, y + 9, controlW, 20).build());
        }
        y += OPTION_ROW_H + 6 + SECTION_H;

        if (fullyVisible(y, OPTION_ROW_H)) {
            addRenderableWidget(Button.builder(Component.translatable(this.developerMode
                    ? "config.rtsbuilding.enabled" : "config.rtsbuilding.disabled"), btn -> {
                this.developerMode = !this.developerMode;
                rebuildConfigWidgets();
            }).bounds(controlX, y + 9, controlW, 20).build());
        }
    }

    private void addFooterButtons() {
        int buttonW = Math.min(96, Math.max(72, this.width / 4));
        int footerY = this.height - 28;
        int startX = (this.width - buttonW * 2 - 8) / 2;
        addRenderableWidget(Button.builder(Component.translatable("config.rtsbuilding.save"), btn -> saveAndClose())
                .bounds(startX, footerY, buttonW, 20)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("gui.rtsbuilding.cancel"), btn -> closeWithoutSave())
                .bounds(startX + buttonW + 8, footerY, buttonW, 20)
                .build());
    }

    private void closeWithoutSave() {
        this.minecraft.setScreen(this.parent);
    }

    private void saveAndClose() {
        try {
            Config.saveClientSettings(
                this.inventoryRtsButtonEnabled,
                this.developerMode
            );
        } catch (RuntimeException ex) {
            ex.printStackTrace();
            return;
        }
        this.minecraft.setScreen(this.parent);
    }

    private void drawGeneralPage(GuiGraphics g) {
        int x = contentX();
        int y = viewportTop() - this.scroll;
        int width = contentWidth();
        g.enableScissor(x, viewportTop(), x + width, viewportBottom());

        drawSection(g, x, y, Component.translatable("config.rtsbuilding.section.compat"));
        y += SECTION_H;
        drawOptionRow(g, x, y, width, Component.translatable("rtsbuilding.configuration.showInventoryRtsButton"),
                Component.translatable("rtsbuilding.configuration.showInventoryRtsButton.tooltip"));
        y += OPTION_ROW_H + 6;

        drawSection(g, x, y, Component.translatable("config.rtsbuilding.section.developer"));
        y += SECTION_H;
        drawOptionRow(g, x, y, width, Component.translatable("config.rtsbuilding.option.developer_mode"),
                Component.translatable("config.rtsbuilding.option.developer_mode.hint"));
        g.disableScissor();
    }

    private int contentHeight() {
        return SECTION_H * 2 + OPTION_ROW_H * 2 + 6;
    }

    private int maxScroll() {
        return Math.max(0, contentHeight() - viewportHeight());
    }

    private int contentWidth() {
        return Math.max(0, Math.min(CONTENT_MAX_W, this.width - 32));
    }

    private int contentX() {
        return (this.width - contentWidth()) / 2;
    }

    private int viewportTop() {
        return HEADER_H + 10;
    }

    private int viewportBottom() {
        return Math.max(viewportTop(), this.height - FOOTER_H - 8);
    }

    private int viewportHeight() {
        return Math.max(0, viewportBottom() - viewportTop());
    }

    private int controlWidth(int width) {
        return Math.min(150, Math.max(92, width / 3));
    }

    private boolean fullyVisible(int y, int height) {
        return y >= viewportTop() && y + height <= viewportBottom();
    }

    private boolean insideViewport(double mouseX, double mouseY) {
        return UiRect.contains(contentX(), viewportTop(), contentWidth(), viewportHeight(),
                mouseX, mouseY);
    }

    private void renderPageBackground(GuiGraphics g) {
        g.fill(0, 0, this.width, this.height, StandaloneScreenStyle.PAGE_BACKGROUND.toArgb());
        g.fill(0, 0, this.width, HEADER_H, StandaloneScreenStyle.BAR_BACKGROUND.toArgb());
        g.fill(0, this.height - FOOTER_H, this.width, this.height,
                StandaloneScreenStyle.BAR_BACKGROUND.toArgb());
        g.hLine(0, this.width, HEADER_H, StandaloneScreenStyle.BAR_DIVIDER.toArgb());
        g.hLine(0, this.width, this.height - FOOTER_H,
                StandaloneScreenStyle.BAR_DIVIDER.toArgb());
    }

    private void drawSection(GuiGraphics g, int x, int y, Component label) {
        g.drawString(this.font, label, x + 2, y + 5,
                StandaloneScreenStyle.SECTION_TEXT.toArgb());
        g.hLine(x, x + contentWidth(), y + SECTION_H - 1,
                StandaloneScreenStyle.INFO_ROW_DIVIDER.toArgb());
    }

    private void drawOptionRow(GuiGraphics g, int x, int y, int width, Component label, Component hint) {
        int controlW = controlWidth(width);
        int hintW = Math.max(24, width - controlW - 34);
        g.fill(x, y, x + width, y + OPTION_ROW_H - 2,
                StandaloneScreenStyle.INFO_ROW_BACKGROUND.toArgb());
        g.hLine(x, x + width, y, StandaloneScreenStyle.INFO_ROW_DIVIDER.toArgb());
        g.drawString(this.font, label, x + 10, y + 7,
                StandaloneScreenStyle.INFO_VALUE.toArgb());
        String hintText = this.font.plainSubstrByWidth(hint.getString(), hintW);
        g.drawString(this.font, Component.literal(hintText), x + 10, y + 20,
                StandaloneScreenStyle.INFO_LABEL.toArgb());
    }

    private void drawScrollbar(GuiGraphics g) {
        int max = maxScroll();
        int viewportH = viewportHeight();
        int contentH = contentHeight();
        if (max <= 0 || viewportH <= 0 || contentH <= 0) {
            return;
        }
        int x = contentX() + contentWidth() - 4;
        int y = viewportTop();
        int thumbH = Math.max(18, viewportH * viewportH / contentH);
        int thumbY = y + (viewportH - thumbH) * this.scroll / max;
        g.fill(x, y, x + 3, y + viewportH, StandaloneScreenStyle.SCROLLBAR_TRACK.toArgb());
        g.fill(x, thumbY, x + 3, thumbY + thumbH,
                StandaloneScreenStyle.INFO_LABEL.toArgb());
    }
}
