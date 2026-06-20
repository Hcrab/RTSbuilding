package com.rtsbuilding.rtsbuilding.server.plugin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Player persistent-data codec for installed RTS plugins.
 *
 * <p>This class only serializes/deserializes the list. It does not decide if an
 * install is legal and it never mutates player inventory.
 */
final class RtsPluginPersistence {
    private static final String NBT_ROOT = "rtsbuilding_plugins";
    private static final String NBT_VERSION = "version";
    private static final String NBT_INSTALLED = "installed";
    private static final String NBT_PLUGIN_ID = "plugin_id";
    private static final String NBT_STACK = "stack";
    private static final String NBT_INSTALLED_GAME_TIME = "installed_game_time";

    private RtsPluginPersistence() {
    }

    static List<RtsInstalledPlugin> load(ServerPlayer player) {
        CompoundTag root = root(player);
        ListTag list = root.getList(NBT_INSTALLED, Tag.TAG_COMPOUND);
        List<RtsInstalledPlugin> installed = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag tag = list.getCompound(i);
            ResourceLocation pluginId = ResourceLocation.tryParse(tag.getString(NBT_PLUGIN_ID));
            if (pluginId == null) {
                continue;
            }
            ItemStack stack = ItemStack.of(tag.getCompound(NBT_STACK));
            if (stack.isEmpty()) {
                RtsPluginDefinition definition = RtsPluginRegistry.byId(pluginId);
                if (definition == null) {
                    continue;
                }
                stack = new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(definition.itemId()));
            }
            installed.add(new RtsInstalledPlugin(pluginId, stack, tag.getLong(NBT_INSTALLED_GAME_TIME)));
        }
        return installed;
    }

    static void save(ServerPlayer player, List<RtsInstalledPlugin> installed) {
        CompoundTag root = root(player);
        ListTag list = new ListTag();
        for (RtsInstalledPlugin plugin : installed) {
            if (plugin == null || plugin.pluginId() == null || plugin.stack().isEmpty()) {
                continue;
            }
            CompoundTag tag = new CompoundTag();
            tag.putString(NBT_PLUGIN_ID, plugin.pluginId().toString());
            tag.put(NBT_STACK, plugin.stack().copyWithCount(1).save(new CompoundTag()));
            tag.putLong(NBT_INSTALLED_GAME_TIME, plugin.installedGameTime());
            list.add(tag);
        }
        root.put(NBT_INSTALLED, list);
        player.getPersistentData().put(NBT_ROOT, root);
    }

    private static CompoundTag root(ServerPlayer player) {
        CompoundTag root = player.getPersistentData().getCompound(NBT_ROOT);
        if (root.isEmpty()) {
            root.putInt(NBT_VERSION, 1);
            root.put(NBT_INSTALLED, new ListTag());
            player.getPersistentData().put(NBT_ROOT, root);
        }
        return root;
    }
}
