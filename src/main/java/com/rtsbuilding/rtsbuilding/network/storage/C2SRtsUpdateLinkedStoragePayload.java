package com.rtsbuilding.rtsbuilding.network.storage;
import com.rtsbuilding.rtsbuilding.network.RtsPacketBuffer;
import io.netty.buffer.ByteBuf;
import com.rtsbuilding.rtsbuilding.platform.math.BlockPos;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
public final class C2SRtsUpdateLinkedStoragePayload implements IMessage {
    public static final int MIN_PRIORITY=-9999, MAX_PRIORITY=9999;
    private BlockPos pos; private byte linkMode; private int priority;
    public C2SRtsUpdateLinkedStoragePayload() { }
    public C2SRtsUpdateLinkedStoragePayload(BlockPos pos,byte linkMode,int priority){this.pos=pos;this.linkMode=linkMode;this.priority=priority;}
    public BlockPos pos(){return pos;} public byte linkMode(){return linkMode;} public int priority(){return priority;}
    @Override public void fromBytes(ByteBuf b){pos=BlockPos.fromLong(b.readLong());linkMode=b.readByte();priority=RtsPacketBuffer.readVarInt(b);}
    @Override public void toBytes(ByteBuf b){if(pos==null)throw new IllegalArgumentException("storage pos");b.writeLong(pos.toLong());b.writeByte(linkMode);RtsPacketBuffer.writeVarInt(b,priority);}
    public boolean isValid(){return pos!=null&&(linkMode==0||linkMode==1)&&priority>=MIN_PRIORITY&&priority<=MAX_PRIORITY;}
}
