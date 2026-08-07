package com.rtsbuilding.rtsbuilding.client.screen.panel;

import com.rtsbuilding.rtsbuilding.client.input.RtsModifierKeys;
import com.rtsbuilding.rtsbuilding.client.screen.layout.BottomPanelLayoutTypes;
import com.rtsbuilding.rtsbuilding.common.persist.RtsClientUiStateStore;
import com.rtsbuilding.rtsbuilding.uicore.bottom.BottomBarUiAction;
import com.rtsbuilding.rtsbuilding.uikit.layout.BottomPanelToolLayout;
import net.minecraft.client.Minecraft;

/**
 * 将工具行的真实点击转换为底栏 Core 动作。
 *
 * <p>本类只负责按 Kit 布局命中热栏、空手、固定槽和翻页槽；物品选择、流体收纳和
 * 固定槽清理仍由 {@link BottomBarUiAdapter} 调用原有控制器完成，避免输入路径绕过
 * 已有的客户端会话与远程仓储逻辑。</p>
 */
final class BottomPanelToolInput {
    private final BottomPanel panel;

    BottomPanelToolInput(BottomPanel panel) {
        this.panel = panel;
    }

    boolean mousePressed(
            double mouseX,
            double mouseY,
            int button,
            BottomPanelLayoutTypes.BottomPanelLayout layout) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null) {
            return false;
        }
        BottomPanelToolLayout tools = BottomPanelToolLayout.standard(
                layout.storageX(), layout.toolY(), layout.mainStorageW(),
                panel.controller.getQuickSlotCount(), panel.pinPage);
        if (!tools.containsRow(mouseX, mouseY)) {
            return false;
        }
        panel.pinPage = tools.pinPage();
        int hotbarIndex = tools.hotbarIndexAt(mouseX, mouseY);
        if (hotbarIndex >= 0) {
            handleHotbar(button, hotbarIndex, minecraft);
            return true;
        }

        int pinCell = tools.pinCellAt(mouseX, mouseY);
        if (pinCell < 0) {
            return true;
        }
        if (tools.isPinPagerCell(pinCell)) {
            panel.dispatchCore(BottomBarUiAction.delta(
                    BottomBarUiAction.Type.CYCLE_PIN_PAGE,
                    1, tools.pinPageCount()));
            return true;
        }
        int pinIndex = tools.pinIndexForCell(pinCell);
        if (pinIndex >= 0) {
            handlePin(button, pinIndex);
        }
        return true;
    }

    private void handleHotbar(
            int button, int hotbarIndex, Minecraft minecraft) {
        if (hotbarIndex == BottomPanelToolLayout.EMPTY_HAND_INDEX) {
            panel.dispatchCore(BottomBarUiAction.simple(
                    BottomBarUiAction.Type.SELECT_EMPTY_HAND));
            return;
        }
        if (button == 1) {
            panel.dispatchCore(BottomBarUiAction.index(
                    BottomBarUiAction.Type.STORE_FLUID_TOOL, hotbarIndex));
            return;
        }
        var stack = minecraft.player.getInventory().getItem(hotbarIndex);
        if (RtsModifierKeys.isShiftDown()
                && RtsClientUiStateStore.isOverlayShiftImportEnabled()
                && !stack.isEmpty()) {
            panel.dispatchCore(BottomBarUiAction.index(
                    BottomBarUiAction.Type.IMPORT_HOTBAR, hotbarIndex));
            return;
        }
        panel.dispatchCore(BottomBarUiAction.index(
                BottomBarUiAction.Type.SELECT_TOOL, hotbarIndex));
    }

    private void handlePin(int button, int pinIndex) {
        if (button == 1) {
            panel.dispatchCore(BottomBarUiAction.index(
                    BottomBarUiAction.Type.STORE_FLUID_PIN, pinIndex));
        } else if (RtsModifierKeys.isShiftDown()) {
            panel.dispatchCore(BottomBarUiAction.index(
                    BottomBarUiAction.Type.CLEAR_PIN, pinIndex));
        } else {
            panel.dispatchCore(BottomBarUiAction.index(
                    BottomBarUiAction.Type.SELECT_PIN, pinIndex));
        }
    }
}
