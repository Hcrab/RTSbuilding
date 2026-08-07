package com.rtsbuilding.rtsbuilding.client.screen.blueprint;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.client.screen.canvas.MinecraftUiCanvas;
import com.rtsbuilding.rtsbuilding.uicore.blueprint.BlueprintLibraryUiState;
import com.rtsbuilding.rtsbuilding.uicore.control.UiControlState;
import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uikit.animation.SystemUiClock;
import com.rtsbuilding.rtsbuilding.uikit.animation.UiControlAnimationRegistry;
import com.rtsbuilding.rtsbuilding.uikit.canvas.BlueprintLibraryChromeRenderer;
import com.rtsbuilding.rtsbuilding.uikit.layout.BlueprintLibraryLayout;
import com.rtsbuilding.rtsbuilding.uikit.theme.BlueprintLibraryStyle;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import static com.rtsbuilding.rtsbuilding.client.screen.blueprint.BlueprintLibraryRenderSupport.drawCentered;
import static com.rtsbuilding.rtsbuilding.client.screen.blueprint.BlueprintLibraryRenderSupport.text;
import static com.rtsbuilding.rtsbuilding.client.screen.blueprint.BlueprintLibraryRenderSupport.trim;

/**
 * 蓝图库的 Kit chrome、列表、详情和捕获锁定态编排器。
 *
 * <p>该类只决定同一帧的绘制层次；选择、搜索、滚动、捕获和文件动作由输入适配器和
 * {@link BlueprintLibraryUiAdapter} 驱动既有 BlueprintPanel 后端。</p>
 */
final class BlueprintLibraryPanelRenderer {
    private static final UiControlAnimationRegistry<String> CONTROL_ANIMATIONS =
            new UiControlAnimationRegistry<>(SystemUiClock.INSTANCE, 128);

    private BlueprintLibraryPanelRenderer() {
    }

    static void renderDisabled(
            GuiGraphicsExtractor graphics,
            Font font,
            int x,
            int y,
            int width,
            int height) {
        MinecraftUiCanvas canvas = new MinecraftUiCanvas(graphics, font);
        BlueprintLibraryChromeRenderer.renderFrame(
                canvas, new UiRect(x, y, width, height));
        graphics.text(font, trim(font,
                        text("screen.rtsbuilding.blueprints.disabled"),
                        width - BlueprintLibraryLayout.FRAME_TEXT_X * 2),
                x + BlueprintLibraryLayout.FRAME_TEXT_X,
                y + BlueprintLibraryLayout.EMPTY_TEXT_Y,
                BlueprintLibraryStyle.PRIMARY_TEXT.toArgb(), false);
        graphics.text(font, trim(font,
                        text("screen.rtsbuilding.blueprints.status.disabled"),
                        width - BlueprintLibraryLayout.FRAME_TEXT_X * 2),
                x + BlueprintLibraryLayout.FRAME_TEXT_X,
                y + BlueprintLibraryLayout.CAPTURE_STATUS_Y,
                BlueprintLibraryStyle.SECONDARY_TEXT.toArgb(), false);
    }

    static void render(
            GuiGraphicsExtractor graphics,
            Font font,
            BlueprintLibraryUiState state,
            int x,
            int y,
            int width,
            int height,
            int mouseX,
            int mouseY) {
        BlueprintLibraryLayout.Geometry geometry = BlueprintLibraryLayout.geometry(
                x, y, width, height);
        BlueprintLibraryLayout.TopBar top = topBar(
                font, x, width, state.captureLocked);
        MinecraftUiCanvas canvas = new MinecraftUiCanvas(graphics, font);
        BlueprintLibraryChromeRenderer.renderTopBar(
                canvas, geometry, top, state.searchFocused,
                hover("top.folder", top.folderBounds(geometry.y).contains(mouseX, mouseY), false),
                hover("top.import", top.importBounds(geometry.y).contains(mouseX, mouseY), false),
                hover("top.sync", top.syncBounds(geometry.y).contains(mouseX, mouseY), false),
                hover("top.capture", top.captureBounds(geometry.y).contains(mouseX, mouseY),
                        state.captureLocked));
        drawTopText(graphics, font, geometry, top, state);
        BlueprintLibraryChromeRenderer.renderBodyFrames(
                canvas, geometry, state.captureLocked);
        if (state.captureLocked) {
            drawCaptureLocked(graphics, font, geometry, state.captureSaving);
            return;
        }
        BlueprintLibraryRowRenderer.render(graphics, font, canvas, geometry, state,
                actionWidths(font), mouseX, mouseY);
        BlueprintLibraryDetailsRenderer.render(graphics, font, canvas, geometry, state);
        graphics.text(font, trim(font, state.status,
                        width - BlueprintLibraryLayout.STATUS_TEXT_RIGHT_INSET),
                x + BlueprintLibraryLayout.STATUS_TEXT_X,
                geometry.statusY, state.statusColor, false);
    }

