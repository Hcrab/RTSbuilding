package com.rtsbuilding.rtsbuilding.client.presentation.panel.downbar.overlay;
import com.rtsbuilding.rtsbuilding.client.infrastructure.di.CompositionRoot;

import com.rtsbuilding.rtsbuilding.client.kernel.RtsClientKernel;
import com.rtsbuilding.rtsbuilding.client.infrastructure.module.building.BuildingModule;
import com.rtsbuilding.rtsbuilding.client.infrastructure.module.storage.StorageModule;
import com.rtsbuilding.rtsbuilding.client.domain.state.FluidEntry;
import com.rtsbuilding.rtsbuilding.client.domain.state.StorageEntry;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.base.component.ScrollBar;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.base.overlay.DownOverlayLayer;
import com.rtsbuilding.rtsbuilding.client.presentation.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.util.render.SpriteRenderer;
import com.rtsbuilding.rtsbuilding.client.util.render.TextRenderer;
import com.rtsbuilding.rtsbuilding.client.util.render.model.NineSliceRegion;
import com.rtsbuilding.rtsbuilding.client.util.render.model.SpriteRegion;
import com.rtsbuilding.rtsbuilding.client.util.render.model.TextureInfo;
import com.rtsbuilding.rtsbuilding.client.util.state.TooltipController;
import com.mojang.blaze3d.systems.RenderSystem;
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
import com.rtsbuilding.rtsbuilding.client.presentation.panel.downbar.render.GridSlotRenderer;
import static com.rtsbuilding.rtsbuilding.client.presentation.panel.downbar.render.GridSlotRenderer.SLOT_SIZE;
import java.util.List;

/**
 * 下栏右嵌层——以网格统一显示绑定容器内的物品与流体。
 *
 * <p>完全参考 AE2 ME 终端的网格渲染方式：等间距格子排列、物品图标居中显示、
 * 数量文本以缩放方式渲染于格子右下角（参考 {@code StackSizeRenderer} 的 0.666x 缩放逻辑）、
 * 超过可视区域时右侧出现纵向滚动条。
 *
 * <p>物品与流体合并为同一连续列表，按「物品 → 流体」顺序平铺显示。
 */
public final class RightDownOverlayLayer extends DownOverlayLayer {

    // ======================== 构造函数 ========================
    
    public RightDownOverlayLayer() {
        this.typeFilterPopup = new TypeFilterPopup(showItems, showFluids, (items, fluids) -> onTypeFilterChanged(items, fluids));
    }
    
    private void onTypeFilterChanged(boolean showItems, boolean showFluids) {
        // 检查状态是否实际改变，避免不必要的重建
        boolean stateChanged = this.showItems != showItems || this.showFluids != showFluids;
        
        this.showItems = showItems;
        this.showFluids = showFluids;
        
        // 状态变化时标记脏，渲染循环中统一重建
        if (stateChanged) {
            slotEntriesDirty = true;
        }
    }
    
    // ======================== 布局常量 ========================

    /** 格子之间的间距 */
    private static final int SLOT_GAP = 0;
    /** 内边距（距 overlay 左/上边缘） */
    private static final int PAD_LEFT = 58;
    private static final int PAD_TOP = 2;
    /** 网格起始绘制高度额外下移量，增加与嵌层顶部的视觉呼吸空间 */
    private static final int GRID_TOP_OFFSET = 20;
    /** 右侧为滚动条预留的宽度 */
    private static final int SCROLLBAR_W = 7;
    /** 滚动条右侧与嵌层右边缘的间距 */
    private static final int RIGHT_MARGIN = 4;

    // ======================== 按钮尺寸常量 ========================

    /** 选择按钮和排序按钮的尺寸 */
    private static final int BUTTON_SIZE = 18;
    /** 选择按钮和排序按钮之间的间距 */
    private static final int BUTTON_SPACING = 1;

    // ======================== 网格外围装饰贴图（slots_overlay.png）=======================

    /** slots_overlay.png：32×16，水平双主题，每个半区 16×16，九宫格边框 2px */
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

    // ======================== 无物品贴图（nothing.png）=======================

    /** nothing.png：32×16，水平双主题，用于在没有选择物品时显示 */
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

    // ======================== 排序按钮贴图（base_ui_2.png 和 sort.png）=======================

    /** base_ui_2.png：32×48，水平双主题，垂直 0-16=正常，16-32=悬浮，32-48=未使用 */
    private static final ResourceLocation SORT_BTN_BG_TEXTURE = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/base/base_ui/base_ui_2.png");
    private static final int SORT_BTN_BG_TEX_W = 32;
    private static final int SORT_BTN_BG_TEX_H = 48;
    private static final int SORT_BTN_BG_STATE_H = 16;
    private static final TextureInfo SORT_BTN_BG_TEX_INFO = new TextureInfo(
            SORT_BTN_BG_TEXTURE, SORT_BTN_BG_TEX_W, SORT_BTN_BG_TEX_H,
            TextureInfo.ThemeLayout.HORIZONTAL_PAIR,
            TextureInfo.FilterMode.PIXEL);
    /** 正常态精灵（v=0~16，半区宽=16） */
    private static final SpriteRegion SORT_BTN_NORMAL = new SpriteRegion(
            SORT_BTN_BG_TEX_INFO, 0, 0, SORT_BTN_BG_TEX_W / 2, SORT_BTN_BG_STATE_H);
    /** 悬浮态精灵（v=16~32） */
    private static final SpriteRegion SORT_BTN_HOVER = new SpriteRegion(
            SORT_BTN_BG_TEX_INFO, 0, SORT_BTN_BG_STATE_H, SORT_BTN_BG_TEX_W / 2, SORT_BTN_BG_STATE_H);

