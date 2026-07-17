package com.rtsbuilding.rtsbuilding.server.storage;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import com.rtsbuilding.rtsbuilding.server.storage.port.RtsItemStorage;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * 流体存储操作守卫——失败时自动回滚。
 *
 * <p>包装了 {@link RtsStorageFluids} 中常见的"提取 → 模拟 → 执行 → 失败退款"
 * 模式。用法：</p>
 * <pre>{@code
 * FluidOperation op = new FluidOperation(gate, insertHandlers, player);
 * if (!op.extract(extractHandlers, targetItem)) return false;
 *
 * FluidStack targetFluid = ...;
 * if (!op.attempt(() -> simulateCheck(targetFluid))) return false;
 * if (!op.attempt(() -> executeAction(targetFluid))) return false;
 *
 * op.commit();
 * // 提交后：处理剩余物，记录最近条目
 * return true;
 * }</pre>
 *
 * <p>如果任何 {@link #attempt(Supplier)} 返回 {@code false}，
 * 已提取的物品将退回到链接存储（玩家物品栏作为回退）。</p>
 */
public final class FluidOperation {

    private final FluidTransferGate gate;
    private final List<RtsItemStorage> insertHandlers;
    private final ServerPlayer player;
    @Nullable
    private ItemStack extracted;
    private boolean finalized;

    /**
     * @param gate           提供提取/退款原语的传输门
     * @param insertHandlers 失败时退回已提取物品的处理器
     * @param player         执行操作的玩家
     */
    public FluidOperation(FluidTransferGate gate, List<RtsItemStorage> insertHandlers, ServerPlayer player) {
        this.gate = Objects.requireNonNull(gate, "gate");
        this.insertHandlers = Objects.requireNonNull(insertHandlers, "insertHandlers");
        this.player = Objects.requireNonNull(player, "player");
    }

    // ──────────────────────────────────────────────────────────────────
    //  提取
    // ──────────────────────────────────────────────────────────────────

    /**
     * 从给定处理器中提取一个匹配 {@code targetItem} 的物品。
     * 提取的物品会被记住，如果操作失败将退款。
     *
     * @return 如果成功提取了物品则返回 true
     */
    public boolean extract(List<RtsItemStorage> handlers, Item targetItem) {
        if (finalized) return false;
        this.extracted = gate.extractOneFromNetwork(handlers, player, targetItem);
        return this.extracted != null && !this.extracted.isEmpty();
    }

    // ──────────────────────────────────────────────────────────────────
    //  尝试
    // ──────────────────────────────────────────────────────────────────

    /**
     * 尝试执行一个操作。如果 {@code action} 返回 false（或抛出异常），
     * 已提取的物品将被退款，且不再接受后续尝试。
     *
     * @return {@code action} 的执行结果
     */
    public boolean attempt(Supplier<Boolean> action) {
        if (finalized) return false;
        try {
            if (action.get()) return true;
        } catch (Exception e) {
            RtsbuildingMod.LOGGER.warn("[FluidOperation] Attempt threw", e);
        }
        rollback();
        return false;
    }

    // ──────────────────────────────────────────────────────────────────
    //  提交 / 回滚
    // ──────────────────────────────────────────────────────────────────

    /**
     * 将操作标记为成功。不会执行退款。
     */
    public void commit() {
        finalized = true;
    }

    // ──────────────────────────────────────────────────────────────────
    //  访问器
    // ──────────────────────────────────────────────────────────────────

    /**
     * 返回已提取的物品（可能已被排出操作修改）。
     * 仅在 {@link #extract(List, Item)} 返回 true 后有效。
     */
    @Nullable
    public ItemStack getExtracted() {
        return extracted;
    }

    // ──────────────────────────────────────────────────────────────────
    //  内部方法
    // ──────────────────────────────────────────────────────────────────

    private void rollback() {
        finalized = true;
        if (extracted != null && !extracted.isEmpty()) {
            try {
                gate.refundToLinked(insertHandlers, player, extracted);
            } catch (Exception e) {
                RtsbuildingMod.LOGGER.error("[FluidOperation] Rollback refund failed", e);
            }
        }
    }
}
