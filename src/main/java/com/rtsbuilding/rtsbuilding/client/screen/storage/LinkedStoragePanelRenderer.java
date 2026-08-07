package com.rtsbuilding.rtsbuilding.client.screen.storage;

import com.rtsbuilding.rtsbuilding.client.record.LinkedStorageEntry;
import com.rtsbuilding.rtsbuilding.client.screen.canvas.MinecraftUiCanvas;
import com.rtsbuilding.rtsbuilding.client.util.RtsClientUiUtil;
import com.rtsbuilding.rtsbuilding.uicore.storage.StorageUiEntry;
import com.rtsbuilding.rtsbuilding.uicore.storage.StorageUiState;
import com.rtsbuilding.rtsbuilding.uicore.storage.StorageUiStatus;
import com.rtsbuilding.rtsbuilding.uikit.canvas.StorageWindowChromeRenderer;
import com.rtsbuilding.rtsbuilding.uikit.layout.StorageWindowLayout;
import com.rtsbuilding.rtsbuilding.uikit.theme.StorageWindowStyle;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * 已绑定仓库共享 chrome 与 Minecraft 字体、物品图标之间的薄适配层。
 *
 * <p>本类不持有滚动、文本焦点或网络命令；优先级输入框仍由面板放在共享控件矩形上方。</p>
 */
final class LinkedStoragePanelRenderer {
    private LinkedStoragePanelRenderer() {
    }

    static void renderHeader(
            GuiGraphicsExtractor graphics,
            Font font,
            StorageWindowLayout.Geometry geometry) {
        graphics.text(font, Component.translatable("screen.rtsbuilding.storage_links.header"),
                geometry.x, geometry.y, StorageWindowStyle.HEADER_TEXT.toArgb(), false);
        graphics.text(font, Component.translatable("screen.rtsbuilding.storage_links.priority"),
                geometry.priorityColumnX,
                geometry.y + StorageWindowLayout.HEADER_COLUMN_TOP,
                StorageWindowStyle.COLUMN_TEXT.toArgb(), false);
        graphics.text(font, Component.translatable(
                        "screen.rtsbuilding.storage_links.mode_extract_header"),
                geometry.extractColumnX,
                geometry.y + StorageWindowLayout.HEADER_COLUMN_TOP,
                StorageWindowStyle.COLUMN_TEXT.toArgb(), false);
    }

    static void renderStatus(
            GuiGraphicsExtractor graphics,
            Font font,
            StorageWindowLayout.Geometry geometry,
            StorageUiState state) {
        String key = state.status == StorageUiStatus.LOADING
                ? "screen.rtsbuilding.storage_links.loading"
                : state.status == StorageUiStatus.FAILED
                        ? "screen.rtsbuilding.storage_links.failed"
                        : "screen.rtsbuilding.storage_links.empty";
        int statusY = geometry.y + StorageWindowLayout.STATUS_Y;
        graphics.text(font, Component.translatable(key), geometry.x, statusY,
                StorageWindowStyle.statusText(state.status).toArgb(), false);
        graphics.text(font, RtsClientUiUtil.trimToWidth(font,
                        Component.translatable("screen.rtsbuilding.storage_links.empty_detail")
                                .getString(), geometry.innerWidth),
                geometry.x, statusY + StorageWindowLayout.STATUS_DETAIL_GAP,
                StorageWindowStyle.STATUS_DETAIL_TEXT.toArgb(), false);
    }

    static void renderRow(
            GuiGraphicsExtractor graphics,
            Font font,
            MinecraftUiCanvas canvas,
            StorageWindowLayout.RowGeometry geometry,
            LinkedStorageEntry platformEntry,
            StorageUiEntry coreEntry,
            boolean priorityEditing,
            double rowHover,
            double priorityHover,
            double extractHover,
            double unlinkHover) {
        StorageWindowChromeRenderer.renderRow(canvas, geometry, coreEntry, priorityEditing,
                rowHover, priorityHover, extractHover, unlinkHover);

        ItemStack preview = platformEntry.preview();
        if (preview != null && !preview.isEmpty()) {
            graphics.item(preview, (int) geometry.icon.getX(), (int) geometry.icon.getY());
            graphics.nextStratum();
        } else {
            StorageWindowChromeRenderer.renderMissingIcon(canvas, geometry.icon);
        }

        int labelWidth = Math.max(30, (int) geometry.priority.getX()
                - ((int) geometry.row.getX() + StorageWindowLayout.ROW_TEXT_X)
                - StorageWindowLayout.COLUMN_GAP);
        graphics.text(font, RtsClientUiUtil.trimToWidth(font, coreEntry.label, labelWidth),
                (int) geometry.row.getX() + StorageWindowLayout.ROW_TEXT_X,
                (int) geometry.row.getY() + StorageWindowLayout.ROW_LABEL_Y,
                StorageWindowStyle.ROW_LABEL_TEXT.toArgb(), false);
        graphics.text(font, coreEntry.position,
                (int) geometry.row.getX() + StorageWindowLayout.ROW_TEXT_X,
                (int) geometry.row.getY() + StorageWindowLayout.ROW_POSITION_Y,
                StorageWindowStyle.ROW_POSITION_TEXT.toArgb(), false);

        if (!priorityEditing) {
            graphics.text(font, RtsClientUiUtil.trimToWidth(font,
                            Integer.toString(coreEntry.priority),
                            StorageWindowLayout.PRIORITY_W - 6),
                    (int) geometry.priority.getX() + StorageWindowLayout.CONTROL_TEXT_X,
                    (int) geometry.priority.getY() + StorageWindowLayout.CONTROL_TEXT_Y,
                    StorageWindowStyle.PRIORITY_TEXT.toArgb(), false);
        }

        StorageWindowStyle.FrameVisual extractVisual = StorageWindowStyle.extract(
                coreEntry.extractOnly, extractHover);
        String extractKey = coreEntry.extractOnly
                ? "screen.rtsbuilding.storage_links.mode_yes"
                : "screen.rtsbuilding.storage_links.mode_no";
        drawCentered(graphics, font, RtsClientUiUtil.trimToWidth(font,
                        Component.translatable(extractKey).getString(),
                        StorageWindowLayout.EXTRACT_W - 6),
                geometry.extract, extractVisual.text.toArgb());
        drawCentered(graphics, font,
                Component.translatable("screen.rtsbuilding.storage_links.unlink"),
                geometry.unlink, StorageWindowStyle.UNLINK_TEXT.toArgb());
    }

    private static void drawCentered(
            GuiGraphicsExtractor graphics,
            Font font,
            Component text,
            com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect bounds,
            int color) {
        RtsClientUiUtil.drawCenteredStringNoShadow(graphics, font, text,
                (int) bounds.getX() + (int) bounds.getWidth() / 2,
                (int) bounds.getY() + StorageWindowLayout.CONTROL_TEXT_Y, color);
    }

    private static void drawCentered(
            GuiGraphicsExtractor graphics,
            Font font,
            String text,
            com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect bounds,
            int color) {
        RtsClientUiUtil.drawCenteredStringNoShadow(graphics, font, text,
                (int) bounds.getX() + (int) bounds.getWidth() / 2,
                (int) bounds.getY() + StorageWindowLayout.CONTROL_TEXT_Y, color);
    }
}
