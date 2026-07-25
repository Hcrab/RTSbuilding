package com.rtsbuilding.rtsbuilding.server.storage;

import com.rtsbuilding.rtsbuilding.common.build.BuilderMode;
import com.rtsbuilding.rtsbuilding.server.service.bindings.RtsLinkedStorageBindingService;
import com.rtsbuilding.rtsbuilding.server.service.bindings.RtsQuickSlotBindingService;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * 拥有玩家 RTS 存储会话的绑定边缘。
 *
 * <p>此辅助类决定哪些存储引用、外部 GUI 目标、快捷槽物品 ID
 * 和建造模式值存储在玩家的 RTS 会话上。它刻意不读取或构建
 * 完整的存储页面、聚合存储内容、移动物品、转移流体、合成、
 * 挖掘、放置方块或持久化包装器，因此现有网络处理器无需了解此拆分。
 *
 * <p>链接存储能力探测和访问检查仍来自 {@link RtsLinkedStorageResolver}；
 * 本类仅将产生的绑定状态应用到会话。远程 GUI 打开委托给 {@link RtsGuiBindingHelper}。
 */
public final class RtsStorageBindings {
    public static final int QUICK_SLOT_COUNT = 27;
    public static final int GUI_BINDING_SLOT_COUNT = 8;

    /** 绑定存储上限——防止玩家无限添加导致页面构建性能退化。 */
    public static final int MAX_LINKED_STORAGES = 50;

    private RtsStorageBindings() {
    }

    // ======================================================================
    //  建造模式
    // ======================================================================

    /**
     * 存储请求的建造模式，并报告离开漏斗模式是否需要管理器
     * 刷新漏斗缓冲并刷新页面。
     */
    public static boolean setMode(RtsStorageSession session, BuilderMode mode) {
        if (session == null) {
            return false;
        }
        session.mode = mode;
        return mode != BuilderMode.FUNNEL && session.funnel.funnelEnabled;
    }

    // ======================================================================
    //  存储链接
    // ======================================================================

    /**
     * 切换或重新定位链接存储引用，同时保留现有的仅提取模式行为。
     * 没有物品或流体端点的目标仍会要求 UI 返回第零页而不保存会话数据。
     */
    public static UpdateResult linkStorage(ServerPlayer player, RtsStorageSession session, BlockPos pos, byte linkMode) {
        return RtsLinkedStorageBindingService.linkStorage(player, session, pos, linkMode);
    }

    /**
     * 更新现有链接存储行的设置。这故意不是链接/创建操作：
     * 详情面板可以编辑模式和 AE 风格优先级，
     * 但服务器仍要求引用已属于玩家的会话。
     */
    public static UpdateResult updateLinkedStorageSettings(ServerPlayer player, RtsStorageSession session,
            BlockPos pos, byte linkMode, int priority) {
        return RtsLinkedStorageBindingService.updateSettings(player, session, pos, linkMode, priority);
    }

    // ======================================================================
    //  快捷槽
    // ======================================================================

    /**
     * 更新一个固定的快捷槽单元格。空白/null 物品 ID 清除该槽位；
     * 非空白 ID 必须在会话变更前解析为已注册的物品。
     */
    public static UpdateResult setQuickSlot(RtsStorageSession session, byte slotId, String itemId, ItemStack previewStack) {
        return RtsQuickSlotBindingService.setQuickSlot(session, slotId, itemId, previewStack);
    }

    public static boolean isValidQuickSlotIndex(int slot) {
        return RtsQuickSlotBindingService.isValidSlotIndex(slot);
    }

    // ======================================================================
    //  GUI 绑定（委托给 RtsGuiBindingHelper）
    // ======================================================================

    /**
     * 绑定或清除一个外部 GUI 槽位。
     */
    public static UpdateResult setGuiBinding(ServerPlayer player, RtsStorageSession session, byte slotId, boolean clear,
            BlockPos pos, Direction face, String itemIdHint) {
        return RtsGuiBindingHelper.setGuiBinding(player, session, slotId, clear, pos, face, itemIdHint);
    }

    /**
     * 从 RTS 相机模式重新打开已保存的 GUI 绑定。
     */
    public static UpdateResult openGuiBinding(ServerPlayer player, RtsStorageSession session, byte slotId, double remotePovBlockReach) {
        return RtsGuiBindingHelper.openGuiBinding(player, session, slotId, remotePovBlockReach);
    }

    /**
     * 回填早于物品 ID 图标的旧 GUI 绑定。
     */
    public static boolean refreshMissingGuiBindingIcons(ServerPlayer player, RtsStorageSession session) {
        return RtsGuiBindingHelper.refreshMissingGuiBindingIcons(player, session);
    }

    // ======================================================================
    //  记录类型
    // ======================================================================

    public record UpdateResult(boolean saveSession, boolean refreshPage, int page) {
        private static final UpdateResult NONE = new UpdateResult(false, false, 0);

        public static UpdateResult none() {
            return NONE;
        }

        public static UpdateResult refreshFirst(boolean saveSession) {
            return new UpdateResult(saveSession, true, 0);
        }

        public static UpdateResult refreshCurrent(RtsStorageSession session, boolean saveSession) {
            return new UpdateResult(saveSession, true, session == null ? 0 : session.browser.page);
        }
    }
}
