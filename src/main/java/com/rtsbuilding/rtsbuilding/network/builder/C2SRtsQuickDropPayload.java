package com.rtsbuilding.rtsbuilding.network.builder;
import com.rtsbuilding.rtsbuilding.network.RtsPacketBuffer;
import io.netty.buffer.ByteBuf;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
public final class C2SRtsQuickDropPayload implements IMessage{
 public static final int MAX_ITEM_ID_CHARS=128; private String itemId;private byte amount;private double dropX,dropY,dropZ;
 public C2SRtsQuickDropPayload(){}
 public C2SRtsQuickDropPayload(String id,byte amount,double x,double y,double z){this.itemId=id==null?"":id;this.amount=amount;dropX=x;dropY=y;dropZ=z;}
 public String itemId(){return itemId;}public byte amount(){return amount;}public double dropX(){return dropX;}public double dropY(){return dropY;}public double dropZ(){return dropZ;}
 public BlockPos dropPos(){return new BlockPos(dropX,dropY,dropZ);}
 @Override public void fromBytes(ByteBuf b){itemId=RtsPacketBuffer.readString(b,MAX_ITEM_ID_CHARS,"quick drop item");amount=b.readByte();dropX=b.readDouble();dropY=b.readDouble();dropZ=b.readDouble();}
 @Override public void toBytes(ByteBuf b){RtsPacketBuffer.writeString(b,itemId,MAX_ITEM_ID_CHARS,"quick drop item");b.writeByte(amount);b.writeDouble(dropX);b.writeDouble(dropY);b.writeDouble(dropZ);}
 public boolean isValid(){return itemId!=null&&!itemId.isEmpty()&&itemId.length()<=MAX_ITEM_ID_CHARS&&amount>0&&amount<=64&&finite(dropX)&&finite(dropY)&&finite(dropZ);}
 private static boolean finite(double v){return !Double.isNaN(v)&&!Double.isInfinite(v);}
}