    /** sort.png：32×48，水平双主题，垂直 0-16=名称排序，16-32=数量排序，32-48=模组排序 */
    private static final ResourceLocation SORT_ICON_TEXTURE = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/down/sort.png");
    private static final int SORT_ICON_TEX_W = 32;
    private static final int SORT_ICON_TEX_H = 48;
    private static final int SORT_ICON_TYPE_H = 16;
    private static final TextureInfo SORT_ICON_TEX_INFO = new TextureInfo(
            SORT_ICON_TEXTURE, SORT_ICON_TEX_W, SORT_ICON_TEX_H,
            TextureInfo.ThemeLayout.HORIZONTAL_PAIR,
            TextureInfo.FilterMode.PIXEL);
    /** 名称排序图标精灵（v=0~16） */
    private static final SpriteRegion SORT_NAME_ICON = new SpriteRegion(
            SORT_ICON_TEX_INFO, 0, 0, SORT_ICON_TEX_W / 2, SORT_ICON_TYPE_H);
    /** 数量排序图标精灵（v=16~32） */
    private static final SpriteRegion SORT_COUNT_ICON = new SpriteRegion(
            SORT_ICON_TEX_INFO, 0, SORT_ICON_TYPE_H, SORT_ICON_TEX_W / 2, SORT_ICON_TYPE_H);
    /** 模组排序图标精灵（v=32~48） */
    private static final SpriteRegion SORT_MOD_ICON = new SpriteRegion(
            SORT_ICON_TEX_INFO, 0, SORT_ICON_TYPE_H * 2, SORT_ICON_TEX_W / 2, SORT_ICON_TYPE_H);

    // ======================== 升降序按钮贴图（sort_order.png）=======================

    /** sort_order.png：32×32，水平双主题，垂直 0-16=升序，16-32=降序 */
    private static final ResourceLocation ORDER_BTN_TEXTURE = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/down/sort_order.png");
    private static final int ORDER_BTN_TEX_W = 32;
    private static final int ORDER_BTN_TEX_H = 32;
    private static final int ORDER_BTN_TYPE_H = 16;
    private static final TextureInfo ORDER_BTN_TEX_INFO = new TextureInfo(
            ORDER_BTN_TEXTURE, ORDER_BTN_TEX_W, ORDER_BTN_TEX_H,
            TextureInfo.ThemeLayout.HORIZONTAL_PAIR,
            TextureInfo.FilterMode.PIXEL);
    /** 升序图标精灵（v=0~16） */
    private static final SpriteRegion ORDER_ASC_ICON = new SpriteRegion(
            ORDER_BTN_TEX_INFO, 0, 0, ORDER_BTN_TEX_W / 2, ORDER_BTN_TYPE_H);
    /** 降序图标精灵（v=16~32） */
    private static final SpriteRegion ORDER_DESC_ICON = new SpriteRegion(
            ORDER_BTN_TEX_INFO, 0, ORDER_BTN_TYPE_H, ORDER_BTN_TEX_W / 2, ORDER_BTN_TYPE_H);

    // ======================== 类型过滤按钮贴图（type.png）=======================

    /** type.png：32×16，水平双主题，垂直 0-16=完整图标 */
    private static final ResourceLocation TYPE_FILTER_TEXTURE = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/down/type.png");
    private static final int TYPE_FILTER_TEX_W = 32;
    private static final int TYPE_FILTER_TEX_H = 16;
    private static final int TYPE_FILTER_TYPE_H = 16;
    private static final TextureInfo TYPE_FILTER_TEX_INFO = new TextureInfo(
            TYPE_FILTER_TEXTURE, TYPE_FILTER_TEX_W, TYPE_FILTER_TEX_H,
            TextureInfo.ThemeLayout.HORIZONTAL_PAIR,
            TextureInfo.FilterMode.PIXEL);
    /** 物品图标精灵（v=0~16，左半区） */
    private static final SpriteRegion TYPE_ITEM_ICON = new SpriteRegion(
            TYPE_FILTER_TEX_INFO, 0, 0, TYPE_FILTER_TEX_W / 2, TYPE_FILTER_TYPE_H);
    /** 流体图标精灵（v=0~16，右半区） */
    private static final SpriteRegion TYPE_FLUID_ICON = new SpriteRegion(
            TYPE_FILTER_TEX_INFO, TYPE_FILTER_TEX_W / 2, 0, TYPE_FILTER_TEX_W / 2, TYPE_FILTER_TYPE_H);

    // ======================== 颜色 ========================
    /** 无数据时提示文本颜色 */
    private static final int HINT_COLOR = 0x60_FFFFFF;

    // ======================== 组件 ========================

    private final ScrollBar scrollBar = new ScrollBar();

    // ======================== 排序相关 ========================

