package com.rtsbuilding.rtsbuilding.network.builder;

import com.rtsbuilding.rtsbuilding.network.RtsPacketBuffer;
import com.rtsbuilding.rtsbuilding.network.RtsProtocolLimits;
import io.netty.buffer.ByteBuf;
import net.minecraft.item.ItemStack;
import com.rtsbuilding.rtsbuilding.platform.math.BlockPos;
import cpw.mods.fml.common.network.simpleimpl.IMessage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 允许大范围破坏、但保证每个 Forge 1.12 自定义包低于 32 KiB 的分片消息。 */
public final class C2SRtsAreaDestroyPayload implements IMessage {
    public static final int MAX_POSITIONS = RtsProtocolLimits.AREA_DESTROY_MAX_POSITIONS;
    public static final int MAX_POSITIONS_PER_PACKET = 2048;

    private int submissionId;
    private int chunkIndex;
    private int chunkCount = 1;
    private int totalPositions;
    private List<BlockPos> positions = Collections.emptyList();
    private byte toolSlot;
    private String toolItemId = "";
    private ItemStack toolPrototype = null;
    private boolean toolProtectionEnabled;

    public C2SRtsAreaDestroyPayload() {
    }

    public C2SRtsAreaDestroyPayload(List<BlockPos> positions, byte toolSlot, String toolItemId,
            ItemStack toolPrototype, boolean toolProtectionEnabled) {
        this(0, 0, 1, positions == null ? 0 : positions.size(), positions,
                toolSlot, toolItemId, toolPrototype, toolProtectionEnabled);
    }

    public C2SRtsAreaDestroyPayload(int submissionId, int chunkIndex, int chunkCount,
            int totalPositions, List<BlockPos> positions, byte toolSlot, String toolItemId,
            ItemStack toolPrototype, boolean toolProtectionEnabled) {
        this.submissionId = submissionId;
        this.chunkIndex = chunkIndex;
        this.chunkCount = chunkCount;
        this.totalPositions = totalPositions;
        this.positions = immutableCopy(positions);
        this.toolSlot = toolSlot;
        this.toolItemId = toolItemId == null ? "" : toolItemId;
        this.toolPrototype = toolPrototype == null ? null : toolPrototype.copy();
        this.toolProtectionEnabled = toolProtectionEnabled;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        submissionId = buffer.readInt();
        chunkIndex = RtsPacketBuffer.readVarInt(buffer);
        chunkCount = RtsPacketBuffer.readVarInt(buffer);
        totalPositions = RtsPacketBuffer.readVarInt(buffer);
        int size = RtsPacketBuffer.readBoundedCount(
                buffer, MAX_POSITIONS_PER_PACKET, "destroy positions chunk");
        List<BlockPos> decoded = new ArrayList<BlockPos>(size);
        for (int i = 0; i < size; i++) decoded.add(BlockPos.fromLong(buffer.readLong()));
        positions = Collections.unmodifiableList(decoded);
        toolSlot = buffer.readByte();
        toolItemId = RtsPacketBuffer.readString(buffer, 256, "tool id");
        toolPrototype = RtsPacketBuffer.readItemStack(buffer);
        toolProtectionEnabled = buffer.readBoolean();
        if (!isValid()) throw new IllegalArgumentException("invalid area destroy chunk");
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        if (!isValid()) throw new IllegalArgumentException("invalid area destroy chunk");
        buffer.writeInt(submissionId);
        RtsPacketBuffer.writeVarInt(buffer, chunkIndex);
        RtsPacketBuffer.writeVarInt(buffer, chunkCount);
        RtsPacketBuffer.writeVarInt(buffer, totalPositions);
        RtsPacketBuffer.writeVarInt(buffer, positions.size());
        for (BlockPos pos : positions) buffer.writeLong(pos.toLong());
        buffer.writeByte(toolSlot);
        RtsPacketBuffer.writeString(buffer, toolItemId, 256, "tool id");
        RtsPacketBuffer.writeItemStack(buffer, toolPrototype);
        buffer.writeBoolean(toolProtectionEnabled);
    }

    public boolean isValid() {
        if (!validChunkShape(totalPositions, chunkIndex, chunkCount, positions)
                || totalPositions > MAX_POSITIONS || toolSlot < 0 || toolSlot > 8
                || toolItemId == null || toolItemId.length() > 256) return false;
        for (BlockPos pos : positions) if (pos == null) return false;
        return true;
    }

    private static boolean validChunkShape(int total, int index, int count, List<BlockPos> values) {
        if (values == null || values.isEmpty() || values.size() > MAX_POSITIONS_PER_PACKET
                || total <= 0 || index < 0 || count <= 0 || index >= count) return false;
        int expectedCount = (total + MAX_POSITIONS_PER_PACKET - 1) / MAX_POSITIONS_PER_PACKET;
        if (count != expectedCount) return false;
        int expectedSize = index == count - 1
                ? total - index * MAX_POSITIONS_PER_PACKET : MAX_POSITIONS_PER_PACKET;
        return values.size() == expectedSize;
    }

    private static List<BlockPos> immutableCopy(List<BlockPos> values) {
        return values == null ? Collections.<BlockPos>emptyList()
                : Collections.unmodifiableList(new ArrayList<BlockPos>(values));
    }

    public int submissionId() { return submissionId; }
    public int chunkIndex() { return chunkIndex; }
    public int chunkCount() { return chunkCount; }
    public int totalPositions() { return totalPositions; }
    public List<BlockPos> positions() { return positions; }
    public byte toolSlot() { return toolSlot; }
    public String toolItemId() { return toolItemId; }
    public ItemStack toolPrototype() { return toolPrototype; }
    public boolean toolProtectionEnabled() { return toolProtectionEnabled; }

    public String metadataSignature() {
        return toolSlot + "|" + toolItemId + "|" + toolProtectionEnabled + "|"
                + String.valueOf(toolPrototype) + "|"
                + String.valueOf(toolPrototype == null ? null : toolPrototype.getTagCompound());
    }
}
