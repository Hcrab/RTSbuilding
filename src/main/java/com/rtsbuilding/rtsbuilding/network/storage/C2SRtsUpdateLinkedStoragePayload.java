package com.rtsbuilding.rtsbuilding.network.storage;
import com.rtsbuilding.rtsbuilding.network.RtsPacketBuffer;
import io.netty.buffer.ByteBuf;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
public final class C2SRtsUpdateLinkedStoragePayload implements IMessage {
    public static final int MIN_PRIORITY=-9999, MAX_PRIORITY=9999;
    private int dimension; private BlockPos pos; private byte linkMode; private int priority;
    public C2SRtsUpdateLinkedStoragePayload() { }
    public C2SRtsUpdateLinkedStoragePayload(BlockPos pos,byte linkMode,int priority){this(Integer.MIN_VALUE,pos,linkMode,priority);}
    public C2SRtsUpdateLinkedStoragePayload(int dimension,BlockPos pos,byte linkMode,int priority){this.dimension=dimension;this.pos=pos;this.linkMode=linkMode;this.priority=priority;}
    public int dimension(){return dimension;} public BlockPos pos(){return pos;} public byte linkMode(){return linkMode;} public int priority(){return priority;}
    @Override public void fromBytes(ByteBuf b){dimension=b.readInt();pos=BlockPos.fromLong(b.readLong());linkMode=b.readByte();priority=RtsPacketBuffer.readVarInt(b);}
    @Override public void toBytes(ByteBuf b){if(pos==null)throw new IllegalArgumentException("storage pos");b.writeInt(dimension);b.writeLong(pos.toLong());b.writeByte(linkMode);RtsPacketBuffer.writeVarInt(b,priority);}
    public boolean isValid(){return pos!=null&&(linkMode==0||linkMode==1)&&priority>=MIN_PRIORITY&&priority<=MAX_PRIORITY;}
}
