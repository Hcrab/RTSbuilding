package com.rtsbuilding.rtsbuilding.platform.registry;

import cpw.mods.fml.common.registry.GameData;
import net.minecraft.block.Block;
import net.minecraft.item.Item;

/** 1.7.10/GTNH 的方块与物品注册表入口。 */
public final class RtsRegistries {
    public static final RtsRegistry<Block> BLOCKS = new RtsRegistry<Block>(GameData.getBlockRegistry());
    public static final RtsRegistry<Item> ITEMS = new RtsRegistry<Item>(GameData.getItemRegistry());

    private RtsRegistries() {}
}
