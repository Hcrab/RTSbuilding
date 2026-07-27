package com.rtsbuilding.rtsbuilding.client.presentation.panel.topbar;

import com.mojang.blaze3d.systems.RenderSystem;
import com.rtsbuilding.rtsbuilding.client.kernel.RtsClientKernel;
import com.rtsbuilding.rtsbuilding.client.infrastructure.module.building.BuildingModule;
import com.rtsbuilding.rtsbuilding.client.infrastructure.module.camera.CameraModule;
import com.rtsbuilding.rtsbuilding.common.build.BuilderMode;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.base.api.RtsPanelApi;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.topbar.group_button.CameraModeGroup;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.topbar.group_button.UtilityButtonGroup;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.topbar.popup.DebugMenuPopup;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.topbar.popup.LogoMenuPopup;
import com.rtsbuilding.rtsbuilding.client.presentation.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.util.render.CrossFadeRenderer;
import com.rtsbuilding.rtsbuilding.client.util.render.SpriteRenderer;
import com.rtsbuilding.rtsbuilding.client.util.render.model.NineSliceRegion;
import com.rtsbuilding.rtsbuilding.client.util.render.model.SpriteRegion;
import com.rtsbuilding.rtsbuilding.client.util.render.model.TextureInfo;
import com.rtsbuilding.rtsbuilding.client.util.animate.AnimFloat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import java.util.List;

public final class TopBarPanel implements RtsPanelApi {

    private BuildingModule buildingModule;
    private CameraModule cameraModule;
    
    private BuilderScreen screen;
    private boolean quickBuildOpen;
    private boolean guideOpen;

    
    private final TopBarLayoutHelper layout = new TopBarLayoutHelper();

    

    private CameraModeGroup cameraModeGroup;
    private UtilityButtonGroup utilityGroup;

    

    
    private ModeSwitcher modeSwitcher;

    

    
    private final AnimFloat logoHoverState = AnimFloat.hover();

    
    private boolean logoPressed;

    
    private LogoMenuPopup logoPopup;
    
    private Runnable pendingOnGearMenuToggle;

    
    private DebugMenuPopup debugPopup;

    

    
    private static final ResourceLocation LOGO_TEXTURE =
            ResourceLocation.tryParse("rtsbuilding:textures/gui/top/logo.png");
    
    private static final int LOGO_SIZE = TopBarLayoutHelper.LOGO_SIZE;
    
    private static final int LOGO_SHEET_WIDTH = 1024;
    
    private static final int LOGO_SHEET_HEIGHT = 1024;

    

    
    private static final ResourceLocation TOP_UI_UP_TEXTURE =
            ResourceLocation.tryParse("rtsbuilding:textures/gui/top/top_ui_up.png");
    
    private static final ResourceLocation TOP_UI_DOWN_TEXTURE =
            ResourceLocation.tryParse("rtsbuilding:textures/gui/top/top_ui_down.png");
    
    private static final int TOP_UI_TEX_W = 32;
    
    private static final int TOP_UI_TEX_H = 16;
    
    private static final int TOP_UI_HALF_W = 16;
    
    private static final int TOP_UI_BORDER = 2;
    private static final TextureInfo TOP_UP_TEX_INFO = new TextureInfo(
            TOP_UI_UP_TEXTURE, TOP_UI_TEX_W, TOP_UI_TEX_H,
            TextureInfo.ThemeLayout.HORIZONTAL_PAIR,
            TextureInfo.FilterMode.PIXEL);
    private static final TextureInfo TOP_DOWN_TEX_INFO = new TextureInfo(
            TOP_UI_DOWN_TEXTURE, TOP_UI_TEX_W, TOP_UI_TEX_H,
            TextureInfo.ThemeLayout.HORIZONTAL_PAIR,
            TextureInfo.FilterMode.PIXEL);
    
    private static final int TOP_SRC_H = 8;
    
    private static final int BOTTOM_SRC_H = TopBarLayoutHelper.BOTTOM_SRC_H;
    private static final NineSliceRegion TOP_UP_NINE_SLICE = NineSliceRegion.fullTheme(
            TOP_UP_TEX_INFO, TOP_SRC_H, TOP_UI_BORDER);
    private static final NineSliceRegion TOP_DOWN_NINE_SLICE = NineSliceRegion.fullTheme(
            TOP_DOWN_TEX_INFO, BOTTOM_SRC_H, TOP_UI_BORDER);
    
