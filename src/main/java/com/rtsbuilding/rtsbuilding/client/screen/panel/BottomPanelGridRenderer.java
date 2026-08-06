package com.rtsbuilding.rtsbuilding.client.screen.panel;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.client.record.FluidEntry;
import com.rtsbuilding.rtsbuilding.client.record.RecentEntry;
import com.rtsbuilding.rtsbuilding.client.record.StorageEntry;
import com.rtsbuilding.rtsbuilding.client.screen.canvas.MinecraftUiCanvas;
import com.rtsbuilding.rtsbuilding.client.util.RtsClientUiUtil;
import com.rtsbuilding.rtsbuilding.client.util.RtsCreativeItemCatalog;
import com.rtsbuilding.rtsbuilding.uicore.bottom.BottomBarUiEntry;
import com.rtsbuilding.rtsbuilding.uicore.control.UiControlState;
import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uikit.animation.SystemUiClock;
import com.rtsbuilding.rtsbuilding.uikit.animation.UiControlAnimationRegistry;
import com.rtsbuilding.rtsbuilding.uikit.animation.UiControlAnimationState;
import com.rtsbuilding.rtsbuilding.uikit.canvas.UiCanvas2D;
import com.rtsbuilding.rtsbuilding.uikit.canvas.UiCompactFrameRenderer;
import com.rtsbuilding.rtsbuilding.uikit.layout.BottomPanelGridLayout;
import com.rtsbuilding.rtsbuilding.uikit.theme.BottomPanelGridStyle;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiColor;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * 底栏储存、创造、最近使用和流体网格的 Minecraft 绘制适配器。
 *
 * <p>本类只消费 Core 条目、真实预览栈、Kit 网格与主题。分页、控制器刷新、
 * 选择动作和网络副作用仍由 {@link BottomPanel} 拥有。</p>
 */
public final class BottomPanelGridRenderer {
    private static final UiControlAnimationRegistry<String> ANIMATIONS =
            new UiControlAnimationRegistry<>(SystemUiClock.INSTANCE, 256);

    private BottomPanelGridRenderer() {
    }

    public static int renderStorage(GuiGraphics graphics, Font font,
                                    List<BottomBarUiEntry> entries,
                                    List<StorageEntry> sourceEntries,
                                    BottomPanelGridLayout.GridView view,
                                    int mouseX, int mouseY, boolean storageLinked) {
        UiCanvas2D canvas = new MinecraftUiCanvas(graphics, font);
        int hovered = view.entryIndexAt(mouseX, mouseY);
        for (int row = 0; row < view.rows; row++) {
            for (int column = 0; column < view.columns; column++) {
                int index = view.entryIndex(row, column);
                int slotX = view.slotX(column);
                int slotY = view.slotY(row);
                BottomBarUiEntry entry = entryAt(entries, index);
                drawFrame(canvas, view, slotX, slotY, BottomPanelGridStyle.STORAGE);
                if (entry == null) {
                    continue;
                }
                drawInteraction(graphics, view, slotX, slotY,
                        animate("storage." + row + "." + column,
                                index == hovered, entry.selected),
                        BottomPanelGridStyle.STORAGE);
                if (entry.sourceIndex >= 0 && entry.sourceIndex < sourceEntries.size()) {
                    graphics.renderItem(sourceEntries.get(entry.sourceIndex).stack(),
                            slotX + 2, slotY + 2);
                }
                drawCount(graphics, font, view, slotX, slotY,
                        RtsClientUiUtil.compactCount(entry.amount),
                        BottomPanelGridStyle.STORAGE.countText);
            }
        }
        if (entries.isEmpty()) {
            renderEmptyState(graphics, font, view.area, storageLinked
                    ? "screen.rtsbuilding.storage.empty_linked"
                    : "screen.rtsbuilding.storage.empty_unlinked");
        }
        return hovered;
    }

