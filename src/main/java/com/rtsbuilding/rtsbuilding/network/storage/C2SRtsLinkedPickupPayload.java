package com.rtsbuilding.rtsbuilding.network.storage;

import com.rtsbuilding.rtsbuilding.network.RtsPacketBuffer;
import io.netty.buffer.ByteBuf;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

/** 从链接储存提取与完整原型（含 metadata/NBT）匹配的物品到鼠标。 */
public final class C2SRtsLinkedPickupPayload implements IMessage {
    private ItemStack prototype = ItemStack.EMPTY;
    private int amount;
    public C2SRtsLinkedPickupPayload() {}
    public C2SRtsLinkedPickupPayload(ItemStack prototype, int amount) {
        this.prototype = copy(prototype);
        this.amount = amount;
    }
    public ItemStack prototype() { return this.prototype; }
    public int amount() { return this.amount; }
    public boolean isValid() { return !this.prototype.isEmpty() && this.amount > 0; }
    @Override public void fromBytes(ByteBuf buffer) {
        this.prototype = RtsPacketBuffer.readItemStack(buffer);
        this.amount = RtsPacketBuffer.readBoundedCount(buffer, Integer.MAX_VALUE, "pickup amount");
    }
    @Override public void toBytes(ByteBuf buffer) {
        if (!isValid()) throw new IllegalArgumentException("invalid linked pickup request");
        RtsPacketBuffer.writeItemStack(buffer, this.prototype);
        RtsPacketBuffer.writeVarInt(buffer, this.amount);
    }
    private static ItemStack copy(ItemStack stack) { return stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack.copy(); }
}
