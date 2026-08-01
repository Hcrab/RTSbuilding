package com.rtsbuilding.rtsbuilding.fabric;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.fabricmc.api.ModInitializer;

/** Fabric 公共入口；实际初始化顺序由公共生命周期所有者维护。 */
public final class RtsbuildingFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        RtsbuildingMod.initialize();
    }
}
