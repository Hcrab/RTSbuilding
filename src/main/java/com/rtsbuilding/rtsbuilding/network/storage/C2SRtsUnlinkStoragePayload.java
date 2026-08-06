package com.rtsbuilding.rtsbuilding.network.storage;
import io.netty.buffer.ByteBuf;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
public final class C2SRtsUnlinkStoragePayload implements IMessage {
    private int dimension; private BlockPos pos;
    public C2SRtsUnlinkStoragePayload() { }
    public C2SRtsUnlinkStoragePayload(BlockPos pos){this(Integer.MIN_VALUE,pos);}
    public C2SRtsUnlinkStoragePayload(int dimension,BlockPos pos){this.dimension=dimension;this.pos=pos;}
    public int dimension(){return dimension;} public BlockPos pos(){return pos;}
    @Override public void fromBytes(ByteBuf b){dimension=b.readInt();pos=BlockPos.fromLong(b.readLong());}
    @Override public void toBytes(ByteBuf b){if(pos==null)throw new IllegalArgumentException("storage pos");b.writeInt(dimension);b.writeLong(pos.toLong());}
    public boolean isValid(){return pos!=null;}
}
