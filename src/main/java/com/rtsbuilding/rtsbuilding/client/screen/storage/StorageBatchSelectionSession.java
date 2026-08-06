package com.rtsbuilding.rtsbuilding.client.screen.storage;

import com.rtsbuilding.rtsbuilding.client.network.RtsClientPacketGateway;
import com.rtsbuilding.rtsbuilding.common.build.BuilderMode;
import com.rtsbuilding.rtsbuilding.common.storage.RtsBatchStorageSelectionBounds;
import net.minecraft.client.Minecraft;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;

/**
 * Link Storage 的批量框选会话。
 *
 * <p>此类只保存玩家的两次点选和本地预览；服务端收到的始终只是两个角点，并在确认时重新
 * 扫描已加载端点、检查权限和存储网络身份。确认后会话保持开启，方便连续框选；Ctrl 可开关，
 * Esc 先清空当前框选、再次按下才退出。</p>
 */
public final class StorageBatchSelectionSession {
    public enum Phase { SELECT_FIRST, SELECT_SECOND, COMPLETE }

    private boolean active;
    private Phase phase = Phase.SELECT_FIRST;
    private BlockPos first;
    private BlockPos second;
    private BlockPos hover;
    private int visualRevision;

    public boolean isActive() { return this.active; }
    public Phase phase() { return this.phase; }

    public void toggle(Minecraft minecraft, BuilderMode mode) {
        if (this.active) {
            deactivate(minecraft, true);
            return;
        }
        if (mode != BuilderMode.LINK_STORAGE) return;
        this.active = true;
        resetSelection();
        show(minecraft, "message.rtsbuilding.storage_batch.entered");
    }

    public void update(BuilderMode mode, BlockPos hoveredPos) {
        if (mode != BuilderMode.LINK_STORAGE) {
            deactivate(null, false);
            return;
        }
        this.hover = hoveredPos == null ? null : hoveredPos.toImmutable();
    }

    public boolean click(Minecraft minecraft, BlockPos clickedPos) {
        if (!this.active) return false;
        if (clickedPos == null) {
            show(minecraft, "message.rtsbuilding.storage_batch.no_target");
            return true;
        }
        if (this.phase == Phase.SELECT_FIRST) {
            this.first = clickedPos.toImmutable();
            this.phase = Phase.SELECT_SECOND;
            show(minecraft, "message.rtsbuilding.storage_batch.first_selected");
            return true;
        }
        if (this.phase == Phase.SELECT_SECOND) {
            if (RtsBatchStorageSelectionBounds.normalize(this.first, clickedPos) == null) {
                show(minecraft, "message.rtsbuilding.storage_batch.too_large");
                return true;
            }
            this.second = clickedPos.toImmutable();
            this.phase = Phase.COMPLETE;
            show(minecraft, "message.rtsbuilding.storage_batch.ready");
            return true;
        }
        return confirm(minecraft);
    }

    public boolean confirm(Minecraft minecraft) {
        if (!this.active || this.phase != Phase.COMPLETE || this.first == null || this.second == null) {
            return false;
        }
        if (RtsBatchStorageSelectionBounds.normalize(this.first, this.second) == null) {
            show(minecraft, "message.rtsbuilding.storage_batch.too_large");
            return true;
        }
        RtsClientPacketGateway.sendBatchLinkStorage(this.first, this.second, true);
        show(minecraft, "message.rtsbuilding.storage_batch.submitted");
        resetSelection();
        return true;
    }

    public boolean adjustHeight(Minecraft minecraft, double scrollY, boolean fast) {
        if (!this.active || this.phase != Phase.COMPLETE || this.first == null || this.second == null
                || scrollY == 0.0D) return false;
        int delta = scrollY > 0.0D ? 1 : -1;
        if (fast) delta *= 4;
        BlockPos adjusted = new BlockPos(this.second.getX(), this.second.getY() + delta, this.second.getZ());
        if (RtsBatchStorageSelectionBounds.normalize(this.first, adjusted) == null) {
            show(minecraft, "message.rtsbuilding.storage_batch.too_large");
            return true;
        }
        this.second = adjusted;
        return true;
    }

    public boolean cancelOrExit(Minecraft minecraft) {
        if (!this.active) return false;
        if (this.phase != Phase.SELECT_FIRST) {
            resetSelection();
            show(minecraft, "message.rtsbuilding.storage_batch.selection_cleared");
        } else {
            deactivate(minecraft, true);
        }
        return true;
    }

    public void deactivate(Minecraft minecraft, boolean notify) {
        if (!this.active) return;
        this.active = false;
        resetSelection();
        this.hover = null;
        if (notify) show(minecraft, "message.rtsbuilding.storage_batch.exited");
    }

    public SelectionBox selectionBox() {
        if (!this.active) return null;
        BlockPos start;
        BlockPos end;
        if (this.phase == Phase.SELECT_FIRST) {
            start = this.hover;
            end = this.hover;
        } else if (this.phase == Phase.SELECT_SECOND) {
            start = this.first;
            end = this.hover == null ? this.first : this.hover;
        } else {
            start = this.first;
            end = this.second;
        }
        RtsBatchStorageSelectionBounds.Bounds bounds =
                RtsBatchStorageSelectionBounds.normalize(start, end);
        return bounds == null ? null : new SelectionBox(bounds.min(), bounds.max(),
                this.phase == Phase.COMPLETE, this.visualRevision);
    }

    private void resetSelection() {
        this.visualRevision++;
        this.phase = Phase.SELECT_FIRST;
        this.first = null;
        this.second = null;
    }

    private static void show(Minecraft minecraft, String key) {
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.sendStatusMessage(new TextComponentTranslation(key), true);
        }
    }

    public static final class SelectionBox {
        private final BlockPos min;
        private final BlockPos max;
        private final boolean complete;
        private final int visualRevision;

        private SelectionBox(BlockPos min, BlockPos max, boolean complete, int visualRevision) {
            this.min = min.toImmutable();
            this.max = max.toImmutable();
            this.complete = complete;
            this.visualRevision = visualRevision;
        }
        public BlockPos min() { return this.min; }
        public BlockPos max() { return this.max; }
        public boolean complete() { return this.complete; }
        public int visualRevision() { return this.visualRevision; }
    }
}
