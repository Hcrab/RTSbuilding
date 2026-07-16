package com.rtsbuilding.rtsbuilding.server.task.buffer;

import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 掉落物 escrow 的不可变任务根。
 *
 * <p>该对象是跨 tick 唯一权威；它不持有玩家、Session、世界实体或储存 handler。
 * 每次状态变化都产生新对象，必须经 TaskStore revision 持久化。</p>
 */
public record BufferEscrowState(long firstQueuedGameTime, List<BufferEscrowEntry> entries) {
    public static final int MAX_BUFFERED_ITEMS = 4_096;
    public static final int MAX_STACKS = 128;

    public BufferEscrowState {
        Objects.requireNonNull(entries, "entries");
        entries = List.copyOf(entries);
        if (entries.size() > MAX_STACKS) throw new IllegalArgumentException("escrow stack 数量越界");
        if (entries.isEmpty() && firstQueuedGameTime != -1L) {
            throw new IllegalArgumentException("空 escrow 的 firstQueuedGameTime 必须为 -1");
        }
        if (!entries.isEmpty() && firstQueuedGameTime < 0L) {
            throw new IllegalArgumentException("非空 escrow 必须有入队时间");
        }
        Set<UUID> claimIds = new HashSet<>();
        Set<Integer> ordinals = new HashSet<>();
        long itemCount = 0L;
        for (BufferEscrowEntry entry : entries) {
            Objects.requireNonNull(entry, "entry");
            if (!claimIds.add(entry.claimId())) throw new IllegalArgumentException("claimId 重复");
            if (!ordinals.add(entry.ordinal())) throw new IllegalArgumentException("ordinal 重复");
            itemCount += entry.ownedStack().getCount();
        }
        if (itemCount > MAX_BUFFERED_ITEMS) throw new IllegalArgumentException("escrow item 数量越界");
    }

    public static BufferEscrowState empty() {
        return new BufferEscrowState(-1L, List.of());
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public int bufferedItems() {
        int total = 0;
        for (BufferEscrowEntry entry : entries) total += entry.ownedStack().getCount();
        return total;
    }

    public boolean requiresRecovery() {
        return entries.stream().anyMatch(entry -> entry.phase() == BufferEscrowPhase.RECOVERY_REQUIRED);
    }

    public long phaseCount(BufferEscrowPhase phase) {
        return entries.stream().filter(entry -> entry.phase() == phase).count();
    }

    /** 建立一批写前 drain reservation；调用方必须先持久化并等待 ACK，才能执行外部写入。 */
    public BufferEscrowState reserveDrainBatch(UUID attemptId, int maxStacks) {
        Objects.requireNonNull(attemptId, "attemptId");
        if (maxStacks <= 0) return this;
        List<BufferEscrowEntry> next = new ArrayList<>(entries.size());
        int reserved = 0;
        for (BufferEscrowEntry entry : entries) {
            if (reserved < maxStacks && entry.phase() == BufferEscrowPhase.ESCROWED) {
                next.add(entry.reserveDrain(attemptId));
                reserved++;
            } else {
                next.add(entry);
            }
        }
        return reserved == 0 ? this : new BufferEscrowState(firstQueuedGameTime, next);
    }

    public BufferEscrowState sourceClaimed(UUID claimId) {
        return replace(claimId, BufferEscrowEntry::sourceClaimed);
    }

    public BufferEscrowState drainApplied(UUID claimId, ItemStack remainder) {
        return replace(claimId, entry -> entry.drainApplied(remainder));
    }

    public BufferEscrowState recoveryRequired(UUID claimId, BufferRecoveryCode reason) {
        return replace(claimId, entry -> entry.recoveryRequired(reason));
    }

    /**
     * 只允许在包含 DRAIN_APPLIED 的 revision 已收到持久化 ACK 后调用。
     * 完全写入的 claim 在此处删除；部分 remainder 回到 ESCROWED 等待下一次 reservation。
     */
    public BufferEscrowState confirmAppliedAfterAck() {
        List<BufferEscrowEntry> next = new ArrayList<>(entries.size());
        boolean changed = false;
        for (BufferEscrowEntry entry : entries) {
            if (entry.phase() != BufferEscrowPhase.DRAIN_APPLIED) {
                next.add(entry);
                continue;
            }
            BufferEscrowEntry confirmed = entry.confirmAppliedAfterAck();
            if (confirmed != null) next.add(confirmed);
            changed = true;
        }
        if (!changed) return this;
        return next.isEmpty() ? empty() : new BufferEscrowState(firstQueuedGameTime, next);
    }

    /**
     * 从 durable snapshot 恢复时的保守规则。
     *
     * <p>DRAIN_RESERVED 表示进程可能在 handler 调用前或调用后崩溃；外部储存没有幂等事务，
     * 因此禁止盲重试，转入人工/专用恢复路径。DRAIN_APPLIED 已是持久化事实，可等待 ACK 确认。</p>
     */
    public BufferEscrowState recoverLoadedSnapshot() {
        List<BufferEscrowEntry> next = new ArrayList<>(entries.size());
        boolean changed = false;
        for (BufferEscrowEntry entry : entries) {
            if (entry.phase() == BufferEscrowPhase.DRAIN_RESERVED) {
                next.add(entry.recoveryRequired(BufferRecoveryCode.DRAIN_OUTCOME_UNKNOWN));
                changed = true;
            } else {
                next.add(entry);
            }
        }
        return changed ? new BufferEscrowState(firstQueuedGameTime, next) : this;
    }

    private BufferEscrowState replace(
            UUID claimId,
            java.util.function.UnaryOperator<BufferEscrowEntry> transition) {
        Objects.requireNonNull(claimId, "claimId");
        List<BufferEscrowEntry> next = new ArrayList<>(entries);
        for (int i = 0; i < next.size(); i++) {
            BufferEscrowEntry entry = next.get(i);
            if (entry.claimId().equals(claimId)) {
                next.set(i, transition.apply(entry));
                return new BufferEscrowState(firstQueuedGameTime, next);
            }
        }
        throw new IllegalArgumentException("未知的 escrow claim: " + claimId);
    }
}
