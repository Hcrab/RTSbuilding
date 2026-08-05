package com.rtsbuilding.rtsbuilding.client.presentation.plugin.grid;

import com.mojang.blaze3d.systems.RenderSystem;
import com.rtsbuilding.rtsbuilding.client.domain.state.FluidEntry;
import com.rtsbuilding.rtsbuilding.client.domain.state.RecentEntry;
import com.rtsbuilding.rtsbuilding.client.domain.state.StorageEntry;
import com.rtsbuilding.rtsbuilding.client.infrastructure.module.storage.StorageModule;
import com.rtsbuilding.rtsbuilding.client.kernel.RtsClientKernel;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.base.component.ScrollBar;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.base.overlay.OverlayContext;
import com.rtsbuilding.rtsbuilding.client.presentation.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.util.animate.AnimFloat;
import com.rtsbuilding.rtsbuilding.client.util.animate.ColorAnimation;
import com.rtsbuilding.rtsbuilding.client.util.animate.Easing;
import com.rtsbuilding.rtsbuilding.client.util.render.DarkUiPalette;
import com.rtsbuilding.rtsbuilding.client.util.render.SdfRenderer;
import com.rtsbuilding.rtsbuilding.client.util.render.SpriteRenderer;
import com.rtsbuilding.rtsbuilding.client.util.render.TextRenderer;
import com.rtsbuilding.rtsbuilding.client.util.render.model.SpriteRegion;
import com.rtsbuilding.rtsbuilding.client.util.render.model.TextureInfo;
import com.rtsbuilding.rtsbuilding.client.util.state.TooltipController;
import com.rtsbuilding.rtsbuilding.client.util.theme.ThemeManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

import java.util.*;

import static com.rtsbuilding.rtsbuilding.client.presentation.plugin.grid.GridSlotRenderer.SLOT_SIZE;

public final class GridRenderer {

    private final OverlayContext ctx;
    private final ScrollBar scrollBar;
    private final ScrollBar recentScrollBar;
    private final GridState state;
    private final TypeFilterPopup typeFilterPopup;
    private final ContainerModePopup containerModePopup;

    private static final int SLOT_GAP = 0;
    private static final int RECENT_MAIN_GAP = 9;

    private static final int PAD_LEFT = 92;
    private static final int PAD_TOP = 2;

    private static final int GRID_TOP_OFFSET = 20;

    private static final int SCROLLBAR_W = 7;

    private static final int RIGHT_GAP = 18;

    private static final int BUTTON_SIZE = 18;
    private static final int BUTTON_SPACING = 1;



    private static final ResourceLocation NOTHING_TEXTURE = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/down/nothing.png");
    private static final int NOTHING_TEX_W = 32;
    private static final int NOTHING_TEX_H = 16;
    private static final TextureInfo NOTHING_TEX_INFO = new TextureInfo(
            NOTHING_TEXTURE, NOTHING_TEX_W, NOTHING_TEX_H,
            TextureInfo.ThemeLayout.HORIZONTAL_PAIR,
            TextureInfo.FilterMode.PIXEL);
    private static final SpriteRegion NOTHING_SPRITE = new SpriteRegion(
            NOTHING_TEX_INFO, 0, 0, NOTHING_TEX_W / 2, NOTHING_TEX_H);



    private static final ResourceLocation SORT_ICON_TEXTURE = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/down/sort.png");
    private static final int SORT_ICON_TEX_W = 32;
    private static final int SORT_ICON_TEX_H = 48;
    private static final int SORT_ICON_TYPE_H = 16;
    private static final TextureInfo SORT_ICON_TEX_INFO = new TextureInfo(
            SORT_ICON_TEXTURE, SORT_ICON_TEX_W, SORT_ICON_TEX_H,
            TextureInfo.ThemeLayout.HORIZONTAL_PAIR,
            TextureInfo.FilterMode.PIXEL);

    private static final SpriteRegion SORT_NAME_ICON = new SpriteRegion(
            SORT_ICON_TEX_INFO, 0, 0, SORT_ICON_TEX_W / 2, SORT_ICON_TYPE_H);

    private static final SpriteRegion SORT_COUNT_ICON = new SpriteRegion(
            SORT_ICON_TEX_INFO, 0, SORT_ICON_TYPE_H, SORT_ICON_TEX_W / 2, SORT_ICON_TYPE_H);

    private static final SpriteRegion SORT_MOD_ICON = new SpriteRegion(
            SORT_ICON_TEX_INFO, 0, SORT_ICON_TYPE_H * 2, SORT_ICON_TEX_W / 2, SORT_ICON_TYPE_H);

    private static final ResourceLocation ORDER_BTN_TEXTURE = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/down/sort_order.png");
    private static final int ORDER_BTN_TEX_W = 32;
    private static final int ORDER_BTN_TEX_H = 32;
    private static final int ORDER_BTN_TYPE_H = 16;
    private static final TextureInfo ORDER_BTN_TEX_INFO = new TextureInfo(
            ORDER_BTN_TEXTURE, ORDER_BTN_TEX_W, ORDER_BTN_TEX_H,
            TextureInfo.ThemeLayout.HORIZONTAL_PAIR,
            TextureInfo.FilterMode.PIXEL);

    private static final SpriteRegion ORDER_ASC_ICON = new SpriteRegion(
            ORDER_BTN_TEX_INFO, 0, 0, ORDER_BTN_TEX_W / 2, ORDER_BTN_TYPE_H);

    private static final SpriteRegion ORDER_DESC_ICON = new SpriteRegion(
            ORDER_BTN_TEX_INFO, 0, ORDER_BTN_TYPE_H, ORDER_BTN_TEX_W / 2, ORDER_BTN_TYPE_H);

    private static final ResourceLocation TYPE_FILTER_TEXTURE = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/down/type.png");
    private static final int TYPE_FILTER_TEX_W = 32;
    private static final int TYPE_FILTER_TEX_H = 16;
    private static final int TYPE_FILTER_TYPE_H = 16;
    private static final TextureInfo TYPE_FILTER_TEX_INFO = new TextureInfo(
            TYPE_FILTER_TEXTURE, TYPE_FILTER_TEX_W, TYPE_FILTER_TEX_H,
            TextureInfo.ThemeLayout.NONE,
            TextureInfo.FilterMode.PIXEL);

