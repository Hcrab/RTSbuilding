package com.rtsbuilding.rtsbuilding.client.event;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.client.util.render.GuiRenderEnhancer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * GUI渲染事件处理器 - 处理GUI渲染增强功能
 * 
 * <p>此处理器监听GUI渲染事件，并应用抗锯齿和其他渲染增强功能
 * 以提升Minecraft GUI界面的视觉质量。</p>
 */
@EventBusSubscriber(modid = RtsbuildingMod.MODID, value = Dist.CLIENT)
public class GuiRenderHandler {

    /**
     * 在GUI层渲染前应用高质量渲染设置
     */
    @SubscribeEvent
    public static void onGuiLayerRenderPre(RenderGuiLayerEvent.Pre event) {
        // 检查是否为我们的自定义界面，或对所有GUI应用增强
        if (shouldApplyEnhancedRendering()) {
            GuiRenderEnhancer.beginHighQualityRender(event.getGuiGraphics());
        }
    }

    /**
     * 在GUI层渲染后恢复标准渲染设置
     */
    @SubscribeEvent
    public static void onGuiLayerRenderPost(RenderGuiLayerEvent.Post event) {
        // 检查是否为我们的自定义界面，或对所有GUI应用增强
        if (shouldApplyEnhancedRendering()) {
            GuiRenderEnhancer.endHighQualityRender(event.getGuiGraphics());
        }
    }

    /**
     * 在屏幕渲染前应用高质量渲染设置
     */
    @SubscribeEvent
    public static void onScreenRenderPre(ScreenEvent.Render.Pre event) {
        if (shouldApplyEnhancedRendering()) {
            GuiRenderEnhancer.beginHighQualityRender(null);
        }
    }

    /**
     * 在屏幕渲染后恢复标准渲染设置
     */
    @SubscribeEvent
    public static void onScreenRenderPost(ScreenEvent.Render.Post event) {
        if (shouldApplyEnhancedRendering()) {
            GuiRenderEnhancer.endHighQualityRender(null);
        }
    }

    /**
     * 确定是否应该应用增强渲染
     * 
     * <p>可以根据当前屏幕类型或其他条件来决定是否应用渲染增强</p>
     */
    private static boolean shouldApplyEnhancedRendering() {
        // 可以根据需要添加条件，比如只对特定界面应用增强
        // 当前设置为总是应用（如果硬件支持）
        try {
            return GuiRenderEnhancer.isAntialiasingSupported();
        } catch (Exception e) {
            // 如果有任何错误，返回false以避免崩溃
            return false;
        }
    }
}