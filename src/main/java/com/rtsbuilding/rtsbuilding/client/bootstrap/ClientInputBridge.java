package com.rtsbuilding.rtsbuilding.client.bootstrap;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.client.kernel.RtsClientKernel;
import net.minecraft.client.Minecraft;
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
}
