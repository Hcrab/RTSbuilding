package com.rtsbuilding.rtsbuilding.client.presentation.panel.topbar.group_button;

import com.mojang.math.Axis;
import com.rtsbuilding.rtsbuilding.client.input.RtsKeyMappings;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.base.button.AbstractButtonGroup;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.topbar.TopBarLayoutHelper;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.topbar.popup.DebugMenuPopup;
import com.rtsbuilding.rtsbuilding.client.util.animate.AnimFloat;
import com.rtsbuilding.rtsbuilding.client.util.animate.ColorAnimation;
import com.rtsbuilding.rtsbuilding.client.util.render.SdfRenderer;
import com.rtsbuilding.rtsbuilding.client.util.state.TooltipController;
import com.rtsbuilding.rtsbuilding.client.util.theme.ThemeManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class UtilityButtonGroup extends AbstractButtonGroup {

    

    
    private static final ResourceLocation DOWN_BG = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/base/button/down_button.png");
    
    private static final ResourceLocation MIDDLE_BG = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/base/button/middle_button.png");
    
    private static final ResourceLocation UP_BG = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/base/button/up_button.png");

    private static final ResourceLocation CHUNK_DISPLAY =
            ResourceLocation.tryParse("rtsbuilding:textures/gui/top/button/chunk_display.png");

    private static final int FOLD_ARROW_SIZE = 11;

    private final DebugMenuPopup debugPopup;

    
    private final TooltipController chunkBtnTooltip = TooltipController.builder().build();

    
    private final AnimFloat arrowRotateAnim = AnimFloat.expand();
    private boolean prevArrowActive;

    public UtilityButtonGroup(DebugMenuPopup debugPopup) {
        
        super(Direction.HORIZONTAL, TopBarLayoutHelper.BTN_SIZE, DEFAULT_INNER_GAP, true,
                DOWN_BG, MIDDLE_BG, UP_BG,
                null, CHUNK_DISPLAY);
        this.debugPopup = debugPopup;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, TopBarLayoutHelper.ButtonGroup group) {
        
        selected[0] = debugPopup != null && debugPopup.isOpen();            
        selected[1] = debugPopup != null && debugPopup.isDebugOverlayEnabled(); 
        super.render(g, mouseX, mouseY, group);
    }

    @Override
    protected void renderExtra(GuiGraphics g, int mouseX, int mouseY, TopBarLayoutHelper.ButtonGroup group) {
        tickChunkTooltip(mouseX, mouseY, group);
        renderFoldArrow(g, group);
    }

    

    private void tickChunkTooltip(int mouseX, int mouseY, TopBarLayoutHelper.ButtonGroup group) {
        var rect = group.rect(1);
        boolean hovered = rect.contains(mouseX, mouseY);
        boolean popupOpen = debugPopup != null && debugPopup.isOpen();
                chunkBtnTooltip.update(hovered, popupOpen);
    }

    
    public void renderTooltipOverlay(GuiGraphics g, TopBarLayoutHelper.ButtonGroup group,
                                      int screenW, int screenH) {
        if (!chunkBtnTooltip.shouldRender()) return;

        var rect = group.rect(1);
        String keyText = RtsKeyMappings.TOGGLE_DEBUG_OVERLAY_KEY.getTranslatedKeyMessage().getString();
        int textColor = ThemeManager.getTextColor();
        int shortcutColor = ColorAnimation.scale(textColor, 0.6f);
        String text = Component.translatable("tooltip.rtsbuilding.debug.overlay").getString() + "\n"
                + Component.translatable("tooltip.rtsbuilding.debug.overlay.desc").getString() + "\n"
                + Component.translatable("tooltip.rtsbuilding.shortcut", keyText).getString();
        chunkBtnTooltip.render(g, rect.x(), rect.y(), rect.width(), rect.height(),
                text, textColor, shortcutColor, screenW, screenH);
    }

    

    private void renderFoldArrow(GuiGraphics g, TopBarLayoutHelper.ButtonGroup group) {
        var rect = group.rect(0);
        boolean arrowActive = debugPopup != null && debugPopup.isOpen();
        if (arrowActive != prevArrowActive) {
            prevArrowActive = arrowActive;
            arrowRotateAnim.target(arrowActive ? 1.0f : 0.0f);
        }

        int arrowX = rect.x() + (rect.width() - FOLD_ARROW_SIZE) / 2;
        int arrowY = rect.y() + (rect.height() - FOLD_ARROW_SIZE) / 2;
        g.pose().pushPose();
        float halfArrow = FOLD_ARROW_SIZE / 2.0f;
        g.pose().translate(arrowX + halfArrow, arrowY + halfArrow, 0);
        g.pose().mulPose(Axis.ZP.rotationDegrees(arrowRotateAnim.get() * 90.0f));
        g.pose().scale(2f / 3f, 2f / 3f, 1f);
        SdfRenderer.drawChevron(g, (int)-halfArrow, (int)-halfArrow, FOLD_ARROW_SIZE, FOLD_ARROW_SIZE,
                ThemeManager.getTextColor(), 0.5f);
        g.pose().popPose();
    }

    

    @Override
    protected void onButtonClick(int index) {
        if (index == 0) {
            
            if (debugPopup != null) debugPopup.toggle();
        } else {
            
            if (debugPopup != null) debugPopup.toggleDebugOverlay();
        }
    }

    

    
    public TopBarLayoutHelper.Rect getPopupAnchor(TopBarLayoutHelper.ButtonGroup group) {
        return group.rect(0);
    }
}

