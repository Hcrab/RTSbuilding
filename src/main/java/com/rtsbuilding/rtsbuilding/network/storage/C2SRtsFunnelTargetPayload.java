package com.rtsbuilding.rtsbuilding.network.storage;
import io.netty.buffer.ByteBuf;
import com.rtsbuilding.rtsbuilding.platform.math.BlockPos;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
public final class C2SRtsFunnelTargetPayload implements IMessage {
    private BlockPos target;
    public C2SRtsFunnelTargetPayload() { }
    public C2SRtsFunnelTargetPayload(BlockPos target){this.target=target;}
    public BlockPos target(){return target;}
    @Override public void fromBytes(ByteBuf b){target=BlockPos.fromLong(b.readLong());}
    @Override public void toBytes(ByteBuf b){if(target==null)throw new IllegalArgumentException("funnel target");b.writeLong(target.toLong());}
    public boolean isValid(){return target!=null;}
}
