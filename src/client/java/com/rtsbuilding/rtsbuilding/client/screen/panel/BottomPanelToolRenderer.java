package com.rtsbuilding.rtsbuilding.client.screen.panel;

import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.screen.canvas.MinecraftUiCanvas;
import com.rtsbuilding.rtsbuilding.client.util.RtsClientUiUtil;
import com.rtsbuilding.rtsbuilding.uicore.bottom.BottomBarUiState;
import com.rtsbuilding.rtsbuilding.uicore.bottom.BottomBarUiToolSlot;
import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uikit.canvas.UiCanvas2D;
import com.rtsbuilding.rtsbuilding.uikit.canvas.UiCompactFrameRenderer;
import com.rtsbuilding.rtsbuilding.uikit.layout.BottomPanelToolLayout;
import com.rtsbuilding.rtsbuilding.uikit.theme.BottomPanelToolStyle;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiColor;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * 底栏工具行的 Minecraft 绘制适配器。
 *
 * <p>本类把 Core 槽位状态、玩家真实热栏、控制器固定槽预览、Kit 几何和共享主题绘制到
 * {@link GuiGraphics}。它只返回悬停结果，不执行选择、导入、清除、翻页或存流体动作；
 * 所有副作用仍由 {@link BottomPanel} 经 Core action 编排。</p>
 */
public final class BottomPanelToolRenderer {
    private static final int CONTENT_INSET = 1;
    private static final int EMPTY_HAND_MARK_SIZE = 10;

    private BottomPanelToolRenderer() {
    }

    public static HoverResult render(
            GuiGraphics graphics,
            Font font,
            BottomBarUiState state,
            Inventory inventory,
            ClientRtsController controller,
            BottomPanelToolLayout layout,
            int mouseX,
            int mouseY) {
        int hoveredHotbarCell = layout.hotbarIndexAt(mouseX, mouseY);
        UiCanvas2D canvas = new MinecraftUiCanvas(graphics, font);
        int slotSize = layout.slotSize();
        int rowY = layout.y();
        for (int cell = 0; cell < layout.hotbarCellCount(); cell++) {
            int cellX = layout.hotbarCellX(cell);
            boolean emptyHand = cell == BottomPanelToolLayout.EMPTY_HAND_INDEX;
            BottomBarUiToolSlot slot = findSlot(
                    state,
                    emptyHand ? BottomBarUiToolSlot.Kind.EMPTY_HAND
                            : BottomBarUiToolSlot.Kind.HOTBAR,
                    cell);
            drawFrame(
                    canvas,
                    cellX,
                    rowY,
                    slotSize,
                    BottomPanelToolStyle.hotbarBackground(
                            emptyHand, slot != null && slot.selected),
                    BottomPanelToolStyle.hotbarBorderLight(emptyHand));

            if (emptyHand) {
                drawEmptyHandMark(graphics, cellX, rowY, slotSize);
            } else {
                ItemStack stack = inventory.getItem(cell);
                if (!stack.isEmpty()) {
                    graphics.renderItem(stack, cellX + CONTENT_INSET, rowY + CONTENT_INSET);
                    graphics.renderItemDecorations(
                            font, stack, cellX + CONTENT_INSET, rowY + CONTENT_INSET);
                }
            }
            if (hoveredHotbarCell == cell) {
                fillInside(
                        graphics, cellX, rowY, slotSize,
                        BottomPanelToolStyle.HOVER_OVERLAY);
            }
        }

        int hoveredPinCell = layout.pinCellAt(mouseX, mouseY);
        for (int cell = 0; cell < layout.visiblePinCells(); cell++) {
            int cellX = layout.pinCellX(cell);
            boolean pager = layout.isPinPagerCell(cell);
            int pinIndex = layout.pinIndexForCell(cell);
            BottomBarUiToolSlot pin = findSlot(
                    state, BottomBarUiToolSlot.Kind.PINNED, pinIndex);
            boolean filled = !pager && pin != null && !pin.itemId.isEmpty();
            drawFrame(
                    canvas,
                    cellX,
                    rowY,
                    slotSize,
                    BottomPanelToolStyle.pinBackground(filled),
                    BottomPanelToolStyle.PIN_BORDER_LIGHT);

            if (pager) {
                fillInside(
                        graphics, cellX, rowY, slotSize,
                        BottomPanelToolStyle.PIN_PAGER_OVERLAY);
                drawCenteredNoShadow(
                        graphics, font, "+", cellX, rowY, slotSize,
                        BottomPanelToolStyle.PIN_PAGER_TEXT);
            } else if (pinIndex >= 0) {
                ItemStack preview = controller.getQuickSlotPreview(pinIndex);
                if (!preview.isEmpty()) {
                    graphics.renderItem(
                            preview, cellX + CONTENT_INSET, rowY + CONTENT_INSET);
                    if (pin != null && pin.selected) {
                        fillInside(
                                graphics, cellX, rowY, slotSize,
                                BottomPanelToolStyle.SELECTED_OVERLAY);
                    }
                    long amount = pin == null ? 0L : pin.amount;
                    RtsClientUiUtil.drawSlotCountOverlay(
                            graphics, font, cellX, rowY, slotSize,
                            RtsClientUiUtil.compactCount(amount),
                            argb(BottomPanelToolStyle.pinCount(amount)));
                } else {
                    drawCenteredNoShadow(
                            graphics, font, Integer.toString(pinIndex + 1),
                            cellX, rowY, slotSize,
                            BottomPanelToolStyle.PIN_INDEX_TEXT);
                }
            }
            if (hoveredPinCell == cell) {
                fillInside(
                        graphics, cellX, rowY, slotSize,
                        BottomPanelToolStyle.HOVER_OVERLAY);
            }
        }

        boolean emptyHandHovered =
                hoveredHotbarCell == BottomPanelToolLayout.EMPTY_HAND_INDEX;
        int hotbarHovered = hoveredHotbarCell >= 0 && !emptyHandHovered
                ? hoveredHotbarCell : -1;
        boolean pagerHovered = hoveredPinCell >= 0
                && layout.isPinPagerCell(hoveredPinCell);
        int pinHovered = hoveredPinCell >= 0 && !pagerHovered
                ? layout.pinIndexForCell(hoveredPinCell) : -1;
        return new HoverResult(
                hotbarHovered, emptyHandHovered, pinHovered, pagerHovered);
    }

