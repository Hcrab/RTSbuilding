package com.rtsbuilding.rtsbuilding.network.storage;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
/** 将玩家自己的指定快捷栏槽位存入链接储存。 */
public final class C2SRtsStoreHotbarSlotPayload implements IMessage {
    private byte slot;
    public C2SRtsStoreHotbarSlotPayload() {}
    public C2SRtsStoreHotbarSlotPayload(byte slot) { this.slot = slot; }
    public byte slot() { return this.slot; }
    public boolean isValid() { return this.slot >= 0 && this.slot < 9; }
    @Override public void fromBytes(ByteBuf buffer) { this.slot = buffer.readByte(); }
    @Override public void toBytes(ByteBuf buffer) { buffer.writeByte(this.slot); }
}
