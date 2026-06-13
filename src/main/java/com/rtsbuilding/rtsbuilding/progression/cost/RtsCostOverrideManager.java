package com.rtsbuilding.rtsbuilding.progression.cost;

import com.rtsbuilding.rtsbuilding.progression.RtsIngredientCost;
import com.rtsbuilding.rtsbuilding.progression.RtsProgressionNode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 成本覆写管理器，集中管理本地配置和网络同步的成本覆写??
 * <p>
 * 线程安全设计：先写数据后??volatile，保??happens-before??
 */
public final class RtsCostOverrideManager implements RtsCostProvider {

    private volatile Map<String, String> localOverrides = Map.of();
    private volatile Map<String, String> syncedOverrides = Map.of();
    private volatile boolean hasSyncedOverrides;

    /**
     * 应用从网络同步过来的成本覆写??
     * 格式：{@code ["camera_core=minecraft:glass:8", "radius_1=minecraft:redstone:12"]}
     */
    public void applySyncedOverrides(List<String> overrides) {
        Map<String, String> parsed = parseOverrideList(overrides);
        // 先写数据，再??volatile 标记，保??happens-before
        this.syncedOverrides = Map.copyOf(parsed);
        this.hasSyncedOverrides = true;
    }

    /**
     * 设置本地配置文件中的成本覆写??
     */
    public void setLocalOverrides(Map<String, String> overrides) {
        this.localOverrides = Map.copyOf(overrides);
    }

    /**
     * 清除所有网络同步的覆写??
     */
    public void clearSyncedOverrides() {
        this.syncedOverrides = Map.of();
        this.hasSyncedOverrides = false;
    }

    /**
     * 检查是否已接收过同步覆???
     */
    public boolean hasSyncedOverrides() {
        return hasSyncedOverrides;
    }

    /**
     * 获取节点的解锁成本（同步→本地→默认???
     */
    @Override
    public List<RtsIngredientCost> costsFor(RtsProgressionNode node) {
        return resolveCosts(node, true);
    }

    /**
     * 仅使用同步覆写的成本查询??
     * <p>
     * 如果从未同步过，回退??{@link #costsFor}（含本地覆写）；
     * 如果同步过但该节点无同步覆写，使用默认成本（忽略本地覆写???
     * 此行为用于客户端：一旦服务端同步过，本地配置的覆写不再生???
     */
    public List<RtsIngredientCost> syncedCostsFor(RtsProgressionNode node) {
        if (!hasSyncedOverrides) {
            return resolveCosts(node, true);
        }
        String text = syncedOverrides.get(node.id().getPath());
        return text == null ? node.costs() : RtsCostSerialization.parse(text, node.costs());
    }

    private List<RtsIngredientCost> resolveCosts(RtsProgressionNode node, boolean includeLocal) {
        if (node == null) {
            return List.of();
        }
        String text = syncedOverrides.get(node.id().getPath());
        if (text == null && includeLocal) {
            text = localOverrides.get(node.id().getPath());
        }
        if (text == null) {
            return node.costs();
        }
        return RtsCostSerialization.parse(text, node.costs());
    }

    /**
     * 解析同步覆写列表??
     * 格式：{@code ["camera_core=minecraft:glass:8", "radius_1=minecraft:redstone:12"]}
     */
    private static Map<String, String> parseOverrideList(List<String> overrides) {
        if (overrides == null || overrides.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, String> out = new LinkedHashMap<>();
        for (String raw : overrides) {
            if (raw == null) continue;
            int split = raw.indexOf('=');
            if (split <= 0) continue;
            String node = raw.substring(0, split).trim();
            String costs = raw.substring(split + 1).trim();
            if (!node.isBlank()) {
                out.put(node, costs);
            }
        }
        return out;
    }
}
