package com.rtsbuilding.rtsbuilding.client.screen.mode;

import java.util.ArrayList;
import java.util.List;

/**
 * R 放置轮盘的纯组合与分页规划。
 *
 * <p>本类只处理每个属性有多少候选，不认识 Minecraft 方块、渲染或输入。
 * 各属性的第 0 项约定为当前幽灵值，因此生成结果的第 0 项也是当前完整状态。
 * 独立后可以在不启动 Minecraft registry 的普通单元测试中锁定分页行为。</p>
 */
final class PlacementStateCombinationPlan {
    private PlacementStateCombinationPlan() {
    }

    static List<int[]> combinations(List<Integer> optionCounts, int limit) {
        if (optionCounts == null || optionCounts.isEmpty() || limit <= 0
                || optionCounts.stream().anyMatch(count -> count == null || count <= 0)) {
            return List.of();
        }
        List<int[]> result = new ArrayList<>();
        append(optionCounts, 0, new int[optionCounts.size()], limit, result);
        return List.copyOf(result);
    }

    static int pageCount(int choiceCount, int pageSize) {
        if (choiceCount <= 0 || pageSize <= 0) {
            return 0;
        }
        return (choiceCount + pageSize - 1) / pageSize;
    }

    private static void append(
            List<Integer> optionCounts,
            int propertyIndex,
            int[] current,
            int limit,
            List<int[]> output) {
        if (output.size() >= limit) {
            return;
        }
        if (propertyIndex >= optionCounts.size()) {
            output.add(current.clone());
            return;
        }
        for (int optionIndex = 0; optionIndex < optionCounts.get(propertyIndex); optionIndex++) {
            current[propertyIndex] = optionIndex;
            append(optionCounts, propertyIndex + 1, current, limit, output);
            if (output.size() >= limit) {
                return;
            }
        }
    }
}
