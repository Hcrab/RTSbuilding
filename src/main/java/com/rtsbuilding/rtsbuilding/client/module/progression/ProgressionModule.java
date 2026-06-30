package com.rtsbuilding.rtsbuilding.client.module.progression;

import com.rtsbuilding.rtsbuilding.client.kernel.FeatureModule;
import com.rtsbuilding.rtsbuilding.network.progression.S2CRtsProgressionStatePayload;
import net.minecraft.core.BlockPos;

/**
 * 升级进度模块——镜像服务端的生存模式升级状态。
 * 默认 IDLE，仅在启用生存升级时升为 WARM。
 */
public final class ProgressionModule implements FeatureModule {

    private boolean enabled;
    private boolean homeSet;
    private BlockPos homePos = BlockPos.ZERO;
    private String homeDimension = "";
    private long homeCooldownTicks;
    private int radiusBlocks = 48;
    private int fluidCapacity = 100;
    private int ultimineLimit = 256;
    private boolean bypassHomeRadius;

    @Override
    public String moduleId() {
        return "progression";
    }

    public void applyProgressionState(S2CRtsProgressionStatePayload payload, Runnable onLocksCleared) {
        this.enabled = payload.enabled();
        this.homeSet = payload.homeSet();
        this.homePos = payload.homePos();
        this.homeDimension = payload.homeDimension() == null ? "" : payload.homeDimension();
        this.homeCooldownTicks = payload.homeCooldownTicks();
        this.radiusBlocks = payload.radiusBlocks();
        this.fluidCapacity = payload.fluidCapacityBuckets();
        this.ultimineLimit = payload.ultimineLimit();
        this.bypassHomeRadius = payload.bypassHomeRadius();
        if (!this.enabled && onLocksCleared != null) onLocksCleared.run();
    }

    public boolean isEnabled() { return enabled; }
    public boolean isHomeSet() { return homeSet; }
    public BlockPos getHomePos() { return homePos; }
    public int getRadiusBlocks() { return radiusBlocks; }
    public int getUltimineLimit() { return ultimineLimit; }
}
