package com.rtsbuilding.rtsbuilding.client.presentation.plugin;

import com.rtsbuilding.rtsbuilding.client.presentation.panel.base.component.ScrollBar;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.base.overlay.OverlayContext;
import com.rtsbuilding.rtsbuilding.client.presentation.plugin.workflow.RowLayout;
import com.rtsbuilding.rtsbuilding.client.presentation.plugin.workflow.WorkflowInputHandler;
import com.rtsbuilding.rtsbuilding.client.presentation.plugin.workflow.WorkflowRenderer;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

public class WorkflowProgress {

    private final OverlayContext context;
    private final WorkflowRenderer renderer;
    private final WorkflowInputHandler inputHandler;

    public WorkflowProgress(OverlayContext context) {
        this.context = context;
        ScrollBar scrollBar = new ScrollBar();
        List<RowLayout> rowLayouts = new ArrayList<>();
        this.renderer = new WorkflowRenderer(context, scrollBar, rowLayouts);
        this.inputHandler = new WorkflowInputHandler(context, scrollBar, rowLayouts);
    }

    public void renderContent(GuiGraphics g) {
        renderer.renderContent(g);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return inputHandler.mouseClicked(mouseX, mouseY, button);
    }

    public OverlayContext getContext() {
        return context;
    }
}
