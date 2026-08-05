package com.rtsbuilding.rtsbuilding.client.screen.standalone;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.server.service.mining.RangeMiningHarvestTier;
import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uikit.theme.StandaloneScreenStyle;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.resources.I18n;
import com.rtsbuilding.rtsbuilding.platform.math.MathHelper;
import net.minecraft.util.ChatComponentText;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Forge 1.12.2 配置页。所有修改先留在草稿中，只有“保存”才写配置。 */
public final class RtsModConfigScreen extends GuiScreen {
    private static final int CONTENT_MAX_W = 720, HEADER_H = 40, FOOTER_H = 40;
    private static final int OPTION_ROW_H = 38, SECTION_H = 18, SCROLL_STEP = 24;
    private static final int SAVE = 1, BACK = 2, SURVIVAL = 10, TEAMS = 11, BLUEPRINTS = 12;
    private static final int INVENTORY_BUTTON = 13, HARVEST_TIER = 14, DEVELOPER = 15;

    private final GuiScreen parent;
    private boolean survivalEnabled = Config.ENABLE_SURVIVAL_PROGRESSION.getAsBoolean();
    private boolean shareWithTeams = Config.SHARE_SURVIVAL_PROGRESSION_WITH_TEAMS.getAsBoolean();
    private boolean blueprintsEnabled = Config.ENABLE_BLUEPRINTS.getAsBoolean();
    private boolean developerMode = Config.isDeveloperModeEnabled();
    private boolean inventoryRtsButtonEnabled = Config.isInventoryRtsButtonEnabled();
    private String draftMaxRadius = Integer.toString(Config.maxActionRadiusBlocks());
    private String draftMaxBlueprintBlocks = Integer.toString(Config.maxBlueprintBlocks());
    private String draftAreaMineMaxWidth = Integer.toString(Config.areaMineMaxWidth());
    private String draftAreaMineMaxHeight = Integer.toString(Config.areaMineMaxHeight());
    private String draftAreaMineMaxDepth = Integer.toString(Config.areaMineMaxDepth());
    private String draftAreaMineMaxVolume = Integer.toString(Config.areaMineMaxVolume());
    private String draftAreaDestroyMaxTargets = Integer.toString(Config.areaDestroyMaxTargets());
    private RangeMiningHarvestTier areaMineMaxHarvestTier = Config.areaMineMaxHarvestTier();
    private final List<GuiTextField> visibleFields = new ArrayList<GuiTextField>();
    private GuiTextField maxRadiusBox, maxBlueprintBlocksBox, areaMineMaxWidthBox, areaMineMaxHeightBox;
    private GuiTextField areaMineMaxDepthBox, areaMineMaxVolumeBox, areaDestroyMaxTargetsBox;
    private int scroll;

    public RtsModConfigScreen(GuiScreen parent) { this.parent = parent; }

    @Override public void initGui() { rebuildConfigWidgets(false); }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        renderPageBackground();
        drawCenteredString(this.fontRendererObj, I18n.format("config.rtsbuilding.title"), this.width / 2, 14,
                StandaloneScreenStyle.TITLE_TEXT.toArgb());
        drawGeneralPage();
        for (GuiTextField field : this.visibleFields) field.drawTextBox();
        drawScrollbar();
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override protected void actionPerformed(GuiButton button) {
        switch (button.id) {
            case SAVE: saveAndClose(); return;
            case BACK: this.mc.displayGuiScreen(this.parent); return;
            case SURVIVAL: this.survivalEnabled = !this.survivalEnabled; break;
            case TEAMS: this.shareWithTeams = !this.shareWithTeams; break;
            case BLUEPRINTS: this.blueprintsEnabled = !this.blueprintsEnabled; break;
            case INVENTORY_BUTTON: this.inventoryRtsButtonEnabled = !this.inventoryRtsButtonEnabled; break;
            case HARVEST_TIER: this.areaMineMaxHarvestTier = this.areaMineMaxHarvestTier.next(); break;
            case DEVELOPER: this.developerMode = !this.developerMode; break;
            default: return;
        }
        rebuildConfigWidgets(true);
    }

