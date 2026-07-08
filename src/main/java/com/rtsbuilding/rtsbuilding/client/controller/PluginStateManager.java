package com.rtsbuilding.rtsbuilding.client.controller;

import com.rtsbuilding.rtsbuilding.network.plugin.S2CRtsPluginStatePayload;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
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
            this.installedPlugins.add(new InstalledPluginView(
                    safe(payload.pluginIds().get(i)),
                    safe(payload.families().get(i)),
                    Math.max(0, payload.radiusBlocks().get(i)),
                    Boolean.TRUE.equals(payload.fieldDeployment().get(i)),
                    Boolean.TRUE.equals(payload.personal().get(i)),
                    safe(payload.ownerNames().get(i)),
                    stack == null ? ItemStack.EMPTY : stack.copyWithCount(1)));
        }
    }

    public List<InstalledPluginView> installedPlugins() {
        return List.copyOf(this.installedPlugins);
    }

    public String teamName() {
        return this.teamName;
    }

    public boolean hasPlugin(String pluginId) {
        if (pluginId == null || pluginId.isBlank()) {
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

    public record InstalledPluginView(
            String pluginId,
            String family,
            int radiusBlocks,
            boolean fieldDeployment,
            boolean personal,
            String ownerName,
            ItemStack stack) {
    }
}
