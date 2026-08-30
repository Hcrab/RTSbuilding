package com.rtsbuilding.rtsbuilding.client.screen.blueprint;

import com.rtsbuilding.rtsbuilding.client.screen.canvas.MinecraftUiCanvas;
import com.rtsbuilding.rtsbuilding.uicore.blueprint.BlueprintLibraryUiEntry;
import com.rtsbuilding.rtsbuilding.uicore.blueprint.BlueprintLibraryUiState;
import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uikit.canvas.BlueprintLibraryChromeRenderer;
import com.rtsbuilding.rtsbuilding.uikit.layout.BlueprintLibraryLayout;
import com.rtsbuilding.rtsbuilding.uikit.theme.BlueprintLibraryStyle;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

import java.util.List;

import static com.rtsbuilding.rtsbuilding.client.screen.blueprint.BlueprintLibraryRenderSupport.text;
import static com.rtsbuilding.rtsbuilding.client.screen.blueprint.BlueprintLibraryRenderSupport.trim;

/**
 * 蓝图库已选详情与真实 ItemStack 预览的生产适配器。
 *
 * <p>本类不重新计算材料统计，也不改变选择；它只读取 adapter 已经为选中项生成的
 * 有界快照，并把真实物品画进 Kit 的最多 18 个预览槽。</p>
 */
final class BlueprintLibraryDetailsRenderer {
    private BlueprintLibraryDetailsRenderer() {
    }

    static void render(
            GuiGraphics graphics,
            Font font,
            MinecraftUiCanvas canvas,
            BlueprintLibraryLayout.Geometry geometry,
            BlueprintLibraryUiState state) {
        BlueprintLibraryUiEntry entry = state.selectedEntry();
        int x = geometry.detailsX;
        int y = geometry.listY;
        int width = geometry.detailsW;
        if (entry == null) {
            graphics.drawString(
                    font,
                    trim(
                            font,
                            text(
                                    "screen.rtsbuilding.blueprints.select_hint"),
                            width - BlueprintLibraryLayout.FRAME_TEXT_X * 2),
                    x + BlueprintLibraryLayout.FRAME_TEXT_X,
                    y + BlueprintLibraryLayout.EMPTY_TEXT_Y,
                    BlueprintLibraryStyle.SECONDARY_TEXT.toArgb(),
                    false);
            return;
        }
        graphics.drawString(
                font,
                trim(font, entry.name,
                        width - BlueprintLibraryLayout.FRAME_TEXT_X * 2),
                x + BlueprintLibraryLayout.FRAME_TEXT_X,
                y + BlueprintLibraryLayout.DETAILS_NAME_Y,
                BlueprintLibraryStyle.PRIMARY_TEXT.toArgb(),
                false);
        boolean invalidEntryHasThreeTextLines =
                BlueprintLibraryLayout.invalidDetailsShowMeta(
                        geometry.listH,
                        font.lineHeight);
        if (entry.valid() || invalidEntryHasThreeTextLines) {
            graphics.drawString(
                    font,
                    trim(
                            font,
                            entry.format + "  " + entry.size,
                            width - BlueprintLibraryLayout.FRAME_TEXT_X * 2),
                    x + BlueprintLibraryLayout.FRAME_TEXT_X,
                    y + BlueprintLibraryLayout.DETAILS_META_Y,
                    BlueprintLibraryStyle.SECONDARY_TEXT.toArgb(),
                    false);
        }
        if (!entry.valid()) {
            graphics.drawString(
                    font,
                    trim(font, entry.error,
                            width - BlueprintLibraryLayout.FRAME_TEXT_X * 2),
                    x + BlueprintLibraryLayout.FRAME_TEXT_X,
                    y + BlueprintLibraryLayout.invalidDetailsTextY(
                            geometry.listH,
                            font.lineHeight),
                    BlueprintLibraryStyle.INVALID_TEXT.toArgb(),
                    false);
            return;
        }

        boolean enough = entry.buildPercent >= 100;
        graphics.drawString(
                font,
                trim(font, entry.materialSummary,
                        width - BlueprintLibraryLayout.FRAME_TEXT_X * 2),
                x + BlueprintLibraryLayout.FRAME_TEXT_X,
                y + BlueprintLibraryLayout.DETAILS_SUMMARY_Y,
                enough
                        ? BlueprintLibraryStyle.READY_TEXT.toArgb()
                        : BlueprintLibraryStyle.WARNING_TEXT.toArgb(),
                false);
        BlueprintLibraryLayout.DetailsGeometry details =
                BlueprintLibraryLayout.detailsGeometry(geometry);
        BlueprintLibraryChromeRenderer.renderDetailsProgress(
                canvas,
                details,
                entry);
        drawPreviewItems(
                graphics,
                canvas,
                details,
                BlueprintPanel.librarySelectedEntry());
    }

    private static void drawPreviewItems(
            GuiGraphics graphics,
            MinecraftUiCanvas canvas,
            BlueprintLibraryLayout.DetailsGeometry details,
            BlueprintEntry entry) {
        if (entry == null) {
            return;
        }
        List<ItemStack> items = entry.previewItems();
        int count = Math.min(
                items.size(),
                details.previewSlots.size());
        for (int index = 0; index < count; index++) {
            UiRect slot = details.previewSlots.get(index);
            BlueprintLibraryChromeRenderer.renderPreviewSlot(
                    canvas,
                    slot);
            graphics.renderItem(
                    items.get(index),
                    (int) slot.getX() + 1,
                    (int) slot.getY() + 1);
        }
    }
}
