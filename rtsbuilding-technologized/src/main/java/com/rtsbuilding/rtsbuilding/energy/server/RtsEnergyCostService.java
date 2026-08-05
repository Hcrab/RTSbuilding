package com.rtsbuilding.rtsbuilding.energy.server;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.api.energy.Action;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Charges RTS operations against the player's energy grid.
 * <p>
 * The energy economy is opt-in: players who have not built any energy blocks
 * are never charged (cost resolves to 0), so existing saves stay unaffected.
 * Once a player builds an energy bank or generator, remote operations draw FE
 * from their grid.
 * <p>
 * Charging is best-effort and never blocks placement: if the grid runs dry the
 * operation still proceeds (charged what it can), with a throttled warning
 * instead of stalling the workflow the way an "insufficient items" pause would.
 */
public final class RtsEnergyCostService {

    /** Ticks between "grid energy insufficient" warnings per player. */
    private static final int WARNING_INTERVAL_TICKS = 100;

    private static final Map<UUID, Long> lastWarningTick = new HashMap<>();

    private RtsEnergyCostService() {
    }

    /**
     * @return The FE that would be charged for {@code count} operations, or 0 if
     *         the cost is disabled or the player has no energy grid.
     */
    public static long costFor(ServerPlayer player, int count) {
        long perOperation = Config.energyPerPlacement();
        if (perOperation <= 0 || count <= 0) {
            return 0;
        }
        if (!RtsEnergyNetworkManager.INSTANCE.hasGrid(player)) {
            return 0;
        }
        long total = perOperation * count;
        return total < 0 ? Long.MAX_VALUE : total;
    }

    /**
     * Deducts the FE for {@code count} operations from the player's grid,
     * charging whatever is available when the grid is too low to cover the full
     * cost. Never blocks the operation.
     *
     * @return The FE actually deducted.
     */
    public static long consume(ServerPlayer player, int count) {
        long cost = costFor(player, count);
        if (cost == 0) {
            return 0;
        }
        long extracted = RtsEnergyNetworkManager.INSTANCE.extract(player, cost, Action.EXECUTE);
        if (extracted < cost) {
            warnInsufficient(player);
        }
        return extracted;
    }

    private static void warnInsufficient(ServerPlayer player) {
        long now = player.serverLevel().getGameTime();
        Long last = lastWarningTick.get(player.getUUID());
        if (last != null && now - last < WARNING_INTERVAL_TICKS) {
            return;
        }
        lastWarningTick.put(player.getUUID(), now);
        player.displayClientMessage(Component.translatable("message.rtsbuilding_technologized.grid_no_energy"), true);
    }

    /** Removes per-player state, e.g. on logout. */
    public static void forget(UUID playerId) {
        lastWarningTick.remove(playerId);
    }
}
