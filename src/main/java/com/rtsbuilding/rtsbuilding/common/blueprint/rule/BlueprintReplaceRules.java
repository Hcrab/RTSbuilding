package com.rtsbuilding.rtsbuilding.common.blueprint.rule;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Set;

/**
 * 蓝图方块替换规则 —— 定义蓝图放置时可以被替换的方块类型。
 * <p>
 * 当一个方块标记为"可软替换"时，蓝图系统在放置方块时可以直接覆盖它，
 * 无需玩家手动清理。包含标签系统和内部硬编码列表两种方式。
 */
public final class BlueprintReplaceRules {

    /** 蓝图软替换方块标签 —— 通过数据包扩展的软替换方块集 */
    public static final TagKey<Block> SOFT_REPLACEABLE = TagKey.create(
            Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(RtsbuildingMod.MODID, "blueprint_soft_replaceable"));

    /** 原版软替换方块硬编码列表 —— 花、草、蘑菇、藤蔓等易替换方块 */
    private static final Set<ResourceLocation> VANILLA_SOFT_REPLACEABLE = Set.of(
            vanilla("short_grass"),      // 矮草丛
            vanilla("tall_grass"),       // 高草丛
            vanilla("fern"),             // 蕨
            vanilla("large_fern"),       // 大型蕨
            vanilla("dead_bush"),        // 枯死的灌木
            vanilla("dandelion"),        // 蒲公英
            vanilla("poppy"),            // 虞美人
            vanilla("blue_orchid"),      // 兰花
            vanilla("allium"),           // 绒球葱
            vanilla("azure_bluet"),      // 茜草花
            vanilla("red_tulip"),        // 红色郁金香
            vanilla("orange_tulip"),     // 橙色郁金香
            vanilla("white_tulip"),      // 白色郁金香
            vanilla("pink_tulip"),       // 粉色郁金香
            vanilla("oxeye_daisy"),      // 滨菊
            vanilla("cornflower"),       // 矢车菊
            vanilla("lily_of_the_valley"), // 铃兰
            vanilla("torchflower"),      // 火把花
            vanilla("wither_rose"),      // 凋灵玫瑰
            vanilla("sunflower"),        // 向日葵
            vanilla("lilac"),            // 丁香
            vanilla("rose_bush"),        // 玫瑰丛
            vanilla("peony"),            // 牡丹
            vanilla("pitcher_plant"),    // 猪笼草
            vanilla("brown_mushroom"),   // 棕色蘑菇
            vanilla("red_mushroom"),     // 红色蘑菇
            vanilla("crimson_roots"),    // 绯红菌索
            vanilla("warped_roots"),     // 诡异菌索
            vanilla("nether_sprouts"),   // 下界苗
            vanilla("vine"),             // 藤蔓
            vanilla("cave_vines"),       // 洞穴藤蔓
            vanilla("cave_vines_plant"), // 洞穴藤蔓植株
            vanilla("twisting_vines"),   // 缠怨藤
            vanilla("twisting_vines_plant"), // 缠怨藤植株
            vanilla("weeping_vines"),    // 垂泪藤
            vanilla("weeping_vines_plant"), // 垂泪藤植株
            vanilla("glow_lichen"),      // 发光地衣
            vanilla("hanging_roots"),    // 垂根
            vanilla("pink_petals"),      // 粉红色花簇
            vanilla("moss_carpet"),      // 苔藓地毯
            vanilla("snow"),             // 雪
            vanilla("seagrass"),         // 海草
            vanilla("tall_seagrass"));   // 高海草

    private BlueprintReplaceRules() {
    }

    /**
     * 判断蓝图是否可以直接替换指定方块状态。
     * <p>
     * 替换条件：空气、可替换方块、或在软替换标签/列表中。
     *
     * @param state 要检查的方块状态
     * @return true 如果该方块可以被蓝图直接替换
     */
    public static boolean canBlueprintReplace(BlockState state) {
        if (state == null || state.isAir() || state.canBeReplaced() || state.is(SOFT_REPLACEABLE)) {
            return true;
        }
        return VANILLA_SOFT_REPLACEABLE.contains(BuiltInRegistries.BLOCK.getKey(state.getBlock()));
    }

    /** 创建 minecraft 命名空间的 {@link ResourceLocation} */
    private static ResourceLocation vanilla(String path) {
        return ResourceLocation.fromNamespaceAndPath("minecraft", path);
    }
}