    /** 排序类型枚举 */
    public enum SortType {
        NAME("Name"),      // 名称排序
        COUNT("Count"),    // 数量排序
        MOD("Mod");       // 模组排序
        
        private final String displayName;
        
        SortType(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }

    /** 当前排序类型 */
    private SortType currentSortType = SortType.NAME;
    
    /** 是否反转排序顺序 */
    private boolean reverseSortOrder = false;

    // ======================== 显示过滤控制 ========================

    /** 是否显示物品 */
    private boolean showItems = true;
    /** 是否显示流体 */
    private boolean showFluids = true;

    // ======================== 弹出菜单 ========================

    /** 类型过滤弹出菜单 */
    private final TypeFilterPopup typeFilterPopup;

    // ======================== 悬浮提示控制器 ========================

    /** 当前选中物品显示区域的悬浮提示控制器 */
    private final TooltipController currentItemTooltip = TooltipController.builder().direction(TooltipController.Direction.ABOVE).build();
    /** 排序按钮的悬浮提示控制器 */
    private final TooltipController sortButtonTooltip = TooltipController.builder().direction(TooltipController.Direction.ABOVE).build();
    /** 升降序按钮的悬浮提示控制器 */
    private final TooltipController orderButtonTooltip = TooltipController.builder().direction(TooltipController.Direction.ABOVE).build();
    /** 类型过滤按钮的悬浮提示控制器 */
    private final TooltipController typeFilterButtonTooltip = TooltipController.builder().direction(TooltipController.Direction.ABOVE).build();

    /** 当前选中的格子索引（-1=无选中），鼠标点击切换 */
    private int selectedSlotIndex = -1;

    /** 当前选中的物品栈（用于在网格上方显示） */
    private ItemStack currentSelectedItem = ItemStack.EMPTY;

    /** 当前帧合并后的显示条目列表（物品+流体） */
    private final List<SlotEntry> slotEntries = new ArrayList<>();
    /** 当前帧的列数 */
    private int cols;
    /** 当前帧的行数 */
    private int rows;
    /** 当前帧悬浮的格子索引，用于外部渲染 tooltip */
    private int tooltipSlotIndex = -1;

    // ======================== 脏标记与缓存（AE2 增量更新模式）=======================

    /** slotEntries 是否需要重建 */
    private boolean slotEntriesDirty = true;
    /** 上次重建时的数据版本号 */
    private int lastRevision = -1;
    /** 上次重建时的排序类型 */
    private SortType lastSortType = SortType.NAME;
    /** 上次重建时的排序顺序 */
    private boolean lastReverseSortOrder = false;
    /** 上次重建时的物品显示状态 */
    private boolean lastShowItems = true;
    /** 上次重建时的流体显示状态 */
    private boolean lastShowFluids = true;

    /** 上次重建时的滚动位置 */
    private int lastScroll = 0;

    // ======================== 平滑滚动动画相关 ========================

    /** 动画持续时间（以刻为单位）*/
    private static final float ANIMATION_DURATION = 10.0f;
    
    /** 目标滚动位置（用于动画） */
    private double targetScroll = 0;
    
    /** 当前滚动位置（用于动画） */
    private double animatedScroll = 0;
    
    /** 动画开始时间 */
    private long animationStartTime = 0L;
    
    /** 是否正在执行滚动动画 */
    private boolean isScrollingAnimated = false;

    /**
     * 获取当前悬浮格子的物品堆，供 BuilderScreen 在缩放通道外渲染 tooltip。
     *
     * @return 悬浮物品的 ItemStack，无悬浮时返回空栈
     */
    public ItemStack getHoveredSlotStack() {
        if (tooltipSlotIndex == -2) {
            // 返回当前选中的物品（当鼠标悬停在其上方时）
            return currentSelectedItem;
        }
        if (tooltipSlotIndex < 0 || tooltipSlotIndex >= slotEntries.size()) return ItemStack.EMPTY;
        return slotEntries.get(tooltipSlotIndex).stack();
    }

    // ======================== 内部数据结构 ========================

    /**
     * 网格中每个槽位的数据封装。
     */
    /**
     * @param sortName 全小写名称（预计算，避免排序时重复调用 getHoverName）
     * @param sortMod  命名空间（预计算，避免排序时重复查 BuiltInRegistries）
     */
    private record SlotEntry(ItemStack stack, long count, boolean isFluid, Object originalEntry,
                             String sortName, String sortMod) {
    }
    
    /**
     * 循环切换排序类型
     */
    private void cycleSortType() {
        switch (currentSortType) {
            case NAME -> currentSortType = SortType.COUNT;
            case COUNT -> currentSortType = SortType.MOD;
            case MOD -> currentSortType = SortType.NAME;
        }
        
        // 标记脏，渲染循环中统一重建
        slotEntriesDirty = true;
    }
    
    /**
     * 切换排序顺序（升序/降序）
     */
    private void toggleSortOrder() {
        reverseSortOrder = !reverseSortOrder;
        
        // 标记脏，渲染循环中统一重建
        slotEntriesDirty = true;
    }
    
    /**
     * 切换类型过滤弹出菜单
     */
    private void toggleTypeFilter() {
        typeFilterPopup.toggle();
    }
    
