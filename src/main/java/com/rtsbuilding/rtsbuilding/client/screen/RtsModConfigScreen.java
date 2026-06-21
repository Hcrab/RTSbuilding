package com.rtsbuilding.rtsbuilding.client.screen;


import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.progression.RtsProgressionNode;
import com.rtsbuilding.rtsbuilding.progression.RtsProgressionNodes;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public final class RtsModConfigScreen extends Screen {
    private static final int CONTENT_MAX_W = 720;
    private static final int HEADER_H = 40;
    private static final int FOOTER_H = 40;
    private static final int TAB_H = 20;
    private static final int TAB_GAP = 6;
    private static final int OPTION_ROW_H = 38;
    private static final int COST_ROW_H = 30;
    private static final int SECTION_H = 18;
    private static final int SCROLL_STEP = 24;

    private enum Page {
        GENERAL("config.rtsbuilding.tab.general"),
        SKILLS("config.rtsbuilding.tab.skills");

        private final String titleKey;

        Page(String titleKey) {
            this.titleKey = titleKey;
        }
    }

    private final Screen parent;
    private final List<RtsProgressionNode> nodes = new ArrayList<>(RtsProgressionNodes.all());
    private final List<EditBox> costBoxes = new ArrayList<>();
    private final List<ResourceLocation> costBoxNodeIds = new ArrayList<>();
    private final Map<ResourceLocation, String> draftCosts = new LinkedHashMap<>();

    private Page page = Page.GENERAL;
    private boolean survivalEnabled = Config.ENABLE_SURVIVAL_PROGRESSION.get();
    private boolean shareWithTeams = Config.SHARE_SURVIVAL_PROGRESSION_WITH_TEAMS.get();
    private boolean blueprintsEnabled = Config.ENABLE_BLUEPRINTS.get();
    private String draftMaxRadius = Integer.toString(Config.maxActionRadiusBlocks());
    private String draftMaxBlueprintBlocks = Integer.toString(Config.maxBlueprintBlocks());
    private EditBox maxRadiusBox;
    private EditBox maxBlueprintBlocksBox;
    private int generalScroll;
    private int skillScroll;

    public RtsModConfigScreen(Screen parent) {
        super(Component.translatable("config.rtsbuilding.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        if (this.draftCosts.isEmpty()) {
            for (RtsProgressionNode node : this.nodes) {
                this.draftCosts.put(node.id(), RtsProgressionNodes.costTextFor(node));
            }
        }
        rebuildConfigWidgets(false);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderPageBackground(g);
        g.drawCenteredString(this.font, this.title, this.width / 2, 14, 0xFFFFFFFF);

        if (this.page == Page.GENERAL) {
            drawGeneralPage(g);
        } else {
            drawSkillsPage(g);
        }
        drawScrollbar(g);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        if (insideViewport(mouseX, mouseY)) {
            int current = currentScroll();
            int next = Mth.clamp(current - (int) Math.signum(scrollY) * SCROLL_STEP, 0, maxScroll(this.page));
            if (next != current) {
                captureVisibleDrafts();
                setFocused(null);
                setCurrentScroll(next);
                rebuildConfigWidgets(false);
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void rebuildConfigWidgets() {
        rebuildConfigWidgets(true);
    }

    private void rebuildConfigWidgets(boolean captureDrafts) {
        if (captureDrafts) {
            captureVisibleDrafts();
        }
        clearWidgets();
        this.costBoxes.clear();
        this.costBoxNodeIds.clear();
        this.maxRadiusBox = null;
        this.maxBlueprintBlocksBox = null;
        clampScrolls();

        addPageTabs();
        if (this.page == Page.GENERAL) {
            addGeneralWidgets();
        } else {
            addSkillWidgets();
        }
        addFooterButtons();
    }

    private void addPageTabs() {
        int x = contentX();
        int y = tabY();
        int tabW = Math.max(60, (contentWidth() - TAB_GAP) / 2);
        Button general = Button.builder(Component.translatable(Page.GENERAL.titleKey), btn -> switchPage(Page.GENERAL))
                .bounds(x, y, tabW, TAB_H)
                .build();
        general.active = this.page != Page.GENERAL;
        addRenderableWidget(general);

        Button skills = Button.builder(Component.translatable(Page.SKILLS.titleKey), btn -> switchPage(Page.SKILLS))
                .bounds(x + tabW + TAB_GAP, y, tabW, TAB_H)
                .build();
        skills.active = this.page != Page.SKILLS;
        addRenderableWidget(skills);
    }

    private void switchPage(Page target) {
        if (this.page == target) {
            return;
        }
        captureVisibleDrafts();
        this.page = target;
        setFocused(null);
        rebuildConfigWidgets(false);
    }

    private void addGeneralWidgets() {
        int x = contentX();
        int width = contentWidth();
        int controlW = controlWidth(width);
        int controlX = x + width - controlW - 10;
        int y = viewportTop() - this.generalScroll + SECTION_H;

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
            this.maxRadiusBox = new EditBox(this.font, controlX, y + 10, controlW, 18,
                    Component.translatable("config.rtsbuilding.max_radius"));
            this.maxRadiusBox.setMaxLength(4);
            this.maxRadiusBox.setValue(this.draftMaxRadius);
            this.maxRadiusBox.setTextColor(0xFFFFFFFF);
            this.maxRadiusBox.setTextColorUneditable(0xFFB8C7D6);
            addRenderableWidget(this.maxRadiusBox);
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
            this.maxBlueprintBlocksBox = new EditBox(this.font, controlX, y + 10, controlW, 18,
                    Component.translatable("config.rtsbuilding.max_blueprint_blocks"));
            this.maxBlueprintBlocksBox.setMaxLength(6);
            this.maxBlueprintBlocksBox.setValue(this.draftMaxBlueprintBlocks);
            this.maxBlueprintBlocksBox.setTextColor(0xFFFFFFFF);
            this.maxBlueprintBlocksBox.setTextColorUneditable(0xFFB8C7D6);
            addRenderableWidget(this.maxBlueprintBlocksBox);
        }
    }

    private void addSkillWidgets() {
        int x = contentX();
        int width = contentWidth();
        int labelW = costLabelWidth(width);
        int rowsTop = viewportTop() - this.skillScroll + SECTION_H + COST_ROW_H;
        int resetW = 52;

        for (int i = 0; i < this.nodes.size(); i++) {
            int rowY = rowsTop + i * COST_ROW_H;
            if (rowY + COST_ROW_H <= viewportTop()) {
                continue;
            }
            if (rowY + COST_ROW_H > viewportBottom()) {
                break;
            }
            RtsProgressionNode node = this.nodes.get(i);
            int boxX = x + 14 + labelW + 8;
            int boxW = Math.max(72, x + width - 12 - resetW - 6 - boxX);
            EditBox box = new EditBox(this.font, boxX, rowY + 5, boxW, 18, Component.translatable(node.titleKey()));
            box.setMaxLength(512);
            box.setValue(this.draftCosts.getOrDefault(node.id(), RtsProgressionNodes.costTextFor(node)));
            box.setTextColor(0xFFFFFFFF);
            box.setTextColorUneditable(0xFFB8C7D6);
            addRenderableWidget(box);
            this.costBoxes.add(box);
            this.costBoxNodeIds.add(node.id());

            final ResourceLocation nodeId = node.id();
            addRenderableWidget(Button.builder(Component.translatable("config.rtsbuilding.reset"), btn -> {
                RtsProgressionNode resetNode = RtsProgressionNodes.get(nodeId);
                if (resetNode != null) {
                    this.draftCosts.put(resetNode.id(), resetNode.costs().isEmpty() ? "" : RtsProgressionNodes.formatCostText(resetNode.costs()));
                    rebuildConfigWidgets();
                }
            }).bounds(x + width - 64, rowY + 5, resetW, 18).build());
        }
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
        Map<String, String> costOverrides = new LinkedHashMap<>();
        for (RtsProgressionNode node : this.nodes) {
            String costs = this.draftCosts.getOrDefault(node.id(), "");
            if (costs != null && !costs.trim().isBlank()) {
                costOverrides.put(node.id().getPath(), costs.trim());
            }
        }
        try {
            Config.saveProgressionSettings(this.survivalEnabled, this.shareWithTeams, parseMaxRadius(), this.blueprintsEnabled,
                    parseMaxBlueprintBlocks(), costOverrides);
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
        for (int i = 0; i < this.costBoxes.size(); i++) {
            this.draftCosts.put(this.costBoxNodeIds.get(i), this.costBoxes.get(i).getValue());
        }
    }

    private int parseMaxRadius() {
        try {
            return Mth.clamp(Integer.parseInt(this.draftMaxRadius.trim()), 48, 512);
        } catch (NumberFormatException ignored) {
            return Config.maxActionRadiusBlocks();
        }
    }

    private int parseMaxBlueprintBlocks() {
        try {
            return Mth.clamp(Integer.parseInt(this.draftMaxBlueprintBlocks.trim()), 1, 200000);
        } catch (NumberFormatException ignored) {
            return Config.maxBlueprintBlocks();
        }
    }

    private void drawGeneralPage(GuiGraphics g) {
        int x = contentX();
        int y = viewportTop() - this.generalScroll;
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
        g.disableScissor();
    }

    private void drawSkillsPage(GuiGraphics g) {
        int x = contentX();
        int y = viewportTop() - this.skillScroll;
        int width = contentWidth();
        g.enableScissor(x, viewportTop(), x + width, viewportBottom());
        drawSection(g, x, y, Component.translatable("config.rtsbuilding.skill_costs"));
        y += SECTION_H;
        drawCostHeader(g, x, y, width);
        y += COST_ROW_H;
        drawCostRows(g, x, y, width);
        g.disableScissor();
    }

    private int contentHeight(Page target) {
        if (target == Page.GENERAL) {
            return SECTION_H + OPTION_ROW_H * 3 + 6 + SECTION_H + OPTION_ROW_H * 2;
        }
        return SECTION_H + COST_ROW_H + this.nodes.size() * COST_ROW_H;
    }

    private int maxScroll(Page target) {
        return Math.max(0, contentHeight(target) - viewportHeight());
    }

    private int currentScroll() {
        return this.page == Page.GENERAL ? this.generalScroll : this.skillScroll;
    }

    private void setCurrentScroll(int value) {
        if (this.page == Page.GENERAL) {
            this.generalScroll = value;
        } else {
            this.skillScroll = value;
        }
    }

    private void clampScrolls() {
        this.generalScroll = Mth.clamp(this.generalScroll, 0, maxScroll(Page.GENERAL));
        this.skillScroll = Mth.clamp(this.skillScroll, 0, maxScroll(Page.SKILLS));
    }

    private int contentWidth() {
        return Math.max(0, Math.min(CONTENT_MAX_W, this.width - 32));
    }

    private int contentX() {
        return (this.width - contentWidth()) / 2;
    }

    private int tabY() {
        return HEADER_H + 6;
    }

    private int viewportTop() {
        return tabY() + TAB_H + 8;
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

    private int costLabelWidth(int width) {
        return Math.min(210, Math.max(110, width / 3));
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

    private void drawCostHeader(GuiGraphics g, int x, int y, int width) {
        int labelW = costLabelWidth(width);
        g.fill(x, y, x + width, y + COST_ROW_H - 2, 0xFF202A36);
        g.drawString(this.font, Component.translatable("config.rtsbuilding.skill_name"), x + 10, y + 9, 0xFFAFC2D4);
        g.drawString(this.font, Component.translatable("config.rtsbuilding.materials"), x + labelW + 22, y + 9, 0xFFAFC2D4);
    }

    private void drawCostRows(GuiGraphics g, int x, int y, int width) {
        int labelW = costLabelWidth(width);
        for (int i = 0; i < this.nodes.size(); i++) {
            int rowY = y + i * COST_ROW_H;
            if (rowY + COST_ROW_H <= viewportTop()) {
                continue;
            }
            if (rowY >= viewportBottom()) {
                break;
            }
            RtsProgressionNode node = this.nodes.get(i);
            g.fill(x, rowY, x + width, rowY + COST_ROW_H - 2, i % 2 == 0 ? 0xFF17202A : 0xFF1B2530);
            String label = Component.translatable(node.titleKey()).getString();
            g.drawString(this.font, this.font.plainSubstrByWidth(label, labelW), x + 10, rowY + 9, 0xFFD9E6F2);
        }
    }

    private void drawScrollbar(GuiGraphics g) {
        int max = maxScroll(this.page);
        int viewportH = viewportHeight();
        int contentH = contentHeight(this.page);
        if (max <= 0 || viewportH <= 0 || contentH <= 0) {
            return;
        }
        int x = contentX() + contentWidth() - 4;
        int y = viewportTop();
        int thumbH = Math.max(18, viewportH * viewportH / contentH);
        int thumbY = y + (viewportH - thumbH) * currentScroll() / max;
        g.fill(x, y, x + 3, y + viewportH, 0x66263545);
        g.fill(x, thumbY, x + 3, thumbY + thumbH, 0xFFAFC2D4);
    }
}
