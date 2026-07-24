package com.rtsbuilding.rtsbuilding.client.presentation.plugin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.rtsbuilding.rtsbuilding.client.domain.state.FluidEntry;
import com.rtsbuilding.rtsbuilding.client.domain.state.RecentEntry;
import com.rtsbuilding.rtsbuilding.client.domain.state.StorageEntry;
import com.rtsbuilding.rtsbuilding.client.infrastructure.di.CompositionRoot;
import com.rtsbuilding.rtsbuilding.client.infrastructure.module.building.BuildingModule;
import com.rtsbuilding.rtsbuilding.client.infrastructure.module.storage.StorageModule;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.base.component.ScrollBar;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.base.overlay.OverlayContext;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.base.popup.BasePopup;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.downbar.render.GridSlotRenderer;
import com.rtsbuilding.rtsbuilding.client.presentation.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.util.render.CrossFadeRenderer;
import com.rtsbuilding.rtsbuilding.client.util.render.SpriteRenderer;
import com.rtsbuilding.rtsbuilding.client.util.render.TextRenderer;
import com.rtsbuilding.rtsbuilding.client.util.render.model.NineSliceRegion;
import com.rtsbuilding.rtsbuilding.client.util.render.model.SpriteRegion;
import com.rtsbuilding.rtsbuilding.client.util.render.model.TextureInfo;
import com.rtsbuilding.rtsbuilding.client.util.state.TooltipController;
import com.rtsbuilding.rtsbuilding.client.util.theme.ThemeManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import org.lwjgl.glfw.GLFW;

import java.util.*;

import static com.rtsbuilding.rtsbuilding.client.presentation.panel.downbar.render.GridSlotRenderer.SLOT_SIZE;


public class ItemGrid {

    
    
    private final OverlayContext context;

    public ItemGrid(OverlayContext context) {
        this.context = context;
        this.typeFilterPopup = new TypeFilterPopup(showItems, showFluids, (items, fluids) -> onTypeFilterChanged(items, fluids));
        this.containerModePopup = new ContainerModePopup(showBidirectional, showExtractOnly, (bidirectional, extractOnly) -> {
            boolean changed = this.showBidirectional != bidirectional || this.showExtractOnly != extractOnly;
            this.showBidirectional = bidirectional;
            this.showExtractOnly = extractOnly;
            if (changed) {
                slotEntriesDirty = true;
            }
        });
    }
    
    private void onTypeFilterChanged(boolean showItems, boolean showFluids) {
        
        boolean stateChanged = this.showItems != showItems || this.showFluids != showFluids;
        
        this.showItems = showItems;
        this.showFluids = showFluids;
        
        
        if (stateChanged) {
            slotEntriesDirty = true;
        }
    }
    
    private int getX() { return context.getX(); }
    private int getY() { return context.getY(); }
    private int getWidth() { return context.getWidth(); }
    private int getHeight() { return context.getHeight(); }
    private int getLastMouseX() { return context.getLastMouseX(); }
    private int getLastMouseY() { return context.getLastMouseY(); }
    private boolean contains(int px, int py) { return context.contains(px, py); }
    private boolean isDividerDragging() { return context.isDividerDragging(); }
    
    private static final int SLOT_GAP = 0;
    private static final int RECENT_MAIN_GAP = 9;
    
    private static final int PAD_LEFT = 92;
    private static final int PAD_TOP = 2;
    
    private static final int GRID_TOP_OFFSET = 20;
    
    private static final int SCROLLBAR_W = 7;
    
    private static final int RIGHT_GAP = 18;

    

    
    private static final int BUTTON_SIZE = 18;
    
    private static final int BUTTON_SPACING = 1;

    

    
    private static final ResourceLocation OVERLAY_TEXTURE = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/down/slots_overlay.png");
    private static final int OVERLAY_TEX_W = 32;
    private static final int OVERLAY_TEX_H = 16;
    private static final int OVERLAY_STATE_H = 16;
    private static final int OVERLAY_BORDER = 2;
    private static final TextureInfo OVERLAY_TEX_INFO = new TextureInfo(
            OVERLAY_TEXTURE, OVERLAY_TEX_W, OVERLAY_TEX_H,
            TextureInfo.ThemeLayout.HORIZONTAL_PAIR,
            TextureInfo.FilterMode.PIXEL);
    private static final NineSliceRegion OVERLAY_NINE_SLICE = NineSliceRegion.fullTheme(
            OVERLAY_TEX_INFO, OVERLAY_STATE_H, OVERLAY_BORDER);

    

    
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

    

    
    private static final ResourceLocation SORT_BTN_BG_TEXTURE = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/base/base_ui/base_ui_2.png");
    private static final int SORT_BTN_BG_TEX_W = 32;
    private static final int SORT_BTN_BG_TEX_H = 48;
    private static final int SORT_BTN_BG_STATE_H = 16;
    private static final TextureInfo SORT_BTN_BG_TEX_INFO = new TextureInfo(
            SORT_BTN_BG_TEXTURE, SORT_BTN_BG_TEX_W, SORT_BTN_BG_TEX_H,
            TextureInfo.ThemeLayout.HORIZONTAL_PAIR,
            TextureInfo.FilterMode.PIXEL);
    
    private static final SpriteRegion SORT_BTN_NORMAL = new SpriteRegion(
            SORT_BTN_BG_TEX_INFO, 0, 0, SORT_BTN_BG_TEX_W / 2, SORT_BTN_BG_STATE_H);
    
    private static final SpriteRegion SORT_BTN_HOVER = new SpriteRegion(
            SORT_BTN_BG_TEX_INFO, 0, SORT_BTN_BG_STATE_H, SORT_BTN_BG_TEX_W / 2, SORT_BTN_BG_STATE_H);

    
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

    

    private final ScrollBar scrollBar = new ScrollBar();
    private final ScrollBar recentScrollBar = new ScrollBar();
    private int recentScroll;

    

    
    public enum SortType {
        NAME("Name"),      
        COUNT("Count"),    
        MOD("Mod");       
        
        private final String displayName;
        
        SortType(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }

    
    private SortType currentSortType = SortType.NAME;
    
    
    private boolean reverseSortOrder = false;

    

    
    private boolean showItems = true;
    
    private boolean showFluids = true;

    
    private boolean showBidirectional = true;
    
    private boolean showExtractOnly = true;

    

    private final TypeFilterPopup typeFilterPopup;
    
    private final ContainerModePopup containerModePopup;

    

    private boolean searchFocused;
    private final StringBuilder searchBuffer = new StringBuilder();
    private int searchCursorPos;
    private long searchCursorBlink;
    private static final long CURSOR_BLINK_MS = 600;

    private boolean recentSortAscending = true;
    private boolean recentSearchFocused;
    private final StringBuilder recentSearchBuffer = new StringBuilder();
    private int recentSearchCursorPos;
    private long recentSearchCursorBlink;

    
    private static final ResourceLocation SEARCH_BOX_TEXTURE = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/base/base_ui/base_ui_4.png");
    private static final int SEARCH_BOX_TEX_W = 32;
    private static final int SEARCH_BOX_TEX_H = 32;
    private static final int SEARCH_BOX_STATE_H = 16;
    private static final int SEARCH_BOX_BORDER = 4;
    private static final TextureInfo SEARCH_BOX_TEX_INFO = new TextureInfo(
            SEARCH_BOX_TEXTURE, SEARCH_BOX_TEX_W, SEARCH_BOX_TEX_H,
            TextureInfo.ThemeLayout.HORIZONTAL_PAIR, TextureInfo.FilterMode.PIXEL);
    private static final NineSliceRegion SEARCH_BOX_NINE_SLICE = NineSliceRegion.fullTheme(
            SEARCH_BOX_TEX_INFO, SEARCH_BOX_STATE_H, SEARCH_BOX_BORDER);

    private static final int SEARCH_INPUT_H = 18;
    private static final int SEARCH_INPUT_PAD = 4;
    private static final int SEARCH_BTN_W = 18;

    private final TooltipController currentItemTooltip = TooltipController.builder().direction(TooltipController.Direction.ABOVE).build();
    
