package com.rtsbuilding.rtsbuilding.network.builder;

import com.rtsbuilding.rtsbuilding.network.RtsPacketBuffer;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 在单个网络包中批量同步多个工作流槽位。 */
public final class S2CRtsWorkflowProgressBatchPayload implements IMessage {
    public static final int MAX_ENTRIES = 127;
    private List<S2CRtsWorkflowProgressPayload> entries = Collections.emptyList();
    public S2CRtsWorkflowProgressBatchPayload() {}
    public S2CRtsWorkflowProgressBatchPayload(List<S2CRtsWorkflowProgressPayload> entries) {
        this.entries = immutableEntries(entries);
    }
    public List<S2CRtsWorkflowProgressPayload> entries() { return this.entries; }
    @Override public void fromBytes(ByteBuf buffer) {
        int count = RtsPacketBuffer.readBoundedCount(buffer, MAX_ENTRIES, "workflow batch entries");
        List<S2CRtsWorkflowProgressPayload> decoded = new ArrayList<S2CRtsWorkflowProgressPayload>(count);
        for (int i = 0; i < count; i++) decoded.add(S2CRtsWorkflowProgressPayload.readNew(buffer));
        this.entries = Collections.unmodifiableList(decoded);
    }
    @Override public void toBytes(ByteBuf buffer) {
        List<S2CRtsWorkflowProgressPayload> safe = immutableEntries(this.entries);
        RtsPacketBuffer.writeVarInt(buffer, safe.size());
        for (S2CRtsWorkflowProgressPayload entry : safe) {
            S2CRtsWorkflowProgressPayload.writeTo(buffer, entry);
        }
    }
    private static List<S2CRtsWorkflowProgressPayload> immutableEntries(
            List<S2CRtsWorkflowProgressPayload> values) {
        if (values == null || values.isEmpty()) return Collections.emptyList();
        if (values.size() > MAX_ENTRIES) throw new IllegalArgumentException("too many workflow batch entries");
        List<S2CRtsWorkflowProgressPayload> copy = new ArrayList<S2CRtsWorkflowProgressPayload>(values.size());
        for (S2CRtsWorkflowProgressPayload value : values) {
            if (value == null) throw new IllegalArgumentException("null workflow batch entry");
            copy.add(value);
        }
        return Collections.unmodifiableList(copy);
    }
}
