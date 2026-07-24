package com.rtsbuilding.rtsbuilding.client.presentation.plugin.binding;

import com.mojang.math.Axis;
import com.rtsbuilding.rtsbuilding.client.domain.state.LinkedStorageEntry;
import com.rtsbuilding.rtsbuilding.client.infrastructure.di.CompositionRoot;
import com.rtsbuilding.rtsbuilding.client.infrastructure.module.storage.StorageModule;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.base.component.ScrollBar;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.base.overlay.OverlayContext;
import com.rtsbuilding.rtsbuilding.client.util.animate.EasingFunctions;
import com.rtsbuilding.rtsbuilding.client.util.animate.FloatAnimation;
import com.rtsbuilding.rtsbuilding.client.util.render.CrossFadeRenderer;
import com.rtsbuilding.rtsbuilding.client.util.render.SpriteRenderer;
import com.rtsbuilding.rtsbuilding.client.util.render.TextRenderer;
import com.rtsbuilding.rtsbuilding.client.util.render.model.NineSliceRegion;
import com.rtsbuilding.rtsbuilding.client.util.render.model.SpriteRegion;
import com.rtsbuilding.rtsbuilding.client.util.render.model.TextureInfo;
import com.rtsbuilding.rtsbuilding.client.util.theme.ThemeManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.*;

public final class BindingRenderer {

    private final OverlayContext ctx;
    private final ScrollBar scrollBar;
    private final List<RowLayout> rowLayouts;
    private final PriorityEditController editController;
    private final EntryAnimationController animController;

    public BindingRenderer(OverlayContext ctx, ScrollBar scrollBar, List<RowLayout> rowLayouts,
                    PriorityEditController editController, EntryAnimationController animController) {
        this.ctx = ctx;
        this.scrollBar = scrollBar;
        this.rowLayouts = rowLayouts;
        this.editController = editController;
        this.animController = animController;
    }

    private static final int ROW_H = 20;
    private static final int ICON_SIZE = 12;
    private static final int PRIORITY_PAD_H = 4;
    private static final int PRIORITY_ICON_GAP = 2;
    private static final int ICON_TEXT_GAP = 4;

    private static final int BTN_HEIGHT = 14;

    private static final int BTN_PAD_H = 4;
    private static final int BTN_GAP = 2;

    private static final int ARROW_BTN_SIZE = 14;

    private static final int ARROW_DRAW_SIZE = 10;
    private static final int SCROLLBAR_W = 7;
    private static final int RIGHT_MARGIN = 4;
    private static final int LEFT_PAD = 5;
    private static final int TOP_PAD = 2;

    private static final int EDIT_INPUT_W = 40;
    private static final int EDIT_INPUT_H = 13;

    private static final long CURSOR_BLINK_MS = 600;

    private static final int UNBIND_COLOR = 0xFFE06060;
    private static final int UNBIND_HOVER_COLOR = 0xFFFF8080;
    private static final int MODE_BI_COLOR = 0xFF60C060;
    private static final int MODE_EXTRACT_COLOR = 0xFFE0A040;
    private static final int BTN_HOVER_FG = 0xFFFFFFFF;

    private static final int LOCATE_BTN_COLOR = 0xFF8080E0;
    private static final int LOCATE_BTN_HOVER_COLOR = 0xFFA0A0FF;

    private static final ResourceLocation INPUT_BOX_TEXTURE = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/base/base_ui/base_ui_4.png");
    private static final int INPUT_BOX_TEX_W = 32;
    private static final int INPUT_BOX_TEX_H = 32;
    private static final int INPUT_BOX_STATE_H = 16;
    private static final int INPUT_BOX_BORDER = 4;
    private static final TextureInfo INPUT_BOX_TEX_INFO = new TextureInfo(
            INPUT_BOX_TEXTURE, INPUT_BOX_TEX_W, INPUT_BOX_TEX_H,
            TextureInfo.ThemeLayout.HORIZONTAL_PAIR, TextureInfo.FilterMode.PIXEL);
    private static final NineSliceRegion INPUT_BOX_NINE_SLICE = NineSliceRegion.fullTheme(
            INPUT_BOX_TEX_INFO, INPUT_BOX_STATE_H, INPUT_BOX_BORDER);