    @Override public void handleMouseInput() {
        super.handleMouseInput();
        int delta = Mouse.getEventDWheel();
        if (delta != 0) {
            ScaledResolution scaled = new ScaledResolution(this.mc, this.mc.displayWidth, this.mc.displayHeight);
            double x = Mouse.getEventX() * scaled.getScaledWidth() / (double) this.mc.displayWidth;
            double y = scaled.getScaledHeight() - Mouse.getEventY() * scaled.getScaledHeight() / (double) this.mc.displayHeight - 1;
            mouseScrolled(x, y, delta > 0 ? 1.0D : -1.0D);
        }
    }

    boolean mouseScrolled(double mouseX, double mouseY, double wheel) {
        if (!insideViewport(mouseX, mouseY)) return false;
        int next = MathHelper.clamp(this.scroll - (int) Math.signum(wheel) * SCROLL_STEP, 0, maxScroll());
        if (next == this.scroll) return true;
        captureVisibleDrafts();
        this.scroll = next;
        rebuildConfigWidgets(false);
        return true;
    }

    @Override protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        for (GuiTextField field : this.visibleFields) field.mouseClicked(mouseX, mouseY, mouseButton);
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override protected void keyTyped(char typedChar, int keyCode) {
        for (GuiTextField field : this.visibleFields) {
            if (field.isFocused()) {
                if (keyCode == Keyboard.KEY_ESCAPE) { field.setFocused(false); return; }
                field.textboxKeyTyped(typedChar, keyCode);
                return;
            }
        }
        if (keyCode == Keyboard.KEY_ESCAPE) this.mc.displayGuiScreen(this.parent);
        else super.keyTyped(typedChar, keyCode);
    }

    @Override public boolean doesGuiPauseGame() { return false; }

    private void rebuildConfigWidgets(boolean captureDrafts) {
        if (captureDrafts) captureVisibleDrafts();
        this.buttonList.clear();
        this.visibleFields.clear();
        this.maxRadiusBox = this.maxBlueprintBlocksBox = null;
        this.areaMineMaxWidthBox = this.areaMineMaxHeightBox = this.areaMineMaxDepthBox = null;
        this.areaMineMaxVolumeBox = this.areaDestroyMaxTargetsBox = null;
        this.scroll = MathHelper.clamp(this.scroll, 0, maxScroll());
        addGeneralWidgets();
        int buttonW = Math.min(96, Math.max(72, this.width / 4));
        int startX = (this.width - buttonW * 2 - 8) / 2;
        this.buttonList.add(new GuiButton(SAVE, startX, this.height - 28, buttonW, 20,
                I18n.format("config.rtsbuilding.save")));
        this.buttonList.add(new GuiButton(BACK, startX + buttonW + 8, this.height - 28, buttonW, 20,
                I18n.format("gui.rtsbuilding.back")));
    }

    private void addGeneralWidgets() {
        int width = contentWidth(), controlW = controlWidth(width);
        int controlX = contentX() + width - controlW - 10;
        int y = viewportTop() - this.scroll + SECTION_H;
        addToggleIfVisible(SURVIVAL, controlX, y, controlW, this.survivalEnabled); y += OPTION_ROW_H;
        addToggleIfVisible(TEAMS, controlX, y, controlW, this.shareWithTeams); y += OPTION_ROW_H;
        if (fullyVisible(y, OPTION_ROW_H)) this.maxRadiusBox = addIntegerBox(controlX, y, controlW, draftMaxRadius, 4);
        y += OPTION_ROW_H + 6 + SECTION_H;
        addToggleIfVisible(BLUEPRINTS, controlX, y, controlW, this.blueprintsEnabled); y += OPTION_ROW_H;
        if (fullyVisible(y, OPTION_ROW_H)) this.maxBlueprintBlocksBox = addIntegerBox(controlX, y, controlW, draftMaxBlueprintBlocks, 6);
        y += OPTION_ROW_H + 6 + SECTION_H;
        addToggleIfVisible(INVENTORY_BUTTON, controlX, y, controlW, this.inventoryRtsButtonEnabled);
        y += OPTION_ROW_H + 6 + SECTION_H;
        if (fullyVisible(y, OPTION_ROW_H)) this.areaMineMaxWidthBox = addIntegerBox(controlX, y, controlW, draftAreaMineMaxWidth, 3); y += OPTION_ROW_H;
        if (fullyVisible(y, OPTION_ROW_H)) this.areaMineMaxHeightBox = addIntegerBox(controlX, y, controlW, draftAreaMineMaxHeight, 3); y += OPTION_ROW_H;
        if (fullyVisible(y, OPTION_ROW_H)) this.areaMineMaxDepthBox = addIntegerBox(controlX, y, controlW, draftAreaMineMaxDepth, 3); y += OPTION_ROW_H;
        if (fullyVisible(y, OPTION_ROW_H)) this.areaMineMaxVolumeBox = addIntegerBox(controlX, y, controlW, draftAreaMineMaxVolume, 6); y += OPTION_ROW_H;
        if (fullyVisible(y, OPTION_ROW_H)) this.areaDestroyMaxTargetsBox = addIntegerBox(controlX, y, controlW, draftAreaDestroyMaxTargets, 6); y += OPTION_ROW_H;
        if (fullyVisible(y, OPTION_ROW_H)) this.buttonList.add(new GuiButton(HARVEST_TIER, controlX, y + 9, controlW, 20,
                I18n.format("config.rtsbuilding.harvest_tier." + this.areaMineMaxHarvestTier.name().toLowerCase())));
        y += OPTION_ROW_H + 6 + SECTION_H;
        addToggleIfVisible(DEVELOPER, controlX, y, controlW, this.developerMode);
    }

