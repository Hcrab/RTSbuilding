package com.rtsbuilding.rtsbuilding.client;

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
    private static final int CONTENT_MAX_W = 620;
    private static final int HEADER_H = 32;
    private static final int FOOTER_H = 36;
    private static final int OPTION_ROW_H = 34;
    private static final int COST_ROW_H = 24;
    private static final int SECTION_H = 16;

    private final Screen parent;
    private final List<RtsProgressionNode> nodes = new ArrayList<>(RtsProgressionNodes.all());
    private final List<EditBox> costBoxes = new ArrayList<>();
    private final Map<ResourceLocation, String> draftCosts = new LinkedHashMap<>();
    private boolean survivalEnabled = Config.ENABLE_SURVIVAL_PROGRESSION.get();
    private boolean shareWithTeams = Config.SHARE_SURVIVAL_PROGRESSION_WITH_TEAMS.get();
    private boolean blueprintsEnabled = Config.ENABLE_BLUEPRINTS.get();
    private String draftMaxRadius = Integer.toString(Config.maxActionRadiusBlocks());
    private String draftMaxBlueprintBlocks = Integer.toString(Config.maxBlueprintBlocks());
    private EditBox maxRadiusBox;
    private EditBox maxBlueprintBlocksBox;
    private int scroll;

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
        g.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFFFFFFF);

        int x = contentX();
        int y = contentTop();
        int width = contentWidth();
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

        int listY = costListTop();
        drawSection(g, x, listY - SECTION_H, Component.translatable("config.rtsbuilding.skill_costs"));
        drawCostRows(g, x, listY, width);
        if (maxScroll() > 0) {
            String page = (this.scroll + 1) + "/" + (maxScroll() + 1);
            g.drawString(this.font, page, x + width - this.font.width(page) - 10, listY - SECTION_H + 5, 0xFFAFC2D4);
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        int next = this.scroll - (int) Math.signum(scrollY);
        next = Mth.clamp(next, 0, maxScroll());
        if (next != this.scroll) {
            captureVisibleDrafts();
            setFocused(null);
            this.scroll = next;
            rebuildConfigWidgets(false);
            return true;
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
        this.maxRadiusBox = null;
        this.maxBlueprintBlocksBox = null;

        int x = contentX();
        int y = contentTop() + SECTION_H;
        int width = contentWidth();
        int buttonX = x + width - 132;
        int fieldX = x + width - 126;

        addRenderableWidget(Button.builder(Component.translatable(this.survivalEnabled
                ? "config.rtsbuilding.enabled"
                : "config.rtsbuilding.disabled"), btn -> {
            this.survivalEnabled = !this.survivalEnabled;
            rebuildConfigWidgets();
        }).bounds(buttonX, y + 7, 122, 20).build());
        y += OPTION_ROW_H;

        addRenderableWidget(Button.builder(Component.translatable(this.shareWithTeams
                ? "config.rtsbuilding.enabled"
                : "config.rtsbuilding.disabled"), btn -> {
            this.shareWithTeams = !this.shareWithTeams;
            rebuildConfigWidgets();
        }).bounds(buttonX, y + 7, 122, 20).build());
        y += OPTION_ROW_H;

        this.maxRadiusBox = new EditBox(this.font, fieldX, y + 8, 116, 18, Component.translatable("config.rtsbuilding.max_radius"));
        this.maxRadiusBox.setMaxLength(4);
        this.maxRadiusBox.setValue(this.draftMaxRadius);
        this.maxRadiusBox.setTextColor(0xFFFFFFFF);
        this.maxRadiusBox.setTextColorUneditable(0xFFB8C7D6);
        addRenderableWidget(this.maxRadiusBox);
        y += OPTION_ROW_H + 6 + SECTION_H;

        addRenderableWidget(Button.builder(Component.translatable(this.blueprintsEnabled
                ? "config.rtsbuilding.enabled"
                : "config.rtsbuilding.disabled"), btn -> {
            this.blueprintsEnabled = !this.blueprintsEnabled;
            rebuildConfigWidgets();
        }).bounds(buttonX, y + 7, 122, 20).build());
        y += OPTION_ROW_H;

        this.maxBlueprintBlocksBox = new EditBox(this.font, fieldX, y + 8, 116, 18,
                Component.translatable("config.rtsbuilding.max_blueprint_blocks"));
        this.maxBlueprintBlocksBox.setMaxLength(6);
        this.maxBlueprintBlocksBox.setValue(this.draftMaxBlueprintBlocks);
        this.maxBlueprintBlocksBox.setTextColor(0xFFFFFFFF);
        this.maxBlueprintBlocksBox.setTextColorUneditable(0xFFB8C7D6);
        addRenderableWidget(this.maxBlueprintBlocksBox);

        int rows = visibleCostRows();
        this.scroll = Mth.clamp(this.scroll, 0, maxScroll());
        int listY = costListTop();
        int labelW = Math.min(160, Math.max(110, width / 3));
        for (int row = 0; row < rows; row++) {
            int nodeIndex = row + this.scroll;
            if (nodeIndex >= this.nodes.size()) {
                break;
            }
            RtsProgressionNode node = this.nodes.get(nodeIndex);
            int rowY = listY + row * COST_ROW_H;
            int resetW = 52;
            int boxX = x + 14 + labelW + 8;
            int boxW = Math.max(72, x + width - 12 - resetW - 6 - boxX);
            EditBox box = new EditBox(this.font, boxX, rowY + 3, boxW, 16, Component.literal(node.id().getPath()));
            box.setMaxLength(512);
            box.setValue(this.draftCosts.getOrDefault(node.id(), RtsProgressionNodes.costTextFor(node)));
            box.setTextColor(0xFFFFFFFF);
            box.setTextColorUneditable(0xFFB8C7D6);
            addRenderableWidget(box);
            this.costBoxes.add(box);

            final int capturedIndex = nodeIndex;
            addRenderableWidget(Button.builder(Component.translatable("config.rtsbuilding.reset"), btn -> {
                RtsProgressionNode resetNode = this.nodes.get(capturedIndex);
                this.draftCosts.put(resetNode.id(), resetNode.costs().isEmpty() ? "" : RtsProgressionNodes.formatCostText(resetNode.costs()));
                rebuildConfigWidgets();
            }).bounds(x + width - 64, rowY + 2, resetW, 18).build());
        }

        int footerY = this.height - 28;
        addRenderableWidget(Button.builder(Component.translatable("config.rtsbuilding.save"), btn -> saveAndClose())
                .bounds(this.width / 2 - 84, footerY, 80, 20)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("gui.rtsbuilding.back"), btn -> this.minecraft.setScreen(this.parent))
                .bounds(this.width / 2 + 4, footerY, 80, 20)
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
            int nodeIndex = this.scroll + i;
            if (nodeIndex < this.nodes.size()) {
                this.draftCosts.put(this.nodes.get(nodeIndex).id(), this.costBoxes.get(i).getValue());
            }
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

    private int visibleCostRows() {
        return Math.max(0, (this.height - costListTop() - FOOTER_H - 8) / COST_ROW_H);
    }

    private int maxScroll() {
        return Math.max(0, this.nodes.size() - visibleCostRows());
    }

    private int contentWidth() {
        return Math.min(CONTENT_MAX_W, this.width - 32);
    }

    private int contentX() {
        return (this.width - contentWidth()) / 2;
    }

    private int contentTop() {
        return HEADER_H + 10;
    }

    private int costListTop() {
        return contentTop() + SECTION_H + OPTION_ROW_H * 3 + 6 + SECTION_H + OPTION_ROW_H * 2 + 22;
    }

    private void renderPageBackground(GuiGraphics g) {
        g.fill(0, 0, this.width, this.height, 0xFF101820);
        g.fill(0, 0, this.width, HEADER_H, 0xFF151B23);
        g.fill(0, this.height - FOOTER_H, this.width, this.height, 0xFF151B23);
        g.hLine(0, this.width, HEADER_H, 0xFF273747);
        g.hLine(0, this.width, this.height - FOOTER_H, 0xFF273747);
    }

    private void drawSection(GuiGraphics g, int x, int y, Component label) {
        g.drawString(this.font, label, x + 2, y + 4, 0xFFF4F7FF);
    }

    private void drawOptionRow(GuiGraphics g, int x, int y, int width, Component label, Component hint) {
        g.fill(x, y, x + width, y + OPTION_ROW_H - 2, 0xFF17202A);
        g.hLine(x, x + width, y, 0xFF263545);
        g.drawString(this.font, label, x + 10, y + 6, 0xFFEAF2FF);
        String hintText = this.font.plainSubstrByWidth(hint.getString(), Math.max(24, width - 160));
        g.drawString(this.font, Component.literal(hintText), x + 10, y + 18, 0xFFAFC2D4);
    }

    private void drawCostRows(GuiGraphics g, int x, int y, int width) {
        int rows = visibleCostRows();
        int labelW = Math.min(160, Math.max(110, width / 3));
        for (int row = 0; row < rows; row++) {
            int nodeIndex = row + this.scroll;
            if (nodeIndex >= this.nodes.size()) {
                break;
            }
            int rowY = y + row * COST_ROW_H;
            RtsProgressionNode node = this.nodes.get(nodeIndex);
            g.fill(x, rowY, x + width, rowY + COST_ROW_H - 2, row % 2 == 0 ? 0xFF17202A : 0xFF1B2530);
            String label = node.id().getPath().replace('_', ' ');
            g.drawString(this.font, this.font.plainSubstrByWidth(label, labelW), x + 10, rowY + 7, 0xFFD9E6F2);
        }
    }
}
