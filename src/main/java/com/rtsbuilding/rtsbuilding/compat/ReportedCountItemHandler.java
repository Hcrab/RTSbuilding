package com.rtsbuilding.rtsbuilding.compat;

import net.minecraftforge.items.IItemHandler;

/** 允许虚拟槽报告超过原版栈上限的真实网络数量。 */
public interface ReportedCountItemHandler {
    long getReportedCount(int slot);
}
