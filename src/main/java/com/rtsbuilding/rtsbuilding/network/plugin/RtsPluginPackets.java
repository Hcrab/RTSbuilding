package com.rtsbuilding.rtsbuilding.network.plugin;

import com.rtsbuilding.rtsbuilding.network.ClientPayloadDispatcher;
import com.rtsbuilding.rtsbuilding.forgecompat.network.ForgePayloadRegistrar;
import com.rtsbuilding.rtsbuilding.network.plugin.handler.RtsPluginNetworkHandlers;

/**
 * Registers RTS plugin install, uninstall, and sync packets.
 */
public final class RtsPluginPackets {
    private RtsPluginPackets() {
    }

    public static void register(ForgePayloadRegistrar registrar) {
        registrar.playToServer(
                C2SRtsInstallPluginPayload.TYPE,
                C2SRtsInstallPluginPayload.STREAM_CODEC,
                RtsPluginNetworkHandlers::handleInstall);

        registrar.playToServer(
                C2SRtsUninstallPluginPayload.TYPE,
                C2SRtsUninstallPluginPayload.STREAM_CODEC,
                RtsPluginNetworkHandlers::handleUninstall);

        registrar.playToServer(
                C2SRtsRequestPluginsPayload.TYPE,
                C2SRtsRequestPluginsPayload.STREAM_CODEC,
                RtsPluginNetworkHandlers::handleRequestPlugins);

        registrar.playToClient(
                S2CRtsPluginStatePayload.TYPE,
                S2CRtsPluginStatePayload.STREAM_CODEC,
                ClientPayloadDispatcher::dispatchPlugin);
    }
}
