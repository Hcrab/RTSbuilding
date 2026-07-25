package com.rtsbuilding.rtsbuilding.network.culling;

import com.rtsbuilding.rtsbuilding.forgecompat.network.RegistryFriendlyByteBuf;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * 范围剔除网络快照的有界编解码器。
 *
 * <p>它只负责纯数据边界，Forge SimpleChannel 的注册留在薄适配层。</p>
 */
final class RtsCullingPayloadCodec {
    static final int MAX_BOXES = 128;
    static final int MAX_REVEALED_BLOCKS = 4096;

    private RtsCullingPayloadCodec() {
    }

    static void write(RegistryFriendlyByteBuf buf, List<RtsCullingBoxSnapshot> boxes, List<BlockPos> revealed) {
        List<RtsCullingBoxSnapshot> safeBoxes = boxes == null ? List.of() : boxes;
        int boxCount = Math.min(MAX_BOXES, safeBoxes.size());
        buf.writeVarInt(boxCount);
        for (int index = 0; index < boxCount; index++) {
            RtsCullingBoxSnapshot box = safeBoxes.get(index);
            buf.writeBlockPos(box.min());
            buf.writeBlockPos(box.max());
        }

        List<BlockPos> safeRevealed = revealed == null ? List.of() : revealed;
        int revealedCount = Math.min(MAX_REVEALED_BLOCKS, safeRevealed.size());
        buf.writeVarInt(revealedCount);
        for (int index = 0; index < revealedCount; index++) {
            buf.writeBlockPos(safeRevealed.get(index));
        }
    }

    static Decoded read(RegistryFriendlyByteBuf buf) {
        int boxCount = readBoundedCount(buf, MAX_BOXES, "range-culling boxes");
        List<RtsCullingBoxSnapshot> boxes = new ArrayList<>(boxCount);
        for (int index = 0; index < boxCount; index++) {
            boxes.add(new RtsCullingBoxSnapshot(buf.readBlockPos(), buf.readBlockPos()));
        }
        int revealedCount = readBoundedCount(buf, MAX_REVEALED_BLOCKS, "range-culling revealed blocks");
        List<BlockPos> revealed = new ArrayList<>(revealedCount);
        for (int index = 0; index < revealedCount; index++) {
            revealed.add(buf.readBlockPos());
        }
        return new Decoded(List.copyOf(boxes), List.copyOf(revealed));
    }

    private static int readBoundedCount(RegistryFriendlyByteBuf buf, int maximum, String label) {
        int count = buf.readVarInt();
        if (count < 0 || count > maximum) {
            throw new IllegalArgumentException(label + " count out of bounds: " + count);
        }
        return count;
    }

    record Decoded(List<RtsCullingBoxSnapshot> boxes, List<BlockPos> revealed) {
    }
}
