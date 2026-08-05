package com.rtsbuilding.rtsbuilding.client.presentation.panel.topbar.popup;

import com.rtsbuilding.rtsbuilding.client.input.RtsKeyMappings;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.base.popup.BasePopup;
import com.rtsbuilding.rtsbuilding.client.util.render.SpriteRenderer;
import com.rtsbuilding.rtsbuilding.client.util.render.TextRenderer;
import com.rtsbuilding.rtsbuilding.client.util.render.model.SpriteRegion;
import com.rtsbuilding.rtsbuilding.client.util.render.model.TextureInfo;
import com.rtsbuilding.rtsbuilding.client.util.theme.ThemeManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public final class LogoMenuPopup extends BasePopup {

    public boolean isGearMenuOpen() {
        return gearMenuOpen;
    }

    
    public record MenuItem(Component label, Runnable action) {}

    private final List<MenuItem> items;

    

    
    private boolean gearMenuOpen;
    
    private Runnable onGearMenuToggle;

    

    
    private static final ResourceLocation SETTING_TEXTURE = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/top/setting.png");
    private static final int SETTING_TEX_W = 1024;
    private static final int SETTING_TEX_H = 512;
    private static final int SETTING_HALF_W = 512;
    private static final int SETTING_ICON_SIZE = 17;

    

    
    private static final int LIGHT_SHORTCUT_COLOR = 0xFF777777;
    
    private static final int DARK_SHORTCUT_COLOR = 0xFF888888;

    
    private static final int LABEL_TO_SHORTCUT_GAP = 12;

    public LogoMenuPopup() {
        this.items = new ArrayList<>();
        
        this.items.add(new MenuItem(
                Component.translatable("screen.rtsbuilding.settings.title"),
                () -> {
                    if (onGearMenuToggle != null) {
                        onGearMenuToggle.run();
                    }
                }));
        
        var font = Minecraft.getInstance().font;
        int[] contentWidths = new int[items.size()];
        for (int i = 0; i < items.size(); i++) {
            int labelWidth = font.width(items.get(i).label());
            int keyWidth = font.width(RtsKeyMappings.OPEN_GEAR_MENU_KEY.getTranslatedKeyMessage());
            contentWidths[i] = SETTING_ICON_SIZE + 4 + labelWidth + LABEL_TO_SHORTCUT_GAP + keyWidth;
        }
        setItemContentWidths(contentWidths);

        initAnims(items.size());
    }

    

    
    public void setGearMenuOpen(boolean open) {
        this.gearMenuOpen = open;
    }

    
    public void setOnGearMenuToggle(Runnable toggle) {
        this.onGearMenuToggle = toggle;
    }

    

    @Override
    protected int getItemCount() {
        return items.size();
    }

    @Override
    protected void renderItem(GuiGraphics g, int index, int itemY, float hoverT) {
        
        int iconX = x + getPadH();
        int iconY = itemY + (getItemHeight() - SETTING_ICON_SIZE) / 2;
        TextureInfo settingTex = new TextureInfo(
                SETTING_TEXTURE, SETTING_TEX_W, SETTING_TEX_H,
                TextureInfo.ThemeLayout.HORIZONTAL_PAIR,
                TextureInfo.FilterMode.PIXEL);
        SpriteRegion iconRegion = new SpriteRegion(settingTex, 0, 0, SETTING_HALF_W, SETTING_TEX_H).withTheme();
        SpriteRenderer.drawSprite(g, iconRegion,
                iconX, iconY, SETTING_ICON_SIZE, SETTING_ICON_SIZE);

        
        int textColor = hoverT > 0.5f ? ThemeManager.getHoverTextColor() : ThemeManager.getTextColor();
        String label = items.get(index).label().getString();
        int textX = iconX + SETTING_ICON_SIZE + 4;
        int textY = iconY + (SETTING_ICON_SIZE - Minecraft.getInstance().font.lineHeight) / 2 + 1;
        TextRenderer.draw(g, label, textX, textY, textColor);

        
        int shortcutColor = ThemeManager.getInstance().isLightMode() ? LIGHT_SHORTCUT_COLOR : DARK_SHORTCUT_COLOR;
        String shortcutLabel = RtsKeyMappings.OPEN_GEAR_MENU_KEY.getTranslatedKeyMessage().getString();
        int shortcutX = textX + Minecraft.getInstance().font.width(label) + LABEL_TO_SHORTCUT_GAP;
        TextRenderer.draw(g, shortcutLabel, shortcutX, textY, shortcutColor);
    }

    @Override
    protected boolean onItemClick(int index) {
        
        close();
        items.get(index).action().run();
        return true;
    }
}
