package com.rtsbuilding.rtsbuilding.mixin;

import com.rtsbuilding.rtsbuilding.platform.data.RtsLegacyPersistentDataAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 在 Fabric 下只保留 NeoForge 写入实体的旧 {@code NeoForgeData}。
 *
 * <p>该 Mixin 不把它当作新的持久化 API，也不允许新业务继续写任意实体数据；职责仅是让
 * 1.21.1 NeoForge 世界切到 Fabric 后仍能完成一次性技能树迁移，并在迁移标记写回后继续
 * 保持跨加载器可读。
 */
@Mixin(Entity.class)
public abstract class EntityLegacyPersistentDataMixin implements RtsLegacyPersistentDataAccess {
    @Unique
    private static final String RTSBUILDING_NEOFORGE_DATA_KEY = "NeoForgeData";

    @Unique
    private CompoundTag rtsbuilding$legacyPersistentData;

    @Inject(method = "load", at = @At("TAIL"))
    private void rtsbuilding$loadLegacyPersistentData(CompoundTag tag, CallbackInfo callback) {
        if (tag.contains(RTSBUILDING_NEOFORGE_DATA_KEY, Tag.TAG_COMPOUND)) {
            rtsbuilding$legacyPersistentData = tag.getCompound(RTSBUILDING_NEOFORGE_DATA_KEY).copy();
        }
    }

    @Inject(method = "saveWithoutId", at = @At("RETURN"))
    private void rtsbuilding$saveLegacyPersistentData(
            CompoundTag tag, CallbackInfoReturnable<CompoundTag> callback) {
        if (rtsbuilding$legacyPersistentData != null && !rtsbuilding$legacyPersistentData.isEmpty()) {
            callback.getReturnValue().put(
                    RTSBUILDING_NEOFORGE_DATA_KEY, rtsbuilding$legacyPersistentData.copy());
        }
    }

    @Override
    public CompoundTag rtsbuilding$getLegacyPersistentData() {
        if (rtsbuilding$legacyPersistentData == null) {
            rtsbuilding$legacyPersistentData = new CompoundTag();
        }
        return rtsbuilding$legacyPersistentData;
    }
}
