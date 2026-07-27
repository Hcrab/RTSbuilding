package com.rtsbuilding.rtsbuilding.network.storage;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
/** 打开当前玩家的指定 GUI 绑定槽位。 */
public final class C2SRtsOpenGuiBindingPayload implements IMessage {
    public static final int SLOT_COUNT = 8;
    private byte slot;
    public C2SRtsOpenGuiBindingPayload() {}
    public C2SRtsOpenGuiBindingPayload(byte slot) { this.slot = slot; }
    public byte slot() { return this.slot; }
    public boolean isValid() { return this.slot >= 0 && this.slot < SLOT_COUNT; }
    @Override public void fromBytes(ByteBuf buffer) { this.slot = buffer.readByte(); }
    @Override public void toBytes(ByteBuf buffer) { buffer.writeByte(this.slot); }
}