    private final TooltipController sortButtonTooltip = TooltipController.builder().direction(TooltipController.Direction.ABOVE).build();
    
    private final TooltipController orderButtonTooltip = TooltipController.builder().direction(TooltipController.Direction.ABOVE).build();
    
    private final TooltipController typeFilterButtonTooltip = TooltipController.builder().direction(TooltipController.Direction.ABOVE).build();
    
    private final TooltipController containerButtonTooltip = TooltipController.builder().direction(TooltipController.Direction.ABOVE).build();

    
    private int selectedSlotIndex = -1;

    
    private ItemStack currentSelectedItem = ItemStack.EMPTY;

    
    private final List<SlotEntry> slotEntries = new ArrayList<>();
    
    private int cols;
    
    private int rows;
    
    private int mainGridOriginX;
    private int mainGridCols;
    private int cachedMainGridWidth;
    private int recentGridOriginX;
    private int recentGridW;
    private int recentCols;
    
    private int tooltipSlotIndex = -1;

    private final Map<String, Integer> itemSelectCounts = new HashMap<>();
    private final Map<String, ItemStack> itemSelectPreviews = new HashMap<>();

    

    
    private boolean slotEntriesDirty = true;
    
    private int lastRevision = -1;
    
    private SortType lastSortType = SortType.NAME;
    
    private boolean lastReverseSortOrder = false;
    
    private boolean lastShowItems = true;
    
    private boolean lastShowFluids = true;
    
    private boolean lastShowBidirectional = true;
    
    private boolean lastShowExtractOnly = true;

    
    private int lastScroll = 0;

    

    
    private static final float ANIMATION_DURATION = 10.0f;
    
    
    private double targetScroll = 0;
    
    
    private double animatedScroll = 0;
    
    
    private long animationStartTime = 0L;
    
    
    private boolean isScrollingAnimated = false;

    
    public ItemStack getCurrentSelectedItem() {
        return currentSelectedItem;
    }

    public ItemStack getHoveredSlotStack() {
        if (tooltipSlotIndex == -2) {
            
            return currentSelectedItem;
        }
        if (tooltipSlotIndex < 0 || tooltipSlotIndex >= slotEntries.size()) return ItemStack.EMPTY;
        return slotEntries.get(tooltipSlotIndex).stack();
    }

    

    
    
    private record SlotEntry(ItemStack stack, long count, boolean isFluid, Object originalEntry,
                             String sortName, String sortMod) {
    }
    
    
    private void cycleSortType() {
        switch (currentSortType) {
            case NAME -> currentSortType = SortType.COUNT;
            case COUNT -> currentSortType = SortType.MOD;
            case MOD -> currentSortType = SortType.NAME;
        }
        
        
        slotEntriesDirty = true;
    }
    
    
    private void toggleSortOrder() {
        reverseSortOrder = !reverseSortOrder;
        
        
        slotEntriesDirty = true;
    }
    
    
    private void toggleTypeFilter() {
        typeFilterPopup.toggle();
    }
    
    

