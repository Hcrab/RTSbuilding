package com.rtsbuilding.rtsbuilding.server.plugin;

import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Computes team-effective plugin state without moving plugin items.
 *
 * <p>Personal installed plugin inventories remain owned by each player. When
 * survival progression sharing is enabled, this service merges online teammates'
 * installed plugin definitions for permission and display purposes only. It
 * does not persist team plugins, grant items, or allow one player to uninstall
 * another player's plugin.
 */
final class RtsPluginTeamService {
    private RtsPluginTeamService() {
    }

    static List<EffectivePlugin> effectivePlugins(ServerPlayer player) {
        if (player == null) {
            return List.of();
        }
        Map<ResourceLocation, EffectivePlugin> merged = new LinkedHashMap<>();
        for (RtsInstalledPlugin plugin : RtsPluginService.installedPlugins(player)) {
            addEffective(merged, plugin, true);
        }

        String sharedKey = RtsProgressionManager.sharedProgressionKey(player);
        if (sharedKey.isBlank() || player.getServer() == null) {
            return new ArrayList<>(merged.values());
        }

        for (ServerPlayer teammate : player.getServer().getPlayerList().getPlayers()) {
            if (teammate == null || teammate.getUUID().equals(player.getUUID())) {
                continue;
            }
            if (!sharedKey.equals(RtsProgressionManager.sharedProgressionKey(teammate))) {
                continue;
            }
            for (RtsInstalledPlugin plugin : RtsPluginService.installedPlugins(teammate)) {
                addEffective(merged, plugin, false);
            }
        }
        return new ArrayList<>(merged.values());
    }

    static List<ServerPlayer> relatedPlayers(ServerPlayer player) {
        if (player == null || player.getServer() == null) {
            return List.of();
        }
        String sharedKey = RtsProgressionManager.sharedProgressionKey(player);
        if (sharedKey.isBlank()) {
            return List.of(player);
        }
        List<ServerPlayer> related = new ArrayList<>();
        for (ServerPlayer onlinePlayer : player.getServer().getPlayerList().getPlayers()) {
            if (onlinePlayer != null && sharedKey.equals(RtsProgressionManager.sharedProgressionKey(onlinePlayer))) {
                related.add(onlinePlayer);
            }
        }
        if (related.isEmpty()) {
            related.add(player);
        }
        return related;
    }

    private static void addEffective(Map<ResourceLocation, EffectivePlugin> merged, RtsInstalledPlugin plugin,
            boolean personal) {
        if (plugin == null || plugin.pluginId() == null || plugin.stack().isEmpty()) {
            return;
        }
        merged.putIfAbsent(plugin.pluginId(), new EffectivePlugin(plugin, personal));
    }

    record EffectivePlugin(RtsInstalledPlugin plugin, boolean personal) {
    }
}
