package com.rtsbuilding.rtsbuilding.client.presentation.plugin.grid;

import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class GridState {

    public final List<SlotEntry> slotEntries = new ArrayList<>();
    public int selectedSlotIndex = -1;
    public ItemStack currentSelectedItem = ItemStack.EMPTY;

    public SortType currentSortType = SortType.NAME;
    public boolean reverseSortOrder = false;

    public boolean showItems = true;
    public boolean showFluids = true;
    public boolean showBidirectional = true;
    public boolean showExtractOnly = true;

    public boolean searchFocused;
    public final StringBuilder searchBuffer = new StringBuilder();
    public int searchCursorPos;
    public long searchCursorBlink;

    public boolean recentSortAscending = true;
    public boolean recentSearchFocused;
    public final StringBuilder recentSearchBuffer = new StringBuilder();
    public int recentSearchCursorPos;
    public long recentSearchCursorBlink;

    public int cols;
    public int rows;
    public int mainGridOriginX;
    public int mainGridCols;
    public int cachedMainGridWidth;
    public int recentGridOriginX;
    public int recentGridW;
    public int recentCols;
    public int tooltipSlotIndex = -1;

    public final Map<String, Integer> itemSelectCounts = new HashMap<>();
    public final Map<String, ItemStack> itemSelectPreviews = new HashMap<>();

    public boolean slotEntriesDirty = true;
    public int lastRevision = -1;
    public SortType lastSortType = SortType.NAME;
    public boolean lastReverseSortOrder = false;
    public boolean lastShowItems = true;
    public boolean lastShowFluids = true;
    public boolean lastShowBidirectional = true;
    public boolean lastShowExtractOnly = true;
    public int lastScroll;

    public double targetScroll;
    public double animatedScroll;
    public long animationStartTime;
    public boolean isScrollingAnimated;
    public static final float ANIMATION_DURATION = 10.0f;
}
