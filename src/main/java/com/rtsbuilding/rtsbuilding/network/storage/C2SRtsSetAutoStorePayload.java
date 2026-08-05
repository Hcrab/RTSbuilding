package com.rtsbuilding.rtsbuilding.network.storage;
import io.netty.buffer.ByteBuf;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
public final class C2SRtsSetAutoStorePayload implements IMessage {
    private boolean enabled;
    public C2SRtsSetAutoStorePayload() { }
    public C2SRtsSetAutoStorePayload(boolean enabled){this.enabled=enabled;}
    public boolean enabled(){return enabled;}
    @Override public void fromBytes(ByteBuf b){enabled=b.readBoolean();}
    @Override public void toBytes(ByteBuf b){b.writeBoolean(enabled);}
}
