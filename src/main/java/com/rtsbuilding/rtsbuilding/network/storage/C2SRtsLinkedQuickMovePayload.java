package com.rtsbuilding.rtsbuilding.network.storage;

import com.rtsbuilding.rtsbuilding.network.RtsPacketBuffer;
import io.netty.buffer.ByteBuf;
import net.minecraft.item.ItemStack;
import cpw.mods.fml.common.network.simpleimpl.IMessage;

/** 以完整原型匹配链接储存并快速移动；实际提取栈和余量由服务端服务持有。 */
public final class C2SRtsLinkedQuickMovePayload implements IMessage {
    private ItemStack prototype = null;
    public C2SRtsLinkedQuickMovePayload() {}
    public C2SRtsLinkedQuickMovePayload(ItemStack prototype) { this.prototype = copy(prototype); }
    public ItemStack prototype() { return this.prototype; }
    public boolean isValid() { return !com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(this.prototype); }
    @Override public void fromBytes(ByteBuf buffer) { this.prototype = RtsPacketBuffer.readItemStack(buffer); }
    @Override public void toBytes(ByteBuf buffer) {
        if (!isValid()) throw new IllegalArgumentException("invalid linked quick-move prototype");
        RtsPacketBuffer.writeItemStack(buffer, this.prototype);
    }
    private static ItemStack copy(ItemStack stack) { return stack == null || com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(stack) ? null : stack.copy(); }
}
