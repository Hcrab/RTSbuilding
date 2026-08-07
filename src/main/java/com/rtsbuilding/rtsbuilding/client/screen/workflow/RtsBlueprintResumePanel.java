package com.rtsbuilding.rtsbuilding.client.screen.workflow;

import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.network.RtsClientNetworkBridge;
import com.rtsbuilding.rtsbuilding.client.screen.panel.RtsWindowPanel;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.common.persist.PersistableProperty;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsResumePlacementActionPayload;
import com.rtsbuilding.rtsbuilding.network.builder.S2CRtsBlueprintResumeScanPayload;
import com.rtsbuilding.rtsbuilding.uikit.layout.WorkflowResumeWindowLayout;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * 蓝图放置作业的材料扫描恢复窗口。
 *
 * <p>本类拥有窗口生命周期、滚动位置和恢复命令；材料行几何、滚动钳制和动作命中
 * 由共享 Kit 负责，真实物品图标和文本由 renderer 负责。</p>
 */
public final class RtsBlueprintResumePanel extends RtsWindowPanel {
    private S2CRtsBlueprintResumeScanPayload scanData;
    private int workflowEntryId = -1;
    private int scrollOffset;
    private boolean canResume;

    @Override
    protected Component getTitle() {
        return Component.translatable("screen.rtsbuilding.workflow.blueprint_resume.title");
    }

    @Override
    protected int getDefaultWidth() {
        return WorkflowResumeWindowLayout.BLUEPRINT_W;
    }

    @Override
    protected int getDefaultHeight() {
        return WorkflowResumeWindowLayout.BLUEPRINT_H;
    }

    @Override
    protected void computeDefaultPosition() {
        if (this.screen == null) {
            return;
        }
        this.windowX = (this.screen.width - WorkflowResumeWindowLayout.BLUEPRINT_W) / 2;
        this.windowY = (this.screen.height - WorkflowResumeWindowLayout.BLUEPRINT_H) / 2;
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

    /** 载入一次不可变扫描结果，并从材料列表开头打开窗口。 */
    public void openWithData(S2CRtsBlueprintResumeScanPayload data) {
        this.scanData = data;
        this.workflowEntryId = data.workflowEntryId();
        this.scrollOffset = 0;
        this.canResume = BlueprintResumePanelRenderer.allMaterialsEnough(data);
        setOpen(true);
    }

    @Override
    public void setOpen(boolean open) {
        super.setOpen(open);
        if (!open) {
            this.scanData = null;
            this.workflowEntryId = -1;
            this.scrollOffset = 0;
            this.canResume = false;
        }
    }

    @Override
    protected boolean handleContentScroll(
            double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.scanData == null) {
            return false;
        }
        this.scrollOffset = WorkflowResumeWindowLayout.scrollBlueprint(
                this.scrollOffset, this.scanData.itemIds().size(), scrollY);
        return true;
    }

    @Override
    protected void renderContent(
            GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (this.scanData == null) {
            return;
        }
        this.scrollOffset = WorkflowResumeWindowLayout.clampBlueprintScroll(
                this.scrollOffset, this.scanData.itemIds().size());
        WorkflowResumeWindowLayout.BlueprintGeometry geometry = geometry();
        double actionHover = animateContentControl("blueprint_resume", this.canResume,
                geometry.action.contains(mouseX, mouseY), false).hover();
        BlueprintResumePanelRenderer.render(graphics, this.screen.font(), geometry,
                this.scanData, this.scrollOffset, this.canResume, actionHover);
    }

    @Override
    protected void handleContentClick(double mouseX, double mouseY, int button) {
        if (button != 0 || this.scanData == null) {
            return;
        }
        if (geometry().hitAction(mouseX, mouseY, this.canResume)) {
            RtsClientNetworkBridge.send(new C2SRtsResumePlacementActionPayload(
                    0, this.workflowEntryId));
            setOpen(false);
        }
    }

    private WorkflowResumeWindowLayout.BlueprintGeometry geometry() {
        int visibleRows = this.scanData == null ? 0 : Math.min(
                WorkflowResumeWindowLayout.BLUEPRINT_MAX_VISIBLE_ROWS,
                Math.max(0, this.scanData.itemIds().size() - this.scrollOffset));
        return WorkflowResumeWindowLayout.blueprint(contentX(), contentY(), contentWidth(),
                contentHeight(), visibleRows);
    }

    private final List<PersistableProperty> properties = List.of(
            PersistableProperty.bounds("blueprint_resume", this)
    );

    @Override
    public List<PersistableProperty> persistableProperties() {
        return this.properties;
    }
}
