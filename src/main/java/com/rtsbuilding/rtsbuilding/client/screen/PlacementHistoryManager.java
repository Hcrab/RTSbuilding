package com.rtsbuilding.rtsbuilding.client.screen;

import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.screen.interaction.InteractionTypes;
import com.rtsbuilding.rtsbuilding.client.screen.shape.ShapeDataRecords;
import com.rtsbuilding.rtsbuilding.client.screen.shape.ShapeGeometryUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

import static com.rtsbuilding.rtsbuilding.client.screen.BuilderScreenConstants.SHAPE_HISTORY_LIMIT;

/**
 * 管理 RTS 模式下方块放置的撤回/重做历史记录。
 * <p>
 * 记录每次放置操作的批次信息（位置、朝向、来源），
 * 提供 {@link #undo()} 和 {@link #redo()} 操作，
 * 支持固定的历史栈上限，在每次新记录时清除重做栈。
 */
public final class PlacementHistoryManager {

    private BuilderScreen screen;
    private ClientRtsController controller;

    private final List<ShapeDataRecords.HistoryBatch> undoStack = new ArrayList<>();
    private final List<ShapeDataRecords.HistoryBatch> redoStack = new ArrayList<>();

    /**
     * 初始化管理器，绑定所属 Screen 和 Controller。
     * <p>
     * 必须在使用任何其他方法前调用。
     */
    public void init(BuilderScreen screen, ClientRtsController controller) {
        this.screen = screen;
        this.controller = controller;
    }

    // ===== 状态查询 =====

    /** 当前可撤回的步数。 */
    public int getUndoSize() {
        return this.undoStack.size();
    }

    /** 当前可重做的步数。 */
    public int getRedoSize() {
        return this.redoStack.size();
    }

    // ===== 撤回 / 重做 =====

    /**
     * 撤销最后一次放置操作。
     * <p>
     * 从撤回栈弹出最近一批记录，反向遍历调用 breakPlaced 移除每个方块，
     * 然后将该批记录压入重做栈。
     *
     * @return 如果存在可撤回的记录则返回 true
     */
    public boolean undo() {
        if (this.undoStack.isEmpty()) {
            return false;
        }
        ShapeDataRecords.HistoryBatch batch = this.undoStack.removeLast();
        List<BlockPos> positions = batch.positions();
        for (int i = positions.size() - 1; i >= 0; i--) {
            this.controller.breakPlaced(positions.get(i), batch.face(), true);
        }
        this.redoStack.add(batch);
        if (this.redoStack.size() > SHAPE_HISTORY_LIMIT) {
            this.redoStack.removeFirst();
        }
        return true;
    }

    /**
     * 重做上一次被撤销的放置操作。
     * <p>
     * 从重做栈弹出最近一批记录，验证放置来源（钉选物品或快捷栏槽位）仍然有效，
     * 然后重新放置所有方块并将该批记录压回撤回栈。
     *
     * @return 如果存在可重做的记录且条件满足则返回 true
     */
    public boolean redo() {
        if (this.redoStack.isEmpty()) {
            return false;
        }
        Minecraft mc = this.screen.getMinecraft();
        int idx = this.redoStack.size() - 1;
        ShapeDataRecords.HistoryBatch batch = this.redoStack.get(idx);
        if (batch.replayKind() == InteractionTypes.PlacementReplayKind.PIN_ITEM) {
            if (!this.controller.hasSelectedItem() || !batch.itemId().equals(this.controller.getSelectedItemId())) {
                return false;
            }
        } else {
            if (mc.player == null) {
                return false;
            }
            this.controller.clearPlacementSelectionPreserveMode();
            this.screen.setSelectedToolSlot(batch.toolSlot());
        }
        this.redoStack.remove(idx);
        Vec3 rayOrigin = mc.gameRenderer.getMainCamera().getPosition();
        Vec3 rayDir = this.screen.computeCursorRayDirection();
        List<BlockHitResult> hits = new ArrayList<>(batch.positions().size());
        for (BlockPos pos : batch.positions()) {
            hits.add(ShapeGeometryUtil.createShapePlacementHit(pos, batch.face()));
        }
        this.controller.placeSelectedBatch(hits, false, rayOrigin, rayDir, false);
        this.undoStack.add(batch);
        if (this.undoStack.size() > SHAPE_HISTORY_LIMIT) {
            this.undoStack.removeFirst();
        }
        return true;
    }

    // ===== 记录方法 =====

    /**
     * 记录单次方块放置到撤回栈。
     *
     * @param hit       放置的碰撞结果
     * @param replayKind 放置来源类型
     * @param itemId     钉选物品 ID（非钉选时传空字符串）
     * @param toolSlot   快捷栏槽位（0-8，钉选时传 -1）
     */
    public void recordSinglePlacement(BlockHitResult hit, InteractionTypes.PlacementReplayKind replayKind, String itemId, int toolSlot) {
        if (hit == null) {
            return;
        }
        recordBatch(replayKind, itemId, toolSlot, hit.getDirection(), List.of(hit.getBlockPos().immutable()));
    }

    /**
     * 记录一批方块放置到撤回栈。
     * <p>
     * 如果 {@code replayKind} 为 {@code PIN_ITEM} 但 {@code itemId} 为空，
     * 则不记录（无法恢复放置来源）。
     * 每次新记录会清空重做栈。
     *
     * @param replayKind 放置来源类型
     * @param itemId     钉选物品 ID（非钉选时传空字符串）
     * @param toolSlot   快捷栏槽位（0-8，钉选时传 -1）
     * @param face       所有位置共同的放置面
     * @param positions  放置的方块位置列表
     */
    public void recordBatch(InteractionTypes.PlacementReplayKind replayKind, String itemId, int toolSlot, Direction face, List<BlockPos> positions) {
        if (positions == null || positions.isEmpty()) {
            return;
        }
        if (replayKind == InteractionTypes.PlacementReplayKind.PIN_ITEM && (itemId == null || itemId.isBlank())) {
            return;
        }
        ShapeDataRecords.HistoryBatch batch = new ShapeDataRecords.HistoryBatch(
                replayKind,
                itemId == null ? "" : itemId,
                Mth.clamp(toolSlot, 0, 8),
                face,
                List.copyOf(positions));
        this.undoStack.add(batch);
        if (this.undoStack.size() > SHAPE_HISTORY_LIMIT) {
            this.undoStack.removeFirst();
        }
        this.redoStack.clear();
    }

    // ===== 生命周期 =====

    /** 清空所有撤回和重做历史记录。 */
    public void clear() {
        this.undoStack.clear();
        this.redoStack.clear();
    }
}
