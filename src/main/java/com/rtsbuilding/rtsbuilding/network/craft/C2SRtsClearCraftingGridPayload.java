package com.rtsbuilding.rtsbuilding.network.craft;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

/** 清空合成格；true 优先退回背包，false 优先送入链接储存。 */
public final class C2SRtsClearCraftingGridPayload implements IMessage {
    private boolean toPlayerInventory;
    public C2SRtsClearCraftingGridPayload() {}
    public C2SRtsClearCraftingGridPayload(boolean toPlayerInventory) {
        this.toPlayerInventory = toPlayerInventory;
    }
    public boolean toPlayerInventory() { return toPlayerInventory; }
    @Override public void fromBytes(ByteBuf buffer) { toPlayerInventory = buffer.readBoolean(); }
    @Override public void toBytes(ByteBuf buffer) { buffer.writeBoolean(toPlayerInventory); }
}
