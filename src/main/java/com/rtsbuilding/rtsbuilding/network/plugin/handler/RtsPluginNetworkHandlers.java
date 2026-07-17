package com.rtsbuilding.rtsbuilding.network.plugin.handler;

import com.rtsbuilding.rtsbuilding.network.plugin.C2SRtsInstallPluginPayload;
import com.rtsbuilding.rtsbuilding.network.plugin.C2SRtsRequestPluginsPayload;
import com.rtsbuilding.rtsbuilding.network.plugin.C2SRtsUninstallPluginPayload;
import com.rtsbuilding.rtsbuilding.server.plugin.RtsPluginService;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server-side packet adapter for RTS plugin inventory actions.
 */
public final class RtsPluginNetworkHandlers {
    private RtsPluginNetworkHandlers() {
    }

    public static void handleInstall(C2SRtsInstallPluginPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                RtsPluginService.installFromInventorySlot(player, payload.inventorySlot());
            }
        });
    }

    public static void handleUninstall(C2SRtsUninstallPluginPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                Identifier pluginId = Identifier.tryParse(payload.pluginId());
                RtsPluginService.uninstall(player, pluginId);
            }
        });
    }

    public static void handleRequestPlugins(C2SRtsRequestPluginsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                RtsPluginService.syncToPlayer(player);
            }
        });
    }
}
