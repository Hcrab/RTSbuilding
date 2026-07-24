package com.rtsbuilding.rtsbuilding.client.presentation.plugin;

import com.mojang.math.Axis;
import com.rtsbuilding.rtsbuilding.client.domain.state.LinkedStorageEntry;
import com.rtsbuilding.rtsbuilding.client.infrastructure.di.CompositionRoot;
import com.rtsbuilding.rtsbuilding.client.infrastructure.module.storage.StorageModule;
import com.rtsbuilding.rtsbuilding.client.network.RtsClientPacketGateway;
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
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.*;


public class ContainerBinding {

    private final OverlayContext context;

    public ContainerBinding(OverlayContext context) {
        this.context = context;
    }

    private int getX() { return context.getX(); }
    private int getY() { return context.getY(); }
    private int getWidth() { return context.getWidth(); }
    private int getHeight() { return context.getHeight(); }
    private int getLastMouseX() { return context.getLastMouseX(); }
    private int getLastMouseY() { return context.getLastMouseY(); }
    private boolean contains(int px, int py) { return context.contains(px, py); }
    private boolean isDividerDragging() { return context.isDividerDragging(); }

    private static final int ROW_H = 20;
    private static final int ICON_SIZE = 12;
    private static final int PRIORITY_W = 14;
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

    

    private final ScrollBar scrollBar = new ScrollBar();
    private final List<RowLayout> rowLayouts = new ArrayList<>();

    
    private final PriorityEditController editController = new PriorityEditController();
    
    private final EntryAnimationController animController = new EntryAnimationController();

    

    
    private record ButtonBar(int unbindW, int toggleW, int locateW, int btnAreaRight) {
        
        int toggleX()  { return btnAreaRight - toggleW; }
        
        int unbindX()  { return toggleX() - BTN_GAP - unbindW; }
        
        int locateX()  { return unbindX() - BTN_GAP - locateW; }

        
        ButtonBar(Minecraft mc, boolean scrollBarVisible, int parentX, int parentW) {
            this(
                    mc.font.width("解绑") + BTN_PAD_H * 2,
                    Math.max(mc.font.width("双向"), mc.font.width("仅提取")) + BTN_PAD_H * 2,
                    Math.max(mc.font.width("开启位置"), mc.font.width("关闭显示")) + BTN_PAD_H * 2,
                    parentX + LEFT_PAD + (parentW - LEFT_PAD - SCROLLBAR_W - RIGHT_MARGIN)
                            - (scrollBarVisible ? 2 : 0) - 1
            );
        }
    }

    
    private static final class RowLayout {
        
        int y;
        
        int arrowBtnX;
        
        int priorityX;
        
        int priorityW;
        
        int unbindX;
        
        int toggleX;
        
        int unbindW;
        
        int toggleW;
        
        int locateBtnX;
        
        int locateBtnW;
        
        int originalIndex;
    }

    

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

        
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        int mouseX = getLastMouseX(), mouseY = getLastMouseY();
        Minecraft mc = Minecraft.getInstance();
        int visibleH = h - TOP_PAD * 2;

