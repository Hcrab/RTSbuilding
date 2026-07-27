package com.rtsbuilding.rtsbuilding.client.presentation.panel.topbar;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import com.rtsbuilding.rtsbuilding.client.input.RtsKeyMappings;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.base.popup.BasePopup;
import com.rtsbuilding.rtsbuilding.client.util.animate.AnimFloat;
import com.rtsbuilding.rtsbuilding.client.util.render.CrossFadeRenderer;
import com.rtsbuilding.rtsbuilding.client.util.render.SpriteRenderer;
import com.rtsbuilding.rtsbuilding.client.util.render.TextRenderer;
import com.rtsbuilding.rtsbuilding.client.util.render.model.NineSliceRegion;
import com.rtsbuilding.rtsbuilding.client.util.render.model.SpriteRegion;
import com.rtsbuilding.rtsbuilding.client.util.render.model.TextureInfo;
import com.rtsbuilding.rtsbuilding.client.util.theme.ThemeManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Consumer;

public final class ModeSwitcher {

    

    public enum Mode {
        INTERACTIVE(0, "interactive"),
        BUILD(1, "build"),
        BLUEPRINT(2, "blueprint");

        final int index;
        final String langKey;

        Mode(int index, String name) {
            this.index = index;
            this.langKey = "screen.rtsbuilding.mode." + name;
        }

        public Component getDisplayName() {
            return Component.translatable(langKey);
        }
    }

    

    
    private static final ResourceLocation MODE_BG_TEXTURE = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/base/base_ui/base_ui_6.png");
    private static final int MODE_BG_TEX_W = 32;
    private static final int MODE_BG_TEX_H = 32;
    
    private static final int MODE_BG_NORMAL_H = 16;
    
    private static final int MODE_BG_BORDER = 4;

    private static final TextureInfo MODE_BG_TEX_INFO = new TextureInfo(
            MODE_BG_TEXTURE, MODE_BG_TEX_W, MODE_BG_TEX_H,
            TextureInfo.ThemeLayout.HORIZONTAL_PAIR,
            TextureInfo.FilterMode.PIXEL);

    private static final NineSliceRegion MODE_BG_NINE_SLICE = NineSliceRegion.fullTheme(
            MODE_BG_TEX_INFO, MODE_BG_NORMAL_H, MODE_BG_BORDER);

    

    private static final ResourceLocation BLUEPRINT_MODE_TEX = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/top/blueprint_mode.png");
    private static final int BLUEPRINT_MODE_TEX_W = 128;
    private static final int BLUEPRINT_MODE_TEX_H = 64;
    private static final TextureInfo BLUEPRINT_TEX_INFO = new TextureInfo(
            BLUEPRINT_MODE_TEX, BLUEPRINT_MODE_TEX_W, BLUEPRINT_MODE_TEX_H,
            TextureInfo.ThemeLayout.HORIZONTAL_PAIR,
            TextureInfo.FilterMode.PIXEL);

    private static final ResourceLocation BUILD_MODE_TEX = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/top/build_mode.png");
    private static final int BUILD_MODE_TEX_W = 128;
    private static final int BUILD_MODE_TEX_H = 64;
    private static final TextureInfo BUILD_TEX_INFO = new TextureInfo(
            BUILD_MODE_TEX, BUILD_MODE_TEX_W, BUILD_MODE_TEX_H,
            TextureInfo.ThemeLayout.HORIZONTAL_PAIR,
            TextureInfo.FilterMode.PIXEL);

