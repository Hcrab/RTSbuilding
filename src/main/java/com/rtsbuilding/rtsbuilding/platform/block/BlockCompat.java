package com.rtsbuilding.rtsbuilding.platform.block;

import cpw.mods.fml.relauncher.ReflectionHelper;
import net.minecraft.block.BlockSlab;

import java.lang.reflect.Field;

/** 少数旧版方块把关键状态藏在字段中；反射只在此处集中使用。 */
public final class BlockCompat {
    private static final Field DOUBLE_SLAB = ReflectionHelper.findField(
            BlockSlab.class, "field_150004_a");

    private BlockCompat() {}

    public static boolean isDoubleSlab(BlockSlab slab) {
        if (slab == null) return false;
        try {
            return DOUBLE_SLAB.getBoolean(slab);
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException("无法读取 1.7.10 台阶类型", failure);
        }
    }
}
