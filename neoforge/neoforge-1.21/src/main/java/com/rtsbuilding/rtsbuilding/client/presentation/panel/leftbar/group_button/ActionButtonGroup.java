package com.rtsbuilding.rtsbuilding.client.presentation.panel.leftbar.group_button;

import com.mojang.blaze3d.systems.RenderSystem;
import com.rtsbuilding.rtsbuilding.client.input.RtsKeyMappings;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.base.button.AbstractButtonGroup;
import com.rtsbuilding.rtsbuilding.client.util.animate.ColorAnimation;
import com.rtsbuilding.rtsbuilding.client.util.render.SpriteRenderer;
import com.rtsbuilding.rtsbuilding.client.util.render.TextRenderer;
import com.rtsbuilding.rtsbuilding.client.util.render.model.SpriteRegion;
import com.rtsbuilding.rtsbuilding.client.util.render.model.TextureInfo;
import com.rtsbuilding.rtsbuilding.client.util.state.TooltipController;
import com.rtsbuilding.rtsbuilding.client.util.theme.ThemeManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class ActionButtonGroup extends AbstractButtonGroup {

    
    private static final ResourceLocation BIND_BTN = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/left/button/bind.png");
    
    private static final ResourceLocation DIRECTION_ROTATE_BTN = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/left/button/direction_rotation.png");
    
    private static final ResourceLocation ITEM_PICKUP_BTN = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/left/button/item_pickup.png");

    

    
    private static final ResourceLocation DOWN_BG = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/base/button/down_button.png");
    
    private static final ResourceLocation MIDDLE_BG = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/base/button/middle_button.png");
    
    private static final ResourceLocation UP_BG = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/base/button/up_button.png");

    
    private static final ResourceLocation ONLY_BG = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/base/button/only_button.png");

    
    private boolean showBindButton = true;

    
    private boolean showRotateButton = true;

    
    private boolean blueprintMode = false;

    

    
    private final TextureInfo onlyTexInfo;
    
    private final SpriteRegion[] onlyRegions;

    
    private final TooltipController bindBtnTooltip = TooltipController.builder().direction(TooltipController.Direction.RIGHT).build();
    private final TooltipController dirRotateBtnTooltip = TooltipController.builder().direction(TooltipController.Direction.RIGHT).build();
    private final TooltipController itemPickupBtnTooltip = TooltipController.builder().direction(TooltipController.Direction.RIGHT).build();

    public ActionButtonGroup() {
        super(Direction.VERTICAL, DEFAULT_BTN_SIZE, DEFAULT_INNER_GAP, true,
                DOWN_BG, MIDDLE_BG, UP_BG,
                BIND_BTN, DIRECTION_ROTATE_BTN, ITEM_PICKUP_BTN);
        
        this.onlyTexInfo = new TextureInfo(
                ONLY_BG, AbstractButtonGroup.TEX_W, AbstractButtonGroup.TEX_H,
                TextureInfo.ThemeLayout.HORIZONTAL_PAIR,
                TextureInfo.FilterMode.PIXEL);
        this.onlyRegions = new SpriteRegion[3];
        this.onlyRegions[0] = new SpriteRegion(onlyTexInfo, 0, 0,                           AbstractButtonGroup.HALF_W, AbstractButtonGroup.STATE_H);
        this.onlyRegions[1] = new SpriteRegion(onlyTexInfo, 0, AbstractButtonGroup.STATE_H, AbstractButtonGroup.HALF_W, AbstractButtonGroup.STATE_H);
        this.onlyRegions[2] = new SpriteRegion(onlyTexInfo, 0, AbstractButtonGroup.STATE_H * 2, AbstractButtonGroup.HALF_W, AbstractButtonGroup.STATE_H);
    }

    
    public void setShowBindButton(boolean show) {
        this.showBindButton = show;
        if (!show) {
            selected[0] = false;
        }
    }

    
    public void setShowRotateButton(boolean show) {
        this.showRotateButton = show;
        if (!show) {
            selected[1] = false;
        }
    }

    
    public void setBlueprintMode(boolean blueprint) {
        this.blueprintMode = blueprint;
        if (blueprint) {
            selected[0] = false;
            selected[1] = false;
        }
    }

    

    
    private int visibleCount() {
        if (blueprintMode) return 1;
        return (showBindButton ? 1 : 0) + (showRotateButton ? 1 : 0) + 1;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, int originX, int originY) {
        int n = patternTextures.length;
        int[] orig = { bgTypeForButton[0], bgTypeForButton[1], bgTypeForButton[2] };
        try {
            int vis = 0;
            int total = visibleCount();
            for (int i = 0; i < n; i++) {
                if (!isVisible(i)) continue;
                int by = originY + vis * (buttonSize + innerGap);
                if (hasBg) {
                    if (total == 1) {
                        renderOnlyBg(g, mouseX, mouseY, i, originX, by);
                    } else {
                        bgTypeForButton[i] = bgTypeForVisualIndex(vis);
                        renderSingleBg(g, mouseX, mouseY, i, originX, by);
                    }
                }
                renderSinglePattern(g, mouseX, mouseY, i, originX, by);
                vis++;
            }
        } finally {
            bgTypeForButton[0] = orig[0];
            bgTypeForButton[1] = orig[1];
            bgTypeForButton[2] = orig[2];
        }
    }

    
    private boolean isVisible(int i) {
        
        if (blueprintMode) return i == 2;
        return switch (i) {
            case 0 -> showBindButton;
            case 1 -> showRotateButton;
            case 2 -> true;
            default -> false;
        };
    }

    
    private int bgTypeForVisualIndex(int visIdx) {
        int total = visibleCount();
        if (visIdx == 0) return 2; 
        if (visIdx == total - 1) return 0; 
        return 1; 
    }

    
    private void renderOnlyBg(GuiGraphics g, int mouseX, int mouseY, int index, int bx, int by) {
        boolean hovering = mouseX >= bx && mouseX < bx + buttonSize
                && mouseY >= by && mouseY < by + buttonSize;
        float hoverT = this.hoverStates[index].track(hovering);

        SpriteRenderer.drawStateSprite(g,
                onlyRegions[0],     
                onlyRegions[1],     
                onlyRegions[2],     
                selected[index],
                hoverT,
                bx, by, buttonSize, buttonSize);
    }

    

    @Override
    public int mouseClicked(double mx, double my, int originX, int originY) {
        int vis = 0;
        for (int i = 0; i < patternTextures.length; i++) {
            if (!isVisible(i)) continue;
            int by = originY + vis * (buttonSize + innerGap);
            if (mx >= originX && mx < originX + buttonSize
                    && my >= by && my < by + buttonSize) {
                onButtonClick(i);
                return i;
            }
            vis++;
        }
        return -1;
    }

    
    public void toggleBindButton() {
        onButtonClick(0);
    }

    
    public void toggleDirectionRotateButton() {
        onButtonClick(1);
    }

    
    public void toggleItemPickupButton() {
        onButtonClick(2);
    }

    @Override
    protected void onButtonClick(int index) {
        
        if (selected[index]) {
            selected[index] = false;
        } else {
            java.util.Arrays.fill(selected, false);
            selected[index] = true;
        }
    }

    
    public void tickTooltips(int mouseX, int mouseY, int originX, int originY) {
        int vis = 0;
        for (int i = 0; i < patternTextures.length; i++) {
            if (!isVisible(i)) {
                
                getTooltip(i).update(false, false);
                continue;
            }
            int by = originY + vis * (buttonSize + innerGap);
            boolean hover = mouseX >= originX && mouseX < originX + buttonSize
                    && mouseY >= by && mouseY < by + buttonSize;
            getTooltip(i).update(hover, false);
            vis++;
        }
    }

    
    private TooltipController getTooltip(int i) {
        return switch (i) {
            case 0 -> bindBtnTooltip;
            case 1 -> dirRotateBtnTooltip;
            case 2 -> itemPickupBtnTooltip;
            default -> throw new IndexOutOfBoundsException("Unexpected index: " + i);
        };
    }

    
    public void renderTooltipOverlay(GuiGraphics g, int originX, int originY,
                                     int screenW, int screenH) {
        int textColor = ThemeManager.getTextColor();
        int shortcutColor = ColorAnimation.scale(textColor, 0.6f);

        int vis = 0;
        for (int i = 0; i < patternTextures.length; i++) {
            if (!isVisible(i)) continue;
            int by = originY + vis * (buttonSize + innerGap);
            TooltipController tooltip = getTooltip(i);
            if (tooltip.shouldRender()) {
                renderSingleTooltip(g, tooltip, i, originX, by, textColor, shortcutColor, screenW, screenH);
            }
            vis++;
        }
    }

    
    private void renderSingleTooltip(GuiGraphics g, TooltipController tooltip, int index,
                                      int btnX, int btnY, int textColor, int shortcutColor,
                                      int screenW, int screenH) {
        String text;
        if (index == 0) {
            String keyText = RtsKeyMappings.TOGGLE_BIND_MODE_KEY.getTranslatedKeyMessage().getString();
            text = Component.translatable("tooltip.rtsbuilding.left.bind_button").getString() + "\n"
                    + Component.translatable("tooltip.rtsbuilding.left.bind_button.desc").getString() + "\n"
                    + Component.translatable("tooltip.rtsbuilding.shortcut", keyText).getString();
        } else if (index == 1) {
            String keyText = RtsKeyMappings.TOGGLE_DIRECTION_ROTATE_MODE_KEY.getTranslatedKeyMessage().getString();
            text = Component.translatable("tooltip.rtsbuilding.left.direction_rotate").getString() + "\n"
                    + Component.translatable("tooltip.rtsbuilding.left.direction_rotate.desc").getString() + "\n"
                    + Component.translatable("tooltip.rtsbuilding.shortcut", keyText).getString();
        } else {
            String keyText = RtsKeyMappings.TOGGLE_ITEM_PICKUP_MODE_KEY.getTranslatedKeyMessage().getString();
            text = Component.translatable("tooltip.rtsbuilding.left.item_pickup").getString() + "\n"
                    + Component.translatable("tooltip.rtsbuilding.left.item_pickup.desc").getString() + "\n"
                    + Component.translatable("tooltip.rtsbuilding.shortcut", keyText).getString();
        }
        renderTooltipRight(g, tooltip, btnX, btnY, buttonSize, buttonSize,
                text, textColor, shortcutColor, screenW, screenH);
    }

    
    private static void renderTooltipRight(GuiGraphics g, TooltipController tooltip,
                                            int btnX, int btnY, int btnW, int btnH,
                                            String text, int color, int shortcutColor,
                                            int screenW, int screenH) {
        float alpha = tooltip.getAlpha();
        var font = Minecraft.getInstance().font;

        String[] lines = text.split("\n");
        int lineHeight = font.lineHeight;
        int lineGap = 1;
        float scaledLineH = lineHeight * 0.75f;
        float scaledLineGap = lineGap * 0.75f;
        int maxLineW = 0;
        for (String line : lines) {
            maxLineW = Math.max(maxLineW, font.width(line));
        }
        int padH = 6, padV = 3;
        int tipW = (int)(maxLineW * 0.75f) + padH * 2;
        int tipH = (int)(scaledLineH * lines.length + scaledLineGap * (lines.length - 1)) + padV * 2;

        
        int tipX = btnX + btnW + 2;
        int tipY = btnY;
        tipX = Math.max(0, Math.min(tipX, screenW - tipW));
        tipY = Math.max(0, Math.min(tipY, screenH - tipH));

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, alpha);
        SpriteRenderer.drawNineSliceFloatingPanel(g, tipX, tipY, tipW, tipH, false);

        float textY = tipY + padV;
        for (int i = 0; i < lines.length; i++) {
            int lineColor = (i == lines.length - 1) ? shortcutColor : color;
            g.pose().pushPose();
            g.pose().translate(tipX + padH, textY, 0);
            g.pose().scale(0.75f, 0.75f, 1.0f);
            TextRenderer.draw(g, lines[i], 0, 0, lineColor);
            g.pose().popPose();
            textY += scaledLineH + scaledLineGap;
        }

        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.disableBlend();
    }
}
