package com.rtsbuilding.rtsbuilding.client.module.blueprint;

import com.rtsbuilding.rtsbuilding.client.kernel.FeatureModule;
import com.rtsbuilding.rtsbuilding.client.kernel.ModuleState;

/**
 * 蓝图模块——管理蓝图捕获、保存、放置。
 * 默认 IDLE，仅在蓝图面板打开时升为 WARM。
 */
public final class BlueprintModule implements FeatureModule {

    private ModuleState state = ModuleState.IDLE;

    @Override
    public String moduleId() {
        return "blueprint";
    }

    @Override
    public ModuleState state() {
        return this.state;
    }

    @Override
    public void onStateChange(ModuleState newState) {
        this.state = newState;
    }
}
