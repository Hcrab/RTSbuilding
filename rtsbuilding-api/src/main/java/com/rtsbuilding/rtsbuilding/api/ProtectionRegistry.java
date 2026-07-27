package com.rtsbuilding.rtsbuilding.api;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.ApiStatus;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Claim/protection check registry.
 *
 * <p>Holds all registered {@link ProtectionCheck} instances.
 * The RTS core iterates through all checkers before each remote operation;
 * if any checker returns {@link ProtectionCheck.Result#DENY}, the operation is denied.
 *
 * <p>If no checkers are registered, all operations are allowed by default
 * (behaving as if this feature is absent).
 *
 * <p>This registry is a global singleton and is thread-safe.
 *
 * @see ProtectionCheck
 */
public final class ProtectionRegistry {

    private static final List<ProtectionCheck> checks = new CopyOnWriteArrayList<>();

    private ProtectionRegistry() {
    }

    /**
     * Register a protection checker.
     * Checkers are invoked in registration order; any checker returning
     * {@link ProtectionCheck.Result#DENY} denies the operation.
     *
     * @param check the protection checker (must not be null)
     */
    public static void register(ProtectionCheck check) {
        if (check != null) {
            checks.add(check);
        }
    }

    /**
     * Check whether the player can break a block at the specified position.
     * Iterates through all registered checkers; returns false if any returns
     * {@link ProtectionCheck.Result#DENY}.
     *
     * @return true if all checkers allow it (or no checkers are registered)
     */
    public static boolean canBreak(ServerPlayer player, BlockPos pos) {
        if (player == null || pos == null || checks.isEmpty()) {
            return true;
        }
        for (ProtectionCheck check : checks) {
            if (check.canBreak(player, pos) == ProtectionCheck.Result.DENY) {
                return false;
            }
        }
        return true;
    }

    /**
     * Check whether the player can place a block at the specified position.
     *
     * @return true if all checkers allow it (or no checkers are registered)
     */
    public static boolean canPlace(ServerPlayer player, BlockPos pos) {
        if (player == null || pos == null || checks.isEmpty()) {
            return true;
        }
        for (ProtectionCheck check : checks) {
            if (check.canPlace(player, pos) == ProtectionCheck.Result.DENY) {
                return false;
            }
        }
        return true;
    }

    /**
     * Check whether the player can interact with a block at the specified position.
     *
     * @return true if all checkers allow it (or no checkers are registered)
     */
    public static boolean canInteract(ServerPlayer player, BlockPos pos) {
        if (player == null || pos == null || checks.isEmpty()) {
            return true;
        }
        for (ProtectionCheck check : checks) {
            if (check.canInteract(player, pos) == ProtectionCheck.Result.DENY) {
                return false;
            }
        }
        return true;
    }

    /** Returns the number of registered checkers. */
    public static int size() {
        return checks.size();
    }

    /** Removes all registered checkers. Used for testing or mod reloading. */
    @ApiStatus.Internal
    public static void clear() {
        checks.clear();
    }
}
