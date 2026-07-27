package com.rtsbuilding.rtsbuilding.network.pathfinding;
import io.netty.buffer.ByteBuf;import net.minecraft.util.math.BlockPos;import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
public final class C2SRtsPathfindingPayload implements IMessage{
 private BlockPos target;public C2SRtsPathfindingPayload(){}public C2SRtsPathfindingPayload(BlockPos t){target=t;}public BlockPos target(){return target;}
 public void fromBytes(ByteBuf b){target=BlockPos.fromLong(b.readLong());}public void toBytes(ByteBuf b){if(target==null)throw new IllegalArgumentException("path target");b.writeLong(target.toLong());}public boolean isValid(){return target!=null;}
}
