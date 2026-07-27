package com.rtsbuilding.rtsbuilding.network.storage;

import com.rtsbuilding.rtsbuilding.network.RtsPacketBuffer;
import io.netty.buffer.ByteBuf;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

/** 设置或清空一个快捷物品槽；预览栈完整保留 metadata/NBT，但线格式固定为一个物品。 */
public final class C2SRtsSetQuickSlotPayload implements IMessage {
    public static final int SLOT_COUNT = 27;
    public static final int MAX_ITEM_ID_CHARS = 128;
    private byte slot;
    private String itemId = "";
    private ItemStack previewStack = ItemStack.EMPTY;
    public C2SRtsSetQuickSlotPayload() {}
    public C2SRtsSetQuickSlotPayload(byte slot, String itemId, ItemStack previewStack) {
        this.slot = slot;
        this.itemId = itemId == null ? "" : itemId;
        this.previewStack = one(previewStack);
    }
    public byte slot() { return this.slot; }
    public String itemId() { return this.itemId; }
    public ItemStack previewStack() { return this.previewStack; }
    public boolean isValid() {
        return this.slot >= 0 && this.slot < SLOT_COUNT && this.itemId.length() <= MAX_ITEM_ID_CHARS;
    }
    @Override public void fromBytes(ByteBuf buffer) {
        this.slot = buffer.readByte();
        this.itemId = RtsPacketBuffer.readString(buffer, MAX_ITEM_ID_CHARS, "quick-slot item id");
        this.previewStack = buffer.readBoolean() ? one(RtsPacketBuffer.readItemStack(buffer)) : ItemStack.EMPTY;
    }
    @Override public void toBytes(ByteBuf buffer) {
        if (!isValid()) throw new IllegalArgumentException("invalid quick-slot binding");
        buffer.writeByte(this.slot);
        RtsPacketBuffer.writeString(buffer, this.itemId, MAX_ITEM_ID_CHARS, "quick-slot item id");
        ItemStack preview = one(this.previewStack);
        buffer.writeBoolean(!preview.isEmpty());
        if (!preview.isEmpty()) RtsPacketBuffer.writeItemStack(buffer, preview);
    }
    private static ItemStack one(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return ItemStack.EMPTY;
        ItemStack copy = stack.copy();
        copy.setCount(1);
        return copy;
    }
}
