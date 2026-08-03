package com.rtsbuilding.rtsbuilding.common.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;

import java.util.UUID;

/**
 * RTS 镜头使用的无碰撞服务端实体。
 *
 * <p>实体只保存镜头所有者并承载原版相机同步，不负责输入、平滑或会话生命周期。
 */
public class RtsCameraEntity extends Entity {
    private UUID ownerUuid;

    public RtsCameraEntity(EntityType<? extends RtsCameraEntity> entityType, Level level) {
        super(entityType, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    @Override
    protected void defineSynchedData() {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag compoundTag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag compoundTag) {
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        @SuppressWarnings("unchecked")
        Packet<ClientGamePacketListener> packet =
                (Packet<ClientGamePacketListener>) NetworkHooks.getEntitySpawningPacket(this);
        return packet;
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
        super.tick();
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public void snapTo(double x, double y, double z, float yaw, float pitch) {
        this.setPos(x, y, z);
        this.setYRot(yaw);
        this.setXRot(pitch);
        this.setYHeadRot(yaw);
        this.setYBodyRot(yaw);
        this.setOldPosAndRot();
        this.yRotO = yaw;
        this.xRotO = pitch;
    }
}
