package com.rtsbuilding.rtsbuilding.client.presentation.panel.topbar;

import com.mojang.blaze3d.systems.RenderSystem;
import com.rtsbuilding.rtsbuilding.client.infrastructure.module.building.BuildingModule;
import com.rtsbuilding.rtsbuilding.client.infrastructure.module.camera.CameraModule;
import com.rtsbuilding.rtsbuilding.client.kernel.RtsClientKernel;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.base.api.RtsPanelApi;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.topbar.group_button.CameraModeGroup;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.topbar.group_button.UtilityButtonGroup;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.topbar.popup.DebugMenuPopup;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.topbar.popup.LogoMenuPopup;
import com.rtsbuilding.rtsbuilding.client.presentation.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.util.animate.AnimFloat;
import com.rtsbuilding.rtsbuilding.client.util.animate.ColorAnimation;
import com.rtsbuilding.rtsbuilding.client.util.render.DarkUiPalette;
import com.rtsbuilding.rtsbuilding.client.util.render.SdfRenderer;
import com.rtsbuilding.rtsbuilding.client.util.render.SpriteRenderer;
import com.rtsbuilding.rtsbuilding.client.util.render.TextRenderer;
import com.rtsbuilding.rtsbuilding.client.util.render.model.SpriteRegion;
import com.rtsbuilding.rtsbuilding.client.util.render.model.TextureInfo;
import com.rtsbuilding.rtsbuilding.client.util.theme.ThemeManager;
import com.rtsbuilding.rtsbuilding.common.build.BuilderMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

public final class TopBarPanel implements RtsPanelApi {

    private BuildingModule buildingModule;
    private CameraModule cameraModule;
    
    private BuilderScreen screen;
    private boolean quickBuildOpen;
    private boolean guideOpen;

    
    private final TopBarLayoutHelper layout = new TopBarLayoutHelper();

    

    private CameraModeGroup cameraModeGroup;
    private UtilityButtonGroup utilityGroup;

    
    private FluidOcclusionIndicator fluidIndicator;

    private ModeSwitcher modeSwitcher;

    

    
    private final AnimFloat logoHoverState = AnimFloat.hover();

    
    private boolean logoPressed;

    
    private final AnimFloat fileHoverState = AnimFloat.hover();

    
    private boolean filePressed;

    
    private Runnable fileButtonAction;

    
    private LogoMenuPopup logoPopup;
    
    private Runnable pendingOnGearMenuToggle;

    
    private DebugMenuPopup debugPopup;

    

    
    private static final ResourceLocation LOGO_TEXTURE =
            ResourceLocation.tryParse("rtsbuilding:textures/gui/top/logo.png");
    
    private static final int LOGO_SIZE = TopBarLayoutHelper.LOGO_SIZE;
    
    private static final int LOGO_SHEET_WIDTH = 512;
    
    private static final int LOGO_SHEET_HEIGHT = 512;

    

    
    private static final int TOP_BAR_HEIGHT = TopBarLayoutHelper.TOP_BAR_HEIGHT;
    
    private static final int SCREEN_BORDER = TopBarLayoutHelper.SCREEN_BORDER;

    private static final int BOTTOM_SRC_H = TopBarLayoutHelper.BOTTOM_SRC_H;

