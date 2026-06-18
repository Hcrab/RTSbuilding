package com.rtsbuilding.rtsbuilding.server.service.api;

import com.rtsbuilding.rtsbuilding.network.storage.RtsStorageSort;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * 存储页面服务接口——管理页面请求、搜索、排序和分类。
 */
public interface PageService {

    /**
     * 请求页面（最简重载，自动补全拼音搜索设置）。
     */
    void requestPage(ServerPlayer player, int page, String search, String category,
                     RtsStorageSort sort, boolean ascending);

    /**
     * 请求页面（带拼音搜索设置）。
     */
    void requestPage(ServerPlayer player, int page, String search, String category,
                     RtsStorageSort sort, boolean ascending, boolean pinyinSearchEnabled);

    /**
     * 请求页面（带拼音和本地化搜索匹配）。
     */
    void requestPage(ServerPlayer player, int page, String search, String category,
                     RtsStorageSort sort, boolean ascending, boolean pinyinSearchEnabled,
                     List<String> localizedSearchMatches);

    /**
     * 请求页面（完整参数）。
     */
    void requestPage(ServerPlayer player, int page, String search, String category,
                     RtsStorageSort sort, boolean ascending, int pageSize,
                     boolean pinyinSearchEnabled, List<String> localizedSearchMatches);

    /**
     * 标记存储视图为脏。
     */
    void markStorageViewDirty(ServerPlayer player, RtsStorageSession session);

    /**
     * 记录最近使用的物品。
     */
    void recordRecentItem(RtsStorageSession session, String itemId, byte kind, long amount);
}
