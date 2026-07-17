package com.rtsbuilding.rtsbuilding.client.screen.developer;

import com.rtsbuilding.rtsbuilding.client.developer.RtsDeveloperScenarioTracker;
import com.rtsbuilding.rtsbuilding.client.util.RtsClientUiUtil;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** 开发者作业入口；任务只能由真实操作事件推进，没有手动“通过”按钮。 */
public final class RtsDeveloperTaskScreen extends Screen {
    private final Screen parent;

    public RtsDeveloperTaskScreen(Screen parent) {
        super(Component.translatable("screen.rtsbuilding.developer.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        var scenarios = RtsDeveloperScenarioTracker.Scenario.values();
        var layout = RtsDeveloperTaskLayout.resolve(this.width, this.height, scenarios.length);
        for (int index = 0; index < scenarios.length; index++) {
            RtsDeveloperScenarioTracker.Scenario scenario = scenarios[index];
            var bounds = layout.taskButtons().get(index);
            addRenderableWidget(Button.builder(Component.translatable(scenario.translationKey()), button -> {
                RtsDeveloperScenarioTracker.getInstance().start(scenario);
                this.minecraft.setScreen(parent);
            }).bounds(bounds.x(), bounds.y(), bounds.width(), bounds.height()).build());
        }
        var back = layout.backButton();
        addRenderableWidget(Button.builder(Component.translatable("gui.rtsbuilding.back"), button ->
                this.minecraft.setScreen(parent))
                .bounds(back.x(), back.y(), back.width(), back.height()).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0xF0101820);
        var layout = RtsDeveloperTaskLayout.resolve(this.width, this.height,
                RtsDeveloperScenarioTracker.Scenario.values().length);
        RtsClientUiUtil.drawCenteredStringNoShadow(
                graphics, this.font, this.title, layout.centerX(), layout.titleY(), 0xFFFFFFFF);
        var tracker = RtsDeveloperScenarioTracker.getInstance();
        if (tracker.activeScenario() != null) {
            RtsClientUiUtil.drawCenteredStringNoShadow(graphics, this.font,
                    Component.translatable("screen.rtsbuilding.developer.active",
                            Component.translatable(tracker.activeScenario().translationKey()),
                            tracker.currentStep() + "/" + tracker.requiredSteps()),
                    layout.centerX(), layout.activeStatusY(), 0xFFFFD27F);
        }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }
}
