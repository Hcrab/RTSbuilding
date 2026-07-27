package com.rtsbuilding.rtsbuilding.common.blueprint.material;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;

import java.util.Collections;
import java.util.List;

/** 从可信方块状态推导建造材料，不相信导入文件自行声明的材料 ID。 */
public final class BlueprintMaterialResolver {
    private BlueprintMaterialResolver() {}

    public static List<ResourceLocation> materialItemIds(IBlockState state) {
        Item item = materialItem(state);
        ResourceLocation id = Item.REGISTRY.getNameForObject(item);
        return item == Items.AIR || id == null
                ? Collections.<ResourceLocation>emptyList()
                : Collections.singletonList(id);
    }

    public static Item materialItem(IBlockState state) {
        if (state == null || state.getBlock() == Blocks.AIR) return Items.AIR;
        Block block = state.getBlock();
        if (block == Blocks.FARMLAND || block == Blocks.GRASS_PATH) return Item.getItemFromBlock(Blocks.DIRT);
        if (block == Blocks.TALLGRASS || block == Blocks.DOUBLE_PLANT) return Item.getItemFromBlock(block);
        Item item = Item.getItemFromBlock(block);
        return item == null ? Items.AIR : item;
    }
}
