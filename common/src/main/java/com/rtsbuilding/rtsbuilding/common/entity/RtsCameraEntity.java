package com.rtsbuilding.rtsbuilding.common.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

import java.util.UUID;

public class RtsCameraEntity extends Entity {
    private UUID ownerUuid;

    // Orbit mode interpolated params（由 CameraEntitySync 设置，getEyePosition 读取）
    private double orbitPrevAngle, orbitPrevPitch, orbitPrevRadius;
    private double orbitCurrAngle, orbitCurrPitch, orbitCurrRadius;
    private double orbitTargetX, orbitTargetY, orbitTargetZ;
    private boolean orbitActive;

    public RtsCameraEntity(EntityType<? extends RtsCameraEntity> entityType, Level level) {
        super(entityType, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag compoundTag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag compoundTag) {
    }

    public UUID getOwnerUuid() {
        return this.ownerUuid;
    }

    public void setOwnerUuid(UUID ownerUuid) {
        this.ownerUuid = ownerUuid;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void tick() {
        // 摄像机实体由 snapTo() 驱动，不做碰撞检测 / 区块更新 / 传送门检测
        // 不调 super.tick() 避免无意义的实体处理开销
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    /**
     * 快速设置摄像机实体的位置和旋转——使用 {@link #setPosRaw} 跳过
     * {@link #setPos(double, double, double)} 中的区块位置重算逻辑，
     * 因为摄像机实体不参与世界交互，无需更新区块引用。
     */
    public void snapTo(double x, double y, double z, float yaw, float pitch) {
        this.setPosRaw(x, y, z);
        this.setYRot(yaw);
        this.setXRot(pitch);
        this.setYHeadRot(yaw);
        this.setYBodyRot(yaw);
        this.setOldPosAndRot();
        this.yRotO = yaw;
        this.xRotO = pitch;
    }

    /**
     * 带 partialTick 插值的快照——设置 old 值为上一 tick 姿态、current 值为当前 tick 姿态，
     * 使 {@link net.minecraft.client.Camera#setup} 中的 {@code lerp(partialTick, old, current)}
     * 能产生平滑帧间插值效果，类似原版玩家视角的工作方式。
     *
     * @param prevX,prevY,prevZ 上一 tick 的位置
     * @param prevYaw,prevPitch 上一 tick 的角度
     * @param currX,currY,currZ 当前 tick 的位置
     * @param currYaw,currPitch 当前 tick 的角度
     */
    public void snapInterpolated(double prevX, double prevY, double prevZ, float prevYaw, float prevPitch,
                                  double currX, double currY, double currZ, float currYaw, float currPitch) {
        // 设置 old 值 = 上一 tick 姿态（供 lerp 起点）
        this.xo = prevX;
        this.yo = prevY;
        this.zo = prevZ;
        this.yRotO = prevYaw;
        this.xRotO = prevPitch;
        // 设置 current 值 = 当前 tick 姿态（供 lerp 终点）
        this.setPosRaw(currX, currY, currZ);
        this.setYRot(currYaw);
        this.setXRot(currPitch);
        this.setYHeadRot(currYaw);
        this.setYBodyRot(currYaw);
    }

    /**
     * 设置轨道模式插值参数——由 {@link CameraModule} 在帧率级更新时使用。
     */
    public void setOrbitInterp(double prevAngle, double prevPitch, double prevRadius,
                                double currAngle, double currPitch, double currRadius,
                                double targetX, double targetY, double targetZ, boolean active) {
        this.orbitPrevAngle = prevAngle;
        this.orbitPrevPitch = prevPitch;
        this.orbitPrevRadius = prevRadius;
        this.orbitCurrAngle = currAngle;
        this.orbitCurrPitch = currPitch;
        this.orbitCurrRadius = currRadius;
        this.orbitTargetX = targetX;
        this.orbitTargetY = targetY;
        this.orbitTargetZ = targetZ;
        this.orbitActive = active;
    }
}
