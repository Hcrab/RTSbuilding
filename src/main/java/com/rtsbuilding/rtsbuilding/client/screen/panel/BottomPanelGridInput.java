package com.rtsbuilding.rtsbuilding.client.screen.panel;

import com.rtsbuilding.rtsbuilding.client.screen.layout.BottomPanelLayoutTypes;
import com.rtsbuilding.rtsbuilding.client.util.RtsCreativeItemCatalog;
import com.rtsbuilding.rtsbuilding.uicore.bottom.BottomBarUiAction;
import com.rtsbuilding.rtsbuilding.uikit.layout.BottomPanelGridLayout;

import static com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreenConstants.SLOT;
import static com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreenConstants.STORAGE_RECENT_GAP;

/**
 * 将创意、仓储、流体和最近使用网格的命中交给 Core 动作。
 *
 * <p>渲染与命中都通过 {@link BottomPanelGridLayout} 解析相同的半开槽位区域，防止
 * 槽间距、右下边界与分页索引出现不一致。真实选择和流体收纳依旧由底栏既有控制器执行。</p>
 */
final class BottomPanelGridInput {
    private final BottomPanel panel;

    BottomPanelGridInput(BottomPanel panel) {
        this.panel = panel;
    }

    boolean leftPressed(
            double mouseX,
            double mouseY,
            BottomPanelLayoutTypes.BottomPanelTab activeTab,
            BottomPanelLayoutTypes.BottomPanelLayout layout) {
        if (activeTab == BottomPanelLayoutTypes.BottomPanelTab.CREATIVE) {
            return handleCreative(mouseX, mouseY, layout);
        }
        return handleStorage(mouseX, mouseY, layout);
    }

    void rightPressedStorage(
            double mouseX,
            double mouseY,
            BottomPanelLayoutTypes.BottomPanelLayout layout) {
        BottomPanelGridLayout.Layout grids = storageLayout(layout);
        int entryIndex = BottomPanel.gridView(
                grids.main, panel.controller.getStorageEntries().size(), 0)
                .entryIndexAt(mouseX, mouseY);
        if (entryIndex >= 0
                && entryIndex < panel.controller.getStorageEntries().size()) {
            panel.controller.storeFluidFromStorageItem(
                    panel.controller.getStorageEntries().get(entryIndex).itemId());
        }
    }

    private boolean handleCreative(
            double mouseX,
            double mouseY,
            BottomPanelLayoutTypes.BottomPanelLayout layout) {
        BottomPanelGridLayout.Layout grids = BottomPanelGridLayout.creative(
                layout.storageX(), layout.gridY(), layout.mainStorageW(),
                layout.gridH(), SLOT, STORAGE_RECENT_GAP);
        int creativeIndex = BottomPanel.gridView(
                grids.main,
                panel.creativeEntriesForCurrentFilter().size(),
                panel.creativePage)
                .entryIndexAt(mouseX, mouseY);
        if (creativeIndex >= 0) {
            RtsCreativeItemCatalog.CreativeEntry entry =
                    panel.getCreativeEntryForTooltip(creativeIndex);
            if (entry != null) {
                panel.dispatchCore(BottomBarUiAction.index(
                        BottomBarUiAction.Type.SELECT_CREATIVE, creativeIndex));
            }
            return true;
        }
        int recentIndex = BottomPanel.gridView(
                grids.recent,
                panel.controller.getRecentEntries().size(), 0)
                .entryIndexAt(mouseX, mouseY);
        if (recentIndex >= 0) {
            panel.dispatchCore(BottomBarUiAction.index(
                    BottomBarUiAction.Type.SELECT_RECENT, recentIndex));
        }
        return true;
    }

    private boolean handleStorage(
            double mouseX,
            double mouseY,
            BottomPanelLayoutTypes.BottomPanelLayout layout) {
        BottomPanelGridLayout.Layout grids = storageLayout(layout);
        if (!grids.fluid.isEmpty()) {
            int fluidIndex = BottomPanel.gridView(
                    grids.fluid,
                    panel.controller.getFluidEntries().size(), 0)
                    .entryIndexAt(mouseX, mouseY);
            if (fluidIndex >= 0) {
                panel.dispatchCore(BottomBarUiAction.index(
                        BottomBarUiAction.Type.SELECT_FLUID, fluidIndex));
                return true;
            }
        }
        int storageIndex = BottomPanel.gridView(
                grids.main,
                panel.controller.getStorageEntries().size(), 0)
                .entryIndexAt(mouseX, mouseY);
        if (storageIndex >= 0) {
            panel.dispatchCore(BottomBarUiAction.index(
                    BottomBarUiAction.Type.SELECT_STORAGE, storageIndex));
            return true;
        }
        int recentIndex = BottomPanel.gridView(
                grids.recent,
                panel.controller.getRecentEntries().size(), 0)
                .entryIndexAt(mouseX, mouseY);
        if (recentIndex >= 0) {
            panel.dispatchCore(BottomBarUiAction.index(
                    BottomBarUiAction.Type.SELECT_RECENT, recentIndex));
        }
        return true;
    }

    private BottomPanelGridLayout.Layout storageLayout(
            BottomPanelLayoutTypes.BottomPanelLayout layout) {
        int fluidWidth = panel.getFluidStripWidth(layout.mainStorageW());
        return BottomPanelGridLayout.storage(
                layout.storageX(), layout.gridY(), layout.mainStorageW(),
                layout.gridH(), SLOT, STORAGE_RECENT_GAP, fluidWidth, 4);
    }
}