    public static int renderCreative(GuiGraphics graphics, Font font,
                                     List<BottomBarUiEntry> entries,
                                     List<RtsCreativeItemCatalog.CreativeEntry> sourceEntries,
                                     BottomPanelGridLayout.GridView view,
                                     int mouseX, int mouseY) {
        UiCanvas2D canvas = new MinecraftUiCanvas(graphics, font);
        int hovered = view.entryIndexAt(mouseX, mouseY);
        for (int row = 0; row < view.rows; row++) {
            for (int column = 0; column < view.columns; column++) {
                int index = view.entryIndex(row, column);
                int slotX = view.slotX(column);
                int slotY = view.slotY(row);
                BottomBarUiEntry entry = entryAt(entries, index);
                drawFrame(canvas, view, slotX, slotY, BottomPanelGridStyle.CREATIVE);
                if (entry == null) {
                    continue;
                }
                drawInteraction(graphics, view, slotX, slotY,
                        animate("creative." + row + "." + column,
                                index == hovered, entry.selected),
                        BottomPanelGridStyle.CREATIVE);
                if (entry.sourceIndex >= 0 && entry.sourceIndex < sourceEntries.size()) {
                    graphics.renderItem(sourceEntries.get(entry.sourceIndex).stack(),
                            slotX + 2, slotY + 2);
                }
            }
        }
        if (entries.isEmpty()) {
            renderEmptyState(graphics, font, view.area,
                    "screen.rtsbuilding.creative.empty");
        }
        BottomBarUiEntry entry = entryAt(entries, hovered);
        return entry == null ? -1 : entry.sourceIndex;
    }

    public static int renderRecent(GuiGraphics graphics, Font font,
                                   List<BottomBarUiEntry> entries,
                                   List<RecentEntry> sourceEntries,
                                   BottomPanelGridLayout.GridView view,
                                   int mouseX, int mouseY) {
        UiCanvas2D canvas = new MinecraftUiCanvas(graphics, font);
        int hovered = view.entryIndexAt(mouseX, mouseY);
        for (int row = 0; row < view.rows; row++) {
            for (int column = 0; column < view.columns; column++) {
                int index = view.entryIndex(row, column);
                int slotX = view.slotX(column);
                int slotY = view.slotY(row);
                BottomBarUiEntry entry = entryAt(entries, index);
                drawFrame(canvas, view, slotX, slotY, BottomPanelGridStyle.RECENT);
                if (entry == null) {
                    continue;
                }
                if (entry.sourceIndex >= 0 && entry.sourceIndex < sourceEntries.size()
                        && !sourceEntries.get(entry.sourceIndex).preview().isEmpty()) {
                    graphics.renderItem(sourceEntries.get(entry.sourceIndex).preview(),
                            slotX + 2, slotY + 2);
                }
                boolean fluid = entry.kind == BottomBarUiEntry.Kind.RECENT_FLUID;
                drawCount(graphics, font, view, slotX, slotY,
                        fluid ? RtsClientUiUtil.compactFluidAmount(entry.amount)
                                : RtsClientUiUtil.compactCount(entry.amount),
                        fluid ? BottomPanelGridStyle.RECENT_FLUID_COUNT
                                : BottomPanelGridStyle.RECENT.countText);
                drawInteraction(graphics, view, slotX, slotY,
                        animate("recent." + row + "." + column,
                                index == hovered, false),
                        BottomPanelGridStyle.RECENT);
            }
        }
        return hovered;
    }

    public static int renderFluid(GuiGraphics graphics, Font font,
                                  List<BottomBarUiEntry> entries,
                                  List<FluidEntry> sourceEntries,
                                  BottomPanelGridLayout.GridView view,
                                  int mouseX, int mouseY) {
        UiCanvas2D canvas = new MinecraftUiCanvas(graphics, font);
        int hovered = view.entryIndexAt(mouseX, mouseY);
        for (int row = 0; row < view.rows; row++) {
            for (int column = 0; column < view.columns; column++) {
                int index = view.entryIndex(row, column);
                int slotX = view.slotX(column);
                int slotY = view.slotY(row);
                BottomBarUiEntry entry = entryAt(entries, index);
                drawFrame(canvas, view, slotX, slotY, BottomPanelGridStyle.FLUID);
                if (entry == null) {
                    continue;
                }
                drawInteraction(graphics, view, slotX, slotY,
                        animate("fluid." + row + "." + column,
                                index == hovered, entry.selected),
                        BottomPanelGridStyle.FLUID);
                if (entry.sourceIndex >= 0 && entry.sourceIndex < sourceEntries.size()
                        && !sourceEntries.get(entry.sourceIndex).preview().isEmpty()) {
                    graphics.renderItem(sourceEntries.get(entry.sourceIndex).preview(),
                            slotX + 2, slotY + 2);
                }
                drawCount(graphics, font, view, slotX, slotY,
                        RtsClientUiUtil.compactFluidAmount(entry.amount),
                        BottomPanelGridStyle.FLUID.countText);
            }
        }
        return hovered;
    }

