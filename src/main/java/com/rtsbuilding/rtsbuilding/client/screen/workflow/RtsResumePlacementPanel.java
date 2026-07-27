package com.rtsbuilding.rtsbuilding.client.screen.workflow;

import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.input.overlay.LegacyGuiGraphics;
import com.rtsbuilding.rtsbuilding.client.screen.panel.RtsWindowPanel;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.common.persist.PersistableProperty;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsResumePlacementActionPayload;
import com.rtsbuilding.rtsbuilding.network.builder.S2CRtsResumePlacementScanPayload;
import com.rtsbuilding.rtsbuilding.network.RtsPayloadRegistrar;
import com.rtsbuilding.rtsbuilding.uikit.layout.WorkflowResumeWindowLayout;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;

import java.util.Collections;
import java.util.List;

/**
 * 搁置放置作业重启面板。
 *
 * <p>本类只拥有窗口生命周期、当前扫描结果和恢复命令；几何、半开命中与 chrome
 * 交给共享 Kit，Minecraft 文本和 ItemStack 交给薄 renderer。</p>
 */
public final class RtsResumePlacementPanel extends RtsWindowPanel {
    private S2CRtsResumePlacementScanPayload scanData;
    private int workflowEntryId = -1;

    public RtsResumePlacementPanel() {
    }

    @Override
    protected ITextComponent getTitle() {
        return new TextComponentTranslation(
                "screen.rtsbuilding.workflow.resume_placement.title");
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
        this.windowX = (this.screen.width
                - WorkflowResumeWindowLayout.PLACEMENT_W) / 2;
        this.windowY = (this.screen.height
                - WorkflowResumeWindowLayout.PLACEMENT_H) / 2;
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
    public void init(
            BuilderScreen screen,
            ClientRtsController controller) {
        super.init(screen, controller);
        this.draggable = true;
        this.resizable = false;
        this.closable = true;
        setOpen(false);
    }

    /**
     * 载入一次不可变扫描结果并打开窗口。
     */
    public void openWithData(
            S2CRtsResumePlacementScanPayload data) {
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
            LegacyGuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick) {
        if (scanData == null) {
            return;
        }
        PlacementResumePanelRenderer.render(
                graphics,
                this.screen.font(),
                geometry(),
                scanData,
                mouseX,
                mouseY);
    }

    @Override
    protected void handleContentClick(
            double mouseX,
            double mouseY,
            int button) {
        if (button != 0 || scanData == null) {
            return;
        }
        WorkflowResumeWindowLayout.PlacementControl control =
                geometry().hitAt(
                        mouseX,
                        mouseY,
                        scanData.missingItems() <= 0);
        if (control
                == WorkflowResumeWindowLayout.PlacementControl.RESUME_OR_SKIP) {
            sendAction(0);
        } else if (control
                == WorkflowResumeWindowLayout.PlacementControl.OVERWRITE) {
            sendAction(1);
        }
    }

    private WorkflowResumeWindowLayout.PlacementGeometry geometry() {
        return WorkflowResumeWindowLayout.placement(
                contentX(),
                contentY(),
                contentWidth(),
                contentHeight(),
                scanData != null && scanData.conflictCount() > 0);
    }

    private void sendAction(int strategy) {
        RtsPayloadRegistrar.sendToServer(
                new C2SRtsResumePlacementActionPayload(
                        strategy,
                        this.workflowEntryId));
        setOpen(false);
    }

    private final List<PersistableProperty> properties = Collections.singletonList(
            PersistableProperty.bounds("resume_placement", this));

    @Override
    public List<PersistableProperty> persistableProperties() {
        return properties;
    }
}
