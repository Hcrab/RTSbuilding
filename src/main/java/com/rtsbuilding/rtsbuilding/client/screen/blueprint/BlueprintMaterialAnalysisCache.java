package com.rtsbuilding.rtsbuilding.client.screen.blueprint;

import com.rtsbuilding.rtsbuilding.common.blueprint.model.RtsBlueprint;

import java.util.ArrayDeque;
import java.util.IdentityHashMap;
import java.util.Iterator;

/**
 * 按 {@link RtsBlueprint} 对象身份保存静态材料分析的有界 LRU 缓存。
 *
 * <p>蓝图是不可变记录时，对象身份就是安全且便宜的缓存键；这里刻意使用
 * {@link IdentityHashMap}，避免调用蓝图 record 的深层 {@code hashCode()}，也避免
 * 两个内容相同但属于不同文件/会话的蓝图互相污染。缓存只保留最近 32 个身份，
 * 淘汰后重新分析不会改变玩家行为。</p>
 *
 * <p>背包、仓库和流体在每次调用时实时读取；每份分析只记住上一次完整库存输入与
 * 模拟结果，输入变化立即重算，不会等待任意 tick 或计时器失效。</p>
 */
final class BlueprintMaterialAnalysisCache {
    static final int DEFAULT_MAX_ENTRIES = 32;

    private final int maxEntries;
    private final IdentityHashMap<RtsBlueprint, BlueprintMaterialAnalysis> analyses = new IdentityHashMap<>();
    private final ArrayDeque<RtsBlueprint> lruOrder = new ArrayDeque<>();

    BlueprintMaterialAnalysisCache() {
        this(DEFAULT_MAX_ENTRIES);
    }

    BlueprintMaterialAnalysisCache(int maxEntries) {
        if (maxEntries <= 0) {
            throw new IllegalArgumentException("maxEntries must be positive");
        }
        this.maxEntries = maxEntries;
    }

    synchronized BlueprintMaterialAnalysis get(RtsBlueprint blueprint) {
        if (blueprint == null) {
            return BlueprintMaterialAnalysis.empty();
        }

        BlueprintMaterialAnalysis cached = analyses.get(blueprint);
        if (cached != null) {
            touch(blueprint);
            return cached;
        }

        BlueprintMaterialAnalysis analysis = BlueprintMaterialAnalysis.analyze(blueprint);
        put(blueprint, analysis);
        return analysis;
    }

    /** 后台解析已完成静态分析时，客户端只接收结果，不在首次绘制时扫描大型蓝图。 */
    synchronized void put(RtsBlueprint blueprint, BlueprintMaterialAnalysis analysis) {
        if (analyses.containsKey(blueprint)) {
            analyses.put(blueprint, analysis);
            touch(blueprint);
            return;
        }
        if (analyses.size() >= maxEntries) {
            RtsBlueprint eldest = lruOrder.removeFirst();
            analyses.remove(eldest);
        }
        analyses.put(blueprint, analysis);
        lruOrder.addLast(blueprint);
    }

    synchronized void clear() {
        analyses.clear();
        lruOrder.clear();
    }

    synchronized int size() {
        return analyses.size();
    }

    private void touch(RtsBlueprint blueprint) {
        for (Iterator<RtsBlueprint> iterator = lruOrder.iterator(); iterator.hasNext();) {
            if (iterator.next() == blueprint) {
                iterator.remove();
                break;
            }
        }
        lruOrder.addLast(blueprint);
    }
}
