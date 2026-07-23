package com.rtsbuilding.rtsbuilding.server.loadout;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.OptionalInt;

/**
 * 挖掘规则定义工具类，判断方块所需的工具角色和等级，以及玩家工具的匹配情况。
 * <p>
 * 将原版的方块挖掘标签系统映射到 RTS 挖掘装备栏系统，
 * 根据方块标签返回所需的工具角色，根据物品 ID 判断工具等级。
 */
public final class RtsMiningRules {
    /** 工具类，禁止实例化 */
    private RtsMiningRules() {
    }

    /**
     * 根据方块状态判断挖掘该方块所需的工具角色。
     * <p>
     * 匹配顺序：镐 → 锹 → 斧 → 锄，返回第一个匹配到的角色。
     *
     * @param state 要挖掘的方块状态
     * @return 所需的工具角色，如果不需要特定工具则返回 null
     */
    public static MiningLoadoutRole requiredRole(BlockState state) {
        if (state.is(BlockTags.MINEABLE_WITH_PICKAXE)) {
            return MiningLoadoutRole.PICK;
        }
        if (state.is(BlockTags.MINEABLE_WITH_SHOVEL)) {
            return MiningLoadoutRole.SHOVEL;
        }
        if (state.is(BlockTags.MINEABLE_WITH_AXE)) {
            return MiningLoadoutRole.AXE;
        }
        if (state.is(BlockTags.MINEABLE_WITH_HOE)) {
            return MiningLoadoutRole.HOE;
        }
        return null;
    }

    /**
     * 根据方块状态判断该方块所需的挖掘等级。
     * <p>
     * 等级定义：
     * <ul>
     *   <li>3 - 需要钻石工具（NEEDS_DIAMOND_TOOL）</li>
     *   <li>2 - 需要铁工具（NEEDS_IRON_TOOL）</li>
     *   <li>1 - 需要石工具（NEEDS_STONE_TOOL）</li>
     *   <li>0 - 无等级要求（徒手或任意工具均可）</li>
     * </ul>
     *
     * @param state 要挖掘的方块状态
     * @return 所需的挖掘等级
     */
    public static int requiredLevel(BlockState state) {
        if (state.is(BlockTags.NEEDS_DIAMOND_TOOL)) {
            return 3;
        }
        if (state.is(BlockTags.NEEDS_IRON_TOOL)) {
            return 2;
        }
        if (state.is(BlockTags.NEEDS_STONE_TOOL)) {
            return 1;
        }
        if (state.is(BlockTags.MINEABLE_WITH_PICKAXE)) {
            // 1.21.1 原版石头、平滑石头等普通镐类方块不一定带 NEEDS_STONE_TOOL；
            // 但对 RTS 生存平衡来说，它们仍然是“需要基础采掘插件”的对象。
            // 泥土、沙子等非镐类方块仍保持 0 级，可以无采掘插件范围挖掘。
            return 1;
        }
        return 0;
    }

    /**
     * 根据物品堆判断其工具等级。
     * <p>
     * 等级定义：
     * <ul>
     *   <li>4 - 下界合金工具（netherite）</li>
     *   <li>3 - 钻石工具（diamond）</li>
     *   <li>2 - 铁工具（iron）</li>
     *   <li>1 - 石/金工具（stone/golden）</li>
     *   <li>0 - 其他（木工具或非工具物品）</li>
     * </ul>
     *
     * @param stack 要判断的物品堆
     * @return 工具等级
     */
    public static int toolLevel(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        if (path.contains("netherite")) {
            return 4;
        }
        if (path.contains("diamond")) {
            return 3;
        }
        if (path.contains("iron")) {
            return 2;
        }
        if (path.contains("stone") || path.contains("golden")) {
            return 1;
        }
        return 0;
    }

    /**
     * 检查玩家是否在装备栏中绑定了符合要求的工具来挖掘指定方块。
     * <p>
     * 判断逻辑：
     * <ol>
     *   <li>先通过 {@link #requiredRole} 获取方块所需的工具角色</li>
     *   <li>如果不需要工具角色则直接返回 true</li>
     *   <li>通过 {@link MiningLoadoutState#getSlot} 获取玩家绑定的槽位</li>
     *   <li>检查工具等级是否达标，且是该方块的正确采集工具</li>
     * </ol>
     *
     * @param player 目标玩家
     * @param state  要挖掘的方块状态
     * @return 如果玩家装备栏中有合适的工具则返回 true，否则返回 false
     */
    public static boolean hasRequiredLoadoutTool(ServerPlayer player, BlockState state) {
        MiningLoadoutRole role = requiredRole(state);
        if (role == null) {
            return true;
        }

        OptionalInt slotOpt = MiningLoadoutState.getSlot(player, role);
        if (slotOpt.isEmpty()) {
            return false;
        }

        ItemStack toolStack = player.getInventory().getItem(slotOpt.getAsInt());
        if (toolStack.isEmpty()) {
            return false;
        }

        int required = requiredLevel(state);
        int actual = toolLevel(toolStack);
        return actual >= required && toolStack.isCorrectToolForDrops(state);
    }
}
