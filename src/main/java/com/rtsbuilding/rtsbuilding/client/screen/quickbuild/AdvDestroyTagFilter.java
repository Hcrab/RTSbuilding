package com.rtsbuilding.rtsbuilding.client.screen.quickbuild;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 按方块标签匹配的过滤器。
 */
public final class AdvDestroyTagFilter extends AdvDestroyFilter {

    private final ResourceLocation tagId;
    private transient TagKey<Block> cachedTag;

    public AdvDestroyTagFilter(ResourceLocation tagId) {
        this.tagId = tagId;
        this.cachedTag = TagKey.create(BuiltInRegistries.BLOCK.key(), tagId);
    }

    public ResourceLocation getTagId() {
        return tagId;
    }

    @Override
    public FilterType getType() {
        return FilterType.TAG;
    }

    @Override
    public boolean matches(BlockState state, Level level, BlockPos pos) {
        if (cachedTag == null) {
            cachedTag = TagKey.create(BuiltInRegistries.BLOCK.key(), tagId);
        }
        return state.is(cachedTag);
    }

    @Override
    public String getDisplayName() {
        return "#" + tagId;
    }

    @Override
    public Serialized serialize() {
        return Serialized.tag(tagId.toString());
    }

    public static AdvDestroyTagFilter deserialize(Serialized s) {
        ResourceLocation id = ResourceLocation.tryParse(s.tagId);
        if (id == null) return null;
        return new AdvDestroyTagFilter(id);
    }
}
