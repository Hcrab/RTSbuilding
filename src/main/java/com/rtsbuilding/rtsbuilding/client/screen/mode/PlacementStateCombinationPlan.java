package com.rtsbuilding.rtsbuilding.client.screen.mode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * R 放置轮盘的纯组合与分页规划。
 *
 * <p>每个属性的第零项始终代表当前幽灵状态，所以输出的第零项也是原完整状态。
 * 这里故意不认识 Minecraft 类型，组合边界可以在不启动游戏注册表时验证。</p>
 */
final class PlacementStateCombinationPlan {
    private PlacementStateCombinationPlan() {
    }

    static List<int[]> combinations(List<Integer> optionCounts, int limit) {
        if (optionCounts == null || optionCounts.isEmpty() || limit <= 0) {
            return Collections.emptyList();
        }
        for (Integer count : optionCounts) {
            if (count == null || count.intValue() <= 0) {
                return Collections.emptyList();
            }
        }
        List<int[]> result = new ArrayList<int[]>();
        append(optionCounts, 0, new int[optionCounts.size()], limit, result);
        return Collections.unmodifiableList(result);
    }

    static int pageCount(int choiceCount, int pageSize) {
        if (choiceCount <= 0 || pageSize <= 0) {
            return 0;
        }
        return (choiceCount + pageSize - 1) / pageSize;
    }

    private static void append(List<Integer> optionCounts, int propertyIndex,
            int[] current, int limit, List<int[]> output) {
        if (output.size() >= limit) {
            return;
        }
        if (propertyIndex >= optionCounts.size()) {
            output.add(current.clone());
            return;
        }
        int count = optionCounts.get(propertyIndex).intValue();
        for (int optionIndex = 0; optionIndex < count; optionIndex++) {
            current[propertyIndex] = optionIndex;
            append(optionCounts, propertyIndex + 1, current, limit, output);
            if (output.size() >= limit) {
                return;
            }
        }
    }
}
