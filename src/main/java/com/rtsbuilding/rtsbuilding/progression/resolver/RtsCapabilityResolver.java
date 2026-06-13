package com.rtsbuilding.rtsbuilding.progression.resolver;

import com.rtsbuilding.rtsbuilding.progression.RtsFeature;
import com.rtsbuilding.rtsbuilding.progression.RtsProgressionNode;
import com.rtsbuilding.rtsbuilding.progression.RtsUnlockEffect;
import com.rtsbuilding.rtsbuilding.progression.tree.RtsProgressionTree;
import net.minecraft.resources.ResourceLocation;

import java.util.EnumSet;
import java.util.Set;

/**
 * 能力推导器，根据已解锁节点集合推导出玩家的完整能力集??
 * <p>
 * ??{@link RtsProgressionTree} 中读取节点定义，遍历所有已解锁节点的效果，
 * 计算出最终的 {@link DerivedCapabilities}??
 */
public final class RtsCapabilityResolver {

    private final RtsProgressionTree tree;

    public RtsCapabilityResolver(RtsProgressionTree tree) {
        this.tree = tree;
    }

    /**
     * 根据已解锁节点推导能???
     */
    public DerivedCapabilities resolve(Set<ResourceLocation> unlockedNodeIds) {
        EnumSet<RtsFeature> features = EnumSet.noneOf(RtsFeature.class);
        int radius = 0;
        int fluidCapacity = 0;
        int ultimineLimit = 0;
        boolean bypassHomeRadius = false;

        if (unlockedNodeIds == null || unlockedNodeIds.isEmpty()) {
            return new DerivedCapabilities(features, radius, fluidCapacity, ultimineLimit, bypassHomeRadius);
        }

        for (ResourceLocation nodeId : unlockedNodeIds) {
            RtsProgressionNode node = tree.get(nodeId);
            if (node == null) continue;

            for (RtsUnlockEffect effect : node.effects()) {
                switch (effect.type()) {
                    case UNLOCK_FEATURE -> {
                        if (effect.feature() != null) {
                            features.add(effect.feature());
                        }
                    }
                    case SET_RADIUS_BLOCKS -> radius = Math.max(radius, effect.value());
                    case SET_FLUID_CAPACITY_BUCKETS -> fluidCapacity = Math.max(fluidCapacity, effect.value());
                    case SET_ULTIMINE_LIMIT -> ultimineLimit = Math.max(ultimineLimit, effect.value());
                    case BYPASS_HOME_RADIUS -> bypassHomeRadius = true;
                }
            }
        }

        return new DerivedCapabilities(features, radius, fluidCapacity, ultimineLimit, bypassHomeRadius);
    }

    /**
     * 已推导的玩家能力，不可变??
     */
    public record DerivedCapabilities(
            EnumSet<RtsFeature> features,
            int actionRadius,
            int fluidCapacityBuckets,
            int ultimineLimit,
            boolean bypassHomeRadius
    ) {
        public DerivedCapabilities {
            features = EnumSet.copyOf(features);
        }
    }
}
