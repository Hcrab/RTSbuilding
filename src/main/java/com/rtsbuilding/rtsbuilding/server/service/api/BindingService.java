package com.rtsbuilding.rtsbuilding.server.service.api;

import com.rtsbuilding.rtsbuilding.common.BuilderMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 存储绑定服务接口——管理玩家链接存储、快捷槽、GUI 绑定和建造模式切换等绑定操作。
 *
 * <p>该接口定义了 RTS 模式下玩家与存储系统之间的所有绑定相关操作：
 * 包括链接/解绑世界中的存储容器、管理快捷槽物品、配置 GUI 绑定快捷键
 * 以及切换建造模式等。实现类通过 {@link com.rtsbuilding.rtsbuilding.server.service.impl.RtsBindingServiceImpl}
 * 提供具体的服务端逻辑。
 */
public interface BindingService {

    /**
     * 设置玩家的当前建造模式。
     *
     * @param player 目标玩家
     * @param mode   要切换到的建造模式（如 BUILD、MINE、CRAFT 等）
     */
    void setMode(ServerPlayer player, BuilderMode mode);

    /**
     * 将世界中的存储方块链接到玩家的 RTS 会话。
     * 链接后，该存储容器的物品将可通过远程储存浏览器访问。
     *
     * @param player   执行链接操作的玩家
     * @param pos      目标存储方块的坐标
     * @param linkMode 链接模式：0=物品+流体，1=仅物品，2=仅流体
     */
    void linkStorage(ServerPlayer player, BlockPos pos, byte linkMode);

    /**
     * 从玩家的 RTS 会话中解绑指定的存储方块。
     * 解绑后，该存储容器将不再可通过远程储存浏览器访问。
     *
     * @param player 执行解绑操作的玩家
     * @param pos    要解绑的存储方块坐标
     */
    void unlinkStorage(ServerPlayer player, BlockPos pos);

    /**
     * 更新已链接存储的设置，包括链接模式和优先级。
     * 优先级决定了物品存取时的顺序（高优先级先被提取）。
     *
     * @param player   执行更新的玩家
     * @param pos      目标链接存储方块的坐标
     * @param linkMode 新的链接模式
     * @param priority 新的优先级（数值越大优先级越高）
     */
    void updateLinkedStorageSettings(ServerPlayer player, BlockPos pos, byte linkMode, int priority);

    /**
     * 启用或禁用掉落物漏斗功能。
     * 开启后，系统会自动收集玩家附近地面上的掉落物并存入链接存储。
     *
     * @param player  目标玩家
     * @param enabled {@code true} 启用漏斗，{@code false} 禁用
     */
    void setFunnelEnabled(ServerPlayer player, boolean enabled);

    /**
     * 更新掉落物漏斗的收集目标位置。
     * 漏斗会将指定位置附近的掉落物自动吸入链接存储。
     *
     * @param player 目标玩家
     * @param target 新的收集目标方块坐标
     */
    void updateFunnelTarget(ServerPlayer player, BlockPos target);

    /**
     * 设置是否自动存入开采掉落的物品。
     * 开启后，通过 RTS 远程挖掘获得的掉落物将自动存入链接存储。
     *
     * @param player  目标玩家
     * @param enabled {@code true} 启用自动存入，{@code false} 禁用
     */
    void setAutoStoreMinedDrops(ServerPlayer player, boolean enabled);

    /**
     * 设置是否启用 BD（Burning Dimension）网络集成。
     * 启用后，玩家的 BD 网络存储将作为额外的存储源。
     *
     * @param player  目标玩家
     * @param enabled {@code true} 启用 BD 网络，{@code false} 禁用
     */
    void setBdNetworkEnabled(ServerPlayer player, boolean enabled);

    /**
     * 设置指定快捷槽位的物品。
     * 快捷槽允许玩家快速选择常用物品进行远程放置。
     *
     * @param player       目标玩家
     * @param slotId       快捷槽位 ID（0-8）
     * @param itemId       物品的注册名（如 "minecraft:stone"）
     * @param previewStack 用于客户端显示的预览物品栈
     */
    void setQuickSlot(ServerPlayer player, byte slotId, String itemId, ItemStack previewStack);

    /**
     * 配置或清除 GUI 绑定的快捷键。
     * GUI 绑定允许玩家将常用的交互操作（如打开某个容器）绑定到快捷键上。
     *
     * @param player     目标玩家
     * @param slotId     绑定槽位 ID
     * @param clear      是否清除该槽位的绑定
     * @param pos        目标方块坐标（非清除模式时需要）
     * @param face       目标方块的面（非清除模式时需要）
     * @param itemIdHint 物品 ID 提示（用于客户端显示）
     */
    void setGuiBinding(ServerPlayer player, byte slotId, boolean clear, BlockPos pos, Direction face, String itemIdHint);

    /**
     * 打开指定 GUI 绑定槽位对应的远程菜单。
     * 执行该绑定时，系统会模拟远程与该方块交互并打开其 GUI。
     *
     * @param player 目标玩家
     * @param slotId 要打开的绑定槽位 ID
     */
    void openGuiBinding(ServerPlayer player, byte slotId);

    /**
     * 将玩家快捷栏中指定槽位的物品存入链接存储。
     * 用于快速将手中的物品转移到远程存储系统中。
     *
     * @param player 目标玩家
     * @param slotId 快捷栏槽位 ID（0-8）
     */
    void storeHotbarSlot(ServerPlayer player, byte slotId);

    /**
     * 关闭当前打开的远程菜单。
     * 当玩家通过远程交互打开了某个容器的 GUI 后，调用此方法关闭它。
     *
     * @param player 目标玩家
     */
    void closeRemoteMenu(ServerPlayer player);
}
