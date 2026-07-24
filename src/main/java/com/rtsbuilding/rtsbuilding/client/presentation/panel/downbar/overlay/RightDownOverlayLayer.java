package com.rtsbuilding.rtsbuilding.client.presentation.panel.downbar.overlay;

import com.mojang.blaze3d.systems.RenderSystem;
import com.rtsbuilding.rtsbuilding.client.domain.state.FluidEntry;
import com.rtsbuilding.rtsbuilding.client.domain.state.StorageEntry;
import com.rtsbuilding.rtsbuilding.client.infrastructure.di.CompositionRoot;
import com.rtsbuilding.rtsbuilding.client.infrastructure.module.building.BuildingModule;
import com.rtsbuilding.rtsbuilding.client.infrastructure.module.storage.StorageModule;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.base.component.ScrollBar;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.base.overlay.DownOverlayLayer;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.downbar.render.GridSlotRenderer;
import com.rtsbuilding.rtsbuilding.client.presentation.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.util.render.SpriteRenderer;
import com.rtsbuilding.rtsbuilding.client.util.render.TextRenderer;
import com.rtsbuilding.rtsbuilding.client.util.render.model.NineSliceRegion;
import com.rtsbuilding.rtsbuilding.client.util.render.model.SpriteRegion;
import com.rtsbuilding.rtsbuilding.client.util.render.model.TextureInfo;
import com.rtsbuilding.rtsbuilding.client.util.state.TooltipController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

import java.util.ArrayList;
import java.util.List;

import static com.rtsbuilding.rtsbuilding.client.presentation.panel.downbar.render.GridSlotRenderer.SLOT_SIZE;


public final class RightDownOverlayLayer extends DownOverlayLayer {

    
    
