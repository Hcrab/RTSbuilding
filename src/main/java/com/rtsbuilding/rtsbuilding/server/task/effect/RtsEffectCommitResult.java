package com.rtsbuilding.rtsbuilding.server.task.effect;

import java.util.Objects;

/** 单个目标的副作用提交确认。 */
public record RtsEffectCommitResult(RtsEffectSet committed) {
    public RtsEffectCommitResult {
        Objects.requireNonNull(committed, "committed");
    }

    public static RtsEffectCommitResult all(RtsEffectSet requested) {
        return new RtsEffectCommitResult(Objects.requireNonNull(requested, "requested"));
    }

    public static RtsEffectCommitResult none() {
        return new RtsEffectCommitResult(RtsEffectSet.empty());
    }
}
