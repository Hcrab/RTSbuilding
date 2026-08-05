package com.rtsbuilding.rtsbuilding.network.storage;
import io.netty.buffer.ByteBuf;
import com.rtsbuilding.rtsbuilding.platform.math.BlockPos;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
public final class C2SRtsLinkStoragePayload implements IMessage {
    public static final byte MODE_BIDIRECTIONAL=0, MODE_EXTRACT_ONLY=1;
    private BlockPos pos; private byte linkMode;
    public C2SRtsLinkStoragePayload() { }
    public C2SRtsLinkStoragePayload(BlockPos pos, byte linkMode){this.pos=pos;this.linkMode=linkMode;}
    public BlockPos pos(){return pos;} public byte linkMode(){return linkMode;}
    @Override public void fromBytes(ByteBuf b){pos=BlockPos.fromLong(b.readLong());linkMode=b.readByte();}
    @Override public void toBytes(ByteBuf b){if(pos==null)throw new IllegalArgumentException("storage pos");b.writeLong(pos.toLong());b.writeByte(linkMode);}
    public boolean isValid(){return pos!=null&&(linkMode==MODE_BIDIRECTIONAL||linkMode==MODE_EXTRACT_ONLY);}
}
