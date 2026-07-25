package com.rtsbuilding.rtsbuilding.client.presentation.panel.container;

import com.mojang.blaze3d.systems.RenderSystem;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.base.window.RtsPanel;
import com.rtsbuilding.rtsbuilding.client.presentation.standalone.BuilderScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;

import static com.rtsbuilding.rtsbuilding.client.presentation.standalone.BuilderScreenConstants.TOP_H;


public final class ContainerScreenPanel extends RtsPanel {

    
    private final ContainerInputForwarder inputForwarder;

    
    private final Component title;

    
    @Nullable
    private int[] computedPanelSize;

    
    private static volatile boolean renderingOverlay;

    
    private static final int PANEL_PAD_H = 10;
    
    private static final int PANEL_PAD_V = 4;
    
    private static final int WIDGET_SCAN_MARGIN = 50;

    public ContainerScreenPanel(AbstractContainerScreen<?> containerScreen) {
        this.inputForwarder = new ContainerInputForwarder(containerScreen);
        this.title = containerScreen.getTitle();
        this.draggable = true;
        this.resizable = true;
        this.closable = true;
    }

    
    public static boolean isRenderingOverlay() {
        return renderingOverlay;
    }

    
    @Nullable
    public AbstractContainerScreen<?> getContainerScreen() {
        return inputForwarder.getScreen();
    }

    
    
    

    @Override
    public void init(BuilderScreen screen) {
        super.init(screen);
        if (!inputForwarder.hasScreen()) return;

        var cs = inputForwarder.getScreen();

        
        int panelW = Math.max(getMinWindowWidth(), getDefaultWidth());
        int panelH = Math.max(getMinWindowHeight(), getDefaultHeight());
        int cw = Math.max(1, panelW - 2);
        int ch = Math.max(1, panelH - getTitleBarHeight() - 8);
        inputForwarder.init(cw, ch);

        
        int[] contentBounds = null;
        if (cs != null) {
            contentBounds = scanContentBounds(cs);
        }
        int cw2 = 0;
        if (contentBounds != null) {
            cw2 = contentBounds[0] + PANEL_PAD_H;
        }
        int ch2 = 0;
        if (contentBounds != null) {
            ch2 = contentBounds[1] + PANEL_PAD_V;
        }

        
        int actualW = Math.max(getMinWindowWidth(), cw2 + 2);
        int actualH = Math.max(getMinWindowHeight(), ch2 + getTitleBarHeight() + 8);

        this.computedPanelSize = new int[]{actualW, actualH};
        this.bounds.setDefaults(actualW, actualH);

        int actualCw = Math.max(1, actualW - 2);
        int actualCh = Math.max(1, actualH - getTitleBarHeight() - 8);
        inputForwarder.init(actualCw, actualCh);
    }

