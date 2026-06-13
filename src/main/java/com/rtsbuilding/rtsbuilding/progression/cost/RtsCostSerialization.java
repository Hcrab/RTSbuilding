package com.rtsbuilding.rtsbuilding.progression.cost;

import com.rtsbuilding.rtsbuilding.progression.RtsIngredientCost;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * 成本字符串序列化/反序列化工具。
 * <p>
 * 纯函数、无状态，方便单元测试。
 * 格式：{@code minecraft:glass:8,minecraft:redstone:12}
 */
public final class RtsCostSerialization {

    /**
     * 将文本解析为成本列表，解析失败返回 fallback。
     */
    public static List<RtsIngredientCost> parse(String text, List<RtsIngredientCost> fallback) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        ArrayList<RtsIngredientCost> out = new ArrayList<>();
        String[] parts = text.split(",");
        for (String rawPart : parts) {
            String part = rawPart.trim();
            if (part.isBlank()) {
                continue;
            }
            // 用 lastIndexOf 分割，因为物品 ID 本身可能包含 ':'
            int split = part.lastIndexOf(':');
            if (split <= 0 || split >= part.length() - 1) {
                return fallback;
            }
            try {
                ResourceLocation itemId = ResourceLocation.parse(part.substring(0, split));
                int count = Math.max(1, Integer.parseInt(part.substring(split + 1)));
                out.add(new RtsIngredientCost(itemId, count));
            } catch (RuntimeException ignored) {
                return fallback;
            }
        }
        return List.copyOf(out);
    }

    /**
     * 将成本列表格式化为文本。
     */
    public static String format(List<RtsIngredientCost> costs) {
        if (costs == null || costs.isEmpty()) {
            return "";
        }
        ArrayList<String> parts = new ArrayList<>(costs.size());
        for (RtsIngredientCost cost : costs) {
            parts.add(cost.itemId() + ":" + cost.count());
        }
        return String.join(",", parts);
    }

    private RtsCostSerialization() {
    }
}
