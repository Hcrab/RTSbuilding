package com.rtsbuilding.rtsbuilding.network.storage;
import io.netty.buffer.ByteBuf;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
/** 切换当前玩家会话的 BD 网络来源。 */
public final class C2SRtsSetBdNetworkPayload implements IMessage {
    private boolean enabled;
    public C2SRtsSetBdNetworkPayload() {}
    public C2SRtsSetBdNetworkPayload(boolean enabled) { this.enabled = enabled; }
    public boolean enabled() { return this.enabled; }
    @Override public void fromBytes(ByteBuf buffer) { this.enabled = buffer.readBoolean(); }
    @Override public void toBytes(ByteBuf buffer) { buffer.writeBoolean(this.enabled); }
}