        scrollBar.setContent(count * ROW_H, visibleH);
        int scroll = scrollBar.getScroll();

        
        renderBackgroundRows(g, x, y, w, count, scroll, visibleH, mouseX, mouseY);

        
        List<Integer> sortedIndices = buildSortedIndices(count, priorities);

        
        ButtonBar btnBar = new ButtonBar(mc, scrollBar.isVisible(), x, w);
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
            boolean hovered = !isDividerDragging() && i < count
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
                                  ButtonBar btnBar, int fontColor, Minecraft mc,
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

        
        LinkedStorageEntry entry = entries.get(origIdx);
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
                                      ButtonBar btnBar, int rowCenterY, int mouseX, int mouseY,
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
        int locateBtnW = mc().font.width(locateText) + BTN_PAD_H * 2;
        int locateBtnX = btnBar.locateX();
        rl.locateBtnX = locateBtnX;
        rl.locateBtnW = locateBtnW;
        boolean hoverLocate = !isDividerDragging()
                && inRect(mouseX, mouseY, locateBtnX, btnY, locateBtnW, BTN_HEIGHT);
        drawTextButton(g, locateBtnX, btnY, locateText,
                hoverLocate ? LOCATE_BTN_HOVER_COLOR : LOCATE_BTN_COLOR,
                themedBtnNormal, themedBtnHover);

        
        String unbindText = "解绑";
        int unbindBtnW = mc().font.width(unbindText) + BTN_PAD_H * 2;
        int unbindX = btnBar.unbindX();
        rl.unbindX = unbindX;
        rl.unbindW = unbindBtnW;
        boolean hoverUnbind = !isDividerDragging()
                && inRect(mouseX, mouseY, unbindX, btnY, unbindBtnW, BTN_HEIGHT);
        drawTextButton(g, unbindX, btnY, unbindText,
                hoverUnbind ? UNBIND_HOVER_COLOR : UNBIND_COLOR,
                themedBtnNormal, themedBtnHover);

        
        String toggleText = entry.isExtractOnly() ? "仅提取" : "双向";
        int toggleBtnW = mc().font.width(toggleText) + BTN_PAD_H * 2;
        int toggleX = btnBar.toggleX();
        rl.toggleX = toggleX;
        rl.toggleW = toggleBtnW;
        boolean hoverToggle = !isDividerDragging()
                && inRect(mouseX, mouseY, toggleX, btnY, toggleBtnW, BTN_HEIGHT);
        int toggleColor = entry.isExtractOnly() ? MODE_EXTRACT_COLOR : MODE_BI_COLOR;
        drawTextButton(g, toggleX, btnY, toggleText,
                hoverToggle ? BTN_HOVER_FG : toggleColor,
                themedBtnNormal, themedBtnHover);
    }

    
    private void drawTextButton(GuiGraphics g, int btnX, int btnY, String text, int textColor,
                                NineSliceRegion themedBtnNormal, NineSliceRegion themedBtnHover) {
        int btnW = mc().font.width(text) + BTN_PAD_H * 2;
        NineSliceRegion slice = textColor == UNBIND_HOVER_COLOR
                || textColor == LOCATE_BTN_HOVER_COLOR
                || textColor == BTN_HOVER_FG
                ? themedBtnHover : themedBtnNormal;
        SpriteRenderer.drawNineSlice(g, slice, btnX, btnY, btnW, BTN_HEIGHT);
        int lineH = mc().font.lineHeight;
        TextRenderer.drawCentered(g, mc().font, text,
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

        Minecraft mc = mc();
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
        int lineH = mc().font.lineHeight;
        TextRenderer.drawCentered(g, mc().font, hint,
                getX() + getWidth() / 2, getY() + (getHeight() - lineH) / 2, textColor);
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
        int barX = x + getWidth() - SCROLLBAR_W - RIGHT_MARGIN;
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

    
    private static Minecraft mc() {
        return Minecraft.getInstance();
    }

    

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        int mx = (int) mouseX;
        int my = (int) mouseY;

        
        if (editController.isEditing()
                && !editController.isClickOnEditBox(mx, my, getX(), getY(), scrollBar.getScroll())) {
            editController.tryCommit();
        }

        StorageModule sm = CompositionRoot.get().module(StorageModule.class);
        if (sm == null) return false;

        
        int barX = getX() + getWidth() - SCROLLBAR_W - RIGHT_MARGIN;
        if (scrollBar.handleClick(mouseX, mouseY, barX,
                getY() + TOP_PAD + 6, getHeight() - TOP_PAD * 2 - 12)) {
            return true;
        }

        
        return handleRowClick(mx, my, sm);
    }

    
    private boolean handleRowClick(int mx, int my, StorageModule sm) {
        var entries = sm.getLinkedStorageEntries();
        var priorities = sm.getLinkedPriorities();
        int count = Math.min(entries.size(), Math.min(rowLayouts.size(), priorities.size()));

        for (int i = 0; i < count; i++) {
            RowLayout rl = rowLayouts.get(i);
            if (rl == null) continue;
            if (my < rl.y || my >= rl.y + ROW_H - 1) continue;

            int origIdx = rl.originalIndex;
            LinkedStorageEntry entry = entries.get(origIdx);

            
            if (inRect(mx, my, rl.arrowBtnX, rl.y, ARROW_BTN_SIZE, ROW_H)) {
                handleArrowSwap(i, count, entries, priorities, rowLayouts);
                return true;
            }

            
            if (inRect(mx, my, rl.priorityX, rl.y, rl.priorityW, ROW_H)) {
                if (!editController.isEditing() || editController.getEditingIndex() != i) {
                    editController.beginEdit(i, priorities.get(origIdx));
                }
                return true;
            }

            
            if (inRect(mx, my, rl.locateBtnX, rl.y, rl.locateBtnW, ROW_H)) {
                sm.toggleLocationDisplay(entry.pos());
                return true;
            }

            
            if (inRect(mx, my, rl.unbindX, rl.y, rl.unbindW, ROW_H)) {
                RtsClientPacketGateway.sendUnlinkStorage(entry.pos());
                return true;
            }

            
            if (inRect(mx, my, rl.toggleX, rl.y, rl.toggleW, ROW_H)) {
                boolean nextExtractOnly = !entry.isExtractOnly();
                RtsClientPacketGateway.sendUpdateLinkedStorage(
                        entry.pos(), nextExtractOnly, priorities.get(origIdx));
                return true;
            }
        }
        return false;
    }

    
    private void handleArrowSwap(int sortedIdx, int count, List<LinkedStorageEntry> entries,
                                  List<Integer> priorities, List<RowLayout> layouts) {
        int targetIdx = (sortedIdx == 0) ? sortedIdx + 1 : sortedIdx - 1;
        if (targetIdx < 0 || targetIdx >= count) return;

        RowLayout currentRl = layouts.get(sortedIdx);
        RowLayout targetRl = layouts.get(targetIdx);

        int currentPriority = priorities.get(currentRl.originalIndex);
        int targetPriority = priorities.get(targetRl.originalIndex);
        LinkedStorageEntry currentEntry = entries.get(currentRl.originalIndex);
        LinkedStorageEntry targetEntry = entries.get(targetRl.originalIndex);

        if (currentPriority == targetPriority) {
            
            int newPriority = (sortedIdx == 0)
                    ? Math.min(100, targetPriority + 1)
                    : Math.max(0, targetPriority - 1);
            RtsClientPacketGateway.sendUpdateLinkedStorage(
                    currentEntry.pos(), currentEntry.isExtractOnly(), newPriority);
        } else {
            RtsClientPacketGateway.sendUpdateLinkedStorage(
                    currentEntry.pos(), currentEntry.isExtractOnly(), targetPriority);
            RtsClientPacketGateway.sendUpdateLinkedStorage(
                    targetEntry.pos(), targetEntry.isExtractOnly(), currentPriority);
        }
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (editController.isEditing()) {
            editController.tryCommit();
        }
        return scrollBar.handleScroll(scrollY);
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        if (scrollBar.isDragging()) {
            scrollBar.endDrag();
            return true;
        }
        return false;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button != 0) return false;
        if (scrollBar.isDragging()) {
            return scrollBar.handleDrag(mouseY, getY() + TOP_PAD, getHeight() - TOP_PAD * 2);
        }
        return false;
    }

    

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return editController.handleKeyPressed(keyCode);
    }

    public boolean charTyped(char codePoint, int modifiers) {
        return editController.handleCharTyped(codePoint);
    }

    

    

    
    private final class PriorityEditController {

        
        private int editingIndex = -1;
        
        private final StringBuilder editBuffer = new StringBuilder();
        
        private long editStartTime;
        
        private boolean isEditing;

        
        private final FloatAnimation priorityBoxAnim = FloatAnimation.builder()
                .from(0f).to(0f)
                .duration(100L)
                .easing(EasingFunctions.EASE_OUT_QUAD)
                .startFromCurrent(true)
                .build();
        
        private int lastAnimRow = -1;
        
        private float lastAnimBaseW;

        

        
        void beginEdit(int rowIndex, int priority) {
            editingIndex = rowIndex;
            isEditing = true;
            editBuffer.setLength(0);
            editBuffer.append(priority);
            editStartTime = System.currentTimeMillis();
            
            lastAnimRow = rowIndex;
            lastAnimBaseW = mc().font.width(String.valueOf(priority)) + PRIORITY_PAD_H * 2;
            priorityBoxAnim.start(1f); 
        }

        
        void tryCommit() {
            if (!isEditing) return;
            String text = editBuffer.toString().trim();
            if (!text.isEmpty()) {
                try {
                    int newPriority = Mth.clamp(Integer.parseInt(text), 0, 100);
                    StorageModule sm = CompositionRoot.get().module(StorageModule.class);
                    if (sm != null && editingIndex >= 0 && editingIndex < rowLayouts.size()) {
                        var entries = sm.getLinkedStorageEntries();
                        RowLayout rl = rowLayouts.get(editingIndex);
                        if (rl.originalIndex >= 0 && rl.originalIndex < entries.size()) {
                            LinkedStorageEntry entry = entries.get(rl.originalIndex);
                            RtsClientPacketGateway.sendUpdateLinkedStorage(
                                    entry.pos(), entry.isExtractOnly(), newPriority);
                        }
                    }
                } catch (NumberFormatException ignored) {}
            }
            doCancel();
        }

        
        void doCancel() {
            if (editingIndex >= 0 && editingIndex < rowLayouts.size()) {
                lastAnimRow = editingIndex;
                lastAnimBaseW = rowLayouts.get(editingIndex).priorityW;
            }
            isEditing = false;
            editingIndex = -1;
            editBuffer.setLength(0);
            priorityBoxAnim.start(0f); 
        }

        
        void tick(int count) {
            priorityBoxAnim.tick();
            if (!priorityBoxAnim.isRunning() && !isEditing) {
                lastAnimRow = -1;
            }
            if (isEditing && editingIndex >= count) {
                doCancel();
            }
        }

        

        boolean isEditing() { return isEditing; }

        
        boolean isEditingRow(int rowIndex) { return isEditing && rowIndex == editingIndex; }

        int getEditingIndex() { return editingIndex; }

        String getBufferText() { return editBuffer.toString(); }

        int getBufferLength() { return editBuffer.length(); }

        long getStartTime() { return editStartTime; }

        float getAnimValue() { return priorityBoxAnim.getValue(); }

        

        
        float computePriorityBoxWidth(int normalW, boolean isEditingRow, int rowIndex) {
            boolean applyAnim = isEditingRow || (rowIndex == lastAnimRow && lastAnimRow >= 0);
            if (!applyAnim) return normalW;
            float baseW = isEditingRow ? normalW : lastAnimBaseW;
            return baseW + (EDIT_INPUT_W - baseW) * priorityBoxAnim.getValue();
        }

        

        
        boolean isClickOnEditBox(int mx, int my, int parentX, int parentY, int scroll) {
            int editBoxX = parentX + LEFT_PAD + ARROW_BTN_SIZE + PRIORITY_ICON_GAP;
            int editBoxY = parentY + TOP_PAD + editingIndex * ROW_H - scroll + ROW_H / 2;
            int boxTop = editBoxY - EDIT_INPUT_H / 2;
            return inRect(mx, my, editBoxX, boxTop, EDIT_INPUT_W, EDIT_INPUT_H);
        }

        

        
        boolean handleKeyPressed(int keyCode) {
            if (!isEditing) return false;

            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                tryCommit();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                doCancel();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (editBuffer.length() > 0) {
                    editBuffer.deleteCharAt(editBuffer.length() - 1);
                }
                return true;
            }
            
            if (keyCode == GLFW.GLFW_KEY_TAB) {
                return true;
            }
            return false;
        }

        
        boolean handleCharTyped(char codePoint) {
            if (!isEditing) return false;
            if (codePoint >= '0' && codePoint <= '9') {
                editBuffer.append(codePoint);
                return true;
            }
            return false;
        }
    }

    

    
    private static final class EntryAnimationController {

        
        private final Map<Integer, Float> entryContentY = new HashMap<>();

        
        private final Map<Integer, Float> barHoverProgress = new HashMap<>();
        
        private final Map<Integer, Boolean> barHoverState = new HashMap<>();

        
        private static final float ENTRY_SMOOTH_FACTOR = 0.15f;
        
        private static final float BAR_HOVER_SMOOTH_FACTOR = 0.28f;
        
        private static final float EPSILON = 0.001f;

        
        void tick(int count) {
            if (entryContentY.size() > count) {
                entryContentY.keySet().removeIf(key -> key >= count);
            }
        }

        
        float updateEntryAnimY(int origIdx, int targetBaseY) {
            Float animY = entryContentY.get(origIdx);
            if (animY == null) {
                animY = (float) targetBaseY;
            } else {
                animY += (targetBaseY - animY) * ENTRY_SMOOTH_FACTOR;
                if (Math.abs(animY - targetBaseY) < 0.5f) {
                    animY = (float) targetBaseY;
                }
            }
            entryContentY.put(origIdx, animY);
            return animY;
        }

        
        float tickBarHover(int barIndex, boolean shouldHover) {
            Float progress = barHoverProgress.get(barIndex);
            if (progress == null) {
                float val = shouldHover ? 1f : 0f;
                barHoverProgress.put(barIndex, val);
                barHoverState.put(barIndex, shouldHover);
                return val;
            }
            Boolean prevState = barHoverState.get(barIndex);
            if (prevState == null || prevState != shouldHover) {
                barHoverState.put(barIndex, shouldHover);
            }
            float target = shouldHover ? 1f : 0f;
            progress += (target - progress) * BAR_HOVER_SMOOTH_FACTOR;
            if (Math.abs(progress - target) < EPSILON) {
                progress = target;
            }
            barHoverProgress.put(barIndex, progress);
            return progress;
        }
    }
}