    @Override
    public void init(BuilderScreen screen) {
        this.screen = screen;
        
        this.buildingModule = RtsClientKernel.get().module(BuildingModule.class);
        this.cameraModule = RtsClientKernel.get().module(CameraModule.class);
        
        this.cameraModeGroup = new CameraModeGroup(cameraModule);
        this.debugPopup = createDebugPopup();
        this.utilityGroup = new UtilityButtonGroup(debugPopup);
        this.fluidIndicator = new FluidOcclusionIndicator();
        
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

        // 打开 RTS 界面时把客户端当前模式同步给服务端：
        // 服务端 session.mode 取自持久化数据，可能与顶栏显示脱节（例如上次用的 BUILD），
        // 导致漏斗等仅限交互/蓝图模式的操作被 ServerActionHandler 静默丢弃。
        // setMode 会触发 onModeChange → BuildingModule.setMode → 下发 SET_MODE 同步服务端。
        if (this.buildingModule != null) {
            this.modeSwitcher.setMode(switch (this.buildingModule.getMode()) {
                case BUILD -> ModeSwitcher.Mode.BUILD;
                case BLUEPRINT -> ModeSwitcher.Mode.BLUEPRINT;
                default -> ModeSwitcher.Mode.INTERACTIVE;
            });
        }
        
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

        
        var fluidGroup = TopBarLayoutHelper.GroupLayout.fluidAfterMode(
                modeSwitcher.getX(), modeSwitcher.getY(), modeSwitcher.getWidth(), modeSwitcher.getHeight());
        fluidIndicator.render(g, mouseX, mouseY, fluidGroup);

        
        cameraModeGroup.tick();
        utilityGroup.tick();
        
        boolean hovering = layout.logoRect().contains(mouseX, mouseY);
        boolean shouldHighlight = hovering || logoPressed;
        this.logoHoverState.track(shouldHighlight);
        if (logoPressed) {
            logoPressed = false;
        }

        
        boolean fileHovering = layout.fileButtonRect().contains(mouseX, mouseY);
        boolean fileHighlight = fileHovering || filePressed;
        this.fileHoverState.track(fileHighlight);
        if (filePressed) {
            filePressed = false;
        }

        
        renderLogoCrossFade(g);

        
        renderFileButton(g);

        
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
        fluidIndicator.renderTooltipOverlay(g,
                TopBarLayoutHelper.GroupLayout.fluidAfterMode(
                        modeSwitcher.getX(), modeSwitcher.getY(), modeSwitcher.getWidth(), modeSwitcher.getHeight()),
                screen.width, screen.height);

        
        if (modeSwitcher != null) {
            modeSwitcher.renderPopup(g, mouseX, mouseY);
        }
    }

    
    private void renderLogoCrossFade(GuiGraphics g) {
        int bgColor = ColorAnimation.lerpRGB(DarkUiPalette.border(), DarkUiPalette.accent(), logoHoverState.get());
        SdfRenderer.drawRoundedRect(g, 0, 0, LOGO_SIZE, LOGO_SIZE, 4, bgColor);
        TextureInfo logoInfo = new TextureInfo(
                LOGO_TEXTURE, LOGO_SHEET_WIDTH, LOGO_SHEET_HEIGHT,
                TextureInfo.ThemeLayout.NONE,
                TextureInfo.FilterMode.NORMAL);
        SpriteRenderer.drawSprite(g, new SpriteRegion(logoInfo, 0, 0, LOGO_SHEET_WIDTH, LOGO_SHEET_HEIGHT),
                0, 0, LOGO_SIZE, LOGO_SIZE);
    }

    
    private void renderFileButton(GuiGraphics g) {
        var rect = layout.fileButtonRect();
        int bgColor = ColorAnimation.lerpRGB(DarkUiPalette.border(), DarkUiPalette.accent(), fileHoverState.get());
        SdfRenderer.drawRoundedRect(g, rect.x(), rect.y(), rect.width(), rect.height(), 4, bgColor);

        var font = Minecraft.getInstance().font;
        String label = "\u6587\u4EF6";
        int textColor = ColorAnimation.lerpRGB(ThemeManager.getTextColor(), DarkUiPalette.black(), fileHoverState.get());
        int textX = rect.x() + (rect.width() - font.width(label)) / 2;
        int textY = rect.y() + (rect.height() - font.lineHeight) / 2;
        TextRenderer.draw(g, label, textX, textY, textColor);
    }

    
    private void renderTopBarBackground(GuiGraphics g) {
        int screenW = screen.width;

        g.fill(0, 0, screenW, TOP_BAR_HEIGHT, DarkUiPalette.border());

        int bottomY = TOP_BAR_HEIGHT + SCREEN_BORDER;
        int bottomColor = (DarkUiPalette.accent() & 0x00FFFFFF) | 0x99000000;
        g.fill(0, bottomY, screenW, bottomY + BOTTOM_SRC_H, bottomColor);
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

        
        if (layout.fileButtonRect().contains(mx, my)) {
            filePressed = true;
            if (fileButtonAction != null) {
                fileButtonAction.run();
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

    
    public void setOnFileButtonClick(Runnable runnable) {
        this.fileButtonAction = runnable;
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

