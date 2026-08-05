package com.rtsbuilding.rtsbuilding.network.builder;

import com.rtsbuilding.rtsbuilding.network.RtsPacketBuffer;
import com.rtsbuilding.rtsbuilding.network.RtsTracedPayload;
import io.netty.buffer.ByteBuf;
import com.rtsbuilding.rtsbuilding.platform.math.EnumFacing;
import com.rtsbuilding.rtsbuilding.platform.math.BlockPos;
import cpw.mods.fml.common.network.simpleimpl.IMessage;

/** 远程交互意图；实体、工具槽、链接物品及权限全部由服务端当前状态决定。 */
public final class C2SRtsInteractPayload implements IMessage, RtsTracedPayload {
    public static final byte SOURCE_TOOL_SLOT = 0, SOURCE_PIN_ITEM = 1;
    public static final byte SOURCE_TOOL_SLOT_AIR = 2, SOURCE_EMPTY_HAND = 3;
    public static final int NO_ENTITY = -1;
    private long traceId;
    private int entityId = NO_ENTITY;
    private BlockPos clickedPos;
    private byte face;
    private double hitX, hitY, hitZ;
    private byte sourceType, toolSlot;
    private String itemId = "";
    private double rayOriginX, rayOriginY, rayOriginZ, rayDirX, rayDirY, rayDirZ;

    public C2SRtsInteractPayload() {
    }

    public C2SRtsInteractPayload(long traceId, int entityId, BlockPos clickedPos, byte face,
            double hitX, double hitY, double hitZ, byte sourceType, byte toolSlot,
            String itemId, double rayOriginX, double rayOriginY, double rayOriginZ,
            double rayDirX, double rayDirY, double rayDirZ) {
        this.traceId = traceId;
        this.entityId = entityId; this.clickedPos = clickedPos; this.face = face;
        this.hitX = hitX; this.hitY = hitY; this.hitZ = hitZ;
        this.sourceType = sourceType; this.toolSlot = toolSlot;
        this.itemId = itemId == null ? "" : itemId;
        this.rayOriginX = rayOriginX; this.rayOriginY = rayOriginY; this.rayOriginZ = rayOriginZ;
        this.rayDirX = rayDirX; this.rayDirY = rayDirY; this.rayDirZ = rayDirZ;
    }

    /** 仅供旧测试/内部构造使用；生产客户端必须传入正数 traceId。 */
    public C2SRtsInteractPayload(int entityId, BlockPos clickedPos, byte face,
            double hitX, double hitY, double hitZ, byte sourceType, byte toolSlot,
            String itemId, double rayOriginX, double rayOriginY, double rayOriginZ,
            double rayDirX, double rayDirY, double rayDirZ) {
        this(0L, entityId, clickedPos, face, hitX, hitY, hitZ, sourceType, toolSlot,
                itemId, rayOriginX, rayOriginY, rayOriginZ, rayDirX, rayDirY, rayDirZ);
    }

    @Override public void fromBytes(ByteBuf b) {
        traceId = b.readLong();
        entityId = b.readInt(); clickedPos = BlockPos.fromLong(b.readLong()); face = b.readByte();
        hitX = b.readDouble(); hitY = b.readDouble(); hitZ = b.readDouble();
        sourceType = b.readByte(); toolSlot = b.readByte();
        itemId = RtsPacketBuffer.readString(b, 128, "interaction item id");
        rayOriginX = b.readDouble(); rayOriginY = b.readDouble(); rayOriginZ = b.readDouble();
        rayDirX = b.readDouble(); rayDirY = b.readDouble(); rayDirZ = b.readDouble();
        if (!isValid()) throw new IllegalArgumentException("invalid RTS interaction");
    }

    @Override public void toBytes(ByteBuf b) {
        if (!isValid()) throw new IllegalArgumentException("invalid RTS interaction");
        b.writeLong(traceId);
        b.writeInt(entityId); b.writeLong(clickedPos.toLong()); b.writeByte(face);
        b.writeDouble(hitX); b.writeDouble(hitY); b.writeDouble(hitZ);
        b.writeByte(sourceType); b.writeByte(toolSlot);
        RtsPacketBuffer.writeString(b, itemId, 128, "interaction item id");
        b.writeDouble(rayOriginX); b.writeDouble(rayOriginY); b.writeDouble(rayOriginZ);
        b.writeDouble(rayDirX); b.writeDouble(rayDirY); b.writeDouble(rayDirZ);
    }

    public boolean isValid() {
        return traceId >= 0L && entityId >= NO_ENTITY && clickedPos != null && face >= 0
                && face < EnumFacing.values().length && sourceType >= SOURCE_TOOL_SLOT
                && sourceType <= SOURCE_EMPTY_HAND && toolSlot >= 0 && toolSlot <= 8
                && itemId != null && itemId.length() <= 128
                && finite(hitX, hitY, hitZ, rayOriginX, rayOriginY, rayOriginZ, rayDirX, rayDirY, rayDirZ);
    }
    private static boolean finite(double... values) {
        for (double value : values) if (Double.isNaN(value) || Double.isInfinite(value)) return false;
        return true;
    }

    @Override public long traceId(){return traceId;}
    public int entityId(){return entityId;} public BlockPos clickedPos(){return clickedPos;}
    public byte face(){return face;} public double hitX(){return hitX;}
    public double hitY(){return hitY;} public double hitZ(){return hitZ;}
    public byte sourceType(){return sourceType;} public byte toolSlot(){return toolSlot;}
    public String itemId(){return itemId;} public double rayOriginX(){return rayOriginX;}
    public double rayOriginY(){return rayOriginY;} public double rayOriginZ(){return rayOriginZ;}
    public double rayDirX(){return rayDirX;} public double rayDirY(){return rayDirY;}
    public double rayDirZ(){return rayDirZ;}
}
