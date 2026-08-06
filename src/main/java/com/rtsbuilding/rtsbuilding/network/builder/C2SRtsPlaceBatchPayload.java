package com.rtsbuilding.rtsbuilding.network.builder;

import com.rtsbuilding.rtsbuilding.network.RtsPacketBuffer;
import io.netty.buffer.ByteBuf;
import net.minecraft.item.ItemStack;
import com.rtsbuilding.rtsbuilding.platform.math.EnumFacing;
import com.rtsbuilding.rtsbuilding.platform.math.BlockPos;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 有严格数量上限的批量放置意图。 */
public final class C2SRtsPlaceBatchPayload implements IMessage {
    public static final int MAX_POSITIONS = 32768;
    public static final int MAX_POSITIONS_PER_PACKET = 2048;
    private int submissionId;
    private int chunkIndex;
    private int chunkCount = 1;
    private int totalPositions;
    private List<BlockPos> clickedPositions = Collections.emptyList();
    private byte face;
    private double hitOffsetX, hitOffsetY, hitOffsetZ;
    private byte rotateSteps;
    private String statePreset = "";
    private boolean forcePlace, skipIfOccupied, overwriteExisting;
    private String itemId = "";
    private ItemStack itemPrototype = null;
    private double rayOriginX, rayOriginY, rayOriginZ, rayDirX, rayDirY, rayDirZ;

    public C2SRtsPlaceBatchPayload() {
    }

    public C2SRtsPlaceBatchPayload(List<BlockPos> positions, byte face,
            double hitOffsetX, double hitOffsetY, double hitOffsetZ, byte rotateSteps,
            String statePreset, boolean forcePlace, boolean skipIfOccupied,
            boolean overwriteExisting, String itemId, ItemStack itemPrototype,
            double rayOriginX, double rayOriginY, double rayOriginZ,
            double rayDirX, double rayDirY, double rayDirZ) {
        this(0, 0, 1, positions == null ? 0 : positions.size(), positions, face,
                hitOffsetX, hitOffsetY, hitOffsetZ, rotateSteps, statePreset,
                forcePlace, skipIfOccupied, overwriteExisting, itemId, itemPrototype,
                rayOriginX, rayOriginY, rayOriginZ, rayDirX, rayDirY, rayDirZ);
    }

    public C2SRtsPlaceBatchPayload(int submissionId, int chunkIndex, int chunkCount,
            int totalPositions, List<BlockPos> positions, byte face,
            double hitOffsetX, double hitOffsetY, double hitOffsetZ, byte rotateSteps,
            String statePreset, boolean forcePlace, boolean skipIfOccupied,
            boolean overwriteExisting, String itemId, ItemStack itemPrototype,
            double rayOriginX, double rayOriginY, double rayOriginZ,
            double rayDirX, double rayDirY, double rayDirZ) {
        this.submissionId = submissionId;
        this.chunkIndex = chunkIndex;
        this.chunkCount = chunkCount;
        this.totalPositions = totalPositions;
        clickedPositions = positions == null ? Collections.<BlockPos>emptyList()
                : Collections.unmodifiableList(new ArrayList<BlockPos>(positions));
        this.face = face;
        this.hitOffsetX = hitOffsetX; this.hitOffsetY = hitOffsetY; this.hitOffsetZ = hitOffsetZ;
        this.rotateSteps = rotateSteps; this.statePreset = statePreset == null ? "" : statePreset;
        this.forcePlace = forcePlace; this.skipIfOccupied = skipIfOccupied;
        this.overwriteExisting = overwriteExisting;
        this.itemId = itemId == null ? "" : itemId;
        this.itemPrototype = itemPrototype == null ? null : itemPrototype;
        this.rayOriginX = rayOriginX; this.rayOriginY = rayOriginY; this.rayOriginZ = rayOriginZ;
        this.rayDirX = rayDirX; this.rayDirY = rayDirY; this.rayDirZ = rayDirZ;
    }

    @Override public void fromBytes(ByteBuf b) {
        submissionId = b.readInt();
        chunkIndex = RtsPacketBuffer.readVarInt(b);
        chunkCount = RtsPacketBuffer.readVarInt(b);
        totalPositions = RtsPacketBuffer.readVarInt(b);
        int size = RtsPacketBuffer.readBoundedCount(b, MAX_POSITIONS_PER_PACKET, "place positions chunk");
        if (size == 0) throw new IllegalArgumentException("empty place batch");
        List<BlockPos> positions = new ArrayList<BlockPos>(size);
        for (int i = 0; i < size; i++) positions.add(BlockPos.fromLong(b.readLong()));
        clickedPositions = Collections.unmodifiableList(positions);
        face = b.readByte();
        hitOffsetX = b.readDouble(); hitOffsetY = b.readDouble(); hitOffsetZ = b.readDouble();
        rotateSteps = b.readByte();
        statePreset = RtsPacketBuffer.readString(b, 256, "state preset");
        forcePlace = b.readBoolean(); skipIfOccupied = b.readBoolean();
        overwriteExisting = b.readBoolean();
        itemId = RtsPacketBuffer.readString(b, 128, "item id");
        itemPrototype = RtsPacketBuffer.readItemStack(b);
        rayOriginX = b.readDouble(); rayOriginY = b.readDouble(); rayOriginZ = b.readDouble();
        rayDirX = b.readDouble(); rayDirY = b.readDouble(); rayDirZ = b.readDouble();
        if (!isValid()) throw new IllegalArgumentException("invalid RTS place batch");
    }

