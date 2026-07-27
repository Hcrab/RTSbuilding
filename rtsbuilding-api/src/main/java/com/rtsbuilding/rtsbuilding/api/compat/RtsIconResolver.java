package com.rtsbuilding.rtsbuilding.api.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import javax.annotation.Nullable;

public interface RtsIconResolver {

    String getModId();

    @Nullable
    String resolveIconId(Level level, BlockPos pos, @Nullable Direction face, String label);
}
