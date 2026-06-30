package com.rtsbuilding.rtsbuilding.client.module.blueprint;

import com.rtsbuilding.rtsbuilding.client.kernel.FeatureModule;

/**
 * 蓝图模块——管理蓝图捕获、保存、放置。
 * 默认 IDLE，仅在蓝图面板打开时升为 WARM。
 */
public final class BlueprintModule implements FeatureModule {

    @Override
    public String moduleId() {
        return "blueprint";
    }
}
