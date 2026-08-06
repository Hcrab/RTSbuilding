package com.rtsbuilding.rtsbuilding.network.storage;

import com.rtsbuilding.rtsbuilding.common.storage.RtsBatchStorageSelectionBounds;
import io.netty.buffer.ByteBuf;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

/** 批量链接只传框选边界，端点发现及验证全部留在服务端。 */
public final class C2SRtsBatchLinkStoragePayload implements IMessage {
    private BlockPos first = BlockPos.ORIGIN;
    private BlockPos second = BlockPos.ORIGIN;
    private byte linkMode;

    public C2SRtsBatchLinkStoragePayload() {
    }

    public C2SRtsBatchLinkStoragePayload(BlockPos first, BlockPos second, byte linkMode) {
        this.first = first == null ? BlockPos.ORIGIN : first.toImmutable();
        this.second = second == null ? BlockPos.ORIGIN : second.toImmutable();
        this.linkMode = linkMode;
    }

    @Override public void fromBytes(ByteBuf buffer) {
        this.first = BlockPos.fromLong(buffer.readLong());
        this.second = BlockPos.fromLong(buffer.readLong());
        this.linkMode = buffer.readByte();
    }
    @Override public void toBytes(ByteBuf buffer) {
        if (!isValid()) throw new IllegalArgumentException("batch storage bounds");
        buffer.writeLong(this.first.toLong());
        buffer.writeLong(this.second.toLong());
        buffer.writeByte(this.linkMode);
    }
    public boolean isValid() {
        return this.first != null && this.second != null
                && (this.linkMode == C2SRtsLinkStoragePayload.MODE_BIDIRECTIONAL
                || this.linkMode == C2SRtsLinkStoragePayload.MODE_EXTRACT_ONLY)
                && RtsBatchStorageSelectionBounds.normalize(this.first, this.second) != null;
    }
    public BlockPos first() { return this.first; }
    public BlockPos second() { return this.second; }
    public byte linkMode() { return this.linkMode; }
}
