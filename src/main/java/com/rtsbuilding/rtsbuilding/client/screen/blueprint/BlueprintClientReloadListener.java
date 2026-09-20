package com.rtsbuilding.rtsbuilding.client.screen.blueprint;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.client.rendering.blueprint.BlueprintGhostRenderer;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** 蓝图专用资源重载接线；不修改共享 RtsClientModEvents。 */
@Mod.EventBusSubscriber(modid = RtsbuildingMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD,
        value = Dist.CLIENT)
public final class BlueprintClientReloadListener {
    private BlueprintClientReloadListener() {
    }

    @SubscribeEvent
    public static void register(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) manager ->
                invalidateBlueprintResources());
    }

    private static void invalidateBlueprintResources() {
        BlueprintMaterialInspector.clearCache();
        BlueprintGhostRenderer.invalidate();
    }
}
