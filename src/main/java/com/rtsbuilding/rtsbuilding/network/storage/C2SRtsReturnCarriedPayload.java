package com.rtsbuilding.rtsbuilding.network.storage;

import com.rtsbuilding.rtsbuilding.network.RtsPacketBuffer;
import io.netty.buffer.ByteBuf;
import cpw.mods.fml.common.network.simpleimpl.IMessage;

/** 将当前服务端容器鼠标栈的一部分退回链接储存。 */
public final class C2SRtsReturnCarriedPayload implements IMessage {
    public static final int MAX_ITEM_ID_CHARS = 128;
    private String itemId = "";
    private int amount;
    public C2SRtsReturnCarriedPayload() {}
    public C2SRtsReturnCarriedPayload(String itemId, int amount) {
        this.itemId = itemId == null ? "" : itemId;
        this.amount = amount;
    }
    public String itemId() { return this.itemId; }
    public int amount() { return this.amount; }
    public boolean isValid() {
        return !this.itemId.trim().isEmpty() && this.itemId.length() <= MAX_ITEM_ID_CHARS && this.amount > 0;
    }
    @Override public void fromBytes(ByteBuf buffer) {
        this.itemId = RtsPacketBuffer.readString(buffer, MAX_ITEM_ID_CHARS, "carried item id");
        this.amount = RtsPacketBuffer.readBoundedCount(buffer, Integer.MAX_VALUE, "carried amount");
    }
    @Override public void toBytes(ByteBuf buffer) {
        if (!isValid()) throw new IllegalArgumentException("invalid carried return request");
        RtsPacketBuffer.writeString(buffer, this.itemId, MAX_ITEM_ID_CHARS, "carried item id");
        RtsPacketBuffer.writeVarInt(buffer, this.amount);
    }
}
