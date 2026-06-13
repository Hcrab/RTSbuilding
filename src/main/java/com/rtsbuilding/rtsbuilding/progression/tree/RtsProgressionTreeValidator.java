package com.rtsbuilding.rtsbuilding.progression.tree;

import com.rtsbuilding.rtsbuilding.progression.RtsProgressionNode;
import net.minecraft.resources.ResourceLocation;

import java.util.*;

/**
 * 科技树验证器，检测循环依赖和悬挂依赖。
 * <p>
 * 使用 Tarjan DFS 算法检测有向无环图（DAG）中的环。
 */
public final class RtsProgressionTreeValidator {

    private RtsProgressionTreeValidator() {
    }

    /**
     * 验证科技树的合法性。
     */
    public static ValidationResult validate(RtsProgressionTree tree) {
        List<String> errors = new ArrayList<>();
        Set<ResourceLocation> allIds = tree.all().stream()
                .map(RtsProgressionNode::id)
                .collect(java.util.stream.Collectors.toSet());

        // 1. 检查依赖节点是否存在
        for (RtsProgressionNode node : tree.all()) {
            for (ResourceLocation dep : node.dependencies()) {
                if (!allIds.contains(dep)) {
                    errors.add("节点 [" + node.id() + "] 依赖的 [" + dep + "] 不存在");
                }
            }
        }

        // 2. 检查循环依赖
        if (allIds.size() > 1) {
            Set<ResourceLocation> cycleNodes = detectCycle(tree, allIds);
            if (!cycleNodes.isEmpty()) {
                errors.add("检测到循环依赖，涉及节点: " + cycleNodes);
            }
        }

        return new ValidationResult(Collections.unmodifiableList(errors));
    }

    /**
     * 使用 DFS 检测循环依赖。
     * 返回参与循环的节点 ID 集合，无循环时返回空集。
     */
    private static Set<ResourceLocation> detectCycle(RtsProgressionTree tree, Set<ResourceLocation> allIds) {
        // WHITE=未访问, GRAY=访问中(当前路径), BLACK=已访问完成
        Map<ResourceLocation, Color> colors = new HashMap<>();
        for (ResourceLocation id : allIds) {
            colors.put(id, Color.WHITE);
        }

        Set<ResourceLocation> cycleNodes = new LinkedHashSet<>();

        for (ResourceLocation id : allIds) {
            if (colors.get(id) == Color.WHITE) {
                if (dfsVisit(tree, id, colors, cycleNodes)) {
                    // 有环，继续检查其他节点
                }
            }
        }

        return cycleNodes;
    }

    private static boolean dfsVisit(RtsProgressionTree tree, ResourceLocation id,
                                     Map<ResourceLocation, Color> colors,
                                     Set<ResourceLocation> cycleNodes) {
        colors.put(id, Color.GRAY);

        RtsProgressionNode node = tree.get(id);
        if (node != null) {
            for (ResourceLocation dep : node.dependencies()) {
                Color depColor = colors.get(dep);
                if (depColor == null) {
                    continue; // 依赖不存在，已在步骤1中报告
                }
                if (depColor == Color.GRAY) {
                    cycleNodes.add(dep);
                    cycleNodes.add(id);
                    return true;
                }
                if (depColor == Color.WHITE) {
                    if (dfsVisit(tree, dep, colors, cycleNodes)) {
                        cycleNodes.add(id);
                        return true;
                    }
                }
            }
        }

        colors.put(id, Color.BLACK);
        return false;
    }

    private enum Color {
        WHITE, GRAY, BLACK
    }

    /**
     * 验证结果。
     */
    public record ValidationResult(List<String> errors) {
        public boolean isValid() {
            return errors.isEmpty();
        }
    }
}
