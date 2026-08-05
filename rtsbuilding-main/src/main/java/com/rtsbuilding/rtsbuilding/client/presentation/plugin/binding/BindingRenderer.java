package com.rtsbuilding.rtsbuilding.client.presentation.plugin.binding;

import com.mojang.math.Axis;
import com.rtsbuilding.rtsbuilding.client.domain.state.LinkedStorageEntry;
import com.rtsbuilding.rtsbuilding.client.infrastructure.module.storage.StorageModule;
import com.rtsbuilding.rtsbuilding.client.kernel.RtsClientKernel;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.base.component.ScrollBar;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.base.overlay.OverlayContext;
import com.rtsbuilding.rtsbuilding.client.util.animate.AnimFloat;
import com.rtsbuilding.rtsbuilding.client.util.animate.ColorAnimation;
import com.rtsbuilding.rtsbuilding.client.util.render.DarkUiPalette;
import com.rtsbuilding.rtsbuilding.client.util.render.SdfRenderer;
import com.rtsbuilding.rtsbuilding.client.util.render.TextRenderer;
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

    private final Map<Integer, AnimFloat> arrowHoverAnims = new HashMap<>();
    private final Map<Integer, AnimFloat> locateHoverAnims = new HashMap<>();
    private final Map<Integer, AnimFloat> unbindHoverAnims = new HashMap<>();
    private final Map<Integer, AnimFloat> toggleHoverAnims = new HashMap<>();
    private final Map<Integer, AnimFloat> priorityHoverAnims = new HashMap<>();

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

    private static final int ARROW_DRAW_SIZE = 6;
    private static final int SCROLLBAR_W = 7;
    private static final int RIGHT_MARGIN = 4;
    private static final int LEFT_PAD = 5;
    private static final int TOP_PAD = 2;

    private static final int EDIT_INPUT_W = 40;
    private static final int EDIT_INPUT_H = ARROW_BTN_SIZE;

    private static final long CURSOR_BLINK_MS = 600;

    private static final int UNBIND_COLOR = 0xFFE06060;
    private static final int UNBIND_HOVER_COLOR = 0xFFFF8080;
    private static final int MODE_BI_COLOR = 0xFF60C060;
    private static final int MODE_EXTRACT_COLOR = 0xFFE0A040;
    private static final int BTN_HOVER_FG = 0xFFFFFFFF;

    private static final int LOCATE_BTN_COLOR = 0xFF8080E0;
    private static final int LOCATE_BTN_HOVER_COLOR = 0xFFA0A0FF;

    public void renderContent(GuiGraphics g) {
        StorageModule sm = RtsClientKernel.get().module(StorageModule.class);
        if (sm == null) return;

        var entries = sm.getLinkedStorageEntries();
        var names = sm.getLinkedDisplayNames();
        var iconIds = sm.getLinkedIconItemIds();
        var priorities = sm.getLinkedPriorities();
        int count = Math.min(entries.size(), Math.min(names.size(),
                Math.min(iconIds.size(), priorities.size())));

        if (count > 0) {
            editController.tick(count);
            animController.tick(count);
        }

        int x = ctx.getX(), y = ctx.getY(), w = ctx.getWidth(), h = ctx.getHeight();
        int mouseX = ctx.getLastMouseX(), mouseY = ctx.getLastMouseY();
        Minecraft mc = Minecraft.getInstance();
        int visibleH = h - TOP_PAD * 2;

        scrollBar.setContent(count * ROW_H, visibleH);
        int scroll = scrollBar.getScroll();

        renderBackgroundRows(g, x, y, w, count, scroll, visibleH, mouseX, mouseY);

        if (count > 0) {
            List<Integer> sortedIndices = buildSortedIndices(count, priorities);

            RowLayout.ButtonBar btnBar = new RowLayout.ButtonBar(mc, scrollBar.isVisible(), x, w);
            int fontColor = ThemeManager.getTextColor();

            rowLayouts.clear();
            int clipY = y + TOP_PAD;
            for (int vi = 0; vi < count; vi++) {
                int origIdx = sortedIndices.get(vi);
                RowLayout rl = new RowLayout();
                rl.originalIndex = origIdx;
                rowLayouts.add(rl);

                renderSingleRow(g, x, y, scroll, vi, origIdx, rl, entries, names, iconIds, priorities,
                        btnBar, fontColor, mc, mouseX, mouseY, clipY, visibleH);
            }
        } else {
            String hint = "No linked";
            int textColor = ThemeManager.getTextColor() & 0xFFFFFF | 0x60000000;
            int lineH = Minecraft.getInstance().font.lineHeight;
            TextRenderer.drawCentered(g, Minecraft.getInstance().font, hint,
                    ctx.getX() + ctx.getWidth() / 2, ctx.getY() + (ctx.getHeight() - lineH) / 2, textColor);
        }

        renderScrollbar(g, x, y, h);
    }

    private void renderBackgroundRows(GuiGraphics g, int x, int y, int w, int count,
                                       int scroll, int visibleH, int mouseX, int mouseY) {
        int firstRow = scroll / ROW_H;
        int totalRows = visibleH / ROW_H + 2;

        for (int i = firstRow; i < firstRow + totalRows; i++) {
            int bgTop = y + TOP_PAD + i * ROW_H - scroll;
            int color = (i % 2 == 0) ? DarkUiPalette.p7() : DarkUiPalette.p6();
            g.fill(x, bgTop, x + w, bgTop + ROW_H, color);
        }
    }

    private void renderSingleRow(GuiGraphics g, int x, int y, int scroll,
                                  int vi, int origIdx, RowLayout rl,
                                  List<LinkedStorageEntry> entries, List<String> names,
                                  List<String> iconIds, List<Integer> priorities,
                                  RowLayout.ButtonBar btnBar, int fontColor, Minecraft mc,
                                  int mouseX, int mouseY, int clipY, int clipH) {
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
            renderArrowButton(g, cursorX, arrowBtnY, vi == 0, mouseX, mouseY);
        }
        cursorX += ARROW_BTN_SIZE + PRIORITY_ICON_GAP;
        rl.priorityX = cursorX;

        int priorityBoxW = mc.font.width(String.valueOf(priority)) + PRIORITY_PAD_H * 2;
        rl.priorityW = priorityBoxW;
        float animW = editController.computePriorityBoxWidth(priorityBoxW, isEditingRow, vi);
        if (actuallyRender || isEditingRow) {
            boolean hoverPriority = !ctx.isDividerDragging() && !isEditingRow
                    && mouseX >= cursorX && mouseX < cursorX + (int) animW
                    && mouseY >= rowCenterY - EDIT_INPUT_H / 2 && mouseY < rowCenterY + EDIT_INPUT_H / 2;
            renderPriorityBox(g, cursorX, rowCenterY, String.valueOf(priority),
                    isEditingRow, dimmed, (int) animW, vi,
                    priorityHoverAnims.computeIfAbsent(vi, k -> AnimFloat.hover()).track(hoverPriority));
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
            renderActionButtons(g, entry, rl, btnBar, rowCenterY, mouseX, mouseY);
        }
    }

    private void renderArrowButton(GuiGraphics g, int btnX, int btnY, boolean isFirst, int mouseX, int mouseY) {
        boolean hovering = !ctx.isDividerDragging()
                && inRect(mouseX, mouseY, btnX, btnY, ARROW_BTN_SIZE, ARROW_BTN_SIZE);
        int rowIndex = rowLayouts.size();
        AnimFloat anim = arrowHoverAnims.computeIfAbsent(rowIndex, k -> AnimFloat.hover());
        float t = anim.track(hovering);
        int fillColor = ColorAnimation.lerpRGB(DarkUiPalette.bg(), DarkUiPalette.accent(), t);
        SdfRenderer.drawBorderedRoundedRect(g, btnX, btnY, ARROW_BTN_SIZE, ARROW_BTN_SIZE, 4,
                DarkUiPalette.black(), fillColor, 1);
        var pose = g.pose();
        pose.pushPose();
        pose.translate(btnX + ARROW_BTN_SIZE / 2, btnY + ARROW_BTN_SIZE / 2, 0);
        pose.mulPose(Axis.ZP.rotationDegrees(isFirst ? 90f : -90f));
        int half = ARROW_DRAW_SIZE / 2;
        SdfRenderer.drawChevron(g, -half, -half, ARROW_DRAW_SIZE, ARROW_DRAW_SIZE,
                ThemeManager.getTextColor(), 0.5f);
        pose.popPose();
    }

    private void renderActionButtons(GuiGraphics g, LinkedStorageEntry entry, RowLayout rl,
                                      RowLayout.ButtonBar btnBar, int rowCenterY, int mouseX, int mouseY) {
        int btnY = rowCenterY - BTN_HEIGHT / 2;
        int rowIndex = rowLayouts.size() - 1;

        String locateText;
        if (entry.worldAvailable()) {
            StorageModule sm = RtsClientKernel.get().module(StorageModule.class);
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
                locateHoverAnims.computeIfAbsent(rowIndex * 3, k -> AnimFloat.hover()).track(hoverLocate));

        String unbindText = "解绑";
        int unbindBtnW = Minecraft.getInstance().font.width(unbindText) + BTN_PAD_H * 2;
        int unbindX = btnBar.unbindX();
        rl.unbindX = unbindX;
        rl.unbindW = unbindBtnW;
        boolean hoverUnbind = !ctx.isDividerDragging()
                && inRect(mouseX, mouseY, unbindX, btnY, unbindBtnW, BTN_HEIGHT);
        drawTextButton(g, unbindX, btnY, unbindText,
                hoverUnbind ? UNBIND_HOVER_COLOR : UNBIND_COLOR,
                unbindHoverAnims.computeIfAbsent(rowIndex * 3 + 1, k -> AnimFloat.hover()).track(hoverUnbind));

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
                toggleHoverAnims.computeIfAbsent(rowIndex * 3 + 2, k -> AnimFloat.hover()).track(hoverToggle));
    }

    private void drawTextButton(GuiGraphics g, int btnX, int btnY, String text, int textColor, float hoverT) {
        int btnW = Minecraft.getInstance().font.width(text) + BTN_PAD_H * 2;
        int fillColor = ColorAnimation.lerpRGB(DarkUiPalette.bg(), DarkUiPalette.accent(), hoverT);
        SdfRenderer.drawBorderedRoundedRect(g, btnX, btnY, btnW, BTN_HEIGHT, 4,
                DarkUiPalette.black(), fillColor, 1);
        int lineH = Minecraft.getInstance().font.lineHeight;
        TextRenderer.drawCentered(g, Minecraft.getInstance().font, text,
                btnX + btnW / 2, btnY + (BTN_HEIGHT - lineH) / 2, textColor);
    }

    private void renderPriorityBox(GuiGraphics g, int boxX, int centerY,
                                    String priorityStr, boolean editing, boolean dimmed, int boxW, int rowIndex,
                                    float hoverT) {
        int boxY = centerY - EDIT_INPUT_H / 2;

        SdfRenderer.drawInputBox(g, boxX, boxY, boxW, EDIT_INPUT_H, editController.getAnimValue(rowIndex), hoverT, 4);

        Minecraft mc = Minecraft.getInstance();
        int fontColor = ThemeManager.getTextColor();
        int textColor = editing ? fontColor : (dimmed ? (fontColor & 0xFFFFFF) | 0x60000000 : fontColor);
        int textX = boxX + 4;
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
