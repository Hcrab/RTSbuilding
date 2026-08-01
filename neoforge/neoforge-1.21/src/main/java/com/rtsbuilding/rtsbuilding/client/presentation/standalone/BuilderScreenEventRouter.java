package com.rtsbuilding.rtsbuilding.client.presentation.standalone;

import com.rtsbuilding.rtsbuilding.client.infrastructure.module.camera.CameraModule;
import com.rtsbuilding.rtsbuilding.client.input.RtsKeyMappings;
import com.rtsbuilding.rtsbuilding.client.kernel.RtsClientKernel;
import com.rtsbuilding.rtsbuilding.client.presentation.event.dispatcher.EventDispatcher;
import com.rtsbuilding.rtsbuilding.client.presentation.event.model.EventResult;
import com.rtsbuilding.rtsbuilding.client.presentation.event.model.KeyPressEvent;
import com.rtsbuilding.rtsbuilding.client.presentation.layout.PanelRegistry;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.base.window.RtsFloatingWindowLayer;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.gear.GearMenuPanel;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.handler.BindModeMouseHandler;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.handler.BuildInteractionHandler;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.handler.BuilderScreenMovementHandler;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.handler.EntityInteractionHandler;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.leftbar.LeftSidebarPanel;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.topbar.TopBarPanel;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import static com.rtsbuilding.rtsbuilding.client.presentation.event.model.EventResult.CONSUMED;
import static com.rtsbuilding.rtsbuilding.client.presentation.event.model.EventResult.PASS;

public final class BuilderScreenEventRouter {

    private final SuperScreen superScreen;

    public interface SuperScreen {
        boolean mouseClicked(double x, double y, int button);
        boolean mouseReleased(double x, double y, int button);
        boolean mouseDragged(double x, double y, int button, double dx, double dy);
        boolean mouseScrolled(double x, double y, double scrollX, double scrollY);
        boolean keyPressed(int keyCode, int scanCode, int modifiers);
        boolean charTyped(char codePoint, int modifiers);
        void mouseMoved(double x, double y);
    }

    public BuilderScreenEventRouter(SuperScreen superScreen) {
        this.superScreen = superScreen;
    }

    public void registerAll(EventDispatcher dispatcher, PanelRegistry panelRegistry,
                            BuilderScreen screen, RtsClientKernel kernel,
                            RtsFloatingWindowLayer floatingWindowLayer,
                            TopBarPanel topBarPanel, LeftSidebarPanel leftSidebarPanel,
                            GearMenuPanel gearMenuPanel,
                            BuilderScreenMovementHandler movementHandler,
                            BindModeMouseHandler bindModeHandler,
                            EntityInteractionHandler entityInteractionHandler,
                            BuildInteractionHandler buildInteractionHandler) {
        registerMouseClickHandlers(dispatcher, screen, kernel, floatingWindowLayer,
                panelRegistry, leftSidebarPanel, movementHandler, bindModeHandler,
                entityInteractionHandler, buildInteractionHandler, topBarPanel);
        registerMouseReleaseHandlers(dispatcher, panelRegistry, floatingWindowLayer, kernel,
                buildInteractionHandler, screen, topBarPanel, leftSidebarPanel);
        registerMouseDragHandlers(dispatcher, panelRegistry, floatingWindowLayer, kernel);
        registerMouseScrollHandlers(dispatcher, panelRegistry, floatingWindowLayer, kernel,
                leftSidebarPanel, screen);
        registerKeyPressHandlers(dispatcher, floatingWindowLayer, panelRegistry,
                kernel, topBarPanel, leftSidebarPanel, gearMenuPanel,
                movementHandler, bindModeHandler, entityInteractionHandler, screen);
        registerCharHandlers(dispatcher, panelRegistry, floatingWindowLayer, kernel);
        registerMouseMoveHandlers(dispatcher, floatingWindowLayer);
    }

