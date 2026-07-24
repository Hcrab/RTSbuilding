package com.rtsbuilding.rtsbuilding.client.presentation.panel.topbar.group_button;

import com.rtsbuilding.rtsbuilding.client.infrastructure.module.camera.CameraModule;
import com.rtsbuilding.rtsbuilding.client.input.RtsKeyMappings;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.base.button.AbstractButtonGroup;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.topbar.TopBarLayoutHelper;
import com.rtsbuilding.rtsbuilding.client.util.animate.ColorAnimation;
import com.rtsbuilding.rtsbuilding.client.util.state.TooltipController;
import com.rtsbuilding.rtsbuilding.client.util.theme.ThemeManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;


public final class CameraModeGroup extends AbstractButtonGroup {

    private static final ResourceLocation FREE_MODE =
            ResourceLocation.tryParse("rtsbuilding:textures/gui/top/button/free_mode.png");
    private static final ResourceLocation SURROUND_MODE =
            ResourceLocation.tryParse("rtsbuilding:textures/gui/top/button/surround_mode.png");

    

    
    private static final ResourceLocation DOWN_BG = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/base/button/down_button.png");
    
    private static final ResourceLocation MIDDLE_BG = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/base/button/middle_button.png");
    
    private static final ResourceLocation UP_BG = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/base/button/up_button.png");

    private final CameraModule cameraModule;

    
    private final TooltipController freeModeTooltip = TooltipController.builder().build();
    private final TooltipController surroundModeTooltip = TooltipController.builder().build();

    public CameraModeGroup(CameraModule cameraModule) {
        
        super(Direction.HORIZONTAL, TopBarLayoutHelper.BTN_SIZE, DEFAULT_INNER_GAP, true,
                DOWN_BG, MIDDLE_BG, UP_BG,
                FREE_MODE, SURROUND_MODE);
        this.cameraModule = cameraModule;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, TopBarLayoutHelper.ButtonGroup group) {
        
        selected[0] = !cameraModule.isPlayerOrbitMode();  
        selected[1] = cameraModule.isPlayerOrbitMode();   
        super.render(g, mouseX, mouseY, group);
    }

    @Override
    protected void renderExtra(GuiGraphics g, int mouseX, int mouseY, TopBarLayoutHelper.ButtonGroup group) {
        
        {
            var rect = group.rect(0);
            boolean hovered = rect.contains(mouseX, mouseY);
                        freeModeTooltip.update(hovered, false);
        }
        
        {
            var rect = group.rect(1);
            boolean hovered = rect.contains(mouseX, mouseY);
                        surroundModeTooltip.update(hovered, false);
        }
    }

    
    public void renderTooltipOverlay(GuiGraphics g, TopBarLayoutHelper.ButtonGroup group,
                                     int screenW, int screenH) {
        String keyText = RtsKeyMappings.TOGGLE_CAMERA_MODE_KEY.getTranslatedKeyMessage().getString();
        int textColor = ThemeManager.getTextColor();
        int shortcutColor = ColorAnimation.scale(textColor, 0.6f);

        
        if (freeModeTooltip.shouldRender()) {
            var rect = group.rect(0);
            String text = Component.translatable("tooltip.rtsbuilding.camera.free_mode").getString() + "\n"
                    + Component.translatable("tooltip.rtsbuilding.camera.free_mode.desc").getString() + "\n"
                    + Component.translatable("tooltip.rtsbuilding.shortcut", keyText).getString();
            freeModeTooltip.render(g, rect.x(), rect.y(), rect.width(), rect.height(),
                    text, textColor, shortcutColor, screenW, screenH);
        }

        
        if (surroundModeTooltip.shouldRender()) {
            var rect = group.rect(1);
            String text = Component.translatable("tooltip.rtsbuilding.camera.surround_mode").getString() + "\n"
                    + Component.translatable("tooltip.rtsbuilding.camera.surround_mode.desc").getString() + "\n"
                    + Component.translatable("tooltip.rtsbuilding.shortcut", keyText).getString();
            surroundModeTooltip.render(g, rect.x(), rect.y(), rect.width(), rect.height(),
                    text, textColor, shortcutColor, screenW, screenH);
        }
    }

    @Override
    protected void onButtonClick(int index) {
        if (index == 0) {
            
            if (cameraModule.isPlayerOrbitMode()) {
                cameraModule.disablePlayerOrbitMode();
            }
        } else {
            
            if (!cameraModule.isPlayerOrbitMode()) {
                cameraModule.enablePlayerOrbitMode();
            }
        }
        
    }
}

