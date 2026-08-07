package com.rtsbuilding.rtsbuilding.client.screen.storage;

import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.common.build.BuilderMode;
import com.rtsbuilding.rtsbuilding.common.storage.RtsBatchStorageSelectionBounds;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * Link Storage 批量框选的客户端交互会话。
 *
 * <p>它只保存两角、预览与高度调整意图；候选储存端点始终由服务端重新扫描。确认后保持批量模式，
 * 方便连续框选；Ctrl 或 Esc 可以退出或清空当前选区。</p>
 */
public final class StorageBatchSelectionSession {
    public enum Phase {
        SELECT_FIRST,
        SELECT_SECOND,
        COMPLETE
    }

    private boolean active;
    private Phase phase = Phase.SELECT_FIRST;
    private BlockPos first;
    private BlockPos second;
    private BlockPos hover;
    private int visualRevision;

    public boolean isActive() {
        return active;
    }

    public Phase phase() {
        return phase;
    }

    public void toggle(Minecraft minecraft, BuilderMode mode) {
        if (active) {
            deactivate(minecraft, true);
            return;
        }
        if (mode != BuilderMode.LINK_STORAGE) {
            return;
        }
        active = true;
        resetSelection();
        show(minecraft, "message.rtsbuilding.storage_batch.entered");
    }

    public void update(BuilderMode mode, BlockPos hoveredPos) {
        if (mode != BuilderMode.LINK_STORAGE) {
            deactivate(null, false);
            return;
        }
        hover = hoveredPos == null ? null : hoveredPos.immutable();
    }

    public boolean click(Minecraft minecraft, ClientRtsController controller, BlockPos clickedPos) {
        if (!active) {
            return false;
        }
        if (clickedPos == null) {
            show(minecraft, "message.rtsbuilding.storage_batch.no_target");
            return true;
        }
        if (phase == Phase.SELECT_FIRST) {
            first = clickedPos.immutable();
            phase = Phase.SELECT_SECOND;
            visualRevision++;
            show(minecraft, "message.rtsbuilding.storage_batch.first_selected");
            return true;
        }
        if (phase == Phase.SELECT_SECOND) {
            if (RtsBatchStorageSelectionBounds.normalize(first, clickedPos) == null) {
                show(minecraft, "message.rtsbuilding.storage_batch.too_large");
                return true;
            }
            second = clickedPos.immutable();
            phase = Phase.COMPLETE;
            visualRevision++;
            show(minecraft, "message.rtsbuilding.storage_batch.ready");
            return true;
        }
        return confirm(minecraft, controller);
    }

    public boolean confirm(Minecraft minecraft, ClientRtsController controller) {
        if (!active || phase != Phase.COMPLETE || first == null || second == null || controller == null) {
            return false;
        }
        if (RtsBatchStorageSelectionBounds.normalize(first, second) == null) {
            show(minecraft, "message.rtsbuilding.storage_batch.too_large");
            return true;
        }
        controller.linkStoragesInSelection(first, second, true);
        show(minecraft, "message.rtsbuilding.storage_batch.submitted");
        resetSelection();
        return true;
    }

    public boolean adjustHeight(Minecraft minecraft, double scrollY, boolean fast) {
        if (!active || phase != Phase.COMPLETE || first == null || second == null || scrollY == 0.0D) {
            return false;
        }
        int delta = scrollY > 0.0D ? 1 : -1;
        if (fast) {
            delta *= 4;
        }
        BlockPos adjusted = new BlockPos(second.getX(), second.getY() + delta, second.getZ());
        if (RtsBatchStorageSelectionBounds.normalize(first, adjusted) == null) {
            show(minecraft, "message.rtsbuilding.storage_batch.too_large");
            return true;
        }
        second = adjusted;
        visualRevision++;
        return true;
    }

    /** Esc 先清空当前框；空框状态下再次 Esc 才退出批量模式。 */
    public boolean cancelOrExit(Minecraft minecraft) {
        if (!active) {
            return false;
        }
        if (phase != Phase.SELECT_FIRST) {
            resetSelection();
            show(minecraft, "message.rtsbuilding.storage_batch.selection_cleared");
        } else {
            deactivate(minecraft, true);
        }
        return true;
    }

    public void deactivate(Minecraft minecraft, boolean notify) {
        if (!active) {
            return;
        }
        active = false;
        resetSelection();
        hover = null;
        if (notify) {
            show(minecraft, "message.rtsbuilding.storage_batch.exited");
        }
    }

    public SelectionBox selectionBox() {
        if (!active) {
            return null;
        }
        BlockPos start;
        BlockPos end;
        if (phase == Phase.SELECT_FIRST) {
            start = hover;
            end = hover;
        } else if (phase == Phase.SELECT_SECOND) {
            start = first;
            end = hover == null ? first : hover;
        } else {
            start = first;
            end = second;
        }
        if (start == null || end == null) {
            return null;
        }
        RtsBatchStorageSelectionBounds.Bounds bounds =
                RtsBatchStorageSelectionBounds.normalize(start, end);
        if (bounds == null) {
            return null;
        }
        return new SelectionBox(bounds.min(), bounds.max(), phase == Phase.COMPLETE, visualRevision);
    }

    private void resetSelection() {
        visualRevision++;
        phase = Phase.SELECT_FIRST;
        first = null;
        second = null;
    }

    private static void show(Minecraft minecraft, String key) {
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.sendSystemMessage(Component.translatable(key));
        }
    }

    public record SelectionBox(BlockPos min, BlockPos max, boolean complete, int visualRevision) {
    }
}
