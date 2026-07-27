package com.rtsbuilding.rtsbuilding.server.plugin;

import com.rtsbuilding.rtsbuilding.server.data.PlayerComponents;
import com.rtsbuilding.rtsbuilding.server.data.SaveScheduler;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Plugin 列表的序列化——数据存储于 {@link com.rtsbuilding.rtsbuilding.server.data.DataCluster}，
 * 通过 {@link PlayerComponents#PLUGINS} 组件读写，替代旧的 {@code player.getPersistentData()} 方式。
 *
 * <p>仅负责序列化/反序列化，不判断安装是否合法，不修改玩家背包。
 */
final class RtsPluginPersistence {
    private static final String NBT_INSTALLED = "installed";
    private static final String NBT_PLUGIN_ID = "plugin_id";
    private static final String NBT_STACK = "stack";
    private static final String NBT_INSTALLED_GAME_TIME = "installed_game_time";

    private RtsPluginPersistence() {
    }

    static List<RtsInstalledPlugin> load(EntityPlayerMP player) {
        NBTTagCompound root = SaveScheduler.INSTANCE.player(player).get(PlayerComponents.PLUGINS);
        NBTTagList list = root.getTagList(NBT_INSTALLED, 10);
        List<RtsInstalledPlugin> installed = new ArrayList<>();
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound tag = list.getCompoundTagAt(i);
            ResourceLocation pluginId = parseId(tag.getString(NBT_PLUGIN_ID));
            if (pluginId == null) continue;

            ItemStack stack = new ItemStack(tag.getCompoundTag(NBT_STACK));
            if (stack.isEmpty()) {
                RtsPluginDefinition definition = RtsPluginRegistry.byId(pluginId);
                if (definition == null) continue;
                Item item = Item.REGISTRY.getObject(definition.itemId());
                if (item == null) continue;
                stack = new ItemStack(item);
            }
            installed.add(new RtsInstalledPlugin(pluginId, stack, tag.getLong(NBT_INSTALLED_GAME_TIME)));
        }
        return installed;
    }

    static void save(EntityPlayerMP player, List<RtsInstalledPlugin> installed) {
        NBTTagCompound root = new NBTTagCompound();
        NBTTagList list = new NBTTagList();
        for (RtsInstalledPlugin plugin : installed) {
            if (plugin == null || plugin.pluginId() == null || plugin.stack().isEmpty()) continue;

            NBTTagCompound tag = new NBTTagCompound();
            tag.setString(NBT_PLUGIN_ID, plugin.pluginId().toString());
            tag.setTag(NBT_STACK, RtsInstalledPlugin.copyOne(plugin.stack()).writeToNBT(new NBTTagCompound()));
            tag.setLong(NBT_INSTALLED_GAME_TIME, plugin.installedGameTime());
            list.appendTag(tag);
        }
        root.setTag(NBT_INSTALLED, list);
        SaveScheduler.INSTANCE.player(player).set(PlayerComponents.PLUGINS, root);
    }

    private static ResourceLocation parseId(String value) {
        try {
            return value == null || value.isEmpty() ? null : new ResourceLocation(value);
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }
}
