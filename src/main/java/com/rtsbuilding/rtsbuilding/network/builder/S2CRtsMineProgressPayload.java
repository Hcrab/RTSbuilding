package com.rtsbuilding.rtsbuilding.network.builder;

import io.netty.buffer.ByteBuf;
import com.rtsbuilding.rtsbuilding.platform.math.BlockPos;
import cpw.mods.fml.common.network.simpleimpl.IMessage;

/** 单方块挖掘裂纹阶段。 */
public final class S2CRtsMineProgressPayload implements IMessage {
    private BlockPos pos = BlockPos.ORIGIN;
    private byte stage;
    public S2CRtsMineProgressPayload() {}
    public S2CRtsMineProgressPayload(BlockPos pos, byte stage) {
        this.pos = pos == null ? BlockPos.ORIGIN : pos;
        this.stage = stage;
    }
    public BlockPos pos() { return this.pos; }
    public byte stage() { return this.stage; }
    @Override public void fromBytes(ByteBuf buffer) {
        this.pos = BlockPos.fromLong(buffer.readLong());
        this.stage = buffer.readByte();
    }
    @Override public void toBytes(ByteBuf buffer) {
        buffer.writeLong(this.pos.toLong());
        buffer.writeByte(this.stage);
    }
}
