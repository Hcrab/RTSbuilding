package com.rtsbuilding.rtsbuilding.server.loadout;

import com.rtsbuilding.rtsbuilding.platform.block.BlockState;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

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
        if (state == null) return null;
        String harvestTool = state.getBlock().getHarvestTool(state.getMetadata());
        if ("pickaxe".equalsIgnoreCase(harvestTool)) return MiningLoadoutRole.PICK;
        if ("shovel".equalsIgnoreCase(harvestTool) || "spade".equalsIgnoreCase(harvestTool)) {
            return MiningLoadoutRole.SHOVEL;
        }
        if ("axe".equalsIgnoreCase(harvestTool)) return MiningLoadoutRole.AXE;
        if ("hoe".equalsIgnoreCase(harvestTool)) return MiningLoadoutRole.HOE;
        return roleFromMaterial(state.getMaterial());
    }

    /**
     * 部分 1.12 原版方块直到 Forge 完整注册阶段才暴露 harvestTool；定向测试、
     * 早期生命周期和少数旧模组方块因此需要按原版材质补齐等价分类。
     */
    private static MiningLoadoutRole roleFromMaterial(Material material) {
        if (material == Material.rock || material == Material.iron || material == Material.anvil) {
            return MiningLoadoutRole.PICK;
        }
        if (material == Material.ground || material == Material.grass || material == Material.sand
                || material == Material.snow || material == Material.craftedSnow || material == Material.clay) {
            return MiningLoadoutRole.SHOVEL;
        }
        if (material == Material.wood || material == Material.gourd) {
            return MiningLoadoutRole.AXE;
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
        if (state == null) return 0;
        int forgeLevel = state.getBlock().getHarvestLevel(state.getMetadata());
        if (requiredRole(state) == MiningLoadoutRole.PICK) {
            // 1.21.1 原版石头、平滑石头等普通镐类方块不一定带 NEEDS_STONE_TOOL；
            // 但对 RTS 生存平衡来说，它们仍然是“需要基础采掘插件”的对象。
            // 泥土、沙子等非镐类方块仍保持 0 级，可以无采掘插件范围挖掘。
            return Math.max(1, forgeLevel);
        }
        return Math.max(0, forgeLevel);
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
        if (com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(stack)) {
            return 0;
        }
        ResourceLocation key = com.rtsbuilding.rtsbuilding.platform.registry.RtsRegistries.ITEMS.getNameForObject(stack.getItem());
        String path = key == null ? "" : key.getResourcePath();
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
    public static boolean hasRequiredLoadoutTool(EntityPlayerMP player, BlockState state) {
        MiningLoadoutRole role = requiredRole(state);
        if (role == null) {
            return true;
        }

        OptionalInt slotOpt = MiningLoadoutState.getSlot(player, role);
        if (!slotOpt.isPresent()) {
            return false;
        }

        ItemStack toolStack = player.inventory.getStackInSlot(slotOpt.getAsInt());
        if (com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(toolStack)) {
            return false;
        }

        int required = requiredLevel(state);
        int actual = toolLevel(toolStack);
        return actual >= required && net.minecraftforge.common.ForgeHooks.canToolHarvestBlock(
                state.getBlock(), state.getMetadata(), toolStack);
    }
}
