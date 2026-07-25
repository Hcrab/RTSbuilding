package com.rtsbuilding.rtsbuilding.server.service.api;

import com.rtsbuilding.rtsbuilding.network.storage.RtsStorageSort;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * 存储页面服务接口——管理远程储存浏览器的页面请求、搜索、排序和分类。
 *
 * <p>该接口定义了 RTS 储存浏览器的页面构建功能：
 * 支持分页浏览链接存储中的物品、按关键字搜索（含拼音搜索）、
 * 按多种排序方式排列、按模组/创造标签页分类过滤，
 * 以及标记存储视图为脏以触发刷新和记录最近使用的物品。
 */
public interface PageService {

    /**
     * 请求指定页面的存储内容（最简重载，自动补全拼音搜索设置）。
     *
     * @param player    目标玩家
     * @param page      页码（从 0 开始）
     * @param search    搜索关键字
     * @param category  分类过滤器
     * @param sort      排序方式
     * @param ascending 是否升序排列
     */
    void requestPage(ServerPlayer player, int page, String search, String category,
                     RtsStorageSort sort, boolean ascending);

    /**
     * 请求指定页面的存储内容（带拼音搜索设置）。
     *
     * @param player              目标玩家
     * @param page                页码
     * @param search              搜索关键字
     * @param category            分类过滤器
     * @param sort                排序方式
     * @param ascending           是否升序排列
     * @param pinyinSearchEnabled 是否启用拼音搜索
     */
    void requestPage(ServerPlayer player, int page, String search, String category,
                     RtsStorageSort sort, boolean ascending, boolean pinyinSearchEnabled);

    /**
     * 请求指定页面的存储内容（完整参数，支持自定义每页大小和本地化搜索匹配）。
     *
     * @param player                 目标玩家
     * @param page                   页码
     * @param search                 搜索关键字
     * @param category               分类过滤器
     * @param sort                   排序方式
     * @param ascending              是否升序排列
     * @param pageSize               每页显示数量
     * @param pinyinSearchEnabled    是否启用拼音搜索
     * @param localizedSearchMatches 本地化搜索匹配的预计算物品 ID 列表
     */
    void requestPage(ServerPlayer player, int page, String search, String category,
                     RtsStorageSort sort, boolean ascending, int pageSize,
                     boolean pinyinSearchEnabled, List<String> localizedSearchMatches);

    /**
     * 标记当前存储视图为脏，触发下次请求时重新构建页面。
     * 当存储内容发生变化时调用，确保客户端显示最新的数据。
     *
     * @param player  目标玩家
     * @param session 玩家的 RTS 储存会话
     */
    void markStorageViewDirty(ServerPlayer player, RtsStorageSession session);

    /**
     * 记录最近使用的物品到会话中。
     * 用于在储存浏览器的"最近使用"分类中显示。
     *
     * @param session 玩家的 RTS 储存会话
     * @param itemId  物品的注册名 ID
     * @param kind    使用类型（放置/使用/合成等）
     * @param amount  使用的数量
     */
    void recordRecentItem(RtsStorageSession session, String itemId, byte kind, long amount);
}
