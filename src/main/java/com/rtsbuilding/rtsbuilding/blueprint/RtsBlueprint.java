package com.rtsbuilding.rtsbuilding.blueprint;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;

public record RtsBlueprint(
        String name,
        String sourceName,
        BlueprintFormat format,
        Vec3i size,
        List<RtsBlueprintBlock> blocks,
        Map<ResourceLocation, Integer> requiredItems) {
    public static RtsBlueprint create(
            String name,
            String sourceName,
            BlueprintFormat format,
            Vec3i size,
            List<RtsBlueprintBlock> blocks) {
        Map<ResourceLocation, Integer> requirements = new LinkedHashMap<>();
        for (RtsBlueprintBlock block : blocks) {
            if (block.isMissingBlock()) {
                continue;
            }
            for (ResourceLocation id : materialItemIds(block)) {
                requirements.merge(id, 1, Integer::sum);
            }
        }
        return new RtsBlueprint(
                name == null || name.isBlank() ? sourceName : name,
                sourceName == null ? "" : sourceName,
                format,
                size,
                List.copyOf(blocks),
                Collections.unmodifiableMap(requirements));
    }

    public int blockCount() {
        return this.blocks.size();
    }

    public static List<ResourceLocation> materialItemIds(RtsBlueprintBlock block) {
        if (block == null || block.isMissingBlock()) {
            return List.of();
        }
        return BlueprintMaterialResolver.materialItemIds(block.state());
    }
}
