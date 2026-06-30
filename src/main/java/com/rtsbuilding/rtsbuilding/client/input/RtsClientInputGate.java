package com.rtsbuilding.rtsbuilding.client.input;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.client.input.overlay.OverlayLayoutHelper.JeiOverlayIngredient;
import com.rtsbuilding.rtsbuilding.client.kernel.RtsClientKernel;
import com.rtsbuilding.rtsbuilding.client.module.camera.CameraModule;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

import java.util.List;

/**
 * 客户端输入门控——处理 GUI 层取消、手渲染取消等。
 * <p>
 * 在 NeoForge 1.21.1 中，GUI 通过 {@link RenderGuiLayerEvent} 分层渲染，
 * 即使 BuilderScreen 开着，某些层（准星、经验条等）仍可能渲染。
 * 这里在 RTS 模式活跃时取消这些层。</p>
 */
@EventBusSubscriber(modid = RtsbuildingMod.MODID, value = Dist.CLIENT)
public final class RtsClientInputGate {
    private RtsClientInputGate() {}

    private static boolean isRtsEnabled() {
        CameraModule cam = RtsClientKernel.get().module(CameraModule.class);
        return cam != null && cam.getState().isEnabled();
    }

    /**
     * 取消 RTS 模式下不需要的 GUI 层渲染（准星、经验条、快捷栏等）。
     */
    @SubscribeEvent
    public static void onRenderGuiLayer(RenderGuiLayerEvent.Pre event) {
        if (!isRtsEnabled()) return;

        if (event.getName().equals(VanillaGuiLayers.CROSSHAIR)
                || event.getName().equals(VanillaGuiLayers.HOTBAR)
                || event.getName().equals(VanillaGuiLayers.EXPERIENCE_BAR)) {
            event.setCanceled(true);
        }
    }

    /**
     * 取消 RTS 模式下玩家手的渲染（额外保底，相机实体机制已生效）。
     */
    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        if (isRtsEnabled()) {
            event.setCanceled(true);
        }
    }

    // ======================================================================
    //  JEI 兼容（占位）
    // ======================================================================

    public static List<Rect2i> getJeiOverlayExtraAreas(Screen screen) {
        return List.of();
    }

    public static JeiOverlayIngredient getJeiOverlayIngredientUnderMouse(double mouseX, double mouseY) {
        return null;
    }
}
