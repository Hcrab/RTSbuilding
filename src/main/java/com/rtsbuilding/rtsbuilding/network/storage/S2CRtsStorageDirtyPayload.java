package com.rtsbuilding.rtsbuilding.network.storage;
import io.netty.buffer.ByteBuf;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
public final class S2CRtsStorageDirtyPayload implements IMessage {
    private boolean dirty;
    public S2CRtsStorageDirtyPayload() {}
    public S2CRtsStorageDirtyPayload(boolean dirty){this.dirty=dirty;}
    @Override public void fromBytes(ByteBuf b){dirty=b.readBoolean();}
    @Override public void toBytes(ByteBuf b){b.writeBoolean(dirty);}
    public boolean dirty(){return dirty;}
}