    private static final SpriteRegion TYPE_ITEM_ICON = new SpriteRegion(
            TYPE_FILTER_TEX_INFO, 0, 0, TYPE_FILTER_TEX_W / 2, TYPE_FILTER_TYPE_H);

    private static final SpriteRegion TYPE_FLUID_ICON = new SpriteRegion(
            TYPE_FILTER_TEX_INFO, TYPE_FILTER_TEX_W / 2, 0, TYPE_FILTER_TEX_W / 2, TYPE_FILTER_TYPE_H);

    private static final ResourceLocation CONTAINER_TEXTURE = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/down/container.png");
    private static final int CONTAINER_TEX_W = 32;
    private static final int CONTAINER_TEX_H = 16;
    private static final int CONTAINER_TYPE_H = 16;
    private static final TextureInfo CONTAINER_TEX_INFO = new TextureInfo(
            CONTAINER_TEXTURE, CONTAINER_TEX_W, CONTAINER_TEX_H,
            TextureInfo.ThemeLayout.NONE,
            TextureInfo.FilterMode.PIXEL);

    private static final SpriteRegion CONTAINER_EXTRACT_ICON = new SpriteRegion(
            CONTAINER_TEX_INFO, 0, 0, CONTAINER_TEX_W / 2, CONTAINER_TYPE_H);

    private static final SpriteRegion CONTAINER_BIDIR_ICON = new SpriteRegion(
            CONTAINER_TEX_INFO, CONTAINER_TEX_W / 2, 0, CONTAINER_TEX_W / 2, CONTAINER_TYPE_H);

    private static final int HINT_COLOR = 0x60_FFFFFF;



    private static final int SEARCH_INPUT_H = 18;
    private static final int SEARCH_INPUT_PAD = 4;
    private static final int SEARCH_BTN_W = 18;

    private static final long CURSOR_BLINK_MS = 600;

    private final TooltipController currentItemTooltip = TooltipController.builder().direction(TooltipController.Direction.ABOVE).build();
    private final TooltipController sortButtonTooltip = TooltipController.builder().direction(TooltipController.Direction.ABOVE).build();
    private final TooltipController orderButtonTooltip = TooltipController.builder().direction(TooltipController.Direction.ABOVE).build();
    private final TooltipController typeFilterButtonTooltip = TooltipController.builder().direction(TooltipController.Direction.ABOVE).build();
    private final TooltipController containerButtonTooltip = TooltipController.builder().direction(TooltipController.Direction.ABOVE).build();
    private final TooltipController recentSortButtonTooltip = TooltipController.builder().direction(TooltipController.Direction.ABOVE).build();

    private final AnimFloat currentItemHover = AnimFloat.hover();
    private final AnimFloat sortBtnHover = AnimFloat.hover();
    private final AnimFloat orderBtnHover = AnimFloat.hover();
    private final AnimFloat typeFilterBtnHover = AnimFloat.hover();
    private final AnimFloat containerBtnHover = AnimFloat.hover();
    private final AnimFloat recentSortHover = AnimFloat.hover();
    private final AnimFloat searchFocusAnim = AnimFloat.of(0f, 100L, Easing.EASE_OUT_QUAD);
    private final AnimFloat recentSearchFocusAnim = AnimFloat.of(0f, 100L, Easing.EASE_OUT_QUAD);
    private final AnimFloat searchHoverAnim = AnimFloat.hover();
    private final AnimFloat recentSearchHoverAnim = AnimFloat.hover();
    private boolean prevSearchFocused;
    private boolean prevRecentSearchFocused;
    private final AnimFloat slotHoverAnim = AnimFloat.hover();
    private final AnimFloat slotSelectedAnim = AnimFloat.hover();
    private int lastHoveredMain = -1;
    private int lastHoveredRecent = -1;
    private int lastSelectedMain = -1;
    private int lastSelectedRecent = -1;

    public GridRenderer(OverlayContext ctx, ScrollBar scrollBar, ScrollBar recentScrollBar,
                 GridState state, TypeFilterPopup typeFilterPopup, ContainerModePopup containerModePopup) {
        this.ctx = ctx;
        this.scrollBar = scrollBar;
        this.recentScrollBar = recentScrollBar;
        this.state = state;
        this.typeFilterPopup = typeFilterPopup;
        this.containerModePopup = containerModePopup;
    }

