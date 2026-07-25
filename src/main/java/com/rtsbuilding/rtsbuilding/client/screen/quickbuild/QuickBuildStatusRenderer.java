package com.rtsbuilding.rtsbuilding.client.screen.quickbuild;

import com.rtsbuilding.rtsbuilding.client.screen.canvas.MinecraftUiCanvas;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.uicore.quickbuild.QuickBuildUiMode;
import com.rtsbuilding.rtsbuilding.uicore.quickbuild.QuickBuildUiState;
import com.rtsbuilding.rtsbuilding.uikit.canvas.QuickBuildChromeRenderer;
import com.rtsbuilding.rtsbuilding.uikit.layout.QuickBuildWindowLayout;
import com.rtsbuilding.rtsbuilding.uikit.theme.QuickBuildStyle;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Quick Build 底部状态区的 1.21.1 生产绘制适配器。
 *
 * <p>本类只把 Core 快照、Kit 几何和真实 Minecraft 字体/物品绘制组合起来，不拥有
 * Quick Build 模式、形状选择、插件权限或世界执行副作用。状态区的进度、成本、缺料、
 * 换行和尺寸提示集中在这里后，{@link QuickBuildPanel} 只保留窗口生命周期与编排，
 * 离屏和生产也不会再分别维护底部信息区偏移。</p>
 */
final class QuickBuildStatusRenderer {
    private QuickBuildStatusRenderer() {}

    static void render(
            GuiGraphics graphics,
            MinecraftUiCanvas canvas,
            BuilderScreen screen,
            QuickBuildUiState state,
            QuickBuildWindowLayout.Geometry layout,
            ItemStack preview,
            boolean creative) {
        QuickBuildChromeRenderer.renderStatus(
                canvas, layout, state.progressCompleted, state.progressTotal);

        int textY = layout.statusTextY;
        int itemY = layout.statusItemY;
        if (state.mode == QuickBuildUiMode.DESTROY) {
            renderDestroyStatus(graphics, screen, state, layout, textY);
            return;
        }

        String costText = "x " + state.costText;
        int textWidth = screen.font().width(costText);
        graphics.drawString(screen.font(), costText, layout.contentX, textY,
                QuickBuildStyle.SUCCESS_TEXT.toArgb(), false);

        int rightEdge = layout.contentX + textWidth;
        if (!preview.isEmpty()) {
            int itemX = layout.contentX + textWidth + QuickBuildWindowLayout.ITEM_GAP;
            graphics.renderItem(preview, itemX, itemY);
            // 物品批次必须在窗口 scissor 仍有效时提交。
            graphics.flush();
            rightEdge = itemX + QuickBuildWindowLayout.ITEM_SIZE;
        }

        if (!creative && !state.selectedItemId.isBlank() && state.missingBlocks > 0) {
            String missingText = screen.text(
                    "screen.rtsbuilding.quick_build.missing_blocks", state.missingBlocks);
            int missingTextX = layout.missingTextX(rightEdge);
            graphics.drawString(screen.font(), missingText, missingTextX, textY,
                    QuickBuildStyle.ERROR_TEXT.toArgb(), false);
            if (!preview.isEmpty()) {
                int missingIconX = layout.missingIconX(
                        missingTextX, screen.font().width(missingText));
                graphics.renderItem(preview, missingIconX, itemY);
                graphics.flush();
            }
        }

        int nextY = renderWrappedText(
                graphics,
                screen,
                Component.translatable(state.hintKey, state.confirmKeyLabel),
                layout.contentX,
                textY + screen.font().lineHeight + QuickBuildWindowLayout.INFO_FOLLOWUP_GAP,
                layout.contentW,
                QuickBuildStyle.HINT_TEXT.toArgb());
        renderDimensionInfo(graphics, screen, state, layout.contentX,
                nextY + QuickBuildWindowLayout.INFO_FOLLOWUP_GAP, layout.contentW);
    }

    private static void renderDestroyStatus(
            GuiGraphics graphics,
            BuilderScreen screen,
            QuickBuildUiState state,
            QuickBuildWindowLayout.Geometry layout,
            int textY) {
        if (state.progressCompleted >= 0 && state.progressTotal > 0) {
            String fullText = state.progressText + "    "
                    + screen.text(
                            "screen.rtsbuilding.quick_build.destroy_remaining",
                            state.remainingBlocks);
            graphics.drawString(screen.font(), fullText, layout.contentX, textY,
                    QuickBuildStyle.SUCCESS_TEXT.toArgb(), false);
            renderDimensionInfo(
                    graphics,
                    screen,
                    state,
                    layout.contentX,
                    textY + screen.font().lineHeight + QuickBuildWindowLayout.INFO_LINE_GAP,
                    layout.contentW);
            return;
        }

        int nextY = renderWrappedText(
                graphics,
                screen,
                Component.translatable(state.hintKey, state.confirmKeyLabel),
                layout.contentX,
                textY,
                layout.contentW,
                QuickBuildStyle.ERROR_TEXT.toArgb());
        renderDimensionInfo(
                graphics,
                screen,
                state,
                layout.contentX,
                nextY + QuickBuildWindowLayout.INFO_FOLLOWUP_GAP,
                layout.contentW);
    }

    private static int renderWrappedText(
            GuiGraphics graphics,
            BuilderScreen screen,
            Component text,
            int x,
            int y,
            int maxWidth,
            int color) {
        List<FormattedCharSequence> lines =
                screen.font().split(text, Math.max(1, maxWidth));
        int lineCount = Math.min(QuickBuildWindowLayout.STATUS_TEXT_MAX_LINES, lines.size());
        for (int i = 0; i < lineCount; i++) {
            graphics.drawString(
                    screen.font(),
                    lines.get(i),
                    x,
                    y + i * screen.font().lineHeight,
                    color,
                    false);
        }
        return y + lineCount * screen.font().lineHeight;
    }

    private static void renderDimensionInfo(
            GuiGraphics graphics,
            BuilderScreen screen,
            QuickBuildUiState state,
            int x,
            int y,
            int maxWidth) {
        Component text = Component.translatable(
                "screen.rtsbuilding.quick_build.dimensions", state.dimensions);
        String trimmed = screen.font().plainSubstrByWidth(
                text.getString(), Math.max(1, maxWidth));
        graphics.drawString(screen.font(), trimmed, x, y,
                QuickBuildStyle.DIMENSION_TEXT.toArgb(), false);
    }
}