    private static final ResourceLocation BTN_TEXTURE = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/base/base_ui/base_ui_2.png");
    private static final int BTN_TEX_W = 32;
    private static final int BTN_TEX_H = 48;
    private static final int BTN_STATE_H = 16;
    private static final int BTN_BORDER = 4;
    private static final TextureInfo BTN_TEX_INFO = new TextureInfo(
            BTN_TEXTURE, BTN_TEX_W, BTN_TEX_H,
            TextureInfo.ThemeLayout.HORIZONTAL_PAIR, TextureInfo.FilterMode.PIXEL);
    private static final NineSliceRegion BTN_NINE_SLICE = NineSliceRegion.fullTheme(
            BTN_TEX_INFO, BTN_STATE_H, BTN_BORDER);

    private static final ResourceLocation ARROW_TEXTURE = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/base/arrow.png");
    private static final int ARROW_TEX_FILE_W = 1024;
    private static final int ARROW_TEX_H = 512;
    private static final TextureInfo ARROW_TEX_INFO = new TextureInfo(
            ARROW_TEXTURE, ARROW_TEX_FILE_W, ARROW_TEX_H,
            TextureInfo.ThemeLayout.HORIZONTAL_PAIR, TextureInfo.FilterMode.PIXEL);

    private static final SpriteRegion ARROW_SPRITE = new SpriteRegion(
            ARROW_TEX_INFO, 0, 0, ARROW_TEX_INFO.halfWidth(), ARROW_TEX_H).withTheme();

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

    public void renderContent(GuiGraphics g) {
        StorageModule sm = CompositionRoot.get().module(StorageModule.class);
        if (sm == null) return;

        var entries = sm.getLinkedStorageEntries();
        var names = sm.getLinkedDisplayNames();
        var iconIds = sm.getLinkedIconItemIds();
        var priorities = sm.getLinkedPriorities();
        int count = Math.min(entries.size(), Math.min(names.size(),
                Math.min(iconIds.size(), priorities.size())));
        if (count == 0) {
            renderEmptyHint(g);
            return;
        }

        editController.tick(count);
        animController.tick(count);

        int x = ctx.getX(), y = ctx.getY(), w = ctx.getWidth(), h = ctx.getHeight();
        int mouseX = ctx.getLastMouseX(), mouseY = ctx.getLastMouseY();
        Minecraft mc = Minecraft.getInstance();
        int visibleH = h - TOP_PAD * 2;

        scrollBar.setContent(count * ROW_H, visibleH);
        int scroll = scrollBar.getScroll();

        renderBackgroundRows(g, x, y, w, count, scroll, visibleH, mouseX, mouseY);

        List<Integer> sortedIndices = buildSortedIndices(count, priorities);

        RowLayout.ButtonBar btnBar = new RowLayout.ButtonBar(mc, scrollBar.isVisible(), x, w);
        int fontColor = ThemeManager.getTextColor();

        NineSliceRegion themedBtnNormal = BTN_NINE_SLICE.withTheme();
        NineSliceRegion themedBtnHover = BTN_NINE_SLICE.withTheme().withVOffset(BTN_STATE_H);
        NineSliceRegion themedInputNormal = INPUT_BOX_NINE_SLICE.withTheme();
        NineSliceRegion themedInputFocus = INPUT_BOX_NINE_SLICE.withTheme().withVOffset(INPUT_BOX_STATE_H);

        rowLayouts.clear();
        int clipY = y + TOP_PAD;
        for (int vi = 0; vi < count; vi++) {
            int origIdx = sortedIndices.get(vi);
            RowLayout rl = new RowLayout();
            rl.originalIndex = origIdx;
            rowLayouts.add(rl);

            renderSingleRow(g, x, y, scroll, vi, origIdx, rl, entries, names, iconIds, priorities,
                    btnBar, fontColor, mc, mouseX, mouseY, clipY, visibleH,
                    themedBtnNormal, themedBtnHover, themedInputNormal, themedInputFocus);
        }

        renderScrollbar(g, x, y, h);
    }

