package com.rtsbuilding.rtsbuilding.network.builder;

import com.rtsbuilding.rtsbuilding.network.RtsPacketBuffer;
import io.netty.buffer.ByteBuf;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

/**
 * 智能填坑的第二次确认意图。
 *
 * <p>客户端只提交玩家点击的锚点、材料和参数，绝不提交本地扫描出的方块坐标。服务端会以
 * 同一套 {@code SmartFillPlanner} 重新规划，再接入既有 PLACE_BATCH 工作流。</p>
 */
public final class C2SRtsConfirmSmartFillPayload implements IMessage {
    private BlockPos clickedPos = BlockPos.ORIGIN;
    private byte face;
    private int maxBlocks;
    private int detectionDiameter;
    private double hitOffsetX;
    private double hitOffsetY;
    private double hitOffsetZ;
    private byte rotateSteps;
    private String itemId = "";
    private ItemStack itemPrototype = ItemStack.EMPTY;
    private double rayOriginX;
    private double rayOriginY;
    private double rayOriginZ;
    private double rayDirX;
    private double rayDirY;
    private double rayDirZ;

    public C2SRtsConfirmSmartFillPayload() {
    }

    public C2SRtsConfirmSmartFillPayload(BlockPos clickedPos, byte face, int maxBlocks,
            int detectionDiameter, double hitOffsetX, double hitOffsetY, double hitOffsetZ,
            byte rotateSteps, String itemId, ItemStack itemPrototype,
            double rayOriginX, double rayOriginY, double rayOriginZ,
            double rayDirX, double rayDirY, double rayDirZ) {
        this.clickedPos = clickedPos == null ? BlockPos.ORIGIN : clickedPos.toImmutable();
        this.face = face;
        this.maxBlocks = maxBlocks;
        this.detectionDiameter = detectionDiameter;
        this.hitOffsetX = hitOffsetX;
        this.hitOffsetY = hitOffsetY;
        this.hitOffsetZ = hitOffsetZ;
        this.rotateSteps = rotateSteps;
        this.itemId = itemId == null ? "" : itemId;
        this.itemPrototype = itemPrototype == null ? ItemStack.EMPTY : itemPrototype.copy();
        this.rayOriginX = rayOriginX;
        this.rayOriginY = rayOriginY;
        this.rayOriginZ = rayOriginZ;
        this.rayDirX = rayDirX;
        this.rayDirY = rayDirY;
        this.rayDirZ = rayDirZ;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        this.clickedPos = BlockPos.fromLong(buffer.readLong());
        this.face = buffer.readByte();
        this.maxBlocks = RtsPacketBuffer.readVarInt(buffer);
        this.detectionDiameter = RtsPacketBuffer.readVarInt(buffer);
        this.hitOffsetX = buffer.readDouble();
        this.hitOffsetY = buffer.readDouble();
        this.hitOffsetZ = buffer.readDouble();
        this.rotateSteps = buffer.readByte();
        this.itemId = RtsPacketBuffer.readString(buffer, 128, "smart fill item id");
        this.itemPrototype = RtsPacketBuffer.readItemStack(buffer);
        this.rayOriginX = buffer.readDouble();
        this.rayOriginY = buffer.readDouble();
        this.rayOriginZ = buffer.readDouble();
        this.rayDirX = buffer.readDouble();
        this.rayDirY = buffer.readDouble();
        this.rayDirZ = buffer.readDouble();
        if (!isValid()) throw new IllegalArgumentException("invalid smart fill request");
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        if (!isValid()) throw new IllegalArgumentException("invalid smart fill request");
        buffer.writeLong(this.clickedPos.toLong());
        buffer.writeByte(this.face);
        RtsPacketBuffer.writeVarInt(buffer, this.maxBlocks);
        RtsPacketBuffer.writeVarInt(buffer, this.detectionDiameter);
        buffer.writeDouble(this.hitOffsetX);
        buffer.writeDouble(this.hitOffsetY);
        buffer.writeDouble(this.hitOffsetZ);
        buffer.writeByte(this.rotateSteps);
        RtsPacketBuffer.writeString(buffer, this.itemId, 128, "smart fill item id");
        RtsPacketBuffer.writeItemStack(buffer, this.itemPrototype);
        buffer.writeDouble(this.rayOriginX);
        buffer.writeDouble(this.rayOriginY);
        buffer.writeDouble(this.rayOriginZ);
        buffer.writeDouble(this.rayDirX);
        buffer.writeDouble(this.rayDirY);
        buffer.writeDouble(this.rayDirZ);
    }

    public boolean isValid() {
        return this.clickedPos != null && this.face >= 0 && this.face < EnumFacing.values().length
                && this.maxBlocks > 0 && this.detectionDiameter > 0
                && this.itemId != null && this.itemId.length() <= 128
                && finite(this.hitOffsetX, this.hitOffsetY, this.hitOffsetZ,
                        this.rayOriginX, this.rayOriginY, this.rayOriginZ,
                        this.rayDirX, this.rayDirY, this.rayDirZ);
    }

    private static boolean finite(double... values) {
        for (double value : values) {
            if (Double.isNaN(value) || Double.isInfinite(value)) return false;
        }
        return true;
    }

    public BlockPos clickedPos() { return this.clickedPos; }
    public byte face() { return this.face; }
    public int maxBlocks() { return this.maxBlocks; }
    public int detectionDiameter() { return this.detectionDiameter; }
    public double hitOffsetX() { return this.hitOffsetX; }
    public double hitOffsetY() { return this.hitOffsetY; }
    public double hitOffsetZ() { return this.hitOffsetZ; }
    public byte rotateSteps() { return this.rotateSteps; }
    public String itemId() { return this.itemId; }
    public ItemStack itemPrototype() { return this.itemPrototype; }
    public double rayOriginX() { return this.rayOriginX; }
    public double rayOriginY() { return this.rayOriginY; }
    public double rayOriginZ() { return this.rayOriginZ; }
    public double rayDirX() { return this.rayDirX; }
    public double rayDirY() { return this.rayDirY; }
    public double rayDirZ() { return this.rayDirZ; }
}
