package com.rtsbuilding.rtsbuilding.client.screen.workflow;

import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.network.RtsClientNetworkBridge;
import com.rtsbuilding.rtsbuilding.client.screen.panel.RtsWindowPanel;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.common.persist.PersistableProperty;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsResumePlacementActionPayload;
import com.rtsbuilding.rtsbuilding.network.builder.S2CRtsResumePlacementScanPayload;
import com.rtsbuilding.rtsbuilding.uikit.layout.WorkflowResumeWindowLayout;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * 搁置放置作业的恢复窗口。
 *
 * <p>本类持有窗口生命周期、当前扫描结果和恢复命令；几何、半开命中和 chrome
 * 交给共享 Kit，真实物品和翻译文本由薄 renderer 负责。</p>
 */
public final class RtsResumePlacementPanel extends RtsWindowPanel {
    private S2CRtsResumePlacementScanPayload scanData;
    private int workflowEntryId = -1;

    @Override
    protected Component getTitle() {
        return Component.translatable("screen.rtsbuilding.workflow.resume_placement.title");
    }

    @Override
    protected int getDefaultWidth() {
        return WorkflowResumeWindowLayout.PLACEMENT_W;
    }

    @Override
    protected int getDefaultHeight() {
        return WorkflowResumeWindowLayout.PLACEMENT_H;
    }

    @Override
    protected void computeDefaultPosition() {
        if (this.screen == null) {
            return;
        }
        this.windowX = (this.screen.width - WorkflowResumeWindowLayout.PLACEMENT_W) / 2;
        this.windowY = (this.screen.height - WorkflowResumeWindowLayout.PLACEMENT_H) / 2;
    }

    @Override
    protected boolean canShowWindow() {
        return this.scanData != null;
    }

    @Override
    protected boolean shouldClipContent() {
        return false;
    }

    @Override
    public void init(BuilderScreen screen, ClientRtsController controller) {
        super.init(screen, controller);
        this.draggable = true;
        this.resizable = false;
        this.closable = true;
        setOpen(false);
    }

    /** 载入一次不可变扫描结果并打开窗口。 */
    public void openWithData(S2CRtsResumePlacementScanPayload data) {
        this.scanData = data;
        this.workflowEntryId = data.workflowEntryId();
        setOpen(true);
    }

    @Override
    public void setOpen(boolean open) {
        super.setOpen(open);
        if (!open) {
            this.scanData = null;
            this.workflowEntryId = -1;
            if (this.controller != null) {
                this.controller.clearResumeScanData();
            }
        }
    }

    @Override
    protected void renderContent(
            GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (this.scanData == null) {
            return;
        }
        WorkflowResumeWindowLayout.PlacementGeometry geometry = geometry();
        boolean enabled = this.scanData.missingItems() <= 0;
        double primaryHover = animateContentControl("resume_primary", enabled,
                geometry.primaryAction.contains(mouseX, mouseY), false).hover();
        double secondaryHover = animateContentControl("resume_secondary", enabled,
                geometry.secondaryAction != null
                        && geometry.secondaryAction.contains(mouseX, mouseY), false).hover();
        PlacementResumePanelRenderer.render(graphics, this.screen.font(), geometry,
                this.scanData, primaryHover, secondaryHover);
    }

    @Override
    protected void handleContentClick(double mouseX, double mouseY, int button) {
        if (button != 0 || this.scanData == null) {
            return;
        }
        WorkflowResumeWindowLayout.PlacementControl control = geometry().hitAt(
                mouseX, mouseY, this.scanData.missingItems() <= 0);
        if (control == WorkflowResumeWindowLayout.PlacementControl.RESUME_OR_SKIP) {
            sendAction(0);
        } else if (control == WorkflowResumeWindowLayout.PlacementControl.OVERWRITE) {
            sendAction(1);
        }
    }

    private WorkflowResumeWindowLayout.PlacementGeometry geometry() {
        return WorkflowResumeWindowLayout.placement(contentX(), contentY(), contentWidth(),
                contentHeight(), this.scanData != null && this.scanData.conflictCount() > 0);
    }

    private void sendAction(int strategy) {
        RtsClientNetworkBridge.send(new C2SRtsResumePlacementActionPayload(
                strategy, this.workflowEntryId));
        setOpen(false);
    }

    private final List<PersistableProperty> properties = List.of(
            PersistableProperty.bounds("resume_placement", this)
    );

    @Override
    public List<PersistableProperty> persistableProperties() {
        return this.properties;
    }
}