    private void registerMouseClickHandlers(EventDispatcher d, BuilderScreen screen,
            RtsClientKernel kernel, RtsFloatingWindowLayer fw, PanelRegistry pr,
            LeftSidebarPanel lb, BuilderScreenMovementHandler mh,
            BindModeMouseHandler bmh, EntityInteractionHandler eih,
            BuildInteractionHandler bih, TopBarPanel topBar) {
        d.onMouseClick(event -> {
            screen.unfocusGridSearch();
            return PASS;
        }, EventDispatcher.P_FLOATING_WINDOW);

        d.onMouseClick(event -> {
            if (fw.mouseClicked(event.x(), event.y(), event.button())) return CONSUMED;
            if (eih.isInteractionPanelOpen()) { eih.closeInteractionPanel(); return CONSUMED; }
            return PASS;
        }, EventDispatcher.P_FLOATING_WINDOW);

        pr.registerContentPanelMouseClick(d);

        d.onMouseClick(event ->
                bmh.handleMouseClick(event, screen, lb),
                EventDispatcher.P_BIND_LOGIC);

        d.onMouseClick(event -> {
            if (event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT
                    && !isAltDown() && !isShiftDown()
                    && !lb.isClickButtonSelected()) {
                kernel.renderPipeline().boxSelector.handleRightClickWithHover();
                return CONSUMED;
            }
            return PASS;
        }, EventDispatcher.P_SELECTION);

        d.onMouseClick(event ->
                eih.handleMouseClick(event, screen, lb),
                EventDispatcher.P_ENTITY_INTERACT);

        d.onMouseClick(event ->
                bih.handleMouseClick(event, screen, lb, topBar),
                EventDispatcher.P_ENTITY_INTERACT);

        d.onMouseClick(event -> {
            if (event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT && isAltDown()) {
                if (mh.handleMovePlayerActionAt(screen)) return CONSUMED;
            }
            return PASS;
        }, EventDispatcher.P_MOVEMENT);

        d.onMouseClick(event -> {
            if (screen.isMouseOverRtsPanelApi(event.x(), event.y())) return CONSUMED;
            if (kernel.inputPipeline().onMouseClicked(event.x(), event.y(), event.button())) return CONSUMED;
            return PASS;
        }, EventDispatcher.P_INPUT_PIPELINE);

        d.onMouseClick(event -> {
            if (superScreen.mouseClicked(event.x(), event.y(), event.button())) return CONSUMED;
            return PASS;
        }, EventDispatcher.P_FALLBACK);
    }

    private void registerMouseReleaseHandlers(EventDispatcher d, PanelRegistry pr,
            RtsFloatingWindowLayer fw, RtsClientKernel kernel,
            BuildInteractionHandler bih, BuilderScreen screen, TopBarPanel topBar,
            LeftSidebarPanel leftSidebarPanel) {
        pr.registerContentPanelMouseRelease(d);

        d.onMouseRelease(event ->
                bih.handleMouseRelease(event, screen, topBar, leftSidebarPanel),
                EventDispatcher.P_BUILD_ACTION);

        d.onMouseRelease(event -> {
            if (fw.mouseReleased(event.x(), event.y(), event.button())) return CONSUMED;
            if (kernel.inputPipeline().onMouseReleased(event.x(), event.y(), event.button())) return CONSUMED;
            if (superScreen.mouseReleased(event.x(), event.y(), event.button())) return CONSUMED;
            return PASS;
        }, EventDispatcher.P_FALLBACK);
    }

    private void registerMouseDragHandlers(EventDispatcher d, PanelRegistry pr,
            RtsFloatingWindowLayer fw, RtsClientKernel kernel) {
        pr.registerContentPanelMouseDrag(d);

        d.onMouseDrag(event -> {
            double clampedDx = Math.abs(event.dx()) > 200 ? 0 : event.dx();
            double clampedDy = Math.abs(event.dy()) > 200 ? 0 : event.dy();
            if (fw.mouseDragged(event.x(), event.y(), event.button(), event.dx(), event.dy())) return CONSUMED;
            if (kernel.inputPipeline().onMouseDragged(event.x(), event.y(), event.button(), clampedDx, clampedDy)) return CONSUMED;
            if (superScreen.mouseDragged(event.x(), event.y(), event.button(), event.dx(), event.dy())) return CONSUMED;
            return PASS;
        }, EventDispatcher.P_FALLBACK);
    }

    private void registerMouseScrollHandlers(EventDispatcher d, PanelRegistry pr,
            RtsFloatingWindowLayer fw, RtsClientKernel kernel,
            LeftSidebarPanel lb, BuilderScreen screen) {
        pr.registerContentPanelMouseScroll(d);

        d.onMouseScroll(event -> {
            if (fw.mouseScrolled(event.x(), event.y(), event.scrollX(), event.scrollY())) return CONSUMED;
            if (!lb.isClickButtonSelected()
                    && kernel.renderPipeline().boxSelector.handleScroll(event.scrollY())) return CONSUMED;
            if (screen.isMouseOverRtsPanelApi(event.x(), event.y())) return CONSUMED;
            if (kernel.inputPipeline().onMouseScrolled(event.x(), event.y(), event.scrollX(), event.scrollY())) return CONSUMED;
            if (superScreen.mouseScrolled(event.x(), event.y(), event.scrollX(), event.scrollY())) return CONSUMED;
            return PASS;
        }, EventDispatcher.P_FALLBACK);
    }