    private void renderBackgroundRows(GuiGraphics g, int x, int y, int w, int count,
                                       int scroll, int visibleH, int mouseX, int mouseY) {
        int firstRow = scroll / ROW_H;
        int totalRows = visibleH / ROW_H + 2;

        int bgThemeOffset = SpriteRenderer.getNineSliceThemeOffset(BG_NINE_SLICE);
        NineSliceRegion normalEvenSlice = BG_NINE_SLICE.withVOffset(0);
        NineSliceRegion normalOddSlice = BG_NINE_SLICE.withVOffset(BG_STATE_H);
        NineSliceRegion hoveredSlice = BG_NINE_SLICE.withVOffset(BG_STATE_H * 2);

        for (int i = firstRow; i < firstRow + totalRows; i++) {
            int bgTop = y + TOP_PAD + i * ROW_H - scroll;
            boolean hovered = !ctx.isDividerDragging() && i < count
                    && mouseX >= x && mouseX < x + w
                    && mouseY >= bgTop && mouseY < bgTop + ROW_H;

            float barHoverT = animController.tickBarHover(i, hovered);
            NineSliceRegion baseSlice = (i % 2 == 0) ? normalEvenSlice : normalOddSlice;
            CrossFadeRenderer.render(g, barHoverT,
                    () -> SpriteRenderer.drawNineSlice(g, baseSlice, bgThemeOffset, x, bgTop, w, ROW_H),
                    () -> SpriteRenderer.drawNineSlice(g, hoveredSlice, bgThemeOffset, x, bgTop, w, ROW_H));
        }
    }

    private void renderSingleRow(GuiGraphics g, int x, int y, int scroll,
                                  int vi, int origIdx, RowLayout rl,
                                  List<LinkedStorageEntry> entries, List<String> names,
                                  List<String> iconIds, List<Integer> priorities,
                                  RowLayout.ButtonBar btnBar, int fontColor, Minecraft mc,
                                  int mouseX, int mouseY, int clipY, int clipH,
                                  NineSliceRegion themedBtnNormal, NineSliceRegion themedBtnHover,
                                  NineSliceRegion themedInputNormal, NineSliceRegion themedInputFocus) {
        int lineH = mc.font.lineHeight;

        int baseRowY = TOP_PAD + vi * ROW_H;
        float animY = animController.updateEntryAnimY(origIdx, baseRowY);
        int contentY = y + Math.round(animY) - scroll;
        rl.y = contentY;

        boolean rowVisible = contentY + ROW_H >= clipY && contentY < clipY + clipH;
        boolean isEditingRow = editController.isEditingRow(vi);
        boolean actuallyRender = rowVisible || isEditingRow;

        var entry = entries.get(origIdx);
        String name = names.get(origIdx);
        String iconItemId = iconIds.get(origIdx);
        int priority = priorities.get(origIdx);
        boolean dimmed = !entry.worldAvailable();

        int rowCenterY = contentY + ROW_H / 2;
        int cursorX = x + LEFT_PAD;

        int arrowBtnY = rowCenterY - ARROW_BTN_SIZE / 2;
        rl.arrowBtnX = cursorX;
        if (actuallyRender) {
            renderArrowButton(g, cursorX, arrowBtnY, vi == 0, themedBtnNormal);
        }
        cursorX += ARROW_BTN_SIZE + PRIORITY_ICON_GAP;
        rl.priorityX = cursorX;

        int priorityBoxW = mc.font.width(String.valueOf(priority)) + PRIORITY_PAD_H * 2;
        rl.priorityW = priorityBoxW;
        float animW = editController.computePriorityBoxWidth(priorityBoxW, isEditingRow, vi);
        if (actuallyRender || isEditingRow) {
            renderPriorityBox(g, cursorX, rowCenterY, String.valueOf(priority),
                    isEditingRow, dimmed, (int) animW, themedInputNormal, themedInputFocus);
        }
        cursorX += (int) animW + PRIORITY_ICON_GAP;

        if (actuallyRender && !iconItemId.isEmpty()) {
            ItemStack stack = resolveItemStack(iconItemId);
            if (!stack.isEmpty()) {
                renderItemIcon(g, stack, cursorX + ICON_SIZE / 2, rowCenterY);
            }
        }
        cursorX += ICON_SIZE;

        if (actuallyRender) {
            int maxNameW = Math.max(0, btnBar.locateX() - cursorX - ICON_TEXT_GAP - BTN_GAP);
            String displayName = TextRenderer.trimToWidth(mc.font, name, maxNameW);
            int nameX = cursorX + ICON_TEXT_GAP;
            int nameColor = dimmed ? (fontColor & 0xFFFFFF) | 0x60000000 : fontColor;
            TextRenderer.draw(g, displayName, nameX, rowCenterY - lineH / 2, nameColor);
        }

        if (actuallyRender) {
            renderActionButtons(g, entry, rl, btnBar, rowCenterY, mouseX, mouseY, themedBtnNormal, themedBtnHover);
        }
    }

