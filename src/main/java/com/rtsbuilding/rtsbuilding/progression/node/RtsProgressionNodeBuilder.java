package com.rtsbuilding.rtsbuilding.progression.node;

import com.rtsbuilding.rtsbuilding.progression.RtsIngredientCost;
import com.rtsbuilding.rtsbuilding.progression.RtsProgressionNode;
import com.rtsbuilding.rtsbuilding.progression.RtsUnlockEffect;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 类型安全??{@link RtsProgressionNode} 建造???
 * <p>
 * 替代旧版 {@code cost(Object...)} 变参方法，在编译时保证类型安???
 * 用法??
 * <pre>{@code
 * RtsProgressionNodeBuilder.of(RtsProgressionNodeId.CAMERA_CORE)
 *     .effects(RtsUnlockEffect.unlock(RtsFeature.CAMERA))
 *     .costs(new ResourceLocation("minecraft:glass"), 8)
 *     .at(0, 0)
 *     .build()
 * }</pre>
 */
public final class RtsProgressionNodeBuilder {
    private final ResourceLocation id;
    private final List<ResourceLocation> dependencies = new ArrayList<>();
    private final List<RtsIngredientCost> costs = new ArrayList<>();
    private final List<RtsUnlockEffect> effects = new ArrayList<>();
    private int x;
    private int y;

    private RtsProgressionNodeBuilder(ResourceLocation id) {
        this.id = id;
    }

    public static RtsProgressionNodeBuilder of(ResourceLocation id) {
        return new RtsProgressionNodeBuilder(id);
    }

    public RtsProgressionNodeBuilder dependsOn(ResourceLocation first, ResourceLocation... rest) {
        this.dependencies.add(first);
        Collections.addAll(this.dependencies, rest);
        return this;
    }

    public RtsProgressionNodeBuilder costs(ResourceLocation itemId, int count) {
        this.costs.add(new RtsIngredientCost(itemId, count));
        return this;
    }

    public RtsProgressionNodeBuilder effects(RtsUnlockEffect first, RtsUnlockEffect... rest) {
        this.effects.add(first);
        Collections.addAll(this.effects, rest);
        return this;
    }

    public RtsProgressionNodeBuilder at(int x, int y) {
        this.x = x;
        this.y = y;
        return this;
    }

    public RtsProgressionNode build() {
        return new RtsProgressionNode(
                id,
                "rtsbuilding.progression." + id.getPath(),
                "rtsbuilding.progression." + id.getPath() + ".desc",
                List.copyOf(dependencies),
                List.copyOf(costs),
                List.copyOf(effects),
                x,
                y);
    }
}
