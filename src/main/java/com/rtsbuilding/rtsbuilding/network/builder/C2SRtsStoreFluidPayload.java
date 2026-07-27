package com.rtsbuilding.rtsbuilding.network.builder;
import com.rtsbuilding.rtsbuilding.network.RtsPacketBuffer;import io.netty.buffer.ByteBuf;import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
public final class C2SRtsStoreFluidPayload implements IMessage{
 public static final byte SOURCE_STORAGE_ITEM=0,SOURCE_TOOL_SLOT=1,SOURCE_PIN_ITEM=2;public static final int MAX_ITEM_ID_CHARS=128;
 private byte sourceType,toolSlot;private String itemId;
 public C2SRtsStoreFluidPayload(){}public C2SRtsStoreFluidPayload(byte s,byte t,String i){sourceType=s;toolSlot=t;itemId=i==null?"":i;}
 public byte sourceType(){return sourceType;}public byte toolSlot(){return toolSlot;}public String itemId(){return itemId;}
 public void fromBytes(ByteBuf b){sourceType=b.readByte();toolSlot=b.readByte();itemId=RtsPacketBuffer.readString(b,MAX_ITEM_ID_CHARS,"fluid source item");}
 public void toBytes(ByteBuf b){b.writeByte(sourceType);b.writeByte(toolSlot);RtsPacketBuffer.writeString(b,itemId,MAX_ITEM_ID_CHARS,"fluid source item");}
 public boolean isValid(){if(sourceType<0||sourceType>2)return false;if(sourceType==SOURCE_TOOL_SLOT)return toolSlot>=0&&toolSlot<=8;return itemId!=null&&!itemId.isEmpty()&&itemId.length()<=MAX_ITEM_ID_CHARS;}
}
