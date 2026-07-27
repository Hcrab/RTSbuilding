package com.rtsbuilding.rtsbuilding.network.storage;

import com.rtsbuilding.rtsbuilding.network.RtsPacketBuffer;
import io.netty.buffer.ByteBuf;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

/** 设置或清空一个远程 GUI 绑定；维度归属与距离仍由服务端会话校验。 */
public final class C2SRtsSetGuiBindingPayload implements IMessage {
    public static final int SLOT_COUNT = 8;
    public static final int MAX_ITEM_ID_CHARS = 128;
    private byte slot;
    private boolean clear;
    private BlockPos pos = BlockPos.ORIGIN;
    private byte faceId = -1;
    private String itemIdHint = "";
    public C2SRtsSetGuiBindingPayload() {}
    public C2SRtsSetGuiBindingPayload(byte slot, boolean clear, BlockPos pos, byte faceId, String itemIdHint) {
        this.slot = slot;
        this.clear = clear;
        this.pos = pos == null ? BlockPos.ORIGIN : pos;
        this.faceId = faceId;
        this.itemIdHint = itemIdHint == null ? "" : itemIdHint;
    }
    public byte slot() { return this.slot; }
    public boolean clear() { return this.clear; }
    public BlockPos pos() { return this.pos; }
    public byte faceId() { return this.faceId; }
    public String itemIdHint() { return this.itemIdHint; }
    public EnumFacing face() {
        return this.faceId >= 0 && this.faceId < EnumFacing.values().length
                ? EnumFacing.values()[this.faceId] : null;
    }
    public boolean isValid() {
        return this.slot >= 0 && this.slot < SLOT_COUNT
                && this.itemIdHint.length() <= MAX_ITEM_ID_CHARS
                && (this.clear || face() != null);
    }
    @Override public void fromBytes(ByteBuf buffer) {
        this.slot = buffer.readByte();
        this.clear = buffer.readBoolean();
        this.pos = BlockPos.fromLong(buffer.readLong());
        this.faceId = buffer.readByte();
        this.itemIdHint = RtsPacketBuffer.readString(buffer, MAX_ITEM_ID_CHARS, "GUI binding item id");
    }
    @Override public void toBytes(ByteBuf buffer) {
        if (!isValid()) throw new IllegalArgumentException("invalid GUI binding");
        buffer.writeByte(this.slot);
        buffer.writeBoolean(this.clear);
        buffer.writeLong(this.pos.toLong());
        buffer.writeByte(this.faceId);
        RtsPacketBuffer.writeString(buffer, this.itemIdHint, MAX_ITEM_ID_CHARS, "GUI binding item id");
    }
}
