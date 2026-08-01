package com.rtsbuilding.rtsbuilding.compat;

import com.rtsbuilding.rtsbuilding.platform.item.RtsItemHandler;

/**
 * Optional extension for {@link RtsItemHandler} implementations that can report
 * the actual network-level count of items in a slot, which may exceed the
 * vanilla {@link net.minecraft.world.item.ItemStack#getMaxStackSize()} limit.
 */
public interface ReportedCountItemHandler {
    long getReportedCount(int slot);
}
