package com.rtsbuilding.rtsbuilding.common.blueprint.model;

import com.rtsbuilding.rtsbuilding.common.blueprint.material.BlueprintMaterialResolver;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.Vec3i;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Java 8 蓝图数据载体；访问器名称与主线 record 保持一致。 */
public final class RtsBlueprint {
    private final String name;
    private final String sourceName;
    private final BlueprintFormat format;
    private final Vec3i size;
    private final List<RtsBlueprintBlock> blocks;
    private final Map<ResourceLocation, Integer> requiredItems;

    public RtsBlueprint(String name, String sourceName, BlueprintFormat format, Vec3i size,
                        List<RtsBlueprintBlock> blocks, Map<ResourceLocation, Integer> requiredItems) {
        this.name = name;
        this.sourceName = sourceName;
        this.format = format;
        this.size = size;
        this.blocks = Collections.unmodifiableList(new ArrayList<RtsBlueprintBlock>(blocks));
        this.requiredItems = Collections.unmodifiableMap(new LinkedHashMap<ResourceLocation, Integer>(requiredItems));
    }

    public static RtsBlueprint create(String name, String sourceName, BlueprintFormat format, Vec3i size,
                                      List<RtsBlueprintBlock> blocks) {
        Map<ResourceLocation, Integer> requirements = new LinkedHashMap<ResourceLocation, Integer>();
        for (RtsBlueprintBlock block : blocks) {
            if (block.isMissingBlock()) continue;
            for (ResourceLocation id : materialItemIds(block)) {
                Integer count = requirements.get(id);
                requirements.put(id, count == null ? 1 : count + 1);
            }
        }
        return new RtsBlueprint(isBlank(name) ? sourceName : name, sourceName == null ? "" : sourceName,
                format, size, blocks, requirements);
    }

    public String name() { return name; }
    public String sourceName() { return sourceName; }
    public BlueprintFormat format() { return format; }
    public Vec3i size() { return size; }
    public List<RtsBlueprintBlock> blocks() { return blocks; }
    public Map<ResourceLocation, Integer> requiredItems() { return requiredItems; }
    public int blockCount() { return blocks.size(); }

    public static List<ResourceLocation> materialItemIds(RtsBlueprintBlock block) {
        return block == null || block.isMissingBlock()
                ? Collections.<ResourceLocation>emptyList()
                : BlueprintMaterialResolver.materialItemIds(block.state());
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