    private static final int TOP_BAR_HEIGHT = TopBarLayoutHelper.TOP_BAR_HEIGHT;
    
    private static final int SCREEN_BORDER = TopBarLayoutHelper.SCREEN_BORDER;

    @Override
    public void init(BuilderScreen screen) {
        this.screen = screen;
        
        this.buildingModule = RtsClientKernel.get().module(BuildingModule.class);
        this.cameraModule = RtsClientKernel.get().module(CameraModule.class);
        
        this.cameraModeGroup = new CameraModeGroup(cameraModule);
        this.debugPopup = createDebugPopup();
        this.utilityGroup = new UtilityButtonGroup(debugPopup);
        
        this.modeSwitcher = new ModeSwitcher();
        
        this.modeSwitcher.setOnModeChange(mode -> {
            var bm = RtsClientKernel.get().module(BuildingModule.class);
            if (bm != null) {
                bm.setMode(switch (mode) {
                    case BUILD -> BuilderMode.BUILD;
                    case BLUEPRINT -> BuilderMode.BLUEPRINT;
                    default -> BuilderMode.INTERACT;
                });
            }
        });
        
        this.logoPopup = createLogoPopup();
        this.logoPopup.positionFromButton(LOGO_SIZE / 2, LOGO_SIZE, screen.width);
        
        if (this.pendingOnGearMenuToggle != null) {
            this.logoPopup.setOnGearMenuToggle(this.pendingOnGearMenuToggle);
            this.pendingOnGearMenuToggle = null;
        }
    }

    private void createButtons() {
    }

    public boolean isQuickBuildOpen() {
        return quickBuildOpen;
    }

    public void toggleQuickBuild() {
        this.quickBuildOpen = !this.quickBuildOpen;
    }

    public boolean isGuideOpen() {
        return guideOpen;
    }

    public void toggleTopGuide() {
        this.guideOpen = !this.guideOpen;
    }

