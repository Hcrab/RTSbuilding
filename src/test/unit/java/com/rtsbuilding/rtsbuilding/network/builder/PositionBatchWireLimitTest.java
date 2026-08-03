package com.rtsbuilding.rtsbuilding.network.builder;

import com.rtsbuilding.rtsbuilding.network.builder.handler.RtsPositionBatchAssembler1122;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 锁住 Forge 1.12 自定义包的 32767 字节硬上限与服务端重组语义。 */
class PositionBatchWireLimitTest {
    @AfterEach
    void clearAssemblies() {
        RtsPositionBatchAssembler1122.clearAll();
    }

    @Test
    void largestAreaDestroyChunkStaysBelowForgeWireLimit() {
        List<BlockPos> positions = positions(C2SRtsAreaDestroyPayload.MAX_POSITIONS_PER_PACKET);
        C2SRtsAreaDestroyPayload payload = new C2SRtsAreaDestroyPayload(
                7, 0, 1, positions.size(), positions, (byte) 8,
                repeat('x', 256), ItemStack.EMPTY, true);

        assertWireSizeBelowLimit(payload::toBytes);
    }

    @Test
    void largestPlaceBatchChunkStaysBelowForgeWireLimit() {
        List<BlockPos> positions = positions(C2SRtsPlaceBatchPayload.MAX_POSITIONS_PER_PACKET);
        C2SRtsPlaceBatchPayload payload = new C2SRtsPlaceBatchPayload(
                9, 0, 1, positions.size(), positions, (byte) 1,
                0.5D, 0.5D, 0.5D, (byte) 3, repeat('s', 256),
                true, true, true, repeat('i', 128), ItemStack.EMPTY,
                1.0D, 2.0D, 3.0D, 0.0D, -1.0D, 0.0D);

        assertWireSizeBelowLimit(payload::toBytes);
    }

    @Test
    void outOfOrderChunksBecomeOneOrderedSubmission() {
        UUID playerId = UUID.randomUUID();
        List<BlockPos> second = Arrays.asList(new BlockPos(2, 0, 0));
        List<BlockPos> first = Arrays.asList(new BlockPos(0, 0, 0), new BlockPos(1, 0, 0));

        assertNull(RtsPositionBatchAssembler1122.accept(
                playerId, "test", 11, 1, 2, 3, 10, "same", second));
        List<BlockPos> merged = RtsPositionBatchAssembler1122.accept(
                playerId, "test", 11, 0, 2, 3, 10, "same", first);

        assertEquals(Arrays.asList(
                new BlockPos(0, 0, 0), new BlockPos(1, 0, 0), new BlockPos(2, 0, 0)), merged);
    }

    @Test
    void conflictingMetadataDropsSubmission() {
        UUID playerId = UUID.randomUUID();
        assertNull(RtsPositionBatchAssembler1122.accept(
                playerId, "test", 12, 0, 2, 2, 10, "a",
                Arrays.asList(new BlockPos(0, 0, 0))));
        assertNull(RtsPositionBatchAssembler1122.accept(
                playerId, "test", 12, 1, 2, 2, 10, "b",
                Arrays.asList(new BlockPos(1, 0, 0))));
    }

    private static void assertWireSizeBelowLimit(Encoder encoder) {
        ByteBuf buffer = Unpooled.buffer();
        try {
            encoder.encode(buffer);
            assertTrue(buffer.readableBytes() < 32767,
                    () -> "payload bytes=" + buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

    private static List<BlockPos> positions(int count) {
        List<BlockPos> positions = new ArrayList<BlockPos>(count);
        for (int index = 0; index < count; index++) {
            positions.add(new BlockPos(index, 64, -index));
        }
        return positions;
    }

    private static String repeat(char value, int count) {
        char[] chars = new char[count];
        Arrays.fill(chars, value);
        return new String(chars);
    }

    private interface Encoder {
        void encode(ByteBuf buffer);
    }
}
