package com.rtsbuilding.rtsbuilding.client.screen;

import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.network.RtsClientPacketGateway;

/**
 * 服务端历史管理的客户端代理（基于 Ultimine-Rewind 风格重构???
 * <p>
 * 历史记录现在完全在服务端管理（参??{@link com.rtsbuilding.rtsbuilding.server.history.ServerHistoryManager}???
 * 此客户端代理负责??
 * <ul>
 *   <li>发送撤回请求到服务??/li>
 *   <li>接收并缓存来自服务端的历史状态同步（undoSize??/li>
 *   <li>提供 getUndoSize ??UI 按钮状态使??/li>
 * </ul>
 * <p>
 * 所有旧有的记录方法保留为空方法以保??API 兼容性，实际历史记录由服务端在执行操作时自动记录??
 */
public final class PlacementHistoryManager {

    /** 当前活跃实例，供网络处理器静态回???*/
    private static PlacementHistoryManager INSTANCE = null;

    /** 上一次从服务端同步的 undoSize（在 INSTANCE 可用前缓存）??*/
    private static int CACHED_UNDO_SIZE = 0;

    private BuilderScreen screen;
    private int undoSize = 0;

    /**
     * 初始化管理器，绑定所??Screen??
     */
    public void init(BuilderScreen screen, ClientRtsController controller) {
        this.screen = screen;
        INSTANCE = this;
        // 应用缓存的同步值（进入 RTS 模式时服务端可能??INSTANCE 设置前就发送了同步包）
        this.undoSize = CACHED_UNDO_SIZE;
    }

    // ===== 状态查??=====

    /** 当前可撤回的步数??*/
    public int getUndoSize() {
        return this.undoSize;
    }

    // ===== 撤回 =====

    /**
     * 发送撤回请求到服务???
     */
    public boolean undo() {
        RtsClientPacketGateway.sendUndo();
        return true;
    }

    // ===== 服务端状态同??=====

    /**
     * 从服务端接收历史状态同???
     * <p>
     * ??{@link com.rtsbuilding.rtsbuilding.client.network.RtsClientNetworkHandlers#handleHistorySync}
     * 在收??{@link com.rtsbuilding.rtsbuilding.network.builder.S2CRtsHistorySyncPayload} 时调???
     *
     * @param newUndoSize 服务端当前可撤回步数
     */
    public static void syncHistoryState(int newUndoSize) {
        // 始终更新缓存，确保在 INSTANCE 设置前不会丢失同??
        CACHED_UNDO_SIZE = newUndoSize;
        PlacementHistoryManager instance = INSTANCE;
        if (instance != null) {
            instance.undoSize = newUndoSize;
        }
    }

    // ===== 生命周期 =====

    /** 清空所有状???*/
    public void clear() {
        this.undoSize = 0;
        CACHED_UNDO_SIZE = 0;
        INSTANCE = null;
    }

    public BuilderScreen getScreen() {
        return screen;
    }
}
