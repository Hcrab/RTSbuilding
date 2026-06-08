package com.rtsbuilding.rtsbuilding.client.screen;

import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.screen.interaction.InteractionTypes;
import com.rtsbuilding.rtsbuilding.client.screen.shape.ShapeDataRecords;
import com.rtsbuilding.rtsbuilding.client.screen.shape.ShapeGeometryUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.*;

import static com.rtsbuilding.rtsbuilding.client.screen.BuilderScreenConstants.SHAPE_HISTORY_LIMIT;

/**
 * 管理 RTS 模式下方块放置/破坏的撤回/重做历史记录。
 * <p>
 * 记录每次操作的批次信息（位置、朝向、来源），
 * 提供 {@link #undo()} 和 {@link #redo()} 操作，
 * 支持固定的历史栈上限，在每次新记录时清除重做栈。
 * <p>
 * 同时支持两种操作类型：
 * <ul>
 *   <li>放置批次（isDestructive=false）：撤回=破坏方块，重做=重新放置</li>
 *   <li>破坏批次（isDestructive=true）：撤回=重新放置方块，重做=再次破坏</li>
 * </ul>
 */
public final class PlacementHistoryManager {

    /** 当前活跃实例，供网络处理器静态回调。 */
    private static PlacementHistoryManager INSTANCE = null;

    private BuilderScreen screen;
    private ClientRtsController controller;

    private final List<ShapeDataRecords.HistoryBatch> undoStack = new ArrayList<>();
    private final List<ShapeDataRecords.HistoryBatch> redoStack = new ArrayList<>();

    /** 待服务端确认的快速建造放置批次。 */
    private final List<PendingBatch> pendingBatches = new ArrayList<>();

    /** 待确认批次超时时长（毫秒）。 */
    private static final long PENDING_TIMEOUT_MS = 15000L;

    /**
     * 记录一个尚待服务端确认的放置批次。
     * <p>
     * 每个位置收到 {@link #confirmPlacement(BlockPos)} 后从待确认集合中移除，
     * 当所有位置确认后自动移入撤回栈。超时未完成的批次将被丢弃。
     *
     * @param blockStates 每个位置对应的方块注册名（与 positions 一一对应），
     *                    用于撤回/重做时恢复正确的方块类型
     */
    public void recordPendingBatch(InteractionTypes.PlacementReplayKind replayKind, String itemId, int toolSlot, Direction face, List<BlockPos> positions, List<String> blockStates) {
        if (positions == null || positions.isEmpty()) {
            return;
        }
        if (replayKind == InteractionTypes.PlacementReplayKind.PIN_ITEM && (itemId == null || itemId.isBlank())) {
            return;
        }
        List<String> resolvedStates = normalizeBlockStates(positions, blockStates);
        ShapeDataRecords.HistoryBatch batch = new ShapeDataRecords.HistoryBatch(
                replayKind,
                itemId == null ? "" : itemId,
                replayKind == InteractionTypes.PlacementReplayKind.PIN_ITEM ? -1 : Mth.clamp(toolSlot, 0, 8),
                face,
                List.copyOf(positions),
                false,
                resolvedStates);
        Set<Long> pendingSet = new HashSet<>(positions.size());
        for (BlockPos pos : positions) {
            pendingSet.add(pos.asLong());
        }
        this.pendingBatches.add(new PendingBatch(batch, pendingSet, System.currentTimeMillis()));
        // 新操作清空重做栈
        this.redoStack.clear();
    }

    /**
     * 通知一个方块已被服务端成功放置。
     * <p>
     * 从所有待确认批次的待确认集合中移除该位置，
     * 完全确认的批次自动移入撤回栈。
     *
     * @param pos 服务端确认放置的方块位置
     */
    public static void confirmPlacement(BlockPos pos) {
        PlacementHistoryManager instance = INSTANCE;
        if (instance == null || pos == null) {
            return;
        }
        long posKey = pos.asLong();
        Iterator<PendingBatch> it = instance.pendingBatches.iterator();
        boolean changed = false;
        while (it.hasNext()) {
            PendingBatch pb = it.next();
            if (pb.batchData.isDestructive()) {
                continue;
            }
            pb.pendingPositions.remove(posKey);
            if (pb.isComplete()) {
                it.remove();
                instance.undoStack.add(pb.batchData);
                if (instance.undoStack.size() > SHAPE_HISTORY_LIMIT) {
                    instance.undoStack.removeFirst();
                }
                instance.saveToDisk();
                changed = true;
            }
        }
    }

