package com.rtsbuilding.rtsbuilding.server.plugin;

import com.rtsbuilding.rtsbuilding.server.data.RtsSharedProgressionData;
import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Computes the server-authoritative plugin state for personal and shared teams.
 *
 * <p>Without a shared progression key, plugins stay in the player's personal
 * save data. With a shared key, plugins are persisted once on the shared
 * progression record, while each entry still records the player who contributed
 * the item. This keeps team capability shared without letting one teammate
 * uninstall another teammate's plugin item.
 */
final class RtsPluginTeamService {
    private RtsPluginTeamService() {
    }

    static List<EffectivePlugin> effectivePlugins(ServerPlayer player) {
        if (player == null) {
            return List.of();
        }
        Map<ResourceLocation, EffectivePlugin> merged = new LinkedHashMap<>();
        for (StoredPlugin stored : installedPlugins(player)) {
            addEffective(merged, stored.plugin(), stored.isOwnedBy(player), stored.ownerName());
        }
        return new ArrayList<>(merged.values());
    }

    static List<StoredPlugin> installedPlugins(ServerPlayer player) {
        if (player == null) {
            return List.of();
        }
        String sharedKey = RtsProgressionManager.sharedProgressionKey(player);
        if (sharedKey.isBlank()) {
            return wrapPersonalPlugins(player, RtsPluginPersistence.load(player));
        }

        List<StoredPlugin> shared = loadSharedPlugins(player, sharedKey);
        migratePersonalPluginsIntoTeam(player, sharedKey, shared);
        return loadSharedPlugins(player, sharedKey);
    }

    static void saveInstalledPlugins(ServerPlayer player, List<StoredPlugin> installed) {
        if (player == null) {
            return;
        }
        String sharedKey = RtsProgressionManager.sharedProgressionKey(player);
        if (sharedKey.isBlank()) {
            List<RtsInstalledPlugin> personal = new ArrayList<>();
            if (installed != null) {
                for (StoredPlugin stored : installed) {
                    if (stored != null) {
                        personal.add(stored.plugin());
                    }
                }
            }
            RtsPluginPersistence.save(player, personal);
            return;
        }

        List<RtsSharedProgressionData.SharedPlugin> sharedPlugins = new ArrayList<>();
        if (installed != null) {
            for (StoredPlugin stored : installed) {
                if (stored == null) {
                    continue;
                }
                sharedPlugins.add(new RtsSharedProgressionData.SharedPlugin(
                        stored.plugin().pluginId(),
                        stored.plugin().stack(),
                        stored.plugin().installedGameTime(),
                        stored.ownerId(),
                        stored.ownerName()));
            }
        }
        RtsProgressionManager.sharedProgressionData(player).setPlugins(sharedKey, sharedPlugins);
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

    static String teamLabel(ServerPlayer player) {
        String sharedKey = RtsProgressionManager.sharedProgressionKey(player);
        if (sharedKey.isBlank()) {
            return "";
        }
        return RtsProgressionManager.sharedProgressionLabel(player);
    }

    private static List<StoredPlugin> loadSharedPlugins(ServerPlayer player, String sharedKey) {
        List<RtsSharedProgressionData.SharedPlugin> plugins =
                RtsProgressionManager.sharedProgressionData(player).plugins(sharedKey);
        List<StoredPlugin> installed = new ArrayList<>(plugins.size());
        for (RtsSharedProgressionData.SharedPlugin plugin : plugins) {
            installed.add(new StoredPlugin(
                    new RtsInstalledPlugin(plugin.pluginId(), plugin.stack(), plugin.installedGameTime()),
                    plugin.ownerId(),
                    plugin.ownerName()));
        }
        return installed;
    }

    private static List<StoredPlugin> wrapPersonalPlugins(ServerPlayer player, List<RtsInstalledPlugin> plugins) {
        List<StoredPlugin> installed = new ArrayList<>(plugins.size());
        UUID ownerId = player.getUUID();
        String ownerName = player.getGameProfile().getName();
        for (RtsInstalledPlugin plugin : plugins) {
            installed.add(new StoredPlugin(plugin, ownerId, ownerName));
        }
        return installed;
    }

    private static void migratePersonalPluginsIntoTeam(ServerPlayer player, String sharedKey, List<StoredPlugin> shared) {
        List<RtsInstalledPlugin> personal = RtsPluginPersistence.load(player);
        if (personal.isEmpty()) {
            return;
        }
        List<RtsInstalledPlugin> remainingPersonal = new ArrayList<>();
        boolean changed = false;
        for (RtsInstalledPlugin plugin : personal) {
            if (canAddWithoutTeamConflict(shared, plugin)) {
                shared.add(new StoredPlugin(plugin, player.getUUID(), player.getGameProfile().getName()));
                changed = true;
            } else {
                remainingPersonal.add(plugin);
            }
        }
        if (changed) {
            saveInstalledPlugins(player, shared);
            RtsPluginPersistence.save(player, remainingPersonal);
        }
    }

    private static boolean canAddWithoutTeamConflict(List<StoredPlugin> installed, RtsInstalledPlugin plugin) {
        RtsPluginDefinition definition = RtsPluginRegistry.byId(plugin.pluginId());
        if (definition == null) {
            return false;
        }
        for (StoredPlugin entry : installed) {
            RtsPluginDefinition existing = RtsPluginRegistry.byId(entry.plugin().pluginId());
            if (existing == null) {
                continue;
            }
            if (existing.id().equals(definition.id())) {
                return false;
            }
            if (definition.family() == RtsPluginFamily.RANGE_EXTENSION
                    && existing.family() == RtsPluginFamily.RANGE_EXTENSION) {
                return false;
            }
        }
        return true;
    }

    private static void addEffective(Map<ResourceLocation, EffectivePlugin> merged, RtsInstalledPlugin plugin,
            boolean personal, String ownerName) {
        if (plugin == null || plugin.pluginId() == null || plugin.stack().isEmpty()) {
            return;
        }
        merged.putIfAbsent(plugin.pluginId(), new EffectivePlugin(plugin, personal, ownerName));
    }

    record StoredPlugin(RtsInstalledPlugin plugin, UUID ownerId, String ownerName) {
        StoredPlugin {
            ownerName = ownerName == null ? "" : ownerName;
        }

        boolean isOwnedBy(ServerPlayer player) {
            return player != null && ownerId != null && ownerId.equals(player.getUUID());
        }
    }

    record EffectivePlugin(RtsInstalledPlugin plugin, boolean personal, String ownerName) {
        EffectivePlugin {
            ownerName = ownerName == null ? "" : ownerName;
        }
    }
}
