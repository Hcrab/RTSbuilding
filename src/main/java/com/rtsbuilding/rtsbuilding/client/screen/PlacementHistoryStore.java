package com.rtsbuilding.rtsbuilding.client.screen;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.rtsbuilding.rtsbuilding.client.screen.interaction.InteractionTypes;
import com.rtsbuilding.rtsbuilding.client.screen.shape.ShapeDataRecords;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.core.Direction;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 放置操作历史的 JSON 持久化工具。
 * <p>
 * 每次新放置/撤回/重做后自动保存到磁盘，用于崩溃恢复。
 * 当玩家退出 RTS 模式时由控制器调用 {@link #clear()} 清除文件。
 * 最多支持 64 次撤回操作（由 {@link BuilderScreenConstants#SHAPE_HISTORY_LIMIT} 控制）。
 * <p>
 * 文件路径：{@code config/rts_building/placement_history.json}
 */
public final class PlacementHistoryStore {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path STORE_PATH = FMLPaths.CONFIGDIR.get()
            .resolve("rts_building")
            .resolve("placement_history.json");

    private PlacementHistoryStore() {
    }

    // ======================================================================
    //  公开 API
    // ======================================================================

    /**
     * 将撤回/重做栈持久化到 JSON 文件。
     * <p>
     * 写入前会校验 undoStack 尺寸不超过 {@code SHAPE_HISTORY_LIMIT}，
     * 超出部分被裁剪（保留最近的记录）。
     * 文件中会附带世界上下文标识，用于加载时的有效性校验。
     *
     * @param undoStack 撤回栈
     * @param redoStack 重做栈（允许为空）
     */
    public static synchronized void save(List<ShapeDataRecords.HistoryBatch> undoStack,
                                         List<ShapeDataRecords.HistoryBatch> redoStack) {
        String context = resolveWorldContext();
        if (context == null || context.isBlank()) {
            return;
        }
        try {
            StoredHistory history = new StoredHistory();
            history.worldContext = context;

            // 裁剪 undoStack 到限制以内
            int limit = BuilderScreenConstants.SHAPE_HISTORY_LIMIT;
            List<ShapeDataRecords.HistoryBatch> srcUndo = undoStack;
            if (srcUndo.size() > limit) {
                srcUndo = srcUndo.subList(srcUndo.size() - limit, srcUndo.size());
            }
            for (ShapeDataRecords.HistoryBatch batch : srcUndo) {
                history.undoStack.add(toStored(batch));
            }
            for (ShapeDataRecords.HistoryBatch batch : redoStack) {
                history.redoStack.add(toStored(batch));
            }

            Files.createDirectories(STORE_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(STORE_PATH)) {
                GSON.toJson(history, writer);
            }
        } catch (IOException ignored) {
            // 写入失败静默忽略
        }
    }

    /**
     * 从 JSON 文件加载历史记录。
     * <p>
     * 如果文件不存在、格式损坏或世界上下文不匹配，返回空结果。
     *
     * @return 加载结果，包含撤回栈和重做栈；绝不会为 null
     */
    public static synchronized LoadResult load() {
        if (!Files.isRegularFile(STORE_PATH)) {
            return LoadResult.EMPTY;
        }
        String currentContext = resolveWorldContext();
        if (currentContext == null || currentContext.isBlank()) {
            return LoadResult.EMPTY;
        }
        try (Reader reader = Files.newBufferedReader(STORE_PATH)) {
            StoredHistory history = GSON.fromJson(reader, StoredHistory.class);
            if (history == null || history.undoStack == null) {
                return LoadResult.EMPTY;
            }
            // 校验世界上下文是否匹配
            if (!currentContext.equals(history.worldContext)) {
                return LoadResult.EMPTY;
            }
            List<ShapeDataRecords.HistoryBatch> undo = new ArrayList<>();
            for (StoredBatch stored : history.undoStack) {
                ShapeDataRecords.HistoryBatch batch = fromStored(stored);
                if (batch != null) {
                    undo.add(batch);
                }
            }
            List<ShapeDataRecords.HistoryBatch> redo = new ArrayList<>();
            if (history.redoStack != null) {
                for (StoredBatch stored : history.redoStack) {
                    ShapeDataRecords.HistoryBatch batch = fromStored(stored);
                    if (batch != null) {
                        redo.add(batch);
                    }
                }
            }
            return new LoadResult(undo, redo);
        } catch (IOException | RuntimeException ignored) {
            return LoadResult.EMPTY;
        }
    }

    /**
     * 删除持久化文件。
     * <p>
     * 在玩家退出 RTS 模式时调用，使下次进入时以空历史记录开始。
     */
    public static synchronized void clear() {
        try {
            Files.deleteIfExists(STORE_PATH);
        } catch (IOException ignored) {
        }
    }

    // ======================================================================
    //  序列化 / 反序列化
    // ======================================================================

    private static StoredBatch toStored(ShapeDataRecords.HistoryBatch batch) {
        List<String> posStrings = new ArrayList<>(batch.positions().size());
        for (var pos : batch.positions()) {
            posStrings.add(pos.getX() + "," + pos.getY() + "," + pos.getZ());
        }
        List<String> blockStateStrings = new ArrayList<>(batch.blockStates().size());
        for (var state : batch.blockStates()) {
            blockStateStrings.add(state == null ? "" : state);
        }
        return new StoredBatch(
                batch.replayKind().name(),
                batch.itemId(),
                batch.toolSlot(),
                batch.face().getName(),
                posStrings,
                batch.isDestructive(),
                blockStateStrings);
    }

    private static ShapeDataRecords.HistoryBatch fromStored(StoredBatch stored) {
        if (stored.replayKind == null || stored.face == null || stored.positions == null) {
            return null;
        }
        InteractionTypes.PlacementReplayKind replayKind;
        try {
            replayKind = InteractionTypes.PlacementReplayKind.valueOf(stored.replayKind);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
        Direction face = Direction.byName(stored.face);
        if (face == null) {
            return null;
        }
        List<net.minecraft.core.BlockPos> positions = new ArrayList<>(stored.positions.size());
        for (String posStr : stored.positions) {
            net.minecraft.core.BlockPos pos = parseBlockPos(posStr);
            if (pos != null) {
                positions.add(pos);
            }
        }
        if (positions.isEmpty()) {
            return null;
        }
        // 兼容旧数据：blockStates 可能为 null
        List<String> blockStates;
        if (stored.blockStates != null && stored.blockStates.size() == positions.size()) {
            blockStates = List.copyOf(stored.blockStates);
        } else {
            blockStates = positions.stream().map(p -> "").toList();
        }
        return new ShapeDataRecords.HistoryBatch(
                replayKind,
                stored.itemId == null ? "" : stored.itemId,
                stored.toolSlot,
                face,
                List.copyOf(positions),
                stored.isDestructive,
                blockStates);
    }

    private static net.minecraft.core.BlockPos parseBlockPos(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        String[] parts = s.split(",");
        if (parts.length != 3) {
            return null;
        }
        try {
            int x = Integer.parseInt(parts[0].trim());
            int y = Integer.parseInt(parts[1].trim());
            int z = Integer.parseInt(parts[2].trim());
            return new net.minecraft.core.BlockPos(x, y, z);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    // ======================================================================
    //  世界上下文
    // ======================================================================

    /**
     * 解析当前世界/服务器的唯一标识符，用于检测玩家是否切换到不同世界。
     * <ul>
     *   <li>单人模式：世界保存文件夹名 + 维度 ID</li>
     *   <li>多人模式：服务器 IP + 端口</li>
     * </ul>
     */
    private static String resolveWorldContext() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return null;
        }
        // 多人模式：使用服务器地址
        ServerData serverData = mc.getCurrentServer();
        if (serverData != null) {
            String serverIp = serverData.ip;
            if (serverIp != null && !serverIp.isBlank()) {
                return "mp|" + serverIp;
            }
        }
        // 单人模式：使用世界保存路径 + 维度
        if (mc.getSingleplayerServer() != null) {
            String worldName = mc.getSingleplayerServer()
                    .getWorldPath(LevelResource.ROOT)
                    .getFileName().toString();
            if (worldName != null && !worldName.isBlank()) {
                String dimension = mc.level.dimension().location().toString();
                return "sp|" + worldName + "|" + dimension;
            }
        }
        // 兜底：仅使用维度
        String dimension = mc.level.dimension().location().toString();
        return "sp|unknown|" + dimension;
    }

    // ======================================================================
    //  数据类
    // ======================================================================

    /** 加载历史记录的结果。 */
    public static final class LoadResult {
        public static final LoadResult EMPTY = new LoadResult(List.of(), List.of());

        public final List<ShapeDataRecords.HistoryBatch> undoStack;
        public final List<ShapeDataRecords.HistoryBatch> redoStack;

        public LoadResult(List<ShapeDataRecords.HistoryBatch> undoStack,
                          List<ShapeDataRecords.HistoryBatch> redoStack) {
            this.undoStack = undoStack;
            this.redoStack = redoStack;
        }

        public boolean isEmpty() {
            return undoStack.isEmpty() && redoStack.isEmpty();
        }
    }

    /** JSON 序列化的完整历史记录结构。 */
    private static final class StoredHistory {
        String worldContext = "";
        List<StoredBatch> undoStack = new ArrayList<>();
        List<StoredBatch> redoStack = new ArrayList<>();
    }

    /** JSON 序列化的单条操作记录。 */
    private static final class StoredBatch {
        String replayKind;
        String itemId;
        int toolSlot;
        String face;
        List<String> positions;
        boolean isDestructive;
        List<String> blockStates;

        // Gson 反序列化需要无参构造器
        @SuppressWarnings("unused")
        StoredBatch() {
        }

        StoredBatch(String replayKind, String itemId, int toolSlot, String face, List<String> positions, boolean isDestructive, List<String> blockStates) {
            this.replayKind = replayKind;
            this.itemId = itemId;
            this.toolSlot = toolSlot;
            this.face = face;
            this.positions = positions;
            this.isDestructive = isDestructive;
            this.blockStates = blockStates;
        }
    }
}
