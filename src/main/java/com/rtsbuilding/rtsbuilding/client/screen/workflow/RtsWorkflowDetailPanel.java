package com.rtsbuilding.rtsbuilding.client.screen.workflow;

import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.screen.panel.RtsWindowPanel;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.common.persist.PersistableProperty;
import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uicore.workflow.WorkflowUiRow;
import com.rtsbuilding.rtsbuilding.uikit.scroll.UiScrollModel;
import com.rtsbuilding.rtsbuilding.uikit.scroll.UiVisibleRange;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiThemeRuntime;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiThemeToken;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/**
 * 工作流完整详情的只读窗口。
 *
 * <p>窗口只持有用户主动打开的一个 entryId；生产工作流列表消失或重排时，
 * 它不会切换到相邻任务，而是保留最后快照并明确标为非活动。完整缺料名称
 * 只在这里按需通过 {@link WorkflowUiAdapter#fullDetailLines(WorkflowUiRow)}
 * 整理，普通列表帧不会为不可见任务构造 ItemStack 名称。</p>
 */
public final class RtsWorkflowDetailPanel extends RtsWindowPanel {
    private static final int DEFAULT_W = 360;
    private static final int DEFAULT_H = 260;
    private static final int MIN_W = 280;
    private static final int MIN_H = 170;
    private static final int PAD = 8;
    private static final int FOOTER_H = 22;
    private static final int LINE_H = 12;
    private static final int TEXT_PADDING = 4;
    private static final int FOOTER_ACTION_SPACE = 70;

    private final UiScrollModel scrollModel = new UiScrollModel(0.0D, 0.0D);
    private WorkflowUiRow selectedRow;
    private int selectedEntryId = -1;
    private boolean finalSnapshot;
    private String cachedSignature = "";
    private int cachedTextWidth = -1;
    private List<FormattedCharSequence> cachedLines = List.of();

    public RtsWorkflowDetailPanel() {
        this.resizable = true;
    }

    @Override
    public void init(BuilderScreen screen, ClientRtsController controller) {
        super.init(screen, controller);
        this.selectedRow = null;
        this.selectedEntryId = -1;
        this.finalSnapshot = false;
        this.scrollModel.setOffset(0.0D);
    }

    /** 从工作流正文打开指定条目的详情，并把该条目提升到浮窗最前。 */
    public void openFor(WorkflowUiRow row) {
        if (row == null || row.entryId < 0) {
            return;
        }
        this.selectedRow = row;
        this.selectedEntryId = row.entryId;
        this.finalSnapshot = false;
        invalidateLines();
        this.scrollModel.setOffset(0.0D);
        setOpen(true);
        markBroughtToFront();
    }

    @Override
    protected boolean canShowWindow() {
        return this.selectedRow != null && this.selectedEntryId >= 0;
    }

    @Override
    protected Component getTitle() {
        return Component.translatable(
                "screen.rtsbuilding.workflow.details.title",
                this.selectedRow == null ? "" : this.selectedRow.label);
    }

    @Override
    protected int getDefaultWidth() {
        return DEFAULT_W;
    }

    @Override
    protected int getDefaultHeight() {
        return DEFAULT_H;
    }

    @Override
    protected int getMinWindowWidth() {
        return this.screen == null ? MIN_W
                : RtsWorkflowDetailLayout.adaptiveMinimum(MIN_W, this.screen.width);
    }

    @Override
    protected int getMinWindowHeight() {
        return this.screen == null ? MIN_H
                : RtsWorkflowDetailLayout.adaptiveMinimum(MIN_H, this.screen.height);
    }

    @Override
    protected void computeDefaultPosition() {
        if (this.screen == null) return;
        this.windowX = Math.max(8, (this.screen.width - DEFAULT_W) / 2);
        this.windowY = Math.max(8, (this.screen.height - DEFAULT_H) / 2);
    }

