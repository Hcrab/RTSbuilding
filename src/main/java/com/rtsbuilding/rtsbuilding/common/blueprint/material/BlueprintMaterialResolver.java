package com.rtsbuilding.rtsbuilding.common.blueprint.material;

import com.rtsbuilding.rtsbuilding.platform.RtsBuiltInRegistries;
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
 * 这样导入的结构文件可以继续携带旧版导出的材料字段，但无法通过该字段篡改生存模式扣料。</p>
 */
public final class BlueprintMaterialResolver {
    private BlueprintMaterialResolver() {
    }

    public static List<ResourceLocation> materialItemIds(BlockState state) {
        Item item = materialItem(state);
        if (item == Items.AIR) {
            return List.of();
        }
        ResourceLocation id = RtsBuiltInRegistries.ITEM.getKey(item);
        if (id == null || !RtsBuiltInRegistries.ITEM.containsKey(id)) {
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
            // 1.20.1 中“矮草”仍使用 GRASS 注册名；1.21 才更名为 SHORT_GRASS。
            return Items.GRASS;
        }
        if (block == Blocks.LARGE_FERN) {
            return Items.FERN;
        }
        Item item = block.asItem();
        return item == null ? Items.AIR : item;
    }
}
