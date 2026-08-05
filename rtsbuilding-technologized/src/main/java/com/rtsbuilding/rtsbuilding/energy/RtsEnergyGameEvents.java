package com.rtsbuilding.rtsbuilding.energy;

import com.rtsbuilding.rtsbuilding.energy.server.RtsEnergyCostService;
import com.rtsbuilding.rtsbuilding.energy.server.RtsEnergyNetworkManager;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * Game (runtime) event handling for the built-in energy addon: keeps per-player
 * energy cost state and the in-world energy grid in sync with the server
 * lifecycle, without the main mod needing to know about them.
 */
@EventBusSubscriber(modid = RtsEnergyMod.MODID)
public final class RtsEnergyGameEvents {

    private RtsEnergyGameEvents() {
    }

    @SubscribeEvent
    static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            RtsEnergyCostService.forget(serverPlayer.getUUID());
        }
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        // Release references to all energy nodes (block entities of the stopped world)
        RtsEnergyNetworkManager.INSTANCE.clear();
    }
}
