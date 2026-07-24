package com.rtsbuilding.rtsbuilding.client.presentation.panel.component;

import com.rtsbuilding.rtsbuilding.client.util.animate.AnimationFactory;
import com.rtsbuilding.rtsbuilding.client.util.animate.FloatAnimation;
import com.rtsbuilding.rtsbuilding.client.util.render.CrossFadeRenderer;
import com.rtsbuilding.rtsbuilding.client.util.render.SpriteRenderer;
import com.rtsbuilding.rtsbuilding.client.util.render.model.SpriteRegion;
import com.rtsbuilding.rtsbuilding.client.util.render.model.TextureInfo;
import com.rtsbuilding.rtsbuilding.client.util.state.HoverStateManager;
import com.rtsbuilding.rtsbuilding.client.util.theme.ThemeManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;


public class ThemeSwitchComponent {

    

    
    public static final int SIZE = 32;
    
    public static final int SLIDER_SIZE = 16;

    

    
    private static final ResourceLocation SWITCH_TEXTURE = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/base/switch_ground.png");
    private static final int SWITCH_TEX_W = 48;
    private static final int SWITCH_TEX_H = 32;
    private static final int SWITCH_FRAME_H = 16;

    
    private static final ResourceLocation SLIDER_TEXTURE = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/base/base_ui/base_ui_5.png");
    private static final int SLIDER_TEX_W = 32;
    private static final int SLIDER_TEX_H = 48;
    private static final int SLIDER_FRAME_W = 16;
    private static final int SLIDER_FRAME_H = 16;

    private static final int SLIDER_U_DARK  = 0;
    private static final int SLIDER_U_LIGHT = 16;

    private static final int SLIDER_V_DEFAULT       = 0;
    private static final int SLIDER_V_HOVER_DEFAULT  = 16;



    

    
    private static final TextureInfo SWITCH_TEX_INFO = new TextureInfo(
            SWITCH_TEXTURE, SWITCH_TEX_W, SWITCH_TEX_H,
            TextureInfo.ThemeLayout.HORIZONTAL_PAIR,
            TextureInfo.FilterMode.PIXEL);
    
    private static final TextureInfo SLIDER_TEX_INFO = new TextureInfo(
            SLIDER_TEXTURE, SLIDER_TEX_W, SLIDER_TEX_H,
            TextureInfo.ThemeLayout.HORIZONTAL_PAIR,
            TextureInfo.FilterMode.PIXEL);

    

    
    private static final int HITBOX_INSET_H = 0;
    
    private static final int HITBOX_INSET_V = 9;

    
    private static final int AREA_W = SIZE - HITBOX_INSET_H * 2;
    private static final int AREA_H = SIZE - HITBOX_INSET_V * 2;

    
    private int areaX, areaY;

    
    private final HoverStateManager hoverState = new HoverStateManager();

    
    private final FloatAnimation slideAnim;
    private boolean lastOn;

    public ThemeSwitchComponent() {
        this.slideAnim = AnimationFactory.newSlideAnim();
        this.slideAnim.snapTo(-1.0f);
    }

    

    
    public void render(GuiGraphics g, int mouseX, int mouseY, int switchX, int switchY, boolean on) {
        
        this.areaX = switchX + HITBOX_INSET_H;
        this.areaY = switchY + HITBOX_INSET_V;

        boolean lightMode = ThemeManager.getInstance().isLightMode();

        
        if (on != lastOn) {
            lastOn = on;
            slideAnim.start(on ? (float) (SLIDER_FRAME_W + 1) : -1.0f);
        }
        slideAnim.tick();
        float slideOffset = slideAnim.getValue();

        int sliderX = switchX + Math.round(slideOffset);

        
        boolean hovered = mouseX >= areaX && mouseX < areaX + AREA_W
                && mouseY >= areaY && mouseY < areaY + AREA_H;

        float hoverT = this.hoverState.update(hovered);

        
        int bgHalfW = SWITCH_TEX_INFO.halfWidth();  
        int bgH = SIZE * SWITCH_FRAME_H / bgHalfW;  
        int bgY = switchY + (SIZE - bgH) / 2;        

        if (SWITCH_TEXTURE != null) {
            SpriteRegion bgRegion = new SpriteRegion(SWITCH_TEX_INFO, 0, 0, bgHalfW, SWITCH_FRAME_H);
            SpriteRenderer.drawSprite(g, bgRegion.withThemeAndVOffset(on ? SWITCH_FRAME_H : 0),
                    switchX, bgY, SIZE, bgH);
        }

        
        int sliderY = bgY + (bgH - SLIDER_FRAME_H) / 2;
        int sliderU = lightMode ? SLIDER_U_LIGHT : SLIDER_U_DARK;
        int slVDefault = SLIDER_V_DEFAULT;
        int slVHover   = SLIDER_V_HOVER_DEFAULT;

        if (SLIDER_TEXTURE != null) {
            renderSwitchFrame(g, SLIDER_TEX_INFO,
                    sliderX, sliderY, SLIDER_FRAME_W, SLIDER_FRAME_H,
                    sliderU, slVDefault, slVHover, hoverT);
        }
    }

    

    
    public boolean handleClick(double mouseX, double mouseY) {
        return mouseX >= areaX && mouseX < areaX + AREA_W
                && mouseY >= areaY && mouseY < areaY + AREA_H;
    }

    

    private static void renderSwitchFrame(GuiGraphics g, TextureInfo texInfo,
                                          int screenX, int screenY, int sw, int sh,
                                          int u, int vDefault, int vHover, float t) {
        SpriteRegion normal = new SpriteRegion(texInfo, u, vDefault, sw, sh);
        SpriteRegion hovered = new SpriteRegion(texInfo, u, vHover, sw, sh);
        CrossFadeRenderer.render(g, t,
                () -> SpriteRenderer.drawSprite(g, normal, screenX, screenY, sw, sh),
                () -> SpriteRenderer.drawSprite(g, hovered, screenX, screenY, sw, sh));
    }
}
