package com.rtsbuilding.rtsbuilding.network.storage;
import io.netty.buffer.ByteBuf;
import com.rtsbuilding.rtsbuilding.platform.math.BlockPos;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
public final class C2SRtsUnlinkStoragePayload implements IMessage {
    private BlockPos pos;
    public C2SRtsUnlinkStoragePayload() { }
    public C2SRtsUnlinkStoragePayload(BlockPos pos){this.pos=pos;}
    public BlockPos pos(){return pos;}
    @Override public void fromBytes(ByteBuf b){pos=BlockPos.fromLong(b.readLong());}
    @Override public void toBytes(ByteBuf b){if(pos==null)throw new IllegalArgumentException("storage pos");b.writeLong(pos.toLong());}
    public boolean isValid(){return pos!=null;}
}
