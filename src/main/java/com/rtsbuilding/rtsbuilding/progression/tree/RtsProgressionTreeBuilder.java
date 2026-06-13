package com.rtsbuilding.rtsbuilding.progression.tree;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.progression.RtsFeature;
import com.rtsbuilding.rtsbuilding.progression.RtsUnlockEffect;
import com.rtsbuilding.rtsbuilding.progression.node.RtsProgressionNodeBuilder;
import com.rtsbuilding.rtsbuilding.progression.node.RtsProgressionNodeId;
import net.minecraft.resources.ResourceLocation;

/**
 * 科技树构建器，负责组装默认的科技树。
 * <p>
 * 替代旧版 {@code RtsProgressionNodes.buildNodes()} 硬编码方式。
 * 使用 {@link RtsProgressionNodeBuilder} 确保编译时类型安全。
 */
public final class RtsProgressionTreeBuilder {

    /**
     * 构建默认科技树，包含所有内置节点。
     */
    public static MutableProgressionTree buildDefaultTree() {
        MutableProgressionTree tree = new MutableProgressionTree();

        // ─── 相机核心（根节点，无依赖） ───
        tree.register(RtsProgressionNodeBuilder.of(RtsProgressionNodeId.CAMERA_CORE)
                .effects(
                        RtsUnlockEffect.unlock(RtsFeature.CAMERA),
                        RtsUnlockEffect.unlock(RtsFeature.INTERACT),
                        RtsUnlockEffect.radius(16))
                .at(0, 0).build());

        // ─── 作用半径扩展链 ───
        tree.register(RtsProgressionNodeBuilder.of(RtsProgressionNodeId.RADIUS_1)
                .dependsOn(RtsProgressionNodeId.CAMERA_CORE)
                .costs(rl("minecraft:glass"), 8)
                .effects(RtsUnlockEffect.radius(16))
                .at(1, 0).build());

        tree.register(RtsProgressionNodeBuilder.of(RtsProgressionNodeId.RADIUS_2)
                .dependsOn(RtsProgressionNodeId.RADIUS_1)
                .costs(rl("minecraft:redstone"), 12)
                .effects(RtsUnlockEffect.radius(32))
                .at(2, 0).build());

        tree.register(RtsProgressionNodeBuilder.of(RtsProgressionNodeId.RADIUS_3)
                .dependsOn(RtsProgressionNodeId.RADIUS_2)
                .costs(rl("minecraft:ender_pearl"), 2)
                .effects(RtsUnlockEffect.radius(48))
                .at(3, 0).build());

        tree.register(RtsProgressionNodeBuilder.of(RtsProgressionNodeId.RADIUS_MAX)
                .dependsOn(RtsProgressionNodeId.RADIUS_3)
                .costs(rl("minecraft:netherite_ingot"), 1)
                .effects(RtsUnlockEffect.radius(Config.maxActionRadiusBlocks()))
                .at(4, 0).build());

        // ─── 存储与远程操作 ───
        tree.register(RtsProgressionNodeBuilder.of(RtsProgressionNodeId.STORAGE_LINK)
                .dependsOn(RtsProgressionNodeId.CAMERA_CORE)
                .costs(rl("minecraft:chest"), 2)
                .costs(rl("minecraft:redstone"), 8)
                .effects(
                        RtsUnlockEffect.unlock(RtsFeature.LINK_STORAGE),
                        RtsUnlockEffect.unlock(RtsFeature.STORAGE_BROWSER))
                .at(1, 1).build());

        tree.register(RtsProgressionNodeBuilder.of(RtsProgressionNodeId.REMOTE_PLACE)
                .dependsOn(RtsProgressionNodeId.STORAGE_LINK)
                .costs(rl("minecraft:copper_ingot"), 16)
                .effects(RtsUnlockEffect.unlock(RtsFeature.REMOTE_PLACE))
                .at(2, 1).build());

        tree.register(RtsProgressionNodeBuilder.of(RtsProgressionNodeId.REMOTE_BREAK)
                .dependsOn(RtsProgressionNodeId.REMOTE_PLACE)
                .costs(rl("minecraft:iron_pickaxe"), 1)
                .costs(rl("minecraft:redstone"), 8)
                .effects(RtsUnlockEffect.unlock(RtsFeature.REMOTE_BREAK))
                .at(3, 1).build());

        tree.register(RtsProgressionNodeBuilder.of(RtsProgressionNodeId.ROTATE_BLOCK)
                .dependsOn(RtsProgressionNodeId.CAMERA_CORE)
                .costs(rl("minecraft:stick"), 4)
                .costs(rl("minecraft:copper_ingot"), 8)
                .effects(RtsUnlockEffect.unlock(RtsFeature.ROTATE_BLOCK))
                .at(1, -1).build());

        // ─── 蓝图 ───
        tree.register(RtsProgressionNodeBuilder.of(RtsProgressionNodeId.BLUEPRINTS)
                .dependsOn(RtsProgressionNodeId.CAMERA_CORE)
                .costs(rl("minecraft:paper"), 1)
                .costs(rl("minecraft:lapis_lazuli"), 1)
                .effects(RtsUnlockEffect.unlock(RtsFeature.BLUEPRINTS))
                .at(1, 3).build());

        // ─── 自动存储与漏斗 ───
        tree.register(RtsProgressionNodeBuilder.of(RtsProgressionNodeId.AUTO_STORE_MINED)
                .dependsOn(RtsProgressionNodeId.STORAGE_LINK)
                .costs(rl("minecraft:hopper"), 1)
                .effects(RtsUnlockEffect.unlock(RtsFeature.AUTO_STORE_MINED_DROPS))
                .at(2, 2).build());

        tree.register(RtsProgressionNodeBuilder.of(RtsProgressionNodeId.FUNNEL)
                .dependsOn(RtsProgressionNodeId.STORAGE_LINK)
                .costs(rl("minecraft:hopper"), 4)
                .costs(rl("minecraft:redstone"), 8)
                .effects(RtsUnlockEffect.unlock(RtsFeature.FUNNEL))
                .at(3, 2).build());

        // ─── 流体处理 ───
        tree.register(RtsProgressionNodeBuilder.of(RtsProgressionNodeId.FLUID_BUFFER)
                .dependsOn(RtsProgressionNodeId.STORAGE_LINK)
                .costs(rl("minecraft:bucket"), 4)
                .costs(rl("minecraft:iron_ingot"), 16)
                .effects(
                        RtsUnlockEffect.unlock(RtsFeature.FLUID_HANDLING),
                        RtsUnlockEffect.fluidCapacityBuckets(100))
                .at(2, 3).build());

        // ─── 远程 GUI 与合成 ───
        tree.register(RtsProgressionNodeBuilder.of(RtsProgressionNodeId.REMOTE_GUI)
                .dependsOn(RtsProgressionNodeId.STORAGE_LINK)
                .costs(rl("minecraft:comparator"), 1)
                .costs(rl("minecraft:redstone"), 16)
                .effects(RtsUnlockEffect.unlock(RtsFeature.REMOTE_GUI_BINDING))
                .at(2, -1).build());

        tree.register(RtsProgressionNodeBuilder.of(RtsProgressionNodeId.CRAFT_TERMINAL)
                .dependsOn(RtsProgressionNodeId.STORAGE_LINK)
                .costs(rl("minecraft:crafting_table"), 1)
                .costs(rl("minecraft:iron_ingot"), 12)
                .effects(RtsUnlockEffect.unlock(RtsFeature.CRAFT_TERMINAL))
                .at(3, -1).build());

        tree.register(RtsProgressionNodeBuilder.of(RtsProgressionNodeId.JEI_TRANSFER)
                .dependsOn(RtsProgressionNodeId.CRAFT_TERMINAL)
                .costs(rl("minecraft:book"), 1)
                .costs(rl("minecraft:lapis_lazuli"), 8)
                .effects(RtsUnlockEffect.unlock(RtsFeature.JEI_TRANSFER))
                .at(4, -1).build());

        // ─── 连锁挖掘与范围破坏 ───
        tree.register(RtsProgressionNodeBuilder.of(RtsProgressionNodeId.ULTIMINE)
                .dependsOn(RtsProgressionNodeId.AUTO_STORE_MINED)
                .costs(rl("minecraft:diamond_pickaxe"), 1)
                .costs(rl("minecraft:redstone_block"), 1)
                .effects(
                        RtsUnlockEffect.unlock(RtsFeature.ULTIMINE),
                        RtsUnlockEffect.ultimineLimit(64))
                .at(3, 3).build());

        tree.register(RtsProgressionNodeBuilder.of(RtsProgressionNodeId.AREA_DESTROY)
                .dependsOn(RtsProgressionNodeId.ULTIMINE)
                .costs(rl("minecraft:beacon"), 1)
                .effects(RtsUnlockEffect.unlock(RtsFeature.AREA_DESTROY))
                .at(4, 3).build());

        // ─── 外场部署 ───
        tree.register(RtsProgressionNodeBuilder.of(RtsProgressionNodeId.FIELD_DEPLOYMENT)
                .dependsOn(RtsProgressionNodeId.RADIUS_MAX)
                .costs(rl("minecraft:dragon_head"), 1)
                .effects(RtsUnlockEffect.bypassHomeRadius())
                .at(5, 0).build());

        return tree;
    }

    private static ResourceLocation rl(String string) {
        return ResourceLocation.parse(string);
    }

    private RtsProgressionTreeBuilder() {
    }
}
