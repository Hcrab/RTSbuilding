package com.rtsbuilding.rtsbuilding.compat.jade;

import com.mojang.blaze3d.platform.Window;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.common.persist.RtsClientUiStateStore;
import com.rtsbuilding.rtsbuilding.mixin.KeyMappingAccessor;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;
import snownee.jade.JadeClient;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/**
 * RTSbuilding × Jade 兼容插件入口。
 * <p>
 * 通过 {@code @WailaPlugin} 注解由 Jade 自动发现，在客户端注册
 * {@link RtsJadeRayTraceCallback} 替换 Jade 默认的射线检测，
 * 使 Jade 的信息面板在 RTS 模式下基于 RTS 的射线命中数据工作。
 * <p>
 * 同时注册：
 * <ul>
 *   <li>{@code JadeBeforeRenderCallback} —— "Jade 面板追踪鼠标"功能</li>
 *   <li>低优先级 {@code InputEvent.Key} 处理器 —— 修复 RTS 模式下
 *       Jade 快捷键无效的问题（绕过 Screen 打开时
 *       {@code KeyMapping.click()} 被跳过的限制）</li>
 * </ul>
 * <p>
 * Jade 未安装时此类不会被加载，天然弱依赖。
 */
@WailaPlugin
public class RtsJadePlugin implements IWailaPlugin {

    private static final int MOUSE_OFFSET = 8;

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.addRayTraceCallback(1000, new RtsJadeRayTraceCallback());
        registration.addBeforeRenderCallback(1000, (rootElement, rect, guiGraphics, accessor) -> {
            Minecraft mc = Minecraft.getInstance();
            if (!(mc.screen instanceof BuilderScreen)) {
                return false;
            }
            if (!RtsClientUiStateStore.isJadePanelTrackMouseEnabled()) {
                return false;
            }
            Window window = mc.getWindow();
            int panelW = rect.rect.getWidth();
            int panelH = rect.rect.getHeight();
            if (panelW <= 0 || panelH <= 0) {
                return false;
            }
            double rawMouseX = mc.mouseHandler.xpos();
            double rawMouseY = mc.mouseHandler.ypos();
            int mouseX = (int) (rawMouseX * window.getGuiScaledWidth() / window.getScreenWidth());
            int mouseY = (int) (rawMouseY * window.getGuiScaledHeight() / window.getScreenHeight());
            int screenW = window.getGuiScaledWidth();
            int screenH = window.getGuiScaledHeight();

            // 默认放在鼠标右侧
            int panelX = mouseX + MOUSE_OFFSET;
            // 右侧屏幕放不下时，切换到鼠标左侧
            if (panelX + panelW > screenW) {
                panelX = mouseX - panelW - MOUSE_OFFSET;
            }
            // 垂直居中于鼠标
            int panelY = mouseY - panelH / 2;
            // 确保不超出屏幕边界
            panelY = Mth.clamp(panelY, 0, screenH - panelH);

            rect.rect.setX(panelX);
            rect.rect.setY(panelY);
            return false;
        });

        NeoForge.EVENT_BUS.addListener(EventPriority.LOW, this::onJadeKeyInput);
    }

    /**
     * 修复 RTS 模式下 Jade 快捷键无效的问题。
     * <p>
     * Minecraft 在 Screen 打开时不会调用 {@code KeyMapping.click()}，
     * 因此 Jade 键位的 {@code clickCount} 始终为 0，{@code consumeClick()} 返回 false。
     * <p>
     * 此处理器以低优先级运行（Jade 自己的 {@code InputEvent.Key} 处理器使用默认优先级，
     * 先执行但因 clickCount=0 而无操作），之后手动递增匹配的 Jade 键位 clickCount
     * 并重新调用 {@code JadeClient.onKeyPressed()}，使快捷键生效。
     */
    private void onJadeKeyInput(InputEvent.Key event) {
        if (event.getAction() != GLFW.GLFW_PRESS) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof BuilderScreen)) {
            return;
        }
        int keyCode = event.getKey();
        int scanCode = event.getScanCode();
        KeyMapping matched = findJadeKeyMapping(keyCode, scanCode);
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