    public void renderContent(GuiGraphics g) {
        updateScrollAnimation();

        StorageModule sm = RtsClientKernel.get().module(StorageModule.class);
        if (sm == null) return;

        checkAndRebuildIfDirty(sm);
        boolean hasStorage = sm.isLinked();
        if (state.slotEntries.isEmpty()) {
            if (!hasStorage && state.searchBuffer.length() == 0) {
                renderEmptyHint(g);
                return;
            }
        }

        int x = ctx.getX(), y = ctx.getY(), w = ctx.getWidth(), h = ctx.getHeight();

        int slotThemeOffset = SpriteRenderer.getThemeOffset(GridSlotRenderer.SLOT_NORMAL);
        Minecraft mc = Minecraft.getInstance();

        int mouseX = ctx.getLastMouseX();
        int mouseY = ctx.getLastMouseY();
        int itemDisplayX = x + PAD_LEFT;
        int itemDisplayY = y + PAD_TOP + 1;
        int itemDisplaySize = BUTTON_SIZE;
        boolean isHoveringOverCurrentSelection = mouseX >= itemDisplayX && mouseX < itemDisplayX + itemDisplaySize
                && mouseY >= itemDisplayY && mouseY < itemDisplayY + itemDisplaySize;

        currentItemTooltip.update(isHoveringOverCurrentSelection, false);

        int itemFill = ColorAnimation.lerpRGB(DarkUiPalette.bg(), DarkUiPalette.accent(), currentItemHover.track(isHoveringOverCurrentSelection));
        SdfRenderer.drawBorderedRoundedRect(g, itemDisplayX, itemDisplayY, itemDisplaySize, itemDisplaySize, 4,
                DarkUiPalette.black(), itemFill, 1);

        if (!state.currentSelectedItem.isEmpty()) {
            RenderSystem.disableDepthTest();
            var pose = g.pose();
            pose.pushPose();
            pose.translate(itemDisplayX + 1, itemDisplayY + 1, 0);
            g.renderItem(state.currentSelectedItem, 0, 0);
            pose.popPose();
            RenderSystem.enableDepthTest();
            g.renderItemDecorations(mc.font, state.currentSelectedItem, itemDisplayX + 1, itemDisplayY + 1);
            RenderSystem.disableDepthTest();
        } else {
            RenderSystem.disableDepthTest();
            int iconWidth = NOTHING_TEX_W / 2;
            int iconHeight = NOTHING_TEX_H;
            int iconOffsetX = (itemDisplaySize - iconWidth) / 2;
            int iconOffsetY = (itemDisplaySize - iconHeight) / 2;
            SpriteRenderer.drawSprite(g, NOTHING_SPRITE, slotThemeOffset,
                    itemDisplayX + iconOffsetX, itemDisplayY + iconOffsetY, iconWidth, iconHeight);
        }

        int sortBtnX = calculateSortButtonX(x);
        int sortBtnY = y + PAD_TOP + 1;
        boolean isHoveringOverSortBtn = mouseX >= sortBtnX && mouseX < sortBtnX + BUTTON_SIZE
                && mouseY >= sortBtnY && mouseY < sortBtnY + BUTTON_SIZE;
        sortButtonTooltip.update(isHoveringOverSortBtn, false);

        int sortFill = ColorAnimation.lerpRGB(DarkUiPalette.bg(), DarkUiPalette.accent(), sortBtnHover.track(isHoveringOverSortBtn));
        SdfRenderer.drawBorderedRoundedRect(g, sortBtnX, sortBtnY, BUTTON_SIZE, BUTTON_SIZE, 4,
                DarkUiPalette.black(), sortFill, 1);
        SpriteRenderer.drawSprite(g, switch (state.currentSortType) {
            case NAME -> SORT_NAME_ICON;
            case COUNT -> SORT_COUNT_ICON;
            case MOD -> SORT_MOD_ICON;
        }, slotThemeOffset, sortBtnX, sortBtnY, BUTTON_SIZE, BUTTON_SIZE);

        int orderBtnX = calculateOrderButtonX(x);
        int orderBtnY = y + PAD_TOP + 1;
        boolean isHoveringOverOrderBtn = mouseX >= orderBtnX && mouseX < orderBtnX + BUTTON_SIZE
                && mouseY >= orderBtnY && mouseY < orderBtnY + BUTTON_SIZE;
        orderButtonTooltip.update(isHoveringOverOrderBtn, false);

        int orderFill = ColorAnimation.lerpRGB(DarkUiPalette.bg(), DarkUiPalette.accent(), orderBtnHover.track(isHoveringOverOrderBtn));
        SdfRenderer.drawBorderedRoundedRect(g, orderBtnX, orderBtnY, BUTTON_SIZE, BUTTON_SIZE, 4,
                DarkUiPalette.black(), orderFill, 1);
        SpriteRenderer.drawSprite(g, state.reverseSortOrder ? ORDER_DESC_ICON : ORDER_ASC_ICON,
                slotThemeOffset, orderBtnX, orderBtnY, BUTTON_SIZE, BUTTON_SIZE);

        int typeFilterBtnX = calculateTypeFilterButtonX(x);
        int typeFilterBtnY = y + PAD_TOP + 1;
        boolean isHoveringOverTypeFilterBtn = mouseX >= typeFilterBtnX && mouseX < typeFilterBtnX + BUTTON_SIZE
                && mouseY >= typeFilterBtnY && mouseY < typeFilterBtnY + BUTTON_SIZE;
        typeFilterButtonTooltip.update(isHoveringOverTypeFilterBtn, false);

        int typeFilterFill = ColorAnimation.lerpRGB(DarkUiPalette.bg(), DarkUiPalette.accent(), typeFilterBtnHover.track(isHoveringOverTypeFilterBtn));
        SdfRenderer.drawBorderedRoundedRect(g, typeFilterBtnX, typeFilterBtnY, BUTTON_SIZE, BUTTON_SIZE, 4,
                DarkUiPalette.black(), typeFilterFill, 1);
        SpriteRenderer.drawSprite(g, TYPE_ITEM_ICON,
                0, typeFilterBtnX, typeFilterBtnY, BUTTON_SIZE, BUTTON_SIZE);

        int containerBtnX = calculateContainerButtonX(x);
        int containerBtnY = y + PAD_TOP + 1;
        boolean isHoveringOverContainerBtn = mouseX >= containerBtnX && mouseX < containerBtnX + BUTTON_SIZE
                && mouseY >= containerBtnY && mouseY < containerBtnY + BUTTON_SIZE;
        containerButtonTooltip.update(isHoveringOverContainerBtn, false);

        int containerFill = ColorAnimation.lerpRGB(DarkUiPalette.bg(), DarkUiPalette.accent(), containerBtnHover.track(isHoveringOverContainerBtn));
        SdfRenderer.drawBorderedRoundedRect(g, containerBtnX, containerBtnY, BUTTON_SIZE, BUTTON_SIZE, 4,
                DarkUiPalette.black(), containerFill, 1);
        SpriteRenderer.drawSprite(g, CONTAINER_EXTRACT_ICON,
                0, containerBtnX, containerBtnY, BUTTON_SIZE, BUTTON_SIZE);

        int recentSortBtnX = state.recentGridOriginX;
        int recentSortBtnY = y + PAD_TOP + 1;
        boolean isHoveringRecentSort = mouseX >= recentSortBtnX && mouseX < recentSortBtnX + BUTTON_SIZE
                && mouseY >= recentSortBtnY && mouseY < recentSortBtnY + BUTTON_SIZE;
        recentSortButtonTooltip.update(isHoveringRecentSort, false);
        int recentSortFill = ColorAnimation.lerpRGB(DarkUiPalette.bg(), DarkUiPalette.accent(), recentSortHover.track(isHoveringRecentSort));
        SdfRenderer.drawBorderedRoundedRect(g, recentSortBtnX, recentSortBtnY, BUTTON_SIZE, BUTTON_SIZE, 4,
                DarkUiPalette.black(), recentSortFill, 1);
        SpriteRenderer.drawSprite(g, state.recentSortAscending ? ORDER_ASC_ICON : ORDER_DESC_ICON,
                slotThemeOffset, recentSortBtnX, recentSortBtnY, BUTTON_SIZE, BUTTON_SIZE);

        int recentSearchX = state.recentGridOriginX + BUTTON_SIZE + BUTTON_SPACING;
        int recentSearchY = y + PAD_TOP + 1;
        int recentSearchW = (x + 3 + state.recentCols * SLOT_SIZE) - recentSearchX;
        if (recentSearchW > SEARCH_INPUT_H) {
            if (state.recentSearchFocused != prevRecentSearchFocused) {
                recentSearchFocusAnim.target(state.recentSearchFocused ? 1f : 0f);
                prevRecentSearchFocused = state.recentSearchFocused;
            }
            boolean recentSearchHovered = !state.recentSearchFocused
                    && mouseX >= recentSearchX && mouseX < recentSearchX + recentSearchW
                    && mouseY >= recentSearchY && mouseY < recentSearchY + SEARCH_INPUT_H;
            SdfRenderer.drawInputBox(g, recentSearchX, recentSearchY, recentSearchW, SEARCH_INPUT_H,
                    recentSearchFocusAnim.get(), recentSearchHoverAnim.track(recentSearchHovered), 4);

            Font searchFont = mc.font;
            String searchText = state.recentSearchBuffer.toString();
            int textColor = ThemeManager.getTextColor();
            int textX = recentSearchX + SEARCH_INPUT_PAD;
            int textY = recentSearchY + (SEARCH_INPUT_H - searchFont.lineHeight) / 2;
            int contentAreaW = recentSearchW - SEARCH_INPUT_PAD * 2;

            if (state.recentSearchFocused) {
                String displayText = TextRenderer.trimToWidth(searchFont, searchText, contentAreaW);
                g.drawString(searchFont, displayText, textX, textY, textColor, false);

                if ((System.currentTimeMillis() / CURSOR_BLINK_MS) % 2 == 0) {
                    int cursorX = textX + searchFont.width(displayText);
                    g.fill(cursorX, textY, cursorX + 1, textY + searchFont.lineHeight, 0xFFFFFFFF);
                }
            } else {
                String placeholder = searchText.isEmpty()
                        ? Component.translatable("tooltip.rtsbuilding.rightdown.search_placeholder").getString()
                        : searchText;
                String displayText = TextRenderer.trimToWidth(searchFont, placeholder, contentAreaW);
                int placeholderColor = searchText.isEmpty() ? (textColor & 0xFFFFFF) | 0x60000000 : textColor;
                g.drawString(searchFont, displayText, textX, textY, placeholderColor, false);
            }
        }

        state.rows = Math.max(1, (h - PAD_TOP - GRID_TOP_OFFSET) / (SLOT_SIZE + SLOT_GAP) + 2);

        int mainCols = Math.max(1, (w - PAD_LEFT - RIGHT_GAP) / (SLOT_SIZE + SLOT_GAP));
        int calcMainGridW = mainCols * (SLOT_SIZE + SLOT_GAP) - SLOT_GAP;
        state.cols = mainCols;
        state.recentCols = 3;
        state.recentGridOriginX = x + 3;
        int mainOriginX = calculateGridOriginX(x);

        List<RecentEntry> recentItems = getRecentItems(sm, state);
        int recentList = recentItems.size();
        state.recentGridW = state.recentCols * (SLOT_SIZE + SLOT_GAP) - SLOT_GAP;

        int searchX = containerBtnX + BUTTON_SIZE + BUTTON_SPACING;
        int searchY = y + PAD_TOP + 1;
        int searchW = (mainOriginX + calcMainGridW) - searchX;
        if (searchW > SEARCH_INPUT_H) {
            if (state.searchFocused != prevSearchFocused) {
                searchFocusAnim.target(state.searchFocused ? 1f : 0f);
                prevSearchFocused = state.searchFocused;
            }
            boolean searchHovered = !state.searchFocused
                    && mouseX >= searchX && mouseX < searchX + searchW
                    && mouseY >= searchY && mouseY < searchY + SEARCH_INPUT_H;
            SdfRenderer.drawInputBox(g, searchX, searchY, searchW, SEARCH_INPUT_H,
                    searchFocusAnim.get(), searchHoverAnim.track(searchHovered), 4);

            Font searchFont = mc.font;
            String searchText = state.searchBuffer.toString();
            int textColor = ThemeManager.getTextColor();
            int textX = searchX + SEARCH_INPUT_PAD;
            int textY = searchY + (SEARCH_INPUT_H - searchFont.lineHeight) / 2;
            int contentAreaW = searchW - SEARCH_INPUT_PAD * 2;

            if (state.searchFocused) {
                String displayText = TextRenderer.trimToWidth(searchFont, searchText, contentAreaW);
                g.drawString(searchFont, displayText, textX, textY, textColor, false);

                if ((System.currentTimeMillis() / CURSOR_BLINK_MS) % 2 == 0) {
                    int cursorX = textX + searchFont.width(displayText);
                    g.fill(cursorX, textY, cursorX + 1, textY + searchFont.lineHeight, 0xFFFFFFFF);
                }
            } else {
                String placeholder = searchText.isEmpty()
                        ? Component.translatable("tooltip.rtsbuilding.rightdown.search_placeholder").getString()
                        : searchText;
                String displayText = TextRenderer.trimToWidth(searchFont, placeholder, contentAreaW);
                int placeholderColor = searchText.isEmpty() ? (textColor & 0xFFFFFF) | 0x60000000 : textColor;
                g.drawString(searchFont, displayText, textX, textY, placeholderColor, false);
            }
        }

        state.mainGridOriginX = mainOriginX;
        state.mainGridCols = mainCols;
        state.cachedMainGridWidth = calcMainGridW;
        int recentItemRows = (recentList + state.recentCols - 1) / state.recentCols;
        int itemRows = (state.slotEntries.size() + mainCols - 1) / mainCols;
        int visibleH = h - PAD_TOP * 2;
        int gridVisibleH = visibleH - GRID_TOP_OFFSET;
        int totalRows = Math.max(recentItemRows, itemRows);
        int gridH = totalRows * (SLOT_SIZE + SLOT_GAP) - SLOT_GAP;

        scrollBar.setContent(gridH, gridVisibleH + 6);
        int scroll = scrollBar.getScroll();
        int recentContentH = recentItemRows * (SLOT_SIZE + SLOT_GAP) - SLOT_GAP;
        recentScrollBar.setContent(recentContentH, gridVisibleH + 6);
        int recentScroll = recentScrollBar.getScroll();

        int originY = calculateGridOriginY(y);
        int frameH = state.rows * (SLOT_SIZE + SLOT_GAP) - SLOT_GAP;
        int scissorBottomY = originY + frameH;

        int localMouseX = ctx.getLastMouseX();
        int localMouseY = ctx.getLastMouseY();
        int hoveredSlot = findHoveredSlot(localMouseX, localMouseY, mainOriginX, originY, scroll);
        state.tooltipSlotIndex = hoveredSlot;
        int hoveredRecent = findRecentHovered(localMouseX, localMouseY, state.recentGridOriginX, originY, recentScroll, recentList);

        g.flush();
        Screen screen = mc.screen;
        if (screen instanceof BuilderScreen bs) {
            bs.enableRtsScissor(g, state.recentGridOriginX, originY + 1, mainOriginX + state.cachedMainGridWidth, scissorBottomY);
        } else {
            g.enableScissor(state.recentGridOriginX, originY + 1, mainOriginX + state.cachedMainGridWidth, scissorBottomY);
        }

        SpriteRenderer.drawTiledGrid(g, GridSlotRenderer.SLOT_NORMAL, slotThemeOffset,
                state.recentGridOriginX, originY, SLOT_SIZE, SLOT_SIZE, SLOT_GAP,
                state.recentCols, Math.max(state.rows, recentItemRows), recentScroll, originY, scissorBottomY);

        SpriteRenderer.drawTiledGrid(g, GridSlotRenderer.SLOT_NORMAL, slotThemeOffset,
                mainOriginX, originY, SLOT_SIZE, SLOT_SIZE, SLOT_GAP,
                mainCols, Math.max(state.rows, itemRows), scroll, originY, scissorBottomY);

        g.flush();

        boolean anySlotHovered = hoveredSlot >= 0 || hoveredRecent >= 0;
        float hoverAlpha = slotHoverAnim.track(anySlotHovered);
        float selectedAlpha = slotSelectedAnim.track(!state.currentSelectedItem.isEmpty());

        boolean nothingHovered = hoveredSlot < 0 && hoveredRecent < 0;

        int foundSelectedRecent = -1;
        for (int i = 0; i < recentList; i++) {
            int col = i % state.recentCols;
            int row = i / state.recentCols;
            int slotX = state.recentGridOriginX + col * (SLOT_SIZE + SLOT_GAP);
            int slotY = originY + row * (SLOT_SIZE + SLOT_GAP) - recentScroll;
            if (slotY + SLOT_SIZE < originY || slotY > scissorBottomY) continue;

            RecentEntry re = recentItems.get(i);
            boolean isHovered = (i == hoveredRecent);
            boolean drawHover = isHovered || (nothingHovered && i == lastHoveredRecent);

            RenderSystem.disableDepthTest();

            ItemStack stack = re.preview();
            if (!stack.isEmpty()) {
                GridSlotRenderer.drawIcon(g, stack, slotX, slotY);
            }

            boolean recentSelected = !state.currentSelectedItem.isEmpty() && ItemStack.isSameItemSameComponents(stack, state.currentSelectedItem);
            if (recentSelected) foundSelectedRecent = i;
            boolean drawSelected = recentSelected || (state.currentSelectedItem.isEmpty() && i == lastSelectedRecent);
            GridSlotRenderer.drawOverlay(g, slotX, slotY, drawHover, drawSelected, slotThemeOffset, hoverAlpha, selectedAlpha);
        }
        if (hoveredRecent >= 0) lastHoveredRecent = hoveredRecent;
        if (foundSelectedRecent >= 0) lastSelectedRecent = foundSelectedRecent;

        int foundSelectedMain = -1;
        for (int i = 0; i < state.slotEntries.size(); i++) {
            int col = i % mainCols;
            int row = i / mainCols;
            int slotX = mainOriginX + col * (SLOT_SIZE + SLOT_GAP);
            int slotY = originY + row * (SLOT_SIZE + SLOT_GAP) - scroll;
            if (slotY + SLOT_SIZE < originY || slotY > scissorBottomY) continue;

            SlotEntry entry = state.slotEntries.get(i);
            boolean isHovered = (i == hoveredSlot);
            boolean drawHover = isHovered || (nothingHovered && i == lastHoveredMain);

            RenderSystem.disableDepthTest();

            ItemStack stack = entry.stack();
            if (entry.isFluid()) {
                if (entry.originalEntry() instanceof com.rtsbuilding.rtsbuilding.client.domain.state.FluidEntry fe) {
                    GridSlotRenderer.drawFluidIcon(g, fe.fluidId(), slotX, slotY);
                }
            } else if (!stack.isEmpty()) {
                GridSlotRenderer.drawIcon(g, stack, slotX, slotY);
            }

            long count = entry.count();
            if (count >= 1) {
                Font font = IClientItemExtensions.of(stack).getFont(stack, IClientItemExtensions.FontContext.ITEM_COUNT);
                if (font == null) font = mc.font;
                GridSlotRenderer.drawAmountText(g, font, count, slotX, slotY);
            }

            boolean mainSelected = !state.currentSelectedItem.isEmpty() && ItemStack.isSameItemSameComponents(stack, state.currentSelectedItem);
            if (mainSelected) foundSelectedMain = i;
            boolean drawSelected = mainSelected || (state.currentSelectedItem.isEmpty() && i == lastSelectedMain);
            GridSlotRenderer.drawOverlay(g, slotX, slotY, drawHover, drawSelected, slotThemeOffset, hoverAlpha, selectedAlpha);
        }
        if (hoveredSlot >= 0) lastHoveredMain = hoveredSlot;
        if (foundSelectedMain >= 0) lastSelectedMain = foundSelectedMain;

        RenderSystem.clear(256, Minecraft.ON_OSX);

        if (state.selectedSlotIndex >= state.slotEntries.size() && !state.slotEntries.isEmpty()) {
            state.selectedSlotIndex = -1;
        }

        g.flush();
        g.disableScissor();

        SdfRenderer.drawRoundedOutline(g, state.recentGridOriginX, originY, state.recentGridW, frameH, 4, DarkUiPalette.accent());
        SdfRenderer.drawRoundedOutline(g, mainOriginX, originY, state.cachedMainGridWidth, frameH, 4, DarkUiPalette.accent());

        int dividerX = (state.recentGridOriginX + state.recentGridW + mainOriginX) / 2;
        g.vLine(dividerX, y + 5, originY + gridVisibleH - 3, ThemeManager.getDividerColor());

        int recentBarX = state.recentGridOriginX + state.recentGridW + 3;
        recentScrollBar.render(g, recentBarX, originY + 6, gridVisibleH - 12);

        if (state.slotEntries.isEmpty() && state.searchBuffer.length() > 0) {
            String hint = Component.translatable("tooltip.rtsbuilding.rightdown.no_search_results").getString();
            int hintX = mainOriginX + state.cachedMainGridWidth / 2;
            int hintY = originY + gridVisibleH / 2;
            TextRenderer.drawCentered(g, mc.font, hint, hintX, hintY, HINT_COLOR);
        }

        renderScrollbar(g, x, y, h);
    }

