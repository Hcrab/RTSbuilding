package com.rtsbuilding.rtsbuilding.client.screen.panel.downbar.overlay;

import com.rtsbuilding.rtsbuilding.client.kernel.RtsClientKernel;
import com.rtsbuilding.rtsbuilding.client.module.building.BuildingModule;
import com.rtsbuilding.rtsbuilding.client.module.storage.StorageModule;
import com.rtsbuilding.rtsbuilding.client.record.FluidEntry;
import com.rtsbuilding.rtsbuilding.client.record.StorageEntry;
import com.rtsbuilding.rtsbuilding.client.screen.panel.base.component.ScrollBar;
import com.rtsbuilding.rtsbuilding.client.screen.panel.base.overlay.DownOverlayLayer;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.util.render.SpriteRenderer;
import com.rtsbuilding.rtsbuilding.client.util.render.TextRenderer;
import com.rtsbuilding.rtsbuilding.client.util.render.model.NineSliceRegion;
import com.rtsbuilding.rtsbuilding.client.util.render.model.SpriteRegion;
import com.rtsbuilding.rtsbuilding.client.util.render.model.TextureInfo;
import com.rtsbuilding.rtsbuilding.client.util.state.TooltipController;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
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
        
        // 只只有在状态变化时才重新构建条目列表
        if (stateChanged) {
            StorageModule sm = RtsClientKernel.get().module(StorageModule.class);
            if (sm != null) {
                buildSlotEntries(sm.getEntries(), sm.getFluidEntries());
            }
        }
    }
    
    // ======================== 布局常量 ========================

    /** 每个格子的尺寸（宽高一致） */
    private static final int SLOT_SIZE = 18;
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

    /** 物品图标在格子内的偏移（居中，16×16 图标在 18×18 格子中上下各 1px） */
    private static final int ICON_OFFSET = 1;

    /** 数量文本缩放系数（参考 AE2 StackSizeRenderer 默认值 0.666f） */
    private static final float AMOUNT_SCALE = 0.666f;
    /** 缩放倒数 */
    private static final float INV_AMOUNT_SCALE = 1.0f / AMOUNT_SCALE;

    // ======================== 按钮尺寸常量 ========================

    /** 选择按钮和排序按钮的尺寸 */
    private static final int BUTTON_SIZE = 18;
    /** 选择按钮和排序按钮之间的间距 */
    private static final int BUTTON_SPACING = 1;

    // ======================== 格子贴图（slots.png）=======================

    /** slots.png：32×48，水平双主题，垂直 0-16=正常，16-32=悬浮，32-48=选中 */
    private static final ResourceLocation SLOTS_TEXTURE = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/down/slots.png");
    private static final int SLOTS_TEX_W = 32;
    private static final int SLOTS_TEX_H = 48;
    private static final int SLOTS_STATE_H = 16;
    /** 选中态垂直偏移（y=32-48） */
    private static final int SLOTS_SELECTED_V_OFFSET = 32;
    private static final TextureInfo SLOTS_TEX_INFO = new TextureInfo(
            SLOTS_TEXTURE, SLOTS_TEX_W, SLOTS_TEX_H,
            TextureInfo.ThemeLayout.HORIZONTAL_PAIR,
            TextureInfo.FilterMode.PIXEL);
    /** 正常态精灵（v=0~16，半区宽=16） */
    private static final SpriteRegion SLOT_NORMAL = new SpriteRegion(
            SLOTS_TEX_INFO, 0, 0, SLOTS_TEX_W / 2, SLOTS_STATE_H);
    /** 悬浮态精灵（v=16~32） */
    private static final SpriteRegion SLOT_HOVER = new SpriteRegion(
            SLOTS_TEX_INFO, 0, SLOTS_STATE_H, SLOTS_TEX_W / 2, SLOTS_STATE_H);
    /** 选中态精灵（v=32~48）——半透明覆盖层，盖在图标之上指示已选中 */
    private static final SpriteRegion SLOT_SELECTED = new SpriteRegion(
            SLOTS_TEX_INFO, 0, SLOTS_SELECTED_V_OFFSET, SLOTS_TEX_W / 2, SLOTS_STATE_H);


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
    /** 物品数量文本颜色 */
    private static final int AMOUNT_COLOR = 0xFF_FFFFFF;
    /** 流体数量文本颜色（浅蓝色调，与物品区分） */
    private static final int FLUID_AMOUNT_COLOR = 0xFF_80C8FF;
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
    private record SlotEntry(ItemStack stack, long count, boolean isFluid, Object originalEntry) {
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
        
        // 重新排序条目
        StorageModule sm = RtsClientKernel.get().module(StorageModule.class);
        if (sm != null) {
            buildSlotEntries(sm.getEntries(), sm.getFluidEntries());
        }
    }
    
    /**
     * 切换排序顺序（升序/降序）
     */
    private void toggleSortOrder() {
        reverseSortOrder = !reverseSortOrder;
        
        // 重新排序条目
        StorageModule sm = RtsClientKernel.get().module(StorageModule.class);
        if (sm != null) {
            buildSlotEntries(sm.getEntries(), sm.getFluidEntries());
        }
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
        StorageModule sm = RtsClientKernel.get().module(StorageModule.class);
        if (sm == null) return;

        var storedItems = sm.getEntries();
        var storedFluids = sm.getFluidEntries();

        // ---- 合并物品与流体为 SlotEntry 列表 ----
        buildSlotEntries(storedItems, storedFluids);
        if (slotEntries.isEmpty()) {
            renderEmptyHint(g);
            return;
        }

        int x = getX(), y = getY(), w = getWidth(), h = getHeight();

        // ---- 获取 Minecraft 实例 ----
        Minecraft mc = Minecraft.getInstance();
        
        // ---- 检测鼠标是否悬停在当前选中物品上（优先检测）----
        int mouseX = (int) getLastMouseX();
        int mouseY = (int) getLastMouseY();
        boolean isHoveringOverCurrentSelection = false;
        int itemDisplayX = x + PAD_LEFT;
        int itemDisplayY = y + PAD_TOP + 1; // 与网格顶部对齐，使用PAD_TOP确保与网格垂直对齐，下移1px
        int itemDisplaySize = BUTTON_SIZE; // 显示区域大小，使用base_ui_2.png样式，与网格槽位对齐
        
        // 检测鼠标是否悬停在当前选中物品上
        if (mouseX >= itemDisplayX && mouseX < itemDisplayX + itemDisplaySize &&
            mouseY >= itemDisplayY && mouseY < itemDisplayY + itemDisplaySize) {
            isHoveringOverCurrentSelection = true;
        }
        
        // 更新当前选中物品显示区域的悬浮状态
        currentItemTooltip.update(isHoveringOverCurrentSelection, false);
        
        // 绘制当前选中的物品（在网格上方）
        // 绘制选中物品背景框
        SpriteRegion selectedItemBgRegion = isHoveringOverCurrentSelection ? SORT_BTN_HOVER.withTheme() : SORT_BTN_NORMAL.withTheme();
        SpriteRenderer.drawSprite(g, selectedItemBgRegion, itemDisplayX, itemDisplayY, itemDisplaySize, itemDisplaySize);
        
        if (!currentSelectedItem.isEmpty()) {
            // 绘制选中物品图标
            RenderSystem.disableDepthTest();
            var pose = g.pose();
            pose.pushPose();
            pose.translate(itemDisplayX + 1, itemDisplayY + 1, 0); // 物品图标 Z=0，居中显示
            g.renderItem(currentSelectedItem, 0, 0);
            g.flush(); // 立即提交物品渲染，确保层级正确
            pose.popPose();
        } else {
            // 当没有选择物品时，绘制 nothing.png 贴图
            RenderSystem.disableDepthTest();
            // 计算贴图居中位置，使用精灵区域的实际尺寸
            int iconWidth = NOTHING_TEX_W / 2; // 水平双主题的半区宽度
            int iconHeight = NOTHING_TEX_H; // 完整高度
            int iconOffsetX = (itemDisplaySize - iconWidth) / 2;
            int iconOffsetY = (itemDisplaySize - iconHeight) / 2;
            int iconX = itemDisplayX + iconOffsetX;
            int iconY = itemDisplayY + iconOffsetY;
            
            SpriteRenderer.drawSprite(g, NOTHING_SPRITE.withTheme(), iconX, iconY, iconWidth, iconHeight);
        }

        // ---- 绘制排序按钮 ----
        // 位置在当前选中物品显示区域的右边，保持1px间距
        int sortBtnX = calculateSortButtonX(x); // 使用辅助方法计算排序按钮X坐标
        int sortBtnY = y + PAD_TOP + 1; // 与当前选中物品在同一水平线上，使用PAD_TOP确保垂直对齐，下移1px
        int sortBtnWidth = BUTTON_SIZE; // 按钮宽度
        int sortBtnHeight = BUTTON_SIZE; // 按钮高度
        
        // 检查鼠标是否悬停在排序按钮上
        boolean isHoveringOverSortBtn = (mouseX >= sortBtnX && mouseX < sortBtnX + sortBtnWidth &&
                                        mouseY >= sortBtnY && mouseY < sortBtnY + sortBtnHeight);
        
        // 更新排序按钮的悬浮状态
        sortButtonTooltip.update(isHoveringOverSortBtn, false);
        
        // 绘制排序按钮背景
        SpriteRegion btnBgRegion = isHoveringOverSortBtn ? SORT_BTN_HOVER.withTheme() : SORT_BTN_NORMAL.withTheme();
        SpriteRenderer.drawSprite(g, btnBgRegion, sortBtnX, sortBtnY, sortBtnWidth, sortBtnHeight);
        
        // 根据当前排序类型绘制相应的图标
        SpriteRegion iconRegion = null;
        switch (currentSortType) {
            case NAME -> iconRegion = SORT_NAME_ICON.withTheme();
            case COUNT -> iconRegion = SORT_COUNT_ICON.withTheme();
            case MOD -> iconRegion = SORT_MOD_ICON.withTheme();
        }
        
        if (iconRegion != null) {
            // 图标绘制在按钮中央
            SpriteRenderer.drawSprite(g, iconRegion, sortBtnX, sortBtnY, sortBtnWidth, sortBtnHeight);
        }

        // ---- 绘制升降序按钮 ----
        // 位置在排序按钮的右边，保持1px间距
        int orderBtnX = calculateOrderButtonX(x); // 使用辅助方法计算升降序按钮X坐标
        int orderBtnY = y + PAD_TOP + 1; // 与当前选中物品在同一水平线上，使用PAD_TOP确保垂直对齐，下移1px
        int orderBtnWidth = BUTTON_SIZE; // 按钮宽度
        int orderBtnHeight = BUTTON_SIZE; // 按钮高度
        
        // 检查鼠标是否悬停在升降序按钮上
        boolean isHoveringOverOrderBtn = (mouseX >= orderBtnX && mouseX < orderBtnX + orderBtnWidth &&
                                        mouseY >= orderBtnY && mouseY < orderBtnY + orderBtnHeight);
        
        // 更新升降序按钮的悬浮状态
        orderButtonTooltip.update(isHoveringOverOrderBtn, false);
        
        // 绘制升降序按钮背景
        SpriteRegion orderBtnBgRegion = isHoveringOverOrderBtn ? SORT_BTN_HOVER.withTheme() : SORT_BTN_NORMAL.withTheme();
        SpriteRenderer.drawSprite(g, orderBtnBgRegion, orderBtnX, orderBtnY, orderBtnWidth, orderBtnHeight);
        
        // 根据当前排序顺序绘制相应的图标
        SpriteRegion orderIconRegion = reverseSortOrder ? ORDER_DESC_ICON.withTheme() : ORDER_ASC_ICON.withTheme();
        
        // 图标绘制在按钮中央
        SpriteRenderer.drawSprite(g, orderIconRegion, orderBtnX, orderBtnY, orderBtnWidth, orderBtnHeight);

        // ---- 绘制类型过滤按钮 ----
        // 位置在升降序按钮的右边，保持1px间距
        int typeFilterBtnX = calculateTypeFilterButtonX(x); // 使用辅助方法计算类型过滤按钮X坐标
        int typeFilterBtnY = y + PAD_TOP + 1; // 与当前选中物品在同一水平线上，使用PAD_TOP确保垂直对齐，下移1px
        int typeFilterBtnWidth = BUTTON_SIZE; // 按钮宽度
        int typeFilterBtnHeight = BUTTON_SIZE; // 按钮高度
        
        // 检查鼠标是否悬停在类型过滤按钮上
        boolean isHoveringOverTypeFilterBtn = (mouseX >= typeFilterBtnX && mouseX < typeFilterBtnX + typeFilterBtnWidth &&
                                        mouseY >= typeFilterBtnY && mouseY < typeFilterBtnY + typeFilterBtnHeight);
        
        // 更新类型过滤按钮的悬浮状态
        typeFilterButtonTooltip.update(isHoveringOverTypeFilterBtn, false);
        
        // 绘制类型过滤按钮背景
        SpriteRegion typeFilterBtnBgRegion = isHoveringOverTypeFilterBtn ? SORT_BTN_HOVER.withTheme() : SORT_BTN_NORMAL.withTheme();
        SpriteRenderer.drawSprite(g, typeFilterBtnBgRegion, typeFilterBtnX, typeFilterBtnY, typeFilterBtnWidth, typeFilterBtnHeight);
        
        // 根据当前显示过滤状态绘制相应的图标
        SpriteRegion typeFilterIconRegion = null;
        if (showItems && showFluids) {
            // 都显示时，使用物品图标
            typeFilterIconRegion = TYPE_ITEM_ICON.withTheme();
        } else if (showItems && !showFluids) {
            // 只显示物品时，使用物品图标
            typeFilterIconRegion = TYPE_ITEM_ICON.withTheme();
        } else if (!showItems && showFluids) {
            // 只显示流体时，使用流体图标
            typeFilterIconRegion = TYPE_FLUID_ICON.withTheme();
        } else {
            // 都不显示时，使用物品图标
            typeFilterIconRegion = TYPE_ITEM_ICON.withTheme();
        }
        
        // 图标绘制在按钮中央
        SpriteRenderer.drawSprite(g, typeFilterIconRegion, typeFilterBtnX, typeFilterBtnY, typeFilterBtnWidth, typeFilterBtnHeight);

        // ---- 布局计算 ----
        /* 当前帧每行可用的实际宽度（用于计算列数） */
        int usableW = w - PAD_LEFT - SCROLLBAR_W - RIGHT_MARGIN;
        cols = Math.max(1, (usableW + SLOT_GAP) / (SLOT_SIZE + SLOT_GAP));
        // 固定网格行数：基于面板内网格实际可视高度决定，+2 行作为滚动缓冲
        rows = Math.max(1, (h - PAD_TOP - GRID_TOP_OFFSET) / (SLOT_SIZE + SLOT_GAP) + 2);
        // 实际物品行数（用于滚动内容范围）
        int itemRows = (slotEntries.size() + cols - 1) / cols;
        int visibleH = h - PAD_TOP * 2;
        int gridVisibleH = visibleH - GRID_TOP_OFFSET;
        int gridH = itemRows * (SLOT_SIZE + SLOT_GAP) - SLOT_GAP;

        // ---- 更新滚动条（仅在网格高度严格超出可视高度时显示）----
        scrollBar.setContent(gridH, gridVisibleH + 6);
        int scroll = scrollBar.getScroll();

        int originX = calculateGridOriginX(x);
        int originY = calculateGridOriginY(y);
        int gridW = cols * (SLOT_SIZE + SLOT_GAP) - SLOT_GAP;
        int frameH = rows * (SLOT_SIZE + SLOT_GAP) - SLOT_GAP;
        int bottomY = originY + gridVisibleH;
        int localMouseX = getLastMouseX();
        int localMouseY = getLastMouseY();
        int hoveredSlot = findHoveredSlot(localMouseX, localMouseY, originX, originY, scroll);
        this.tooltipSlotIndex = hoveredSlot;

        // ---- 绘制网格外围装饰框（固定位置，不随滚动偏移）----
        SpriteRenderer.drawNineSlice(g, OVERLAY_NINE_SLICE.withTheme(), originX, originY, gridW, frameH);

        // ---- 启用 GPU Scissor 精确裁剪网格区域，替代 CPU 跳过逻辑 ----
        g.flush();
        Screen screen = mc.screen;
        // 使用frameH作为裁剪底部，确保裁剪区域与网格框架对齐
        int scissorBottomY = originY + frameH;
        if (screen instanceof BuilderScreen bs) {
            bs.enableRtsScissor(g, originX, originY + 1, originX + gridW, scissorBottomY);
        } else {
            g.enableScissor(originX, originY + 1, originX + gridW, scissorBottomY);
        }

        // ---- 第一遍：批量绘制整个网格区域的所有格子背景，与 AE2 行为一致 ----
        // 仅绘制格子背景
        // 计算需要绘制的总单元格数（取预设网格数和实际物品数的最大值）
        int totalRequiredCells = Math.max(rows * cols, slotEntries.size());
        for (int i = 0; i < totalRequiredCells; i++) {
            int col = i % cols;
            int row = i / cols;
            int slotX = originX + col * (SLOT_SIZE + SLOT_GAP);
            int slotY = originY + row * (SLOT_SIZE + SLOT_GAP) - scroll;
            // 检查格子是否在可视范围内（在顶部和底部边界之内）
            if (slotY + SLOT_SIZE < originY || slotY > scissorBottomY) continue;
            SpriteRenderer.drawSprite(g, SLOT_NORMAL.withTheme(), slotX, slotY, SLOT_SIZE, SLOT_SIZE);
        }

        // ---- 第二遍：逐格渲染物品图标 + 数量文字 + 悬浮层 ----
        for (int i = 0; i < slotEntries.size(); i++) {
            int col = i % cols;
            int row = i / cols;
            int slotX = originX + col * (SLOT_SIZE + SLOT_GAP);
            int slotY = originY + row * (SLOT_SIZE + SLOT_GAP) - scroll;
            // 检查格子是否在可视范围内（在顶部和底部边界之内）
            if (slotY + SLOT_SIZE < originY || slotY > scissorBottomY) continue;

            SlotEntry entry = slotEntries.get(i);
            boolean hovered = (i == hoveredSlot);

            // ---- 物品图标（居中 16×16）----
            RenderSystem.disableDepthTest();
            int iconX = slotX + ICON_OFFSET;
            int iconY = slotY + ICON_OFFSET;
            ItemStack stack = entry.stack();
            if (!stack.isEmpty()) {
                var pose = g.pose();
                pose.pushPose();
                pose.translate(iconX, iconY, 0);  // 物品图标 Z=0
                g.renderItem(stack, 0, 0);
                g.flush(); // 立即提交物品渲染，确保层级正确
                pose.popPose();
            }

            // ---- 数量文本（以格子右下角为参考点，后渲染于图标之上）----
            RenderSystem.disableDepthTest();
            long count = entry.count;
            if (count > 1) {
                String text = formatAmount(count);
                int textW = mc.font.width(text);

                float scale = AMOUNT_SCALE;
                float invScale = INV_AMOUNT_SCALE;
                // 以物品背景框右/下边缘为基准，缩放坐标系中右对齐文本
                float refRight = slotX + SLOT_SIZE;
                float refBottom = slotY + SLOT_SIZE;
                int tx = (int) (refRight * invScale - textW);
                int ty = (int) (refBottom * invScale - mc.font.lineHeight);

                // 切换到下一个渲染层级，确保数量文字在物品图标之上
                g.pose().pushPose();
                g.pose().scale(scale, scale, 1.0f);
                g.pose().translate(tx, ty, 200); // 数量文字 Z=200，高于物品图标

                int color = entry.isFluid() ? FLUID_AMOUNT_COLOR : AMOUNT_COLOR;
                // 阴影文字（确保在任何图标颜色上都清晰）
                g.drawString(mc.font, text, 1, 1, 0xFF_000000, false);
                g.drawString(mc.font, text, 0, 0, color, false);
                g.flush(); // 立即提交文字渲染，确保层级正确

                g.pose().popPose();
            }

            // ---- 选中覆盖层 / 悬浮叠加层（盖在图标和数量文字之上）----
            RenderSystem.disableDepthTest();
            var pose = g.pose();
            pose.pushPose();
            pose.translate(slotX, slotY, 300); // 覆盖层 Z=300，最高层级
            boolean isSelected = (i == selectedSlotIndex);
            // 检查选中的索引是否仍然有效，防止越界
            if (i == selectedSlotIndex && selectedSlotIndex < slotEntries.size()) {
                SpriteRenderer.drawSprite(g, SLOT_SELECTED.withTheme(), 0, 0, SLOT_SIZE, SLOT_SIZE);
                g.flush(); // 立即提交选中状态渲染
            } else if (hovered) {
                SpriteRenderer.drawSprite(g, SLOT_HOVER.withTheme(), 0, 0, SLOT_SIZE, SLOT_SIZE);
                g.flush(); // 立即提交悬浮状态渲染
            }
            pose.popPose();
        }

        // 如果选中的索引超出了当前条目数，则重置选择状态
        if (selectedSlotIndex >= slotEntries.size() && !slotEntries.isEmpty()) {
            selectedSlotIndex = -1;
        }

        // ---- 恢复为嵌层 Scissor 边界（弹出网格 Scissor，让 DownOverlayLayer 的嵌层 Scissor 自然恢复）----
        g.flush();
        g.disableScissor();

        // ---- 重新绘制网格外围装饰框（位于网格内容上方，确保装饰框在最顶层）----
        SpriteRenderer.drawNineSlice(g, OVERLAY_NINE_SLICE.withTheme(), originX, originY, gridW, frameH);

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
                    slotEntries.add(new SlotEntry(se.stack(), se.count(), false, obj));
                }
            }
        }
        // 根据显示过滤条件添加流体
        if (showFluids) {
            for (Object obj : fluids) {
                if (obj instanceof FluidEntry fe) {
                    if (fe.preview() == null || fe.preview().isEmpty()) continue;
                    slotEntries.add(new SlotEntry(fe.preview(), fe.amount(), true, obj));
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
            int result = 0;
            
            switch (currentSortType) {
                case NAME -> {
                    // 按名称排序
                    String name1 = entry1.stack().getHoverName().getString();
                    String name2 = entry2.stack().getHoverName().getString();
                    result = name1.compareToIgnoreCase(name2);
                }
                case COUNT -> {
                    // 按数量排序
                    long count1 = entry1.count();
                    long count2 = entry2.count();
                    result = Long.compare(count2, count1); // 降序排列（数量大的在前面）
                }
                case MOD -> {
                    // 按模组排序
                    String mod1 = BuiltInRegistries.ITEM.getKey(entry1.stack().getItem()).getNamespace();
                    String mod2 = BuiltInRegistries.ITEM.getKey(entry2.stack().getItem()).getNamespace();
                    result = mod1.compareToIgnoreCase(mod2);
                    
                    // 如果模组相同，再按名称排序
                    if (result == 0) {
                        String name1 = entry1.stack().getHoverName().getString();
                        String name2 = entry2.stack().getHoverName().getString();
                        result = name1.compareToIgnoreCase(name2);
                    }
                }
            }
            
            // 如果启用了反向排序，则反转结果
            if (reverseSortOrder) {
                result = -result;
            }
            
            return result;
        });
    }

    // ======================== 单格子渲染（已内联到 renderContent 的两遍式循环中）========================

    // ======================== 数量格式化 ========================

    /**
     * 将数量格式化为紧凑形式（参考 AE2 AmountFormat.SLOT 风格）。
     *
     * <ul>
     *   <li>&ge; 1,000,000,000 → "1.0B"（十亿）</li>
     *   <li>&ge; 1,000,000 → "1.0M"（百万）</li>
     *   <li>&ge; 1,000 → "1.0K"（千）</li>
     *   <li>其他 → 原样输出</li>
     * </ul>
     */
    private static String formatAmount(long count) {
        if (count >= 1_000_000_000L) {
            double val = count / 100_000_000.0;
            return String.format("%.1fB", val / 10.0);
        } else if (count >= 1_000_000L) {
            double val = count / 100_000.0;
            return String.format("%.1fM", val / 10.0);
        } else if (count >= 1_000L) {
            double val = count / 100.0;
            return String.format("%.1fK", val / 10.0);
        }
        return String.valueOf(count);
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
            BuildingModule buildingModule = RtsClientKernel.get().module(BuildingModule.class);
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
                BuildingModule buildingModule = RtsClientKernel.get().module(BuildingModule.class);
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