    @Override public void toBytes(ByteBuf b) {
        if (!isValid()) throw new IllegalArgumentException("invalid RTS place batch");
        b.writeInt(submissionId);
        RtsPacketBuffer.writeVarInt(b, chunkIndex);
        RtsPacketBuffer.writeVarInt(b, chunkCount);
        RtsPacketBuffer.writeVarInt(b, totalPositions);
        RtsPacketBuffer.writeVarInt(b, clickedPositions.size());
        for (BlockPos pos : clickedPositions) b.writeLong(pos.toLong());
        b.writeByte(face); b.writeDouble(hitOffsetX); b.writeDouble(hitOffsetY); b.writeDouble(hitOffsetZ);
        b.writeByte(rotateSteps);
        RtsPacketBuffer.writeString(b, statePreset, 256, "state preset");
        b.writeBoolean(forcePlace); b.writeBoolean(skipIfOccupied); b.writeBoolean(overwriteExisting);
        RtsPacketBuffer.writeString(b, itemId, 128, "item id");
        RtsPacketBuffer.writeItemStack(b, itemPrototype);
        b.writeDouble(rayOriginX); b.writeDouble(rayOriginY); b.writeDouble(rayOriginZ);
        b.writeDouble(rayDirX); b.writeDouble(rayDirY); b.writeDouble(rayDirZ);
    }

    public boolean isValid() {
        if (clickedPositions == null || clickedPositions.isEmpty()
                || clickedPositions.size() > MAX_POSITIONS_PER_PACKET
                || totalPositions <= 0 || totalPositions > MAX_POSITIONS
                || chunkIndex < 0 || chunkCount <= 0 || chunkIndex >= chunkCount
                || chunkCount != (totalPositions + MAX_POSITIONS_PER_PACKET - 1) / MAX_POSITIONS_PER_PACKET
                || clickedPositions.size() != (chunkIndex == chunkCount - 1
                        ? totalPositions - chunkIndex * MAX_POSITIONS_PER_PACKET
                        : MAX_POSITIONS_PER_PACKET)
                || face < 0
                || face >= EnumFacing.values().length || statePreset == null
                || statePreset.length() > 256 || itemId == null || itemId.length() > 128) return false;
        for (BlockPos pos : clickedPositions) if (pos == null) return false;
        return finite(hitOffsetX, hitOffsetY, hitOffsetZ, rayOriginX, rayOriginY,
                rayOriginZ, rayDirX, rayDirY, rayDirZ);
    }

    private static boolean finite(double... values) {
        for (double value : values) if (Double.isNaN(value) || Double.isInfinite(value)) return false;
        return true;
    }

    public List<BlockPos> clickedPositions(){return clickedPositions;} public byte face(){return face;}
    public int submissionId(){return submissionId;} public int chunkIndex(){return chunkIndex;}
    public int chunkCount(){return chunkCount;} public int totalPositions(){return totalPositions;}
    public double hitOffsetX(){return hitOffsetX;} public double hitOffsetY(){return hitOffsetY;}
    public double hitOffsetZ(){return hitOffsetZ;} public byte rotateSteps(){return rotateSteps;}
    public String statePreset(){return statePreset;} public boolean forcePlace(){return forcePlace;}
    public boolean skipIfOccupied(){return skipIfOccupied;}
    public boolean overwriteExisting(){return overwriteExisting;} public String itemId(){return itemId;}
    public ItemStack itemPrototype(){return itemPrototype;} public double rayOriginX(){return rayOriginX;}
    public double rayOriginY(){return rayOriginY;} public double rayOriginZ(){return rayOriginZ;}
    public double rayDirX(){return rayDirX;} public double rayDirY(){return rayDirY;}
    public double rayDirZ(){return rayDirZ;}

    public String metadataSignature() {
        return face + "|" + hitOffsetX + "|" + hitOffsetY + "|" + hitOffsetZ + "|"
                + rotateSteps + "|" + statePreset + "|" + forcePlace + "|" + skipIfOccupied
                + "|" + overwriteExisting + "|" + itemId + "|" + rayOriginX + "|"
                + rayOriginY + "|" + rayOriginZ + "|" + rayDirX + "|" + rayDirY + "|"
                + rayDirZ + "|" + String.valueOf(itemPrototype) + "|"
                + String.valueOf(itemPrototype == null ? null : itemPrototype.getTagCompound());
    }
}
