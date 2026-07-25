package com.rtsbuilding.rtsbuilding.client.presentation.panel.topbar.popup;

import com.rtsbuilding.rtsbuilding.client.presentation.panel.base.popup.BasePopup;
import com.rtsbuilding.rtsbuilding.client.util.render.CrossFadeRenderer;
import com.rtsbuilding.rtsbuilding.client.util.render.TextRenderer;
import com.rtsbuilding.rtsbuilding.client.util.theme.ThemeManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;


public final class DebugMenuPopup extends BasePopup {

    

    
    private boolean debugOverlayEnabled = false;
    
    private boolean chunkBorderVisible = true;
    
    private boolean collisionBoxVisible = true;

    
    private boolean chunkBorderRenderingActive;
    
    private boolean collisionBoxRenderingActive;

    

    
    public record DebugToggleItem(Component label, ToggleAction action) {}

    @FunctionalInterface
    public interface ToggleAction {
        void onToggle(boolean newState);
    }

    private final DebugToggleItem[] items;
    private final boolean[] states;

    

    
    private static final ResourceLocation MODE_BUTTON_TEXTURE =
            ResourceLocation.tryParse("rtsbuilding:textures/gui/base/base_ui/base_ui_5.png");
    private static final int MODE_BTN_TEX_W = 32;
    private static final int MODE_BTN_TEX_H = 48;
    
    private static final int MODE_BTN_SIZE = 16;
    
    private static final int MODE_BTN_STATE_H = 16;

    
    private static final int BTN_TEXT_GAP = 4;

    

    public DebugMenuPopup() {
        List<DebugToggleItem> itemsList = new ArrayList<>();

        
        itemsList.add(new DebugToggleItem(
                Component.translatable("screen.rtsbuilding.debug.chunk_border"),
                state -> {
                    boolean was = this.chunkBorderVisible;
                    this.chunkBorderVisible = state;
                    
                    if (this.debugOverlayEnabled && was != state) {
                        syncChunkBorder(state);
                    }
                }));

        
        itemsList.add(new DebugToggleItem(
                Component.translatable("screen.rtsbuilding.debug.collision_box"),
                state -> {
                    boolean was = this.collisionBoxVisible;
                    this.collisionBoxVisible = state;
                    
                    if (this.debugOverlayEnabled && was != state) {
                        syncCollisionBox(state);
                    }
                }));

        this.items = itemsList.toArray(new DebugToggleItem[0]);
        this.states = new boolean[]{true, true};

        
        var font = Minecraft.getInstance().font;
        int[] contentWidths = new int[items.length];
        for (int i = 0; i < items.length; i++) {
            contentWidths[i] = MODE_BTN_SIZE + BTN_TEXT_GAP + font.width(items[i].label());
        }
        setItemContentWidths(contentWidths);

        initAnims(items.length);
    }

    

    public boolean isDebugOverlayEnabled() { return debugOverlayEnabled; }

    

    
    public void toggleDebugOverlay() {
        debugOverlayEnabled = !debugOverlayEnabled;
        if (debugOverlayEnabled) {
            enableAllDebugFeatures();
        } else {
            disableAllDebugFeatures();
        }
    }

    
    public void setItemState(int index, boolean state) {
        if (index >= 0 && index < states.length) {
            states[index] = state;
        }
    }

    

    
    public void onRtsExited() {
        
        disableAllDebugFeatures();
    }

    
    public void onPostUiStateLoad() {
        if (debugOverlayEnabled) {
            enableAllDebugFeatures();
        }
    }

    

    
    private void enableAllDebugFeatures() {
        if (chunkBorderVisible) {
            syncChunkBorder(true);
        }
        if (collisionBoxVisible) {
            syncCollisionBox(true);
        }
    }

    
    private void disableAllDebugFeatures() {
        syncChunkBorder(false);
        syncCollisionBox(false);
    }

    
    private void syncChunkBorder(boolean desired) {
        try {
            Field f = DebugRenderer.class
                    .getDeclaredField("renderChunkborder");
            f.setAccessible(true);
            boolean actual = f.getBoolean(Minecraft.getInstance().debugRenderer);
            if (actual != desired) {
                Minecraft.getInstance().debugRenderer.switchRenderChunkborder();
            }
            this.chunkBorderRenderingActive = desired;
        } catch (Exception e) {
            
            if (desired != chunkBorderRenderingActive) {
                Minecraft.getInstance().debugRenderer.switchRenderChunkborder();
                chunkBorderRenderingActive = desired;
            }
        }
    }

    
    private void syncCollisionBox(boolean desired) {
        var dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        if (dispatcher.shouldRenderHitBoxes() != desired) {
            dispatcher.setRenderHitBoxes(desired);
        }
        this.collisionBoxRenderingActive = desired;
    }

    @Override
    protected int getItemCount() {
        return items.length;
    }

    @Override
    protected void renderItem(GuiGraphics g, int index, int itemY, float hoverT) {
        
        int textColor = hoverT > 0.5f ? ThemeManager.getHoverTextColor() : ThemeManager.getTextColor();
        String label = items[index].label().getString();
        int textX = x + getPadH();
        int textY = itemY + (getItemHeight() - Minecraft.getInstance().font.lineHeight) / 2 + 1;
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
        
        states[index] = !states[index];
        
        if (items[index].action() != null) {
            items[index].action().onToggle(states[index]);
        }
        return true;
    }
}