    @Override
    public void tick() {
        super.tick();
        if (!inputForwarder.hasScreen() || !isOpen()) return;

        inputForwarder.tick();

        
        autoGrowIfNeeded();

        
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.containerMenu.containerId == 0) {
            setOpen(false);
        }
    }

    
    private void autoGrowIfNeeded() {
        if (isResizing()) return; 
        var cs = inputForwarder.getScreen();
        if (cs == null) return;

        int[] contentBounds = scanContentBounds(cs);
        int neededContentW = contentBounds[0] + PANEL_PAD_H;
        int neededContentH = contentBounds[1] + PANEL_PAD_V;

        int neededPanelW = Math.max(getMinWindowWidth(), neededContentW + 2);
        int neededPanelH = Math.max(getMinWindowHeight(), neededContentH + getTitleBarHeight() + 8);

        
        if (neededPanelW <= getWindowWidth() && neededPanelH <= getWindowHeight()) return;

        int newW = Math.min(Math.max(getWindowWidth(), neededPanelW), getMaxWindowWidth());
        int newH = Math.min(Math.max(getWindowHeight(), neededPanelH), getMaxWindowHeight());

        if (newW > getWindowWidth() || newH > getWindowHeight()) {
            
            setWindowWidth(newW);
            setWindowHeight(newH);
            
            int cw = Math.max(1, newW - 2);
            int ch = Math.max(1, newH - getTitleBarHeight() - 8);
            inputForwarder.init(cw, ch);
            
            this.computedPanelSize = new int[]{newW, newH};
            onBoundsChanged();
        }
    }

    @Override
    protected void onClose() {
        super.onClose();
        
        closeContainerOnServer();
        
        inputForwarder.clear();
    }

    
    private void closeContainerOnServer() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.containerMenu.containerId == 0) return;

        
        
        
        
        int containerId = mc.player.containerMenu.containerId;
        if (mc.player instanceof LocalPlayer localPlayer) {
            localPlayer.connection.send(new ServerboundContainerClosePacket(containerId));
        }
        mc.player.containerMenu = mc.player.inventoryMenu;
    }

    @Override
    protected void onBoundsChanged() {
        super.onBoundsChanged();
        
        int cw = Math.max(1, getWindowWidth() - 2);
        int ch = Math.max(1, getWindowHeight() - getTitleBarHeight() - 8);
        inputForwarder.init(cw, ch);
        
        this.computedPanelSize = new int[]{getWindowWidth(), getWindowHeight()};
    }

    
    
    

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        var cs = inputForwarder.getScreen();
        if (cs == null) return;

        int cx = contentX();
        int cy = contentY();

        g.pose().pushPose();
        try {
            g.pose().translate(cx, cy, 0);
            renderingOverlay = true;
            try {
                
                cs.render(g, mouseX - cx, mouseY - cy, partialTick);
            } finally {
                renderingOverlay = false;
            }
        } finally {
            g.pose().popPose();
        }

        
        RenderSystem.clear(256, false); 
    }

    
    
    
    
    
    

    @Override
    protected void handleContentClick(double mouseX, double mouseY, int button) {
        inputForwarder.mouseClicked(mouseX - contentX(), mouseY - contentY(), button);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!this.open) return false;
        
        
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT && isInsideWindow(mouseX, mouseY)) {
            inputForwarder.mouseClicked(mouseX - contentX(), mouseY - contentY(), button);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (!this.open) return false;
        
        if (super.mouseDragged(mouseX, mouseY, button, dragX, dragY)) return true;

        
        inputForwarder.mouseDragged(mouseX - contentX(), mouseY - contentY(), button, dragX, dragY);

        
        if (isInsideWindow(mouseX, mouseY)) return true;
        return button == GLFW.GLFW_MOUSE_BUTTON_LEFT;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (!this.open) return false;
        
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            inputForwarder.mouseReleased(mouseX - contentX(), mouseY - contentY(), button);
            return isInsideWindow(mouseX, mouseY);
        }
        boolean handled = super.mouseReleased(mouseX, mouseY, button);
        inputForwarder.mouseReleased(mouseX - contentX(), mouseY - contentY(), button);
        return handled;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!this.open) return false;
        if (!inputForwarder.hasScreen() || !isInsideWindow(mouseX, mouseY)) return false;
        
        
        inputForwarder.mouseScrolled(mouseX - contentX(), mouseY - contentY(), scrollX, scrollY);
        return true;
    }

    @Override
    public boolean mouseMoved(double mouseX, double mouseY) {
        if (!this.open) return false;
        if (!inputForwarder.hasScreen() || !isInsideWindow(mouseX, mouseY)) return false;
        inputForwarder.mouseMoved(mouseX - contentX(), mouseY - contentY());
        return false;
    }

    @Override
    protected boolean handleWindowKeyPressed(int keyCode, int scanCode, int modifiers) {
        
        
        
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) return false;
        return inputForwarder.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    protected boolean handleWindowCharTyped(char codePoint, int modifiers) {
        return inputForwarder.charTyped(codePoint, modifiers);
    }

    
    
    

    @Override
    protected Component getTitle() {
        return title;
    }

    @Override
    protected int getDefaultWidth() {
        if (computedPanelSize != null) return computedPanelSize[0];
        var cs = inputForwarder.getScreen();
        if (cs != null) {
            return Math.max(88, cs.getXSize() + 8);
        }
        return 184;
    }

    @Override
    protected int getDefaultHeight() {
        if (computedPanelSize != null) return computedPanelSize[1];
        var cs = inputForwarder.getScreen();
        if (cs != null) {
            return getTitleBarHeight() + cs.getYSize() + PANEL_PAD_V + 8;
        }
        return 200;
    }

    @Override
    public int getMinWindowWidth() {
        return 88;
    }

    @Override
    public int getMinWindowHeight() {
        return getTitleBarHeight() + 50;
    }

    @Override
    protected int getMaxWindowHeight() {
        
        
        return Integer.MAX_VALUE;
    }

    @Override
    public void clampWindowToScreen() {
        if (this.screen == null) return;
        int maxX = Math.max(0, this.screen.width - bounds.getWidth());
        bounds.setX(Mth.clamp(bounds.getX(), 0, maxX));

        if (bounds.getHeight() > this.screen.height) {
            
            int minY = this.screen.height - bounds.getHeight(); 
            int maxY = 0;                                        
            bounds.setY(Mth.clamp(bounds.getY(), minY, maxY));
        } else {
            int maxY = Math.max(0, this.screen.height - getTitleBarHeight());
            bounds.setY(Mth.clamp(bounds.getY(), 0, maxY));
        }
    }

    
    private int[] computePanelSizeFromContent(AbstractContainerScreen<?> cs) {
        int[] contentBounds = scanContentBounds(cs);
        int cw = contentBounds[0] + PANEL_PAD_H;
        int ch = contentBounds[1] + PANEL_PAD_V;
        return new int[]{cw + 2, ch + getTitleBarHeight() + 8};
    }

    
    private int[] scanContentBounds(AbstractContainerScreen<?> cs) {
        int bgLeft = cs.getGuiLeft();
        int bgTop = cs.getGuiTop();
        int bgRight = bgLeft + cs.getXSize();
        int bgBottom = bgTop + cs.getYSize();

        int minX = bgLeft;
        int minY = bgTop;
        int maxX = bgRight;
        int maxY = bgBottom;

        int margin = WIDGET_SCAN_MARGIN;
        for (Renderable r : cs.renderables) {
            if (r instanceof AbstractWidget w) {
                int wx = w.getX();
                int wy = w.getY();
                int ww = w.getWidth();
                int wh = w.getHeight();

                
                boolean nearX = wx + ww > bgLeft - margin && wx < bgRight + margin;
                boolean nearY = wy + wh > bgTop - margin && wy < bgBottom + margin;

                if (nearX && nearY) {
                    if (wx < minX) minX = wx;
                    if (wy < minY) minY = wy;
                    if (wx + ww > maxX) maxX = wx + ww;
                    if (wy + wh > maxY) maxY = wy + wh;
                }
            }
        }

        return new int[]{maxX - minX, maxY - minY};
    }

    @Override
    protected boolean shouldClipContent() {
        
        
        return false;
    }

    @Override
    protected void computeDefaultPosition() {
        if (screen == null) return;
        setWindowX(Math.max(8, (screen.width - getWindowWidth()) / 2));
        if (getWindowHeight() > screen.height) {
            
            setWindowY(TOP_H + 6);
        } else {
            setWindowY(Mth.clamp((screen.height - getWindowHeight()) / 2,
                    TOP_H + 6,
                    Math.max(TOP_H + 6, screen.height - getWindowHeight() - 8)));
        }
    }
}
