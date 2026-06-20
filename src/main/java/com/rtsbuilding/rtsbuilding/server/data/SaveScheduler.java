package com.rtsbuilding.rtsbuilding.server.data;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 统一持久化调度器——管理所有 {@link DataCluster} 的生命周期。
 *
 * <p>核心职责：
 * <ul>
 *   <li>按玩家 UUID + 作用域管理对应的 DataCluster</li>
 *   <li>按 tick 间隔批量刷盘（默认每 200 tick ≈ 10 秒）</li>
 *   <li>在关键事件（保存世界、玩家登出、服务器关闭）时强制刷盘</li>
 *   <li>零闲置开销——无脏数据时 flush 是空操作</li>
 * </ul>
 *
 * <p><b>统一生命周期管理</b>：所有持久化组件（会话、工作流等）统一
 * 由此调度器管理 DataCluster 缓存，避免各子系统各自维护独立缓存
 * 导致的「分裂」问题。
 *
 * <p>使用方式：
 * <pre>{@code
 * // 获取玩家会话数据簇
 * DataCluster cluster = SaveScheduler.INSTANCE.player(player);
 * cluster.set(SessionComponents.BROWSER, browserState);
 *
 * // 获取玩家工作流数据簇
 * DataCluster wf = SaveScheduler.INSTANCE.dataCluster(server, player.getUUID(), "workflow");
 * wf.set(WorkflowComponents.FULL_WORKFLOW, data);
 *
 * // 在服务器 tick 事件中调用
 * SaveScheduler.INSTANCE.onTick(server);
 *
 * // 玩家登出时（自动清理所有作用域的数据）
 * SaveScheduler.INSTANCE.onPlayerLogout(player);
 * }</pre>
 */
public enum SaveScheduler {
    INSTANCE;

    /** 默认刷盘间隔（tick 数），200 tick ≈ 10 秒 */
    private static final int DEFAULT_FLUSH_INTERVAL = 200;

    /** 缓存键分隔符 */
    private static final String KEY_SEPARATOR = "::";

    private final Map<String, DataCluster> clusters = new ConcurrentHashMap<>();
    private int tickCounter;
    private int flushInterval = DEFAULT_FLUSH_INTERVAL;

    // ──────────────────────────────────────────────────────────────────
    //  公开 API
    // ──────────────────────────────────────────────────────────────────

