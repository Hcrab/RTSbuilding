package com.rtsbuilding.rtsbuilding.client.kernel;

import com.rtsbuilding.rtsbuilding.client.domain.module.ModuleState;
import net.minecraft.client.Minecraft;

public interface FeatureModule {

    
    default void init(RtsClientKernel kernel) {}

    
    default void tickPre(long epochMs, int tickIndex) {}

    
    default void tick(long epochMs, int tickIndex) {}

    
    default void onStateChange(ModuleState newState) {}

    
    default void onSessionEvent(StateEvent event) {}

    
    default Minecraft mc() {
        return Minecraft.getInstance();
    }

    
    String moduleId();
}
