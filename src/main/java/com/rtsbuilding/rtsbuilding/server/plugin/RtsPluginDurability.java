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
 * <p>普通玩家数据仍由 {@link SaveScheduler} 批量保存；本类只服务于低频但不可丢失的插件变更。
 * 它先落盘插件状态，再保存包含插件物品增减的玩家背包，从而把强制结束服务端时的丢失窗口
 * 从自动保存周期缩短到本次操作返回之前。本类不负责判定插件是否合法，也不修改插件列表。
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

            // 插件物品已经从背包扣除或退回；同一检查点保存玩家文件，避免状态与物品只存一边。
            server.getPlayerList().saveAll();
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
