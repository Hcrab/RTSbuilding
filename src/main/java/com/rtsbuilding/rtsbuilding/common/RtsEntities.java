package com.rtsbuilding.rtsbuilding.common;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.common.entity.RtsCameraEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Supplier;

/**
 * RTSBuilding 的实体注册所有者。
 *
 * <p>业务代码只依赖这里公开的实体句柄；Forge 注册表差异被限制在本类中。
 */
public final class RtsEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, RtsbuildingMod.MODID);

    public static final RegistryObject<EntityType<RtsCameraEntity>> RTS_CAMERA_ENTITY =
            ENTITY_TYPES.register("rts_camera",
                    () -> EntityType.Builder.<RtsCameraEntity>of(RtsCameraEntity::new, MobCategory.MISC)
                            .sized(0.1F, 0.1F)
                            .clientTrackingRange(128)
                            .updateInterval(1)
                            .noSave()
                            .noSummon()
                            .build(RtsbuildingMod.MODID + ":rts_camera"));

    private RtsEntities() {
    }

    public static <T extends Entity> RegistryObject<EntityType<T>> simpleEntity(
            String id,
            EntityType.EntityFactory<T> factory,
            MobCategory category,
            float width,
            float height,
            int trackingRange,
            int updateInterval) {
        return ENTITY_TYPES.register(id, () -> EntityType.Builder.of(factory, category)
                .sized(width, height)
                .clientTrackingRange(trackingRange)
                .updateInterval(updateInterval)
                .build(RtsbuildingMod.MODID + ":" + id));
    }

    public static <T extends Entity> RegistryObject<EntityType<T>> registerEntity(
            String id, Supplier<EntityType<T>> factory) {
        return ENTITY_TYPES.register(id, factory);
    }

    public static void register(IEventBus modEventBus) {
        ENTITY_TYPES.register(modEventBus);
    }
}