    static BlueprintLibraryLayout.TopBar topBar(
            Font font,
            int x,
            int width,
            boolean captureLocked) {
        return BlueprintLibraryLayout.topBar(
                x, width, captureLocked,
                font.width(text("screen.rtsbuilding.blueprints.open_folder_short")),
                font.width(text("screen.rtsbuilding.blueprints.import_file_short")),
                font.width(text("screen.rtsbuilding.blueprints.sync_create_short")),
                font.width(text(captureLocked
                        ? "screen.rtsbuilding.blueprints.capture_active_short"
                        : "screen.rtsbuilding.blueprints.capture_short")));
    }

    static BlueprintLibraryLayout.ActionTextWidths actionWidths(Font font) {
        return new BlueprintLibraryLayout.ActionTextWidths(
                font.width(text("screen.rtsbuilding.blueprints.save_as_short")),
                font.width(text("screen.rtsbuilding.blueprints.rename")),
                font.width(text("screen.rtsbuilding.blueprints.delete")));
    }

    static double hover(String stableId, boolean hovered, boolean selected) {
        UiControlState state = new UiControlState(
                true, selected, false, false, "")
                .withInteraction(hovered, false, false);
        return CONTROL_ANIMATIONS.update(
                stableId, state, Config.isUiAnimationsEnabled()).hover();
    }

    private static void drawTopText(
            GuiGraphicsExtractor graphics,
            Font font,
            BlueprintLibraryLayout.Geometry geometry,
            BlueprintLibraryLayout.TopBar top,
            BlueprintLibraryUiState state) {
        drawCentered(graphics, font, top.folderBounds(geometry.y),
                text("screen.rtsbuilding.blueprints.open_folder_short"));
        drawCentered(graphics, font, top.importBounds(geometry.y),
                text("screen.rtsbuilding.blueprints.import_file_short"));
        drawCentered(graphics, font, top.syncBounds(geometry.y),
                text("screen.rtsbuilding.blueprints.sync_create_short"));
        drawCentered(graphics, font, top.captureBounds(geometry.y),
                text(state.captureLocked
                        ? "screen.rtsbuilding.blueprints.capture_active_short"
                        : "screen.rtsbuilding.blueprints.capture_short"));
        String searchLabel = state.query.isEmpty() && !state.searchFocused
                ? text("screen.rtsbuilding.blueprints.search")
                : state.query + (state.searchFocused
                        && (net.minecraft.util.Util.getMillis() / 500L) % 2L == 0L
                        ? "_" : "");
        graphics.text(font, trim(font, searchLabel,
                        top.searchW - BlueprintLibraryLayout.SEARCH_TEXT_INSET * 2),
                top.searchX + BlueprintLibraryLayout.SEARCH_TEXT_INSET,
                geometry.y + BlueprintLibraryLayout.SEARCH_TEXT_TOP,
                state.query.isEmpty() && !state.searchFocused
                        ? BlueprintLibraryStyle.SEARCH_PLACEHOLDER_TEXT.toArgb()
                        : BlueprintLibraryStyle.SEARCH_TEXT.toArgb(), false);
    }

    private static void drawCaptureLocked(
            GuiGraphicsExtractor graphics,
            Font font,
            BlueprintLibraryLayout.Geometry geometry,
            boolean saving) {
        graphics.text(font, trim(font,
                        text("screen.rtsbuilding.blueprints.capture_tool_title"),
                        geometry.width - BlueprintLibraryLayout.CAPTURE_TEXT_X * 2),
                geometry.x + BlueprintLibraryLayout.CAPTURE_TEXT_X,
                geometry.listY + BlueprintLibraryLayout.CAPTURE_TITLE_Y,
                BlueprintLibraryStyle.PRIMARY_TEXT.toArgb(), false);
        graphics.text(font, trim(font,
                        text(saving
                                ? "screen.rtsbuilding.blueprints.status.save_busy"
                                : "screen.rtsbuilding.blueprints.status.capture_locked"),
                        geometry.width - BlueprintLibraryLayout.CAPTURE_TEXT_X * 2),
                geometry.x + BlueprintLibraryLayout.CAPTURE_TEXT_X,
                geometry.listY + BlueprintLibraryLayout.CAPTURE_STATUS_Y,
                BlueprintLibraryStyle.CAPTURE_WARNING_TEXT.toArgb(), false);
    }
}
