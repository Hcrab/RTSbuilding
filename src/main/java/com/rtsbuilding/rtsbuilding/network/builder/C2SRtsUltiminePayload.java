package com.rtsbuilding.rtsbuilding.network.builder;

import com.rtsbuilding.rtsbuilding.network.RtsPacketBuffer;
import com.rtsbuilding.rtsbuilding.network.RtsTracedPayload;
import io.netty.buffer.ByteBuf;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

/** 连锁挖掘请求及其客户端因果身份。 */
public final class C2SRtsUltiminePayload implements IMessage, RtsTracedPayload {
    private BlockPos pos;
    private byte face;
    private byte toolSlot;
    private byte mode;
    private String toolItemId = "";
    private ItemStack toolPrototype = ItemStack.EMPTY;
    private short limit;
    private boolean toolProtectionEnabled;
    private long traceId;
    private int sequence;
    private long clientTick = -1L;
    private byte inputKind;

    public C2SRtsUltiminePayload() {
    }

    public C2SRtsUltiminePayload(BlockPos pos, byte face, byte toolSlot, String toolItemId,
            ItemStack toolPrototype, short limit, byte mode, boolean toolProtectionEnabled) {
        this(pos, face, toolSlot, toolItemId, toolPrototype, limit, mode,
                toolProtectionEnabled, 0L, 0, -1L, (byte) 0);
    }

    public C2SRtsUltiminePayload(BlockPos pos, byte face, byte toolSlot, String toolItemId,
            ItemStack toolPrototype, short limit, byte mode, boolean toolProtectionEnabled,
            long traceId, int sequence, long clientTick, byte inputKind) {
        this.pos = pos;
        this.face = face;
        this.toolSlot = toolSlot;
        this.toolItemId = toolItemId == null ? "" : toolItemId;
        this.toolPrototype = toolPrototype == null ? ItemStack.EMPTY : toolPrototype.copy();
        this.limit = limit;
        this.mode = mode;
        this.toolProtectionEnabled = toolProtectionEnabled;
        this.traceId = traceId;
        this.sequence = Math.max(0, sequence);
        this.clientTick = clientTick;
        this.inputKind = inputKind;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        pos = BlockPos.fromLong(buffer.readLong());
        face = buffer.readByte();
        toolSlot = buffer.readByte();
        toolItemId = RtsPacketBuffer.readString(buffer, 256, "tool id");
        toolPrototype = RtsPacketBuffer.readItemStack(buffer);
        limit = buffer.readShort();
        mode = buffer.readByte();
        toolProtectionEnabled = buffer.readBoolean();
        traceId = buffer.readLong();
        sequence = RtsPacketBuffer.readVarInt(buffer);
        clientTick = buffer.readLong();
        inputKind = buffer.readByte();
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        if (pos == null) throw new IllegalArgumentException("ultimine pos");
        buffer.writeLong(pos.toLong());
        buffer.writeByte(face);
        buffer.writeByte(toolSlot);
        RtsPacketBuffer.writeString(buffer, toolItemId, 256, "tool id");
        RtsPacketBuffer.writeItemStack(buffer, toolPrototype);
        buffer.writeShort(limit);
        buffer.writeByte(mode);
        buffer.writeBoolean(toolProtectionEnabled);
        buffer.writeLong(traceId);
        RtsPacketBuffer.writeVarInt(buffer, sequence);
        buffer.writeLong(clientTick);
        buffer.writeByte(inputKind);
    }

    public boolean isValid() {
        return pos != null && face >= 0 && face < EnumFacing.values().length
                && toolSlot >= 0 && toolSlot <= 8 && limit >= 1 && limit <= 256 && mode >= 0
                && toolItemId != null && toolItemId.length() <= 256 && sequence >= 0;
    }

    public BlockPos pos() { return pos; }
    public byte face() { return face; }
    public byte toolSlot() { return toolSlot; }
    public String toolItemId() { return toolItemId; }
    public ItemStack toolPrototype() { return toolPrototype; }
    public short limit() { return limit; }
    public byte mode() { return mode; }
    public boolean toolProtectionEnabled() { return toolProtectionEnabled; }
    @Override public long traceId() { return traceId; }
    public int sequence() { return sequence; }
    public long clientTick() { return clientTick; }
    public byte inputKind() { return inputKind; }
}
