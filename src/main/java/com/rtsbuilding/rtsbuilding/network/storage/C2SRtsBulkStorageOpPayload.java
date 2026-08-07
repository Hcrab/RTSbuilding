package com.rtsbuilding.rtsbuilding.network.storage;

import com.rtsbuilding.rtsbuilding.network.RtsPacketBuffer;
import io.netty.buffer.ByteBuf;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

/** 合成终端批量存取请求；物品原型只用于精确筛选，不是物品来源。 */
public final class C2SRtsBulkStorageOpPayload implements IMessage {
    public static final byte WITHDRAW = 0;
    public static final byte DEPOSIT_INVENTORY = 1;
    public static final byte DEPOSIT_HOTBAR = 2;
    public static final byte DEPOSIT_ALL = 3;

    private byte action;
    private ItemStack prototype = ItemStack.EMPTY;
    private int amount;

    public C2SRtsBulkStorageOpPayload() {
    }

    public C2SRtsBulkStorageOpPayload(byte action, ItemStack prototype, int amount) {
        this.action = action;
        this.prototype = prototype == null ? ItemStack.EMPTY : prototype.copy();
        if (!this.prototype.isEmpty()) this.prototype.setCount(1);
        this.amount = Math.max(0, amount);
    }

    @Override public void fromBytes(ByteBuf buffer) {
        action = buffer.readByte();
        prototype = RtsPacketBuffer.readItemStack(buffer);
        if (!prototype.isEmpty()) prototype.setCount(1);
        amount = RtsPacketBuffer.readVarInt(buffer);
        if (!isValid()) throw new IllegalArgumentException("invalid bulk storage operation");
    }

    @Override public void toBytes(ByteBuf buffer) {
        if (!isValid()) throw new IllegalArgumentException("invalid bulk storage operation");
        buffer.writeByte(action);
        RtsPacketBuffer.writeItemStack(buffer, prototype);
        RtsPacketBuffer.writeVarInt(buffer, amount);
    }

    public boolean isValid() {
        if (action < WITHDRAW || action > DEPOSIT_ALL || amount < 0) return false;
        return action != WITHDRAW || (!prototype.isEmpty() && amount > 0);
    }

    public byte action() { return action; }
    public ItemStack prototype() { return prototype; }
    public int amount() { return amount; }
}
