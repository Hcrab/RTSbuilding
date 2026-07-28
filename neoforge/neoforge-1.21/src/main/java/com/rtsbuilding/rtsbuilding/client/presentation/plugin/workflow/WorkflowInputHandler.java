package com.rtsbuilding.rtsbuilding.client.presentation.plugin.workflow;

import com.rtsbuilding.rtsbuilding.client.kernel.RtsClientKernel;
import com.rtsbuilding.rtsbuilding.client.infrastructure.module.workflow.WorkflowModule;
import com.rtsbuilding.rtsbuilding.client.network.RtsClientPacketGateway;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.base.component.ScrollBar;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.base.overlay.OverlayContext;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public class WorkflowInputHandler {

    private final OverlayContext context;
    private final ScrollBar scrollBar;
    private final List<RowLayout> rowLayouts;

    public WorkflowInputHandler(OverlayContext context, ScrollBar scrollBar, List<RowLayout> rowLayouts) {
        this.context = context;
        this.scrollBar = scrollBar;
        this.rowLayouts = rowLayouts;
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        if (!context.contains((int) mouseX, (int) mouseY)) return false;

        int mx = (int) mouseX;
        int my = (int) mouseY;

        WorkflowModule wm = RtsClientKernel.get().module(WorkflowModule.class);
        if (wm == null) return false;

        for (RowLayout rl : rowLayouts) {
            if (rl.containsToggle(mx, my)) {
                RtsClientPacketGateway.sendPauseWorkflow(rl.entryId());
                return true;
            }
            if (rl.containsDelete(mx, my)) {
                RtsClientPacketGateway.sendDeleteWorkflow(rl.entryId());
                return true;
            }
        }

        return false;
    }
}