    public RightDownOverlayLayer() {
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
    
    

    
    private static final int SLOT_GAP = 0;
    
    private static final int PAD_LEFT = 58;
    private static final int PAD_TOP = 2;
    
    private static final int GRID_TOP_OFFSET = 20;
    
    private static final int SCROLLBAR_W = 7;
    
    private static final int RIGHT_MARGIN = 4;

    

    
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
    
    private int tooltipSlotIndex = -1;

    

    
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
    
    

    @Override
    protected void renderContent(GuiGraphics g) {
        
        updateScrollAnimation();
        
        
        StorageModule sm = CompositionRoot.get().module(StorageModule.class);
        if (sm == null) return;

        
        checkAndRebuildIfDirty(sm);
        boolean hasStorage = !sm.getEntries().isEmpty() || !sm.getFluidEntries().isEmpty();
        if (slotEntries.isEmpty() && !hasStorage) {
            renderEmptyHint(g);
            return;
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

        
        int usableW = w - PAD_LEFT - SCROLLBAR_W - RIGHT_MARGIN;
        cols = Math.max(1, (usableW + SLOT_GAP) / (SLOT_SIZE + SLOT_GAP));
        rows = Math.max(1, (h - PAD_TOP - GRID_TOP_OFFSET) / (SLOT_SIZE + SLOT_GAP) + 2);
        int itemRows = (slotEntries.size() + cols - 1) / cols;
        int visibleH = h - PAD_TOP * 2;
        int gridVisibleH = visibleH - GRID_TOP_OFFSET;
        int gridH = itemRows * (SLOT_SIZE + SLOT_GAP) - SLOT_GAP;

        scrollBar.setContent(gridH, gridVisibleH + 6);
        int scroll = scrollBar.getScroll();

        int originX = calculateGridOriginX(x);
        int originY = calculateGridOriginY(y);
        int gridW = cols * (SLOT_SIZE + SLOT_GAP) - SLOT_GAP;
        int frameH = rows * (SLOT_SIZE + SLOT_GAP) - SLOT_GAP;
        int localMouseX = getLastMouseX();
        int localMouseY = getLastMouseY();
        int hoveredSlot = findHoveredSlot(localMouseX, localMouseY, originX, originY, scroll);
        this.tooltipSlotIndex = hoveredSlot;

        
        g.flush();
        Screen screen = mc.screen;
        int scissorBottomY = originY + frameH;
        if (screen instanceof BuilderScreen bs) {
            bs.enableRtsScissor(g, originX, originY + 1, originX + gridW, scissorBottomY);
        } else {
            g.enableScissor(originX, originY + 1, originX + gridW, scissorBottomY);
        }

        
        
        SpriteRenderer.drawTiledGrid(g, GridSlotRenderer.SLOT_NORMAL, slotThemeOffset,
                originX, originY, SLOT_SIZE, SLOT_SIZE, SLOT_GAP,
                cols, Math.max(rows, itemRows), scroll, originY, scissorBottomY);

        
        g.flush();

        
        for (int i = 0; i < slotEntries.size(); i++) {
            int col = i % cols;
            int row = i / cols;
            int slotX = originX + col * (SLOT_SIZE + SLOT_GAP);
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

            boolean isSelected = i == selectedSlotIndex && selectedSlotIndex < slotEntries.size();
            GridSlotRenderer.drawOverlay(g, slotX, slotY, hovered, isSelected, slotThemeOffset);
        }

        
        RenderSystem.clear(256, Minecraft.ON_OSX);

        if (selectedSlotIndex >= slotEntries.size() && !slotEntries.isEmpty()) {
            selectedSlotIndex = -1;
        }

        
        g.flush();
        g.disableScissor();

        
        SpriteRenderer.drawNineSlice(g, OVERLAY_NINE_SLICE, overlayThemeOffset, originX, originY, gridW, frameH);

        
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
        int originY = y + PAD_TOP + GRID_TOP_OFFSET;
        int gridVisibleH = h - PAD_TOP * 2 - GRID_TOP_OFFSET;
        int barX = x + getWidth() - SCROLLBAR_W - RIGHT_MARGIN;
        scrollBar.render(g, barX, originY + 6, gridVisibleH - 12);
    }

    

    
    private int findHoveredSlot(int mx, int my, int originX, int originY, int scroll) {
        if (!contains(mx, my)) return -1;
        int relX = mx - originX;
        int relY = my - originY + scroll;
        if (relX < 0 || relY < 0) return -1;
        int col = relX / (SLOT_SIZE + SLOT_GAP);
        int row = relY / (SLOT_SIZE + SLOT_GAP);
        if (col >= cols || row >= rows) return -1;
        int idx = row * cols + col;
        if (idx >= slotEntries.size()) return -1;
        
        
        
        int calculatedFrameHeight = rows * (SLOT_SIZE + SLOT_GAP) - SLOT_GAP;
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

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!contains((int) mouseX, (int) mouseY)) return false;
        return scrollBar.handleScroll(scrollY);
    }

    @Override
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
        int barX = x + getWidth() - SCROLLBAR_W - RIGHT_MARGIN;
        
        
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

        
        if (!contains((int) mouseX, (int) mouseY)) return false;
        int w = getWidth();
        int originX = x + PAD_LEFT;
        int usableW = w - PAD_LEFT - SCROLLBAR_W - RIGHT_MARGIN;
        int cols = Math.max(1, (usableW + SLOT_GAP) / (SLOT_SIZE + SLOT_GAP));
        int relX = (int) mouseX - originX;
        int relY = (int) mouseY - originY + scrollBar.getScroll();
        if (relX < 0 || relY < 0) {
            return false;
        }
        int col = relX / (SLOT_SIZE + SLOT_GAP);
        int row = relY / (SLOT_SIZE + SLOT_GAP);
        if (col >= cols) {
            return false;
        }
        int idx = row * cols + col;
        if (idx >= slotEntries.size()) {
            return false;
        }
        
        
        
        int calculatedFrameHeight = rows * (SLOT_SIZE + SLOT_GAP) - SLOT_GAP;
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

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        if (scrollBar.isDragging()) {
            scrollBar.endDrag();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button != 0) return false;
        if (scrollBar.isDragging()) {
            int originY = getY() + PAD_TOP + GRID_TOP_OFFSET;
            int gridVisibleH = getHeight() - PAD_TOP * 2 - GRID_TOP_OFFSET;
            return scrollBar.handleDrag(mouseY, originY, gridVisibleH);
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
    
    @Override
    protected void postRenderContent(GuiGraphics g) {
        
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
}
