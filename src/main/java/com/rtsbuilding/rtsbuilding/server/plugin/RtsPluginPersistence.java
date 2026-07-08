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
 * Plugin 列表的序列化——数据存储于 {@link com.rtsbuilding.rtsbuilding.server.data.DataCluster}，
 * 通过 {@link PlayerComponents#PLUGINS} 组件读写，替代旧的 {@code player.getPersistentData()} 方式。
 *
 * <p>仅负责序列化/反序列化，不判断安装是否合法，不修改玩家背包。
 */
final class RtsPluginPersistence {
    private static final String NBT_ROOT = "rtsbuilding_plugins";
    private static final String NBT_INSTALLED = "installed";
    private static final String NBT_PLUGIN_ID = "plugin_id";
    private static final String NBT_STACK = "stack";
    private static final String NBT_INSTALLED_GAME_TIME = "installed_game_time";

    private RtsPluginPersistence() {
    }

    static List<RtsInstalledPlugin> load(ServerPlayer player) {
        CompoundTag root = player.getPersistentData().getCompound(NBT_ROOT);
        ListTag list = root.getList(NBT_INSTALLED, Tag.TAG_COMPOUND);
        List<RtsInstalledPlugin> installed = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag tag = list.getCompound(i);
            ResourceLocation pluginId = ResourceLocation.tryParse(tag.getString(NBT_PLUGIN_ID));
            if (pluginId == null) continue;

            ItemStack stack = ItemStack.of(tag.getCompound(NBT_STACK));
            if (stack.isEmpty()) {
                RtsPluginDefinition definition = RtsPluginRegistry.byId(pluginId);
                if (definition == null) continue;
                stack = new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(definition.itemId()));
            }
            installed.add(new RtsInstalledPlugin(pluginId, stack, tag.getLong(NBT_INSTALLED_GAME_TIME)));
        }
        return installed;
    }

    static void save(ServerPlayer player, List<RtsInstalledPlugin> installed) {
        CompoundTag root = new CompoundTag();
        ListTag list = new ListTag();
        for (RtsInstalledPlugin plugin : installed) {
            if (plugin == null || plugin.pluginId() == null || plugin.stack().isEmpty()) continue;

            CompoundTag tag = new CompoundTag();
            tag.putString(NBT_PLUGIN_ID, plugin.pluginId().toString());
            tag.put(NBT_STACK, plugin.stack().copyWithCount(1).save(new CompoundTag()));
            tag.putLong(NBT_INSTALLED_GAME_TIME, plugin.installedGameTime());
            list.add(tag);
        }
        root.put(NBT_INSTALLED, list);
        player.getPersistentData().put(NBT_ROOT, root);
    }
}
