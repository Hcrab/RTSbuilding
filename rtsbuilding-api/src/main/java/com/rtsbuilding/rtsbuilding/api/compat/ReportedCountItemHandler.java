package com.rtsbuilding.rtsbuilding.api.compat;

import net.neoforged.neoforge.items.IItemHandler;

public interface ReportedCountItemHandler extends IItemHandler {
    long getReportedCount(int slot);
}
