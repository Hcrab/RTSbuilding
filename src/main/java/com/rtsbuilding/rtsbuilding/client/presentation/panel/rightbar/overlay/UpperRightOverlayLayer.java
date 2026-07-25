package com.rtsbuilding.rtsbuilding.client.presentation.panel.rightbar.overlay;

import com.rtsbuilding.rtsbuilding.client.domain.state.WorkflowProgress;
import com.rtsbuilding.rtsbuilding.client.infrastructure.di.CompositionRoot;
import com.rtsbuilding.rtsbuilding.client.infrastructure.module.workflow.WorkflowModule;
import com.rtsbuilding.rtsbuilding.client.network.RtsClientPacketGateway;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.base.overlay.DownOverlayLayer;
import com.rtsbuilding.rtsbuilding.client.util.render.TextRenderer;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowProgressProcessor;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;


public final class UpperRightOverlayLayer extends DownOverlayLayer {

    
    private static final int ROW_HEIGHT = 24;
    private static final int ROW_PAD_V = 2;
    private static final int ROW_PAD_H = 6;
    private static final int BAR_HEIGHT = 8;
    private static final int BAR_GAP = 2;

    private static final int PROGRESS_BAR_COLOR = 0xFF4A9BDB;
    private static final int PROGRESS_BG_COLOR = 0x334A9BDB;
    private static final int SUSPENDED_BAR_COLOR = 0xFF888888;
    private static final int SUSPENDED_BG_COLOR = 0x33888888;
    private static final int COMPLETE_BAR_COLOR = 0xFF55CC55;
    private static final int COMPLETE_BG_COLOR = 0x3355CC55;
    private static final int TEXT_COLOR = 0xFFCCCCCC;
    private static final int SUPPRESSED_TEXT_COLOR = 0xFF888888;

    
    private static final int BUTTON_SIZE = 10;


    private final List<RowLayout> cachedRows = new ArrayList<>();

    public UpperRightOverlayLayer() {
    }

    @Override
    protected void renderContent(GuiGraphics g) {
        WorkflowModule wm = CompositionRoot.get().module(WorkflowModule.class);
        if (wm == null) return;

        WorkflowProgress progress = wm.getProgress();
        RtsWorkflowStatus[] statuses = progress.statuses();
        int activeCount = progress.activeCount();
        if (activeCount <= 0) {
            renderNoWorkflows(g);
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        var font = mc.font;
        int ox = getX() + ROW_PAD_H;
        int oy = getY() + ROW_PAD_V;
        int contentW = getWidth() - ROW_PAD_H * 2;
        int barW = contentW;

        cachedRows.clear();

        for (int i = 0; i < statuses.length; i++) {
            RtsWorkflowStatus status = statuses[i];
            if (status == null || !status.isActive()) continue;

            String label = RtsWorkflowProgressProcessor.formatLabel(status);
            String progressText = RtsWorkflowProgressProcessor.formatProgressText(status);
            int fillW = RtsWorkflowProgressProcessor.computeFillWidth(status, barW);

            int rowY = oy;
            int rowH = ROW_HEIGHT;

            
            boolean hovered = false;
            int mx = getLastMouseX();
            int my = getLastMouseY();
            if (mx >= ox && mx < ox + contentW && my >= rowY && my < rowY + rowH) {
                hovered = true;
            }

            
            int labelColor = status.suspended() || status.paused() ? SUPPRESSED_TEXT_COLOR : TEXT_COLOR;
            g.fill(ox, rowY, ox + contentW, rowY + rowH, hovered ? 0x15FFFFFF : 0x00000000);

            
            TextRenderer.draw(g, label, ox, rowY + 1, labelColor);

            
            int barY = rowY + font.lineHeight + BAR_GAP;
            int bgColor = status.isComplete() ? COMPLETE_BG_COLOR
                    : (status.suspended() || status.paused() ? SUSPENDED_BG_COLOR : PROGRESS_BG_COLOR);
            int fgColor = status.isComplete() ? COMPLETE_BAR_COLOR
                    : (status.suspended() || status.paused() ? SUSPENDED_BAR_COLOR : PROGRESS_BAR_COLOR);
            g.fill(ox, barY, ox + barW, barY + BAR_HEIGHT, bgColor);
            if (fillW > 0) {
                g.fill(ox, barY, ox + fillW, barY + BAR_HEIGHT, fgColor);
            }

            
            int textX = ox + barW - font.width(progressText);
            TextRenderer.draw(g, progressText, textX, rowY + 1, labelColor);

            
            int btnX = ox + contentW - BUTTON_SIZE;
            int btnY = rowY + 1;
            if (hovered) {
                String pauseLabel = status.paused() ? "\u25B6" : "\u23F8";
                TextRenderer.draw(g, pauseLabel, btnX, btnY, labelColor);
            }

            
            cachedRows.add(new RowLayout(i, ox, rowY, contentW, rowH));

            oy += rowH + ROW_PAD_V;
        }
    }

    @Override
    protected void postRenderContent(GuiGraphics g) {
        super.postRenderContent(g);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        if (!contains((int) mouseX, (int) mouseY)) return false;

        for (RowLayout row : cachedRows) {
            if (row.contains((int) mouseX, (int) mouseY)) {
                handleRowClick(row);
                return true;
            }
        }
        return false;
    }

    private void handleRowClick(RowLayout row) {
        WorkflowModule wm = CompositionRoot.get().module(WorkflowModule.class);
        if (wm == null) return;

        WorkflowProgress progress = wm.getProgress();
        RtsWorkflowStatus[] statuses = progress.statuses();
        if (row.slotIdx < 0 || row.slotIdx >= statuses.length) return;

        RtsWorkflowStatus status = statuses[row.slotIdx];
        if (status == null || !status.isActive()) return;

        
        RtsClientPacketGateway.sendPauseWorkflow(status.entryId());
    }

    private void renderNoWorkflows(GuiGraphics g) {
        var font = Minecraft.getInstance().font;
        String text = Component.translatable("screen.rtsbuilding.workflow.none").getString();
        int tx = getX() + (getWidth() - font.width(text)) / 2;
        int ty = getY() + (getHeight() - font.lineHeight) / 2;
        TextRenderer.draw(g, text, tx, ty, SUPPRESSED_TEXT_COLOR);
    }

    private record RowLayout(int slotIdx, int x, int y, int width, int height) {
        boolean contains(int px, int py) {
            return px >= x && px < x + width && py >= y && py < y + height;
        }
    }
}