    /**
     * 初始化管理器，绑定所属 Screen 和 Controller。
     * <p>
     * 必须在使用任何其他方法前调用。
     */
    public void init(BuilderScreen screen, ClientRtsController controller) {
        this.screen = screen;
        this.controller = controller;
        INSTANCE = this;
        loadFromDisk();
    }

    // ===== 状态查询 =====

    /** 当前可撤回的步数（不计待确认批次）。 */
    public int getUndoSize() {
        cleanStalePendingBatches();
        return this.undoStack.size();
    }

    /** 当前可重做的步数。 */
    public int getRedoSize() {
        cleanStalePendingBatches();
        return this.redoStack.size();
    }

    // ===== 撤回 / 重做 =====

    /**
     * 撤销最后一次操作（放置或破坏）。
     * <p>
     * 从撤回栈弹出最近一批记录：
     * <ul>
     *   <li>放置批次：反向遍历调用 breakPlaced 移除每个方块</li>
     *   <li>破坏批次：使用当前选中物品重新放置方块</li>
     * </ul>
     * 然后将该批记录压入重做栈。
     *
     * @return 如果存在可撤回的记录则返回 true
     */
    public boolean undo() {
        cleanStalePendingBatches();
        if (this.undoStack.isEmpty()) {
            return false;
        }
        ShapeDataRecords.HistoryBatch batch = this.undoStack.removeLast();
        List<BlockPos> positions = batch.positions();

        if (batch.isDestructive()) {
            // 破坏批次 → 撤回 = 重新放置方块
            undoBreak(batch, positions);
        } else {
            // 放置批次 → 撤回 = 破坏方块
            undoPlacement(batch, positions);
        }

        this.redoStack.add(batch);
        if (this.redoStack.size() > SHAPE_HISTORY_LIMIT) {
            this.redoStack.removeFirst();
        }
        saveToDisk();
        return true;
    }

    /** 撤回放置操作：破坏所有放置的方块。 */
    private void undoPlacement(ShapeDataRecords.HistoryBatch batch, List<BlockPos> positions) {
        for (int i = positions.size() - 1; i >= 0; i--) {
            this.controller.breakPlaced(positions.get(i), batch.face(), true);
        }
    }

