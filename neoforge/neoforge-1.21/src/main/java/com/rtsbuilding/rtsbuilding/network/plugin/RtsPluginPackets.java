package com.rtsbuilding.rtsbuilding.network.plugin;

import com.rtsbuilding.rtsbuilding.network.ClientPayloadDispatcher;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Registers RTS plugin install, uninstall, and sync packets.
 */
public final class RtsPluginPackets {
    private RtsPluginPackets() {
    }

    public static void register(PayloadRegistrar registrar) {

        registrar.playToClient(
                S2CRtsPluginStatePayload.TYPE,
                S2CRtsPluginStatePayload.STREAM_CODEC,
                ClientPayloadDispatcher::dispatchPlugin);
    }
}
