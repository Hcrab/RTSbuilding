package com.rtsbuilding.rtsbuilding.server.network;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** 只负责清理区域破坏分片的临时网络状态，不参与任务执行。 */
@Mod.EventBusSubscriber(modid = RtsbuildingMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RtsAreaDestroyFragmentEvents {
    private RtsAreaDestroyFragmentEvents() {
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            RtsAreaDestroyFragmentNetwork.clear(player.getUUID());
        }
    }

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
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.getServer().overworld() == null) return;
        RtsAreaDestroyFragmentNetwork.expireAll(event.getServer().overworld().getGameTime(), owner -> {
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(owner);
            if (player != null) {
                player.displayClientMessage(Component.translatable(
                        "message.rtsbuilding.mining.transfer_incomplete"), false);
            }
        });
    }
}
