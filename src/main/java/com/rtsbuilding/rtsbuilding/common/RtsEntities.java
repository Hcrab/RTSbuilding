package com.rtsbuilding.rtsbuilding.common;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.common.entity.RtsCameraEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 实体注册器 —— RTSbuilding 的所有实体在此集中注册。
 * <p>
 * 提供 {@link #simpleEntity(String, EntityType.EntityFactory, MobCategory, float, float, int, int)}
 * 和 {@link #registerEntity(String, java.util.function.Supplier)} 两种工厂方法，
 * 分别用于注册简单实体和高度自定义的实体。
 */
public final class RtsEntities {

    /** 统一的实体注册表实例 */
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, RtsbuildingMod.MODID);

    // ============================================================
    //  实体定义
    // ============================================================

    /** RTS 相机实体 —— 用于 RTS 模式的俯视视角控制 */
    public static final DeferredHolder<EntityType<?>, EntityType<RtsCameraEntity>> RTS_CAMERA_ENTITY =
            ENTITY_TYPES.register("rts_camera",
                    () -> EntityType.Builder.of(RtsCameraEntity::new, MobCategory.MISC)
                            .sized(0.1F, 0.1F)
                            .clientTrackingRange(128)
                            .updateInterval(1)
                            .noSave()
                            .noSummon()
                            .build(ResourceKey.create(
                                    Registries.ENTITY_TYPE,
                                    Identifier.fromNamespaceAndPath(RtsbuildingMod.MODID, "rts_camera"))));

    // ============================================================
    //  工厂方法
    // ============================================================

    /**
     * 注册一个使用默认 {@link EntityType.Builder} 配置的简单实体。
     *
     * @param id              实体的注册名
     * @param factory         实体实例的工厂函数
     * @param category        实体的 {@link MobCategory}（如 MISC、CREATURE 等）
     * @param width           碰撞箱宽度
     * @param height          碰撞箱高度
     * @param trackingRange   实体追踪范围（客户端渲染距离），单位：格
     * @param updateInterval  实体更新间隔，单位：tick（20 tick = 1秒）
     * @return 实体的 {@link DeferredHolder}
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
                .build(ResourceKey.create(
                        Registries.ENTITY_TYPE,
                        Identifier.fromNamespaceAndPath(RtsbuildingMod.MODID, id))));
    }

    /**
     * 注册任意自定义 {@link EntityType} 的实体。
     *
     * @param id      实体的注册名
     * @param factory 创建 {@link EntityType} 实例的工厂函数
     * @return 实体的 {@link DeferredHolder}
     */
    @SuppressWarnings("unchecked")
    public static <T extends net.minecraft.world.entity.Entity> DeferredHolder<EntityType<?>, EntityType<T>> registerEntity(
            String id,
            java.util.function.Supplier<EntityType<T>> factory) {
        return (DeferredHolder<EntityType<?>, EntityType<T>>) (DeferredHolder<?, ?>) ENTITY_TYPES.register(id, factory);
    }

    // ============================================================
    //  注册入口
    // ============================================================

    /**
     * 在模组总线上注册所有实体。
     *
     * @param modEventBus 模组事件总线
     */
    public static void register(IEventBus modEventBus) {
        ENTITY_TYPES.register(modEventBus);
    }

    private RtsEntities() {
    }
}
