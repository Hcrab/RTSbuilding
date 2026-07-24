package com.rtsbuilding.rtsbuilding.client.presentation.panel.downbar.overlay;

import com.rtsbuilding.rtsbuilding.client.presentation.panel.base.popup.BasePopup;
import com.rtsbuilding.rtsbuilding.client.util.render.CrossFadeRenderer;
import com.rtsbuilding.rtsbuilding.client.util.render.TextRenderer;
import com.rtsbuilding.rtsbuilding.client.util.theme.ThemeManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;


public final class ContainerModePopup extends BasePopup {

    

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