    @Override
    public void render(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick) {
        refreshSelectedRow();
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    protected void renderContent(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick) {
        if (this.screen == null || this.selectedRow == null) {
            return;
        }
        RtsWorkflowDetailLayout.Geometry layout = detailLayout();
        UiRect viewport = layout.viewport;
        int x = (int) viewport.getX();
        int y = (int) viewport.getY();
        int width = layout.textWidth;
        int footerY = layout.footerY;
        List<FormattedCharSequence> lines = ensureLines(width);
        double viewportHeight = Math.max(1.0D, viewport.getHeight());
        this.scrollModel.setExtents(lines.size() * (double) LINE_H, viewportHeight);

        graphics.fill(
                (int) viewport.getX(),
                (int) viewport.getY(),
                (int) viewport.right(),
                (int) viewport.bottom(),
                UiThemeRuntime.color(UiThemeToken.SURFACE_SUNKEN).toArgb());
        if (!lines.isEmpty()) {
            UiVisibleRange visible = UiVisibleRange.calculate(
                    lines.size(), LINE_H, viewportHeight,
                    this.scrollModel.getOffset(), 0);
            int first = visible.getFirstInclusive();
            int last = visible.getLastExclusive();
            int firstY = (int) Math.round(
                    viewport.getY() - this.scrollModel.getOffset() + first * (double) LINE_H);
            graphics.enableScissor(
                    (int) viewport.getX(),
                    (int) viewport.getY(),
                    (int) viewport.right(),
                    (int) viewport.bottom());
            try {
                for (int index = first; index < last && index < lines.size(); index++) {
                    graphics.drawString(
                            this.screen.font(),
                            lines.get(index),
                            (int) viewport.getX() + TEXT_PADDING,
                            firstY + (index - first) * LINE_H,
                            UiThemeRuntime.color(UiThemeToken.TEXT_PRIMARY).toArgb(),
                            false);
                }
            } finally {
                graphics.disableScissor();
            }
        }

        String footer = Component.translatable(
                this.finalSnapshot
                        ? "screen.rtsbuilding.workflow.details.final"
                        : "screen.rtsbuilding.workflow.details.scroll").getString();
        graphics.drawString(
                this.screen.font(),
                this.screen.font().plainSubstrByWidth(footer, Math.max(20, width - FOOTER_ACTION_SPACE)),
                x,
                footerY + 4,
                UiThemeRuntime.color(this.finalSnapshot
                        ? UiThemeToken.WARNING : UiThemeToken.TEXT_SECONDARY).toArgb(),
                false);
        String close = Component.translatable(
                "screen.rtsbuilding.workflow.details.close").getString();
        graphics.drawString(
                this.screen.font(),
                close,
                x + width - this.screen.font().width(close),
                footerY + 4,
                UiThemeRuntime.color(UiThemeToken.TEXT_PRIMARY).toArgb(),
                false);
    }

    @Override
    protected void handleContentClick(double mouseX, double mouseY, int button) {
        if (button != 0 || this.screen == null) {
            return;
        }
        RtsWorkflowDetailLayout.Geometry layout = detailLayout();
        UiRect viewport = layout.viewport;
        int x = (int) viewport.getX();
        int width = layout.textWidth;
        int footerY = layout.footerY;
        String close = Component.translatable(
                "screen.rtsbuilding.workflow.details.close").getString();
        if (UiRect.contains(
                x + width - this.screen.font().width(close), footerY,
                this.screen.font().width(close), FOOTER_H, mouseX, mouseY)) {
            setOpen(false);
        }
    }

    @Override
    protected boolean handleContentScroll(
            double mouseX,
            double mouseY,
            double scrollX,
            double scrollY) {
        RtsWorkflowDetailLayout.Geometry layout = detailLayout();
        UiRect viewport = layout.viewport;
        if (!viewport.contains(mouseX, mouseY)) {
            return false;
        }
        ensureLines(layout.textWidth);
        if (scrollY != 0.0D) {
            this.scrollModel.scrollBy(-scrollY * LINE_H * 2.0D);
        }
        return true;
    }

    private void refreshSelectedRow() {
        if (this.controller == null || this.selectedEntryId < 0) {
            return;
        }
        WorkflowUiRow current = WorkflowUiAdapter.findRow(
                this.controller, this.selectedEntryId);
        if (current == null) {
            if (!this.finalSnapshot) {
                this.finalSnapshot = true;
                invalidateLines();
            }
            return;
        }
        if (this.finalSnapshot || !sameDetailSource(this.selectedRow, current)) {
            this.selectedRow = current;
            this.finalSnapshot = false;
            invalidateLines();
        }
    }

    private List<FormattedCharSequence> ensureLines(int width) {
        if (this.selectedRow == null || this.screen == null) {
            return List.of();
        }
        if (!this.cachedLines.isEmpty()
                && this.cachedTextWidth == width
                && !this.cachedSignature.isEmpty()) {
            return this.cachedLines;
        }
        List<FormattedCharSequence> lines = new ArrayList<>();
        List<String> details = WorkflowUiAdapter.fullDetailLines(this.selectedRow);
        if (this.finalSnapshot) {
            details.add(0, Component.translatable(
                    "screen.rtsbuilding.workflow.details.final").getString());
        }
        for (String detail : details) {
            for (FormattedCharSequence line : this.screen.font().split(
                    Component.literal(detail), Math.max(40, width - TEXT_PADDING * 2))) {
                lines.add(line);
            }
        }
        this.cachedLines = List.copyOf(lines);
        this.cachedTextWidth = width;
        this.cachedSignature = detailSignature(this.selectedRow, this.finalSnapshot);
        return this.cachedLines;
    }

    private RtsWorkflowDetailLayout.Geometry detailLayout() {
        return RtsWorkflowDetailLayout.content(
                contentX(), contentY(), contentWidth(), contentHeight(), PAD, FOOTER_H);
    }

    private void invalidateLines() {
        this.cachedLines = List.of();
        this.cachedSignature = "";
        this.cachedTextWidth = -1;
    }

    private static boolean sameDetailSource(WorkflowUiRow left, WorkflowUiRow right) {
        if (left == right) return true;
        if (left == null || right == null) return false;
        return detailSignature(left, false).equals(detailSignature(right, false));
    }

    private static String detailSignature(WorkflowUiRow row, boolean finalSnapshot) {
        if (row == null) return "";
        return row.entryId + "|" + row.label + "|" + row.progressText + "|"
                + row.statusText + "|" + row.detailText + "|" + row.nextStepText
                + "|" + row.missingItemIds.hashCode() + "|" + finalSnapshot;
    }

    private final List<PersistableProperty> properties = List.of(
            PersistableProperty.bounds("workflow_details", this)
    );

    @Override
    public List<PersistableProperty> persistableProperties() {
        return this.properties;
    }
}
