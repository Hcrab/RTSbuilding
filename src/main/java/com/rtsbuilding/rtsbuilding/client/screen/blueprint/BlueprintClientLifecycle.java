package com.rtsbuilding.rtsbuilding.client.screen.blueprint;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/**
 * 蓝图库异步任务的客户端生命期适配器，不拥有选择或文件结果。
 * 换世界时取消旧代任务；每 tick 最多发布一条，后台无需等待面板一直保持打开。
 */
@EventBusSubscriber(modid = RtsbuildingMod.MODID, value = Dist.CLIENT)
public final class BlueprintClientLifecycle {
    private static Object world;

    private BlueprintClientLifecycle() { }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Object currentWorld = Minecraft.getInstance().level;
        if (world != currentWorld) {
            BlueprintPanel.closeLibrary();
            world = currentWorld;
        }
        if (currentWorld != null) BlueprintPanel.tickLibrary();
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        BlueprintPanel.closeLibrary();
        world = null;
    }
}
