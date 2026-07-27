package com.rtsbuilding.rtsbuilding.network.builder;
import io.netty.buffer.ByteBuf;import net.minecraft.util.EnumFacing;import net.minecraft.util.math.BlockPos;import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
public final class C2SRtsBreakPayload implements IMessage{
 private BlockPos pos;private byte face;private boolean allowAdjacentFallback;
 public C2SRtsBreakPayload(){}public C2SRtsBreakPayload(BlockPos p,byte f,boolean a){pos=p;face=f;allowAdjacentFallback=a;}
 public BlockPos pos(){return pos;}public byte face(){return face;}public boolean allowAdjacentFallback(){return allowAdjacentFallback;}
 public void fromBytes(ByteBuf b){pos=BlockPos.fromLong(b.readLong());face=b.readByte();allowAdjacentFallback=b.readBoolean();}
 public void toBytes(ByteBuf b){if(pos==null)throw new IllegalArgumentException("break pos");b.writeLong(pos.toLong());b.writeByte(face);b.writeBoolean(allowAdjacentFallback);}
 public boolean isValid(){return pos!=null&&face>=0&&face<EnumFacing.values().length;}
}
