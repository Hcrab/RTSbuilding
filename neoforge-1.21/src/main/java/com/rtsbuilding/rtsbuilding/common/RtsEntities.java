package com.rtsbuilding.rtsbuilding.common;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.common.entity.RtsCameraEntity;
import com.rtsbuilding.rtsbuilding.platform.Platform;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Entity registry — all RTSbuilding entities are registered centrally here.
 * <p>
 * Provides two factory methods: {@link #simpleEntity(String, EntityType.EntityFactory, MobCategory, float, float, int, int)}
 * for simple entities and {@link #registerEntity(String, java.util.function.Supplier)} for highly customized entities.
 */
public final class RtsEntities {

    /** Unified entity registry instance */
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = Platform.entityRegister();

    // ============================================================
    //  Entity definitions
    // ============================================================

    /** RTS camera entity — used for top-down view control in RTS mode */
    public static final DeferredHolder<EntityType<?>, EntityType<RtsCameraEntity>> RTS_CAMERA_ENTITY =
            ENTITY_TYPES.register("rts_camera",
                    () -> EntityType.Builder.of(RtsCameraEntity::new, MobCategory.MISC)
                            .sized(0.1F, 0.1F)
                            .clientTrackingRange(128)
                            .updateInterval(1)
                            .noSave()
                            .noSummon()
                            .build(ResourceLocation.fromNamespaceAndPath(RtsbuildingMod.MODID, "rts_camera").toString()));

    // ============================================================
    //  Factory methods
    // ============================================================

    /**
     * Register a simple entity using the default {@link EntityType.Builder} configuration.
     *
     * @param id              The registry name of the entity
     * @param factory         Factory function for creating entity instances
     * @param category        The entity's {@link MobCategory} (e.g., MISC, CREATURE, etc.)
     * @param width           Collision box width
     * @param height          Collision box height
     * @param trackingRange   Entity tracking range (client render distance), in blocks
     * @param updateInterval  Entity update interval, in ticks (20 ticks = 1 second)
     * @return The entity's {@link DeferredHolder}
     */
    public static <T extends net.minecraft.world.entity.Entity> DeferredHolder<EntityType<?>, EntityType<T>> simpleEntity(
            String id,
            EntityType.EntityFactory<T> factory,
            MobCategory category,
            float width, float height,
            int trackingRange, int updateInterval) {
        return ENTITY_TYPES.register(id, () -> EntityType.Builder.of(factory, category)
                .sized(width, height)
                .clientTrackingRange(trackingRange)
                .updateInterval(updateInterval)
                .build(ResourceLocation.fromNamespaceAndPath(RtsbuildingMod.MODID, id).toString()));
    }

    /**
     * Register an entity with any custom {@link EntityType}.
     *
     * @param id      The registry name of the entity
     * @param factory Factory function for creating the {@link EntityType} instance
     * @return The entity's {@link DeferredHolder}
     */
    public static <T extends net.minecraft.world.entity.Entity> DeferredHolder<EntityType<?>, EntityType<T>> registerEntity(
            String id,
            java.util.function.Supplier<EntityType<T>> factory) {
        return (DeferredHolder<EntityType<?>, EntityType<T>>) ENTITY_TYPES.register(id, factory);
    }

    // ============================================================
    //  Registration entry point
    // ============================================================

    /**
     * Register all entities on the mod event bus.
     *
     * @param modEventBus The mod event bus
     */
    public static void register(IEventBus modEventBus) {
        ENTITY_TYPES.register(modEventBus);
    }

    private RtsEntities() {
    }
}
