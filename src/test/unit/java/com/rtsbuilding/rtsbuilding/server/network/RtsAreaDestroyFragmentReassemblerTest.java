package com.rtsbuilding.rtsbuilding.server.network;

import com.rtsbuilding.rtsbuilding.common.diagnostics.RtsMiningStopOrigin;
import com.rtsbuilding.rtsbuilding.common.diagnostics.RtsTraceInputKind;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsAreaDestroyFragmentPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 只验证分片值状态机，不启动世界、服务端或客户端。 */
class RtsAreaDestroyFragmentReassemblerTest {
    @org.junit.jupiter.api.BeforeAll
    static void bootstrapRegistries() {
        if (net.neoforged.fml.loading.LoadingModList.get() == null) {
            net.neoforged.fml.loading.LoadingModList.of(List.of(), List.of(), List.of(), List.of(), java.util.Map.of());
        }
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }
    private static final long TRACE = 0x1020_3040_5060_7080L;
    private static final long REQUEST = 0x0102_0304_0506_0708L;

    @AfterEach
    void clearNetworkState() {
        RtsAreaDestroyFragmentNetwork.clearAll();
    }

    @Test
    void preservesFortyEightCubeAndMaximumVolumeInOneCompletion() {
        assertReassemblesWithoutLoss(cubePositions(48), REQUEST);
        assertReassemblesWithoutLoss(positions(262_144), REQUEST + 1L);
    }

    @Test
    void acceptsOutOfOrderFragmentsAndIgnoresExactDuplicates() {
        List<BlockPos> positions = positions(2_049);
        List<C2SRtsAreaDestroyFragmentPayload> fragments = split(positions, REQUEST);
        RtsAreaDestroyFragmentReassembler reassembler = new RtsAreaDestroyFragmentReassembler();

        List<C2SRtsAreaDestroyFragmentPayload> shuffled = new ArrayList<>(fragments);
        Collections.reverse(shuffled);
        int completed = 0;
        for (C2SRtsAreaDestroyFragmentPayload fragment : shuffled) {
            if (reassembler.accept(fragment, 20L).status()
                    == RtsAreaDestroyFragmentReassembler.Status.COMPLETE) {
                completed++;
            }
        }
        assertEquals(1, completed);
        assertEquals(
                RtsAreaDestroyFragmentReassembler.Status.DUPLICATE,
                reassembler.accept(fragments.getFirst(), 21L).status());
    }

    @Test
    void expiresIncompleteRequestWithoutExecutingOrRetainingTargets() {
        List<C2SRtsAreaDestroyFragmentPayload> fragments = split(positions(257), REQUEST);
        RtsAreaDestroyFragmentReassembler reassembler = new RtsAreaDestroyFragmentReassembler();

        assertEquals(RtsAreaDestroyFragmentReassembler.Status.INCOMPLETE,
                reassembler.accept(fragments.getFirst(), 100L).status());
        assertEquals(1, reassembler.pendingCount());
        assertEquals(1, reassembler.expire(
                100L + RtsAreaDestroyFragmentReassembler.FRAGMENT_TIMEOUT_TICKS));
        assertEquals(0, reassembler.pendingCount());
        assertEquals(0, reassembler.pendingTargetCount());
        assertFalse(reassembler.hasPending(REQUEST));
    }

    @Test
    void isolatesSameRequestIdentityBetweenPlayers() {
        UUID firstPlayer = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID secondPlayer = UUID.fromString("00000000-0000-0000-0000-000000000002");
        C2SRtsAreaDestroyFragmentPayload first = split(positions(257), REQUEST).getFirst();
        C2SRtsAreaDestroyFragmentPayload second = split(positions(257), REQUEST).getFirst();

        assertEquals(RtsAreaDestroyFragmentReassembler.Status.INCOMPLETE,
                RtsAreaDestroyFragmentNetwork.accept(firstPlayer, first, 1L).status());
        assertEquals(RtsAreaDestroyFragmentReassembler.Status.INCOMPLETE,
                RtsAreaDestroyFragmentNetwork.accept(secondPlayer, second, 1L).status());
        assertEquals(2, RtsAreaDestroyFragmentNetwork.trackedPlayerCount());
        assertEquals(1, RtsAreaDestroyFragmentNetwork.pendingRequestCount(firstPlayer));
        assertEquals(1, RtsAreaDestroyFragmentNetwork.pendingRequestCount(secondPlayer));
    }

