package com.rtsbuilding.rtsbuilding.client.screen.standalone;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

public final class RtsModConfigScreen extends Screen {
    private static final int CONTENT_MAX_W = 720;
    private static final int HEADER_H = 40;
    private static final int FOOTER_H = 40;
    private static final int OPTION_ROW_H = 38;
    private static final int SECTION_H = 18;
    private static final int SCROLL_STEP = 24;

    private final Screen parent;

    private boolean survivalEnabled = Config.ENABLE_SURVIVAL_PROGRESSION.getAsBoolean();
    private boolean shareWithTeams = Config.SHARE_SURVIVAL_PROGRESSION_WITH_TEAMS.getAsBoolean();
    private boolean blueprintsEnabled = Config.ENABLE_BLUEPRINTS.getAsBoolean();
    private boolean developerMode = Config.isDeveloperModeEnabled();
    private String draftMaxRadius = Integer.toString(Config.maxActionRadiusBlocks());
    private String draftMaxBlueprintBlocks = Integer.toString(Config.maxBlueprintBlocks());
    private String draftAreaMineMaxWidth = Integer.toString(Config.areaMineMaxWidth());
    private String draftAreaMineMaxHeight = Integer.toString(Config.areaMineMaxHeight());
    private String draftAreaMineMaxDepth = Integer.toString(Config.areaMineMaxDepth());
    private String draftAreaMineMaxVolume = Integer.toString(Config.areaMineMaxVolume());
    private String draftAreaDestroyMaxTargets = Integer.toString(Config.areaDestroyMaxTargets());
    private EditBox maxRadiusBox;
    private EditBox maxBlueprintBlocksBox;
    private EditBox areaMineMaxWidthBox;
    private EditBox areaMineMaxHeightBox;
    private EditBox areaMineMaxDepthBox;
    private EditBox areaMineMaxVolumeBox;
    private EditBox areaDestroyMaxTargetsBox;
    private int scroll;