    public void renderContent(GuiGraphics g) {
        
        updateScrollAnimation();
        
        
        StorageModule sm = CompositionRoot.get().module(StorageModule.class);
        if (sm == null) return;

        
        checkAndRebuildIfDirty(sm);
        boolean hasStorage = sm.isLinked();
        if (slotEntries.isEmpty()) {
            if (!hasStorage) {
                renderEmptyHint(g);
                return;
            }
        }
        
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();

        
        int slotThemeOffset = SpriteRenderer.getThemeOffset(GridSlotRenderer.SLOT_NORMAL);
        int overlayThemeOffset = SpriteRenderer.getNineSliceThemeOffset(OVERLAY_NINE_SLICE);

        
        Minecraft mc = Minecraft.getInstance();
        
        
        int mouseX = (int) getLastMouseX();
        int mouseY = (int) getLastMouseY();
        int itemDisplayX = x + PAD_LEFT;
        int itemDisplayY = y + PAD_TOP + 1;
        int itemDisplaySize = BUTTON_SIZE;
        boolean isHoveringOverCurrentSelection = mouseX >= itemDisplayX && mouseX < itemDisplayX + itemDisplaySize
                && mouseY >= itemDisplayY && mouseY < itemDisplayY + itemDisplaySize;

        currentItemTooltip.update(isHoveringOverCurrentSelection, false);

        
        SpriteRenderer.drawSprite(g, isHoveringOverCurrentSelection ? SORT_BTN_HOVER : SORT_BTN_NORMAL,
                slotThemeOffset, itemDisplayX, itemDisplayY, itemDisplaySize, itemDisplaySize);

        if (!currentSelectedItem.isEmpty()) {
            RenderSystem.disableDepthTest();
            var pose = g.pose();
            pose.pushPose();
            pose.translate(itemDisplayX + 1, itemDisplayY + 1, 0);
            g.renderItem(currentSelectedItem, 0, 0);
            pose.popPose();
            RenderSystem.enableDepthTest();
            g.renderItemDecorations(mc.font, currentSelectedItem, itemDisplayX + 1, itemDisplayY + 1);
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

        SpriteRenderer.drawSprite(g, isHoveringOverSortBtn ? SORT_BTN_HOVER : SORT_BTN_NORMAL,
                slotThemeOffset, sortBtnX, sortBtnY, BUTTON_SIZE, BUTTON_SIZE);
        SpriteRenderer.drawSprite(g, switch (currentSortType) {
            case NAME -> SORT_NAME_ICON;
            case COUNT -> SORT_COUNT_ICON;
            case MOD -> SORT_MOD_ICON;
        }, slotThemeOffset, sortBtnX, sortBtnY, BUTTON_SIZE, BUTTON_SIZE);

        
        int orderBtnX = calculateOrderButtonX(x);
        int orderBtnY = y + PAD_TOP + 1;
        boolean isHoveringOverOrderBtn = mouseX >= orderBtnX && mouseX < orderBtnX + BUTTON_SIZE
                && mouseY >= orderBtnY && mouseY < orderBtnY + BUTTON_SIZE;
        orderButtonTooltip.update(isHoveringOverOrderBtn, false);

        SpriteRenderer.drawSprite(g, isHoveringOverOrderBtn ? SORT_BTN_HOVER : SORT_BTN_NORMAL,
                slotThemeOffset, orderBtnX, orderBtnY, BUTTON_SIZE, BUTTON_SIZE);
        SpriteRenderer.drawSprite(g, reverseSortOrder ? ORDER_DESC_ICON : ORDER_ASC_ICON,
                slotThemeOffset, orderBtnX, orderBtnY, BUTTON_SIZE, BUTTON_SIZE);

        
        int typeFilterBtnX = calculateTypeFilterButtonX(x);
        int typeFilterBtnY = y + PAD_TOP + 1;
        boolean isHoveringOverTypeFilterBtn = mouseX >= typeFilterBtnX && mouseX < typeFilterBtnX + BUTTON_SIZE
                && mouseY >= typeFilterBtnY && mouseY < typeFilterBtnY + BUTTON_SIZE;
        typeFilterButtonTooltip.update(isHoveringOverTypeFilterBtn, false);

        SpriteRenderer.drawSprite(g, isHoveringOverTypeFilterBtn ? SORT_BTN_HOVER : SORT_BTN_NORMAL,
                slotThemeOffset, typeFilterBtnX, typeFilterBtnY, BUTTON_SIZE, BUTTON_SIZE);
        SpriteRenderer.drawSprite(g, TYPE_ITEM_ICON,
                0, typeFilterBtnX, typeFilterBtnY, BUTTON_SIZE, BUTTON_SIZE);

        
        int containerBtnX = calculateContainerButtonX(x);
        int containerBtnY = y + PAD_TOP + 1;
        boolean isHoveringOverContainerBtn = mouseX >= containerBtnX && mouseX < containerBtnX + BUTTON_SIZE
                && mouseY >= containerBtnY && mouseY < containerBtnY + BUTTON_SIZE;
        containerButtonTooltip.update(isHoveringOverContainerBtn, false);

        SpriteRenderer.drawSprite(g, isHoveringOverContainerBtn ? SORT_BTN_HOVER : SORT_BTN_NORMAL,
                slotThemeOffset, containerBtnX, containerBtnY, BUTTON_SIZE, BUTTON_SIZE);
        SpriteRenderer.drawSprite(g, CONTAINER_EXTRACT_ICON,
                0, containerBtnX, containerBtnY, BUTTON_SIZE, BUTTON_SIZE);

        
        int recentSortBtnX = recentGridOriginX;
        int recentSortBtnY = y + PAD_TOP + 1;
        boolean isHoveringRecentSort = mouseX >= recentSortBtnX && mouseX < recentSortBtnX + BUTTON_SIZE
                && mouseY >= recentSortBtnY && mouseY < recentSortBtnY + BUTTON_SIZE;
        SpriteRenderer.drawSprite(g, isHoveringRecentSort ? SORT_BTN_HOVER : SORT_BTN_NORMAL,
                slotThemeOffset, recentSortBtnX, recentSortBtnY, BUTTON_SIZE, BUTTON_SIZE);
        SpriteRenderer.drawSprite(g, recentSortAscending ? ORDER_ASC_ICON : ORDER_DESC_ICON,
                slotThemeOffset, recentSortBtnX, recentSortBtnY, BUTTON_SIZE, BUTTON_SIZE);

        int recentSearchX = recentGridOriginX + BUTTON_SIZE + BUTTON_SPACING;
        int recentSearchY = y + PAD_TOP + 1;
        int recentSearchW = (x + 3 + recentCols * SLOT_SIZE) - recentSearchX;
        if (recentSearchW > SEARCH_INPUT_H) {
            NineSliceRegion normalSpec = SEARCH_BOX_NINE_SLICE.withTheme();
            NineSliceRegion focusSpec = SEARCH_BOX_NINE_SLICE.withTheme().withVOffset(SEARCH_BOX_STATE_H);
            CrossFadeRenderer.render(g, recentSearchFocused ? 1f : 0f,
                    () -> SpriteRenderer.drawNineSlice(g, normalSpec, recentSearchX, recentSearchY, recentSearchW, SEARCH_INPUT_H),
                    () -> SpriteRenderer.drawNineSlice(g, focusSpec, recentSearchX, recentSearchY, recentSearchW, SEARCH_INPUT_H));

            Font searchFont = mc.font;
            String searchText = recentSearchBuffer.toString();
            int textColor = ThemeManager.getTextColor();
            int textX = recentSearchX + SEARCH_INPUT_PAD;
            int textY = recentSearchY + (SEARCH_INPUT_H - searchFont.lineHeight) / 2;
            int contentAreaW = recentSearchW - SEARCH_INPUT_PAD * 2;

            if (recentSearchFocused) {
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

        
        rows = Math.max(1, (h - PAD_TOP - GRID_TOP_OFFSET) / (SLOT_SIZE + SLOT_GAP) + 2);

        
        int mainCols = Math.max(1, (w - PAD_LEFT - RIGHT_GAP) / (SLOT_SIZE + SLOT_GAP));
        int calcMainGridW = mainCols * (SLOT_SIZE + SLOT_GAP) - SLOT_GAP;
        cols = mainCols;
        recentCols = 3;
        recentGridOriginX = x + 3;
        int mainOriginX = calculateGridOriginX(x);
        int recentList = getRecentItems().size();
        recentGridW = recentCols * (SLOT_SIZE + SLOT_GAP) - SLOT_GAP;

        int searchX = containerBtnX + BUTTON_SIZE + BUTTON_SPACING;
        int searchY = y + PAD_TOP + 1;
        int searchW = (mainOriginX + calcMainGridW) - searchX;
        if (searchW > SEARCH_INPUT_H) {
            NineSliceRegion normalSpec = SEARCH_BOX_NINE_SLICE.withTheme();
            NineSliceRegion focusSpec = SEARCH_BOX_NINE_SLICE.withTheme().withVOffset(SEARCH_BOX_STATE_H);
            CrossFadeRenderer.render(g, searchFocused ? 1f : 0f,
                    () -> SpriteRenderer.drawNineSlice(g, normalSpec, searchX, searchY, searchW, SEARCH_INPUT_H),
                    () -> SpriteRenderer.drawNineSlice(g, focusSpec, searchX, searchY, searchW, SEARCH_INPUT_H));

            Font searchFont = mc.font;
            String searchText = searchBuffer.toString();
            int textColor = ThemeManager.getTextColor();
            int textX = searchX + SEARCH_INPUT_PAD;
            int textY = searchY + (SEARCH_INPUT_H - searchFont.lineHeight) / 2;
            int contentAreaW = searchW - SEARCH_INPUT_PAD * 2;

            if (searchFocused) {
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

        
        mainGridOriginX = mainOriginX;
        mainGridCols = mainCols;
        cachedMainGridWidth = calcMainGridW;
        int recentItemRows = (recentList + recentCols - 1) / recentCols;
        int itemRows = (slotEntries.size() + mainCols - 1) / mainCols;
        int visibleH = h - PAD_TOP * 2;
        int gridVisibleH = visibleH - GRID_TOP_OFFSET;
        int totalRows = Math.max(recentItemRows, itemRows);
        int gridH = totalRows * (SLOT_SIZE + SLOT_GAP) - SLOT_GAP;

        scrollBar.setContent(gridH, gridVisibleH + 6);
        int scroll = scrollBar.getScroll();
        int recentContentH = recentItemRows * (SLOT_SIZE + SLOT_GAP) - SLOT_GAP;
        recentScrollBar.setContent(recentContentH, gridVisibleH + 6);
        recentScroll = recentScrollBar.getScroll();

        int originY = calculateGridOriginY(y);
        int frameH = rows * (SLOT_SIZE + SLOT_GAP) - SLOT_GAP;
        int scissorBottomY = originY + frameH;

        
        int localMouseX = getLastMouseX();
        int localMouseY = getLastMouseY();
        int hoveredSlot = findHoveredSlot(localMouseX, localMouseY, mainOriginX, originY, scroll);
        this.tooltipSlotIndex = hoveredSlot;
        int hoveredRecent = findRecentHovered(localMouseX, localMouseY, recentGridOriginX, originY, recentScroll, recentList);

        
        g.flush();
        Screen screen = mc.screen;
        if (screen instanceof BuilderScreen bs) {
            bs.enableRtsScissor(g, recentGridOriginX, originY + 1, mainOriginX + cachedMainGridWidth, scissorBottomY);
        } else {
            g.enableScissor(recentGridOriginX, originY + 1, mainOriginX + cachedMainGridWidth, scissorBottomY);
        }

        
        SpriteRenderer.drawTiledGrid(g, GridSlotRenderer.SLOT_NORMAL, slotThemeOffset,
                recentGridOriginX, originY, SLOT_SIZE, SLOT_SIZE, SLOT_GAP,
                recentCols, Math.max(rows, recentItemRows), recentScroll, originY, scissorBottomY);

        SpriteRenderer.drawTiledGrid(g, GridSlotRenderer.SLOT_NORMAL, slotThemeOffset,
                mainOriginX, originY, SLOT_SIZE, SLOT_SIZE, SLOT_GAP,
                mainCols, Math.max(rows, itemRows), scroll, originY, scissorBottomY);

        
        g.flush();

        
        for (int i = 0; i < recentList; i++) {
            int col = i % recentCols;
            int row = i / recentCols;
            int slotX = recentGridOriginX + col * (SLOT_SIZE + SLOT_GAP);
            int slotY = originY + row * (SLOT_SIZE + SLOT_GAP) - recentScroll;
            if (slotY + SLOT_SIZE < originY || slotY > scissorBottomY) continue;

            RecentEntry re = getRecentItems().get(i);
            boolean hovered = (i == hoveredRecent);

            RenderSystem.disableDepthTest();

            ItemStack stack = re.preview();
            if (!stack.isEmpty()) {
                GridSlotRenderer.drawIcon(g, stack, slotX, slotY);
            }

            if (re.amount() > 1) {
                GridSlotRenderer.drawAmountText(g, mc.font, re.amount(), slotX, slotY, false);
            }

            boolean recentSelected = !currentSelectedItem.isEmpty() && ItemStack.isSameItemSameComponents(stack, currentSelectedItem);
            GridSlotRenderer.drawOverlay(g, slotX, slotY, hovered, recentSelected, slotThemeOffset);
        }

        
        for (int i = 0; i < slotEntries.size(); i++) {
            int col = i % mainCols;
            int row = i / mainCols;
            int slotX = mainOriginX + col * (SLOT_SIZE + SLOT_GAP);
            int slotY = originY + row * (SLOT_SIZE + SLOT_GAP) - scroll;
            if (slotY + SLOT_SIZE < originY || slotY > scissorBottomY) continue;

            SlotEntry entry = slotEntries.get(i);
            boolean hovered = (i == hoveredSlot);

            RenderSystem.disableDepthTest();

            ItemStack stack = entry.stack();
            if (!stack.isEmpty()) {
                GridSlotRenderer.drawIcon(g, stack, slotX, slotY);
            }

            long count = entry.count;
            if (count >= 1) {
                Font font = IClientItemExtensions.of(stack).getFont(stack, IClientItemExtensions.FontContext.ITEM_COUNT);
                if (font == null) font = mc.font;
                GridSlotRenderer.drawAmountText(g, font, count, slotX, slotY, entry.isFluid());
            }

            boolean mainSelected = !currentSelectedItem.isEmpty() && ItemStack.isSameItemSameComponents(stack, currentSelectedItem);
            GridSlotRenderer.drawOverlay(g, slotX, slotY, hovered, mainSelected, slotThemeOffset);
        }

        
        RenderSystem.clear(256, Minecraft.ON_OSX);

        if (selectedSlotIndex >= slotEntries.size() && !slotEntries.isEmpty()) {
            selectedSlotIndex = -1;
        }

        
        g.flush();
        g.disableScissor();

        
        SpriteRenderer.drawNineSlice(g, OVERLAY_NINE_SLICE, overlayThemeOffset, recentGridOriginX, originY, recentGridW, frameH);
        SpriteRenderer.drawNineSlice(g, OVERLAY_NINE_SLICE, overlayThemeOffset, mainOriginX, originY, cachedMainGridWidth, frameH);

        
        int dividerX = (recentGridOriginX + recentGridW + mainOriginX) / 2;
        g.vLine(dividerX, y + 5, originY + gridVisibleH - 3, ThemeManager.getDividerColor());

        
        int recentBarX = recentGridOriginX + recentGridW + 3;
        recentScrollBar.render(g, recentBarX, originY + 6, gridVisibleH - 12);

        if (slotEntries.isEmpty() && searchBuffer.length() > 0) {
            String hint = Component.translatable("tooltip.rtsbuilding.rightdown.no_search_results").getString();
            int hintX = mainOriginX + cachedMainGridWidth / 2;
            int hintY = originY + gridVisibleH / 2;
            TextRenderer.drawCentered(g, mc.font, hint, hintX, hintY, HINT_COLOR);
        }

        
        renderScrollbar(g, x, y, h);
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
        return Math.max(1, (getWidth() - PAD_LEFT - RIGHT_GAP) / SLOT_SIZE);
    }

    private int getCalcMainGridWidth() {
        return getCalcMainCols() * SLOT_SIZE;
    }

    private int getCalcRows() {
        return Math.max(1, (getHeight() - PAD_TOP - GRID_TOP_OFFSET) / SLOT_SIZE + 2);
    }

    
    private void recordItemSelection(String itemId, ItemStack stack) {
        if (itemId == null || stack.isEmpty()) return;
        itemSelectCounts.merge(itemId, 1, Integer::sum);
        itemSelectPreviews.put(itemId, stack);
    }

    private List<RecentEntry> getRecentItems() {
        StorageModule sm = CompositionRoot.get().module(StorageModule.class);
        if (sm == null) return List.of();

        List<RecentEntry> serverEntries = sm.getRecentEntriesTyped();

        Map<String, RecentEntry> merged = new LinkedHashMap<>();
        for (RecentEntry entry : serverEntries) {
            merged.put(entry.id(), entry);
        }
        for (var entry : itemSelectCounts.entrySet()) {
            String id = entry.getKey();
            if (!merged.containsKey(id)) {
                ItemStack preview = itemSelectPreviews.getOrDefault(id, ItemStack.EMPTY);
                if (!preview.isEmpty()) {
                    merged.put(id, new RecentEntry(id, 0, 0, (byte) 0, preview));
                }
            }
        }

        List<RecentEntry> result = new ArrayList<>(merged.values());
        result.sort(Comparator.<RecentEntry, Integer>comparing(e -> itemSelectCounts.getOrDefault(e.id(), 0)).reversed());
        if (!recentSortAscending) {
            java.util.Collections.reverse(result);
        }
        if (recentSearchBuffer.length() > 0) {
            String query = recentSearchBuffer.toString().toLowerCase();
            result.removeIf(e -> {
                String name = e.preview().getHoverName().getString().toLowerCase();
                return !name.contains(query);
            });
        }
        return result;
    }

    
    private int findRecentHovered(int mx, int my, int originX, int originY, int scroll, int count) {
        if (!contains(mx, my) || count <= 0) return -1;
        int relX = mx - originX;
        int relY = my - originY + scroll;
        if (relX < 0 || relY < 0) return -1;
        int localRows = getCalcRows();
        int col = relX / (SLOT_SIZE + SLOT_GAP);
        int row = relY / (SLOT_SIZE + SLOT_GAP);
        if (col >= recentCols || row >= localRows) return -1;
        int idx = row * recentCols + col;
        if (idx >= count) return -1;
        int bottomY = originY + localRows * (SLOT_SIZE + SLOT_GAP) - SLOT_GAP;
        if (my < originY || my >= bottomY) return -1;
        return idx;
    }

    

    
    private void buildSlotEntries(List<?> items, List<?> fluids) {
        slotEntries.clear();
        
        if (showItems) {
            for (Object obj : items) {
                if (obj instanceof StorageEntry se) {
                    if (se.stack() == null || se.stack().isEmpty()) continue;
                    
                    boolean matchesBidirectional = se.isBidirectional() && showBidirectional;
                    boolean matchesExtractOnly = se.isExtractOnly() && showExtractOnly;
                    if (!matchesBidirectional && !matchesExtractOnly) continue;
                    
                    String sortName = se.stack().getHoverName().getString().toLowerCase();
                    String sortMod = se.namespace();
                    slotEntries.add(new SlotEntry(se.stack(), se.count(), false, obj, sortName, sortMod));
                }
            }
        }
        
        if (showFluids) {
            for (Object obj : fluids) {
                if (obj instanceof FluidEntry fe) {
                    if (fe.preview() == null || fe.preview().isEmpty()) continue;
                    String sortName = fe.label() != null ? fe.label().toLowerCase() : "";
                    String sortMod = fe.namespace() != null ? fe.namespace() : "";
                    slotEntries.add(new SlotEntry(fe.preview(), fe.amount(), true, obj, sortName, sortMod));
                }
            }
        }
        
        
        sortSlotEntries();
    }
    
    
    private void sortSlotEntries() {
        slotEntries.sort((entry1, entry2) -> {
            int result;
            switch (currentSortType) {
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
            return reverseSortOrder ? -result : result;
        });
    }

    
    private void checkAndRebuildIfDirty(StorageModule sm) {
        int currentRevision = sm.getRevision();
        boolean revisionChanged = currentRevision != lastRevision;
        boolean sortChanged = currentSortType != lastSortType || reverseSortOrder != lastReverseSortOrder;
        boolean filterChanged = showItems != lastShowItems || showFluids != lastShowFluids;
        boolean containerFilterChanged = showBidirectional != lastShowBidirectional || showExtractOnly != lastShowExtractOnly;

        if (slotEntriesDirty || revisionChanged || sortChanged || filterChanged || containerFilterChanged) {
            buildSlotEntries(sm.getEntries(), sm.getFluidEntries());
            lastRevision = currentRevision;
            lastSortType = currentSortType;
            lastReverseSortOrder = reverseSortOrder;
            lastShowItems = showItems;
            lastShowFluids = showFluids;
            lastShowBidirectional = showBidirectional;
            lastShowExtractOnly = showExtractOnly;
            slotEntriesDirty = false;
        }
    }

    

    
    private void renderEmptyHint(GuiGraphics g) {
        String hint = "No storage";
        Minecraft mc = Minecraft.getInstance();
        int lineH = mc.font.lineHeight;
        TextRenderer.drawCentered(g, mc.font, hint,
                getX() + getWidth() / 2, getY() + (getHeight() - lineH) / 2, HINT_COLOR);
    }

    

    
    private void renderScrollbar(GuiGraphics g, int x, int y, int h) {
        int barX = mainGridOriginX + cachedMainGridWidth + 3;
        int originY = y + PAD_TOP + GRID_TOP_OFFSET;
        int gridVisibleH = h - PAD_TOP * 2 - GRID_TOP_OFFSET;
        scrollBar.render(g, barX, originY + 6, gridVisibleH - 12);
    }

    

    
    private int findHoveredSlot(int mx, int my, int originX, int originY, int scroll) {
        if (!contains(mx, my)) return -1;
        int relX = mx - originX;
        int relY = my - originY + scroll;
        if (relX < 0 || relY < 0) return -1;
        int localCols = getCalcMainCols();
        int localRows = getCalcRows();
        int col = relX / (SLOT_SIZE + SLOT_GAP);
        int row = relY / (SLOT_SIZE + SLOT_GAP);
        if (col >= localCols || row >= localRows) return -1;
        int idx = row * localCols + col;
        if (idx >= slotEntries.size()) return -1;
        
        
        
        int calculatedFrameHeight = localRows * (SLOT_SIZE + SLOT_GAP) - SLOT_GAP;
        int bottomY = originY + calculatedFrameHeight;
        if (my < originY || my >= bottomY) {
            return -1;
        }
        
        return idx;
    }

    

    
    public boolean isMouseOverPopup(int mx, int my) {
        return (typeFilterPopup.isOpen() && typeFilterPopup.contains(mx, my))
                || (containerModePopup.isOpen() && containerModePopup.contains(mx, my));
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!contains((int) mouseX, (int) mouseY)) return false;
        int recentRight = getX() + 3 + recentCols * SLOT_SIZE;
        int mainLeft = getX() + PAD_LEFT;
        int dividerX = (recentRight + mainLeft) / 2;
        if (mouseX < dividerX) {
            return recentScrollBar.handleScroll(scrollY);
        }
        return scrollBar.handleScroll(scrollY);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        int x = getX(), y = getY(), h = getHeight();

        
        int typeFilterBtnX = calculateTypeFilterButtonX(x);
        int typeFilterBtnY = y + PAD_TOP + 1;
        int containerBtnX = calculateContainerButtonX(x);
        int containerBtnY = y + PAD_TOP + 1;

        if (typeFilterPopup.isOpen()) {
            boolean onToggleBtn = mouseX >= typeFilterBtnX && mouseX < typeFilterBtnX + BUTTON_SIZE
                    && mouseY >= typeFilterBtnY && mouseY < typeFilterBtnY + BUTTON_SIZE;
            if (!onToggleBtn && !typeFilterPopup.contains((int) mouseX, (int) mouseY)) {
                typeFilterPopup.close();
            }
        }
        if (containerModePopup.isOpen()) {
            boolean onToggleBtn = mouseX >= containerBtnX && mouseX < containerBtnX + BUTTON_SIZE
                    && mouseY >= containerBtnY && mouseY < containerBtnY + BUTTON_SIZE;
            if (!onToggleBtn && !containerModePopup.contains((int) mouseX, (int) mouseY)) {
                containerModePopup.close();
            }
        }
        int originY = y + PAD_TOP + GRID_TOP_OFFSET;
        int gridVisibleH = h - PAD_TOP * 2 - GRID_TOP_OFFSET;
        int localMainGridOriginX = x + PAD_LEFT;
        int localMainGridWidth = getCalcMainGridWidth();
        int barX = localMainGridOriginX + localMainGridWidth + 3;
        
        
        int itemDisplayX = x + PAD_LEFT;
        int itemDisplayY = y + PAD_TOP + 1; 
        int itemDisplaySize = BUTTON_SIZE; 

        
        if (mouseX >= itemDisplayX && mouseX < itemDisplayX + itemDisplaySize &&
            mouseY >= itemDisplayY && mouseY < itemDisplayY + itemDisplaySize) {
            
            scrollToSelectedItem();
            return true;
        }
        
        
        int sortBtnX = calculateSortButtonX(x); 
        int sortBtnY = y + PAD_TOP + 1; 
        int sortBtnWidth = BUTTON_SIZE; 
        int sortBtnHeight = BUTTON_SIZE; 
        
        if (mouseX >= sortBtnX && mouseX < sortBtnX + sortBtnWidth &&
            mouseY >= sortBtnY && mouseY < sortBtnY + sortBtnHeight) {
            
            cycleSortType();
            return true;
        }
        
        
        int orderBtnX = calculateOrderButtonX(x); 
        int orderBtnY = y + PAD_TOP + 1; 
        int orderBtnWidth = BUTTON_SIZE; 
        int orderBtnHeight = BUTTON_SIZE; 
        
        if (mouseX >= orderBtnX && mouseX < orderBtnX + orderBtnWidth &&
            mouseY >= orderBtnY && mouseY < orderBtnY + orderBtnHeight) {
            
            toggleSortOrder();
            return true;
        }
        
        
        if (mouseX >= typeFilterBtnX && mouseX < typeFilterBtnX + BUTTON_SIZE &&
            mouseY >= typeFilterBtnY && mouseY < typeFilterBtnY + BUTTON_SIZE) {
            
            toggleTypeFilter();
            
            int screenWidth = Minecraft.getInstance().screen != null ? Minecraft.getInstance().screen.width : 0;
            typeFilterPopup.positionFromButtonAbove(typeFilterBtnX + BUTTON_SIZE / 2, typeFilterBtnY, screenWidth);
            return true;
        }
        
        
        
        if (mouseX >= containerBtnX && mouseX < containerBtnX + BUTTON_SIZE &&
            mouseY >= containerBtnY && mouseY < containerBtnY + BUTTON_SIZE) {
            
            containerModePopup.toggle();
            
            int screenWidth = Minecraft.getInstance().screen != null ? Minecraft.getInstance().screen.width : 0;
            containerModePopup.positionFromButtonAbove(containerBtnX + BUTTON_SIZE / 2, containerBtnY, screenWidth);
            return true;
        }

        
        int recentSortBtnX = localMainGridOriginX + 3 - PAD_LEFT;
        int recentSortBtnY = y + PAD_TOP + 1;
        if (mouseX >= recentSortBtnX && mouseX < recentSortBtnX + BUTTON_SIZE
                && mouseY >= recentSortBtnY && mouseY < recentSortBtnY + BUTTON_SIZE) {
            recentSortAscending = !recentSortAscending;
            return true;
        }

        int recentSearchX = recentSortBtnX + BUTTON_SIZE + BUTTON_SPACING;
        int recentSearchY = y + PAD_TOP + 1;
        int recentSearchW = (x + 3 + recentCols * SLOT_SIZE) - recentSearchX;
        if (recentSearchW > SEARCH_INPUT_H) {
            boolean clickedRecentSearch = mouseX >= recentSearchX && mouseX < recentSearchX + recentSearchW
                    && mouseY >= recentSearchY && mouseY < recentSearchY + SEARCH_INPUT_H;
            if (clickedRecentSearch) {
                searchFocused = false;
                recentSearchFocused = true;
                recentSearchCursorBlink = System.currentTimeMillis();
                return true;
            } else if (recentSearchFocused) {
                recentSearchFocused = false;
            }
        }

        
        int searchX = containerBtnX + BUTTON_SIZE + BUTTON_SPACING;
        int searchY = y + PAD_TOP + 1;
        int searchW = (localMainGridOriginX + localMainGridWidth) - searchX;
        if (searchW > SEARCH_INPUT_H) {
            boolean clickedSearch = mouseX >= searchX && mouseX < searchX + searchW
                    && mouseY >= searchY && mouseY < searchY + SEARCH_INPUT_H;
            if (clickedSearch) {
                recentSearchFocused = false;
                searchFocused = true;
                searchCursorBlink = System.currentTimeMillis();
                return true;
            } else if (searchFocused) {
                searchFocused = false;
                
                StorageModule sm = CompositionRoot.get().module(StorageModule.class);
                if (sm != null) {
                    sm.setSearch(searchBuffer.toString());
                }
            }
        }
        
        
        if (typeFilterPopup.isOpen() && typeFilterPopup.contains((int) mouseX, (int) mouseY)) {
            return typeFilterPopup.handleClick((int) mouseX, (int) mouseY);
        }
        
        if (containerModePopup.isOpen() && containerModePopup.contains((int) mouseX, (int) mouseY)) {
            return containerModePopup.handleClick((int) mouseX, (int) mouseY);
        }
        
        if (scrollBar.handleClick(mouseX, mouseY, barX,
                originY + 6, gridVisibleH - 12)) {
            return true;
        }

        int localRecentGridOriginX = x + 3;
        int localRecentGridW = recentCols * SLOT_SIZE;
        int recentBarX = localRecentGridOriginX + localRecentGridW + 3;
        if (recentScrollBar.handleClick(mouseX, mouseY, recentBarX,
                originY + 6, gridVisibleH - 12)) {
            return true;
        }

        
        if (!contains((int) mouseX, (int) mouseY)) return false;
        int w = getWidth();

        
        List<RecentEntry> recentItems = getRecentItems();
        if (!recentItems.isEmpty()) {
            int relRecentX = (int) mouseX - localRecentGridOriginX;
            int relRecentY = (int) mouseY - originY;
            if (relRecentX >= 0 && relRecentY >= 0) {
                int recentCol = relRecentX / (SLOT_SIZE + SLOT_GAP);
                int recentRow = relRecentY / (SLOT_SIZE + SLOT_GAP);
                if (recentCol < recentCols && recentRow >= 0) {
                    int recentIdx = recentRow * recentCols + recentCol;
                    if (recentIdx < recentItems.size()) {
                        RecentEntry clickedRecent = recentItems.get(recentIdx);
                        if (!clickedRecent.preview().isEmpty()) {
                            selectedSlotIndex = -1;
                            currentSelectedItem = clickedRecent.preview().copy();
                            
                            String itemId = BuiltInRegistries.ITEM.getKey(clickedRecent.preview().getItem()).toString();
                            if (itemId != null) {
                                String label = clickedRecent.preview().getHoverName().getString();
                                recordItemSelection(itemId, clickedRecent.preview());
                                BuildingModule buildingModule = CompositionRoot.get().module(BuildingModule.class);
                                if (buildingModule != null) {
                                    buildingModule.selectItem(itemId, label, clickedRecent.preview());
                                }
                            }
                        }
                        return true;
                    }
                }
            }
        }

        
        int localMainGridCols = getCalcMainCols();
        int localRows = getCalcRows();
        int relX = (int) mouseX - localMainGridOriginX;
        int relY = (int) mouseY - originY + scrollBar.getScroll();
        if (relX < 0 || relY < 0) {
            return false;
        }
        int col = relX / (SLOT_SIZE + SLOT_GAP);
        int row = relY / (SLOT_SIZE + SLOT_GAP);
        if (col >= localMainGridCols) {
            return false;
        }
        int idx = row * localMainGridCols + col;
        if (idx >= slotEntries.size()) {
            return false;
        }
        
        
        
        int calculatedFrameHeight = localRows * (SLOT_SIZE + SLOT_GAP) - SLOT_GAP;
        int bottomY = originY + calculatedFrameHeight;
        if (mouseY < originY || mouseY >= bottomY) {
            return false;
        }
        
        if (selectedSlotIndex == idx) {
            selectedSlotIndex = -1;
            
            currentSelectedItem = ItemStack.EMPTY;
        } else {
            selectedSlotIndex = idx;
            
            SlotEntry clickedEntry = slotEntries.get(idx);
            currentSelectedItem = clickedEntry.stack().copy();
        }
        
        
        SlotEntry entry = slotEntries.get(idx);
        if (!entry.isFluid()) {
            
            String itemId = BuiltInRegistries.ITEM.getKey(entry.stack().getItem()).toString();
            String label = entry.stack().getHoverName().getString();
            recordItemSelection(itemId, entry.stack());
            
            
            BuildingModule buildingModule = CompositionRoot.get().module(BuildingModule.class);
            if (buildingModule != null) {
                buildingModule.selectItem(itemId, label, entry.stack());
            }
        } else {
            
            
            if (entry.originalEntry() instanceof FluidEntry originalFluidEntry) {
                String fluidId = originalFluidEntry.fluidId();
                String label = entry.stack().getHoverName().getString();
                
                
                BuildingModule buildingModule = CompositionRoot.get().module(BuildingModule.class);
                if (buildingModule != null) {
                    buildingModule.selectFluid(fluidId, label, entry.stack());
                }
            }
        }
        
        return true;
    }

    private void applySearch() {
        searchFocused = false;
        StorageModule sm = CompositionRoot.get().module(StorageModule.class);
        if (sm != null) {
            sm.setSearch(searchBuffer.toString());
        }
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (recentSearchFocused) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                recentSearchFocused = false;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (recentSearchCursorPos > 0 && recentSearchBuffer.length() > 0) {
                    recentSearchBuffer.deleteCharAt(recentSearchCursorPos - 1);
                    recentSearchCursorPos--;
                    recentSearchCursorBlink = System.currentTimeMillis();
                }
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_DELETE) {
                if (recentSearchCursorPos < recentSearchBuffer.length()) {
                    recentSearchBuffer.deleteCharAt(recentSearchCursorPos);
                    recentSearchCursorBlink = System.currentTimeMillis();
                }
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_LEFT) {
                recentSearchCursorPos = Math.max(0, recentSearchCursorPos - 1);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_RIGHT) {
                recentSearchCursorPos = Math.min(recentSearchBuffer.length(), recentSearchCursorPos + 1);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_HOME) {
                recentSearchCursorPos = 0;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_END) {
                recentSearchCursorPos = recentSearchBuffer.length();
                return true;
            }
            if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0 && keyCode == GLFW.GLFW_KEY_V) {
                String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
                if (clip != null && !clip.isEmpty()) {
                    recentSearchBuffer.insert(recentSearchCursorPos, clip);
                    recentSearchCursorPos += clip.length();
                    recentSearchCursorBlink = System.currentTimeMillis();
                }
                return true;
            }
            return false;
        }
        if (!searchFocused) return false;
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            applySearch();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            searchFocused = false;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            if (searchCursorPos > 0 && searchBuffer.length() > 0) {
                searchBuffer.deleteCharAt(searchCursorPos - 1);
                searchCursorPos--;
                searchCursorBlink = System.currentTimeMillis();
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DELETE) {
            if (searchCursorPos < searchBuffer.length()) {
                searchBuffer.deleteCharAt(searchCursorPos);
                searchCursorBlink = System.currentTimeMillis();
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_LEFT) {
            searchCursorPos = Math.max(0, searchCursorPos - 1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_RIGHT) {
            searchCursorPos = Math.min(searchBuffer.length(), searchCursorPos + 1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_HOME) {
            searchCursorPos = 0;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_END) {
            searchCursorPos = searchBuffer.length();
            return true;
        }
        if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0 && keyCode == GLFW.GLFW_KEY_V) {
            String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
            if (clip != null && !clip.isEmpty()) {
                searchBuffer.insert(searchCursorPos, clip);
                searchCursorPos += clip.length();
                searchCursorBlink = System.currentTimeMillis();
            }
            return true;
        }
        return false;
    }

    public boolean charTyped(char codePoint, int modifiers) {
        if (recentSearchFocused) {
            if (codePoint >= 32 && !Character.isISOControl(codePoint)) {
                recentSearchBuffer.insert(recentSearchCursorPos, codePoint);
                recentSearchCursorPos++;
                recentSearchCursorBlink = System.currentTimeMillis();
                return true;
            }
            return false;
        }
        if (!searchFocused) return false;
        if (codePoint >= 32 && !Character.isISOControl(codePoint)) {
            searchBuffer.insert(searchCursorPos, codePoint);
            searchCursorPos++;
            searchCursorBlink = System.currentTimeMillis();
            return true;
        }
        return false;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        if (scrollBar.isDragging()) {
            scrollBar.endDrag();
            return true;
        }
        if (recentScrollBar.isDragging()) {
            recentScrollBar.endDrag();
            return true;
        }
        return false;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button != 0) return false;
        int originY = getY() + PAD_TOP + GRID_TOP_OFFSET;
        int gridVisibleH = getHeight() - PAD_TOP * 2 - GRID_TOP_OFFSET;
        if (scrollBar.isDragging()) {
            return scrollBar.handleDrag(mouseY, originY + 6, gridVisibleH - 12);
        }
        if (recentScrollBar.isDragging()) {
            return recentScrollBar.handleDrag(mouseY, originY + 6, gridVisibleH - 12);
        }
        return false;
    }

    
    private void scrollToSelectedItem() {
        if (currentSelectedItem.isEmpty() || slotEntries.isEmpty()) {
            return;
        }
        
        
        int targetIndex = -1;
        for (int i = 0; i < slotEntries.size(); i++) {
            SlotEntry entry = slotEntries.get(i);
            if (ItemStack.isSameItemSameComponents(entry.stack(), currentSelectedItem)) {
                targetIndex = i;
                break;
            }
        }
        
        if (targetIndex == -1) {
            return; 
        }
        
        
        int targetRow = targetIndex / cols;
        
        
        int targetY = targetRow * (SLOT_SIZE + SLOT_GAP);
        
        
        int gridVisibleH = getHeight() - PAD_TOP * 2 - GRID_TOP_OFFSET;
        int rowsVisible = gridVisibleH / (SLOT_SIZE + SLOT_GAP);
        int centeredScroll = targetY - (rowsVisible / 2) * (SLOT_SIZE + SLOT_GAP);
        
        
        centeredScroll = Math.max(0, centeredScroll);
        centeredScroll = Math.min(scrollBar.getMaxScroll(), centeredScroll);
        
        
        startSmoothScrollAnimation(centeredScroll);
    }
    
    
    private void startSmoothScrollAnimation(double targetScrollPos) {
        this.targetScroll = targetScrollPos;
        this.animatedScroll = scrollBar.getScroll();
        this.animationStartTime = System.currentTimeMillis();
        this.isScrollingAnimated = true;
    }
    
    
    private void updateScrollAnimation() {
        if (!isScrollingAnimated) {
            return;
        }
        
        long currentTime = System.currentTimeMillis();
        float elapsed = (currentTime - animationStartTime) / 1000.0f * 20.0f; 
        
        if (elapsed >= ANIMATION_DURATION) {
            
            scrollBar.setScroll((int) targetScroll);
            animatedScroll = targetScroll;
            isScrollingAnimated = false;
            return;
        }
        
        
        float progress = elapsed / ANIMATION_DURATION;
        float easeOut = 1.0f - (float) Math.pow(1.0f - progress, 2); 
        
        animatedScroll = animatedScroll + (targetScroll - animatedScroll) * easeOut;
        
        
        scrollBar.setScroll((int) animatedScroll);
    }
    
    public void postRenderContent(GuiGraphics g) {
        
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) {
            renderTooltipOverlay(g, (int) getLastMouseX(), (int) getLastMouseY(),
                    mc.screen.width, mc.screen.height);
            
            typeFilterPopup.render(g, (int) getLastMouseX(), (int) getLastMouseY());
            containerModePopup.render(g, (int) getLastMouseX(), (int) getLastMouseY());
        }
    }
    
    
    public void renderTooltipOverlay(GuiGraphics g, int mouseX, int mouseY, int screenW, int screenH) {
        int x = getX(), y = getY();
        
        
        int itemDisplayX = x + PAD_LEFT;
        int itemDisplayY = y + PAD_TOP + 1;
        int itemDisplaySize = BUTTON_SIZE;
        
        
        int sortBtnX = calculateSortButtonX(x);
        int sortBtnY = y + PAD_TOP + 1;
        int sortBtnWidth = BUTTON_SIZE;
        int sortBtnHeight = BUTTON_SIZE;
        
        
        int orderBtnX = calculateOrderButtonX(x);
        int orderBtnY = y + PAD_TOP + 1;
        int orderBtnWidth = BUTTON_SIZE;
        int orderBtnHeight = BUTTON_SIZE;
        
        
        int typeFilterBtnX = calculateTypeFilterButtonX(x);
        int typeFilterBtnY = y + PAD_TOP + 1;
        int typeFilterBtnWidth = BUTTON_SIZE;
        int typeFilterBtnHeight = BUTTON_SIZE;
        
        
        int containerBtnX = calculateContainerButtonX(x);
        int containerBtnY = y + PAD_TOP + 1;
        int containerBtnWidth = BUTTON_SIZE;
        int containerBtnHeight = BUTTON_SIZE;
        
        
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
                    sortBtnX, sortBtnY, sortBtnWidth, sortBtnHeight,
                    text, screenW, screenH);
        }
        
        
        if (orderButtonTooltip.shouldRender()) {
            String text = Component.translatable("tooltip.rtsbuilding.rightdown.order_button").getString() + "\n" +
                         Component.translatable("tooltip.rtsbuilding.rightdown.order_button.desc").getString();
            renderTooltipAbove(g, orderButtonTooltip,
                    orderBtnX, orderBtnY, orderBtnWidth, orderBtnHeight,
                    text, screenW, screenH);
        }
        
        
        if (typeFilterButtonTooltip.shouldRender()) {
            String text = Component.translatable("tooltip.rtsbuilding.rightdown.type_filter_button").getString() + "\n" +
                         Component.translatable("tooltip.rtsbuilding.rightdown.type_filter_button.desc").getString();
            renderTooltipAbove(g, typeFilterButtonTooltip,
                    typeFilterBtnX, typeFilterBtnY, typeFilterBtnWidth, typeFilterBtnHeight,
                    text, screenW, screenH);
        }

        
        if (containerButtonTooltip.shouldRender()) {
            String text = Component.translatable("tooltip.rtsbuilding.rightdown.container_button").getString() + "\n" +
                         Component.translatable("tooltip.rtsbuilding.rightdown.container_button.desc").getString();
            renderTooltipAbove(g, containerButtonTooltip,
                    containerBtnX, containerBtnY, containerBtnWidth, containerBtnHeight,
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
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, alpha);
        SpriteRenderer.drawNineSliceFloatingPanel(g, tipX, tipY, tipW, tipH, false);
        
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

    
    private static final class TypeFilterPopup extends BasePopup {

        

        public record TypeFilterItem(Component label, Runnable action) {}

        private final TypeFilterItem[] items;
        private final boolean[] states;

        

        private boolean showItems;
        private boolean showFluids;
        
        
        
        @FunctionalInterface
        public interface OnFilterChangeListener {
            void onFilterChanged(boolean showItems, boolean showFluids);
        }
        
        private final OnFilterChangeListener listener;

        

        private static final ResourceLocation MODE_BUTTON_TEXTURE =
                ResourceLocation.tryParse("rtsbuilding:textures/gui/base/base_ui/base_ui_5.png");
        private static final int MODE_BTN_TEX_W = 32;
        private static final int MODE_BTN_TEX_H = 48;
        private static final int MODE_BTN_SIZE = 16;
        private static final int MODE_BTN_STATE_H = 16;
        private static final int BTN_TEXT_GAP = 4;

        public TypeFilterPopup(boolean showItems, boolean showFluids, OnFilterChangeListener listener) {
            this.showItems = showItems;
            this.showFluids = showFluids;
            this.listener = listener;

            
            this.items = new TypeFilterItem[]{
                new TypeFilterItem(Component.translatable("tooltip.rtsbuilding.rightdown.type_filter_item"), this::toggleItems),
                new TypeFilterItem(Component.translatable("tooltip.rtsbuilding.rightdown.type_filter_fluid"), this::toggleFluids)
            };

            
            this.states = new boolean[]{showItems, showFluids};

            
            var font = Minecraft.getInstance().font;
            int[] contentWidths = new int[items.length];
            for (int i = 0; i < items.length; i++) {
                contentWidths[i] = MODE_BTN_SIZE + BTN_TEXT_GAP + font.width(items[i].label().getString());
            }
            setItemContentWidths(contentWidths);

            initAnims(items.length);
        }

        

        public void setShowItems(boolean showItems) {
            this.showItems = showItems;
            this.states[0] = showItems;
        }

        public void setShowFluids(boolean showFluids) {
            this.showFluids = showFluids;
            this.states[1] = showFluids;
        }

        public boolean isShowItems() {
            return showItems;
        }

        public boolean isShowFluids() {
            return showFluids;
        }

        private void toggleItems() {
            showItems = !showItems;
            states[0] = showItems;
            if (listener != null) {
                listener.onFilterChanged(showItems, showFluids);
            }
        }

        private void toggleFluids() {
            showFluids = !showFluids;
            states[1] = showFluids;
            if (listener != null) {
                listener.onFilterChanged(showItems, showFluids);
            }
        }

        

        @Override
        protected int getItemCount() {
            return items.length;
        }

        @Override
        protected void renderItem(GuiGraphics g, int index, int itemY, float hoverT) {
            var font = Minecraft.getInstance().font;
            int textColor = hoverT > 0.5f ? ThemeManager.getHoverTextColor() : ThemeManager.getTextColor();
            String label = items[index].label().getString();
            int textX = x + getPadH();
            int textY = itemY + (getItemHeight() - font.lineHeight) / 2 + 1;
            TextRenderer.draw(g, label, textX, textY, textColor);

            int btnX = x + getPopupWidth() - getPadH() - MODE_BTN_SIZE;
            int btnY = itemY + (getItemHeight() - MODE_BTN_SIZE) / 2;

            boolean sel = states[index];
            boolean lightMode = ThemeManager.getInstance().isLightMode();
            if (sel) {
                g.blit(MODE_BUTTON_TEXTURE, btnX, btnY, MODE_BTN_SIZE, MODE_BTN_SIZE,
                        lightMode ? 16 : 0, 32, MODE_BTN_TEX_W / 2, MODE_BTN_STATE_H,
                        MODE_BTN_TEX_W, MODE_BTN_TEX_H);
            } else {
                int u = lightMode ? 16 : 0;
                CrossFadeRenderer.render(g, hoverT,
                        () -> g.blit(MODE_BUTTON_TEXTURE, btnX, btnY, MODE_BTN_SIZE, MODE_BTN_SIZE,
                                u, 0, MODE_BTN_TEX_W / 2, MODE_BTN_STATE_H,
                                MODE_BTN_TEX_W, MODE_BTN_TEX_H),
                        () -> g.blit(MODE_BUTTON_TEXTURE, btnX, btnY, MODE_BTN_SIZE, MODE_BTN_SIZE,
                                u, 16, MODE_BTN_TEX_W / 2, MODE_BTN_STATE_H,
                                MODE_BTN_TEX_W, MODE_BTN_TEX_H));
            }
        }

        @Override
        protected boolean onItemClick(int index) {
            
            if (items[index].action() != null) {
                items[index].action().run();
            }
            return true;
        }
    }

    
    private static final class ContainerModePopup extends BasePopup {

        

        public record ContainerModeItem(Component label, Runnable action) {}

        private final ContainerModeItem[] items;
        private final boolean[] states;

        

        private boolean showBidirectional;
        private boolean showExtractOnly;
        
        
        
        @FunctionalInterface
        public interface OnFilterChangeListener {
            void onFilterChanged(boolean showBidirectional, boolean showExtractOnly);
        }
        
        private final OnFilterChangeListener listener;

        

        private static final ResourceLocation MODE_BUTTON_TEXTURE =
                ResourceLocation.tryParse("rtsbuilding:textures/gui/base/base_ui/base_ui_5.png");
        private static final int MODE_BTN_TEX_W = 32;
        private static final int MODE_BTN_TEX_H = 48;
        private static final int MODE_BTN_SIZE = 16;
        private static final int MODE_BTN_STATE_H = 16;
        private static final int BTN_TEXT_GAP = 4;

        public ContainerModePopup(boolean showBidirectional, boolean showExtractOnly, OnFilterChangeListener listener) {
            this.showBidirectional = showBidirectional;
            this.showExtractOnly = showExtractOnly;
            this.listener = listener;

            
            this.items = new ContainerModeItem[]{
                new ContainerModeItem(Component.translatable("tooltip.rtsbuilding.rightdown.container_bidirectional"), this::toggleBidirectional),
                new ContainerModeItem(Component.translatable("tooltip.rtsbuilding.rightdown.container_extract"), this::toggleExtractOnly)
            };

            
            this.states = new boolean[]{showBidirectional, showExtractOnly};

            
            var font = Minecraft.getInstance().font;
            int[] contentWidths = new int[items.length];
            for (int i = 0; i < items.length; i++) {
                contentWidths[i] = MODE_BTN_SIZE + BTN_TEXT_GAP + font.width(items[i].label().getString());
            }
            setItemContentWidths(contentWidths);

            initAnims(items.length);
        }

        

        public void setShowBidirectional(boolean show) {
            this.showBidirectional = show;
            this.states[0] = show;
        }

        public void setShowExtractOnly(boolean show) {
            this.showExtractOnly = show;
            this.states[1] = show;
        }

        public boolean isShowBidirectional() {
            return showBidirectional;
        }

        public boolean isShowExtractOnly() {
            return showExtractOnly;
        }

        private void toggleBidirectional() {
            showBidirectional = !showBidirectional;
            states[0] = showBidirectional;
            if (listener != null) {
                listener.onFilterChanged(showBidirectional, showExtractOnly);
            }
        }

        private void toggleExtractOnly() {
            showExtractOnly = !showExtractOnly;
            states[1] = showExtractOnly;
            if (listener != null) {
                listener.onFilterChanged(showBidirectional, showExtractOnly);
            }
        }

        

        @Override
        protected int getItemCount() {
            return items.length;
        }

        @Override
        protected void renderItem(GuiGraphics g, int index, int itemY, float hoverT) {
            var font = Minecraft.getInstance().font;
            int textColor = hoverT > 0.5f ? ThemeManager.getHoverTextColor() : ThemeManager.getTextColor();
            String label = items[index].label().getString();
            int textX = x + getPadH();
            int textY = itemY + (getItemHeight() - font.lineHeight) / 2 + 1;
            TextRenderer.draw(g, label, textX, textY, textColor);

            int btnX = x + getPopupWidth() - getPadH() - MODE_BTN_SIZE;
            int btnY = itemY + (getItemHeight() - MODE_BTN_SIZE) / 2;

            boolean sel = states[index];
            boolean lightMode = ThemeManager.getInstance().isLightMode();
            if (sel) {
                g.blit(MODE_BUTTON_TEXTURE, btnX, btnY, MODE_BTN_SIZE, MODE_BTN_SIZE,
                        lightMode ? 16 : 0, 32, MODE_BTN_TEX_W / 2, MODE_BTN_STATE_H,
                        MODE_BTN_TEX_W, MODE_BTN_TEX_H);
            } else {
                int u = lightMode ? 16 : 0;
                CrossFadeRenderer.render(g, hoverT,
                        () -> g.blit(MODE_BUTTON_TEXTURE, btnX, btnY, MODE_BTN_SIZE, MODE_BTN_SIZE,
                                u, 0, MODE_BTN_TEX_W / 2, MODE_BTN_STATE_H,
                                MODE_BTN_TEX_W, MODE_BTN_TEX_H),
                        () -> g.blit(MODE_BUTTON_TEXTURE, btnX, btnY, MODE_BTN_SIZE, MODE_BTN_SIZE,
                                u, 16, MODE_BTN_TEX_W / 2, MODE_BTN_STATE_H,
                                MODE_BTN_TEX_W, MODE_BTN_TEX_H));
            }
        }

        @Override
        protected boolean onItemClick(int index) {
            
            if (items[index].action() != null) {
                items[index].action().run();
            }
            return true;
        }
    }
}