    public void onRtsExited() {
        if (debugPopup != null) {
            debugPopup.onRtsExited();
        }
    }

    
    public void setOnGearMenuToggle(Runnable runnable) {
        this.pendingOnGearMenuToggle = runnable;
        if (this.logoPopup != null) {
            this.logoPopup.setOnGearMenuToggle(runnable);
        }
    }

    
    public void setGearMenuOpen(boolean open) {
        if (this.logoPopup != null) {
            this.logoPopup.setGearMenuOpen(open);
        }
    }

    
    public void toggleDebugOverlay() {
        if (this.debugPopup != null) {
            this.debugPopup.toggleDebugOverlay();
        }
    }

    
    public void cycleMode() {
        if (modeSwitcher != null) {
            modeSwitcher.cycleMode();
        }
    }

    
    public ModeSwitcher.Mode getCurrentMode() {
        return modeSwitcher != null ? modeSwitcher.getCurrentMode() : ModeSwitcher.Mode.INTERACTIVE;
    }

    
    public void setMode(ModeSwitcher.Mode mode) {
        if (modeSwitcher != null) {
            modeSwitcher.setMode(mode);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderTopBarBackground(g);

        
        if (modeSwitcher != null) {
            modeSwitcher.render(g, mouseX, mouseY);
        }

        
        var groupLayout = TopBarLayoutHelper.GroupLayout.create(screen.width, screen.getRightSidebarWidth());
        cameraModeGroup.render(g, mouseX, mouseY, groupLayout.modeGroup());
        utilityGroup.render(g, mouseX, mouseY, groupLayout.utilityGroup());

        
        cameraModeGroup.tick();
        utilityGroup.tick();
        
        boolean hovering = layout.logoRect().contains(mouseX, mouseY);
        boolean shouldHighlight = hovering || logoPressed;
        this.logoHoverState.track(shouldHighlight);
        if (logoPressed) {
            logoPressed = false;
        }

        
        renderLogoCrossFade(g);

        
        RenderSystem.defaultBlendFunc();

        
        if (debugPopup != null) {
            var anchor = utilityGroup.getPopupAnchor(groupLayout.utilityGroup());
            debugPopup.positionFromButton(
                    anchor.x() + anchor.width() / 2,
                    anchor.y() + anchor.height(),
                    screen.width);
            debugPopup.render(g, mouseX, mouseY);
        }

        
        if (logoPopup != null) {
            logoPopup.render(g, mouseX, mouseY);
        }

        RenderSystem.disableBlend();
    }

    
    @Override
    public void renderOverlays(GuiGraphics g, int mouseX, int mouseY) {
        var groupLayout = TopBarLayoutHelper.GroupLayout.create(screen.width, screen.getRightSidebarWidth());
        cameraModeGroup.renderTooltipOverlay(g, groupLayout.modeGroup(), screen.width, screen.height);
        utilityGroup.renderTooltipOverlay(g, groupLayout.utilityGroup(), screen.width, screen.height);

        
        if (modeSwitcher != null) {
            modeSwitcher.renderPopup(g, mouseX, mouseY);
        }
    }

    
    private void renderLogoCrossFade(GuiGraphics g) {
        TextureInfo logoInfo = new TextureInfo(
                LOGO_TEXTURE, LOGO_SHEET_WIDTH, LOGO_SHEET_HEIGHT,
                TextureInfo.ThemeLayout.HORIZONTAL_PAIR,
                TextureInfo.FilterMode.PIXEL);
        int halfW = LOGO_SHEET_WIDTH / 2;
        int halfH = LOGO_SHEET_HEIGHT / 2;
        SpriteRegion normal = new SpriteRegion(logoInfo, 0, 0, halfW, halfH);
        SpriteRegion highlighted = normal.withVOffset(halfH);
        Runnable normalRender = () -> SpriteRenderer.drawSprite(g, normal.withTheme(), 0, 0, LOGO_SIZE, LOGO_SIZE);
        Runnable highlightedRender = () -> SpriteRenderer.drawSprite(g, highlighted.withTheme(), 0, 0, LOGO_SIZE, LOGO_SIZE);
        CrossFadeRenderer.render(g, logoHoverState.get(), normalRender, highlightedRender);
    }

    
    private void renderTopBarBackground(GuiGraphics g) {
        int screenW = screen.width;

        
        SpriteRenderer.drawNineSlice(g, TOP_UP_NINE_SLICE.withTheme(),
                0, 0, screenW, TOP_BAR_HEIGHT);

        
        int bottomY = TOP_BAR_HEIGHT + SCREEN_BORDER;
        SpriteRenderer.drawNineSlice(g, TOP_DOWN_NINE_SLICE.withTheme(),
                0, bottomY, screenW, BOTTOM_SRC_H);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        int mx = (int) mouseX;
        int my = (int) mouseY;

        

        
        if (logoPopup != null && logoPopup.isOpen()) {
            if (logoPopup.contains(mx, my)) {
                return logoPopup.handleClick(mx, my);
            }
            logoPopup.close();
            return true;
        }

        
        if (debugPopup != null && debugPopup.isOpen()) {
            if (debugPopup.contains(mx, my)) {
                return debugPopup.handleClick(mx, my);
            }
            debugPopup.close();
            return true;
        }

        
        if (modeSwitcher != null && modeSwitcher.mouseClicked(mx, my)) return true;

        
        if (layout.logoRect().contains(mx, my)) {
            logoPressed = true;
            if (logoPopup != null) {
                logoPopup.toggle();
            }
            return true;
        }

        
        var groupLayout = TopBarLayoutHelper.GroupLayout.create(screen.width, screen.getRightSidebarWidth());
        if (cameraModeGroup.mouseClicked(mx, my, groupLayout.modeGroup())) return true;
        if (utilityGroup.mouseClicked(mx, my, groupLayout.utilityGroup())) return true;

        return false;
    }

    
    private LogoMenuPopup createLogoPopup() {
        return new LogoMenuPopup();
    }

    
    private DebugMenuPopup createDebugPopup() {
        return new DebugMenuPopup();
    }

    public boolean isMouseOverAnyPopup(int mouseX, int mouseY) {
        if (debugPopup != null && debugPopup.isOpen() && debugPopup.contains(mouseX, mouseY)) {
            return true;
        }
        if (modeSwitcher != null && modeSwitcher.isMouseOverPopup(mouseX, mouseY)) {
            return true;
        }
        return logoPopup != null && logoPopup.isOpen() && logoPopup.contains(mouseX, mouseY);
    }
}

