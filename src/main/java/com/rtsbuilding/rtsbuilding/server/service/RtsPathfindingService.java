package com.rtsbuilding.rtsbuilding.server.service;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side pathfinding tracking — no longer drives movement directly.
 * <p>
 * Movement is now handled entirely on the client side
 * ({@link com.rtsbuilding.rtsbuilding.client.pathfinding.RtsClientPathfinding}).
 * The client steers the local player and reports position via the vanilla
 * {@link net.minecraft.network.protocol.game.ServerboundMovePlayerPacket}.
 * <p>
 * This service only tracks which players have active targets, so that
 * cleanup can happen on dimension change / logout.
 */
public final class RtsPathfindingService {

    private static final Map<UUID, BlockPos> MOVE_TARGETS = new ConcurrentHashMap<>();

    private RtsPathfindingService() {}

    /**
     * Records that the player is moving toward {@code target}.
     * Movement itself is handled on the client.
     */
    public static void goTo(ServerPlayer player, BlockPos target) {
        cancel(player);
        MOVE_TARGETS.put(player.getUUID(), target.immutable());
    }

    /**
     * Cancels any active movement for the given player.
     */
    public static void cancel(ServerPlayer player) {
        MOVE_TARGETS.remove(player.getUUID());
    }

    /**
     * Returns {@code true} if the player currently has a movement target.
     */
    public static boolean isMoving(ServerPlayer player) {
        return MOVE_TARGETS.containsKey(player.getUUID());
    }
}
