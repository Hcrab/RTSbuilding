package com.rtsbuilding.rtsbuilding.server.plugin;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * 插件安装、卸载与迁移后的即时耐久化检查点。
 *
 * <p>Forge 1.20.1 的个人插件保存在玩家持久数据中，队伍插件保存在
 * SavedData 中。本类把两者与背包修改放进同一个低频检查点，避免强制关闭
 * 服务器时只保存了物品或只保存了插件状态。它不参与合法性判断。</p>
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
            String sharedKey = RtsProgressionManager.sharedProgressionKey(player);
            if (!sharedKey.isBlank()) {
                ServerLevel storageLevel = server.getLevel(Level.OVERWORLD);
                if (storageLevel == null) {
                    storageLevel = player.serverLevel();
                }
                storageLevel.getDataStorage().save();
            }
            server.getPlayerList().saveAll();
            return true;
        } catch (RuntimeException exception) {
            RtsbuildingMod.LOGGER.error(
                    "插件变更即时保存失败：玩家 {}，将由后续自动保存继续重试",
                    player.getGameProfile().getName(),
                    exception);
            return false;
        }
    }
}
