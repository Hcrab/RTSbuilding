package com.rtsbuilding.rtsbuilding.common.blueprint.material;

import net.minecraft.block.Block;
import com.rtsbuilding.rtsbuilding.platform.block.BlockState;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;

import java.util.Collections;
import java.util.List;

/** 从可信方块状态推导建造材料，不相信导入文件自行声明的材料 ID。 */
public final class BlueprintMaterialResolver {
    private BlueprintMaterialResolver() {}

    public static List<ResourceLocation> materialItemIds(BlockState state) {
        Item item = materialItem(state);
        ResourceLocation id = com.rtsbuilding.rtsbuilding.platform.registry.RtsRegistries.ITEMS.getNameForObject(item);
        return item == null || id == null
                ? Collections.<ResourceLocation>emptyList()
                : Collections.singletonList(id);
    }

    public static Item materialItem(BlockState state) {
        if (state == null || state.getBlock() == Blocks.air) return null;
        Block block = state.getBlock();
        if (block == Blocks.farmland || block == Blocks.grass) return Item.getItemFromBlock(Blocks.dirt);
        if (block == Blocks.tallgrass || block == Blocks.double_plant) return Item.getItemFromBlock(block);
        Item item = Item.getItemFromBlock(block);
        return item == null ? null : item;
    }
}
