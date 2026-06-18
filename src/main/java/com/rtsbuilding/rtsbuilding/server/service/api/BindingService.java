package com.rtsbuilding.rtsbuilding.server.service.api;

import com.rtsbuilding.rtsbuilding.common.BuilderMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * 存储绑定服务接口——管理玩家链接存储、快捷槽、GUI 绑定和模式切换。
 */
public interface BindingService {

    /**
     * 设置建造模式。
     */
    void setMode(ServerPlayer player, BuilderMode mode);

    /**
     * 链接存储方块。
     */
    void linkStorage(ServerPlayer player, BlockPos pos, byte linkMode);

    /**
     * 解绑存储方块。
     */
    void unlinkStorage(ServerPlayer player, BlockPos pos);

    /**
     * 更新链接存储设置（模式、优先级）。
     */
    void updateLinkedStorageSettings(ServerPlayer player, BlockPos pos, byte linkMode, int priority);

    /**
     * 设置漏斗开关。
     */
    void setFunnelEnabled(ServerPlayer player, boolean enabled);

    /**
     * 更新漏斗目标位置。
     */
    void updateFunnelTarget(ServerPlayer player, BlockPos target);

    /**
     * 设置自动存入掉落物开关。
     */
    void setAutoStoreMinedDrops(ServerPlayer player, boolean enabled);

    /**
     * 设置 BD 网络开关。
     */
    void setBdNetworkEnabled(ServerPlayer player, boolean enabled);

    /**
     * 设置快捷槽物品。
     */
    void setQuickSlot(ServerPlayer player, byte slotId, String itemId, ItemStack previewStack);

    /**
     * 设置 GUI 绑定。
     */
    void setGuiBinding(ServerPlayer player, byte slotId, boolean clear, BlockPos pos, Direction face, String itemIdHint);

    /**
     * 打开 GUI 绑定。
     */
    void openGuiBinding(ServerPlayer player, byte slotId);

    /**
     * 将快捷栏物品存入链接存储。
     */
    void storeHotbarSlot(ServerPlayer player, byte slotId);

    /**
     * 关闭远程菜单。
     */
    void closeRemoteMenu(ServerPlayer player);
}
