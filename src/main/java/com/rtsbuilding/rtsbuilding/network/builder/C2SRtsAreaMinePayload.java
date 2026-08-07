package com.rtsbuilding.rtsbuilding.network.builder;

import com.rtsbuilding.rtsbuilding.network.RtsPacketBuffer;
import com.rtsbuilding.rtsbuilding.network.RtsProtocolLimits;
import com.rtsbuilding.rtsbuilding.network.RtsTracedPayload;
import io.netty.buffer.ByteBuf;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

/** 范围挖掘请求及其客户端因果身份。 */
public final class C2SRtsAreaMinePayload implements IMessage, RtsTracedPayload {
    public static final int MAX_VOLUME = RtsProtocolLimits.AREA_MINE_MAX_VOLUME;

    private int minX;
    private int maxX;
    private int minY;
    private int maxY;
    private int minZ;
    private int maxZ;
    private byte toolSlot;
    private byte shapeType;
    private byte fillType;
    private String toolItemId = "";
    private ItemStack toolPrototype = ItemStack.EMPTY;
    private boolean toolProtectionEnabled;
    private long traceId;
    private int sequence;
    private long clientTick = -1L;
    private byte inputKind;

    public C2SRtsAreaMinePayload() {
    }

    public C2SRtsAreaMinePayload(int minX, int maxX, int minY, int maxY, int minZ,
            int maxZ, byte toolSlot, String toolItemId, ItemStack toolPrototype,
            byte shapeType, byte fillType, boolean toolProtectionEnabled) {
        this(minX, maxX, minY, maxY, minZ, maxZ, toolSlot, toolItemId, toolPrototype,
                shapeType, fillType, toolProtectionEnabled, 0L, 0, -1L, (byte) 0);
    }

    public C2SRtsAreaMinePayload(int minX, int maxX, int minY, int maxY, int minZ,
            int maxZ, byte toolSlot, String toolItemId, ItemStack toolPrototype,
            byte shapeType, byte fillType, boolean toolProtectionEnabled,
            long traceId, int sequence, long clientTick, byte inputKind) {
        this.minX = minX;
        this.maxX = maxX;
        this.minY = minY;
        this.maxY = maxY;
        this.minZ = minZ;
        this.maxZ = maxZ;
        this.toolSlot = toolSlot;
        this.toolItemId = toolItemId == null ? "" : toolItemId;
        this.toolPrototype = toolPrototype == null ? ItemStack.EMPTY : toolPrototype.copy();
        this.shapeType = shapeType;
        this.fillType = fillType;
        this.toolProtectionEnabled = toolProtectionEnabled;
        this.traceId = traceId;
        this.sequence = Math.max(0, sequence);
        this.clientTick = clientTick;
        this.inputKind = inputKind;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        minX = buffer.readInt();
        maxX = buffer.readInt();
        minY = buffer.readInt();
        maxY = buffer.readInt();
        minZ = buffer.readInt();
        maxZ = buffer.readInt();
        toolSlot = buffer.readByte();
        toolItemId = RtsPacketBuffer.readString(buffer, 256, "tool id");
        toolPrototype = RtsPacketBuffer.readItemStack(buffer);
        shapeType = buffer.readByte();
        fillType = buffer.readByte();
        toolProtectionEnabled = buffer.readBoolean();
        traceId = buffer.readLong();
        sequence = RtsPacketBuffer.readVarInt(buffer);
        clientTick = buffer.readLong();
        inputKind = buffer.readByte();
        if (!isValid()) throw new IllegalArgumentException("invalid area mine");
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        if (!isValid()) throw new IllegalArgumentException("invalid area mine");
        buffer.writeInt(minX);
        buffer.writeInt(maxX);
        buffer.writeInt(minY);
        buffer.writeInt(maxY);
        buffer.writeInt(minZ);
        buffer.writeInt(maxZ);
        buffer.writeByte(toolSlot);
        RtsPacketBuffer.writeString(buffer, toolItemId, 256, "tool id");
        RtsPacketBuffer.writeItemStack(buffer, toolPrototype);
        buffer.writeByte(shapeType);
        buffer.writeByte(fillType);
        buffer.writeBoolean(toolProtectionEnabled);
        buffer.writeLong(traceId);
        RtsPacketBuffer.writeVarInt(buffer, sequence);
        buffer.writeLong(clientTick);
        buffer.writeByte(inputKind);
    }

    public boolean isValid() {
        if (minX > maxX || minY > maxY || minZ > maxZ || toolSlot < 0 || toolSlot > 8
                || shapeType < 0 || fillType < 0 || sequence < 0) return false;
        long dx = (long) maxX - minX + 1L;
        long dy = (long) maxY - minY + 1L;
        long dz = (long) maxZ - minZ + 1L;
        if (dx <= 0L || dy <= 0L || dz <= 0L) return false;
        if (dx > MAX_VOLUME || dy > MAX_VOLUME || dz > MAX_VOLUME) return false;
        long volume = dx * dy;
        if (volume > MAX_VOLUME) return false;
        volume *= dz;
        return volume > 0L && volume <= MAX_VOLUME
                && toolItemId != null && toolItemId.length() <= 256;
    }

    public int minX() { return minX; }
    public int maxX() { return maxX; }
    public int minY() { return minY; }
    public int maxY() { return maxY; }
    public int minZ() { return minZ; }
    public int maxZ() { return maxZ; }
    public byte toolSlot() { return toolSlot; }
    public String toolItemId() { return toolItemId; }
    public ItemStack toolPrototype() { return toolPrototype; }
    public byte shapeType() { return shapeType; }
    public byte fillType() { return fillType; }
    public boolean toolProtectionEnabled() { return toolProtectionEnabled; }
    public BlockPos minPos() { return new BlockPos(minX, minY, minZ); }
    public BlockPos maxPos() { return new BlockPos(maxX, maxY, maxZ); }
    @Override public long traceId() { return traceId; }
    public int sequence() { return sequence; }
    public long clientTick() { return clientTick; }
    public byte inputKind() { return inputKind; }
}
