package com.rtsbuilding.rtsbuilding.server.storage.state;

import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;

/**
 * 远程放置与已放置方块回收的可变状态容器。
 *
 * <p>从 RtsStorageSession 提取，按 "玩家如何执行远程放置和回收"
 * 的职责聚合。包含放置批次作业队列和已放置方块被破坏后的掉落物回收队列。
 *
 * <h3>设计约束</h3>
 * <ul>
 *   <li><b>状态容器</b>——业务判断仍在服务层；这里只维护队列与其物品 ID 索引的一致性</li>
 *   <li><b>可独立实例化</b>——便于测试放置状态切换而无需完整 session</li>
 * </ul>
 */
public class RtsPlacementState {

    // ======================================================================
    // 放置队列
    //      尚未执行的方块放置批次。
    //      PlaceBatchJob 类型定义在 RtsPlacementBatch 中。
    // ======================================================================

    /** 待处理的放置批次作业队列 */
    /** 已放置方块被破坏后的掉落物回收作业队列 */
    public final Deque<PlacedRecoveryJob> recoveryJobs = new ArrayDeque<>();

    /**
     * 已放置方块被破坏后的掉落物回收作业。operationId 与 claim ordinal 一起提供稳定身份；
     * requiredPersistedRevision 只属于当前进程，不写入 NBT。
     */
    public static final class PlacedRecoveryJob {
        private final UUID operationId;
        private final int dimension;
        private final BlockPos targetPos;
        private final Deque<PlacedRecoveryClaim> claims;
        private long requiredPersistedRevision;

        public PlacedRecoveryJob(UUID operationId, int dimension,
                BlockPos targetPos, Deque<PlacedRecoveryClaim> claims) {
            this.operationId = java.util.Objects.requireNonNull(operationId, "operationId");
            this.dimension = dimension;
            this.targetPos = java.util.Objects.requireNonNull(targetPos, "targetPos").toImmutable();
            this.claims = java.util.Objects.requireNonNull(claims, "claims");
        }

        public UUID operationId() { return operationId; }
        public int dimension() { return dimension; }
        public BlockPos targetPos() { return targetPos; }
        public Deque<PlacedRecoveryClaim> claims() { return claims; }

        /** 当前进程内，在该 revision 得到落盘 ACK 前禁止接管任何世界实体。 */
        public long requiredPersistedRevision() { return requiredPersistedRevision; }

        public void requirePersistedRevision(long revision) {
            requiredPersistedRevision = Math.max(requiredPersistedRevision, Math.max(0L, revision));
        }
    }

    /**
     * 世界掉落实体的保守 claim。expectedStack 始终防御性复制，避免外部代码修改持久化指纹。
     */
    public static final class PlacedRecoveryClaim {
        private final UUID entityId;
        private final int ordinal;
        private final ItemStack expectedStack;

        public PlacedRecoveryClaim(UUID entityId, int ordinal, ItemStack expectedStack) {
            if (entityId == null) throw new IllegalArgumentException("entityId 不能为空");
            if (ordinal < 0) throw new IllegalArgumentException("ordinal 不能为负数");
            if (expectedStack == null || expectedStack.isEmpty()) {
                throw new IllegalArgumentException("expectedStack 不能为空");
            }
            this.entityId = entityId;
            this.ordinal = ordinal;
            this.expectedStack = expectedStack.copy();
        }

        public UUID entityId() { return entityId; }
        public int ordinal() { return ordinal; }
        public ItemStack expectedStack() {
            return expectedStack.copy();
        }

        /** 只有物品、组件和数量完全相同，才允许消费这个世界实体。 */
        public boolean matches(ItemStack actual) {
            return actual != null
                    && !actual.isEmpty()
                    && ItemStack.areItemStacksEqual(actual, expectedStack);
        }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof PlacedRecoveryClaim)) return false;
            PlacedRecoveryClaim that = (PlacedRecoveryClaim) other;
            return ordinal == that.ordinal && entityId.equals(that.entityId)
                    && ItemStack.areItemStacksEqual(expectedStack, that.expectedStack);
        }
        @Override public int hashCode() {
            return java.util.Objects.hash(entityId, ordinal, expectedStack.getItem(),
                    expectedStack.getMetadata(), expectedStack.getCount(), expectedStack.getTagCompound());
        }
    }
}
