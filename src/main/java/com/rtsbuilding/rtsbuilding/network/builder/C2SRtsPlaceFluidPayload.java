package com.rtsbuilding.rtsbuilding.network.builder;

import com.rtsbuilding.rtsbuilding.network.RtsPacketBuffer;
import io.netty.buffer.ByteBuf;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

/** 放置流体意图；流体库存与桶/容器来源由服务端重新解析。 */
public final class C2SRtsPlaceFluidPayload implements IMessage {
    private BlockPos clickedPos;
    private byte face;
    private double hitX, hitY, hitZ;
    private boolean forcePlace;
    private String fluidId = "";
    private double rayOriginX, rayOriginY, rayOriginZ, rayDirX, rayDirY, rayDirZ;

    public C2SRtsPlaceFluidPayload() {
    }

    public C2SRtsPlaceFluidPayload(BlockPos clickedPos, byte face, double hitX, double hitY, double hitZ,
            boolean forcePlace, String fluidId, double rayOriginX, double rayOriginY,
            double rayOriginZ, double rayDirX, double rayDirY, double rayDirZ) {
        this.clickedPos = clickedPos; this.face = face;
        this.hitX = hitX; this.hitY = hitY; this.hitZ = hitZ; this.forcePlace = forcePlace;
        this.fluidId = fluidId == null ? "" : fluidId;
        this.rayOriginX = rayOriginX; this.rayOriginY = rayOriginY; this.rayOriginZ = rayOriginZ;
        this.rayDirX = rayDirX; this.rayDirY = rayDirY; this.rayDirZ = rayDirZ;
    }

    @Override public void fromBytes(ByteBuf b) {
        clickedPos = BlockPos.fromLong(b.readLong()); face = b.readByte();
        hitX = b.readDouble(); hitY = b.readDouble(); hitZ = b.readDouble();
        forcePlace = b.readBoolean();
        fluidId = RtsPacketBuffer.readString(b, 128, "fluid id");
        rayOriginX = b.readDouble(); rayOriginY = b.readDouble(); rayOriginZ = b.readDouble();
        rayDirX = b.readDouble(); rayDirY = b.readDouble(); rayDirZ = b.readDouble();
        if (!isValid()) throw new IllegalArgumentException("invalid RTS fluid placement");
    }

    @Override public void toBytes(ByteBuf b) {
        if (!isValid()) throw new IllegalArgumentException("invalid RTS fluid placement");
        b.writeLong(clickedPos.toLong()); b.writeByte(face);
        b.writeDouble(hitX); b.writeDouble(hitY); b.writeDouble(hitZ); b.writeBoolean(forcePlace);
        RtsPacketBuffer.writeString(b, fluidId, 128, "fluid id");
        b.writeDouble(rayOriginX); b.writeDouble(rayOriginY); b.writeDouble(rayOriginZ);
        b.writeDouble(rayDirX); b.writeDouble(rayDirY); b.writeDouble(rayDirZ);
    }

    public boolean isValid() {
        return clickedPos != null && face >= 0 && face < EnumFacing.values().length
                && fluidId != null && !fluidId.isEmpty() && fluidId.length() <= 128
                && finite(hitX, hitY, hitZ, rayOriginX, rayOriginY, rayOriginZ, rayDirX, rayDirY, rayDirZ);
    }
    private static boolean finite(double... values) {
        for (double value : values) if (Double.isNaN(value) || Double.isInfinite(value)) return false;
        return true;
    }

    public BlockPos clickedPos(){return clickedPos;} public byte face(){return face;}
    public double hitX(){return hitX;} public double hitY(){return hitY;} public double hitZ(){return hitZ;}
    public boolean forcePlace(){return forcePlace;} public String fluidId(){return fluidId;}
    public double rayOriginX(){return rayOriginX;} public double rayOriginY(){return rayOriginY;}
    public double rayOriginZ(){return rayOriginZ;} public double rayDirX(){return rayDirX;}
    public double rayDirY(){return rayDirY;} public double rayDirZ(){return rayDirZ;}
}
