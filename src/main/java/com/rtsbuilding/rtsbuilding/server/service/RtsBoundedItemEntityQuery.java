package com.rtsbuilding.rtsbuilding.server.service;

import com.google.common.base.Predicate;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
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
 * 1.12 专用的有界掉落物查询。
 *
 * <p>原版 {@code World#getEntitiesWithinAABB} 会先收集范围内的全部实体，再把完整列表交给调用方。
 * 漏斗或批量恢复遇到极端实体堆积时，这会让“每 tick 只处理少量实体”的预算失去意义。
 * 本类直接遍历已经加载的区块实体分段，并在命中数量超过上限时立即停止；它不会加载新区块，
 * 也不负责修改或拾取实体。</p>
 */
public final class RtsBoundedItemEntityQuery {
    private RtsBoundedItemEntityQuery() {
    }

    /**
     * 查询范围内至多 {@code maxResults} 个掉落物。
     *
     * @return 查询结果；若还有至少一个符合条件但未返回的实体，{@link Result#saturated()} 为真
     */
    public static Result query(WorldServer level, AxisAlignedBB box, int maxResults,
            Predicate<? super EntityItem> filter) {
        if (level == null || box == null) {
            return Result.empty();
        }

        int safeLimit = Math.max(0, maxResults);
        List<EntityItem> matches = new ArrayList<EntityItem>(Math.min(safeLimit, 64));
        int minChunkX = MathHelper.floor((box.minX - World.MAX_ENTITY_RADIUS) / 16.0D);
        int maxChunkXExclusive = MathHelper.ceil((box.maxX + World.MAX_ENTITY_RADIUS) / 16.0D);
        int minChunkZ = MathHelper.floor((box.minZ - World.MAX_ENTITY_RADIUS) / 16.0D);
        int maxChunkZExclusive = MathHelper.ceil((box.maxZ + World.MAX_ENTITY_RADIUS) / 16.0D);

        for (int chunkX = minChunkX; chunkX < maxChunkXExclusive; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ < maxChunkZExclusive; chunkZ++) {
                Chunk chunk = level.getChunkProvider().getLoadedChunk(chunkX, chunkZ);
                if (chunk == null) {
                    continue;
                }
                if (collectFromChunk(chunk, box, safeLimit, filter, matches)) {
                    return new Result(matches, true);
                }
            }
        }
        return new Result(matches, false);
    }

    /** 达到上限后只再观察一个命中，用它区分“刚好装满”和“确实被截断”。 */
    private static boolean collectFromChunk(Chunk chunk, AxisAlignedBB box, int limit,
            Predicate<? super EntityItem> filter, List<EntityItem> matches) {
        ClassInheritanceMultiMap<Entity>[] sections = chunk.getEntityLists();
        int minSection = MathHelper.floor((box.minY - World.MAX_ENTITY_RADIUS) / 16.0D);
        int maxSection = MathHelper.floor((box.maxY + World.MAX_ENTITY_RADIUS) / 16.0D);
        minSection = MathHelper.clamp(minSection, 0, sections.length - 1);
        maxSection = MathHelper.clamp(maxSection, 0, sections.length - 1);

        for (int sectionIndex = minSection; sectionIndex <= maxSection; sectionIndex++) {
            for (EntityItem entity : sections[sectionIndex].getByClass(EntityItem.class)) {
                if (!entity.getEntityBoundingBox().intersects(box)
                        || filter != null && !filter.apply(entity)) {
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

    /** 不可变的有界查询结果。 */
    public static final class Result {
        private static final Result EMPTY = new Result(Collections.<EntityItem>emptyList(), false);

        private final List<EntityItem> entities;
        private final boolean saturated;

        private Result(List<EntityItem> entities, boolean saturated) {
            this.entities = Collections.unmodifiableList(new ArrayList<EntityItem>(entities));
            this.saturated = saturated;
        }

        private static Result empty() {
            return EMPTY;
        }

        public List<EntityItem> entities() {
            return entities;
        }

        public boolean saturated() {
            return saturated;
        }
    }
}
