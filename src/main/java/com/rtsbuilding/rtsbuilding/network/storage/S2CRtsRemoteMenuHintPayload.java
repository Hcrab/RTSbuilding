package com.rtsbuilding.rtsbuilding.network.storage;
import com.rtsbuilding.rtsbuilding.network.RtsTracedPayload;
import io.netty.buffer.ByteBuf;
import com.rtsbuilding.rtsbuilding.platform.math.BlockPos;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
public final class S2CRtsRemoteMenuHintPayload implements IMessage, RtsTracedPayload {
    private long traceId;
    private BlockPos pos;
    public S2CRtsRemoteMenuHintPayload() {}
    public S2CRtsRemoteMenuHintPayload(BlockPos pos){this(0L,pos);}
    public S2CRtsRemoteMenuHintPayload(long traceId,BlockPos pos){this.traceId=traceId;this.pos=pos;}
    @Override public void fromBytes(ByteBuf b){traceId=b.readLong();pos=BlockPos.fromLong(b.readLong());}
    @Override public void toBytes(ByteBuf b){if(traceId<0L||pos==null)throw new IllegalArgumentException("remote menu hint");b.writeLong(traceId);b.writeLong(pos.toLong());}
    @Override public long traceId(){return traceId;}
    public BlockPos pos(){return pos;}
}
