package com.rtsbuilding.rtsbuilding.network.culling;

import com.rtsbuilding.rtsbuilding.network.RtsPacketBuffer;
import io.netty.buffer.ByteBuf;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 1.12 ByteBuf 的有界剔除状态 codec。 */
final class RtsCullingPayloadCodec {
    static final int MAX_BOXES = 128;
    static final int MAX_REVEALED_BLOCKS = 4096;
    private RtsCullingPayloadCodec() { }

    static void write(ByteBuf buf, List<RtsCullingBoxSnapshot> boxes, List<BlockPos> revealed) {
        List<RtsCullingBoxSnapshot> safeBoxes = boxes == null
                ? Collections.<RtsCullingBoxSnapshot>emptyList() : boxes;
        int boxCount = Math.min(MAX_BOXES, safeBoxes.size());
        RtsPacketBuffer.writeVarInt(buf, boxCount);
        for (int i = 0; i < boxCount; i++) {
            writePos(buf, safeBoxes.get(i).min());
            writePos(buf, safeBoxes.get(i).max());
        }
        List<BlockPos> safeRevealed = revealed == null ? Collections.<BlockPos>emptyList() : revealed;
        int revealedCount = Math.min(MAX_REVEALED_BLOCKS, safeRevealed.size());
        RtsPacketBuffer.writeVarInt(buf, revealedCount);
        for (int i = 0; i < revealedCount; i++) writePos(buf, safeRevealed.get(i));
    }

    static Decoded read(ByteBuf buf) {
        int boxCount = RtsPacketBuffer.readBoundedCount(buf, MAX_BOXES, "culling boxes");
        List<RtsCullingBoxSnapshot> boxes = new ArrayList<RtsCullingBoxSnapshot>(boxCount);
        for (int i = 0; i < boxCount; i++) boxes.add(new RtsCullingBoxSnapshot(readPos(buf), readPos(buf)));
        int revealedCount = RtsPacketBuffer.readBoundedCount(buf, MAX_REVEALED_BLOCKS, "revealed blocks");
        List<BlockPos> revealed = new ArrayList<BlockPos>(revealedCount);
        for (int i = 0; i < revealedCount; i++) revealed.add(readPos(buf));
        return new Decoded(boxes, revealed);
    }

    private static void writePos(ByteBuf buf, BlockPos pos) {
        BlockPos safe = pos == null ? BlockPos.ORIGIN : pos;
        buf.writeLong(safe.toLong());
    }
    private static BlockPos readPos(ByteBuf buf) { return BlockPos.fromLong(buf.readLong()); }

    static final class Decoded {
        private final List<RtsCullingBoxSnapshot> boxes;
        private final List<BlockPos> revealed;
        Decoded(List<RtsCullingBoxSnapshot> boxes, List<BlockPos> revealed) {
            this.boxes = Collections.unmodifiableList(new ArrayList<RtsCullingBoxSnapshot>(boxes));
            this.revealed = Collections.unmodifiableList(new ArrayList<BlockPos>(revealed));
        }
        List<RtsCullingBoxSnapshot> boxes() { return boxes; }
        List<BlockPos> revealed() { return revealed; }
    }
}