    // ======================== 核心渲染入口 ========================

    @Override
    protected void renderContent(GuiGraphics g) {
        // ---- 更新滚动动画状态 ----
        updateScrollAnimation();
        
        // ---- 获取数据源 ----
        StorageModule sm = CompositionRoot.get().module(StorageModule.class);
        if (sm == null) return;

        // ---- 脏标记检查：仅在数据或过滤条件变更时重建列表 ----
        checkAndRebuildIfDirty(sm);
        if (slotEntries.isEmpty()) {
            renderEmptyHint(g);
            return;
        }

        int x = getX(), y = getY(), w = getWidth(), h = getHeight();

        // ---- Pre-compute theme offsets (theme doesn't change mid-frame) ----
        int slotThemeOffset = SpriteRenderer.getThemeOffset(GridSlotRenderer.SLOT_NORMAL);
        int overlayThemeOffset = SpriteRenderer.getNineSliceThemeOffset(OVERLAY_NINE_SLICE);

        // ---- 获取 Minecraft 实例 ----
        Minecraft mc = Minecraft.getInstance();
        
        // ---- 检测鼠标是否悬停在当前选中物品上（优先检测）----
        int mouseX = (int) getLastMouseX();
        int mouseY = (int) getLastMouseY();
        int itemDisplayX = x + PAD_LEFT;
        int itemDisplayY = y + PAD_TOP + 1;
        int itemDisplaySize = BUTTON_SIZE;
        boolean isHoveringOverCurrentSelection = mouseX >= itemDisplayX && mouseX < itemDisplayX + itemDisplaySize
                && mouseY >= itemDisplayY && mouseY < itemDisplayY + itemDisplaySize;

        currentItemTooltip.update(isHoveringOverCurrentSelection, false);

        // 绘制选中物品背景框
        SpriteRenderer.drawSprite(g, isHoveringOverCurrentSelection ? SORT_BTN_HOVER : SORT_BTN_NORMAL,
                slotThemeOffset, itemDisplayX, itemDisplayY, itemDisplaySize, itemDisplaySize);

        if (!currentSelectedItem.isEmpty()) {
            RenderSystem.disableDepthTest();
            var pose = g.pose();
            pose.pushPose();
            pose.translate(itemDisplayX + 1, itemDisplayY + 1, 0);
            g.renderItem(currentSelectedItem, 0, 0);
            pose.popPose();
        } else {
            RenderSystem.disableDepthTest();
            int iconWidth = NOTHING_TEX_W / 2;
            int iconHeight = NOTHING_TEX_H;
            int iconOffsetX = (itemDisplaySize - iconWidth) / 2;
            int iconOffsetY = (itemDisplaySize - iconHeight) / 2;
            SpriteRenderer.drawSprite(g, NOTHING_SPRITE, slotThemeOffset,
                    itemDisplayX + iconOffsetX, itemDisplayY + iconOffsetY, iconWidth, iconHeight);
        }

        // ---- 排序按钮 ----
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

        // ---- 升降序按钮 ----
        int orderBtnX = calculateOrderButtonX(x);
        int orderBtnY = y + PAD_TOP + 1;
        boolean isHoveringOverOrderBtn = mouseX >= orderBtnX && mouseX < orderBtnX + BUTTON_SIZE
                && mouseY >= orderBtnY && mouseY < orderBtnY + BUTTON_SIZE;
        orderButtonTooltip.update(isHoveringOverOrderBtn, false);

        SpriteRenderer.drawSprite(g, isHoveringOverOrderBtn ? SORT_BTN_HOVER : SORT_BTN_NORMAL,
                slotThemeOffset, orderBtnX, orderBtnY, BUTTON_SIZE, BUTTON_SIZE);
        SpriteRenderer.drawSprite(g, reverseSortOrder ? ORDER_DESC_ICON : ORDER_ASC_ICON,
                slotThemeOffset, orderBtnX, orderBtnY, BUTTON_SIZE, BUTTON_SIZE);

        // ---- 类型过滤按钮 ----
        int typeFilterBtnX = calculateTypeFilterButtonX(x);
        int typeFilterBtnY = y + PAD_TOP + 1;
        boolean isHoveringOverTypeFilterBtn = mouseX >= typeFilterBtnX && mouseX < typeFilterBtnX + BUTTON_SIZE
                && mouseY >= typeFilterBtnY && mouseY < typeFilterBtnY + BUTTON_SIZE;
        typeFilterButtonTooltip.update(isHoveringOverTypeFilterBtn, false);

        SpriteRenderer.drawSprite(g, isHoveringOverTypeFilterBtn ? SORT_BTN_HOVER : SORT_BTN_NORMAL,
                slotThemeOffset, typeFilterBtnX, typeFilterBtnY, BUTTON_SIZE, BUTTON_SIZE);
        SpriteRenderer.drawSprite(g, showFluids && !showItems ? TYPE_FLUID_ICON : TYPE_ITEM_ICON,
                slotThemeOffset, typeFilterBtnX, typeFilterBtnY, BUTTON_SIZE, BUTTON_SIZE);

        // ---- 网格布局计算 ----
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

        // ---- 启用 GPU Scissor 裁剪网格区域 ----
        g.flush();
        Screen screen = mc.screen;
        int scissorBottomY = originY + frameH;
        if (screen instanceof BuilderScreen bs) {
            bs.enableRtsScissor(g, originX, originY + 1, originX + gridW, scissorBottomY);
        } else {
            g.enableScissor(originX, originY + 1, originX + gridW, scissorBottomY);
        }

        // ---- 批量网格平铺背景（单次 getBuffer + 单次 batch 提交）----
        // 使用 itemRows（数据总行数）而非 rows（可视行数），确保滚动时超出可视区的格子也有背景
        SpriteRenderer.drawTiledGrid(g, GridSlotRenderer.SLOT_NORMAL, slotThemeOffset,
                originX, originY, SLOT_SIZE, SLOT_SIZE, SLOT_GAP,
                cols, Math.max(rows, itemRows), scroll, originY, scissorBottomY);

        // ---- flush 背景批次，确保物品渲染在正确的渲染层级 ----
        g.flush();

        // ---- 第二遍：逐格渲染物品图标 + 数量文字 + 覆盖层 ----
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
            if (count > 1) {
                Font font = IClientItemExtensions.of(stack).getFont(stack, IClientItemExtensions.FontContext.ITEM_COUNT);
                if (font == null) font = mc.font;
                GridSlotRenderer.drawAmountText(g, font, count, slotX, slotY, entry.isFluid());
            }

            boolean isSelected = i == selectedSlotIndex && selectedSlotIndex < slotEntries.size();
            GridSlotRenderer.drawOverlay(g, slotX, slotY, hovered, isSelected, slotThemeOffset);
        }

