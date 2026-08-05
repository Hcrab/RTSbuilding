package com.rtsbuilding.rtsbuilding.common;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.common.entity.RtsCameraEntity;
import net.minecraft.entity.Entity;
import cpw.mods.fml.common.registry.EntityRegistry;

/**
 * 1.12.2 实体注册边界。旧 Forge 没有 EntityType 注册表，因此显式保存实体类和追踪参数，
 * 并在 preInit 使用 {@link EntityRegistry#registerModEntity} 注册。
 */
public final class RtsEntities {
    public static final Registration<RtsCameraEntity> RTS_CAMERA_ENTITY = new Registration<>(
            "rts_camera", RtsCameraEntity.class, 0, 128, 1, false);

    private static boolean registered;

    public static synchronized void register(Object modInstance) {
        if (registered) return;
        register(RTS_CAMERA_ENTITY, modInstance);
        registered = true;
    }

    private static <T extends Entity> void register(Registration<T> registration, Object modInstance) {
        EntityRegistry.registerModEntity(
                registration.entityClass,
                registration.id,
                registration.numericId,
                modInstance,
                registration.trackingRange,
                registration.updateFrequency,
                registration.velocityUpdates);
    }

    public static final class Registration<T extends Entity> {
        private final String id;
        private final Class<T> entityClass;
        private final int numericId;
        private final int trackingRange;
        private final int updateFrequency;
        private final boolean velocityUpdates;

        private Registration(String id, Class<T> entityClass, int numericId, int trackingRange,
                int updateFrequency, boolean velocityUpdates) {
            this.id = id;
            this.entityClass = entityClass;
            this.numericId = numericId;
            this.trackingRange = trackingRange;
            this.updateFrequency = updateFrequency;
            this.velocityUpdates = velocityUpdates;
        }

        /** 保留现有调用点的 get() 形状；调用者可进一步读取实体类。 */
        public Registration<T> get() { return this; }
        public String id() { return id; }
        public Class<T> entityClass() { return entityClass; }
    }

    private RtsEntities() {
    }
}
