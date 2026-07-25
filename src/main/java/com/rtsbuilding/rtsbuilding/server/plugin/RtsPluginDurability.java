package com.rtsbuilding.rtsbuilding.server.plugin;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.server.data.SaveScheduler;
import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.IOUtilities;

/**
 * 插件安装、卸载与迁移完成后的即时耐久化检查点。
 *
 * <p>所有 RTS 插件数据统一通过 {@link SaveScheduler} 写入 DataCluster（session.dat），
 * 不再额外主动保存玩家背包。背包变更由 Minecraft 的自然保存周期持久化，避免两套存储
 * 系统在服务器崩溃时状态不一致。最坏情况：插件已落盘但物品回滚（玩家可重装或管理员回收），
 * 远优于插件丢失或物品被扣但插件未安装。
 *
 * <p>本类不负责判定插件是否合法，也不修改插件列表。仅将 DataCluster 的丢失窗口从
 * 自动保存周期（~200 tick）缩短到本次操作返回之前。
 */
final class RtsPluginDurability {
    private RtsPluginDurability() {
    }

    static boolean checkpoint(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return false;
        }

        try {
            // 个人插件与队伍迁移后的个人残留都位于玩家 session.dat。
            if (!SaveScheduler.INSTANCE.player(player).flush()) {
                RtsbuildingMod.LOGGER.error(
                        "插件变更即时保存失败：玩家 {} 的 RTS 数据尚未落盘，将保留脏数据等待重试",
                        player.getGameProfile().getName());
                return false;
            }

            // 队伍共享插件使用 SavedData；save() 只提交异步任务，必须等 IO worker 真正完成。
            String sharedKey = RtsProgressionManager.sharedProgressionKey(player);
            if (!sharedKey.isBlank()) {
                ServerLevel storageLevel = server.getLevel(Level.OVERWORLD);
                if (storageLevel == null) {
                    storageLevel = player.serverLevel();
                }
                storageLevel.getDataStorage().save();
                IOUtilities.waitUntilIOWorkerComplete();
            }

            return true;
        } catch (RuntimeException exception) {
            RtsbuildingMod.LOGGER.error(
                    "插件变更即时保存异常：玩家 {}，将由后续自动保存继续重试",
                    player.getGameProfile().getName(),
                    exception);
            return false;
        }
    }
}
