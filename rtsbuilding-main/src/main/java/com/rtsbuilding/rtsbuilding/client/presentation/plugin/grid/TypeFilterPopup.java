package com.rtsbuilding.rtsbuilding.client.presentation.plugin.grid;

import com.rtsbuilding.rtsbuilding.client.presentation.panel.base.popup.BasePopup;
import com.rtsbuilding.rtsbuilding.client.util.render.CrossFadeRenderer;
import com.rtsbuilding.rtsbuilding.client.util.render.TextRenderer;
import com.rtsbuilding.rtsbuilding.client.util.theme.ThemeManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class TypeFilterPopup extends BasePopup {

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
