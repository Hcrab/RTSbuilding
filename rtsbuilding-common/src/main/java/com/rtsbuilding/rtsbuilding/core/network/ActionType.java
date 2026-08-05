package com.rtsbuilding.rtsbuilding.core.network;

public enum ActionType {
    SET_MODE, TOGGLE_CAMERA, SET_AUTO_STORE, SET_BD_NETWORK,
    LINK_STORAGE, UNLINK_STORAGE, UPDATE_LINKED_STORAGE, LINKED_QUICK_MOVE,
    LINKED_PICKUP, IMPORT_MENU_SLOT, FILL_INVENTORY,
    CLOSE_REMOTE_MENU, STORE_HOTBAR_SLOT,
    RETURN_CARRIED, REQUEST_PAGE,
    CRAFT_RECIPE, REQUEST_CRAFTABLES,
    OPEN_CRAFT_TERMINAL,
    /**
     * @deprecated 客户端插件请求逻辑已删除（网关 sendRequestPlugins / 模块 requestPlugins 均移除）。
     * 保留占位：C2SAction 按 ordinal 编解码，删除该值会使后续类型序号前移，
     * 导致新旧端混连时协议错位（如旧端序号 25 被解析为 PATHFIND）。
     */
    @Deprecated
    REQUEST_PLUGINS,
    PATHFIND,
    PLACE_BLOCK, PLACE_BATCH, PLACE_FLUID, ROTATE_BLOCK, SUBMIT_PENDING,
    MINE_BLOCK, AREA_MINE, AREA_DESTROY, ULTIMINE, BREAK,
    INTERACT_BLOCK, STORE_FLUID, QUICK_DROP, UNDO,
    PAUSE_WORKFLOW, DELETE_WORKFLOW,
    CAMERA_POSE,
    FUNNEL_PICKUP, FUNNEL_BOX_PICKUP,
    /**
     * 客户端在切换“物品拾取（漏斗）”按钮时同步开启/关闭状态到服务端，
     * 服务端据此允许/拒绝漏斗请求（客户端在服务端开启前不会真正吸物）。
     */
    SET_FUNNEL,
}
