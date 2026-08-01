package com.rtsbuilding.rtsbuilding.client.screen.standalone.craftterminal;

import com.rtsbuilding.rtsbuilding.client.record.StorageEntry;
import com.rtsbuilding.rtsbuilding.client.screen.canvas.MinecraftUiCanvas;
import com.rtsbuilding.rtsbuilding.client.util.RtsClientUiUtil;
import com.rtsbuilding.rtsbuilding.uicore.craftterminal.CraftTerminalUiAction;
import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uikit.canvas.CraftTerminalChromeRenderer;
import com.rtsbuilding.rtsbuilding.uikit.layout.CraftTerminalLayout;
import com.rtsbuilding.rtsbuilding.uikit.theme.CraftTerminalStyle;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Minecraft 侧的合成终端内容适配器。
 *
 * <p>共享 UiKit 负责全部 chrome 和几何；本类只补上 Minecraft 才能绘制的物品与数量。
 * 它不拥有按钮坐标、不处理输入，也不发送网络包。</p>
 */
public final class CraftTerminalRenderer {
    private CraftTerminalRenderer() {
    }

    public static void render(
            GuiGraphics graphics,
            Font font,
            int left,
            int top,
            CraftTerminalLayout.Geometry layout,
            CraftTerminalScrollState scrollState,
            int totalEntries,
            boolean searchFocused,
            boolean searchHasText,
            CraftTerminalSearchMode searchMode,
            boolean searchPinned,
            int sortMode,
            boolean ascending,
            int mouseX,
            int mouseY) {
        double relativeX = mouseX - left;
        double relativeY = mouseY - top;
        CraftTerminalUiAction hoveredAction = layout.actionAt(relativeX, relativeY);
        int hoveredStorageCell = layout.storageCellAt(relativeX, relativeY);

        MinecraftUiCanvas canvas = new MinecraftUiCanvas(graphics, font);
        canvas.pushTransform();
        canvas.translate(left, top);
        try {
            CraftTerminalChromeRenderer.render(
                    canvas,
                    layout,
                    hoveredAction,
                    hoveredStorageCell,
                    searchFocused,
                    searchHasText,
                    searchPinned,
                    searchMode.ordinal(),
                    sortMode,
                    ascending,
                    scrollState.fraction(totalEntries, layout.rows),
                    scrollState.thumbFraction(totalEntries, layout.rows));
        } finally {
            canvas.popTransform();
        }

        int cellCount = layout.rows * CraftTerminalLayout.COLUMNS;
        for (int cell = 0; cell < cellCount; cell++) {
            StorageEntry entry = scrollState.entryAtVisibleCell(cell);
            if (entry == null) {
                continue;
            }
            UiRect bounds = layout.storageCell(cell);
            int x = left + (int) bounds.getX() + 1;
            int y = top + (int) bounds.getY() + 1;
            graphics.renderItem(entry.stack(), x, y);
            RtsClientUiUtil.drawSlotCountOverlay(
                    graphics,
                    font,
                    x - 1,
                    y - 1,
                    CraftTerminalLayout.SLOT_SIZE,
                    RtsClientUiUtil.compactCount(entry.count()),
                    CraftTerminalStyle.COUNT_TEXT.toArgb());
        }
    }
}
