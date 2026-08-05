package com.rtsbuilding.rtsbuilding.client.presentation.panel.topbar;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import com.rtsbuilding.rtsbuilding.client.input.RtsKeyMappings;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.base.popup.BasePopup;
import com.rtsbuilding.rtsbuilding.client.util.animate.AnimFloat;
import com.rtsbuilding.rtsbuilding.client.util.render.SdfRenderer;
import com.rtsbuilding.rtsbuilding.client.util.render.TextRenderer;
import com.rtsbuilding.rtsbuilding.client.util.theme.ThemeManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

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

    



    



    

    
    private static final int SWITCHER_HEIGHT = 14;
    
    private static final int ARROW_SIZE = 11;
    
    private static final int TEXT_ARROW_GAP = 5;
    
    private static final int PAD_H = 5;
    private static final int MARGIN_LEFT = 2;



    

    private static final int POPUP_ITEM_HEIGHT = 22;
    
    private static final int POPUP_PAD_H = 6;
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
        return PAD_H * 2 + maxTextWidth + TEXT_ARROW_GAP + ARROW_SIZE;
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

    
    public int getHeight() {
        return SWITCHER_HEIGHT;
    }

    

    


    
    public void render(GuiGraphics g, int mouseX, int mouseY) {
        int x = getX();
        int y = getY();
        int w = getWidth();

        
        boolean hovering = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + SWITCHER_HEIGHT;
        hoverState.track(hovering);

        SdfRenderer.drawButtonBg(g, 3, true, false, hoverState.get(), x, y, w, SWITCHER_HEIGHT);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        
        int textX = x + PAD_H;
        int textY = y + (SWITCHER_HEIGHT - Minecraft.getInstance().font.lineHeight) / 2 + 1;
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
        g.pose().pushPose();
        float half = ARROW_SIZE / 2.0f;
        g.pose().translate(x + half, y + half, 0);
        g.pose().mulPose(Axis.ZP.rotationDegrees(this.arrowAnim.get() * 90.0f));
        g.pose().scale(2f / 3f, 2f / 3f, 1f);
        SdfRenderer.drawChevron(g, (int)-half, (int)-half, ARROW_SIZE, ARROW_SIZE,
                ThemeManager.getTextColor(), 0.5f);
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
                widths[i] = font.width(modes[i].getDisplayName()) + POPUP_SHORTCUT_GAP + shortcutW;
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

            int textColor = hoverT > 0.5f
                    ? ThemeManager.getHoverTextColor()
                    : ThemeManager.getTextColor();
            String label = mode.getDisplayName().getString();
            int textX = x + getPadH();
            int textY = itemY + (getItemHeight() - Minecraft.getInstance().font.lineHeight) / 2 + 1;
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

