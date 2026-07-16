package com.rtsbuilding.rtsbuilding.client.screen.quickbuild;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 按具体物品/方块匹配的过滤器。
 */
public final class AdvDestroyItemStackFilter extends AdvDestroyFilter {

    private final ResourceLocation itemId;

    public AdvDestroyItemStackFilter(ResourceLocation itemId) {
        this.itemId = itemId;
    }

    public ResourceLocation getItemId() {
        return itemId;
    }

    @Override
    public FilterType getType() {
        return FilterType.ITEM_STACK;
    }

    @Override
    public boolean matches(BlockState state, Level level, BlockPos pos) {
        Block block = state.getBlock();
        ResourceLocation blockItemId = BuiltInRegistries.ITEM.getKey(block.asItem());
        return blockItemId.equals(itemId);
    }

    @Override
    public String getDisplayName() {
        return itemId.toString();
    }

    @Override
    public Serialized serialize() {
        return Serialized.itemStack(itemId.toString());
    }

    public static AdvDestroyItemStackFilter deserialize(Serialized s) {
        ResourceLocation id = ResourceLocation.tryParse(s.itemId);
        if (id == null) return null;
        return new AdvDestroyItemStackFilter(id);
    }
}