    private void renderArrowButton(GuiGraphics g, int btnX, int btnY, boolean isFirst, NineSliceRegion themedBtnSlice) {
        SpriteRenderer.drawNineSlice(g, themedBtnSlice, btnX, btnY, ARROW_BTN_SIZE, ARROW_BTN_SIZE);
        var pose = g.pose();
        pose.pushPose();
        pose.translate(btnX + ARROW_BTN_SIZE / 2, btnY + ARROW_BTN_SIZE / 2, 0);
        if (isFirst) {
            pose.mulPose(Axis.ZP.rotationDegrees(180f));
        }
        SpriteRenderer.drawSprite(g, ARROW_SPRITE,
                -ARROW_DRAW_SIZE / 2, -ARROW_DRAW_SIZE / 2,
                ARROW_DRAW_SIZE, ARROW_DRAW_SIZE);
        pose.popPose();
    }

    private void renderActionButtons(GuiGraphics g, LinkedStorageEntry entry, RowLayout rl,
                                      RowLayout.ButtonBar btnBar, int rowCenterY, int mouseX, int mouseY,
                                      NineSliceRegion themedBtnNormal, NineSliceRegion themedBtnHover) {
        int btnY = rowCenterY - BTN_HEIGHT / 2;

        String locateText;
        if (entry.worldAvailable()) {
            StorageModule sm = CompositionRoot.get().module(StorageModule.class);
            boolean showLocate = sm != null && sm.isLocationDisplayActive(entry.pos());
            locateText = showLocate ? "关闭显示" : "开启位置";
        } else {
            locateText = "开启位置";
        }
        int locateBtnW = Minecraft.getInstance().font.width(locateText) + BTN_PAD_H * 2;
        int locateBtnX = btnBar.locateX();
        rl.locateBtnX = locateBtnX;
        rl.locateBtnW = locateBtnW;
        boolean hoverLocate = !ctx.isDividerDragging()
                && inRect(mouseX, mouseY, locateBtnX, btnY, locateBtnW, BTN_HEIGHT);
        drawTextButton(g, locateBtnX, btnY, locateText,
                hoverLocate ? LOCATE_BTN_HOVER_COLOR : LOCATE_BTN_COLOR,
                themedBtnNormal, themedBtnHover);

        String unbindText = "解绑";
        int unbindBtnW = Minecraft.getInstance().font.width(unbindText) + BTN_PAD_H * 2;
        int unbindX = btnBar.unbindX();
        rl.unbindX = unbindX;
        rl.unbindW = unbindBtnW;
        boolean hoverUnbind = !ctx.isDividerDragging()
                && inRect(mouseX, mouseY, unbindX, btnY, unbindBtnW, BTN_HEIGHT);
        drawTextButton(g, unbindX, btnY, unbindText,
                hoverUnbind ? UNBIND_HOVER_COLOR : UNBIND_COLOR,
                themedBtnNormal, themedBtnHover);

        String toggleText = entry.isExtractOnly() ? "仅提取" : "双向";
        int toggleBtnW = Minecraft.getInstance().font.width(toggleText) + BTN_PAD_H * 2;
        int toggleX = btnBar.toggleX();
        rl.toggleX = toggleX;
        rl.toggleW = toggleBtnW;
        boolean hoverToggle = !ctx.isDividerDragging()
                && inRect(mouseX, mouseY, toggleX, btnY, toggleBtnW, BTN_HEIGHT);
        int toggleColor = entry.isExtractOnly() ? MODE_EXTRACT_COLOR : MODE_BI_COLOR;
        drawTextButton(g, toggleX, btnY, toggleText,
                hoverToggle ? BTN_HOVER_FG : toggleColor,
                themedBtnNormal, themedBtnHover);
    }

    private void drawTextButton(GuiGraphics g, int btnX, int btnY, String text, int textColor,
                                NineSliceRegion themedBtnNormal, NineSliceRegion themedBtnHover) {
        int btnW = Minecraft.getInstance().font.width(text) + BTN_PAD_H * 2;
        NineSliceRegion slice = textColor == UNBIND_HOVER_COLOR
                || textColor == LOCATE_BTN_HOVER_COLOR
                || textColor == BTN_HOVER_FG
                ? themedBtnHover : themedBtnNormal;
        SpriteRenderer.drawNineSlice(g, slice, btnX, btnY, btnW, BTN_HEIGHT);
        int lineH = Minecraft.getInstance().font.lineHeight;
        TextRenderer.drawCentered(g, Minecraft.getInstance().font, text,
                btnX + btnW / 2, btnY + (BTN_HEIGHT - lineH) / 2, textColor);
    }