    private static final ResourceLocation INTERACTIVE_MODE_TEX = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/top/interactive_mode.png");
    private static final int INTERACTIVE_MODE_TEX_W = 128;
    private static final int INTERACTIVE_MODE_TEX_H = 96;
    private static final TextureInfo INTERACTIVE_TEX_INFO = new TextureInfo(
            INTERACTIVE_MODE_TEX, INTERACTIVE_MODE_TEX_W, INTERACTIVE_MODE_TEX_H,
            TextureInfo.ThemeLayout.HORIZONTAL_PAIR,
            TextureInfo.FilterMode.PIXEL);

    

    
    private static final ResourceLocation FOLD_ARROW_TEX = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/base/arrow.png");
    private static final int FOLD_ARROW_TEX_W = 1024;
    private static final int FOLD_ARROW_TEX_H = 512;
    private static final int FOLD_ARROW_HALF_W = 512;
    private static final int FOLD_ARROW_STATE_H = 512;

    private static final TextureInfo FOLD_ARROW_TEX_INFO = new TextureInfo(
            FOLD_ARROW_TEX, FOLD_ARROW_TEX_W, FOLD_ARROW_TEX_H,
            TextureInfo.ThemeLayout.HORIZONTAL_PAIR,
            TextureInfo.FilterMode.PIXEL);

    

    
    private static final int SWITCHER_HEIGHT = 14;
    
    private static final int ICON_SIZE = 12;
    
    private static final int ICON_TEXT_GAP = 3;
    
    private static final int ARROW_SIZE = 8;
    
    private static final int TEXT_ARROW_GAP = 5;
    
    private static final int PAD_H = 5;
    
    private static final int MARGIN_LEFT = 2;

    

    
    private static final int POPUP_ITEM_HEIGHT = 22;
    
    private static final int POPUP_PAD_H = 6;
    
    private static final int POPUP_ICON_SIZE = 14;
    
    private static final int POPUP_ICON_SLOT_W = POPUP_ICON_SIZE;
    
    private static final int POPUP_SHORTCUT_GAP = 16;

    
    private static final int LIGHT_SHORTCUT_COLOR = 0xFF777777;
    
    private static final int DARK_SHORTCUT_COLOR = 0xFF888888;

    

    
    private Mode currentMode = Mode.INTERACTIVE;

    
    private final AnimFloat hoverState = AnimFloat.hover();

    
    private Consumer<Mode> onModeChange;

    
    private final ModePopup popup;

    
    private final int fixedWidth;

    
    private final AnimFloat arrowAnim = AnimFloat.hover();

    public ModeSwitcher() {
        this.popup = new ModePopup(this);
        this.fixedWidth = computeFixedWidth();
    }

    
    private int computeFixedWidth() {
        var font = Minecraft.getInstance().font;
        int maxTextWidth = 0;
        for (Mode mode : Mode.values()) {
            int tw = font.width(mode.getDisplayName());
            if (tw > maxTextWidth) maxTextWidth = tw;
        }
        return PAD_H * 2 + ICON_SIZE + ICON_TEXT_GAP + maxTextWidth + TEXT_ARROW_GAP + ARROW_SIZE;
    }

    

    public Mode getCurrentMode() {
        return currentMode;
    }

