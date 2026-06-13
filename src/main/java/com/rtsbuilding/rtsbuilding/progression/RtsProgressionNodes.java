package com.rtsbuilding.rtsbuilding.progression;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.progression.cost.RtsCostOverrideManager;
import com.rtsbuilding.rtsbuilding.progression.cost.RtsCostSerialization;
import com.rtsbuilding.rtsbuilding.progression.node.RtsProgressionNodeId;
import com.rtsbuilding.rtsbuilding.progression.tree.MutableProgressionTree;
import com.rtsbuilding.rtsbuilding.progression.tree.RtsProgressionTreeBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.List;

/**
 * 科技树节点的集中访问入口（兼容层）。
 * <p>
 * 内部委托给新的模块化架构：
 * <ul>
 *   <li>{@link RtsProgressionTreeBuilder} + {@link MutableProgressionTree} — 树结构</li>
 *   <li>{@link RtsCostOverrideManager} — 成本覆写</li>
 *   <li>{@link RtsCostSerialization} — 成本序列化</li>
 * </ul>
 * <p>
 * 所有公共 API 签名保持不变，现有调用方无需修改。
 */
public final class RtsProgressionNodes {
    // ─── 常量（向后兼容，委托至 RtsProgressionNodeId） ───
    public static final ResourceLocation CAMERA_CORE = RtsProgressionNodeId.CAMERA_CORE;
    public static final ResourceLocation RADIUS_1 = RtsProgressionNodeId.RADIUS_1;
    public static final ResourceLocation RADIUS_2 = RtsProgressionNodeId.RADIUS_2;
    public static final ResourceLocation RADIUS_3 = RtsProgressionNodeId.RADIUS_3;
    public static final ResourceLocation RADIUS_MAX = RtsProgressionNodeId.RADIUS_MAX;
    public static final ResourceLocation STORAGE_LINK = RtsProgressionNodeId.STORAGE_LINK;
    public static final ResourceLocation REMOTE_PLACE = RtsProgressionNodeId.REMOTE_PLACE;
    public static final ResourceLocation REMOTE_BREAK = RtsProgressionNodeId.REMOTE_BREAK;
    public static final ResourceLocation ROTATE_BLOCK = RtsProgressionNodeId.ROTATE_BLOCK;
    public static final ResourceLocation AUTO_STORE_MINED = RtsProgressionNodeId.AUTO_STORE_MINED;
    public static final ResourceLocation FUNNEL = RtsProgressionNodeId.FUNNEL;
    public static final ResourceLocation FLUID_BUFFER = RtsProgressionNodeId.FLUID_BUFFER;
    public static final ResourceLocation REMOTE_GUI = RtsProgressionNodeId.REMOTE_GUI;
    public static final ResourceLocation CRAFT_TERMINAL = RtsProgressionNodeId.CRAFT_TERMINAL;
    public static final ResourceLocation JEI_TRANSFER = RtsProgressionNodeId.JEI_TRANSFER;
    public static final ResourceLocation ULTIMINE = RtsProgressionNodeId.ULTIMINE;
    public static final ResourceLocation AREA_DESTROY = RtsProgressionNodeId.AREA_DESTROY;
    public static final ResourceLocation BLUEPRINTS = RtsProgressionNodeId.BLUEPRINTS;
    public static final ResourceLocation FIELD_DEPLOYMENT = RtsProgressionNodeId.FIELD_DEPLOYMENT;

    // ─── 内部新架构的实例 ───
    private static final MutableProgressionTree TREE = RtsProgressionTreeBuilder.buildDefaultTree();
    private static final RtsCostOverrideManager COST_MANAGER = new RtsCostOverrideManager();

    static {
        // 首次加载时从配置文件注入本地覆写
        COST_MANAGER.setLocalOverrides(Config.progressionCostOverrides());
    }

    private RtsProgressionNodes() {
    }

    public static RtsProgressionNode get(ResourceLocation id) {
        return TREE.get(id);
    }

    public static Collection<RtsProgressionNode> all() {
        return TREE.all();
    }

    public static boolean contains(ResourceLocation id) {
        return TREE.contains(id);
    }

    public static List<RtsIngredientCost> costsFor(RtsProgressionNode node) {
        return COST_MANAGER.costsFor(node);
    }

    public static List<RtsIngredientCost> syncedCostsFor(RtsProgressionNode node) {
        return COST_MANAGER.syncedCostsFor(node);
    }

    public static void applySyncedCostOverrides(List<String> overrides) {
        COST_MANAGER.applySyncedOverrides(overrides);
    }

    public static String costTextFor(RtsProgressionNode node) {
        return RtsCostSerialization.format(costsFor(node));
    }

    public static String formatCostText(List<RtsIngredientCost> costs) {
        return RtsCostSerialization.format(costs);
    }

    /**
     * 获取内部科技树实例（供新架构代码使用）。
     */
    public static MutableProgressionTree tree() {
        return TREE;
    }

    /**
     * 获取内部成本覆写管理器（供新架构代码使用）。
     */
    public static RtsCostOverrideManager costManager() {
        return COST_MANAGER;
    }
}
