package com.rtsbuilding.rtsbuilding.client.presentation.plugin.workflow;

import com.rtsbuilding.rtsbuilding.client.domain.state.WorkflowProgress;
import com.rtsbuilding.rtsbuilding.client.infrastructure.module.workflow.WorkflowModule;
import com.rtsbuilding.rtsbuilding.client.kernel.RtsClientKernel;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.base.component.ScrollBar;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.base.overlay.OverlayContext;
import com.rtsbuilding.rtsbuilding.client.util.animate.AnimFloat;
import com.rtsbuilding.rtsbuilding.client.util.animate.ColorAnimation;
import com.rtsbuilding.rtsbuilding.client.util.animate.Easing;
import com.rtsbuilding.rtsbuilding.client.util.render.DarkUiPalette;
import com.rtsbuilding.rtsbuilding.client.util.render.SdfRenderer;
import com.rtsbuilding.rtsbuilding.client.util.render.SpriteRenderer;
import com.rtsbuilding.rtsbuilding.client.util.render.TextRenderer;
import com.rtsbuilding.rtsbuilding.client.util.render.model.NineSliceRegion;
import com.rtsbuilding.rtsbuilding.client.util.render.model.TextureInfo;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowProgressProcessor;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WorkflowRenderer {

    private static final int ROW_HEIGHT = 20;
    private static final int TOP_PAD = 2;
    private static final int ROW_PAD_H = 6;
    private static final int BAR_HEIGHT = 12;

    private static final int TEXT_COLOR = 0xFFCCCCCC;
    private static final int SUPPRESSED_TEXT_COLOR = 0xFF888888;

    private static final int BTN_SIZE = 14;
    private static final int TOGGLE_ICON_SIZE = 10;
    private static final int CLOSE_ICON_SIZE = 8;
    private static final int BTN_GAP = 2;
    private static final int BTN_AREA_W = BTN_SIZE * 2 + BTN_GAP;

    private static final int SCROLLBAR_GAP = 3;
    private static final int SCROLLBAR_TRACK_W = 7;



    private static final ResourceLocation BG_TEXTURE = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/base/base_ui/base_ui_7.png");
    private static final int BG_TEX_W = 32;
    private static final int BG_TEX_H = 48;
    private static final int BG_STATE_H = 16;
    private static final int BG_BORDER = 4;
    private static final TextureInfo BG_TEX_INFO = new TextureInfo(
            BG_TEXTURE, BG_TEX_W, BG_TEX_H,
            TextureInfo.ThemeLayout.HORIZONTAL_PAIR, TextureInfo.FilterMode.PIXEL);
    private static final NineSliceRegion BG_NINE_SLICE = NineSliceRegion.fullTheme(
            BG_TEX_INFO, BG_STATE_H, BG_BORDER);



    private final OverlayContext context;
    private final ScrollBar scrollBar;
    private final List<RowLayout> rowLayouts;

    private final Map<Integer, AnimFloat> toggleBtnHovers = new HashMap<>();
    private final Map<Integer, AnimFloat> deleteBtnHovers = new HashMap<>();
    private final Map<Integer, AnimFloat> toggleStateAnims = new HashMap<>();
    private final Map<Integer, Boolean> prevOnHold = new HashMap<>();

    public WorkflowRenderer(OverlayContext context, ScrollBar scrollBar, List<RowLayout> rowLayouts) {
        this.context = context;
        this.scrollBar = scrollBar;
        this.rowLayouts = rowLayouts;
    }

    public void renderContent(GuiGraphics g) {
        WorkflowModule wm = RtsClientKernel.get().module(WorkflowModule.class);
        if (wm == null) return;

        WorkflowProgress progress = wm.getProgress();
        RtsWorkflowStatus[] statuses = progress.statuses();

        Minecraft mc = Minecraft.getInstance();
        var font = mc.font;

        int baseOy = context.getY() + TOP_PAD;
        int visibleH = context.getHeight() - TOP_PAD * 2;

        rowLayouts.clear();

        int mx = context.getLastMouseX();
        int my = context.getLastMouseY();

        int activeCount = 0;
        for (RtsWorkflowStatus status : statuses) {
            if (status != null && status.isActive()) activeCount++;
        }
        int totalH = activeCount * ROW_HEIGHT;
        scrollBar.setContent(totalH, visibleH);
        int scroll = scrollBar.getScroll();

        int scrollbarW = scrollBar.isVisible() ? SCROLLBAR_TRACK_W + SCROLLBAR_GAP : 0;
        int ox = context.getX() + ROW_PAD_H;
        int contentW = context.getWidth() - ROW_PAD_H * 2 - scrollbarW;
        int barW = contentW - BTN_AREA_W - BTN_GAP;

        int btnRight = ox + contentW;
        int deleteBtnX = btnRight - BTN_SIZE;
        int toggleBtnX = deleteBtnX - BTN_GAP - BTN_SIZE;

        int bgThemeOffset = SpriteRenderer.getNineSliceThemeOffset(BG_NINE_SLICE);
        NineSliceRegion bgEven = BG_NINE_SLICE.withVOffset(0);
        NineSliceRegion bgOdd = BG_NINE_SLICE.withVOffset(BG_STATE_H);

        int firstRow = scroll / ROW_HEIGHT;
        int totalBgRows = visibleH / ROW_HEIGHT + 2;
        for (int i = firstRow; i < firstRow + totalBgRows; i++) {
            if (i < 0) continue;
            int bgTop = baseOy + i * ROW_HEIGHT - scroll;
            NineSliceRegion slice = (i % 2 == 0) ? bgEven : bgOdd;
            SpriteRenderer.drawNineSlice(g, slice, bgThemeOffset, context.getX(), bgTop, context.getWidth(), ROW_HEIGHT);
        }

        int oy = baseOy - scroll;

        for (int i = 0; i < statuses.length; i++) {
            RtsWorkflowStatus status = statuses[i];
            if (status == null || !status.isActive()) continue;

            String label = RtsWorkflowProgressProcessor.formatLabel(status);
            String progressText = RtsWorkflowProgressProcessor.formatProgressText(status);
            int fillW = RtsWorkflowProgressProcessor.computeFillWidth(status, barW);

            int rowY = oy;
            int barY = rowY + 4;
            int btnY = barY + (BAR_HEIGHT - BTN_SIZE) / 2;

            int labelColor = status.onHold() ? SUPPRESSED_TEXT_COLOR : TEXT_COLOR;

            float fillRatio = (float) fillW / barW;
            int fillStart = status.onHold() ? 0xFFFF8C00 : 0xFF2E7D32;
            int fillEnd = status.onHold() ? 0xFFFFB74D : 0xFF66BB6A;
            SdfRenderer.drawProgressBar(g, ox, barY, barW, BAR_HEIGHT, fillRatio,
                    DarkUiPalette.accent(), fillStart, fillEnd, DarkUiPalette.hoverBorder());

            int textCenterY = barY + (BAR_HEIGHT - font.lineHeight) / 2 + 1;
            int textRightBound = ox + barW - 2;
            int textX = textRightBound - font.width(progressText);
            TextRenderer.draw(g, label, ox + 4, textCenterY, labelColor);
            TextRenderer.draw(g, progressText, textX, textCenterY, labelColor);

            int toggleIconX = toggleBtnX + (BTN_SIZE - TOGGLE_ICON_SIZE) / 2;
            int toggleIconY = btnY + (BTN_SIZE - TOGGLE_ICON_SIZE) / 2;
            int closeIconX = deleteBtnX + (BTN_SIZE - CLOSE_ICON_SIZE) / 2;
            int closeIconY = btnY + (BTN_SIZE - CLOSE_ICON_SIZE) / 2;

            AnimFloat toggleHover = toggleBtnHovers.computeIfAbsent(i, k -> AnimFloat.hover());
            AnimFloat deleteHover = deleteBtnHovers.computeIfAbsent(i, k -> AnimFloat.hover());

            boolean toggleHovered = mx >= toggleBtnX && mx < toggleBtnX + BTN_SIZE
                    && my >= btnY && my < btnY + BTN_SIZE;
            boolean deleteHovered = mx >= deleteBtnX && mx < deleteBtnX + BTN_SIZE
                    && my >= btnY && my < btnY + BTN_SIZE;

            {
                float t = toggleHover.track(toggleHovered);
                int fill = ColorAnimation.lerpRGB(DarkUiPalette.bg(), DarkUiPalette.accent(), t);
                SdfRenderer.drawBorderedRoundedRect(g, toggleBtnX, btnY, BTN_SIZE, BTN_SIZE, 4, DarkUiPalette.black(), fill, 1);
            }
            {
                AnimFloat stateAnim = toggleStateAnims.computeIfAbsent(i, k -> AnimFloat.of(0f, 200L, Easing.EASE_OUT_QUAD));
                Boolean prev = prevOnHold.get(i);
                if (prev == null || prev != status.onHold()) {
                    stateAnim.target(status.onHold() ? 1f : 0f);
                    prevOnHold.put(i, status.onHold());
                }
                float stateT = stateAnim.get();
                int playSize = TOGGLE_ICON_SIZE * 4 / 5;
                int playX = toggleBtnX + (BTN_SIZE - playSize) / 2;
                int playY = btnY + (BTN_SIZE - playSize) / 2;
                int iconCx = toggleIconX + TOGGLE_ICON_SIZE / 2;
                int iconCy = toggleIconY + TOGGLE_ICON_SIZE / 2;
                int playAlpha = Math.round(stateT * 255);
                int pauseAlpha = Math.round((1f - stateT) * 255);
                SdfRenderer.drawChevron(g, playX, playY, playSize, playSize, (playAlpha << 24) | 0x00FFFFFF, 1f);
                SdfRenderer.drawPauseIcon(g, iconCx, iconCy, TOGGLE_ICON_SIZE, (pauseAlpha << 24) | 0x00FFFFFF);
            }

            {
                float t = deleteHover.track(deleteHovered);
                int fill = ColorAnimation.lerpRGB(DarkUiPalette.bg(), DarkUiPalette.accent(), t);
                SdfRenderer.drawBorderedRoundedRect(g, deleteBtnX, btnY, BTN_SIZE, BTN_SIZE, 4, DarkUiPalette.black(), fill, 1);
            }
            SdfRenderer.drawRoundedRect(g, closeIconX, closeIconY, CLOSE_ICON_SIZE, CLOSE_ICON_SIZE, 2, 0xFFFF4444);

            rowLayouts.add(new RowLayout(i, status.entryId(), toggleBtnX, deleteBtnX, btnY, rowY, ROW_HEIGHT));

            oy += ROW_HEIGHT;
        }

        if (activeCount == 0) {
            String text = Component.translatable("screen.rtsbuilding.workflow.none").getString();
            int tx = context.getX() + (context.getWidth() - font.width(text)) / 2;
            int ty = context.getY() + (context.getHeight() - font.lineHeight) / 2;
            TextRenderer.draw(g, text, tx, ty, SUPPRESSED_TEXT_COLOR);
        }

        toggleBtnHovers.keySet().removeIf(k -> k >= statuses.length || statuses[k] == null || !statuses[k].isActive());
        deleteBtnHovers.keySet().removeIf(k -> k >= statuses.length || statuses[k] == null || !statuses[k].isActive());

        if (scrollBar.isVisible()) {
            int barX = ox + contentW + SCROLLBAR_GAP;
            scrollBar.render(g, barX, baseOy, visibleH);
        }
    }
}
