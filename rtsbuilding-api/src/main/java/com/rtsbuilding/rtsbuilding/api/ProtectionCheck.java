package com.rtsbuilding.rtsbuilding.api;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

/**
 * Claim/protection check interface.
 *
 * <p>Third-party claim plugins (FTB Chunks, GriefPrevention, Lands, etc.) implement
 * this interface and register via {@link ProtectionRegistry#register(ProtectionCheck)}.
 * Once registered, all RTS remote operations (mining, placement, interaction, breaking, etc.)
 * will pass through this check before touching the target block.
 *
 * <h3>Usage Example</h3>
 * <pre>{@code
 * ProtectionRegistry.register((player, pos) -> {
 *     if (myPlugin.isClaimedByOther(player, pos)) {
 *         return ProtectionCheck.Result.DENY;
 *     }
 *     return ProtectionCheck.Result.PASS;
 * });
 * }</pre>
 *
 * @see ProtectionRegistry
 */
@FunctionalInterface
public interface ProtectionCheck {

    /**
     * Check whether the player can perform an action at the specified position.
     *
     * @param player the player performing the action
     * @param pos    target block position
     * @return {@link Result#DENY} to deny the operation, {@link Result#PASS} to let subsequent checkers decide
     */
    Result canBreak(ServerPlayer player, BlockPos pos);

    /**
     * Check whether the player can place a block at the specified position.
     * Defaults to delegating to {@link #canBreak}.
     */
    default Result canPlace(ServerPlayer player, BlockPos pos) {
        return canBreak(player, pos);
    }

    /**
     * Check whether the player can interact with a block at the specified position.
     * Defaults to delegating to {@link #canBreak}.
     */
    default Result canInteract(ServerPlayer player, BlockPos pos) {
        return canBreak(player, pos);
    }

    /**
     * Protection check result.
     */
    enum Result {
        /** Explicitly deny the operation */
        DENY,
        /** This checker does not decide; let subsequent checkers or default logic handle it */
        PASS
    }
}