    /** 撤回破坏操作：根据记录的方块类型重新放置对应方块。 */
    private void undoBreak(ShapeDataRecords.HistoryBatch batch, List<BlockPos> positions) {
        Minecraft mc = this.screen.getMinecraft();
        if (mc == null || mc.player == null) {
            return;
        }

        List<String> blockStates = batch.blockStates();
        if (blockStates == null || blockStates.isEmpty()) {
            return; // 没有方块类型信息，无法恢复
        }

        Vec3 rayOrigin = mc.gameRenderer.getMainCamera().getPosition();
        Vec3 rayDir = this.screen.computeCursorRayDirection();

        // 按方块注册名分组，同类方块一起放置
        Map<String, List<BlockPos>> byType = new LinkedHashMap<>();
        for (int i = 0; i < positions.size() && i < blockStates.size(); i++) {
            String stateId = blockStates.get(i);
            if (stateId == null || stateId.isBlank()) continue;
            byType.computeIfAbsent(stateId, k -> new ArrayList<>()).add(positions.get(i));
        }

        if (byType.isEmpty()) {
            return;
        }

        // 保存当前选择状态以便后续恢复
        String savedItemId = this.controller.getSelectedItemId();
        String savedItemLabel = this.controller.getSelectedItemLabel();
        ItemStack savedItemPreview = this.controller.getSelectedItemPreview();
        int savedToolSlot = mc.player.getInventory().selected;
        boolean hadItemSelected = !savedItemId.isBlank();

        // 清除当前选择，开始按方块类型放置
        this.controller.clearPlacementSelectionPreserveMode();

        try {
            for (Map.Entry<String, List<BlockPos>> entry : byType.entrySet()) {
                String blockRegistryName = entry.getKey();
                List<BlockPos> posList = entry.getValue();

                // 从方块注册名构造对应的物品
                ResourceLocation blockId = ResourceLocation.tryParse(blockRegistryName);
                if (blockId == null || !BuiltInRegistries.BLOCK.containsKey(blockId)) continue;
                Block block = BuiltInRegistries.BLOCK.get(blockId);
                ItemStack itemStack = new ItemStack(block);
                if (itemStack.isEmpty()) continue;

                String itemId = BuiltInRegistries.ITEM.getKey(itemStack.getItem()).toString();
                String label = itemStack.getHoverName().getString();

                // 优先使用快捷栏中的物品
                int hotbarSlot = findInventorySlotContaining(mc.player, itemId, true);
                if (hotbarSlot >= 0) {
                    // 快捷栏有此物品，切换到对应槽位并使用工具槽模式
                    this.screen.setSelectedToolSlot(hotbarSlot);
                    this.controller.clearPlacementSelectionPreserveMode();
                } else {
                    // 使用钉选物品模式（从 RTS 存储系统获取）
                    this.controller.selectItemForPlacement(itemId, label, itemStack);
                }

                // 构建放置命中结果
                List<BlockHitResult> hits = new ArrayList<>(posList.size());
                for (BlockPos pos : posList) {
                    hits.add(ShapeGeometryUtil.createShapePlacementHit(pos, batch.face()));
                }
                this.controller.placeSelectedBatch(hits, false, rayOrigin, rayDir, false);
            }
        } finally {
            // 恢复原始选择状态
            this.controller.clearPlacementSelectionPreserveMode();
            if (hadItemSelected) {
                this.controller.selectItemForPlacement(savedItemId, savedItemLabel, savedItemPreview);
            }
            this.screen.setSelectedToolSlot(savedToolSlot);
        }
    }

    /**
     * 在玩家背包中查找包含指定物品 ID 的槽位。
     *
     * @param player     玩家
     * @param itemId     物品注册名
     * @param hotbarOnly 是否仅搜索快捷栏（0-8）
     * @return 槽位索引（0-8 快捷栏，9-35 主背包），-1 表示未找到
     */
    private static int findInventorySlotContaining(net.minecraft.world.entity.player.Player player, String itemId, boolean hotbarOnly) {
        if (player == null || itemId == null || itemId.isBlank()) return -1;
        int limit = hotbarOnly ? 9 : 36;
        for (int i = 0; i < limit; i++) {
            ItemStack stack = player.getInventory().items.get(i);
            if (!stack.isEmpty()) {
                ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
                if (id != null && id.toString().equals(itemId)) {
                    return i;
                }
            }
        }
        return -1;
    }

    /**
     * 重做上一次被撤销的操作（放置或破坏）。
     * <p>
     * 从重做栈弹出最近一批记录：
     * <ul>
     *   <li>放置批次：验证来源有效性后重新放置方块</li>
     *   <li>破坏批次：直接破坏方块</li>
     * </ul>
     * 然后将该批记录压回撤回栈。
     * 操作后自动持久化到磁盘以支持崩溃恢复。
     *
     * @return 如果存在可重做的记录且条件满足则返回 true
     */
    public boolean redo() {
        cleanStalePendingBatches();
        if (this.redoStack.isEmpty()) {
            return false;
        }
        int idx = this.redoStack.size() - 1;
        ShapeDataRecords.HistoryBatch batch = this.redoStack.get(idx);

        if (batch.isDestructive()) {
            // 破坏批次 → 重做 = 再次破坏方块
            return redoBreak(batch, idx);
        } else {
            // 放置批次 → 重做 = 重新放置方块
            return redoPlacement(batch, idx);
        }
    }

    /** 重做放置操作：重新放置所有方块。 */
    private boolean redoPlacement(ShapeDataRecords.HistoryBatch batch, int idx) {
        Minecraft mc = this.screen.getMinecraft();
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
        saveToDisk();
        return true;
    }