    private void addToggleIfVisible(int id, int x, int y, int width, boolean enabled) {
        if (fullyVisible(y, OPTION_ROW_H)) this.buttonList.add(new GuiButton(id, x, y + 9, width, 20,
                I18n.format(enabled ? "config.rtsbuilding.enabled" : "config.rtsbuilding.disabled")));
    }

    private GuiTextField addIntegerBox(int x, int y, int width, String value, int maxLength) {
        GuiTextField box = new NoShadowTextField(100 + this.visibleFields.size(), this.fontRendererObj,
                x, y + 10, width, 18);
        box.setMaxStringLength(maxLength);
        box.setText(value);
        box.setTextColor(StandaloneScreenStyle.TITLE_TEXT.toArgb());
        box.setDisabledTextColour(StandaloneScreenStyle.INFO_EMPTY.toArgb());
        this.visibleFields.add(box);
        return box;
    }

    private void saveAndClose() {
        captureVisibleDrafts();
        try {
            Config.saveGeneralSettings(survivalEnabled, shareWithTeams, parseMaxRadius(), blueprintsEnabled, parseMaxBlueprintBlocks());
            Config.saveAreaMineLimitSettings(parseAreaMineMaxWidth(), parseAreaMineMaxHeight(), parseAreaMineMaxDepth(),
                    parseAreaMineMaxVolume(), parseAreaDestroyMaxTargets(), areaMineMaxHarvestTier);
            Config.setInventoryRtsButtonEnabled(inventoryRtsButtonEnabled);
            Config.setDeveloperModeEnabled(developerMode);
        } catch (RuntimeException ex) {
            if (this.mc != null && this.mc.thePlayer != null)
                this.mc.thePlayer.addChatMessage(new ChatComponentText("RTSBuilding config save failed: " + ex.getClass().getSimpleName()));
            return;
        }
        ClientRtsController.get().setSurvivalProgressionEnabled(survivalEnabled);
        this.mc.displayGuiScreen(this.parent);
    }

    private void captureVisibleDrafts() {
        if (maxRadiusBox != null) draftMaxRadius = maxRadiusBox.getText();
        if (maxBlueprintBlocksBox != null) draftMaxBlueprintBlocks = maxBlueprintBlocksBox.getText();
        if (areaMineMaxWidthBox != null) draftAreaMineMaxWidth = areaMineMaxWidthBox.getText();
        if (areaMineMaxHeightBox != null) draftAreaMineMaxHeight = areaMineMaxHeightBox.getText();
        if (areaMineMaxDepthBox != null) draftAreaMineMaxDepth = areaMineMaxDepthBox.getText();
        if (areaMineMaxVolumeBox != null) draftAreaMineMaxVolume = areaMineMaxVolumeBox.getText();
        if (areaDestroyMaxTargetsBox != null) draftAreaDestroyMaxTargets = areaDestroyMaxTargetsBox.getText();
    }

