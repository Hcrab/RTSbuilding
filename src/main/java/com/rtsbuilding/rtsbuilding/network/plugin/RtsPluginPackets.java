package com.rtsbuilding.rtsbuilding.network.plugin;

import com.rtsbuilding.rtsbuilding.network.RtsPayloadRegistrar;
import com.rtsbuilding.rtsbuilding.network.plugin.handler.RtsPluginNetworkHandlers;
import cpw.mods.fml.relauncher.Side;

/** 插件域稳定 discriminator：68-71。 */
public final class RtsPluginPackets {
    private RtsPluginPackets() {
    }

    public static void register() {
        RtsPayloadRegistrar.registerMessage(68, RtsPluginNetworkHandlers.Install.class,
                C2SRtsInstallPluginPayload.class, Side.SERVER);
        RtsPayloadRegistrar.registerMessage(69, RtsPluginNetworkHandlers.Uninstall.class,
                C2SRtsUninstallPluginPayload.class, Side.SERVER);
        RtsPayloadRegistrar.registerMessage(70, RtsPluginNetworkHandlers.Request.class,
                C2SRtsRequestPluginsPayload.class, Side.SERVER);
        RtsPayloadRegistrar.registerMessage(71, RtsPluginNetworkHandlers.ClientState.class,
                S2CRtsPluginStatePayload.class, Side.CLIENT);
    }
}
