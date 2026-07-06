package com.rtsbuilding.rtsbuilding.client.screen.panel.select;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * 实体条目——对应框选内的一个可交互实体。
 *
 * @param entityId    实体网络 ID
 * @param entity      实体对象引用
 * @param displayName 显示名称
 * @param hitLocation 命中位置
 */
public record EntityEntry(int entityId, Entity entity, String displayName, Vec3 hitLocation)
        implements SelectableEntry {

    @Override
    public Object identifier() {
        return entityId;
    }
}
