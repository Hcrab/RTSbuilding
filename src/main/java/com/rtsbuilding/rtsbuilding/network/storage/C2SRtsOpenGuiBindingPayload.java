package com.rtsbuilding.rtsbuilding.network.storage;
import com.rtsbuilding.rtsbuilding.network.RtsTracedPayload;
import io.netty.buffer.ByteBuf;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
/** 打开当前玩家的指定 GUI 绑定槽位。 */
public final class C2SRtsOpenGuiBindingPayload implements IMessage, RtsTracedPayload {
    public static final int SLOT_COUNT = 8;
    private long traceId;
    private byte slot;
    public C2SRtsOpenGuiBindingPayload() {}
    public C2SRtsOpenGuiBindingPayload(byte slot) { this(0L, slot); }
    public C2SRtsOpenGuiBindingPayload(long traceId, byte slot) { this.traceId = traceId; this.slot = slot; }
    @Override public long traceId() { return this.traceId; }
    public byte slot() { return this.slot; }
    public boolean isValid() { return this.traceId >= 0L && this.slot >= 0 && this.slot < SLOT_COUNT; }
    @Override public void fromBytes(ByteBuf buffer) { this.traceId = buffer.readLong(); this.slot = buffer.readByte(); }
    @Override public void toBytes(ByteBuf buffer) {
        if (!isValid()) throw new IllegalArgumentException("invalid GUI binding open");
        buffer.writeLong(this.traceId);
        buffer.writeByte(this.slot);
    }
}
