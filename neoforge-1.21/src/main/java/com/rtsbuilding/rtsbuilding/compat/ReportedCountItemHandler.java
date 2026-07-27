package com.rtsbuilding.rtsbuilding.compat;

import net.neoforged.neoforge.items.IItemHandler;

/**
 * Optional extension for {@link IItemHandler} implementations that can report
 * the actual network-level count of items in a slot, which may exceed the
 * vanilla {@link net.minecraft.world.item.ItemStack#getMaxStackSize()} limit.
 */
public interface ReportedCountItemHandler {
    long getReportedCount(int slot);
}