    public void postRenderContent(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) {
            renderTooltipOverlay(g, ctx.getLastMouseX(), ctx.getLastMouseY(),
                    mc.screen.width, mc.screen.height);

            typeFilterPopup.render(g, ctx.getLastMouseX(), ctx.getLastMouseY());
            containerModePopup.render(g, ctx.getLastMouseX(), ctx.getLastMouseY());
        }
    }

    private int calculateSortButtonX(int baseX) {
        return baseX + PAD_LEFT + BUTTON_SIZE + BUTTON_SPACING;
    }

    private int calculateOrderButtonX(int baseX) {
        return baseX + PAD_LEFT + BUTTON_SIZE + BUTTON_SPACING + BUTTON_SIZE + BUTTON_SPACING;
    }

    private int calculateTypeFilterButtonX(int baseX) {
        return baseX + PAD_LEFT + BUTTON_SIZE + BUTTON_SPACING + BUTTON_SIZE + BUTTON_SPACING + BUTTON_SIZE + BUTTON_SPACING;
    }

    private int calculateContainerButtonX(int baseX) {
        return baseX + PAD_LEFT + BUTTON_SIZE + BUTTON_SPACING + BUTTON_SIZE + BUTTON_SPACING + BUTTON_SIZE + BUTTON_SPACING + BUTTON_SIZE + BUTTON_SPACING;
    }

    private int calculateGridOriginX(int baseX) {
        return baseX + PAD_LEFT;
    }

    private int calculateGridOriginY(int baseY) {
        return baseY + PAD_TOP + GRID_TOP_OFFSET;
    }

    private int getCalcMainCols() {
        return Math.max(1, (ctx.getWidth() - PAD_LEFT - RIGHT_GAP) / SLOT_SIZE);
    }

    private int getCalcMainGridWidth() {
        return getCalcMainCols() * SLOT_SIZE;
    }

    private int getCalcRows() {
        return Math.max(1, (ctx.getHeight() - PAD_TOP - GRID_TOP_OFFSET) / SLOT_SIZE + 2);
    }

    private int findHoveredSlot(int mx, int my, int originX, int originY, int scroll) {
        if (!ctx.contains(mx, my)) return -1;
        int relX = mx - originX;
        int relY = my - originY + scroll;
        if (relX < 0 || relY < 0) return -1;
        int localCols = getCalcMainCols();
        int localRows = getCalcRows();
        int col = relX / (SLOT_SIZE + SLOT_GAP);
        int row = relY / (SLOT_SIZE + SLOT_GAP);
        if (col >= localCols || row >= localRows) return -1;
        int idx = row * localCols + col;
        if (idx >= state.slotEntries.size()) return -1;

        int calculatedFrameHeight = localRows * (SLOT_SIZE + SLOT_GAP) - SLOT_GAP;
        int bottomY = originY + calculatedFrameHeight;
        if (my < originY || my >= bottomY) {
            return -1;
        }

        return idx;
    }

    private int findRecentHovered(int mx, int my, int originX, int originY, int scroll, int count) {
        if (!ctx.contains(mx, my) || count <= 0) return -1;
        int relX = mx - originX;
        int relY = my - originY + scroll;
        if (relX < 0 || relY < 0) return -1;
        int localRows = getCalcRows();
        int col = relX / (SLOT_SIZE + SLOT_GAP);
        int row = relY / (SLOT_SIZE + SLOT_GAP);
        if (col >= state.recentCols || row >= localRows) return -1;
        int idx = row * state.recentCols + col;
        if (idx >= count) return -1;
        int bottomY = originY + localRows * (SLOT_SIZE + SLOT_GAP) - SLOT_GAP;
        if (my < originY || my >= bottomY) return -1;
        return idx;
    }

    private void renderEmptyHint(GuiGraphics g) {
        String hint = "No storage";
        Minecraft mc = Minecraft.getInstance();
        int lineH = mc.font.lineHeight;
        TextRenderer.drawCentered(g, mc.font, hint,
                ctx.getX() + ctx.getWidth() / 2, ctx.getY() + (ctx.getHeight() - lineH) / 2, HINT_COLOR);
    }

    private void renderScrollbar(GuiGraphics g, int x, int y, int h) {
        int barX = state.mainGridOriginX + state.cachedMainGridWidth + 3;
        int originY = y + PAD_TOP + GRID_TOP_OFFSET;
        int gridVisibleH = h - PAD_TOP * 2 - GRID_TOP_OFFSET;
        scrollBar.render(g, barX, originY + 6, gridVisibleH - 12);
    }

    private void renderTooltipOverlay(GuiGraphics g, int mouseX, int mouseY, int screenW, int screenH) {
        int x = ctx.getX(), y = ctx.getY();

        int itemDisplayX = x + PAD_LEFT;
        int itemDisplayY = y + PAD_TOP + 1;
        int itemDisplaySize = BUTTON_SIZE;

        int sortBtnX = calculateSortButtonX(x);
        int sortBtnY = y + PAD_TOP + 1;

        int orderBtnX = calculateOrderButtonX(x);
        int orderBtnY = y + PAD_TOP + 1;

        int typeFilterBtnX = calculateTypeFilterButtonX(x);
        int typeFilterBtnY = y + PAD_TOP + 1;

        int containerBtnX = calculateContainerButtonX(x);
        int containerBtnY = y + PAD_TOP + 1;

        int recentSortBtnX = state.recentGridOriginX;
        int recentSortBtnY = y + PAD_TOP + 1;

        if (currentItemTooltip.shouldRender()) {
            String text = Component.translatable("tooltip.rtsbuilding.rightdown.current_selected_item").getString() + "\n" +
                         Component.translatable("tooltip.rtsbuilding.rightdown.current_selected_item.desc").getString();
            renderTooltipAbove(g, currentItemTooltip,
                    itemDisplayX, itemDisplayY, itemDisplaySize, itemDisplaySize,
                    text, screenW, screenH);
        }

        if (sortButtonTooltip.shouldRender()) {
            String text = Component.translatable("tooltip.rtsbuilding.rightdown.sort_button").getString() + "\n" +
                         Component.translatable("tooltip.rtsbuilding.rightdown.sort_button.desc").getString();
            renderTooltipAbove(g, sortButtonTooltip,
                    sortBtnX, sortBtnY, BUTTON_SIZE, BUTTON_SIZE,
                    text, screenW, screenH);
        }

        if (orderButtonTooltip.shouldRender()) {
            String text = Component.translatable("tooltip.rtsbuilding.rightdown.order_button").getString() + "\n" +
                         Component.translatable("tooltip.rtsbuilding.rightdown.order_button.desc").getString();
            renderTooltipAbove(g, orderButtonTooltip,
                    orderBtnX, orderBtnY, BUTTON_SIZE, BUTTON_SIZE,
                    text, screenW, screenH);
        }

        if (typeFilterButtonTooltip.shouldRender()) {
            String text = Component.translatable("tooltip.rtsbuilding.rightdown.type_filter_button").getString() + "\n" +
                         Component.translatable("tooltip.rtsbuilding.rightdown.type_filter_button.desc").getString();
            renderTooltipAbove(g, typeFilterButtonTooltip,
                    typeFilterBtnX, typeFilterBtnY, BUTTON_SIZE, BUTTON_SIZE,
                    text, screenW, screenH);
        }

        if (containerButtonTooltip.shouldRender()) {
            String text = Component.translatable("tooltip.rtsbuilding.rightdown.container_button").getString() + "\n" +
                         Component.translatable("tooltip.rtsbuilding.rightdown.container_button.desc").getString();
            renderTooltipAbove(g, containerButtonTooltip,
                    containerBtnX, containerBtnY, BUTTON_SIZE, BUTTON_SIZE,
                    text, screenW, screenH);
        }

        if (recentSortButtonTooltip.shouldRender()) {
            String text = Component.translatable("tooltip.rtsbuilding.rightdown.recent_order_button").getString() + "\n" +
                         Component.translatable("tooltip.rtsbuilding.rightdown.recent_order_button.desc").getString();
            renderTooltipAbove(g, recentSortButtonTooltip,
                    recentSortBtnX, recentSortBtnY, BUTTON_SIZE, BUTTON_SIZE,
                    text, screenW, screenH);
        }
    }

    private static void renderTooltipAbove(GuiGraphics g, TooltipController tooltip,
                                           int btnX, int btnY, int btnW, int btnH,
                                           String text, int screenW, int screenH) {
        float alpha = tooltip.getAlpha();
        var font = Minecraft.getInstance().font;

        String[] lines = text.split("\\n");
        int lineHeight = font.lineHeight;
        int lineGap = 1;
        float scaledLineH = lineHeight * 0.75f;
        float scaledLineGap = lineGap * 0.75f;
        int maxLineW = 0;
        for (String line : lines) {
            maxLineW = Math.max(maxLineW, font.width(line));
        }
        int padH = 6, padV = 3;
        int tipW = (int)(maxLineW * 0.75f) + padH * 2;
        int tipH = (int)(scaledLineH * lines.length + scaledLineGap * (lines.length - 1)) + padV * 2;

        int tipX = btnX;
        int tipY = btnY - tipH - 2;

        tipX = Math.max(0, Math.min(tipX, screenW - tipW));
        tipY = Math.max(0, tipY);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        SdfRenderer.drawVectorFloatingPanel(g, tipX, tipY, tipW, tipH, false, alpha);

        float textY = tipY + padV;
        for (int i = 0; i < lines.length; i++) {
            g.pose().pushPose();
            g.pose().translate(tipX + padH, textY, 0);
            g.pose().scale(0.75f, 0.75f, 1.0f);
            TextRenderer.draw(g, lines[i], 0, 0, 0xFFFFFFFF);
            g.pose().popPose();
            textY += scaledLineH + scaledLineGap;
        }

        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.disableBlend();
    }

    private void updateScrollAnimation() {
        if (!state.isScrollingAnimated) {
            return;
        }

        long currentTime = System.currentTimeMillis();
        float elapsed = (currentTime - state.animationStartTime) / 1000.0f * 20.0f;

        if (elapsed >= GridState.ANIMATION_DURATION) {
            scrollBar.setScroll((int) state.targetScroll);
            state.animatedScroll = state.targetScroll;
            state.isScrollingAnimated = false;
            return;
        }

        float progress = elapsed / GridState.ANIMATION_DURATION;
        float easeOut = 1.0f - (float) Math.pow(1.0f - progress, 2);

        state.animatedScroll = state.animatedScroll + (state.targetScroll - state.animatedScroll) * easeOut;

        scrollBar.setScroll((int) state.animatedScroll);
    }

    public void startSmoothScrollAnimation(double targetScrollPos) {
        state.targetScroll = targetScrollPos;
        state.animatedScroll = scrollBar.getScroll();
        state.animationStartTime = System.currentTimeMillis();
        state.isScrollingAnimated = true;
    }

    public void scrollToSelectedItem() {
        if (state.currentSelectedItem.isEmpty() || state.slotEntries.isEmpty()) {
            return;
        }

        int targetIndex = -1;
        for (int i = 0; i < state.slotEntries.size(); i++) {
            SlotEntry entry = state.slotEntries.get(i);
            if (ItemStack.isSameItemSameComponents(entry.stack(), state.currentSelectedItem)) {
                targetIndex = i;
                break;
            }
        }

        if (targetIndex == -1) {
            return;
        }

        int targetRow = targetIndex / state.cols;
        int targetY = targetRow * (SLOT_SIZE + SLOT_GAP);

        int gridVisibleH = ctx.getHeight() - PAD_TOP * 2 - GRID_TOP_OFFSET;
        int rowsVisible = gridVisibleH / (SLOT_SIZE + SLOT_GAP);
        int centeredScroll = targetY - (rowsVisible / 2) * (SLOT_SIZE + SLOT_GAP);

        centeredScroll = Math.max(0, centeredScroll);
        centeredScroll = Math.min(scrollBar.getMaxScroll(), centeredScroll);

        startSmoothScrollAnimation(centeredScroll);
    }

    private void checkAndRebuildIfDirty(StorageModule sm) {
        int currentRevision = sm.getRevision();
        boolean revisionChanged = currentRevision != state.lastRevision;
        boolean sortChanged = state.currentSortType != state.lastSortType || state.reverseSortOrder != state.lastReverseSortOrder;
        boolean filterChanged = state.showItems != state.lastShowItems || state.showFluids != state.lastShowFluids;
        boolean containerFilterChanged = state.showBidirectional != state.lastShowBidirectional || state.showExtractOnly != state.lastShowExtractOnly;

        if (state.slotEntriesDirty || revisionChanged || sortChanged || filterChanged || containerFilterChanged) {
            buildSlotEntries(sm.getEntries(), sm.getFluidEntries());
            state.lastRevision = currentRevision;
            state.lastSortType = state.currentSortType;
            state.lastReverseSortOrder = state.reverseSortOrder;
            state.lastShowItems = state.showItems;
            state.lastShowFluids = state.showFluids;
            state.lastShowBidirectional = state.showBidirectional;
            state.lastShowExtractOnly = state.showExtractOnly;
            state.slotEntriesDirty = false;
        }
    }

    private void buildSlotEntries(List<?> items, List<?> fluids) {
        state.slotEntries.clear();

        if (state.showItems) {
            for (Object obj : items) {
                if (obj instanceof StorageEntry se) {
                    if (se.stack() == null || se.stack().isEmpty()) continue;

                    // 背包来源条目（MODE_PLAYER_INVENTORY）不受双向/只读过滤影响，始终显示；
                    // 来源独立成条，与存储条目分开计数，转移变化可正确反映
                    boolean matchesBidirectional = se.isBidirectional() && state.showBidirectional;
                    boolean matchesExtractOnly = se.isExtractOnly() && state.showExtractOnly;
                    boolean matchesPlayerInventory = se.isPlayerInventory();
                    if (!matchesBidirectional && !matchesExtractOnly && !matchesPlayerInventory) continue;

                    String sortName = se.stack().getHoverName().getString().toLowerCase();
                    String sortMod = se.namespace();
                    state.slotEntries.add(new SlotEntry(se.stack(), se.count(), false, obj, sortName, sortMod));
                }
            }
        }

        if (state.showFluids) {
            for (Object obj : fluids) {
                if (obj instanceof FluidEntry fe) {
                    if (fe.preview() == null || fe.preview().isEmpty()) continue;

                    boolean matchesBidirectional = fe.isBidirectional() && state.showBidirectional;
                    boolean matchesExtractOnly = fe.isExtractOnly() && state.showExtractOnly;
                    if (!matchesBidirectional && !matchesExtractOnly) continue;

                    String sortName = fe.label() != null ? fe.label().toLowerCase() : "";
                    String sortMod = fe.namespace() != null ? fe.namespace() : "";
                    state.slotEntries.add(new SlotEntry(fe.preview(), fe.amount(), true, obj, sortName, sortMod));
                }
            }
        }

        sortSlotEntries();
    }

    private void sortSlotEntries() {
        state.slotEntries.sort((entry1, entry2) -> {
            int result;
            switch (state.currentSortType) {
                case NAME -> result = entry1.sortName().compareTo(entry2.sortName());
                case COUNT -> result = Long.compare(entry2.count(), entry1.count());
                case MOD -> {
                    result = entry1.sortMod().compareTo(entry2.sortMod());
                    if (result == 0) {
                        result = entry1.sortName().compareTo(entry2.sortName());
                    }
                }
                default -> result = 0;
            }
            // 同排位时存储条目优先，背包条目排后
            if (result == 0) {
                result = Integer.compare(sourceRank(entry1), sourceRank(entry2));
            }
            return state.reverseSortOrder ? -result : result;
        });
    }

    private static int sourceRank(SlotEntry entry) {
        return entry.originalEntry() instanceof StorageEntry se && se.isPlayerInventory() ? 1 : 0;
    }

    public static List<RecentEntry> getRecentItems(StorageModule sm) {
        if (sm == null) return List.of();

        List<RecentEntry> serverEntries = sm.getRecentEntriesTyped();

        return serverEntries;
    }

    public static List<RecentEntry> getRecentItems(StorageModule sm, GridState state) {
        if (sm == null) return List.of();

        List<RecentEntry> serverEntries = sm.getRecentEntriesTyped();

        Map<String, RecentEntry> merged = new LinkedHashMap<>();
        for (RecentEntry entry : serverEntries) {
            merged.put(entry.id(), entry);
        }
        for (var entry : state.itemSelectCounts.entrySet()) {
            String id = entry.getKey();
            if (!merged.containsKey(id)) {
                ItemStack preview = state.itemSelectPreviews.getOrDefault(id, ItemStack.EMPTY);
                if (!preview.isEmpty()) {
                    merged.put(id, new RecentEntry(id, 0, 0, (byte) 0, preview));
                }
            }
        }

        List<RecentEntry> result = new ArrayList<>(merged.values());
        result.sort(Comparator.<RecentEntry, Integer>comparing(e -> state.itemSelectCounts.getOrDefault(e.id(), 0)).reversed());
        if (!state.recentSortAscending) {
            java.util.Collections.reverse(result);
        }
        if (state.recentSearchBuffer.length() > 0) {
            String query = state.recentSearchBuffer.toString().toLowerCase();
            result.removeIf(e -> {
                String name = e.preview().getHoverName().getString().toLowerCase();
                return !name.contains(query);
            });
        }
        return result;
    }

    public void recordItemSelection(String itemId, ItemStack stack) {
        if (itemId == null || stack.isEmpty()) return;
        state.itemSelectCounts.merge(itemId, 1, Integer::sum);
        state.itemSelectPreviews.put(itemId, stack);
        // 重新选中即恢复最近栏显示：取消数据层屏蔽，条目将在下次服务端同步时回归
        StorageModule sm = RtsClientKernel.get().module(StorageModule.class);
        if (sm != null) sm.restoreRecentEntry(itemId);
    }
}
