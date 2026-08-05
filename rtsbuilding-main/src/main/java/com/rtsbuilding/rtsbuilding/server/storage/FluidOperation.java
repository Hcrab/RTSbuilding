package com.rtsbuilding.rtsbuilding.server.storage;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Fluid storage operation guard — auto-rollback on failure.
 *
 * <p>Wraps the common "extract → simulate → execute → refund on failure"
 * pattern found in {@link RtsStorageFluids}. Usage:</p>
 * <pre>{@code
 * FluidOperation op = new FluidOperation(gate, insertHandlers, player);
 * if (!op.extract(extractHandlers, targetItem)) return false;
 *
 * FluidStack targetFluid = ...;
 * if (!op.attempt(() -> simulateCheck(targetFluid))) return false;
 * if (!op.attempt(() -> executeAction(targetFluid))) return false;
 *
 * op.commit();
 * // After commit: handle remainder, record recent entry
 * return true;
 * }</pre>
 *
 * <p>If any {@link #attempt(Supplier)} returns {@code false},
 * the extracted items are refunded back to linked storage (player inventory as fallback).</p>
 */
public final class FluidOperation {

    private final FluidTransferGate gate;
    private final List<IItemHandler> insertHandlers;
    private final ServerPlayer player;
    @Nullable
    private ItemStack extracted;
    private boolean finalized;

    /**
     * @param gate           The transfer gate providing extract/refund primitives
     * @param insertHandlers The handlers to refund extracted items to on failure
     * @param player         The player performing the operation
     */
    public FluidOperation(FluidTransferGate gate, List<IItemHandler> insertHandlers, ServerPlayer player) {
        this.gate = Objects.requireNonNull(gate, "gate");
        this.insertHandlers = Objects.requireNonNull(insertHandlers, "insertHandlers");
        this.player = Objects.requireNonNull(player, "player");
    }

    // ──────────────────────────────────────────────────────────────────
    //  Extract
    // ──────────────────────────────────────────────────────────────────

    /**
     * Extracts one item matching {@code targetItem} from the given handlers.
     * The extracted item is remembered and will be refunded if the operation fails.
     *
     * @return true if an item was successfully extracted
     */
    public boolean extract(List<IItemHandler> handlers, Item targetItem) {
        if (finalized) return false;
        this.extracted = gate.extractOneFromNetwork(handlers, player, targetItem);
        return this.extracted != null && !this.extracted.isEmpty();
    }

    // ──────────────────────────────────────────────────────────────────
    //  Attempt
    // ──────────────────────────────────────────────────────────────────

    /**
     * Attempts to execute an action. If {@code action} returns false (or throws an exception),
     * the extracted items are refunded and no further attempts are accepted.
     *
     * @return The result of {@code action}
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
    //  Commit / Rollback
    // ──────────────────────────────────────────────────────────────────

    /**
     * Marks the operation as successful. No refund will be performed.
     */
    public void commit() {
        finalized = true;
    }

    // ──────────────────────────────────────────────────────────────────
    //  Accessors
    // ──────────────────────────────────────────────────────────────────

    /**
     * Returns the extracted item (may have been modified by drain operations).
     * Only valid after {@link #extract(List, Item)} returns true.
     */
    @Nullable
    public ItemStack getExtracted() {
        return extracted;
    }

    // ──────────────────────────────────────────────────────────────────
    //  Internal methods
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
