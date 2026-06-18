package com.rtsbuilding.rtsbuilding.server.storage.session;

import com.rtsbuilding.rtsbuilding.network.storage.RtsStorageSort;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStoragePageBuilder;

import java.util.HashSet;
import java.util.Set;

/**
 * 存储浏览器与合成浏览器的可变状态容器。
 *
 * <p>从 RtsStorageSession 提取，按 "用户在浏览器界面上如何
 * 查看和筛选内容" 的职责聚合。包含翻页、搜索、分类排序和拼音模糊搜索。
 *
 * <h3>设计约束</h3>
 * <ul>
 *   <li><b>纯数据容器</b>——不包含业务逻辑，仅持有 public mutable 字段</li>
 *   <li><b>可独立实例化</b>——便于测试浏览器状态切换而无需完整 session</li>
 * </ul>
 */
public class RtsBrowserState {

    /** 方块放置批次默认大小。 */
    public static final int CRAFTABLE_BATCH_SIZE = 12;

    // ======================================================================
    // 存储浏览器状态
    // ======================================================================

    /** 当前页号（0-based） */
    public int page;
    /** 每页条目数，默认从页面构建器常量读取 */
    public int pageSize = RtsStoragePageBuilder.DEFAULT_PAGE_SIZE;
    /** 搜索关键词（空串 = 无筛选） */
    public String search = "";
    /** 分类筛选："all" / "mod|namespace" / "tab|name" */
    public String category = "all";
    /** 当前排序方式：数量/名称/模组/种类 */
    public RtsStorageSort sort = RtsStorageSort.QUANTITY;
    /** true = 升序，false = 降序 */
    public boolean ascending = false;
    /** 拼音模糊搜索开关 */
    public boolean pinyinSearchEnabled;
    /** 已本地化的搜索命中 ID 集合（用于客户端高亮/快速过滤） */
    public final Set<String> localizedSearchMatches = new HashSet<>();

    // ======================================================================
    // 合成浏览器状态
    // ======================================================================

    /** 合成搜索关键词 */
    public String craftSearch = "";
    /** 是否显示不可合成的配方 */
    public boolean craftShowUnavailable;
    /** 已请求的合成配方总数（含偏移量和限制量，至少为 CRAFTABLE_BATCH_SIZE） */
    public int craftRequestedCount = CRAFTABLE_BATCH_SIZE;
    /** 合成搜索的拼音模糊搜索开关 */
    public boolean craftPinyinSearchEnabled;
    /** 合成搜索的本地化命中 ID 集合 */
    public final Set<String> craftLocalizedSearchMatches = new HashSet<>();
}
