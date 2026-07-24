package com.rtsbuilding.rtsbuilding.client.presentation.plugin.binding;

import com.rtsbuilding.rtsbuilding.client.domain.state.LinkedStorageEntry;
import com.rtsbuilding.rtsbuilding.client.infrastructure.di.CompositionRoot;
import com.rtsbuilding.rtsbuilding.client.infrastructure.module.storage.StorageModule;
import com.rtsbuilding.rtsbuilding.client.network.RtsClientPacketGateway;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.base.component.ScrollBar;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.base.overlay.OverlayContext;

import java.util.List;

public final class BindingInputHandler {

    private static final int ROW_H = 20;
    private static final int SCROLLBAR_W = 7;
    private static final int RIGHT_MARGIN = 4;
    private static final int TOP_PAD = 2;
    private static final int ARROW_BTN_SIZE = 14;

    private final OverlayContext ctx;
    private final ScrollBar scrollBar;
    private final List<RowLayout> rowLayouts;
    private final PriorityEditController editController;

    public BindingInputHandler(OverlayContext ctx, ScrollBar scrollBar, List<RowLayout> rowLayouts,
                        PriorityEditController editController) {
        this.ctx = ctx;
        this.scrollBar = scrollBar;
        this.rowLayouts = rowLayouts;
        this.editController = editController;
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        int mx = (int) mouseX;
        int my = (int) mouseY;

        if (editController.isEditing()
                && !editController.isClickOnEditBox(mx, my, ctx.getX(), ctx.getY(), scrollBar.getScroll())) {
            editController.tryCommit();
        }

        StorageModule sm = CompositionRoot.get().module(StorageModule.class);
        if (sm == null) return false;

        int barX = ctx.getX() + ctx.getWidth() - SCROLLBAR_W - RIGHT_MARGIN;
        if (scrollBar.handleClick(mouseX, mouseY, barX,
                ctx.getY() + TOP_PAD + 6, ctx.getHeight() - TOP_PAD * 2 - 12)) {
            return true;
        }

        return handleRowClick(mx, my, sm);
    }

    private boolean handleRowClick(int mx, int my, StorageModule sm) {
        var entries = sm.getLinkedStorageEntries();
        var priorities = sm.getLinkedPriorities();
        int count = Math.min(entries.size(), Math.min(rowLayouts.size(), priorities.size()));

        for (int i = 0; i < count; i++) {
            RowLayout rl = rowLayouts.get(i);
            if (rl == null) continue;
            if (my < rl.y || my >= rl.y + ROW_H - 1) continue;

            int origIdx = rl.originalIndex;
            var entry = entries.get(origIdx);

            if (inRect(mx, my, rl.arrowBtnX, rl.y, ARROW_BTN_SIZE, ROW_H)) {
                handleArrowSwap(i, count, entries, priorities, rowLayouts);
                return true;
            }

            if (inRect(mx, my, rl.priorityX, rl.y, rl.priorityW, ROW_H)) {
                if (!editController.isEditing() || editController.getEditingIndex() != i) {
                    editController.beginEdit(i, priorities.get(origIdx));
                }
                return true;
            }

            if (inRect(mx, my, rl.locateBtnX, rl.y, rl.locateBtnW, ROW_H)) {
                sm.toggleLocationDisplay(entry.pos());
                return true;
            }

            if (inRect(mx, my, rl.unbindX, rl.y, rl.unbindW, ROW_H)) {
                RtsClientPacketGateway.sendUnlinkStorage(entry.pos());
                return true;
            }

            if (inRect(mx, my, rl.toggleX, rl.y, rl.toggleW, ROW_H)) {
                boolean nextExtractOnly = !entry.isExtractOnly();
                RtsClientPacketGateway.sendUpdateLinkedStorage(
                        entry.pos(), nextExtractOnly, priorities.get(origIdx));
                return true;
            }
        }
        return false;
    }

    private void handleArrowSwap(int sortedIdx, int count, List<LinkedStorageEntry> entries,
                                  List<Integer> priorities, List<RowLayout> layouts) {
        int targetIdx = (sortedIdx == 0) ? sortedIdx + 1 : sortedIdx - 1;
        if (targetIdx < 0 || targetIdx >= count) return;

        RowLayout currentRl = layouts.get(sortedIdx);
        RowLayout targetRl = layouts.get(targetIdx);

        int currentPriority = priorities.get(currentRl.originalIndex);
        int targetPriority = priorities.get(targetRl.originalIndex);
        var currentEntry = entries.get(currentRl.originalIndex);
        var targetEntry = entries.get(targetRl.originalIndex);

        if (currentPriority == targetPriority) {
            int newPriority = (sortedIdx == 0)
                    ? Math.min(100, targetPriority + 1)
                    : Math.max(0, targetPriority - 1);
            RtsClientPacketGateway.sendUpdateLinkedStorage(
                    currentEntry.pos(), currentEntry.isExtractOnly(), newPriority);
        } else {
            RtsClientPacketGateway.sendUpdateLinkedStorage(
                    currentEntry.pos(), currentEntry.isExtractOnly(), targetPriority);
            RtsClientPacketGateway.sendUpdateLinkedStorage(
                    targetEntry.pos(), targetEntry.isExtractOnly(), currentPriority);
        }
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (editController.isEditing()) {
            editController.tryCommit();
        }
        return scrollBar.handleScroll(scrollY);
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        if (scrollBar.isDragging()) {
            scrollBar.endDrag();
            return true;
        }
        return false;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button != 0) return false;
        if (scrollBar.isDragging()) {
            return scrollBar.handleDrag(mouseY, ctx.getY() + TOP_PAD, ctx.getHeight() - TOP_PAD * 2);
        }
        return false;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return editController.handleKeyPressed(keyCode);
    }

    public boolean charTyped(char codePoint, int modifiers) {
        return editController.handleCharTyped(codePoint);
    }

    private static boolean inRect(int px, int py, int rx, int ry, int rw, int rh) {
        return px >= rx && px < rx + rw && py >= ry && py < ry + rh;
    }
}
