package com.rtsbuilding.rtsbuilding.progression.tree;

import com.rtsbuilding.rtsbuilding.progression.RtsProgressionNode;
import net.minecraft.resources.ResourceLocation;

import java.util.Collection;

/**
 * 科技树接口，定义对科技树节点的只读访问??
 * <p>
 * 支持基于注册表的可扩展设计，可被 Datapack / 其他 Mod 扩展??
 */
public interface RtsProgressionTree {

    /**
     * 根据 ID 获取节点??
     */
    RtsProgressionNode get(ResourceLocation id);

    /**
     * 获取所有注册的节点??
     */
    Collection<RtsProgressionNode> all();

    /**
     * 检查指??ID 的节点是否存在于树中??
     */
    boolean contains(ResourceLocation id);
}