    public void setMode(Mode mode) {
        this.currentMode = mode;
        if (onModeChange != null) {
            onModeChange.accept(mode);
        }
    }

    
    public void setOnModeChange(Consumer<Mode> callback) {
        this.onModeChange = callback;
    }

    
    public void cycleMode() {
        Mode[] modes = Mode.values();
        int next = (currentMode.index + 1) % modes.length;
        setMode(modes[next]);
    }

    
    public boolean isPopupOpen() {
        return popup.isOpen();
    }

    
    public boolean isMouseOverPopup(int mx, int my) {
        return popup.isOpen() && popup.contains(mx, my);
    }

    

    
    public int getX() {
        return MARGIN_LEFT;
    }

    
    public int getY() {
        int bottomBarY = TopBarLayoutHelper.TOP_BAR_HEIGHT + TopBarLayoutHelper.SCREEN_BORDER;
        return bottomBarY + (TopBarLayoutHelper.BOTTOM_SRC_H - SWITCHER_HEIGHT) / 2;
    }

    
    public int getWidth() {
        return fixedWidth;
    }

    

    
    private SpriteRegion getModeIconRegion(Mode mode) {
        return switch (mode) {
            case INTERACTIVE -> new SpriteRegion(
                    INTERACTIVE_TEX_INFO, 0, 0,
                    INTERACTIVE_TEX_INFO.halfWidth(), INTERACTIVE_TEX_INFO.halfHeight());
            case BUILD -> new SpriteRegion(
                    BUILD_TEX_INFO, 0, 0,
                    BUILD_TEX_INFO.halfWidth(), BUILD_TEX_INFO.halfHeight());
            case BLUEPRINT -> new SpriteRegion(
                    BLUEPRINT_TEX_INFO, 0, 0,
                    BLUEPRINT_TEX_INFO.halfWidth(), BLUEPRINT_TEX_INFO.halfHeight());
        };
    }

    
    private static int getIconDrawWidth(Mode mode, int drawH) {
        return switch (mode) {
            case INTERACTIVE -> drawH * INTERACTIVE_TEX_INFO.halfWidth() / INTERACTIVE_TEX_INFO.halfHeight();
            case BUILD -> drawH * BUILD_TEX_INFO.halfWidth() / BUILD_TEX_INFO.halfHeight();
            case BLUEPRINT -> drawH * BLUEPRINT_TEX_INFO.halfWidth() / BLUEPRINT_TEX_INFO.halfHeight();
        };
    }

    

    
    public void render(GuiGraphics g, int mouseX, int mouseY) {
        int x = getX();
        int y = getY();
        int w = getWidth();

        
        boolean hovering = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + SWITCHER_HEIGHT;
        hoverState.track(hovering);

        
        NineSliceRegion bgNormal = MODE_BG_NINE_SLICE.withTheme();
        NineSliceRegion bgHover = MODE_BG_NINE_SLICE.withVOffset(MODE_BG_NORMAL_H).withTheme();
        CrossFadeRenderer.render(g, hoverState.get(),
                () -> SpriteRenderer.drawNineSlice(g, bgNormal, x, y, w, SWITCHER_HEIGHT),
                () -> SpriteRenderer.drawNineSlice(g, bgHover, x, y, w, SWITCHER_HEIGHT));

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        
        int iconH = ICON_SIZE;
        int iconW = getIconDrawWidth(currentMode, iconH);
        int iconX = x + PAD_H + (ICON_SIZE - iconW) / 2;
        int iconY = y + (SWITCHER_HEIGHT - iconH) / 2;
        SpriteRegion iconRegion = getModeIconRegion(currentMode).withTheme();
        SpriteRenderer.drawSprite(g, iconRegion, iconX, iconY, iconW, iconH);

        
        int textX = x + PAD_H + ICON_SIZE + ICON_TEXT_GAP;
        int textY = iconY + (iconH - Minecraft.getInstance().font.lineHeight) / 2 + 1;
        int textColor = ThemeManager.getTextColor();
        TextRenderer.draw(g, currentMode.getDisplayName(), textX, textY, textColor);

        
        int arrowX = textX + Minecraft.getInstance().font.width(currentMode.getDisplayName()) + TEXT_ARROW_GAP;
        int arrowY = y + (SWITCHER_HEIGHT - ARROW_SIZE) / 2;
        renderArrow(g, arrowX, arrowY);

        RenderSystem.disableBlend();
    }

    
    public void renderPopup(GuiGraphics g, int mouseX, int mouseY) {
        if (popup.isOpen()) {
            popup.setPosition(getX(), getY() + SWITCHER_HEIGHT);
            popup.render(g, mouseX, mouseY);
        }
    }

    
    private void renderArrow(GuiGraphics g, int x, int y) {

        SpriteRegion arrowRegion = new SpriteRegion(
                FOLD_ARROW_TEX_INFO, 0, 0, FOLD_ARROW_HALF_W, FOLD_ARROW_STATE_H).withTheme();
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        float half = ARROW_SIZE / 2.0f;
        g.pose().translate(half, half, 0);
        g.pose().mulPose(Axis.ZP.rotationDegrees((1.0f + this.arrowAnim.get()) * 90.0f));
        g.pose().translate(-half, -half, 0);
        SpriteRenderer.drawSprite(g, arrowRegion, 0, 0, ARROW_SIZE, ARROW_SIZE);
        g.pose().popPose();
    }

    

    
    public boolean mouseClicked(int mx, int my) {
        int x = getX();
        int y = getY();
        int w = getWidth();

        
        if (popup.isOpen()) {
            if (popup.contains(mx, my)) {
                return popup.handleClick(mx, my);
            }
            popup.close();
            arrowAnim.target(0.0f);
            return true;
        }

        
        if (mx >= x && mx < x + w && my >= y && my < y + SWITCHER_HEIGHT) {
            popup.toggle();
            arrowAnim.target(1.0f);
            return true;
        }

        return false;
    }

    

    
    private static final class ModePopup extends BasePopup {

