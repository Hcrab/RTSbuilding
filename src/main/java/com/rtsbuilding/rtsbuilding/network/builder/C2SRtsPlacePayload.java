package com.rtsbuilding.rtsbuilding.network.builder;

import com.rtsbuilding.rtsbuilding.network.RtsPacketBuffer;
import io.netty.buffer.ByteBuf;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

/** 单方块放置意图；物品原型保留完整 NBT，但真实物品必须由服务端重新提取。 */
public final class C2SRtsPlacePayload implements IMessage {
    private BlockPos clickedPos;
    private byte face;
    private double hitX, hitY, hitZ;
    private byte rotateSteps;
    private String statePreset = "";
    private boolean forcePlace, skipIfOccupied;
    private String itemId = "";
    private ItemStack itemPrototype = ItemStack.EMPTY;
    private double rayOriginX, rayOriginY, rayOriginZ, rayDirX, rayDirY, rayDirZ;
    private boolean quickBuild, forceEmptyHand;

    public C2SRtsPlacePayload() {
    }

    public C2SRtsPlacePayload(BlockPos clickedPos, byte face, double hitX, double hitY, double hitZ,
            byte rotateSteps, String statePreset, boolean forcePlace, boolean skipIfOccupied,
            String itemId, ItemStack itemPrototype, double rayOriginX, double rayOriginY,
            double rayOriginZ, double rayDirX, double rayDirY, double rayDirZ,
            boolean quickBuild, boolean forceEmptyHand) {
        this.clickedPos = clickedPos;
        this.face = face;
        this.hitX = hitX; this.hitY = hitY; this.hitZ = hitZ;
        this.rotateSteps = rotateSteps;
        this.statePreset = statePreset == null ? "" : statePreset;
        this.forcePlace = forcePlace; this.skipIfOccupied = skipIfOccupied;
        this.itemId = itemId == null ? "" : itemId;
        this.itemPrototype = itemPrototype == null ? ItemStack.EMPTY : itemPrototype;
        this.rayOriginX = rayOriginX; this.rayOriginY = rayOriginY; this.rayOriginZ = rayOriginZ;
        this.rayDirX = rayDirX; this.rayDirY = rayDirY; this.rayDirZ = rayDirZ;
        this.quickBuild = quickBuild; this.forceEmptyHand = forceEmptyHand;
    }

    @Override public void fromBytes(ByteBuf b) {
        clickedPos = BlockPos.fromLong(b.readLong()); face = b.readByte();
        hitX = b.readDouble(); hitY = b.readDouble(); hitZ = b.readDouble();
        rotateSteps = b.readByte();
        statePreset = RtsPacketBuffer.readString(b, 256, "state preset");
        forcePlace = b.readBoolean(); skipIfOccupied = b.readBoolean();
        itemId = RtsPacketBuffer.readString(b, 128, "item id");
        itemPrototype = RtsPacketBuffer.readItemStack(b);
        rayOriginX = b.readDouble(); rayOriginY = b.readDouble(); rayOriginZ = b.readDouble();
        rayDirX = b.readDouble(); rayDirY = b.readDouble(); rayDirZ = b.readDouble();
        quickBuild = b.readBoolean(); forceEmptyHand = b.readBoolean();
        if (!isValid()) throw new IllegalArgumentException("invalid RTS place payload");
    }

    @Override public void toBytes(ByteBuf b) {
        if (!isValid()) throw new IllegalArgumentException("invalid RTS place payload");
        b.writeLong(clickedPos.toLong()); b.writeByte(face);
        b.writeDouble(hitX); b.writeDouble(hitY); b.writeDouble(hitZ); b.writeByte(rotateSteps);
        RtsPacketBuffer.writeString(b, statePreset, 256, "state preset");
        b.writeBoolean(forcePlace); b.writeBoolean(skipIfOccupied);
        RtsPacketBuffer.writeString(b, itemId, 128, "item id");
        RtsPacketBuffer.writeItemStack(b, itemPrototype);
        b.writeDouble(rayOriginX); b.writeDouble(rayOriginY); b.writeDouble(rayOriginZ);
        b.writeDouble(rayDirX); b.writeDouble(rayDirY); b.writeDouble(rayDirZ);
        b.writeBoolean(quickBuild); b.writeBoolean(forceEmptyHand);
    }

    public boolean isValid() {
        return clickedPos != null && face >= 0 && face < EnumFacing.values().length
                && statePreset != null && statePreset.length() <= 256
                && itemId != null && itemId.length() <= 128
                && finite(hitX, hitY, hitZ, rayOriginX, rayOriginY, rayOriginZ, rayDirX, rayDirY, rayDirZ);
    }

    private static boolean finite(double... values) {
        for (double value : values) if (Double.isNaN(value) || Double.isInfinite(value)) return false;
        return true;
    }

    public BlockPos clickedPos(){return clickedPos;} public byte face(){return face;}
    public double hitX(){return hitX;} public double hitY(){return hitY;} public double hitZ(){return hitZ;}
    public byte rotateSteps(){return rotateSteps;} public String statePreset(){return statePreset;}
    public boolean forcePlace(){return forcePlace;} public boolean skipIfOccupied(){return skipIfOccupied;}
    public String itemId(){return itemId;} public ItemStack itemPrototype(){return itemPrototype;}
    public double rayOriginX(){return rayOriginX;} public double rayOriginY(){return rayOriginY;}
    public double rayOriginZ(){return rayOriginZ;} public double rayDirX(){return rayDirX;}
    public double rayDirY(){return rayDirY;} public double rayDirZ(){return rayDirZ;}
    public boolean quickBuild(){return quickBuild;} public boolean forceEmptyHand(){return forceEmptyHand;}
}
