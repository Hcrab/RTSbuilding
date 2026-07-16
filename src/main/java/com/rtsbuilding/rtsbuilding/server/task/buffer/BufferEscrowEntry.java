package com.rtsbuilding.rtsbuilding.server.task.buffer;

import net.minecraft.world.item.ItemStack;

import java.util.Objects;
import java.util.UUID;

/**
 * Escrow 中单个物品 claim 的纯值快照。
 *
 * <p>{@code sourceSnapshot} 永远保留最初物品及组件，供来源校验和人工审计；
 * {@code ownedStack} 表示当前仍由 escrow 负责的 remainder。所有访问器均返回副本，避免调用方
 * 绕过 TaskStore revision 就地修改 ItemStack。</p>
 */
public record BufferEscrowEntry(
        UUID claimId,
        int ordinal,
        UUID sourceEntityId,
        ItemStack sourceSnapshot,
        ItemStack ownedStack,
        BufferEscrowPhase phase,
        UUID attemptId,
        int reservedCount,
        BufferRecoveryCode recoveryCode) {

    public BufferEscrowEntry {
        Objects.requireNonNull(claimId, "claimId");
        Objects.requireNonNull(sourceSnapshot, "sourceSnapshot");
        Objects.requireNonNull(ownedStack, "ownedStack");
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(recoveryCode, "recoveryCode");
        sourceSnapshot = sourceSnapshot.copy();
        ownedStack = ownedStack.copy();
        validate(ordinal, sourceEntityId, sourceSnapshot, ownedStack,
                phase, attemptId, reservedCount, recoveryCode);
    }

    public static BufferEscrowEntry prepared(
            UUID claimId, int ordinal, UUID sourceEntityId, ItemStack sourceSnapshot) {
        return new BufferEscrowEntry(claimId, ordinal, sourceEntityId,
                sourceSnapshot, sourceSnapshot, BufferEscrowPhase.SOURCE_PREPARED,
                null, 0, BufferRecoveryCode.NONE);
    }

    public static BufferEscrowEntry alreadyEscrowed(
            UUID claimId, int ordinal, ItemStack stack) {
        return new BufferEscrowEntry(claimId, ordinal, null,
                stack, stack, BufferEscrowPhase.ESCROWED,
                null, 0, BufferRecoveryCode.NONE);
    }

    @Override
    public ItemStack sourceSnapshot() {
        return sourceSnapshot.copy();
    }

    @Override
    public ItemStack ownedStack() {
        return ownedStack.copy();
    }

    public BufferEscrowEntry sourceClaimed() {
        require(BufferEscrowPhase.SOURCE_PREPARED);
        return new BufferEscrowEntry(claimId, ordinal, sourceEntityId,
                sourceSnapshot, ownedStack, BufferEscrowPhase.ESCROWED,
                null, 0, BufferRecoveryCode.NONE);
    }

    public BufferEscrowEntry reserveDrain(UUID nextAttemptId) {
        require(BufferEscrowPhase.ESCROWED);
        Objects.requireNonNull(nextAttemptId, "nextAttemptId");
        return new BufferEscrowEntry(claimId, ordinal, sourceEntityId,
                sourceSnapshot, ownedStack, BufferEscrowPhase.DRAIN_RESERVED,
                nextAttemptId, ownedStack.getCount(), BufferRecoveryCode.NONE);
    }

    public BufferEscrowEntry drainApplied(ItemStack remainder) {
        require(BufferEscrowPhase.DRAIN_RESERVED);
        ItemStack safeRemainder = remainder == null ? ItemStack.EMPTY : remainder.copy();
        if (!safeRemainder.isEmpty()
                && (!ItemStack.isSameItemSameComponents(ownedStack, safeRemainder)
                || safeRemainder.getCount() > reservedCount)) {
            throw new IllegalArgumentException("drain remainder 与 reservation 不一致");
        }
        return new BufferEscrowEntry(claimId, ordinal, sourceEntityId,
                sourceSnapshot, safeRemainder, BufferEscrowPhase.DRAIN_APPLIED,
                attemptId, reservedCount, BufferRecoveryCode.NONE);
    }

    /** 只允许中央持久化层在 DRAIN_APPLIED revision 收到 ACK 后调用。 */
    public BufferEscrowEntry confirmAppliedAfterAck() {
        require(BufferEscrowPhase.DRAIN_APPLIED);
        if (ownedStack.isEmpty()) return null;
        return new BufferEscrowEntry(claimId, ordinal, sourceEntityId,
                sourceSnapshot, ownedStack, BufferEscrowPhase.ESCROWED,
                null, 0, BufferRecoveryCode.NONE);
    }

    public BufferEscrowEntry recoveryRequired(BufferRecoveryCode reason) {
        if (reason == null || reason == BufferRecoveryCode.NONE) {
            throw new IllegalArgumentException("恢复原因不能为空");
        }
        return new BufferEscrowEntry(claimId, ordinal, sourceEntityId,
                sourceSnapshot, ownedStack, BufferEscrowPhase.RECOVERY_REQUIRED,
                attemptId, reservedCount, reason);
    }

    private void require(BufferEscrowPhase expected) {
        if (phase != expected) {
            throw new IllegalStateException("非法 escrow 阶段切换: " + phase + " -> " + expected);
        }
    }

    private static void validate(
            int ordinal,
            UUID sourceEntityId,
            ItemStack sourceSnapshot,
            ItemStack ownedStack,
            BufferEscrowPhase phase,
            UUID attemptId,
            int reservedCount,
            BufferRecoveryCode recoveryCode) {
        if (ordinal < 0) throw new IllegalArgumentException("ordinal 不能为负数");
        if (sourceSnapshot.isEmpty()) throw new IllegalArgumentException("sourceSnapshot 不能为空");
        if (!ownedStack.isEmpty()
                && !ItemStack.isSameItemSameComponents(sourceSnapshot, ownedStack)) {
            throw new IllegalArgumentException("ownedStack 必须保持原物品及组件");
        }
        if (phase == BufferEscrowPhase.SOURCE_PREPARED && sourceEntityId == null) {
            throw new IllegalArgumentException("SOURCE_PREPARED 必须有来源实体 UUID");
        }
        if ((phase == BufferEscrowPhase.ESCROWED || phase == BufferEscrowPhase.SOURCE_PREPARED)
                && ownedStack.isEmpty()) {
            throw new IllegalArgumentException("待处理 escrow 物品不能为空");
        }
        boolean drainPhase = phase == BufferEscrowPhase.DRAIN_RESERVED
                || phase == BufferEscrowPhase.DRAIN_APPLIED;
        if (drainPhase && (attemptId == null || reservedCount <= 0)) {
            throw new IllegalArgumentException("drain 阶段必须有 attemptId 与 reservedCount");
        }
        if (phase == BufferEscrowPhase.DRAIN_RESERVED
                && (ownedStack.isEmpty() || ownedStack.getCount() != reservedCount)) {
            throw new IllegalArgumentException("DRAIN_RESERVED 必须保留完整 reservation");
        }
        if (phase == BufferEscrowPhase.DRAIN_APPLIED
                && !ownedStack.isEmpty() && ownedStack.getCount() > reservedCount) {
            throw new IllegalArgumentException("DRAIN_APPLIED remainder 超过 reservation");
        }
        if (!drainPhase && phase != BufferEscrowPhase.RECOVERY_REQUIRED
                && (attemptId != null || reservedCount != 0)) {
            throw new IllegalArgumentException("非 drain 阶段不能保留 attempt");
        }
        if (phase == BufferEscrowPhase.RECOVERY_REQUIRED
                != (recoveryCode != BufferRecoveryCode.NONE)) {
            throw new IllegalArgumentException("恢复阶段与恢复原因不一致");
        }
    }
}