    private static void renderEmptyState(GuiGraphics graphics, Font font,
                                         BottomPanelGridLayout.GridArea area,
                                         String translationKey) {
        int messageWidth = Math.max(24,
                area.width - BottomPanelGridLayout.EMPTY_TEXT_HORIZONTAL_INSET * 2);
        int centerY = area.y + Math.max(8, area.height / 2 - 10);
        String title = RtsClientUiUtil.trimToWidth(font,
                Component.translatable(translationKey).getString(), messageWidth);
        String detail = RtsClientUiUtil.trimToWidth(font,
                Component.translatable(translationKey + ".detail").getString(), messageWidth);
        RtsClientUiUtil.drawCenteredStringNoShadow(graphics, font, title,
                area.x + area.width / 2, centerY,
                argb(BottomPanelGridStyle.EMPTY_TITLE));
        RtsClientUiUtil.drawCenteredStringNoShadow(graphics, font, detail,
                area.x + area.width / 2, centerY + 12,
                argb(BottomPanelGridStyle.EMPTY_DETAIL));
    }

    private static void drawFrame(UiCanvas2D canvas,
                                  BottomPanelGridLayout.GridView view,
                                  int slotX, int slotY,
                                  BottomPanelGridStyle.Visual style) {
        UiCompactFrameRenderer.frame(
                canvas,
                new UiRect(slotX, slotY, view.slotExtent, view.slotExtent),
                style.background, style.borderLight, style.borderDark);
    }

    private static void drawInteraction(
            GuiGraphics graphics,
            BottomPanelGridLayout.GridView view,
            int slotX,
            int slotY,
            UiControlAnimationState.Snapshot animation,
            BottomPanelGridStyle.Visual style) {
        UiColor selection = BottomPanelGridStyle.selectionOverlay(
                style, animation.selection());
        if (selection.alpha() > 0) {
            fillInside(graphics, view, slotX, slotY, argb(selection));
        }
        UiColor hover = BottomPanelGridStyle.hoverOverlay(
                animation.selection(), animation.hover());
        if (hover.alpha() > 0) {
            fillInside(graphics, view, slotX, slotY, argb(hover));
        }
    }

    private static UiControlAnimationState.Snapshot animate(
            String stableId, boolean hovered, boolean selected) {
        UiControlState state = new UiControlState(
                true, selected, false, false, "")
                .withInteraction(hovered, false, false);
        return ANIMATIONS.update(
                stableId, state, Config.isUiAnimationsEnabled());
    }

    private static void fillInside(GuiGraphics graphics,
                                   BottomPanelGridLayout.GridView view,
                                   int slotX, int slotY, int color) {
        graphics.fill(slotX + 1, slotY + 1,
                slotX + view.slotExtent - 1,
                slotY + view.slotExtent - 1, color);
    }

    private static void drawCount(GuiGraphics graphics, Font font,
                                  BottomPanelGridLayout.GridView view,
                                  int slotX, int slotY, String text, UiColor color) {
        RtsClientUiUtil.drawSlotCountOverlay(graphics, font, slotX, slotY,
                view.slotExtent, text, argb(color));
    }

    private static BottomBarUiEntry entryAt(List<BottomBarUiEntry> entries, int index) {
        return index >= 0 && index < entries.size() ? entries.get(index) : null;
    }

    private static int argb(UiColor color) {
        return color.toArgb();
    }
}
