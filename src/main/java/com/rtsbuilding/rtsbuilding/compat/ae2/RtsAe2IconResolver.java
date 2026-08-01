package com.rtsbuilding.rtsbuilding.compat.ae2;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/** 在没有 AE2 1.21.1 Fabric 发行物时，为绑定项提供安全的方块物品图标回退。 */
public final class RtsAe2IconResolver {
    private RtsAe2IconResolver() {
    }

    public static String resolveGuiBindingIconItemId(
            Level level, BlockPos pos, Direction face, String labelHint) {
        if (level == null || pos == null || !level.hasChunkAt(pos)) {
            return "";
        }
        Item item = level.getBlockState(pos).getBlock().asItem();
        if (item == Items.AIR) {
            return "";
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        return id == null ? "" : id.toString();
    }
}
