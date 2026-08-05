package com.rtsbuilding.rtsbuilding.client.screen.panel;

import com.rtsbuilding.rtsbuilding.client.screen.layout.BottomPanelLayoutTypes;
import com.rtsbuilding.rtsbuilding.common.persist.RtsClientUiStateStore;
import com.rtsbuilding.rtsbuilding.uicore.bottom.BottomBarUiAction;
import com.rtsbuilding.rtsbuilding.uikit.layout.BottomPanelToolLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import org.lwjgl.input.Keyboard;

/**
 * 工具行槽位命中到 Core 动作的生产适配器。
 *
 * <p>只处理热栏、空手、固定槽和翻页槽的左右键语义；整行几何由 Kit 所有，
 * 状态转移和平台副作用仍由 BottomPanel 的统一 Core 出口执行。</p>
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
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.thePlayer == null) {
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
        ItemStack stack = minecraft.thePlayer.inventory.getStackInSlot(hotbarIndex);
        if (isShiftDown()
                && RtsClientUiStateStore.isOverlayShiftImportEnabled()
                && !com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(stack)) {
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
        } else if (isShiftDown()) {
            panel.dispatchCore(BottomBarUiAction.index(
                    BottomBarUiAction.Type.CLEAR_PIN, pinIndex));
        } else {
            panel.dispatchCore(BottomBarUiAction.index(
                    BottomBarUiAction.Type.SELECT_PIN, pinIndex));
        }
    }

    private static boolean isShiftDown() {
        return Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)
                || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
    }
}
