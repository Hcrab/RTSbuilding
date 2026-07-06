package com.rtsbuilding.rtsbuilding.client.bootstrap;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.client.kernel.RtsClientKernel;
import com.rtsbuilding.rtsbuilding.client.module.camera.CameraModule;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.RtsCraftTerminalScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * 输入事件桥接——将 NeoForge 事件桥接到新架构的 InputPipeline。
 *
 * <p>注意：{@link BuilderScreen} 自身也委托给 InputPipeline，
 * 为避免双重处理，本桥接器在 BuilderScreen 打开时跳过事件。
 * {@link BuilderScreen#mouseDragged} 的委托已在本桥接器的
 * {@link #onMouseDragged} 中统一处理。</p>
 */
@EventBusSubscriber(modid = RtsbuildingMod.MODID, value = Dist.CLIENT)
public final class ClientInputBridge {

    private ClientInputBridge() {}

    private static RtsClientKernel kernel() {
        return RtsClientKernel.get();
    }

    /** BuilderScreen 打开时跳过，由 BuilderScreen 自身委托给 InputPipeline。 */
    private static boolean shouldSkip() {
        return Minecraft.getInstance().screen instanceof com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
    }

    @SubscribeEvent
    public static void onMouseClicked(ScreenEvent.MouseButtonPressed.Pre event) {
        if (shouldSkip()) return;
        if (kernel().inputPipeline().onMouseClicked(event.getMouseX(), event.getMouseY(), event.getButton())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onMouseReleased(ScreenEvent.MouseButtonReleased.Pre event) {
        if (shouldSkip()) return;
        if (kernel().inputPipeline().onMouseReleased(event.getMouseX(), event.getMouseY(), event.getButton())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onMouseScrolled(ScreenEvent.MouseScrolled.Pre event) {
        if (shouldSkip()) return;
        if (kernel().inputPipeline().onMouseScrolled(event.getMouseX(), event.getMouseY(), event.getScrollDeltaX(), event.getScrollDeltaY())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onMouseDragged(ScreenEvent.MouseDragged.Pre event) {
        if (shouldSkip()) return;
        if (kernel().inputPipeline().onMouseDragged(event.getMouseX(), event.getMouseY(), event.getMouseButton(), event.getDragX(), event.getDragY())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onKeyPressed(ScreenEvent.KeyPressed.Pre event) {
        if (shouldSkip()) return;
        if (kernel().inputPipeline().onKeyPressed(event.getKeyCode(), event.getScanCode(), event.getModifiers())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onCharTyped(ScreenEvent.CharacterTyped.Pre event) {
        if (shouldSkip()) return;
        if (kernel().inputPipeline().onCharTyped((char) event.getCodePoint(), 0)) {
            event.setCanceled(true);
        }
    }

    /**
     * 屏幕打开拦截——RTS 模式下将容器屏幕注入到 BuilderScreen 的覆盖层中。
     * <p>当 RTS 摄像机活跃且 BuilderScreen 是当前屏幕时，
     * 阻止容器屏幕（ChestScreen、FurnaceScreen、MerchantScreen 等）替换 BuilderScreen，
     * 改为将其存储为 BuilderScreen 的子覆盖层进行渲染和交互。
     * 这样 RTS 模式和摄像机保持活跃，容器 GUI 在 RTS UI 之上渲染。</p>
     */
    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        CameraModule cam = kernel().module(CameraModule.class);
        if (cam == null || !cam.getState().isEnabled()) return;

        Minecraft mc = Minecraft.getInstance();
        Screen current = mc.screen;
        if (!(current instanceof BuilderScreen builderScreen)) return;

        Screen newScreen = event.getScreen();
        // 允许 RTS 自有屏幕通过
        if (newScreen instanceof BuilderScreen || newScreen instanceof RtsCraftTerminalScreen) return;

        // 拦截容器屏幕 → 注入为 BuilderScreen 的子覆盖层
        if (newScreen instanceof AbstractContainerScreen<?> containerScreen) {
            RtsbuildingMod.LOGGER.debug("RTS: Intercepting {} as overlay in BuilderScreen",
                    containerScreen.getClass().getSimpleName());
            builderScreen.showContainerScreen(containerScreen);
            event.setCanceled(true);
        }
        // 非容器屏幕（聊天、设置等）允许正常替换 BuilderScreen，
        // 但 BuilderScreen.onClose() 会通过 CameraModule.disableCamera() 停用相机。
        // 对于非容器屏幕暂不做特殊处理。
    }
}
