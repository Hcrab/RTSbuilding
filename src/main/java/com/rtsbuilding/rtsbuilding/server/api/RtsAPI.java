package com.rtsbuilding.rtsbuilding.server.api;

/**
 * RTS 建筑模组的主 API 入口。
 *
 * <p>第三方附加模组应通过此接口访问所有 RTS 功能。
 * 通过 {@link #get()} 获取全局单例。
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 获取 API 实例
 * RtsAPI api = RtsAPI.get();
 *
 * // 查询玩家存储中特定物品的数量
 * long count = api.storage().countItems(player, Items.DIAMOND);
 * }</pre>
 *
 * <p>所有方法均为线程安全。当没有玩家会话活跃时返回默认值。
 */
public interface RtsAPI {

    /**
     * 返回全局 {@link RtsAPI} 实例。
     * 模组初始化完成后始终可用。
     */
    static RtsAPI get() {
        return Holder.INSTANCE;
    }

    // ======================================================================
    // 子 API
    // ======================================================================

    /** 存储查询：计数、提取和归还物品/流体 */
    RtsStorageQueryAPI storage();

    /** 蓝图材料查询与提取 */
    RtsBlueprintAPI blueprint();

    /** 远程方块放置 */
    RtsPlacementAPI placement();

    /** 远程交互（右键容器/实体等） */
    RtsInteractionAPI interaction();

    /** 远程挖掘与连锁挖掘 */
    RtsMiningAPI mining();

    /** 链接存储与玩家物品栏之间的物品转移 */
    RtsTransferAPI transfer();

    /** 合成终端操作 */
    RtsCraftingAPI crafting();

    /** 流体操作 */
    RtsFluidAPI fluids();

    /** 存储绑定管理 */
    RtsBindingsAPI bindings();

    /** 会话查询 */
    RtsSessionQueryAPI sessions();

    /**
     * 设置内部实现。由 RTS 核心在模组初始化期间调用。
     *
     * @throws IllegalStateException 如果实现已被设置
     */
    static void setImplementation(RtsAPI implementation) {
        if (Holder.INSTANCE != null && implementation != null) {
            throw new IllegalStateException("RtsAPI implementation already set");
        }
        Holder.INSTANCE = implementation;
    }

    final class Holder {
        private Holder() {
        }

        static RtsAPI INSTANCE;
    }
}
