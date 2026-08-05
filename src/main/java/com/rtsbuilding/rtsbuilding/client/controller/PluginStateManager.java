package com.rtsbuilding.rtsbuilding.client.controller;

import com.rtsbuilding.rtsbuilding.network.plugin.S2CRtsPluginStatePayload;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Client-side mirror of the server-authoritative RTS plugin list.
 *
 * <p>This class exists only so screens can render installed plugin state. It
 * deliberately does not decide whether a player may build, mine, store, or use
 * blueprints; those decisions remain in the server plugin service.
 */
public final class PluginStateManager {
    private final List<InstalledPluginView> installedPlugins = new ArrayList<>();
    private String teamName = "";

    public void applyPluginState(S2CRtsPluginStatePayload payload) {
        this.installedPlugins.clear();
        this.teamName = "";
        if (payload == null) {
            return;
        }
        this.teamName = safe(payload.teamName());
        int size = Math.min(payload.pluginIds().size(),
                Math.min(payload.families().size(),
                        Math.min(payload.radiusBlocks().size(),
                                Math.min(payload.fieldDeployment().size(),
                                        Math.min(payload.personal().size(),
                                                Math.min(payload.ownerNames().size(), payload.stacks().size()))))));
        for (int i = 0; i < size; i++) {
            ItemStack stack = payload.stacks().get(i);
            ItemStack preview = stack == null ? null : stack.copy();
            if (!com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(preview)) {
                preview.stackSize = 1;
            }
            this.installedPlugins.add(new InstalledPluginView(
                    safe(payload.pluginIds().get(i)),
                    safe(payload.families().get(i)),
                    Math.max(0, payload.radiusBlocks().get(i)),
                    Boolean.TRUE.equals(payload.fieldDeployment().get(i)),
                    Boolean.TRUE.equals(payload.personal().get(i)),
                    safe(payload.ownerNames().get(i)),
                    preview));
        }
    }

    public List<InstalledPluginView> installedPlugins() {
        return Collections.unmodifiableList(new ArrayList<InstalledPluginView>(this.installedPlugins));
    }

    public String teamName() {
        return this.teamName;
    }

    public boolean hasPlugin(String pluginId) {
        if (pluginId == null || pluginId.trim().isEmpty()) {
            return false;
        }
        for (InstalledPluginView plugin : this.installedPlugins) {
            if (pluginId.equals(plugin.pluginId())) {
                return true;
            }
        }
        return false;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    /**
     * 供客户端界面读取的插件快照；这里只保存服务端同步结果，不承担权限判断。
     */
    public static final class InstalledPluginView {
        private final String pluginId;
        private final String family;
        private final int radiusBlocks;
        private final boolean fieldDeployment;
        private final boolean personal;
        private final String ownerName;
        private final ItemStack stack;

        InstalledPluginView(String pluginId, String family, int radiusBlocks, boolean fieldDeployment,
                            boolean personal, String ownerName, ItemStack stack) {
            this.pluginId = pluginId;
            this.family = family;
            this.radiusBlocks = radiusBlocks;
            this.fieldDeployment = fieldDeployment;
            this.personal = personal;
            this.ownerName = ownerName;
            this.stack = stack;
        }

        public String pluginId() { return this.pluginId; }
        public String family() { return this.family; }
        public int radiusBlocks() { return this.radiusBlocks; }
        public boolean fieldDeployment() { return this.fieldDeployment; }
        public boolean personal() { return this.personal; }
        public String ownerName() { return this.ownerName; }
        public ItemStack stack() { return this.stack; }
    }
}
