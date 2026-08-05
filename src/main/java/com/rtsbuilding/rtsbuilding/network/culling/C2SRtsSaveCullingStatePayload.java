package com.rtsbuilding.rtsbuilding.network.culling;

import com.rtsbuilding.rtsbuilding.network.RtsPacketBuffer;
import io.netty.buffer.ByteBuf;
import com.rtsbuilding.rtsbuilding.platform.math.BlockPos;
import cpw.mods.fml.common.network.simpleimpl.IMessage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 客户端保存当前维度剔除状态；服务端仍校验玩家实际维度。 */
public final class C2SRtsSaveCullingStatePayload implements IMessage {
    private static final int MAX_DIMENSION_CHARS = 128;
    private String dimension = "";
    private List<RtsCullingBoxSnapshot> boxes = Collections.emptyList();
    private List<BlockPos> revealed = Collections.emptyList();

    public C2SRtsSaveCullingStatePayload() { }
    public C2SRtsSaveCullingStatePayload(String dimension, List<RtsCullingBoxSnapshot> boxes, List<BlockPos> revealed) {
        this.dimension = dimension == null ? "" : dimension;
        this.boxes = immutableBoxes(boxes);
        this.revealed = immutablePositions(revealed);
    }
    public String dimension() { return dimension; }
    public List<RtsCullingBoxSnapshot> boxes() { return boxes; }
    public List<BlockPos> revealed() { return revealed; }
    @Override public void fromBytes(ByteBuf buf) {
        dimension = RtsPacketBuffer.readString(buf, MAX_DIMENSION_CHARS, "dimension");
        RtsCullingPayloadCodec.Decoded decoded = RtsCullingPayloadCodec.read(buf);
        boxes = decoded.boxes();
        revealed = decoded.revealed();
    }
    @Override public void toBytes(ByteBuf buf) {
        RtsPacketBuffer.writeString(buf, dimension, MAX_DIMENSION_CHARS, "dimension");
        RtsCullingPayloadCodec.write(buf, boxes, revealed);
    }
    private static List<RtsCullingBoxSnapshot> immutableBoxes(List<RtsCullingBoxSnapshot> value) {
        return value == null ? Collections.<RtsCullingBoxSnapshot>emptyList()
                : Collections.unmodifiableList(new ArrayList<RtsCullingBoxSnapshot>(value));
    }
    private static List<BlockPos> immutablePositions(List<BlockPos> value) {
        return value == null ? Collections.<BlockPos>emptyList()
                : Collections.unmodifiableList(new ArrayList<BlockPos>(value));
    }
}
