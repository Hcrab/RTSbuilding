package com.rtsbuilding.rtsbuilding.common.entity;

import com.rtsbuilding.rtsbuilding.common.RtsEntities;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

import java.util.UUID;

/** 服务端权威的无碰撞 RTS 相机实体；它是会话对象，不写入世界存档。 */
public class RtsCameraEntity extends EntityLivingBase {
    private static final ItemStack[] EMPTY_EQUIPMENT = new ItemStack[5];
    private UUID ownerUuid;

    /** EntityRegistry 在 1.12.2 中要求实体提供 World 构造器。 */
    public RtsCameraEntity(World world) {
        super(world);
        setSize(0.1F, 0.1F);
        noClip = true;
        setHealth(1.0F);
    }

    /** 迁移期兼容现有工厂调用形状；注册信息由 Forge 管理，构造时无需消费。 */
    public RtsCameraEntity(RtsEntities.Registration<RtsCameraEntity> ignored, World world) {
        this(world);
    }

    @Override
    protected void entityInit() {
        // 1.7.10 的 EntityLivingBase 构造器会通过虚调用进入这里；必须先注册生命值等
        // 原版 DataWatcher 项。空实现会让随后 setHealth() 更新不存在的索引并直接崩服。
        super.entityInit();
        // 所有权只由服务端会话管理；客户端无需同步 DataParameter。
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound compound) {
        // 相机不会进入存档；保留空实现满足 Entity 契约。
    }

    @Override
    public void writeEntityToNBT(NBTTagCompound compound) {
        // 相机不会进入存档；所有权由玩家会话重建。
    }

    @Override
    public boolean writeToNBTOptional(NBTTagCompound compound) {
        return false;
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public void setOwnerUuid(UUID ownerUuid) {
        this.ownerUuid = ownerUuid;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean canBePushed() {
        return false;
    }

    @Override
    public boolean isPushedByWater() {
        return false;
    }

    @Override public ItemStack getHeldItem() { return null; }
    @Override public ItemStack getEquipmentInSlot(int slot) { return null; }
    @Override public void setCurrentItemOrArmor(int slot, ItemStack stack) { }
    @Override public ItemStack[] getLastActiveItems() { return EMPTY_EQUIPMENT; }
    @Override public float getEyeHeight() { return 0.0F; }

    @Override
    public void onUpdate() {
        super.onUpdate();
        noClip = true;
        motionX = 0.0D;
        motionY = 0.0D;
        motionZ = 0.0D;
    }

    public void snapTo(double x, double y, double z, float yaw, float pitch) {
        setPosition(x, y, z);
        rotationYaw = yaw;
        rotationPitch = pitch;
        prevRotationYaw = yaw;
        prevRotationPitch = pitch;
        prevPosX = x;
        prevPosY = y;
        prevPosZ = z;
        lastTickPosX = x;
        lastTickPosY = y;
        lastTickPosZ = z;
    }
}