    /** 重做破坏操作：再次破坏所有方块。 */
    private boolean redoBreak(ShapeDataRecords.HistoryBatch batch, int idx) {
        this.redoStack.remove(idx);
        List<BlockPos> positions = batch.positions();
        for (int i = positions.size() - 1; i >= 0; i--) {
            this.controller.breakPlaced(positions.get(i), batch.face(), true);
        }
        this.undoStack.add(batch);
        if (this.undoStack.size() > SHAPE_HISTORY_LIMIT) {
            this.undoStack.removeFirst();
        }
        saveToDisk();
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
        // 解析实际放置位置：如果点击位置的方块可替换则放置在原地，否则放置在点击面的相邻位置
        BlockPos clickedPos = hit.getBlockPos().immutable();
        Direction face = hit.getDirection();
        Minecraft mc = Minecraft.getInstance();
        BlockPos placedPos = clickedPos.relative(face);
        if (mc.level != null && mc.level.hasChunkAt(clickedPos) && mc.level.getBlockState(clickedPos).canBeReplaced()) {
            placedPos = clickedPos;
        }
        // 记录当前放置的方块类型
        String blockStateId = resolveBlockStateFromSlot(mc, replayKind, itemId, toolSlot);
        recordBatch(replayKind, itemId, toolSlot, face, List.of(placedPos), List.of(blockStateId));
    }

    /**
     * 记录一批方块操作到撤回栈（5 参数便捷方法，默认 isDestructive=false）。
     * <p>
     * 如果 {@code replayKind} 为 {@code PIN_ITEM} 但 {@code itemId} 为空，
     * 则不记录（无法恢复放置来源）。
     * 每次新记录会清空重做栈。
     * 记录后自动持久化到磁盘以支持崩溃恢复。
     *
     * @param replayKind 放置来源类型
     * @param itemId     钉选物品 ID（非钉选时传空字符串）
     * @param toolSlot   快捷栏槽位（0-8，钉选时传 -1）
     * @param face       所有位置共同的操作面
     * @param positions  操作的方块位置列表
     */
    /**
     * 记录一批方块操作到撤回栈（5 参数便捷方法，默认 isDestructive=false，blockStates 为空）。
     */
    public void recordBatch(InteractionTypes.PlacementReplayKind replayKind, String itemId, int toolSlot, Direction face, List<BlockPos> positions) {
        recordBatch(replayKind, itemId, toolSlot, face, positions, false, List.of());
    }

    /**
     * 记录一批方块操作到撤回栈（6 参数便捷方法，默认 isDestructive=false）。
     */
    public void recordBatch(InteractionTypes.PlacementReplayKind replayKind, String itemId, int toolSlot, Direction face, List<BlockPos> positions, List<String> blockStates) {
        recordBatch(replayKind, itemId, toolSlot, face, positions, false, blockStates);
    }

    /**
     * 记录一批方块操作到撤回栈。
     * <p>
     * 如果 {@code replayKind} 为 {@code PIN_ITEM} 但 {@code itemId} 为空，
     * 则不记录（无法恢复放置来源）。
     * 每次新记录会清空重做栈。
     * 记录后自动持久化到磁盘以支持崩溃恢复。
     *
     * @param replayKind   操作来源类型
     * @param itemId       钉选物品 ID（非钉选时传空字符串）
     * @param toolSlot     快捷栏槽位（0-8，钉选时传 -1）
     * @param face         所有位置共同的操作面
     * @param positions    操作的方块位置列表
     * @param isDestructive true 表示破坏操作，false 表示放置操作
     * @param blockStates  每个位置对应的方块注册名（与 positions 一一对应），
     *                     用于撤回/重做时恢复正确的方块类型
     */
    public void recordBatch(InteractionTypes.PlacementReplayKind replayKind, String itemId, int toolSlot, Direction face, List<BlockPos> positions, boolean isDestructive, List<String> blockStates) {
        if (positions == null || positions.isEmpty()) {
            return;
        }
        if (replayKind == InteractionTypes.PlacementReplayKind.PIN_ITEM && (itemId == null || itemId.isBlank())) {
            return;
        }
        cleanStalePendingBatches();
        List<String> resolvedStates = normalizeBlockStates(positions, blockStates);
        ShapeDataRecords.HistoryBatch batch = new ShapeDataRecords.HistoryBatch(
                replayKind,
                itemId == null ? "" : itemId,
                replayKind == InteractionTypes.PlacementReplayKind.PIN_ITEM ? -1 : Mth.clamp(toolSlot, 0, 8),
                face,
                List.copyOf(positions),
                isDestructive,
                resolvedStates);
        this.undoStack.add(batch);
        if (this.undoStack.size() > SHAPE_HISTORY_LIMIT) {
            this.undoStack.removeFirst();
        }
        this.redoStack.clear();
        saveToDisk();
    }