        // ---- 清除深度缓冲区 ----
        RenderSystem.clear(256, Minecraft.ON_OSX);

        if (selectedSlotIndex >= slotEntries.size() && !slotEntries.isEmpty()) {
            selectedSlotIndex = -1;
        }

        // ---- 恢复 Scissor ----
        g.flush();
        g.disableScissor();

        // ---- 绘制网格外围装饰框（位于内容上方）----
        SpriteRenderer.drawNineSlice(g, OVERLAY_NINE_SLICE, overlayThemeOffset, originX, originY, gridW, frameH);

        // ---- 滚动条 ----
        renderScrollbar(g, x, y, h);
    }

    /**
     * 计算排序按钮的X坐标
     *
     * @param baseX 基础X坐标
     * @return 排序按钮的X坐标
     */
    private int calculateSortButtonX(int baseX) {
        return baseX + PAD_LEFT + BUTTON_SIZE + BUTTON_SPACING;
    }
    
    /**
     * 计算升降序按钮的X坐标
     *
     * @param baseX 基础X坐标
     * @return 升降序按钮的X坐标
     */
    private int calculateOrderButtonX(int baseX) {
        return baseX + PAD_LEFT + BUTTON_SIZE + BUTTON_SPACING + BUTTON_SIZE + BUTTON_SPACING;
    }
    
    /**
     * 计算类型过滤按钮的X坐标
     *
     * @param baseX 基础X坐标
     * @return 类型过滤按钮的X坐标
     */
    private int calculateTypeFilterButtonX(int baseX) {
        return baseX + PAD_LEFT + BUTTON_SIZE + BUTTON_SPACING + BUTTON_SIZE + BUTTON_SPACING + BUTTON_SIZE + BUTTON_SPACING;
    }
    
    /**
     * 计算网格的原点X坐标
     *
     * @param baseX 基础X坐标
     * @return 网格的原点X坐标
     */
    private int calculateGridOriginX(int baseX) {
        return baseX + PAD_LEFT;
    }
    
    /**
     * 计算网格的原点Y坐标
     *
     * @param baseY 基础Y坐标
     * @return 网格的原点Y坐标
     */
    private int calculateGridOriginY(int baseY) {
        return baseY + PAD_TOP + GRID_TOP_OFFSET;
    }

    // ======================== 合成条目 ========================

    /**
     * 将 StorageEntry 和 FluidEntry 合并为统一的 SlotEntry 列表，并根据当前排序类型进行排序。
     */
    private void buildSlotEntries(List<?> items, List<?> fluids) {
        slotEntries.clear();
        // 根据显示过滤条件添加物品
        if (showItems) {
            for (Object obj : items) {
                if (obj instanceof StorageEntry se) {
                    if (se.stack() == null || se.stack().isEmpty()) continue;
                    // 预计算排序键，避免排序时重复调用 getHoverName / BuiltInRegistries
                    String sortName = se.stack().getHoverName().getString().toLowerCase();
                    String sortMod = se.namespace();
                    slotEntries.add(new SlotEntry(se.stack(), se.count(), false, obj, sortName, sortMod));
                }
            }
        }
        // 根据显示过滤条件添加流体
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
        
        // 根据当前排序类型对条目进行排序
        sortSlotEntries();
    }
    
    /**
     * 根据当前排序类型对槽位条目进行排序
     */
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

