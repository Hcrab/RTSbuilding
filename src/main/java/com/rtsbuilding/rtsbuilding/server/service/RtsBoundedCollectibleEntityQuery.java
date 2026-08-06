package com.rtsbuilding.rtsbuilding.server.service;

import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.util.ClassInheritanceMultiMap;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 1.12 漏斗专用的有界可收集实体查询。
 *
 * <p>本类只负责从已经加载的区块中收集掉落物和经验球，并在达到结果上限时立即停止；
 * 它不加载新区块，也不决定物品或经验应当如何结算。这样旧版漏斗增加经验球后，仍与新版一样
 * 共享同一份实体预算，不会退回到 {@code World#getEntitiesWithinAABB} 的无界临时列表。</p>
 */
public final class RtsBoundedCollectibleEntityQuery {
    private RtsBoundedCollectibleEntityQuery() {
    }

    public static List<Entity> query(WorldServer level, AxisAlignedBB box, int maxResults) {
        if (level == null || box == null || maxResults <= 0) {
            return Collections.emptyList();
        }

        List<Entity> matches = new ArrayList<Entity>(Math.min(maxResults, 64));
        int minChunkX = MathHelper.floor((box.minX - World.MAX_ENTITY_RADIUS) / 16.0D);
        int maxChunkXExclusive = MathHelper.ceil((box.maxX + World.MAX_ENTITY_RADIUS) / 16.0D);
        int minChunkZ = MathHelper.floor((box.minZ - World.MAX_ENTITY_RADIUS) / 16.0D);
        int maxChunkZExclusive = MathHelper.ceil((box.maxZ + World.MAX_ENTITY_RADIUS) / 16.0D);

        for (int chunkX = minChunkX; chunkX < maxChunkXExclusive; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ < maxChunkZExclusive; chunkZ++) {
                Chunk chunk = level.getChunkProvider().getLoadedChunk(chunkX, chunkZ);
                if (chunk != null && collectFromChunk(chunk, box, maxResults, matches)) {
                    return matches;
                }
            }
        }
        return matches;
    }

    private static boolean collectFromChunk(Chunk chunk, AxisAlignedBB box, int limit,
            List<Entity> matches) {
        ClassInheritanceMultiMap<Entity>[] sections = chunk.getEntityLists();
        int minSection = MathHelper.floor((box.minY - World.MAX_ENTITY_RADIUS) / 16.0D);
        int maxSection = MathHelper.floor((box.maxY + World.MAX_ENTITY_RADIUS) / 16.0D);
        minSection = MathHelper.clamp(minSection, 0, sections.length - 1);
        maxSection = MathHelper.clamp(maxSection, 0, sections.length - 1);

        for (int sectionIndex = minSection; sectionIndex <= maxSection; sectionIndex++) {
            for (Entity entity : sections[sectionIndex]) {
                if (!(entity instanceof EntityItem) && !(entity instanceof EntityXPOrb)) {
                    continue;
                }
                if (!entity.isEntityAlive() || !entity.getEntityBoundingBox().intersects(box)) {
                    continue;
                }
                if (entity instanceof EntityItem && ((EntityItem) entity).getItem().isEmpty()) {
                    continue;
                }
                if (matches.size() >= limit) {
                    return true;
                }
                matches.add(entity);
            }
        }
        return false;
    }
}
