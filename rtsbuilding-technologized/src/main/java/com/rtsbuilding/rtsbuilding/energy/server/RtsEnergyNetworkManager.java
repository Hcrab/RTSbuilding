package com.rtsbuilding.rtsbuilding.energy.server;

import com.rtsbuilding.rtsbuilding.api.energy.Action;
import com.rtsbuilding.rtsbuilding.api.energy.AutomationType;
import com.rtsbuilding.rtsbuilding.api.energy.IEnergyContainer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.*;

/**
 * Server-side manager for the RTS energy grid.
 * <p>
 * Tracks every placed energy node ({@link RtsEnergyNode}) and aggregates the
 * per-player grid: the combined energy buffers of all {@code rts_energy_bank}
 * and {@code rts_thermal_generator} blocks the player has built. RTS operations
 * draw energy from this aggregate via {@link #extract(ServerPlayer, long, Action)}.
 * <p>
 * Generation is handled by the nodes themselves: generators add to their own
 * buffer each tick, and since those buffers are part of the player's aggregate,
 * generated energy immediately counts toward the player's grid.
 */
public final class RtsEnergyNetworkManager {

    public static final RtsEnergyNetworkManager INSTANCE = new RtsEnergyNetworkManager();

    private final Map<ResourceKey<Level>, Map<BlockPos, RtsEnergyNode>> nodes = new HashMap<>();

    private RtsEnergyNetworkManager() {
    }

    /** Registers a node so it counts toward its owner's grid. */
    public synchronized void register(RtsEnergyNode node) {
        if (node == null || node.getLevel() == null || node.getLevel().isClientSide) {
            return;
        }
        nodes.computeIfAbsent(node.getLevel().dimension(), key -> new HashMap<>())
              .put(node.getBlockPos(), node);
    }

    /** Unregisters a node (block broken or chunk unloaded). */
    public synchronized void unregister(Level level, BlockPos pos) {
        if (level == null || pos == null) {
            return;
        }
        Map<BlockPos, RtsEnergyNode> byPos = nodes.get(level.dimension());
        if (byPos != null) {
            byPos.remove(pos);
            if (byPos.isEmpty()) {
                nodes.remove(level.dimension());
            }
        }
    }

    /** Clears all nodes — call on server stopped to avoid leaking world references. */
    public synchronized void clear() {
        nodes.clear();
    }

    private synchronized List<RtsEnergyNode> nodesFor(ServerPlayer player) {
        List<RtsEnergyNode> result = new ArrayList<>();
        if (player == null) {
            return result;
        }
        UUID playerId = player.getUUID();
        Map<BlockPos, RtsEnergyNode> byPos = nodes.get(player.serverLevel().dimension());
        if (byPos != null) {
            for (RtsEnergyNode node : byPos.values()) {
                if (playerId.equals(node.getOwner())) {
                    result.add(node);
                }
            }
        }
        return result;
    }

    private synchronized List<IEnergyContainer> buffersFor(ServerPlayer player) {
        List<IEnergyContainer> buffers = new ArrayList<>();
        for (RtsEnergyNode node : nodesFor(player)) {
            IEnergyContainer buffer = node.getEnergyBuffer();
            if (buffer != null) {
                buffers.add(buffer);
            }
        }
        return buffers;
    }

    /**
     * @return {@code true} if the player has built at least one energy block.
     */
    public boolean hasGrid(ServerPlayer player) {
        return !buffersFor(player).isEmpty();
    }

    /**
     * @return The total FE stored in the player's grid.
     */
    public long getStored(ServerPlayer player) {
        long total = 0;
        for (IEnergyContainer buffer : buffersFor(player)) {
            total += buffer.getEnergy();
        }
        return total;
    }

    /**
     * @return The total FE capacity of the player's grid.
     */
    public long getMaxEnergy(ServerPlayer player) {
        long total = 0;
        for (IEnergyContainer buffer : buffersFor(player)) {
            total += buffer.getMaxEnergy();
        }
        return total;
    }

    /**
     * @return The FE the player's grid can still accept.
     */
    public long getNeeded(ServerPlayer player) {
        long total = 0;
        for (IEnergyContainer buffer : buffersFor(player)) {
            total += buffer.getNeeded();
        }
        return total;
    }

    /**
     * Inserts energy into the player's grid, distributing across all owned buffers.
     *
     * @return The energy that could not be inserted.
     */
    public long insert(ServerPlayer player, long amount, Action action) {
        if (amount <= 0) {
            return amount;
        }
        long remaining = amount;
        for (IEnergyContainer buffer : buffersFor(player)) {
            remaining = buffer.insert(remaining, action, AutomationType.EXTERNAL);
            if (remaining == 0) {
                return 0;
            }
        }
        return remaining;
    }

    /**
     * Extracts energy from the player's grid, distributing across all owned buffers.
     *
     * @return The energy actually extracted.
     */
    public long extract(ServerPlayer player, long amount, Action action) {
        if (amount <= 0) {
            return 0;
        }
        long extracted = 0;
        for (IEnergyContainer buffer : buffersFor(player)) {
            if (buffer.isEmpty()) {
                continue;
            }
            extracted += buffer.extract(amount - extracted, action, AutomationType.EXTERNAL);
            if (extracted >= amount) {
                break;
            }
        }
        return extracted;
    }
}
