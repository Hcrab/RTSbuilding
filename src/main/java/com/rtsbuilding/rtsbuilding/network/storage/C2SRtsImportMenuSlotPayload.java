package com.rtsbuilding.rtsbuilding.network.storage;
import com.rtsbuilding.rtsbuilding.network.RtsPacketBuffer;
import io.netty.buffer.ByteBuf;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
/** 导入当前服务端容器中的槽位；handler 会再次按当前 window/menu 校验。 */
public final class C2SRtsImportMenuSlotPayload implements IMessage {
    public static final int MAX_MENU_SLOT = 4095;
    private int menuSlot;
    public C2SRtsImportMenuSlotPayload() {}
    public C2SRtsImportMenuSlotPayload(int menuSlot) { this.menuSlot = menuSlot; }
    public int menuSlot() { return this.menuSlot; }
    public boolean isValid() { return this.menuSlot >= 0 && this.menuSlot <= MAX_MENU_SLOT; }
    @Override public void fromBytes(ByteBuf buffer) {
        this.menuSlot = RtsPacketBuffer.readBoundedCount(buffer, MAX_MENU_SLOT, "menu slot");
    }
    @Override public void toBytes(ByteBuf buffer) {
        if (!isValid()) throw new IllegalArgumentException("menu slot out of range: " + this.menuSlot);
        RtsPacketBuffer.writeVarInt(buffer, this.menuSlot);
    }
}