    private int parseMaxRadius() { return parseClampedInt(draftMaxRadius, 48, 512, Config.maxActionRadiusBlocks()); }
    private int parseMaxBlueprintBlocks() { return parseClampedInt(draftMaxBlueprintBlocks, 1, 200000, Config.maxBlueprintBlocks()); }
    private int parseAreaMineMaxWidth() { return parseClampedInt(draftAreaMineMaxWidth, 1, 256, Config.areaMineMaxWidth()); }
    private int parseAreaMineMaxHeight() { return parseClampedInt(draftAreaMineMaxHeight, 1, 256, Config.areaMineMaxHeight()); }
    private int parseAreaMineMaxDepth() { return parseClampedInt(draftAreaMineMaxDepth, 1, 256, Config.areaMineMaxDepth()); }
    private int parseAreaMineMaxVolume() { return parseClampedInt(draftAreaMineMaxVolume, 1, 262144, Config.areaMineMaxVolume()); }
    private int parseAreaDestroyMaxTargets() { return parseClampedInt(draftAreaDestroyMaxTargets, 1, 262144, Config.areaDestroyMaxTargets()); }
    private int parseClampedInt(String raw, int min, int max, int fallback) {
        try { return MathHelper.clamp(Integer.parseInt(raw.trim()), min, max); }
        catch (NumberFormatException ignored) { return fallback; }
    }

