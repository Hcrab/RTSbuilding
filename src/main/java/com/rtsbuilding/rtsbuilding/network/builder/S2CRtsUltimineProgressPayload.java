package com.rtsbuilding.rtsbuilding.network.builder;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

/** Ultimine 总进度；processed 为负仍表示进度结束。 */
public final class S2CRtsUltimineProgressPayload implements IMessage {
    private int processed;
    private int total;
    public S2CRtsUltimineProgressPayload() {}
    public S2CRtsUltimineProgressPayload(int processed, int total) {
        this.processed = processed;
        this.total = total;
    }
    public int processed() { return this.processed; }
    public int total() { return this.total; }
    @Override public void fromBytes(ByteBuf buffer) {
        this.processed = buffer.readInt();
        this.total = buffer.readInt();
    }
    @Override public void toBytes(ByteBuf buffer) {
        buffer.writeInt(this.processed);
        buffer.writeInt(this.total);
    }
}
