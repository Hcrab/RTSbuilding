package com.rtsbuilding.rtsbuilding.compat.jade;

import com.mojang.blaze3d.platform.Window;
import com.rtsbuilding.rtsbuilding.client.screen.layout.JadeOverlayLayout;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.common.persist.RtsClientUiStateStore;
import com.rtsbuilding.rtsbuilding.mixin.KeyMappingAccessor;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;
import snownee.jade.JadeClient;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/**
 * RTSBuilding 与 Jade 的客户端兼容入口。
 *
 * <p>Jade 通过 {@link WailaPlugin} 自动发现本类。这里仅连接 RTS 射线、面板布局和
 * Jade 快捷键；Jade 未安装时本类不会被加载，服务端也不依赖 Jade。
 */
@WailaPlugin
public final class RtsJadePlugin implements IWailaPlugin {

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.addRayTraceCallback(1000, new RtsJadeRayTraceCallback());
        registration.addBeforeRenderCallback(1000, (rootElement, rect, guiGraphics, accessor) -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (!(minecraft.screen instanceof BuilderScreen screen)) {
                JadeOverlayLayout.clearReservation();
                return false;
            }
            if (RtsClientUiStateStore.isJadePanelHidden()) {
                JadeOverlayLayout.clearReservation();
                return true;
            }

            int panelWidth = rect.rect.getWidth();
            int panelHeight = rect.rect.getHeight();
            if (panelWidth <= 0 || panelHeight <= 0) {
                JadeOverlayLayout.clearReservation();
                return false;
            }

            Window window = minecraft.getWindow();
            int screenWidth = window.getGuiScaledWidth();
            int screenHeight = window.getGuiScaledHeight();
            JadeOverlayLayout.Position position;
            if (RtsClientUiStateStore.isJadePanelTrackMouseEnabled()) {
                int mouseX = (int) Math.round(
                        minecraft.mouseHandler.xpos() * screenWidth / Math.max(1, window.getScreenWidth()));
                int mouseY = (int) Math.round(
                        minecraft.mouseHandler.ypos() * screenHeight / Math.max(1, window.getScreenHeight()));
                position = JadeOverlayLayout.followingCursor(
                        screenWidth, screenHeight, panelWidth, panelHeight, mouseX, mouseY);
                JadeOverlayLayout.clearReservation();
            } else {
                double currentGuiScale = window.getScreenWidth() / (double) Math.max(1, screenWidth);
                double renderScale = screen.getRtsGuiScale() / currentGuiScale;
                position = JadeOverlayLayout.anchored(
                        screenWidth, screenHeight, panelWidth, panelHeight, renderScale);
                JadeOverlayLayout.publishAnchoredReservation(position.x(), renderScale);
            }

            rect.rect.setX(position.x());
            rect.rect.setY(position.y());
            return false;
        });

        NeoForge.EVENT_BUS.addListener(EventPriority.LOW, this::onJadeKeyInput);
    }

    /**
     * Screen 打开时原版不会为键位增加 clickCount；在 RTS 界面中补回 Jade 自己的快捷键点击。
     */
    private void onJadeKeyInput(InputEvent.Key event) {
        if (event.getAction() != GLFW.GLFW_PRESS
                || !(Minecraft.getInstance().screen instanceof BuilderScreen)) {
            return;
        }
        KeyMapping matched = findJadeKeyMapping(event.getKey(), event.getScanCode());
        if (matched == null) {
            return;
        }
        KeyMappingAccessor accessor = (KeyMappingAccessor) matched;
        accessor.setClickCount(accessor.getClickCount() + 1);
        JadeClient.onKeyPressed(event.getAction());
    }

    private static KeyMapping findJadeKeyMapping(int keyCode, int scanCode) {
        if (JadeClient.openConfig != null && JadeClient.openConfig.matches(keyCode, scanCode)) {
            return JadeClient.openConfig;
        }
        if (JadeClient.showOverlay != null && JadeClient.showOverlay.matches(keyCode, scanCode)) {
            return JadeClient.showOverlay;
        }
        if (JadeClient.toggleLiquid != null && JadeClient.toggleLiquid.matches(keyCode, scanCode)) {
            return JadeClient.toggleLiquid;
        }
        if (JadeClient.narrate != null && JadeClient.narrate.matches(keyCode, scanCode)) {
            return JadeClient.narrate;
        }
        return null;
    }
}