    private void registerKeyPressHandlers(EventDispatcher d,
            RtsFloatingWindowLayer fw, PanelRegistry pr,
            RtsClientKernel kernel, TopBarPanel topBar, LeftSidebarPanel lb,
            GearMenuPanel gearMenu, BuilderScreenMovementHandler mh,
            BindModeMouseHandler bmh, EntityInteractionHandler eih,
            BuilderScreen screen) {
        d.onKeyPress(event -> {
            if (fw.keyPressed(event.keyCode(), event.scanCode(), event.modifiers())) return CONSUMED;
            if (event.keyCode() == GLFW.GLFW_KEY_ESCAPE && eih.isInteractionPanelOpen()) {
                eih.closeInteractionPanel();
                return CONSUMED;
            }
            return PASS;
        }, EventDispatcher.P_FLOATING_WINDOW);

        pr.registerContentPanelKeyPress(d);

        d.onKeyPress(event -> handleShortcut(event, kernel, topBar, lb, gearMenu, screen),
                EventDispatcher.P_UI_PANEL);

        d.onKeyPress(event ->
                bmh.handleKeyPress(event, lb),
                EventDispatcher.P_BIND_LOGIC);

        d.onKeyPress(event -> {
            if (kernel.inputPipeline().onKeyPressed(event.keyCode(), event.scanCode(), event.modifiers())) return CONSUMED;
            return PASS;
        }, EventDispatcher.P_INPUT_PIPELINE);

        d.onKeyPress(event -> {
            if (superScreen.keyPressed(event.keyCode(), event.scanCode(), event.modifiers())) return CONSUMED;
            return PASS;
        }, EventDispatcher.P_FALLBACK);
    }

    private void registerCharHandlers(EventDispatcher d, PanelRegistry pr,
            RtsFloatingWindowLayer fw, RtsClientKernel kernel) {
        pr.registerContentPanelCharTyped(d);

        d.onChar(event -> {
            if (fw.charTyped(event.codePoint(), event.modifiers())) return CONSUMED;
            if (kernel.inputPipeline().onCharTyped(event.codePoint(), event.modifiers())) return CONSUMED;
            if (superScreen.charTyped(event.codePoint(), event.modifiers())) return CONSUMED;
            return PASS;
        }, EventDispatcher.P_FALLBACK);
    }

    private void registerMouseMoveHandlers(EventDispatcher d, RtsFloatingWindowLayer fw) {
        d.onMouseMove(event -> {
            if (fw != null) fw.mouseMoved(event.x(), event.y());
            superScreen.mouseMoved(event.x(), event.y());
            return CONSUMED;
        }, EventDispatcher.P_FALLBACK);
    }

    private EventResult handleShortcut(KeyPressEvent event, RtsClientKernel kernel,
            TopBarPanel topBar, LeftSidebarPanel lb, GearMenuPanel gearMenu,
            BuilderScreen screen) {
        if (RtsKeyMappings.OPEN_GEAR_MENU_KEY.matches(event.keyCode(), event.scanCode())) {
            gearMenu.toggleOpen();
            topBar.setGearMenuOpen(gearMenu.isOpen());
            return CONSUMED;
        }
        if (RtsKeyMappings.TOGGLE_DEBUG_OVERLAY_KEY.matches(event.keyCode(), event.scanCode())) {
            topBar.toggleDebugOverlay();
            return CONSUMED;
        }
        if (RtsKeyMappings.TOGGLE_CAMERA_MODE_KEY.matches(event.keyCode(), event.scanCode())) {
            CameraModule cam = kernel.module(CameraModule.class);
            if (cam != null) cam.togglePlayerOrbitMode();
            return CONSUMED;
        }
        if (RtsKeyMappings.TOGGLE_SELECT_MODE_KEY.matches(event.keyCode(), event.scanCode())) {
            lb.toggleSelectMode();
            if (lb.isClickButtonSelected()) screen.clearBoxSelection();
            return CONSUMED;
        }
        if (RtsKeyMappings.TOGGLE_BIND_MODE_KEY.matches(event.keyCode(), event.scanCode())) {
            lb.toggleBindMode();
            return CONSUMED;
        }
        if (RtsKeyMappings.TOGGLE_DIRECTION_ROTATE_MODE_KEY.matches(event.keyCode(), event.scanCode())) {
            lb.toggleDirectionRotateMode();
            return CONSUMED;
        }
        if (RtsKeyMappings.TOGGLE_ITEM_PICKUP_MODE_KEY.matches(event.keyCode(), event.scanCode())) {
            lb.toggleItemPickupMode();
            return CONSUMED;
        }
        if (RtsKeyMappings.CYCLE_MODE_KEY.matches(event.keyCode(), event.scanCode())) {
            topBar.cycleMode();
            return CONSUMED;
        }
        return PASS;
    }

    private static boolean isAltDown() {
        long window = Minecraft.getInstance().getWindow().getWindow();
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_ALT) == GLFW.GLFW_PRESS;
    }

    private static boolean isShiftDown() {
        long window = Minecraft.getInstance().getWindow().getWindow();
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
    }
}
