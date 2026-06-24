package com.rtsbuilding.rtsbuilding.client.bootstrap;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.client.kernel.RtsClientKernel;
import com.rtsbuilding.rtsbuilding.client.kernel.StateEvent;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * 客户端 tick 处理器——驱动 TLK 内核的 tick 循环 + 死亡检测。
 */
@EventBusSubscriber(modid = RtsbuildingMod.MODID, value = Dist.CLIENT)
public final class ClientTickHandler {

    private ClientTickHandler() {}

    private static boolean wasDead;

    @SubscribeEvent
    public static void onClientTickPre(ClientTickEvent.Pre event) {
        RtsClientKernel kernel = RtsClientKernel.get();
        if (!kernel.isInitialized()) return;

        kernel.inputPipeline().onTickPre();

        // 死亡检测：玩家刚死的瞬间分发 PlayerDied 事件
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            boolean isDead = !mc.player.isAlive() || mc.player.isDeadOrDying();
            if (isDead && !wasDead) {
                kernel.dispatch(new StateEvent.PlayerDied());
            }
            wasDead = isDead;
        } else {
            wasDead = false;
        }
    }

    @SubscribeEvent
    public static void onClientTickPost(ClientTickEvent.Post event) {
        RtsClientKernel kernel = RtsClientKernel.get();
        if (kernel.isInitialized()) {
            kernel.tick();
            kernel.inputPipeline().onTickPost();
        }
    }
}