    @Test
    void requiresConsistentMetadataAndDeclaredTargetBudget() {
        List<C2SRtsAreaDestroyFragmentPayload> fragments = split(positions(257), REQUEST);
        RtsAreaDestroyFragmentReassembler reassembler = new RtsAreaDestroyFragmentReassembler();
        C2SRtsAreaDestroyFragmentPayload first = fragments.getFirst();

        C2SRtsAreaDestroyFragmentPayload conflictingMetadata = new C2SRtsAreaDestroyFragmentPayload(
                TRACE, REQUEST, first.sequence(), first.clientTick(), first.heldMs(),
                first.inputKind(), first.stopOrigin(), 1, fragments.size(), first.totalPositions(),
                true, (byte) 4, "minecraft:netherite_pickaxe", ItemStack.EMPTY, true,
                fragments.get(1).positions());
        assertEquals(RtsAreaDestroyFragmentReassembler.Status.INCOMPLETE,
                reassembler.accept(first, 1L).status());
        assertEquals(RtsAreaDestroyFragmentReassembler.Status.REJECTED,
                reassembler.accept(conflictingMetadata, 2L).status());
        assertEquals(0, reassembler.pendingCount());

        C2SRtsAreaDestroyFragmentPayload overBudget = new C2SRtsAreaDestroyFragmentPayload(
                TRACE, REQUEST + 1L, 1, 1L, 0,
                RtsTraceInputKind.MOUSE.wireId(), RtsMiningStopOrigin.NONE.wireId(),
                0, 1, 1, true, (byte) 0, "", ItemStack.EMPTY, false,
                positions(2));
        assertEquals(RtsAreaDestroyFragmentReassembler.Status.REJECTED,
                new RtsAreaDestroyFragmentReassembler().accept(overBudget, 0L).status());
    }

    private static void assertReassemblesWithoutLoss(List<BlockPos> expected, long requestId) {
        RtsAreaDestroyFragmentReassembler reassembler = new RtsAreaDestroyFragmentReassembler();
        C2SRtsAreaDestroyTracePayloadHolder holder = new C2SRtsAreaDestroyTracePayloadHolder();
        for (C2SRtsAreaDestroyFragmentPayload fragment : split(expected, requestId)) {
            RtsAreaDestroyFragmentReassembler.Result result = reassembler.accept(fragment, 10L);
            if (result.status() == RtsAreaDestroyFragmentReassembler.Status.COMPLETE) {
                holder.payload = result.payload();
            }
        }
        assertNotNull(holder.payload);
        assertEquals(expected, holder.payload.positions());
        assertEquals(TRACE, holder.payload.traceId());
        assertEquals(7, holder.payload.sequence());
        assertEquals((byte) 3, holder.payload.toolSlot());
        assertEquals("minecraft:diamond_pickaxe", holder.payload.toolItemId());
        assertTrue(holder.payload.toolProtectionEnabled());
        assertEquals(0, reassembler.pendingCount());
    }

    private static List<C2SRtsAreaDestroyFragmentPayload> split(
            List<BlockPos> positions, long requestId) {
        return C2SRtsAreaDestroyFragmentPayload.split(
                TRACE, requestId, 7, 99L, 12,
                RtsTraceInputKind.MOUSE.wireId(), RtsMiningStopOrigin.NONE.wireId(),
                positions, (byte) 3, "minecraft:diamond_pickaxe", ItemStack.EMPTY, true);
    }

    private static List<BlockPos> positions(int count) {
        List<BlockPos> positions = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            positions.add(new BlockPos(index & 255, (index >>> 8) & 255, index >>> 16));
        }
        return List.copyOf(positions);
    }

    private static List<BlockPos> cubePositions(int edge) {
        List<BlockPos> positions = new ArrayList<>(edge * edge * edge);
        for (int y = 0; y < edge; y++) {
            for (int z = 0; z < edge; z++) {
                for (int x = 0; x < edge; x++) {
                    positions.add(new BlockPos(x, y, z));
                }
            }
        }
        return List.copyOf(positions);
    }

    private static final class C2SRtsAreaDestroyTracePayloadHolder {
        private com.rtsbuilding.rtsbuilding.network.builder.C2SRtsAreaDestroyTracePayload payload;
    }
}