    public RtsModConfigScreen(Screen parent) {
        super(Component.translatable("config.rtsbuilding.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        rebuildConfigWidgets(false);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderPageBackground(g);
        g.drawCenteredString(this.font, this.title, this.width / 2, 14, 0xFFFFFFFF);
        drawGeneralPage(g);
        drawScrollbar(g);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (insideViewport(mouseX, mouseY)) {
            int next = Mth.clamp(this.scroll - (int) Math.signum(scrollY) * SCROLL_STEP, 0, maxScroll());
            if (next != this.scroll) {
                captureVisibleDrafts();
                setFocused(null);
                this.scroll = next;
                rebuildConfigWidgets(false);
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
        rebuildConfigWidgets(true);
    }

    private void rebuildConfigWidgets(boolean captureDrafts) {
        if (captureDrafts) {
            captureVisibleDrafts();
        }
        clearWidgets();
        this.maxRadiusBox = null;
        this.maxBlueprintBlocksBox = null;
        this.areaMineMaxWidthBox = null;
        this.areaMineMaxHeightBox = null;
        this.areaMineMaxDepthBox = null;
        this.areaMineMaxVolumeBox = null;
        this.areaDestroyMaxTargetsBox = null;
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
            addRenderableWidget(Button.builder(Component.translatable(this.survivalEnabled
                    ? "config.rtsbuilding.enabled"
                    : "config.rtsbuilding.disabled"), btn -> {
                this.survivalEnabled = !this.survivalEnabled;
                rebuildConfigWidgets();
            }).bounds(controlX, y + 9, controlW, 20).build());
        }
        y += OPTION_ROW_H;

        if (fullyVisible(y, OPTION_ROW_H)) {
            addRenderableWidget(Button.builder(Component.translatable(this.shareWithTeams
                    ? "config.rtsbuilding.enabled"
                    : "config.rtsbuilding.disabled"), btn -> {
                this.shareWithTeams = !this.shareWithTeams;
                rebuildConfigWidgets();
            }).bounds(controlX, y + 9, controlW, 20).build());
        }
        y += OPTION_ROW_H;

        if (fullyVisible(y, OPTION_ROW_H)) {
            this.maxRadiusBox = addIntegerBox(controlX, y, controlW,
                    Component.translatable("config.rtsbuilding.max_radius"), this.draftMaxRadius, 4);
        }
        y += OPTION_ROW_H + 6 + SECTION_H;

        if (fullyVisible(y, OPTION_ROW_H)) {
            addRenderableWidget(Button.builder(Component.translatable(this.blueprintsEnabled
                    ? "config.rtsbuilding.enabled"
                    : "config.rtsbuilding.disabled"), btn -> {
                this.blueprintsEnabled = !this.blueprintsEnabled;
                rebuildConfigWidgets();
            }).bounds(controlX, y + 9, controlW, 20).build());
        }
        y += OPTION_ROW_H;

        if (fullyVisible(y, OPTION_ROW_H)) {
            this.maxBlueprintBlocksBox = addIntegerBox(controlX, y, controlW,
                    Component.translatable("config.rtsbuilding.max_blueprint_blocks"), this.draftMaxBlueprintBlocks, 6);
        }
        y += OPTION_ROW_H + 6 + SECTION_H;

        if (fullyVisible(y, OPTION_ROW_H)) {
            this.areaMineMaxWidthBox = addIntegerBox(controlX, y, controlW,
                    Component.translatable("config.rtsbuilding.area_mine_max_width"), this.draftAreaMineMaxWidth, 3);
        }
        y += OPTION_ROW_H;

        if (fullyVisible(y, OPTION_ROW_H)) {
            this.areaMineMaxHeightBox = addIntegerBox(controlX, y, controlW,
                    Component.translatable("config.rtsbuilding.area_mine_max_height"), this.draftAreaMineMaxHeight, 3);
        }
        y += OPTION_ROW_H;

        if (fullyVisible(y, OPTION_ROW_H)) {
            this.areaMineMaxDepthBox = addIntegerBox(controlX, y, controlW,
                    Component.translatable("config.rtsbuilding.area_mine_max_depth"), this.draftAreaMineMaxDepth, 3);
        }
        y += OPTION_ROW_H;

        if (fullyVisible(y, OPTION_ROW_H)) {
            this.areaMineMaxVolumeBox = addIntegerBox(controlX, y, controlW,
                    Component.translatable("config.rtsbuilding.area_mine_max_volume"), this.draftAreaMineMaxVolume, 6);
        }
        y += OPTION_ROW_H;

        if (fullyVisible(y, OPTION_ROW_H)) {
            this.areaDestroyMaxTargetsBox = addIntegerBox(controlX, y, controlW,
                    Component.translatable("config.rtsbuilding.area_destroy_max_targets"),
                    this.draftAreaDestroyMaxTargets, 6);
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

    private EditBox addIntegerBox(int x, int y, int width, Component label, String value, int maxLength) {
        EditBox box = new EditBox(this.font, x, y + 10, width, 18, label);
        box.setMaxLength(maxLength);
        box.setValue(value);
        box.setTextColor(0xFFFFFFFF);
        box.setTextColorUneditable(0xFFB8C7D6);
        addRenderableWidget(box);
        return box;
    }

    private void addFooterButtons() {
        int buttonW = Math.min(96, Math.max(72, this.width / 4));
        int footerY = this.height - 28;
        int startX = (this.width - buttonW * 2 - 8) / 2;
        addRenderableWidget(Button.builder(Component.translatable("config.rtsbuilding.save"), btn -> saveAndClose())
                .bounds(startX, footerY, buttonW, 20)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("gui.rtsbuilding.back"), btn -> this.minecraft.setScreen(this.parent))
                .bounds(startX + buttonW + 8, footerY, buttonW, 20)
                .build());
    }

    private void saveAndClose() {
        captureVisibleDrafts();
        try {
            Config.saveGeneralSettings(
                    this.survivalEnabled,
                    this.shareWithTeams,
                    parseMaxRadius(),
                    this.blueprintsEnabled,
                    parseMaxBlueprintBlocks());
            Config.saveAreaMineLimitSettings(
                    parseAreaMineMaxWidth(),
                    parseAreaMineMaxHeight(),
                    parseAreaMineMaxDepth(),
                    parseAreaMineMaxVolume(),
                    parseAreaDestroyMaxTargets());
            Config.setDeveloperModeEnabled(this.developerMode);
        } catch (RuntimeException ex) {
            if (this.minecraft != null && this.minecraft.player != null) {
                this.minecraft.player.displayClientMessage(Component.literal("RTSBuilding config save failed: " + ex.getClass().getSimpleName()), false);
            }
            return;
        }
        ClientRtsController.get().setSurvivalProgressionEnabled(this.survivalEnabled);
        this.minecraft.setScreen(this.parent);
    }

    private void captureVisibleDrafts() {
        if (this.maxRadiusBox != null) {
            this.draftMaxRadius = this.maxRadiusBox.getValue();
        }
        if (this.maxBlueprintBlocksBox != null) {
            this.draftMaxBlueprintBlocks = this.maxBlueprintBlocksBox.getValue();
        }
        if (this.areaMineMaxWidthBox != null) {
            this.draftAreaMineMaxWidth = this.areaMineMaxWidthBox.getValue();
        }
        if (this.areaMineMaxHeightBox != null) {
            this.draftAreaMineMaxHeight = this.areaMineMaxHeightBox.getValue();
        }
        if (this.areaMineMaxDepthBox != null) {
            this.draftAreaMineMaxDepth = this.areaMineMaxDepthBox.getValue();
        }
        if (this.areaMineMaxVolumeBox != null) {
            this.draftAreaMineMaxVolume = this.areaMineMaxVolumeBox.getValue();
        }
        if (this.areaDestroyMaxTargetsBox != null) {
            this.draftAreaDestroyMaxTargets = this.areaDestroyMaxTargetsBox.getValue();
        }
    }

    private int parseMaxRadius() {
        return parseClampedInt(this.draftMaxRadius, 48, 512, Config.maxActionRadiusBlocks());
    }

    private int parseMaxBlueprintBlocks() {
        return parseClampedInt(this.draftMaxBlueprintBlocks, 1, 200000, Config.maxBlueprintBlocks());
    }

    private int parseAreaMineMaxWidth() {
        return parseClampedInt(this.draftAreaMineMaxWidth, 1, 256, Config.areaMineMaxWidth());
    }

    private int parseAreaMineMaxHeight() {
        return parseClampedInt(this.draftAreaMineMaxHeight, 1, 256, Config.areaMineMaxHeight());
    }

    private int parseAreaMineMaxDepth() {
        return parseClampedInt(this.draftAreaMineMaxDepth, 1, 256, Config.areaMineMaxDepth());
    }

    private int parseAreaMineMaxVolume() {
        return parseClampedInt(this.draftAreaMineMaxVolume, 1, 262144, Config.areaMineMaxVolume());
    }

    private int parseAreaDestroyMaxTargets() {
        return parseClampedInt(this.draftAreaDestroyMaxTargets, 1, 262144, Config.areaDestroyMaxTargets());
    }

    private int parseClampedInt(String raw, int min, int max, int fallback) {
        try {
            return Mth.clamp(Integer.parseInt(raw.trim()), min, max);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private void drawGeneralPage(GuiGraphics g) {
        int x = contentX();
        int y = viewportTop() - this.scroll;
        int width = contentWidth();
        g.enableScissor(x, viewportTop(), x + width, viewportBottom());
        drawSection(g, x, y, Component.translatable("config.rtsbuilding.section.gameplay"));
        y += SECTION_H;
        drawOptionRow(g, x, y, width, Component.translatable("config.rtsbuilding.option.survival"),
                Component.translatable("config.rtsbuilding.option.survival.hint"));
        y += OPTION_ROW_H;
        drawOptionRow(g, x, y, width, Component.translatable("config.rtsbuilding.option.teams"),
                Component.translatable("config.rtsbuilding.option.teams.hint"));
        y += OPTION_ROW_H;
        drawOptionRow(g, x, y, width, Component.translatable("config.rtsbuilding.max_radius"),
                Component.translatable("config.rtsbuilding.max_radius.hint"));
        y += OPTION_ROW_H + 6;

        drawSection(g, x, y, Component.translatable("config.rtsbuilding.section.blueprints"));
        y += SECTION_H;
        drawOptionRow(g, x, y, width, Component.translatable("config.rtsbuilding.option.blueprints"),
                Component.translatable("config.rtsbuilding.option.blueprints.hint"));
        y += OPTION_ROW_H;
        drawOptionRow(g, x, y, width, Component.translatable("config.rtsbuilding.max_blueprint_blocks"),
                Component.translatable("config.rtsbuilding.max_blueprint_blocks.hint"));
        y += OPTION_ROW_H + 6;

        drawSection(g, x, y, Component.translatable("config.rtsbuilding.section.area_mining"));
        y += SECTION_H;
        drawOptionRow(g, x, y, width, Component.translatable("config.rtsbuilding.area_mine_max_width"),
                Component.translatable("config.rtsbuilding.area_mine_max_width.hint"));
        y += OPTION_ROW_H;
        drawOptionRow(g, x, y, width, Component.translatable("config.rtsbuilding.area_mine_max_height"),
                Component.translatable("config.rtsbuilding.area_mine_max_height.hint"));
        y += OPTION_ROW_H;
        drawOptionRow(g, x, y, width, Component.translatable("config.rtsbuilding.area_mine_max_depth"),
                Component.translatable("config.rtsbuilding.area_mine_max_depth.hint"));
        y += OPTION_ROW_H;
        drawOptionRow(g, x, y, width, Component.translatable("config.rtsbuilding.area_mine_max_volume"),
                Component.translatable("config.rtsbuilding.area_mine_max_volume.hint"));
        y += OPTION_ROW_H;
        drawOptionRow(g, x, y, width, Component.translatable("config.rtsbuilding.area_destroy_max_targets"),
                Component.translatable("config.rtsbuilding.area_destroy_max_targets.hint"));
        y += OPTION_ROW_H + 6;

        drawSection(g, x, y, Component.translatable("config.rtsbuilding.section.developer"));
        y += SECTION_H;
        drawOptionRow(g, x, y, width, Component.translatable("config.rtsbuilding.option.developer_mode"),
                Component.translatable("config.rtsbuilding.option.developer_mode.hint"));
        g.disableScissor();
    }

    private int contentHeight() {
        return SECTION_H * 4 + OPTION_ROW_H * 11 + 18;
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
        return mouseX >= contentX() && mouseX <= contentX() + contentWidth()
                && mouseY >= viewportTop() && mouseY <= viewportBottom();
    }

    private void renderPageBackground(GuiGraphics g) {
        g.fill(0, 0, this.width, this.height, 0xFF101820);
        g.fill(0, 0, this.width, HEADER_H, 0xFF151B23);
        g.fill(0, this.height - FOOTER_H, this.width, this.height, 0xFF151B23);
        g.hLine(0, this.width, HEADER_H, 0xFF273747);
        g.hLine(0, this.width, this.height - FOOTER_H, 0xFF273747);
    }

    private void drawSection(GuiGraphics g, int x, int y, Component label) {
        g.drawString(this.font, label, x + 2, y + 5, 0xFFF4F7FF);
        g.hLine(x, x + contentWidth(), y + SECTION_H - 1, 0xFF263545);
    }

    private void drawOptionRow(GuiGraphics g, int x, int y, int width, Component label, Component hint) {
        int controlW = controlWidth(width);
        int hintW = Math.max(24, width - controlW - 34);
        g.fill(x, y, x + width, y + OPTION_ROW_H - 2, 0xFF17202A);
        g.hLine(x, x + width, y, 0xFF263545);
        g.drawString(this.font, label, x + 10, y + 7, 0xFFEAF2FF);
        String hintText = this.font.plainSubstrByWidth(hint.getString(), hintW);
        g.drawString(this.font, Component.literal(hintText), x + 10, y + 20, 0xFFAFC2D4);
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
        g.fill(x, y, x + 3, y + viewportH, 0x66263545);
        g.fill(x, thumbY, x + 3, thumbY + thumbH, 0xFFAFC2D4);
    }
}
