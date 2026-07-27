package com.rtsbuilding.rtsbuilding.network.plugin;

import com.rtsbuilding.rtsbuilding.network.RtsPacketBuffer;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

public final class C2SRtsInstallPluginPayload implements IMessage {
    private int inventorySlot;

    public C2SRtsInstallPluginPayload() {
    }

    public C2SRtsInstallPluginPayload(int inventorySlot) {
        this.inventorySlot = inventorySlot;
    }

    public int inventorySlot() { return inventorySlot; }
    public boolean isValid() { return inventorySlot >= 0 && inventorySlot < 36; }

    @Override public void fromBytes(ByteBuf buffer) {
        inventorySlot = RtsPacketBuffer.readVarInt(buffer);
    }

    @Override public void toBytes(ByteBuf buffer) {
        if (!isValid()) throw new IllegalArgumentException("plugin inventory slot out of range");
        RtsPacketBuffer.writeVarInt(buffer, inventorySlot);
    }
}