        private final ModeSwitcher switcher;

        ModePopup(ModeSwitcher switcher) {
            this.switcher = switcher;
            initAnims(Mode.values().length);
            
            var font = Minecraft.getInstance().font;
            Mode[] modes = Mode.values();
            int[] widths = new int[modes.length];
            int shortcutW = font.width(RtsKeyMappings.CYCLE_MODE_KEY.getTranslatedKeyMessage());
            for (int i = 0; i < modes.length; i++) {
                widths[i] = POPUP_ICON_SLOT_W + 4 + font.width(modes[i].getDisplayName()) + POPUP_SHORTCUT_GAP + shortcutW;
            }
            setItemContentWidths(widths);
        }

        @Override
        protected int getItemCount() {
            return Mode.values().length;
        }

        @Override
        protected int getItemHeight() {
            return POPUP_ITEM_HEIGHT;
        }

        @Override
        protected int getPadH() {
            return POPUP_PAD_H;
        }

        @Override
        protected void renderItem(GuiGraphics g, int index, int itemY, float hoverT) {
            Mode mode = Mode.values()[index];

            
            int iconH = POPUP_ICON_SIZE;
            int iconW = switcher.getIconDrawWidth(mode, iconH);
            int iconX = x + getPadH() + (POPUP_ICON_SLOT_W - iconW) / 2;
            int iconY = itemY + (getItemHeight() - iconH) / 2;
            SpriteRegion iconRegion = switcher.getModeIconRegion(mode).withTheme();
            SpriteRenderer.drawSprite(g, iconRegion, iconX, iconY, iconW, iconH);

            
            int textColor = hoverT > 0.5f
                    ? ThemeManager.getHoverTextColor()
                    : ThemeManager.getTextColor();
            String label = mode.getDisplayName().getString();
            int textX = x + getPadH() + POPUP_ICON_SLOT_W + 4;
            int textY = iconY + (iconH - Minecraft.getInstance().font.lineHeight) / 2 + 1;
            TextRenderer.draw(g, label, textX, textY, textColor);

            
            int shortcutColor = ThemeManager.getInstance().isLightMode() ? LIGHT_SHORTCUT_COLOR : DARK_SHORTCUT_COLOR;
            String shortcutLabel = RtsKeyMappings.CYCLE_MODE_KEY.getTranslatedKeyMessage().getString();
            int shortcutX = x + getPopupWidth() - getPadH() - Minecraft.getInstance().font.width(shortcutLabel);
            TextRenderer.draw(g, shortcutLabel, shortcutX, textY, shortcutColor);
        }

        @Override
        protected boolean onItemClick(int index) {
            Mode selectedMode = Mode.values()[index];
            if (selectedMode != switcher.currentMode) {
                switcher.setMode(selectedMode);
            }
            close();
            return true;
        }
    }
}

