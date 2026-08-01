package com.rtsbuilding.rtsbuilding.platform.data;

import net.minecraft.nbt.CompoundTag;

/**
 * 暴露从 NeoForge 玩家存档中保留下来的 {@code NeoForgeData} 根标签。
 *
 * <p>Fabric 本身没有 {@code Entity#getPersistentData()}，但玩家可能直接携带 NeoForge
 * 主线存档进入 Fabric。这个窄接口只服务旧技能树迁移，不把加载器私有 NBT 重新扩散到业务层。
 */
public interface RtsLegacyPersistentDataAccess {
    CompoundTag rtsbuilding$getLegacyPersistentData();
}
