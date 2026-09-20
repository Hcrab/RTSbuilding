package com.rtsbuilding.rtsbuilding.client.screen.blueprint;

import com.rtsbuilding.rtsbuilding.common.blueprint.model.RtsBlueprint;

import java.util.ArrayDeque;
import java.util.IdentityHashMap;
import java.util.Iterator;

/** 按蓝图对象身份保存静态材料分析的有界 LRU 缓存。 */
final class BlueprintMaterialAnalysisCache {
    static final int DEFAULT_MAX_ENTRIES = 32;
    private final int maxEntries;
    private final IdentityHashMap<RtsBlueprint, BlueprintMaterialAnalysis> analyses = new IdentityHashMap<>();
    private final ArrayDeque<RtsBlueprint> lruOrder = new ArrayDeque<>();

    BlueprintMaterialAnalysisCache() { this(DEFAULT_MAX_ENTRIES); }

    BlueprintMaterialAnalysisCache(int maxEntries) {
        if (maxEntries <= 0) throw new IllegalArgumentException("maxEntries must be positive");
        this.maxEntries = maxEntries;
    }

    synchronized BlueprintMaterialAnalysis get(RtsBlueprint blueprint) {
        if (blueprint == null) return BlueprintMaterialAnalysis.empty();
        BlueprintMaterialAnalysis cached = analyses.get(blueprint);
        if (cached != null) { touch(blueprint); return cached; }
        BlueprintMaterialAnalysis analysis = BlueprintMaterialAnalysis.analyze(blueprint);
        put(blueprint, analysis);
        return analysis;
    }

    synchronized void put(RtsBlueprint blueprint, BlueprintMaterialAnalysis analysis) {
        if (blueprint == null || analysis == null) return;
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

    synchronized void clear() { analyses.clear(); lruOrder.clear(); }
    synchronized int size() { return analyses.size(); }

    private void touch(RtsBlueprint blueprint) {
        for (Iterator<RtsBlueprint> iterator = lruOrder.iterator(); iterator.hasNext();) {
            if (iterator.next() == blueprint) { iterator.remove(); break; }
        }
        lruOrder.addLast(blueprint);
    }
}