    /**
     * 脏标记检查——仅在数据版本、排序、过滤条件发生变化时才重建 slotEntries。
     * AE2 的 Repo 使用类似的增量检查机制，避免每帧 O(n log n) 全量排序。
     */
    private void checkAndRebuildIfDirty(StorageModule sm) {
        int currentRevision = sm.getRevision();
        boolean revisionChanged = currentRevision != lastRevision;
        boolean sortChanged = currentSortType != lastSortType || reverseSortOrder != lastReverseSortOrder;
        boolean filterChanged = showItems != lastShowItems || showFluids != lastShowFluids;

        if (slotEntriesDirty || revisionChanged || sortChanged || filterChanged) {
            buildSlotEntries(sm.getEntries(), sm.getFluidEntries());
            lastRevision = currentRevision;
            lastSortType = currentSortType;
            lastReverseSortOrder = reverseSortOrder;
            lastShowItems = showItems;
            lastShowFluids = showFluids;
            slotEntriesDirty = false;
        }
    }

    // ======================== 空状态提示 ========================

    /** 无数据时显示提示文本。 */
    private void renderEmptyHint(GuiGraphics g) {
        String hint = "No storage";
        Minecraft mc = Minecraft.getInstance();
        int lineH = mc.font.lineHeight;
        TextRenderer.drawCentered(g, mc.font, hint,
                getX() + getWidth() / 2, getY() + (getHeight() - lineH) / 2, HINT_COLOR);
    }

    // ======================== 滚动条渲染 ========================

    /** 渲染纵向滚动条（首尾各缩 6px 视觉边距），坐标与网格起始位置对齐。 */
    private void renderScrollbar(GuiGraphics g, int x, int y, int h) {
        int originY = y + PAD_TOP + GRID_TOP_OFFSET;
        int gridVisibleH = h - PAD_TOP * 2 - GRID_TOP_OFFSET;
        int barX = x + getWidth() - SCROLLBAR_W - RIGHT_MARGIN;
        scrollBar.render(g, barX, originY + 6, gridVisibleH - 12);
    }

    // ======================== 悬浮检测 ========================

    /** 查找鼠标悬浮的格子索引，-1 表示无。 */
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
        
        // 添加网格可视区域边界检查，防止被裁剪的区域响应悬停
        // 使用计算的框架高度确保与裁剪区域一致，而不是直接使用getHeight()
        int calculatedFrameHeight = rows * (SLOT_SIZE + SLOT_GAP) - SLOT_GAP;
        int bottomY = originY + calculatedFrameHeight;
        if (my < originY || my >= bottomY) {
            return -1;
        }
        