    /**
     * 获取指定玩家的会话 {@link DataCluster}。
     *
     * <p>数据存储在 {@code rtsbuilding/players/{uuid}/session.dat}。
     * 懒加载——首次调用时才读文件。
     */
    public DataCluster player(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            throw new IllegalStateException("无法在服务器未就绪时获取 DataCluster");
        }
        return dataCluster(server, player.getUUID(), "session");
    }

    /**
     * 获取指定 UUID 的玩家会话 {@link DataCluster}（服务端引用可用时）。
     */
    public DataCluster player(MinecraftServer server, UUID playerId) {
        return dataCluster(server, playerId, "session");
    }

    /**
     * 获取指定 UUID 和作用域的 {@link DataCluster}。
     *
     * <p>每个 scope 对应一个独立的 NBT 文件：
     * <ul>
     *   <li>{@code "session"} → {@code rtsbuilding/players/{uuid}/session.dat}（会话数据）</li>
     *   <li>{@code "workflow"} → {@code rtsbuilding/players/{uuid}/workflow.dat}（工作流数据）</li>
     * </ul>
     *
     * <p>所有 scope 的 DataCluster 统一由本调度器管理生命周期，
     * 确保玩家登出/服务器关闭时所有数据都能正确刷盘，
     * 避免各子系统各自维护独立缓存导致的「分裂」问题。
     *
     * @param server   Minecraft 服务器实例
     * @param playerId 玩家 UUID
     * @param scope    数据用途标识，用于区分不同文件
     */
    public DataCluster dataCluster(MinecraftServer server, UUID playerId, String scope) {
        return clusters.computeIfAbsent(cacheKey(playerId, scope), key -> {
            var store = new RtsAtomicNbtStore(server, "rtsbuilding/players/" + playerId, scope + ".dat");
            return new DataCluster(store);
        });
    }

    /**
     * 每 tick 调用——到达间隔时批量刷盘。
     * 建议在 {@code ServerTickEvent} 中调用。
     */
    public void onTick(MinecraftServer server) {
        if (++tickCounter % flushInterval != 0) return;
        flushAll();
    }

    /**
     * 保存世界时调用——确保所有数据落盘。
     * 建议在 {@code SaveEvent} 中调用。
     */
    public void onSave() {
        flushAll();
    }

    /**
     * 玩家登出时调用——刷盘并释放该玩家所有作用域的数据。
     * 建议在 {@code PlayerLoggedOutEvent} 中调用。
     */
    public void onPlayerLogout(ServerPlayer player) {
        UUID playerId = player.getUUID();
        String prefix = playerId + KEY_SEPARATOR;
        clusters.entrySet().removeIf(entry -> {
            if (entry.getKey().startsWith(prefix)) {
                entry.getValue().flushAndClose();
                return true;
            }
            return false;
        });
    }

    /**
     * 服务器关闭时调用——刷所有盘并清空。
     */
    public void onServerStopped() {
        flushAll();
        clusters.clear();
    }

    /** 立即刷新所有玩家的数据。 */
    public void flushAll() {
        for (Map.Entry<String, DataCluster> entry : clusters.entrySet()) {
            try {
                entry.getValue().flush();
            } catch (Exception e) {
                RtsbuildingMod.LOGGER.error("保存数据失败（缓存键 {}）: {}", entry.getKey(), e.getMessage());
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────
    //  内部辅助方法
    // ──────────────────────────────────────────────────────────────────

    private static String cacheKey(UUID playerId, String scope) {
        return playerId.toString() + KEY_SEPARATOR + scope;
    }

    // ──────────────────────────────────────────────────────────────────
    //  旧文件清理
    // ──────────────────────────────────────────────────────────────────

    /**
     * 清理迁移后遗留的旧版全量文件。
     *
     * <p>检查 {@code rtsbuilding/storage_sessions.dat} 和
     * {@code rtsbuilding/workflow_data.dat} 是否还有未迁移的玩家数据。
     * 如果文件已为空（所有玩家数据已按 UUID 拆分迁移），则安全删除。
     * 如果还有残留数据，打日志提醒管理员哪些玩家尚未登录迁移。
     *
     * @param server Minecraft 服务器实例
     */
    public void cleanupLegacyFiles(MinecraftServer server) {
        if (server == null) return;

        cleanupSingleLegacyFile(server, "rtsbuilding", "storage_sessions.dat", "旧版存储会话");
        cleanupSingleLegacyFile(server, "rtsbuilding", "workflow_data.dat", "旧版工作流");
    }

    private void cleanupSingleLegacyFile(MinecraftServer server, String subDir, String fileName, String label) {
        Path path = server.getWorldPath(LevelResource.ROOT).resolve(subDir).resolve(fileName);
        if (!Files.isRegularFile(path)) return;

        try {
            CompoundTag root = new RtsAtomicNbtStore(server, subDir, fileName).read();
            CompoundTag players = root.getCompound("players");
            if (players.isEmpty()) {
                Files.delete(path);
                Path tempPath = path.resolveSibling(fileName + ".tmp");
                Files.deleteIfExists(tempPath);
                RtsbuildingMod.LOGGER.info("[迁移] 已清理空的 {} 文件: {}", label, path.getFileName());
            } else {
                RtsbuildingMod.LOGGER.warn("[迁移] {} 文件仍有 {} 名玩家数据未迁移 (需这些玩家登录一次)",
                        label, players.getAllKeys().size());
            }
        } catch (IOException | RuntimeException e) {
            RtsbuildingMod.LOGGER.warn("[迁移] 检查 {} 文件失败: {}", label, e.getMessage());
        }
    }

    // ──────────────────────────────────────────────────────────────────
    //  配置
    // ──────────────────────────────────────────────────────────────────

    /** 设置刷盘间隔（tick 数），默认 200。 */
    public void setFlushInterval(int ticks) {
        this.flushInterval = Math.max(20, ticks); // 最少 1 秒
    }

    /** 返回当前缓存的集群数量（用于诊断）。 */
    public int cachedClusterCount() {
        return clusters.size();
    }
}