    private static BottomBarUiToolSlot findSlot(
            BottomBarUiState state,
            BottomBarUiToolSlot.Kind kind,
            int sourceIndex) {
        if (sourceIndex < 0) {
            return null;
        }
        for (BottomBarUiToolSlot slot : state.toolSlots) {
            if (slot.kind == kind && slot.sourceIndex == sourceIndex) {
                return slot;
            }
        }
        return null;
    }

    private static void drawFrame(
            UiCanvas2D canvas,
            int x,
            int y,
            int size,
            UiColor background,
            UiColor borderLight) {
        UiCompactFrameRenderer.frame(
                canvas, new UiRect(x, y, size, size),
                background, borderLight, BottomPanelToolStyle.BORDER_DARK);
    }

    private static void drawEmptyHandMark(
            GuiGraphics graphics, int x, int y, int slotSize) {
        int left = x + (slotSize - EMPTY_HAND_MARK_SIZE) / 2;
        int top = y + (slotSize - EMPTY_HAND_MARK_SIZE) / 2;
        graphics.fill(
                left, top,
                left + EMPTY_HAND_MARK_SIZE,
                top + EMPTY_HAND_MARK_SIZE,
                argb(BottomPanelToolStyle.EMPTY_HAND_MARK));
    }

    private static void fillInside(
            GuiGraphics graphics, int x, int y, int size, UiColor color) {
        graphics.fill(
                x + CONTENT_INSET,
                y + CONTENT_INSET,
                x + size - CONTENT_INSET,
                y + size - CONTENT_INSET,
                argb(color));
    }

    private static void drawCenteredNoShadow(
            GuiGraphics graphics,
            Font font,
            String text,
            int x,
            int y,
            int size,
            UiColor color) {
        int textX = x + (size - font.width(text)) / 2;
        int textY = y + Math.max(0, (size - font.lineHeight) / 2);
        graphics.drawString(font, text, textX, textY, argb(color), false);
    }

    private static int argb(UiColor color) {
        return color.toArgb();
    }

    /** 当前帧四类工具控件互斥的悬停结果。 */
    public static final class HoverResult {
        public final int hotbarIndex;
        public final boolean emptyHand;
        public final int pinIndex;
        public final boolean pinPager;

        private HoverResult(
                int hotbarIndex,
                boolean emptyHand,
                int pinIndex,
                boolean pinPager) {
            this.hotbarIndex = hotbarIndex;
            this.emptyHand = emptyHand;
            this.pinIndex = pinIndex;
            this.pinPager = pinPager;
        }
    }
}
