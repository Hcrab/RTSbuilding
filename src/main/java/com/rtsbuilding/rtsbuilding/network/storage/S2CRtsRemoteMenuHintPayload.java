package com.rtsbuilding.rtsbuilding.network.storage;
import io.netty.buffer.ByteBuf;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
public final class S2CRtsRemoteMenuHintPayload implements IMessage {
    private BlockPos pos;
    public S2CRtsRemoteMenuHintPayload() {}
    public S2CRtsRemoteMenuHintPayload(BlockPos pos){this.pos=pos;}
    @Override public void fromBytes(ByteBuf b){pos=BlockPos.fromLong(b.readLong());}
    @Override public void toBytes(ByteBuf b){if(pos==null)throw new IllegalArgumentException("remote menu pos");b.writeLong(pos.toLong());}
    public BlockPos pos(){return pos;}
}
