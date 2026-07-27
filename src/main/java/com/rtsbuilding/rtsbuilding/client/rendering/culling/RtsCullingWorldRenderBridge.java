package com.rtsbuilding.rtsbuilding.client.rendering.culling;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.client.rendering.blueprint.BlueprintCaptureRenderer;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

/**
 * 把尚未迁完的主线总渲染器中的捕获/剔除预览接入 Forge 1.12 世界末尾阶段。
 *
 * <p>该桥只负责生命周期分发，不持有缓冲或 GL 状态。两个渲染器均使用自己的缓冲，因此不会结束
 * Minecraft 的 Tessellator，也不会破坏其他模组在同一事件阶段提交的顶点。</p>
 */
@Mod.EventBusSubscriber(modid = RtsbuildingMod.MODID, value = Side.CLIENT)
public final class RtsCullingWorldRenderBridge {
    private RtsCullingWorldRenderBridge() {
    }

    @SubscribeEvent
    public static void onRenderWorldLast(RenderWorldLastEvent event) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.world == null || minecraft.player == null) return;
        RtsCullingRenderer.render();
        BlueprintCaptureRenderer.renderBlueprintCaptureBox();
    }
}
