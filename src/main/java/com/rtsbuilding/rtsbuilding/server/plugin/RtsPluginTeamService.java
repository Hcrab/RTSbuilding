package com.rtsbuilding.rtsbuilding.server.plugin;

import com.rtsbuilding.rtsbuilding.server.data.RtsSharedProgressionData;
import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ResourceLocation;

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

    static List<EffectivePlugin> effectivePlugins(EntityPlayerMP player) {
        if (player == null) {
            return java.util.Collections.emptyList();
        }
        Map<ResourceLocation, EffectivePlugin> merged = new LinkedHashMap<>();
        for (StoredPlugin stored : installedPlugins(player)) {
            addEffective(merged, stored.plugin(), stored.isOwnedBy(player), stored.ownerName());
        }
        return new ArrayList<>(merged.values());
    }

    static List<StoredPlugin> installedPlugins(EntityPlayerMP player) {
        if (player == null) {
            return java.util.Collections.emptyList();
        }
        String sharedKey = RtsProgressionManager.sharedProgressionKey(player);
        if (sharedKey.isEmpty()) {
            return wrapPersonalPlugins(player, RtsPluginPersistence.load(player));
        }

        List<StoredPlugin> shared = loadSharedPlugins(player, sharedKey);
        migratePersonalPluginsIntoTeam(player, sharedKey, shared);
        return loadSharedPlugins(player, sharedKey);
    }

    static void saveInstalledPlugins(EntityPlayerMP player, List<StoredPlugin> installed) {
        if (player == null) {
            return;
        }
        String sharedKey = RtsProgressionManager.sharedProgressionKey(player);
        if (sharedKey.isEmpty()) {
            List<RtsInstalledPlugin> personal = new ArrayList<>();
            if (installed != null) {
                for (StoredPlugin stored : installed) {
                    if (stored != null) {
                        personal.add(stored.plugin());
                    }
                }
            }
            RtsPluginPersistence.save(player, personal);
        } else {
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
        RtsPluginDurability.checkpoint(player);
    }

    static List<EntityPlayerMP> relatedPlayers(EntityPlayerMP player) {
        if (player == null || com.rtsbuilding.rtsbuilding.platform.server.ServerCompat.getServer(player) == null) {
            return java.util.Collections.emptyList();
        }
        String sharedKey = RtsProgressionManager.sharedProgressionKey(player);
        if (sharedKey.isEmpty()) {
            return java.util.Collections.singletonList(player);
        }
        List<EntityPlayerMP> related = new ArrayList<>();
        for (EntityPlayerMP onlinePlayer : com.rtsbuilding.rtsbuilding.platform.server.ServerCompat.getPlayerList(com.rtsbuilding.rtsbuilding.platform.server.ServerCompat.getServer(player)).getPlayers()) {
            if (onlinePlayer != null && sharedKey.equals(RtsProgressionManager.sharedProgressionKey(onlinePlayer))) {
                related.add(onlinePlayer);
            }
        }
        if (related.isEmpty()) {
            related.add(player);
        }
        return related;
    }

    static String teamLabel(EntityPlayerMP player) {
        String sharedKey = RtsProgressionManager.sharedProgressionKey(player);
        if (sharedKey.isEmpty()) {
            return "";
        }
        return RtsProgressionManager.sharedProgressionLabel(player);
    }

    private static List<StoredPlugin> loadSharedPlugins(EntityPlayerMP player, String sharedKey) {
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

    private static List<StoredPlugin> wrapPersonalPlugins(EntityPlayerMP player, List<RtsInstalledPlugin> plugins) {
        List<StoredPlugin> installed = new ArrayList<>(plugins.size());
        UUID ownerId = player.getUniqueID();
        String ownerName = player.getGameProfile().getName();
        for (RtsInstalledPlugin plugin : plugins) {
            installed.add(new StoredPlugin(plugin, ownerId, ownerName));
        }
        return installed;
    }

    private static void migratePersonalPluginsIntoTeam(EntityPlayerMP player, String sharedKey, List<StoredPlugin> shared) {
        List<RtsInstalledPlugin> personal = RtsPluginPersistence.load(player);
        if (personal.isEmpty()) {
            return;
        }
        List<RtsInstalledPlugin> remainingPersonal = new ArrayList<>();
        boolean changed = false;
        for (RtsInstalledPlugin plugin : personal) {
            if (canAddWithoutTeamConflict(shared, plugin)) {
                shared.add(new StoredPlugin(plugin, player.getUniqueID(), player.getGameProfile().getName()));
                changed = true;
            } else {
                remainingPersonal.add(plugin);
            }
        }
        if (changed) {
            saveInstalledPlugins(player, shared);
            RtsPluginPersistence.save(player, remainingPersonal);
            RtsPluginDurability.checkpoint(player);
        }
    }

    static boolean canAddWithoutTeamConflict(List<StoredPlugin> installed, RtsInstalledPlugin plugin) {
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
            if (definition.family() == existing.family() && definition.family().mutuallyExclusive()) {
                return false;
            }
        }
        return true;
    }

    private static void addEffective(Map<ResourceLocation, EffectivePlugin> merged, RtsInstalledPlugin plugin,
            boolean personal, String ownerName) {
        if (plugin == null || plugin.pluginId() == null || com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(plugin.stack())) {
            return;
        }
        merged.putIfAbsent(plugin.pluginId(), new EffectivePlugin(plugin, personal, ownerName));
    }

    static final class StoredPlugin {
        private final RtsInstalledPlugin plugin;
        private final UUID ownerId;
        private final String ownerName;

        StoredPlugin(RtsInstalledPlugin plugin, UUID ownerId, String ownerName) {
            this.plugin = plugin;
            this.ownerId = ownerId;
            this.ownerName = ownerName == null ? "" : ownerName;
        }

        RtsInstalledPlugin plugin() { return plugin; }
        UUID ownerId() { return ownerId; }
        String ownerName() { return ownerName; }
        boolean isOwnedBy(EntityPlayerMP player) {
            return player != null && ownerId != null && ownerId.equals(player.getUniqueID());
        }
    }

    static final class EffectivePlugin {
        private final RtsInstalledPlugin plugin;
        private final boolean personal;
        private final String ownerName;

        EffectivePlugin(RtsInstalledPlugin plugin, boolean personal, String ownerName) {
            this.plugin = plugin;
            this.personal = personal;
            this.ownerName = ownerName == null ? "" : ownerName;
        }

        RtsInstalledPlugin plugin() { return plugin; }
        boolean personal() { return personal; }
        String ownerName() { return ownerName; }
    }
}
