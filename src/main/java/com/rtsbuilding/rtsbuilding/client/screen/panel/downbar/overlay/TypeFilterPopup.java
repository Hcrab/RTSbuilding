package com.rtsbuilding.rtsbuilding.client.screen.panel.downbar.overlay;

import com.rtsbuilding.rtsbuilding.client.screen.panel.base.popup.BasePopup;
import com.rtsbuilding.rtsbuilding.client.util.render.TextRenderer;
import com.rtsbuilding.rtsbuilding.client.util.theme.ThemeManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * 类型过滤弹出菜单——点击类型过滤按钮后显示的菜单。
 *
 * <p>提供两个选项："物品"和"流体"，分别控制物品和流体的显示状态。</p>
 */
public final class TypeFilterPopup extends BasePopup {

    // ======================== 菜单项数据 ========================

    public record TypeFilterItem(Component label, Runnable action) {}

    private final TypeFilterItem[] items;
    private final boolean[] states;

    // ======================== 状态引用 ========================

    private boolean showItems;
    private boolean showFluids;
    
    // ======================== 回调接口 ========================
    
    @FunctionalInterface
    public interface OnFilterChangeListener {
        void onFilterChanged(boolean showItems, boolean showFluids);
    }
    
    private final OnFilterChangeListener listener;

    // ======================== 构造 ========================

    public TypeFilterPopup(boolean showItems, boolean showFluids, OnFilterChangeListener listener) {
        this.showItems = showItems;
        this.showFluids = showFluids;
        this.listener = listener;

        // 创建菜单项：物品和流体
        this.items = new TypeFilterItem[]{
            new TypeFilterItem(Component.translatable("tooltip.rtsbuilding.rightdown.type_filter_item"), this::toggleItems),
            new TypeFilterItem(Component.translatable("tooltip.rtsbuilding.rightdown.type_filter_fluid"), this::toggleFluids)
        };

        // 初始化状态数组
        this.states = new boolean[]{showItems, showFluids};

        // 自动计算每个菜单项的内容宽度（文字宽度）
        var font = Minecraft.getInstance().font;
        int[] contentWidths = new int[items.length];
        for (int i = 0; i < items.length; i++) {
            contentWidths[i] = font.width(items[i].label().getString());
        }
        setItemContentWidths(contentWidths);

        initAnims(items.length);
    }

    // ======================== 状态管理 ========================

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

    // ======================== BasePopup 实现 ========================

    @Override
    protected int getItemCount() {
        return items.length;
    }

    @Override
    protected void renderItem(GuiGraphics g, int index, int itemY, float hoverT) {
        // 文字（居中对齐）
        int textColor = hoverT > 0.5f ? ThemeManager.getHoverTextColor() : ThemeManager.getTextColor();
        String label = items[index].label().getString();
        int textX = x + (getPopupWidth() - Minecraft.getInstance().font.width(label)) / 2;
        int textY = itemY + (getItemHeight() - Minecraft.getInstance().font.lineHeight) / 2 + 1;
        TextRenderer.draw(g, label, textX, textY, textColor);
    }

    @Override
    protected boolean onItemClick(int index) {
        // 执行对应的动作
        if (items[index].action() != null) {
            items[index].action().run();
        }
        return true;
    }
}