    private void drawGeneralPage() {
        int x = contentX(), y = viewportTop() - scroll, width = contentWidth();
        enableViewportScissor();
        drawSection(x, y, "config.rtsbuilding.section.gameplay"); y += SECTION_H;
        drawOptionRow(x, y, width, "config.rtsbuilding.option.survival", "config.rtsbuilding.option.survival.hint"); y += OPTION_ROW_H;
        drawOptionRow(x, y, width, "config.rtsbuilding.option.teams", "config.rtsbuilding.option.teams.hint"); y += OPTION_ROW_H;
        drawOptionRow(x, y, width, "config.rtsbuilding.max_radius", "config.rtsbuilding.max_radius.hint"); y += OPTION_ROW_H + 6;
        drawSection(x, y, "config.rtsbuilding.section.blueprints"); y += SECTION_H;
        drawOptionRow(x, y, width, "config.rtsbuilding.option.blueprints", "config.rtsbuilding.option.blueprints.hint"); y += OPTION_ROW_H;
        drawOptionRow(x, y, width, "config.rtsbuilding.max_blueprint_blocks", "config.rtsbuilding.max_blueprint_blocks.hint"); y += OPTION_ROW_H + 6;
        drawSection(x, y, "config.rtsbuilding.section.compat"); y += SECTION_H;
        drawOptionRow(x, y, width, "rtsbuilding.configuration.showInventoryRtsButton", "rtsbuilding.configuration.showInventoryRtsButton.tooltip"); y += OPTION_ROW_H + 6;
        drawSection(x, y, "config.rtsbuilding.section.area_mining"); y += SECTION_H;
        String[] mining = {"area_mine_max_width", "area_mine_max_height", "area_mine_max_depth", "area_mine_max_volume", "area_destroy_max_targets", "area_mine_max_harvest_tier"};
        for (String key : mining) { drawOptionRow(x, y, width, "config.rtsbuilding." + key, "config.rtsbuilding." + key + ".hint"); y += OPTION_ROW_H; }
        y += 6; drawSection(x, y, "config.rtsbuilding.section.developer"); y += SECTION_H;
        drawOptionRow(x, y, width, "config.rtsbuilding.option.developer_mode", "config.rtsbuilding.option.developer_mode.hint");
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    private void enableViewportScissor() {
        ScaledResolution sr = new ScaledResolution(this.mc, this.mc.displayWidth, this.mc.displayHeight); int factor = sr.getScaleFactor();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(contentX() * factor, this.mc.displayHeight - viewportBottom() * factor,
                contentWidth() * factor, viewportHeight() * factor);
    }

    private void renderPageBackground() {
        drawRect(0, 0, width, height, StandaloneScreenStyle.PAGE_BACKGROUND.toArgb());
        drawRect(0, 0, width, HEADER_H, StandaloneScreenStyle.BAR_BACKGROUND.toArgb());
        drawRect(0, height - FOOTER_H, width, height, StandaloneScreenStyle.BAR_BACKGROUND.toArgb());
        drawRect(0, HEADER_H, width, HEADER_H + 1, StandaloneScreenStyle.BAR_DIVIDER.toArgb());
        drawRect(0, height - FOOTER_H, width, height - FOOTER_H + 1, StandaloneScreenStyle.BAR_DIVIDER.toArgb());
    }

    private void drawSection(int x, int y, String key) {
        this.fontRendererObj.drawString(I18n.format(key), x + 2, y + 5, StandaloneScreenStyle.SECTION_TEXT.toArgb(), false);
        drawRect(x, y + SECTION_H - 1, x + contentWidth(), y + SECTION_H, StandaloneScreenStyle.INFO_ROW_DIVIDER.toArgb());
    }

    private void drawOptionRow(int x, int y, int width, String labelKey, String hintKey) {
        int hintW = Math.max(24, width - controlWidth(width) - 34);
        drawRect(x, y, x + width, y + OPTION_ROW_H - 2, StandaloneScreenStyle.INFO_ROW_BACKGROUND.toArgb());
        drawRect(x, y, x + width, y + 1, StandaloneScreenStyle.INFO_ROW_DIVIDER.toArgb());
        this.fontRendererObj.drawString(I18n.format(labelKey), x + 10, y + 7, StandaloneScreenStyle.INFO_VALUE.toArgb(), false);
        String hint = this.fontRendererObj.trimStringToWidth(I18n.format(hintKey), hintW);
        this.fontRendererObj.drawString(hint, x + 10, y + 20, StandaloneScreenStyle.INFO_LABEL.toArgb(), false);
    }

    private void drawScrollbar() {
        int max = maxScroll(), viewportH = viewportHeight(), contentH = contentHeight();
        if (max <= 0 || viewportH <= 0 || contentH <= 0) return;
        int x = contentX() + contentWidth() - 4, y = viewportTop();
        int thumbH = Math.max(18, viewportH * viewportH / contentH);
        int thumbY = y + (viewportH - thumbH) * scroll / max;
        drawRect(x, y, x + 3, y + viewportH, StandaloneScreenStyle.SCROLLBAR_TRACK.toArgb());
        drawRect(x, thumbY, x + 3, thumbY + thumbH, StandaloneScreenStyle.INFO_LABEL.toArgb());
    }

    private int contentHeight() { return SECTION_H * 5 + OPTION_ROW_H * 13 + 24; }
    private int maxScroll() { return Math.max(0, contentHeight() - viewportHeight()); }
    private int contentWidth() { return Math.max(0, Math.min(CONTENT_MAX_W, width - 32)); }
    private int contentX() { return (width - contentWidth()) / 2; }
    private int viewportTop() { return HEADER_H + 10; }
    private int viewportBottom() { return Math.max(viewportTop(), height - FOOTER_H - 8); }
    private int viewportHeight() { return Math.max(0, viewportBottom() - viewportTop()); }
    private int controlWidth(int width) { return Math.min(150, Math.max(92, width / 3)); }
    private boolean fullyVisible(int y, int h) { return y >= viewportTop() && y + h <= viewportBottom(); }
    private boolean insideViewport(double x, double y) { return UiRect.contains(contentX(), viewportTop(), contentWidth(), viewportHeight(), x, y); }

    /** 1.12 原版文本框固定画阴影；数字配置框改为无阴影绘制，输入状态仍由 GuiTextField 管理。 */
    private static final class NoShadowTextField extends GuiTextField {
        private final net.minecraft.client.gui.FontRenderer font;
        private final int drawX, drawY, drawW, drawH;
        private NoShadowTextField(int id, net.minecraft.client.gui.FontRenderer font, int x, int y, int w, int h) {
            super(font, x, y, w, h); this.font = font; this.drawX = x; this.drawY = y; this.drawW = w; this.drawH = h;
        }
        @Override public void drawTextBox() {
            if (!getVisible()) return;
            Gui.drawRect(drawX, drawY, drawX + drawW, drawY + drawH,
                    StandaloneScreenStyle.INPUT_BORDER_LIGHT.toArgb());
            Gui.drawRect(drawX + 1, drawY + 1, drawX + drawW - 1, drawY + drawH - 1,
                    StandaloneScreenStyle.INPUT_BACKGROUND.toArgb());
            String visible = font.trimStringToWidth(getText(), Math.max(1, drawW - 6), true);
            int textX = drawX + 3, textY = drawY + (drawH - 8) / 2;
            font.drawString(visible, textX, textY, StandaloneScreenStyle.INPUT_TEXT.toArgb(), false);
            if (isFocused()) Gui.drawRect(textX + font.getStringWidth(visible) + 1, textY - 1,
                    textX + font.getStringWidth(visible) + 2, textY + 9,
                    StandaloneScreenStyle.INPUT_CURSOR.toArgb());
        }
    }
}
