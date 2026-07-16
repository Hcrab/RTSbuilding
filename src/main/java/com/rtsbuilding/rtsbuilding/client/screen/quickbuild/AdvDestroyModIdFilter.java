package com.rtsbuilding.rtsbuilding.client.screen.quickbuild;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 按 Mod ID 匹配的过滤器。
 */
public final class AdvDestroyModIdFilter extends AdvDestroyFilter {

    private final String modId;

    public AdvDestroyModIdFilter(String modId) {
        this.modId = modId;
    }

    public String getModId() {
        return modId;
    }

    @Override
    public FilterType getType() {
        return FilterType.MOD_ID;
    }

    @Override
    public boolean matches(BlockState state, Level level, BlockPos pos) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace().equals(modId);
    }

    @Override
    public String getDisplayName() {
        return modId;
    }

    @Override
    public Serialized serialize() {
        return Serialized.modId(modId);
    }

    public static AdvDestroyModIdFilter deserialize(Serialized s) {
        if (s.modId == null || s.modId.isEmpty()) return null;
        return new AdvDestroyModIdFilter(s.modId);
    }
}
