package com.rtsbuilding.rtsbuilding.network.culling;

import com.rtsbuilding.rtsbuilding.network.RtsPacketBuffer;
import io.netty.buffer.ByteBuf;
import com.rtsbuilding.rtsbuilding.platform.math.BlockPos;
import cpw.mods.fml.common.network.simpleimpl.IMessage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 服务端返回当前玩家、当前维度的剔除状态。 */
public final class S2CRtsCullingStatePayload implements IMessage {
    private static final int MAX_DIMENSION_CHARS = 128;
    private String dimension = "";
    private List<RtsCullingBoxSnapshot> boxes = Collections.emptyList();
    private List<BlockPos> revealed = Collections.emptyList();

    public S2CRtsCullingStatePayload() { }
    public S2CRtsCullingStatePayload(String dimension, List<RtsCullingBoxSnapshot> boxes, List<BlockPos> revealed) {
        this.dimension = dimension == null ? "" : dimension;
        this.boxes = boxes == null ? Collections.<RtsCullingBoxSnapshot>emptyList()
                : Collections.unmodifiableList(new ArrayList<RtsCullingBoxSnapshot>(boxes));
        this.revealed = revealed == null ? Collections.<BlockPos>emptyList()
                : Collections.unmodifiableList(new ArrayList<BlockPos>(revealed));
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
}