    private void renderPriorityBox(GuiGraphics g, int boxX, int centerY,
                                    String priorityStr, boolean editing, boolean dimmed, int boxW,
                                    NineSliceRegion themedInputNormal, NineSliceRegion themedInputFocus) {
        int boxY = centerY - EDIT_INPUT_H / 2;

        float crossFadeT = editController.getAnimValue();
        CrossFadeRenderer.render(g, crossFadeT,
                () -> SpriteRenderer.drawNineSlice(g, themedInputNormal, boxX, boxY, boxW, EDIT_INPUT_H),
                () -> SpriteRenderer.drawNineSlice(g, themedInputFocus, boxX, boxY, boxW, EDIT_INPUT_H));

        Minecraft mc = Minecraft.getInstance();
        int fontColor = ThemeManager.getTextColor();
        int textColor = editing ? fontColor : (dimmed ? (fontColor & 0xFFFFFF) | 0x60000000 : fontColor);
        int textX = boxX + 3;
        int textY = boxY + (EDIT_INPUT_H - mc.font.lineHeight) / 2;

        if (editing) {
            String text = editController.getBufferText();
            if (!text.isEmpty()) {
                String visible = TextRenderer.trimToWidth(mc.font, text, boxW - 8);
                TextRenderer.draw(g, visible, textX, textY, textColor);
            }
            long elapsed = System.currentTimeMillis() - editController.getStartTime();
            if ((elapsed / CURSOR_BLINK_MS) % 2 == 0) {
                int cursorVisualX = mc.font.width(text.isEmpty() ? "0"
                        : text.substring(0, Math.min(editController.getBufferLength(), text.length())));
                int clampedX = Math.min(cursorVisualX, boxW - 8);
                g.fill(textX + clampedX, textY,
                        textX + clampedX + 1, textY + mc.font.lineHeight, 0xFFFFFFFF);
            }
        } else if (priorityStr != null && !priorityStr.isEmpty()) {
            int textWidth = mc.font.width(priorityStr);
            int centeredTextX = boxX + (boxW - textWidth) / 2;
            TextRenderer.draw(g, priorityStr, centeredTextX, textY, textColor);
        }
    }

    private void renderEmptyHint(GuiGraphics g) {
        String hint = "No linked";
        int textColor = ThemeManager.getTextColor() & 0xFFFFFF | 0x60000000;
        int lineH = Minecraft.getInstance().font.lineHeight;
        TextRenderer.drawCentered(g, Minecraft.getInstance().font, hint,
                ctx.getX() + ctx.getWidth() / 2, ctx.getY() + (ctx.getHeight() - lineH) / 2, textColor);
    }

    private void renderItemIcon(GuiGraphics g, ItemStack stack, int centerX, int centerY) {
        if (stack.isEmpty()) return;
        var pose = g.pose();
        pose.pushPose();
        float scale = (float) ICON_SIZE / 16.0f;
        pose.translate(centerX, centerY, 0);
        pose.scale(scale, scale, 1.0f);
        g.renderItem(stack, -8, -8);
        pose.popPose();
    }

    private void renderScrollbar(GuiGraphics g, int x, int y, int h) {
        int visibleH = h - TOP_PAD * 2;
        int barX = x + ctx.getWidth() - SCROLLBAR_W - RIGHT_MARGIN;
        scrollBar.render(g, barX, y + TOP_PAD + 6, visibleH - 12);
    }

    private static List<Integer> buildSortedIndices(int count, List<Integer> priorities) {
        List<Integer> sorted = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            sorted.add(i);
        }
        sorted.sort(Comparator.comparingInt(priorities::get));
        return sorted;
    }

    private static ItemStack resolveItemStack(String itemId) {
        if (itemId == null || itemId.isBlank()) return ItemStack.EMPTY;
        ResourceLocation key = ResourceLocation.tryParse(itemId);
        if (key == null || !BuiltInRegistries.ITEM.containsKey(key)) return ItemStack.EMPTY;
        return new ItemStack(BuiltInRegistries.ITEM.get(key));
    }

    private static boolean inRect(int px, int py, int rx, int ry, int rw, int rh) {
        return px >= rx && px < rx + rw && py >= ry && py < ry + rh;
    }
}
