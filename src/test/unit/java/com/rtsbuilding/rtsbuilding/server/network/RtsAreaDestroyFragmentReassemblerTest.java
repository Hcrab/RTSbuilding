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

/** 只验证 Forge 分片值状态机，不启动世界、服务端或客户端。 */
class RtsAreaDestroyFragmentReassemblerTest {
    private static final long TRACE = 0x1020_3040_5060_7080L;
    private static final long REQUEST = 0x0102_0304_0506_0708L;

    @AfterEach
    void clearNetworkState() {
        RtsAreaDestroyFragmentNetwork.clearAll();
    }

    @Test
    void reassemblesOutOfOrderFragmentsWithoutLoss() {
        List<BlockPos> expected = positions(2_049);
        List<C2SRtsAreaDestroyFragmentPayload> fragments = split(expected, REQUEST);
        Collections.reverse(fragments = new ArrayList<>(fragments));
        RtsAreaDestroyFragmentReassembler reassembler = new RtsAreaDestroyFragmentReassembler();
        C2SRtsAreaDestroyTracePayloadHolder holder = new C2SRtsAreaDestroyTracePayloadHolder();
        for (C2SRtsAreaDestroyFragmentPayload fragment : fragments) {
            var result = reassembler.accept(fragment, 20L);
            if (result.status() == RtsAreaDestroyFragmentReassembler.Status.COMPLETE) {
                holder.payload = result.payload();
            }
        }
        assertNotNull(holder.payload);
        assertEquals(expected, holder.payload.positions());
        assertEquals(TRACE, holder.payload.traceId());
        assertEquals("minecraft:diamond_pickaxe", holder.payload.toolItemId());
        assertEquals(0, reassembler.pendingCount());
    }

    @Test
    void expiresIncompleteAndIsolatesPlayers() {
        List<C2SRtsAreaDestroyFragmentPayload> fragments = split(positions(257), REQUEST);
        RtsAreaDestroyFragmentReassembler reassembler = new RtsAreaDestroyFragmentReassembler();
        assertEquals(RtsAreaDestroyFragmentReassembler.Status.INCOMPLETE,
                reassembler.accept(fragments.get(0), 100L).status());
        assertEquals(1, reassembler.expire(100L + RtsAreaDestroyFragmentReassembler.FRAGMENT_TIMEOUT_TICKS));
        assertEquals(0, reassembler.pendingTargetCount());
        assertFalse(reassembler.hasPending(REQUEST));

        UUID first = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID second = UUID.fromString("00000000-0000-0000-0000-000000000002");
        assertEquals(RtsAreaDestroyFragmentReassembler.Status.INCOMPLETE,
                RtsAreaDestroyFragmentNetwork.accept(first, fragments.get(0), 1L).status());
        assertEquals(RtsAreaDestroyFragmentReassembler.Status.INCOMPLETE,
                RtsAreaDestroyFragmentNetwork.accept(second, fragments.get(0), 1L).status());
        assertEquals(2, RtsAreaDestroyFragmentNetwork.trackedPlayerCount());
    }

    private static List<C2SRtsAreaDestroyFragmentPayload> split(List<BlockPos> positions, long requestId) {
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

    private static final class C2SRtsAreaDestroyTracePayloadHolder {
        private com.rtsbuilding.rtsbuilding.network.builder.C2SRtsAreaDestroyTracePayload payload;
    }
}
