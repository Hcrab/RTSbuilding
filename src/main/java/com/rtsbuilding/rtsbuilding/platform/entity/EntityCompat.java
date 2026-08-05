package com.rtsbuilding.rtsbuilding.platform.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import com.google.common.base.Predicate;
import com.rtsbuilding.rtsbuilding.platform.math.AxisAlignedBB;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 1.7.10 实体生成、掉落物寿命和仅渲染实体状态的兼容入口。 */
public final class EntityCompat {
    private EntityCompat() {}

    public static boolean spawn(World world, Entity entity) {
        return world != null && entity != null && world.spawnEntityInWorld(entity);
    }

    public static void setPickupDelay(EntityItem entity, int ticks) {
        if (entity != null) entity.delayBeforeCanPickup = Math.max(0, ticks);
    }

    public static void setNoDespawn(EntityItem entity) {
        if (entity == null) return;
        entity.age = 0;
        entity.lifespan = Integer.MAX_VALUE;
    }

    /** 1.7.10 没有 noGravity 标志；临时预览实体不会加入世界，只需冻结运动。 */
    public static void freezePreview(Entity entity) {
        if (entity == null) return;
        entity.motionX = entity.motionY = entity.motionZ = 0.0D;
        entity.noClip = true;
    }

    public static Entity findByUuid(WorldServer world, UUID entityId) {
        if (world == null || entityId == null) return null;
        for (Object value : world.loadedEntityList) {
            if (value instanceof Entity && entityId.equals(((Entity) value).getUniqueID())) {
                return (Entity) value;
            }
        }
        return null;
    }

    public static List<Entity> getEntitiesExcluding(
            World world, Entity excluded, AxisAlignedBB box, Predicate<Entity> predicate) {
        ArrayList<Entity> result = new ArrayList<Entity>();
        if (world == null || box == null) return result;
        for (Object value : world.getEntitiesWithinAABBExcludingEntity(excluded, box)) {
            if (value instanceof Entity) {
                Entity entity = (Entity) value;
                if (predicate == null || predicate.apply(entity)) result.add(entity);
            }
        }
        return result;
    }
}
