package com.rtsbuilding.rtsbuilding.client.rendering.animation;


import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Client-only visual lie for server-confirmed remote mining.
 *
 * <p>Remote mining must absorb drops immediately on the server, because many
 * modpack item entities are easiest to catch right after {@code destroyBlock}
 * succeeds. That same insertion work can delay the block update that reaches
 * the client. This class hides the block locally as air as soon as the server
 * sends the break-confirmation animation packet.</p>
 *
 * <p>The break-confirmation packet also carries the server's post-break block
 * state. If the confirmed state is air, the local air is final and is not
 * restored. If a mod replaces the block with a non-air state, the fake air is
 * held briefly for the shrink-out animation and then settled to that confirmed
 * state.</p>
 */
public final class ClientFakeAirBlocks {
    private static final long NON_AIR_SETTLE_TIMEOUT_MS = 750L;
    private static final int CLIENT_BLOCK_UPDATE_FLAGS = 3;

    private static final Map<Long, FakeAirEntry> ENTRIES = new LinkedHashMap<>();
    private static ClientLevel activeLevel;

    private ClientFakeAirBlocks() {
    }

    public static void hideUntilServerState(BlockPos pos, BlockState originalState, BlockState resultState) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || pos == null) {
            return;
        }
        syncLevel(level);
        if (!level.hasChunkAt(pos)) {
            return;
        }
        BlockState animationState = originalState == null || originalState.isAir()
                ? level.getBlockState(pos)
                : originalState;
        BlockState confirmedState = resultState == null ? Blocks.AIR.defaultBlockState() : resultState;
        if ((animationState == null || animationState.isAir())
                && confirmedState.isAir()
                && level.getBlockState(pos).isAir()) {
            return;
        }
        BlockPos immutablePos = pos.immutable();
        long key = immutablePos.asLong();
        if (confirmedState.isAir()) {
            ENTRIES.remove(key);
        } else {
            ENTRIES.put(key, new FakeAirEntry(
                    immutablePos,
                    confirmedState,
                    System.currentTimeMillis() + NON_AIR_SETTLE_TIMEOUT_MS));
        }
        level.setBlock(immutablePos, Blocks.AIR.defaultBlockState(), CLIENT_BLOCK_UPDATE_FLAGS);
    }

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            ENTRIES.clear();
            activeLevel = null;
            return;
        }
        syncLevel(level);
        if (ENTRIES.isEmpty()) {
            return;
        }

        long now = System.currentTimeMillis();
        Iterator<Map.Entry<Long, FakeAirEntry>> iterator = ENTRIES.entrySet().iterator();
        while (iterator.hasNext()) {
            FakeAirEntry entry = iterator.next().getValue();
            if (entry == null) {
                iterator.remove();
                continue;
            }
            BlockState currentState = level.getBlockState(entry.pos());
            if (currentState.equals(entry.confirmedState())) {
                iterator.remove();
                continue;
            }
            if (now < entry.settleAtMs()) {
                continue;
            }
            iterator.remove();
            if (!level.hasChunkAt(entry.pos())) {
                continue;
            }
            if (level.getBlockState(entry.pos()).isAir()) {
                level.setBlock(entry.pos(), entry.confirmedState(), CLIENT_BLOCK_UPDATE_FLAGS);
            }
        }
    }

    private static void syncLevel(ClientLevel level) {
        if (activeLevel == level) {
            return;
        }
        ENTRIES.clear();
        activeLevel = level;
    }

    private record FakeAirEntry(BlockPos pos, BlockState confirmedState, long settleAtMs) {
    }
}
