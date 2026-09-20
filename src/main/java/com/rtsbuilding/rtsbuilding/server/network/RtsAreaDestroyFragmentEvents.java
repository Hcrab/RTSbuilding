package com.rtsbuilding.rtsbuilding.server.network;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** 只负责清理区域破坏分片的临时网络状态。 */
@EventBusSubscriber(modid = RtsbuildingMod.MODID)
public final class RtsAreaDestroyFragmentEvents {
    private RtsAreaDestroyFragmentEvents() {
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            RtsAreaDestroyFragmentNetwork.clear(player.getUUID());
        }
    }

    /** 未完成选区属于发起它的维度；换维度不能把旧坐标误用于新世界。 */
    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            RtsAreaDestroyFragmentNetwork.clear(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        RtsAreaDestroyFragmentNetwork.clearAll();
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        RtsAreaDestroyFragmentNetwork.expireAll(event.getServer().overworld().getGameTime(), owner -> {
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(owner);
            if (player != null) player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "message.rtsbuilding.mining.transfer_incomplete"), false);
        });
    }
}