    // ===== 破坏记录 =====

    /**
     * 记录一个尚待服务端确认的破坏批次（范围破坏）。
     * <p>
     * 每个位置收到 {@link #confirmBreak(BlockPos)} 后从待确认集合中移除，
     * 当所有位置确认后自动移入撤回栈。超时未完成的批次将被丢弃。
     *
     * @param face      点击的面
     * @param toolSlot  当前选中的快捷栏槽位
     * @param positions 被破坏的方块位置列表
     */
    public void recordPendingBreak(Direction face, int toolSlot, List<BlockPos> positions) {
        if (positions == null || positions.isEmpty()) {
            return;
        }
        Direction resolvedFace = face == null ? Direction.UP : face;
        int slot = Mth.clamp(toolSlot, 0, 8);
        // 在方块被破坏前捕获方块类型（即时查询世界状态）
        List<String> blockStates = captureBlockStatesFromLevel(positions);
        ShapeDataRecords.HistoryBatch batch = new ShapeDataRecords.HistoryBatch(
                InteractionTypes.PlacementReplayKind.TOOL_SLOT,
                "",
                slot,
                resolvedFace,
                List.copyOf(positions),
                true,
                blockStates);
        Set<Long> pendingSet = new HashSet<>(positions.size());
        for (BlockPos pos : positions) {
            pendingSet.add(pos.asLong());
        }
        this.pendingBatches.add(new PendingBatch(batch, pendingSet, System.currentTimeMillis()));
        // 新操作清空重做栈
        this.redoStack.clear();
    }

    /**
     * 通知一个方块已被服务端成功破坏。
     * <p>
     * 从所有待确认的破坏批次的待确认集合中移除该位置，
     * 完全确认的批次自动移入撤回栈。
     *
     * @param pos 服务端确认破坏的方块位置
     */
    public static void confirmBreak(BlockPos pos) {
        PlacementHistoryManager instance = INSTANCE;
        if (instance == null || pos == null) {
            return;
        }
        long posKey = pos.asLong();
        Iterator<PendingBatch> it = instance.pendingBatches.iterator();
        boolean changed = false;
        while (it.hasNext()) {
            PendingBatch pb = it.next();
            // 只匹配破坏批次（isDestructive=true）
            if (!pb.batchData.isDestructive()) {
                continue;
            }
            pb.pendingPositions.remove(posKey);
            if (pb.isComplete()) {
                it.remove();
                instance.undoStack.add(pb.batchData);
                if (instance.undoStack.size() > SHAPE_HISTORY_LIMIT) {
                    instance.undoStack.removeFirst();
                }
                instance.saveToDisk();
                changed = true;
            }
        }
    }

    /**
     * 记录一次方块破坏操作到撤回栈（即时记录，不等待服务端确认）。
     * <p>
     * 用于范围破坏（Range Destroy）等即时破坏操作。
     * 撤回时将使用当前选中物品重新放置方块，
     * 重做时将再次破坏方块。
     *
     * @param positions 被破坏的方块位置列表
     * @param face      点击的面
     * @param toolSlot  当前选中的快捷栏槽位
     */
    public void recordBreak(List<BlockPos> positions, Direction face, int toolSlot) {
        if (positions == null || positions.isEmpty()) {
            return;
        }
        Direction resolvedFace = face == null ? Direction.UP : face;
        int slot = Mth.clamp(toolSlot, 0, 8);
        List<String> blockStates = captureBlockStatesFromLevel(positions);
        recordBatch(InteractionTypes.PlacementReplayKind.TOOL_SLOT, "", slot, resolvedFace, positions, true, blockStates);
    }

