package com.rtsbuilding.rtsbuilding.blueprint;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * 蓝图材料解析器。
 *
 * <p>这个类只根据可信的 {@link BlockState} 推导放置成本，不读取蓝图 NBT 里声明的物品 ID。
 * 这样导入文件可以继续携带旧版导出的材料字段，但无法通过该字段篡改生存模式扣料。</p>
 */
public final class BlueprintMaterialResolver {
    private BlueprintMaterialResolver() {
    }

    public static List<ResourceLocation> materialItemIds(BlockState state) {
        Item item = materialItem(state);
        if (item == Items.AIR) {
            return List.of();
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
            return List.of();
        }
        return List.of(id);
    }

    public static Item materialItem(BlockState state) {
        if (state == null || state.isAir()) {
            return Items.AIR;
        }
        Block block = state.getBlock();
        if (block == Blocks.FARMLAND || block == Blocks.DIRT_PATH) {
            return Items.DIRT;
        }
        if (block == Blocks.TALL_GRASS) {
            return Items.GRASS;
        }
        if (block == Blocks.LARGE_FERN) {
            return Items.FERN;
        }
        Item item = block.asItem();
        return item == null ? Items.AIR : item;
    }
}
