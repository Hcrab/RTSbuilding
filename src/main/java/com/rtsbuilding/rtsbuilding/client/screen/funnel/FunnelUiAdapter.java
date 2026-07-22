package com.rtsbuilding.rtsbuilding.client.screen.funnel;

import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.record.FunnelBufferEntry;
import com.rtsbuilding.rtsbuilding.common.build.BuilderMode;
import com.rtsbuilding.rtsbuilding.uicore.funnel.FunnelUiEntry;
import com.rtsbuilding.rtsbuilding.uicore.funnel.FunnelUiState;

import java.util.ArrayList;
import java.util.List;

/** 把真实 ItemStack 缓存收束为有界的漏斗 Core 快照。 */
final class FunnelUiAdapter {
    private FunnelUiAdapter() {
    }

    static FunnelUiState snapshot(ClientRtsController controller, boolean visible,
                                  int capacity, int hoveredSourceIndex) {
        List<FunnelBufferEntry> source = controller.getFunnelBufferEntries();
        int count = Math.min(source.size(), Math.max(1, capacity));
        List<FunnelUiEntry> rows = new ArrayList<FunnelUiEntry>(count);
        for (int index = 0; index < count; index++) {
            FunnelBufferEntry entry = source.get(index);
            rows.add(new FunnelUiEntry(index, entry.itemId(),
                    entry.stack().getHoverName().getString(), entry.count()));
        }
        return new FunnelUiState(controller.getMode() == BuilderMode.FUNNEL,
                visible, source.size(), capacity, hoveredSourceIndex, rows);
    }
}
