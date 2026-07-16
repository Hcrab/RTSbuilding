package com.rtsbuilding.rtsbuilding.server.task;

import com.rtsbuilding.rtsbuilding.server.task.buffer.BufferEscrowState;
import com.rtsbuilding.rtsbuilding.server.task.buffer.LegacyBufferSourceFingerprint;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Objects;
import java.util.UUID;

/**
 * 掉落缓存任务的纯值载荷。
 *
 * <p>玩家与 Session 只允许在执行 slice 时临时解析；跨 tick 的物品所有权、阶段和游标全部由
 * {@link BufferEscrowState} 持有，避免 Session 同时成为任务与缓存的双重权威。</p>
 */
public record BufferDrainTaskPayload(
        UUID ownerId,
        ResourceKey<Level> dimension,
        UUID escrowId,
        LegacyBufferSourceFingerprint legacySourceFingerprint,
        BufferEscrowState state) implements TaskPayload {

    public BufferDrainTaskPayload {
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(escrowId, "escrowId");
        Objects.requireNonNull(state, "state");
    }

    /** 非 legacy 的世界实体 escrow 兼容构造器。 */
    public BufferDrainTaskPayload(
            UUID ownerId, ResourceKey<Level> dimension, UUID escrowId, BufferEscrowState state) {
        this(ownerId, dimension, escrowId, null, state);
    }

    public BufferDrainTaskPayload withState(BufferEscrowState nextState) {
        return new BufferDrainTaskPayload(
                ownerId, dimension, escrowId, legacySourceFingerprint, nextState);
    }

    public boolean migratedFromLegacySession() {
        return legacySourceFingerprint != null;
    }
}
