package com.rtsbuilding.rtsbuilding.client.screen.blueprint;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.client.rendering.blueprint.BlueprintGhostRenderer;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Forge 蓝图库客户端生命期 owner。
 * 换世界/断线取消旧代 worker 与渲染网格；客户端 tick 只负责有界发布后台结果。
 */
@Mod.EventBusSubscriber(modid = RtsbuildingMod.MODID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BlueprintClientLifecycle {
    private static Object world;

    private BlueprintClientLifecycle() {
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft minecraft = Minecraft.getInstance();
        Object currentWorld = minecraft.level;
        if (world != currentWorld) {
            BlueprintPanel.closeLibrary();
            BlueprintGhostRenderer.invalidate();
            world = currentWorld;
        }
        if (currentWorld != null) BlueprintPanel.tickLibrary();
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        BlueprintPanel.closeLibrary();
        BlueprintGhostRenderer.invalidate();
        world = null;
    }
}
