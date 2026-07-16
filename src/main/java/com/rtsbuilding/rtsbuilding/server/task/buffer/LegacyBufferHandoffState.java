package com.rtsbuilding.rtsbuilding.server.task.buffer;

import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * legacy Session buffer 两阶段交接的纯值协议状态。
 *
 * <p>本对象只证明来源与所有权，不保存 ItemStack，也不触碰 Session、TaskStore 或玩家。调用方必须把
 * 本状态与真实缓存/escrow 一起持久化，并以 durable ACK 驱动两个显式阶段转换。</p>
 */
public record LegacyBufferHandoffState(
        LegacyBufferMigrationIdentity migrationIdentity,
        LegacyBufferSourceFingerprint sourceFingerprint,
        long firstQueuedGameTime,
        int stackCount,
        int itemCount,
        LegacyBufferOwnershipPhase phase) {

    public LegacyBufferHandoffState {
        Objects.requireNonNull(migrationIdentity, "migrationIdentity");
        Objects.requireNonNull(sourceFingerprint, "sourceFingerprint");
        Objects.requireNonNull(phase, "phase");
        if (firstQueuedGameTime < 0L) throw new IllegalArgumentException("firstQueuedGameTime 不能为负数");
        if (stackCount <= 0 || stackCount > BufferEscrowState.MAX_STACKS) {
            throw new IllegalArgumentException("legacy buffer stackCount 越界");
        }
        if (itemCount <= 0 || itemCount > BufferEscrowState.MAX_BUFFERED_ITEMS) {
            throw new IllegalArgumentException("legacy buffer itemCount 越界");
        }
    }

    public static LegacyBufferHandoffState freezeSessionOwned(
            RegistryAccess registryAccess, UUID ownerId, long firstQueuedGameTime,
            List<ItemStack> orderedStacks) {
        Objects.requireNonNull(orderedStacks, "orderedStacks");
        LegacyBufferSourceFingerprint fingerprint =
                LegacyBufferSourceFingerprint.freeze(registryAccess, orderedStacks);
        long items = 0L;
        for (ItemStack stack : orderedStacks) items += stack.getCount();
        if (items <= 0L || items > BufferEscrowState.MAX_BUFFERED_ITEMS) {
            throw new IllegalArgumentException("legacy buffer itemCount 越界");
        }
        return new LegacyBufferHandoffState(
                LegacyBufferMigrationIdentity.derive(ownerId, firstQueuedGameTime, fingerprint),
                fingerprint, firstQueuedGameTime, orderedStacks.size(), (int) items,
                LegacyBufferOwnershipPhase.SESSION_OWNED);
    }

    /** Task 准备 revision 已 durable ACK；重复 ACK 保持幂等。 */
    public LegacyBufferHandoffState acknowledgeTaskPrepared() {
        if (phase != LegacyBufferOwnershipPhase.SESSION_OWNED) return this;
        return withPhase(LegacyBufferOwnershipPhase.TASK_PREPARED_ACKED);
    }

    /** Session 清除 revision 已 durable ACK；禁止跳过 Task ACK。 */
    public LegacyBufferHandoffState acknowledgeSessionClear() {
        if (phase == LegacyBufferOwnershipPhase.SESSION_OWNED) {
            throw new IllegalStateException("Task 准备 ACK 前不能确认 Session clear");
        }
        if (phase == LegacyBufferOwnershipPhase.SESSION_CLEAR_ACKED) return this;
        return withPhase(LegacyBufferOwnershipPhase.SESSION_CLEAR_ACKED);
    }

    /** 只有 Session 尚为唯一权威时，登出清理才可以把物品发给玩家。 */
    public boolean sessionMayPayout() {
        return phase == LegacyBufferOwnershipPhase.SESSION_OWNED;
    }

    /** Task 必须等 Session 清除持久化后才能向 AE/RS、背包或世界执行 drain。 */
    public boolean taskMayDrain() {
        return phase == LegacyBufferOwnershipPhase.SESSION_CLEAR_ACKED;
    }

    public boolean matchesSource(RegistryAccess registryAccess, List<ItemStack> orderedStacks) {
        if (orderedStacks == null || orderedStacks.size() != stackCount) return false;
        long items = 0L;
        for (ItemStack stack : orderedStacks) {
            if (stack == null || stack.isEmpty()) return false;
            items += stack.getCount();
        }
        return items == itemCount
                && sourceFingerprint.equals(
                        LegacyBufferSourceFingerprint.freeze(registryAccess, orderedStacks));
    }

    private LegacyBufferHandoffState withPhase(LegacyBufferOwnershipPhase nextPhase) {
        return new LegacyBufferHandoffState(
                migrationIdentity, sourceFingerprint, firstQueuedGameTime,
                stackCount, itemCount, nextPhase);
    }
}
