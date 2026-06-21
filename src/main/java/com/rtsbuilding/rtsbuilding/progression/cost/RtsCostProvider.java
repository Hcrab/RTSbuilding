package com.rtsbuilding.rtsbuilding.progression.cost;

import com.rtsbuilding.rtsbuilding.progression.RtsIngredientCost;
import com.rtsbuilding.rtsbuilding.progression.RtsProgressionNode;

import java.util.List;

/**
 * 成本提供者接口，抽象成本的来源（默认成本 / 本地配置覆写 / 网络同步覆写???
 */
@FunctionalInterface
public interface RtsCostProvider {
    /**
     * 获取指定节点的解锁成本（已考虑所有覆写）??
     */
    List<RtsIngredientCost> costsFor(RtsProgressionNode node);
}