    // ===== 磁盘持久化 =====

    /**
     * 将当前撤回/重做栈持久化到磁盘 JSON 文件。
     * <p>
     * 用于崩溃恢复：当游戏异常退出时，下次进入 RTS 模式可还原历史操作。
     */
    public void saveToDisk() {
        PlacementHistoryStore.save(this.undoStack, this.redoStack);
    }

    /**
     * 从磁盘 JSON 文件加载之前的撤回/重做栈。
     * <p>
     * 仅在 {@link #init(BuilderScreen, ClientRtsController)} 被调用时
     * 自动触发。加载前会校验世界上下文是否匹配。
     */
    public void loadFromDisk() {
        PlacementHistoryStore.LoadResult result = PlacementHistoryStore.load();
        if (result.isEmpty()) {
            return;
        }
        this.undoStack.clear();
        this.redoStack.clear();
        this.undoStack.addAll(result.undoStack);
        this.redoStack.addAll(result.redoStack);
    }

    // ===== 生命周期 =====

    /** 清空所有撤回和重做历史记录，同时删除磁盘文件。 */
    public void clear() {
        this.undoStack.clear();
        this.redoStack.clear();
        this.pendingBatches.clear();
        PlacementHistoryStore.clear();
        INSTANCE = null;
    }

    // ===== 待确认批次管理 =====

    /**
     * 一个等待服务端确认的批次。
     */
    private record PendingBatch(
            ShapeDataRecords.HistoryBatch batchData,
            Set<Long> pendingPositions,
            long createdAtMs
    ) {
        boolean isComplete() {
            return pendingPositions.isEmpty();
        }
    }

    /** 清理超时的待确认批次。 */
    private void cleanStalePendingBatches() {
        long now = System.currentTimeMillis();
        Iterator<PendingBatch> it = this.pendingBatches.iterator();
        while (it.hasNext()) {
            if (now - it.next().createdAtMs > PENDING_TIMEOUT_MS) {
                it.remove();
            }
        }
    }

    // ===== 方块类型捕获 =====

    /**
     * 从世界中查询每个位置的方块注册名。
     * <p>
     * 用于破坏操作在方块被真正破坏前记录其类型。
     */
    private static List<String> captureBlockStatesFromLevel(List<BlockPos> positions) {
        Minecraft mc = Minecraft.getInstance();
        List<String> states = new ArrayList<>(positions.size());
        if (mc.level == null) {
            for (BlockPos ignored : positions) {
                states.add("");
            }
            return states;
        }
        for (BlockPos pos : positions) {
            if (mc.level.hasChunkAt(pos)) {
                BlockState state = mc.level.getBlockState(pos);
                states.add(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
            } else {
                states.add("");
            }
        }
        return states;
    }

    /**
     * 从快捷栏或钉选物品解析方块注册名。
     * <p>
     * 用于单格放置操作在放置时记录方块类型。
     */
    private static String resolveBlockStateFromSlot(Minecraft mc, InteractionTypes.PlacementReplayKind replayKind, String itemId, int toolSlot) {
        if (mc.player == null) return "";
        ItemStack stack;
        if (replayKind == InteractionTypes.PlacementReplayKind.PIN_ITEM) {
            // 钉选模式下从注册名解析
            if (itemId == null || itemId.isBlank()) return "";
            var item = BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(itemId));
            if (item.isEmpty() || !(item.get() instanceof BlockItem blockItem)) return "";
            return BuiltInRegistries.BLOCK.getKey(blockItem.getBlock()).toString();
        } else {
            if (toolSlot < 0 || toolSlot >= 9) return "";
            stack = mc.player.getInventory().getItem(toolSlot);
            if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem blockItem)) return "";
            return BuiltInRegistries.BLOCK.getKey(blockItem.getBlock()).toString();
        }
    }

    /**
     * 规范化 blockStates：如果传入的列表为空或尺寸不匹配，用空字符串填充。
     */
    private static List<String> normalizeBlockStates(List<BlockPos> positions, List<String> blockStates) {
        if (blockStates != null && blockStates.size() == positions.size()) {
            return List.copyOf(blockStates);
        }
        return positions.stream().map(p -> "").toList();
    }
}
