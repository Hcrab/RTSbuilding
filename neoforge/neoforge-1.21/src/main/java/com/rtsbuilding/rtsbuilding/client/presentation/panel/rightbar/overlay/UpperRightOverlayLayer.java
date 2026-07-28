package com.rtsbuilding.rtsbuilding.client.presentation.panel.rightbar.overlay;

import com.rtsbuilding.rtsbuilding.client.presentation.panel.base.overlay.DownOverlayLayer;
import com.rtsbuilding.rtsbuilding.client.presentation.plugin.WorkflowProgress;
import net.minecraft.client.gui.GuiGraphics;

public final class UpperRightOverlayLayer extends DownOverlayLayer {

    private final WorkflowProgress workflowProgress;

    public UpperRightOverlayLayer() {
        this.workflowProgress = new WorkflowProgress(this);
    }

    @Override
    protected void renderContent(GuiGraphics g) {
        workflowProgress.renderContent(g);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return workflowProgress.mouseClicked(mouseX, mouseY, button);
    }
}
