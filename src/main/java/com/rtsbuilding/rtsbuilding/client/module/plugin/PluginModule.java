package com.rtsbuilding.rtsbuilding.client.module.plugin;

import com.rtsbuilding.rtsbuilding.client.kernel.FeatureModule;
import com.rtsbuilding.rtsbuilding.client.kernel.ModuleState;
import com.rtsbuilding.rtsbuilding.client.network.RtsClientPacketGateway;
import com.rtsbuilding.rtsbuilding.network.plugin.S2CRtsPluginStatePayload;

import java.util.ArrayList;
import java.util.List;

/**
 * 插件模块——客户端插件状态镜像。
 * 默认 IDLE，仅在插件管理界面打开时升为 WARM。
 */
public final class PluginModule implements FeatureModule {

    private ModuleState state = ModuleState.IDLE;
    private final List<PluginView> plugins = new ArrayList<>();

    @Override
    public String moduleId() {
        return "plugin";
    }

    @Override
    public ModuleState state() {
        return this.state;
    }

    @Override
    public void onStateChange(ModuleState newState) {
        this.state = newState;
    }

    public void applyPluginState(S2CRtsPluginStatePayload payload) {
        this.plugins.clear();
        if (payload == null) return;
        int size = Math.min(payload.pluginIds().size(), payload.stacks().size());
        for (int i = 0; i < size; i++) {
            plugins.add(new PluginView(
                    safe(payload.pluginIds().get(i)),
                    safe(payload.families().get(i)),
                    Math.max(0, payload.radiusBlocks().get(i)),
                    Boolean.TRUE.equals(payload.fieldDeployment().get(i))));
        }
    }

    public List<PluginView> getPlugins() {
        return List.copyOf(this.plugins);
    }

    public boolean hasPlugin(String pluginId) {
        if (pluginId == null) return false;
        return plugins.stream().anyMatch(p -> pluginId.equals(p.pluginId()));
    }

    public void requestPlugins() {
        RtsClientPacketGateway.sendRequestPlugins();
    }

    private static String safe(String v) {
        return v == null ? "" : v;
    }

    public record PluginView(String pluginId, String family, int radiusBlocks, boolean fieldDeployment) {}
}
