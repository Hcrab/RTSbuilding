package com.rtsbuilding.rtsbuilding.server.task.effect;

import java.util.Objects;

/**
 * 副作用归并规则的唯一入口。
 *
 * <p>当前所有类型都是幂等脏标记，因此同一目标、同一 Tick 内按集合并集归并。后续若出现
 * “最后一个值获胜”的投影，应在这里增加明确类型，而不是把任意 {@link Runnable} 塞进账本。</p>
 */
public final class RtsEffectReducer {
    private RtsEffectReducer() {
    }

    public static RtsEffectSet reduce(RtsEffectSet current, RtsEffectSet incoming) {
        return Objects.requireNonNull(current, "current")
                .union(Objects.requireNonNull(incoming, "incoming"));
    }
}
