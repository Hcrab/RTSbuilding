package com.rtsbuilding.rtsbuilding.server.api;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;

/**
 * 存储绑定 API。
 *
 * <p>管理玩家的链接存储引用、快捷槽和外部 GUI 绑定。
 */
public interface RtsBindingsAPI {

    /**
     * 设置建造模式。
     *
     * @param player 目标玩家
     * @param mode   模式（com.rtsbuilding.rtsbuilding.common.BuilderMode）
     */
    void setMode(ServerPlayer player, Object mode);

    /**
     * 链接一个存储方块到玩家会话。
     *
     * @param player   执行玩家
     * @param pos      方块坐标
     * @param linkMode 链接模式
     */
    void linkStorage(ServerPlayer player, BlockPos pos, byte linkMode);

    /**
     * 从玩家会话解绑一个存储方块。
     */
    void unlinkStorage(ServerPlayer player, BlockPos pos);

    /**
     * 更新链接存储的设置。
     */
    void updateLinkedStorageSettings(ServerPlayer player, BlockPos pos,
                                     byte linkMode, int priority);

    /**
     * 设置漏斗功能开关。
     *
     * @param player  目标玩家
     * @param enabled 是否启用
     */
    void setFunnelEnabled(ServerPlayer player, boolean enabled);

    /**
     * 更新漏斗目标位置。
     */
    void updateFunnelTarget(ServerPlayer player, BlockPos target);

    /**
     * 设置自动存储挖掘掉落物。
     */
    void setAutoStoreMinedDrops(ServerPlayer player, boolean enabled);

    /**
     * 设置 BD 网络开关。
     */
    void setBdNetworkEnabled(ServerPlayer player, boolean enabled);

    /**
     * 设置快捷槽。
     */
    void setQuickSlot(ServerPlayer player, byte slotId, String itemId,
                      net.minecraft.world.item.ItemStack previewStack);

    /**
     * 设置外部 GUI 绑定。
     */
    void setGuiBinding(ServerPlayer player, byte slotId, boolean clear,
                       BlockPos pos, Direction face, String itemIdHint);

    /**
     * 打开外部 GUI 绑定。
     */
    void openGuiBinding(ServerPlayer player, byte slotId);

    /**
     * 从客户端请求关闭远程菜单。
     */
    void closeRemoteMenu(ServerPlayer player);

    /**
     * 设置热键栏槽位到链接存储。
     */
    void storeHotbarSlot(ServerPlayer player, byte slotId);
}
