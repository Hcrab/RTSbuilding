package com.rtsbuilding.rtsbuilding.server.storage;

import com.rtsbuilding.rtsbuilding.network.storage.RtsStorageSort;

import java.util.HashSet;
import java.util.Set;

/**
 * 存储浏览器与合成浏览器的可变状态容器。
 *
 * <p>本类只描述玩家如何查看、筛选和排序存储内容，不负责扫描存储、
 * 生成页面、移动物品或发包。把这些字段从 {@link RtsStorageSession} 中拆出来，
 * 可以让页面、搜索、合成浏览器逻辑独立演进，而不继续拉大整个会话对象。</p>
 */
public class RtsBrowserState {

    /** 合成配方浏览器默认批次大小。 */
    public static final int CRAFTABLE_BATCH_SIZE = 12;

    /** 当前页号，0-based。 */
    public int page;
    /** 每页条目数。 */
    public int pageSize = RtsStoragePageBuilder.DEFAULT_PAGE_SIZE;
    /** 搜索关键词，空串表示无筛选。 */
    public String search = "";
    /** 分类筛选："all" / "mod|namespace" / "tab|name"。 */
    public String category = "all";
    /** 当前排序方式。 */
    public RtsStorageSort sort = RtsStorageSort.QUANTITY;
    /** true = 升序，false = 降序。 */
    public boolean ascending;
    /** 存储搜索是否启用拼音模糊匹配。 */
    public boolean pinyinSearchEnabled;
    /** 客户端传来的本地化搜索命中 ID 集合。 */
    public final Set<String> localizedSearchMatches = new HashSet<>();

    /** 合成搜索关键词。 */
    public String craftSearch = "";
    /** 是否显示暂不可合成的配方。 */
    public boolean craftShowUnavailable;
    /** 已请求的合成配方总数，至少为 {@link #CRAFTABLE_BATCH_SIZE}。 */
    public int craftRequestedCount = CRAFTABLE_BATCH_SIZE;
    /** 合成搜索是否启用拼音模糊匹配。 */
    public boolean craftPinyinSearchEnabled;
    /** 合成搜索的本地化命中 ID 集合。 */
    public final Set<String> craftLocalizedSearchMatches = new HashSet<>();
}
