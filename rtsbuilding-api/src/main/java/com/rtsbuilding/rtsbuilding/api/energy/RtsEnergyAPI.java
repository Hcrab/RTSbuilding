package com.rtsbuilding.rtsbuilding.api.energy;

import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * Energy API for the RTS Building mod.
 * <p>
 * Gives access to the per-player energy grid — the combined storage and
 * generation of all {@code rts_energy_bank} and {@code rts_thermal_generator}
 * blocks the player has built. RTS operations such as remote placement consume
 * FE from this grid.
 * <p>
 * Values are measured in Forge Energy (FE).
 *
 * <h3>Usage Example</h3>
 * <pre>{@code
 * RtsEnergyAPI energy = RtsAPI.get().energy();
 * if (energy.consume(player, 50)) {
 *     // place the block
 * }
 * }</pre>
 */
public interface RtsEnergyAPI {

    /**
     * Creates a simple energy container with the given capacity.
     *
     * @param maxEnergy Maximum storable energy in FE.
     *
     * @return A new {@link IEnergyContainer} with no change listener.
     */
    IEnergyContainer createContainer(long maxEnergy);

    /**
     * Creates a simple energy container with the given capacity and listener.
     *
     * @param maxEnergy Maximum storable energy in FE.
     * @param listener  Listener notified when the container's contents change, or {@code null}.
     *
     * @return A new {@link IEnergyContainer}.
     */
    IEnergyContainer createContainer(long maxEnergy, @Nullable IContentsListener listener);

    /**
     * Checks whether the player has built any energy blocks (and therefore has a grid).
     *
     * @param player The server player.
     *
     * @return {@code true} if the player has a non-empty energy grid.
     */
    boolean hasGrid(ServerPlayer player);

    /**
     * @param player The server player.
     *
     * @return The total FE currently stored in the player's energy grid.
     */
    long getStoredEnergy(ServerPlayer player);

    /**
     * @param player The server player.
     *
     * @return The total FE capacity of the player's energy grid.
     */
    long getMaxEnergy(ServerPlayer player);

    /**
     * @param player The server player.
     *
     * @return The FE the player's grid can still accept.
     */
    long getNeededEnergy(ServerPlayer player);

    /**
     * Inserts energy into the player's energy grid storage.
     *
     * @param player The server player.
     * @param amount Energy to insert in FE.
     * @param action The action to perform, either {@link Action#EXECUTE} or {@link Action#SIMULATE}
     *
     * @return The remaining energy that was not inserted (0 if fully accepted).
     */
    long insertEnergy(ServerPlayer player, long amount, Action action);

    /**
     * Extracts energy from the player's energy grid storage.
     *
     * @param player The server player.
     * @param amount Energy to extract in FE.
     * @param action The action to perform, either {@link Action#EXECUTE} or {@link Action#SIMULATE}
     *
     * @return Energy extracted (0 if nothing could be extracted).
     */
    long extractEnergy(ServerPlayer player, long amount, Action action);

    /**
     * Convenience: extracts {@code amount} FE from the player's grid, executing
     * only if the full amount is available (atomic check + deduct).
     *
     * @param player The server player.
     * @param amount Energy to consume in FE.
     *
     * @return {@code true} if the energy was available and has been consumed.
     */
    default boolean consume(ServerPlayer player, long amount) {
        return extractEnergy(player, amount, Action.SIMULATE) >= amount
                && extractEnergy(player, amount, Action.EXECUTE) >= amount;
    }
}
