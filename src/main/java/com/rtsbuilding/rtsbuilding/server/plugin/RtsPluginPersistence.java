package com.rtsbuilding.rtsbuilding.server.plugin;

import com.rtsbuilding.rtsbuilding.server.data.PlayerComponents;
import com.rtsbuilding.rtsbuilding.server.data.SaveScheduler;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Plugin 鍒楄〃鐨勫簭鍒楀寲鈥斺€旀暟鎹瓨鍌ㄤ簬 {@link com.rtsbuilding.rtsbuilding.server.data.DataCluster}锛?
 * 閫氳繃 {@link PlayerComponents#PLUGINS} 缁勪欢璇诲啓锛屾浛浠ｆ棫鐨?{@code player.getPersistentData()} 鏂瑰紡銆?
 *
 * <p>浠呰礋璐ｅ簭鍒楀寲/鍙嶅簭鍒楀寲锛屼笉鍒ゆ柇瀹夎鏄惁鍚堟硶锛屼笉淇敼鐜╁鑳屽寘銆?
 */
final class RtsPluginPersistence {
    private static final String NBT_INSTALLED = "installed";
    private static final String NBT_PLUGIN_ID = "plugin_id";
    private static final String NBT_STACK = "stack";
    private static final String NBT_INSTALLED_GAME_TIME = "installed_game_time";

    private RtsPluginPersistence() {
    }

    static List<RtsInstalledPlugin> load(ServerPlayer player) {
        CompoundTag root = SaveScheduler.INSTANCE.player(player).get(PlayerComponents.PLUGINS);
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
        SaveScheduler.INSTANCE.player(player).set(PlayerComponents.PLUGINS, root);
    }
}
