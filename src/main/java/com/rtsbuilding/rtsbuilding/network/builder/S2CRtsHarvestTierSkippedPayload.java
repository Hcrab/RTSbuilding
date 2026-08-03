package com.rtsbuilding.rtsbuilding.network.builder;

import com.rtsbuilding.rtsbuilding.network.RtsPacketBuffer;
import io.netty.buffer.ByteBuf;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 回传因采掘等级不足而从范围破坏预览中剔除的坐标。 */
public final class S2CRtsHarvestTierSkippedPayload implements IMessage {
    public static final int MAX_POSITIONS = C2SRtsAreaDestroyPayload.MAX_POSITIONS_PER_PACKET;
    private List<BlockPos> positions = Collections.emptyList();
    public S2CRtsHarvestTierSkippedPayload() {}
    public S2CRtsHarvestTierSkippedPayload(List<BlockPos> positions) {
        this.positions = immutableBounded(positions);
    }
    public List<BlockPos> positions() { return this.positions; }
    @Override public void fromBytes(ByteBuf buffer) {
        int size = RtsPacketBuffer.readBoundedCount(buffer, MAX_POSITIONS, "harvest-tier skipped positions");
        List<BlockPos> decoded = new ArrayList<BlockPos>(size);
        for (int i = 0; i < size; i++) decoded.add(BlockPos.fromLong(buffer.readLong()));
        this.positions = Collections.unmodifiableList(decoded);
    }
    @Override public void toBytes(ByteBuf buffer) {
        List<BlockPos> safe = immutableBounded(this.positions);
        RtsPacketBuffer.writeVarInt(buffer, safe.size());
        for (BlockPos pos : safe) buffer.writeLong(pos.toLong());
    }
    private static List<BlockPos> immutableBounded(List<BlockPos> values) {
        if (values == null || values.isEmpty()) return Collections.emptyList();
        int size = Math.min(values.size(), MAX_POSITIONS);
        List<BlockPos> copy = new ArrayList<BlockPos>(size);
        for (int i = 0; i < size; i++) {
            BlockPos pos = values.get(i);
            if (pos != null) copy.add(pos);
        }
        return Collections.unmodifiableList(copy);
    }
}