        return idx;
    }

    // ======================== 鼠标事件 ========================

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!contains((int) mouseX, (int) mouseY)) return false;
        return scrollBar.handleScroll(scrollY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        int x = getX(), y = getY(), h = getHeight();
        int originY = y + PAD_TOP + GRID_TOP_OFFSET;
        int gridVisibleH = h - PAD_TOP * 2 - GRID_TOP_OFFSET;
        int barX = x + getWidth() - SCROLLBAR_W - RIGHT_MARGIN;
        
        // 检查是否点击了当前选中的物品显示区域（使用base_ui_2.png样式），如果是则执行定位
        int itemDisplayX = x + PAD_LEFT;
        int itemDisplayY = y + PAD_TOP + 1; // 与网格顶部对齐，使用PAD_TOP确保与网格垂直对齐，下移1px
        int itemDisplaySize = BUTTON_SIZE; // 显示区域大小，使用base_ui_2.png样式，与网格槽位对齐

        // 检查鼠标是否点击了当前选中物品的显示区域
        if (mouseX >= itemDisplayX && mouseX < itemDisplayX + itemDisplaySize &&
            mouseY >= itemDisplayY && mouseY < itemDisplayY + itemDisplaySize) {
            // 点击了当前选中物品显示区域，执行定位操作
            scrollToSelectedItem();
            return true;
        }
        
        // 检查是否点击了排序按钮
        int sortBtnX = calculateSortButtonX(x); // 使用辅助方法计算排序按钮X坐标
        int sortBtnY = y + PAD_TOP + 1; // 与当前选中物品在同一水平线上，下移1px
        int sortBtnWidth = BUTTON_SIZE; // 按钮宽度
        int sortBtnHeight = BUTTON_SIZE; // 按钮高度
        
        if (mouseX >= sortBtnX && mouseX < sortBtnX + sortBtnWidth &&
            mouseY >= sortBtnY && mouseY < sortBtnY + sortBtnHeight) {
            // 点击了排序按钮，切换排序类型
            cycleSortType();
            return true;
        }
        
        // 检查是否点击了升降序按钮
        int orderBtnX = calculateOrderButtonX(x); // 使用辅助方法计算升降序按钮X坐标
        int orderBtnY = y + PAD_TOP + 1; // 与当前选中物品在同一水平线上，下移1px
        int orderBtnWidth = BUTTON_SIZE; // 按钮宽度
        int orderBtnHeight = BUTTON_SIZE; // 按钮高度
        
        if (mouseX >= orderBtnX && mouseX < orderBtnX + orderBtnWidth &&
            mouseY >= orderBtnY && mouseY < orderBtnY + orderBtnHeight) {
            // 点击了升降序按钮，切换排序顺序
            toggleSortOrder();
            return true;
        }
        
        // 检查是否点击了类型过滤按钮
        int typeFilterBtnX = calculateTypeFilterButtonX(x); // 使用辅助方法计算类型过滤按钮X坐标
        int typeFilterBtnY = y + PAD_TOP + 1; // 与当前选中物品在同一水平线上，下移1px
        int typeFilterBtnWidth = BUTTON_SIZE; // 按钮宽度
        int typeFilterBtnHeight = BUTTON_SIZE; // 按钮高度
        
        if (mouseX >= typeFilterBtnX && mouseX < typeFilterBtnX + typeFilterBtnWidth &&
            mouseY >= typeFilterBtnY && mouseY < typeFilterBtnY + typeFilterBtnHeight) {
            // 点击了类型过滤按钮，切换弹出菜单
            toggleTypeFilter();
            // 定位弹出菜单到按钮位置
            int screenWidth = Minecraft.getInstance().screen != null ? Minecraft.getInstance().screen.width : 0;
            typeFilterPopup.positionFromButton(typeFilterBtnX + typeFilterBtnWidth / 2, typeFilterBtnY, screenWidth);
            return true;
        }
        
        // 检查是否点击了弹出菜单
        if (typeFilterPopup.isOpen() && typeFilterPopup.contains((int) mouseX, (int) mouseY)) {
            return typeFilterPopup.handleClick((int) mouseX, (int) mouseY);
        }
        
        if (scrollBar.handleClick(mouseX, mouseY, barX,
                originY + 6, gridVisibleH - 12)) {
            return true;
        }

        // ---- 格子点击选中/取消 ----
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
        
        // 添加网格可视区域边界检查，防止被裁剪的区域响应点击
        // 使用计算的框架高度确保与裁剪区域一致，而不是使用gridVisibleH
        int calculatedFrameHeight = rows * (SLOT_SIZE + SLOT_GAP) - SLOT_GAP;
        int bottomY = originY + calculatedFrameHeight;
        if (mouseY < originY || mouseY >= bottomY) {
            return false;
        }
        // 点击同一格子取消选中，否则切换选中
        if (selectedSlotIndex == idx) {
            selectedSlotIndex = -1;
            // 取消选中时清空当前选中的物品
            currentSelectedItem = ItemStack.EMPTY;
        } else {
            selectedSlotIndex = idx;
            // 设置当前选中的物品
            SlotEntry clickedEntry = slotEntries.get(idx);
            currentSelectedItem = clickedEntry.stack().copy();
        }
        
        // 执行物品选择
        SlotEntry entry = slotEntries.get(idx);
        if (!entry.isFluid()) {
            // 选择物品
            String itemId = BuiltInRegistries.ITEM.getKey(entry.stack().getItem()).toString();
            String label = entry.stack().getHoverName().getString();
            
            // 获取 BuildingModule 并设置选中物品
            BuildingModule buildingModule = CompositionRoot.get().module(BuildingModule.class);
            if (buildingModule != null) {
                buildingModule.selectItem(itemId, label, entry.stack());
            }
        } else {
            // 选择流体
            // 从原始条目中获取流体ID
            if (entry.originalEntry() instanceof FluidEntry originalFluidEntry) {
                String fluidId = originalFluidEntry.fluidId();
                String label = entry.stack().getHoverName().getString();
                
                // 获取 BuildingModule 并设置选中流体
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

    /**
     * 定位到选中物品在网格中的位置
     */
    private void scrollToSelectedItem() {
        if (currentSelectedItem.isEmpty() || slotEntries.isEmpty()) {
            return;
        }
        
        // 在条目列表中查找当前选中的物品
        int targetIndex = -1;
        for (int i = 0; i < slotEntries.size(); i++) {
            SlotEntry entry = slotEntries.get(i);
            if (ItemStack.isSameItemSameComponents(entry.stack(), currentSelectedItem)) {
                targetIndex = i;
                break;
            }
        }
        
        if (targetIndex == -1) {
            return; // 没有找到对应的物品
        }
        
        // 计算目标行号
        int targetRow = targetIndex / cols;
        
        // 计算目标行在屏幕上的位置
        int targetY = targetRow * (SLOT_SIZE + SLOT_GAP);
        
        // 计算滚动条的目标滚动位置，使目标行居中显示
        int gridVisibleH = getHeight() - PAD_TOP * 2 - GRID_TOP_OFFSET;
        int rowsVisible = gridVisibleH / (SLOT_SIZE + SLOT_GAP);
        int centeredScroll = targetY - (rowsVisible / 2) * (SLOT_SIZE + SLOT_GAP);
        
        // 限制滚动范围
        centeredScroll = Math.max(0, centeredScroll);
        centeredScroll = Math.min(scrollBar.getMaxScroll(), centeredScroll);
        
        // 启动平滑滚动动画
        startSmoothScrollAnimation(centeredScroll);
    }
    
    /**
     * 开始平滑滚动动画
     * 
     * @param targetScrollPos 目标滚动位置
     */
    private void startSmoothScrollAnimation(double targetScrollPos) {
        this.targetScroll = targetScrollPos;
        this.animatedScroll = scrollBar.getScroll();
        this.animationStartTime = System.currentTimeMillis();
        this.isScrollingAnimated = true;
    }
    
    /**
     * 更新滚动动画状态
     */
    private void updateScrollAnimation() {
        if (!isScrollingAnimated) {
            return;
        }
        
        long currentTime = System.currentTimeMillis();
        float elapsed = (currentTime - animationStartTime) / 1000.0f * 20.0f; // 转换为刻数
        
        if (elapsed >= ANIMATION_DURATION) {
            // 动画完成，设置最终位置
            scrollBar.setScroll((int) targetScroll);
            animatedScroll = targetScroll;
            isScrollingAnimated = false;
            return;
        }
        
        // 使用缓动函数（ease-out）计算当前位置
        float progress = elapsed / ANIMATION_DURATION;
        float easeOut = 1.0f - (float) Math.pow(1.0f - progress, 2); // 二次缓出
        
        animatedScroll = animatedScroll + (targetScroll - animatedScroll) * easeOut;
        
        // 设置滚动条位置
        scrollBar.setScroll((int) animatedScroll);
    }
    
    @Override
    protected void postRenderContent(GuiGraphics g) {
        // 获取 Minecraft 屏幕尺寸用于边界检测
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) {
            renderTooltipOverlay(g, (int) getLastMouseX(), (int) getLastMouseY(),
                    mc.screen.width, mc.screen.height);
            // 渲染类型过滤弹出菜单
            typeFilterPopup.render(g, (int) getLastMouseX(), (int) getLastMouseY());
        }
    }
    
    /**
     * 渲染按钮的悬浮提示框
     *
     * @param g GuiGraphics 实例
     * @param mouseX 鼠标X坐标
     * @param mouseY 鼠标Y坐标
     * @param screenW 屏幕宽度
     * @param screenH 屏幕高度
     */
    public void renderTooltipOverlay(GuiGraphics g, int mouseX, int mouseY, int screenW, int screenH) {
        int x = getX(), y = getY();
        
        // 当前选中物品显示区域按钮位置
        int itemDisplayX = x + PAD_LEFT;
        int itemDisplayY = y + PAD_TOP + 1;
        int itemDisplaySize = BUTTON_SIZE;
        
        // 排序按钮位置
        int sortBtnX = calculateSortButtonX(x);
        int sortBtnY = y + PAD_TOP + 1;
        int sortBtnWidth = BUTTON_SIZE;
        int sortBtnHeight = BUTTON_SIZE;
        
        // 升降序按钮位置
        int orderBtnX = calculateOrderButtonX(x);
        int orderBtnY = y + PAD_TOP + 1;
        int orderBtnWidth = BUTTON_SIZE;
        int orderBtnHeight = BUTTON_SIZE;
        
        // 类型过滤按钮位置
        int typeFilterBtnX = calculateTypeFilterButtonX(x);
        int typeFilterBtnY = y + PAD_TOP + 1;
        int typeFilterBtnWidth = BUTTON_SIZE;
        int typeFilterBtnHeight = BUTTON_SIZE;
        
        // 渲染当前选中物品显示区域的悬浮提示
        if (currentItemTooltip.shouldRender()) {
            String text = Component.translatable("tooltip.rtsbuilding.rightdown.current_selected_item").getString() + "\n" +
                         Component.translatable("tooltip.rtsbuilding.rightdown.current_selected_item.desc").getString();
            renderTooltipAbove(g, currentItemTooltip,
                    itemDisplayX, itemDisplayY, itemDisplaySize, itemDisplaySize,
                    text, screenW, screenH);
        }
        
        // 渲染排序按钮的悬浮提示
        if (sortButtonTooltip.shouldRender()) {
            String text = Component.translatable("tooltip.rtsbuilding.rightdown.sort_button").getString() + "\n" +
                         Component.translatable("tooltip.rtsbuilding.rightdown.sort_button.desc").getString();
            renderTooltipAbove(g, sortButtonTooltip,
                    sortBtnX, sortBtnY, sortBtnWidth, sortBtnHeight,
                    text, screenW, screenH);
        }
        
        // 渲染升降序按钮的悬浮提示
        if (orderButtonTooltip.shouldRender()) {
            String text = Component.translatable("tooltip.rtsbuilding.rightdown.order_button").getString() + "\n" +
                         Component.translatable("tooltip.rtsbuilding.rightdown.order_button.desc").getString();
            renderTooltipAbove(g, orderButtonTooltip,
                    orderBtnX, orderBtnY, orderBtnWidth, orderBtnHeight,
                    text, screenW, screenH);
        }
        
        // 渲染类型过滤按钮的悬浮提示
        if (typeFilterButtonTooltip.shouldRender()) {
            String text = Component.translatable("tooltip.rtsbuilding.rightdown.type_filter_button").getString() + "\n" +
                         Component.translatable("tooltip.rtsbuilding.rightdown.type_filter_button.desc").getString();
            renderTooltipAbove(g, typeFilterButtonTooltip,
                    typeFilterBtnX, typeFilterBtnY, typeFilterBtnWidth, typeFilterBtnHeight,
                    text, screenW, screenH);
        }
    }
    
    /**
     * 在按钮上方渲染悬浮提示
     */
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
        
        // 定位到按钮上方
        int tipX = btnX;
        int tipY = btnY - tipH - 2;
        
        // 确保提示框在屏幕范围内
        tipX = Math.max(0, Math.min(tipX, screenW - tipW));
        tipY = Math.max(0, tipY); // 不允许负值，避免提示框超出屏幕顶部
        
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
