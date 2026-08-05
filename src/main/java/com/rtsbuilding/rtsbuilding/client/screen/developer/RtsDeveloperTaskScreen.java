package com.rtsbuilding.rtsbuilding.client.screen.developer;

import com.rtsbuilding.rtsbuilding.client.developer.RtsDeveloperScenarioTracker;
import com.rtsbuilding.rtsbuilding.client.input.overlay.LegacyGuiGraphics;
import com.rtsbuilding.rtsbuilding.client.util.RtsClientUiUtil;
import com.rtsbuilding.rtsbuilding.uikit.theme.DeveloperScreenStyle;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import org.lwjgl.input.Keyboard;

import java.io.IOException;

/** 开发者作业入口；任务只能由真实操作事件推进，没有手动“通过”按钮。 */
public final class RtsDeveloperTaskScreen extends GuiScreen {
    private static final int TASK_BUTTON_BASE = 100;
    private static final int BACK_BUTTON = 1;

    private final GuiScreen parent;

    public RtsDeveloperTaskScreen(GuiScreen parent) {
        this.parent = parent;
    }

    @Override
    public void initGui() {
        this.buttonList.clear();
        RtsDeveloperScenarioTracker.Scenario[] scenarios =
                RtsDeveloperScenarioTracker.Scenario.values();
        RtsDeveloperTaskLayout.Layout layout =
                RtsDeveloperTaskLayout.resolve(this.width, this.height, scenarios.length);
        for (int index = 0; index < scenarios.length; index++) {
            RtsDeveloperScenarioTracker.Scenario scenario = scenarios[index];
            RtsDeveloperTaskLayout.Bounds bounds = layout.taskButtons().get(index);
            this.buttonList.add(new GuiButton(
                    TASK_BUTTON_BASE + index,
                    bounds.x(), bounds.y(), bounds.width(), bounds.height(),
                    I18n.format(scenario.translationKey())));
        }
        RtsDeveloperTaskLayout.Bounds back = layout.backButton();
        this.buttonList.add(new GuiButton(
                BACK_BUTTON, back.x(), back.y(), back.width(), back.height(),
                I18n.format("gui.rtsbuilding.back")));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button == null || !button.enabled) {
            return;
        }
        if (button.id == BACK_BUTTON) {
            this.mc.displayGuiScreen(this.parent);
            return;
        }
        int index = button.id - TASK_BUTTON_BASE;
        RtsDeveloperScenarioTracker.Scenario[] scenarios =
                RtsDeveloperScenarioTracker.Scenario.values();
        if (index >= 0 && index < scenarios.length) {
            RtsDeveloperScenarioTracker.getInstance().start(scenarios[index]);
            this.mc.displayGuiScreen(this.parent);
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            this.mc.displayGuiScreen(this.parent);
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        LegacyGuiGraphics graphics = new LegacyGuiGraphics(this.mc, this.width, this.height);
        graphics.fill(0, 0, this.width, this.height, DeveloperScreenStyle.BACKGROUND.toArgb());
        RtsDeveloperTaskLayout.Layout layout = RtsDeveloperTaskLayout.resolve(
                this.width, this.height, RtsDeveloperScenarioTracker.Scenario.values().length);
        RtsClientUiUtil.drawCenteredStringNoShadow(
                graphics, this.fontRendererObj,
                I18n.format("screen.rtsbuilding.developer.title"),
                layout.centerX(), layout.titleY(), DeveloperScreenStyle.TITLE.toArgb());

        RtsDeveloperScenarioTracker tracker = RtsDeveloperScenarioTracker.getInstance();
        if (tracker.activeScenario() != null) {
            String active = I18n.format(
                    "screen.rtsbuilding.developer.active",
                    I18n.format(tracker.activeScenario().translationKey()),
                    tracker.currentStep() + "/" + tracker.requiredSteps());
            RtsClientUiUtil.drawCenteredStringNoShadow(
                    graphics, this.fontRendererObj, active,
                    layout.centerX(), layout.activeStatusY(),
                    DeveloperScreenStyle.ACTIVE_STATUS.toArgb());
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
