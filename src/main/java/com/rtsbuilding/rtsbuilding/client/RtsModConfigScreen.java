package com.rtsbuilding.rtsbuilding.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.RtsCommunityLinks;
import com.rtsbuilding.rtsbuilding.progression.RtsProgressionNode;
import com.rtsbuilding.rtsbuilding.progression.RtsProgressionNodes;

import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public final class RtsModConfigScreen extends Screen {
    private final Screen parent;
    private final List<RtsProgressionNode> nodes = new ArrayList<>(RtsProgressionNodes.all());
    private final List<EditBox> costBoxes = new ArrayList<>();
    private final Map<ResourceLocation, String> draftCosts = new LinkedHashMap<>();
    private boolean survivalEnabled = Config.ENABLE_SURVIVAL_PROGRESSION.get();
    private boolean shareWithTeams = Config.SHARE_SURVIVAL_PROGRESSION_WITH_TEAMS.get();
    private String draftMaxRadius = Integer.toString(Config.maxActionRadiusBlocks());
    private EditBox maxRadiusBox;
    private int scroll;

    public RtsModConfigScreen(Screen parent) {
        super(Component.literal("RTSBuilding Config"));
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

        int panelW = Math.min(520, this.width - 24);
        int x = (this.width - panelW) / 2;
        int y = 18;
        boolean compact = panelW < 430;
        int contentW = panelW - 24;
        int buttonW = compact ? contentW : Math.max(120, (contentW - 10) / 2);

        addRenderableWidget(Button.builder(Component.translatable(this.survivalEnabled
                ? "config.rtsbuilding.survival_on"
                : "config.rtsbuilding.survival_off"), btn -> {
            this.survivalEnabled = !this.survivalEnabled;
            rebuildConfigWidgets();
        }).bounds(x + 12, y + 30, buttonW, 20).build());
        addRenderableWidget(Button.builder(Component.translatable(this.shareWithTeams
                ? "config.rtsbuilding.teams_on"
                : "config.rtsbuilding.teams_off"), btn -> {
            this.shareWithTeams = !this.shareWithTeams;
            rebuildConfigWidgets();
        }).bounds(compact ? x + 12 : x + 22 + buttonW, compact ? y + 54 : y + 30, buttonW, 20).build());

        int radiusY = compact ? y + 82 : y + 56;
        int radiusBoxX = x + Math.min(154, Math.max(96, panelW / 3));
        this.maxRadiusBox = new EditBox(this.font, radiusBoxX, radiusY, 72, 16, Component.translatable("config.rtsbuilding.max_radius"));
        this.maxRadiusBox.setMaxLength(4);
        this.maxRadiusBox.setValue(this.draftMaxRadius);
        this.maxRadiusBox.setTextColor(0xFFFFFFFF);
        this.maxRadiusBox.setTextColorUneditable(0xFFB8C7D6);
        addRenderableWidget(this.maxRadiusBox);

        addRenderableWidget(Button.builder(Component.translatable("config.rtsbuilding.open_discord"), btn -> {
            Util.getPlatform().openUri(RtsCommunityLinks.DISCORD_INVITE);
        }).bounds(x + 12, compact ? y + 158 : y + 132, buttonW, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("config.rtsbuilding.copy_qq"), btn -> {
            if (this.minecraft != null) {
                this.minecraft.keyboardHandler.setClipboard(RtsCommunityLinks.QQ_GROUP);
            }
            btn.setMessage(Component.translatable("config.rtsbuilding.copied_qq"));
        }).bounds(compact ? x + 12 : x + 22 + buttonW, compact ? y + 182 : y + 132, buttonW, 20).build());

        int listY = configListY(y, compact);
        int rows = visibleRows();
        this.scroll = Mth.clamp(this.scroll, 0, maxScroll());
        for (int row = 0; row < rows; row++) {
            int nodeIndex = row + this.scroll;
            if (nodeIndex >= this.nodes.size()) {
                break;
            }
            RtsProgressionNode node = this.nodes.get(nodeIndex);
            int labelW = Math.min(130, Math.max(84, panelW / 3));
            int resetW = 50;
            int boxX = x + 14 + labelW + 8;
            int boxW = Math.max(58, x + panelW - 12 - resetW - 6 - boxX);
            EditBox box = new EditBox(this.font, boxX, listY + row * 24 + 3, boxW, 16, Component.literal(node.id().getPath()));
            box.setMaxLength(512);
            box.setValue(this.draftCosts.getOrDefault(node.id(), RtsProgressionNodes.costTextFor(node)));
            box.setTextColor(0xFFFFFFFF);
            box.setTextColorUneditable(0xFFB8C7D6);
            addRenderableWidget(box);
            this.costBoxes.add(box);

            final int capturedIndex = nodeIndex;
            addRenderableWidget(Button.builder(Component.literal("Reset"), btn -> {
                RtsProgressionNode resetNode = this.nodes.get(capturedIndex);
                this.draftCosts.put(resetNode.id(), resetNode.costs().isEmpty() ? "" : RtsProgressionNodes.formatCostText(resetNode.costs()));
                rebuildConfigWidgets();
            }).bounds(x + panelW - 62, listY + row * 24 + 2, 50, 18).build());
        }

        addRenderableWidget(Button.builder(Component.literal("Save"), btn -> saveAndClose())
                .bounds(x + panelW - 118, this.height - 28, 54, 20)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Back"), btn -> this.minecraft.setScreen(this.parent))
                .bounds(x + panelW - 58, this.height - 28, 46, 20)
                .build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);
        int panelW = Math.min(520, this.width - 24);
        int x = (this.width - panelW) / 2;
        int y = 18;
        boolean compact = panelW < 430;
        int radiusY = compact ? y + 86 : y + 60;
        g.fill(x, y, x + panelW, this.height - 34, 0xEE101820);
        g.hLine(x, x + panelW, y, 0xFF6E8799);
        g.hLine(x, x + panelW, this.height - 34, 0xFF0D1218);
        g.drawString(this.font, "RTSBuilding Mod Config", x + 10, y + 9, 0xFFFFFF);
        g.drawString(this.font, Component.translatable("config.rtsbuilding.max_radius"), x + 10, radiusY, 0xF4F7FF);
        int hintX = x + Math.min(232, Math.max(180, panelW / 2));
        g.drawString(this.font, trim(Component.translatable("config.rtsbuilding.max_radius.hint").getString(), x + panelW - 12 - hintX), hintX, radiusY, 0xAFC2D4);
        g.drawString(this.font, trim(Component.translatable("config.rtsbuilding.server_authority").getString(), panelW - 20), x + 10, compact ? y + 110 : y + 80, 0xAFC2D4);
        g.drawString(this.font, Component.translatable("config.rtsbuilding.community"), x + 10, compact ? y + 126 : y + 96, 0xF4F7FF);
        g.drawString(this.font, trim("Discord: " + RtsCommunityLinks.DISCORD_INVITE, panelW - 20), x + 10, compact ? y + 140 : y + 112, 0xAFC2D4);
        g.drawString(this.font, trim("QQ: " + RtsCommunityLinks.QQ_GROUP, panelW - 20), x + 10, compact ? y + 152 : y + 124, 0xAFC2D4);
        g.drawString(this.font, Component.translatable("config.rtsbuilding.skill_costs"), x + 10, configListY(y, compact) - 16, 0xF4F7FF);

        int listY = configListY(y, compact);
        int rows = visibleRows();
        int labelW = Math.min(130, Math.max(84, panelW / 3));
        for (int row = 0; row < rows; row++) {
            int nodeIndex = row + this.scroll;
            if (nodeIndex >= this.nodes.size()) {
                break;
            }
            RtsProgressionNode node = this.nodes.get(nodeIndex);
            int rowY = listY + row * 24;
            g.fill(x + 8, rowY, x + panelW - 8, rowY + 22, row % 2 == 0 ? 0x5519222D : 0x55202A36);
            g.drawString(this.font, trim(node.id().getPath().replace('_', ' '), labelW), x + 14, rowY + 7, 0xD9E6F2);
        }

        if (maxScroll() > 0) {
            g.drawString(this.font, (this.scroll + 1) + "/" + (maxScroll() + 1), x + panelW - 48, configListY(y, compact) - 16, 0xAFC2D4);
        }
        for (var renderable : this.renderables) {
            renderable.render(g, mouseX, mouseY, partialTick);
        }
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

    private int visibleRows() {
        int panelW = Math.min(520, this.width - 24);
        boolean compact = panelW < 430;
        return Math.max(1, (this.height - (compact ? 246 : 222)) / 24);
    }

    private int maxScroll() {
        return Math.max(0, this.nodes.size() - visibleRows());
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
            Config.saveProgressionSettings(this.survivalEnabled, this.shareWithTeams, parseMaxRadius(), costOverrides);
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
        for (int i = 0; i < this.costBoxes.size(); i++) {
            int nodeIndex = this.scroll + i;
            if (nodeIndex < this.nodes.size()) {
                this.draftCosts.put(this.nodes.get(nodeIndex).id(), this.costBoxes.get(i).getValue());
            }
        }
    }

    private String trim(String text, int width) {
        return this.font.plainSubstrByWidth(text, Math.max(8, width));
    }

    private int configListY(int y, boolean compact) {
        return compact ? y + 222 : y + 176;
    }

    private int parseMaxRadius() {
        if (this.maxRadiusBox == null) {
            return Config.maxActionRadiusBlocks();
        }
        try {
            return Mth.clamp(Integer.parseInt(this.draftMaxRadius.trim()), 48, 512);
        } catch (NumberFormatException ignored) {
            return Config.maxActionRadiusBlocks();
        }
    }
}
