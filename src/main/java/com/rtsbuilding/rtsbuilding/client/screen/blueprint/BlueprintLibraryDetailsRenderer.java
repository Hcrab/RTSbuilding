package com.rtsbuilding.rtsbuilding.client.screen.blueprint;

import com.rtsbuilding.rtsbuilding.client.screen.canvas.MinecraftUiCanvas;
import com.rtsbuilding.rtsbuilding.uicore.blueprint.BlueprintLibraryUiEntry;
import com.rtsbuilding.rtsbuilding.uicore.blueprint.BlueprintLibraryUiState;
import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uikit.canvas.BlueprintLibraryChromeRenderer;
import com.rtsbuilding.rtsbuilding.uikit.layout.BlueprintLibraryLayout;
import com.rtsbuilding.rtsbuilding.uikit.theme.BlueprintLibraryStyle;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

import java.util.List;

import static com.rtsbuilding.rtsbuilding.client.screen.blueprint.BlueprintLibraryRenderSupport.text;
import static com.rtsbuilding.rtsbuilding.client.screen.blueprint.BlueprintLibraryRenderSupport.trim;

/** 绘制已选蓝图的材料摘要、进度与真实物品预览。 */
final class BlueprintLibraryDetailsRenderer {
    private BlueprintLibraryDetailsRenderer() {
    }

    static void render(
            GuiGraphicsExtractor graphics,
            Font font,
            MinecraftUiCanvas canvas,
            BlueprintLibraryLayout.Geometry geometry,
            BlueprintLibraryUiState state) {
        BlueprintLibraryUiEntry entry = state.selectedEntry();
        int x = geometry.detailsX;
        int y = geometry.listY;
        int width = geometry.detailsW;
        if (entry == null) {
            graphics.text(font, trim(font,
                            text("screen.rtsbuilding.blueprints.select_hint"),
                            width - BlueprintLibraryLayout.FRAME_TEXT_X * 2),
                    x + BlueprintLibraryLayout.FRAME_TEXT_X,
                    y + BlueprintLibraryLayout.EMPTY_TEXT_Y,
                    BlueprintLibraryStyle.SECONDARY_TEXT.toArgb(), false);
            return;
        }
        graphics.text(font, trim(font, entry.name,
                        width - BlueprintLibraryLayout.FRAME_TEXT_X * 2),
                x + BlueprintLibraryLayout.FRAME_TEXT_X,
                y + BlueprintLibraryLayout.DETAILS_NAME_Y,
                BlueprintLibraryStyle.PRIMARY_TEXT.toArgb(), false);
        boolean showMeta = entry.valid() || BlueprintLibraryLayout.invalidDetailsShowMeta(
                geometry.listH, font.lineHeight);
        if (showMeta) {
            graphics.text(font, trim(font, entry.format + "  " + entry.size,
                            width - BlueprintLibraryLayout.FRAME_TEXT_X * 2),
                    x + BlueprintLibraryLayout.FRAME_TEXT_X,
                    y + BlueprintLibraryLayout.DETAILS_META_Y,
                    BlueprintLibraryStyle.SECONDARY_TEXT.toArgb(), false);
        }
        if (!entry.valid()) {
            graphics.text(font, trim(font, entry.error,
                            width - BlueprintLibraryLayout.FRAME_TEXT_X * 2),
                    x + BlueprintLibraryLayout.FRAME_TEXT_X,
                    y + BlueprintLibraryLayout.invalidDetailsTextY(
                            geometry.listH, font.lineHeight),
                    BlueprintLibraryStyle.INVALID_TEXT.toArgb(), false);
            return;
        }
        boolean enough = entry.buildPercent >= 100;
        graphics.text(font, trim(font, entry.materialSummary,
                        width - BlueprintLibraryLayout.FRAME_TEXT_X * 2),
                x + BlueprintLibraryLayout.FRAME_TEXT_X,
                y + BlueprintLibraryLayout.DETAILS_SUMMARY_Y,
                enough ? BlueprintLibraryStyle.READY_TEXT.toArgb()
                        : BlueprintLibraryStyle.WARNING_TEXT.toArgb(), false);
        BlueprintLibraryLayout.DetailsGeometry details =
                BlueprintLibraryLayout.detailsGeometry(geometry);
        BlueprintLibraryChromeRenderer.renderDetailsProgress(canvas, details, entry);
        drawPreviewItems(graphics, canvas, details,
                BlueprintPanel.librarySelectedEntry());
    }

    private static void drawPreviewItems(
            GuiGraphicsExtractor graphics,
            MinecraftUiCanvas canvas,
            BlueprintLibraryLayout.DetailsGeometry details,
            BlueprintEntry entry) {
        if (entry == null) {
            return;
        }
        List<ItemStack> items = entry.previewItems();
        int count = Math.min(items.size(), details.previewSlots.size());
        for (int index = 0; index < count; index++) {
            UiRect slot = details.previewSlots.get(index);
            BlueprintLibraryChromeRenderer.renderPreviewSlot(canvas, slot);
            graphics.item(items.get(index),
                    (int) slot.getX() + 1,
                    (int) slot.getY() + 1);
        }
    }
}
