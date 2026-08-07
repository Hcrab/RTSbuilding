package com.rtsbuilding.rtsbuilding.network.builder;

import com.rtsbuilding.rtsbuilding.network.RtsPacketBuffer;
import com.rtsbuilding.rtsbuilding.network.RtsTracedPayload;
import io.netty.buffer.ByteBuf;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

/** 单方块挖掘/停止消息，附带客户端因果身份与输入边界。 */
public final class C2SRtsMinePayload implements IMessage, RtsTracedPayload {
    private BlockPos pos;
    private byte face;
    private boolean start;
    private byte toolSlot;
    private String toolItemId = "";
    private ItemStack toolPrototype = ItemStack.EMPTY;
    private boolean allowPlacedBlockRecovery;
    private boolean toolProtectionEnabled;
    private long traceId;
    private int sequence;
    private long clientTick = -1L;
    private int heldMs;
    private byte inputKind;
    private byte stopOrigin;
    private boolean shiftDown;
    private double hitX = Double.NaN, hitY = Double.NaN, hitZ = Double.NaN;
    private double rayOriginX = Double.NaN, rayOriginY = Double.NaN, rayOriginZ = Double.NaN;
    private double rayDirX = Double.NaN, rayDirY = Double.NaN, rayDirZ = Double.NaN;

    public C2SRtsMinePayload() {}

    public C2SRtsMinePayload(BlockPos pos, byte face, boolean start, byte toolSlot,
            String toolItemId, ItemStack toolPrototype, boolean allowRecovery, boolean protect) {
        this(pos, face, start, toolSlot, toolItemId, toolPrototype, allowRecovery, protect,
                0L, 0, -1L, 0, (byte) 0, (byte) 0, false,
                Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN,
                Double.NaN, Double.NaN, Double.NaN);
    }

    public C2SRtsMinePayload(BlockPos pos, byte face, boolean start, byte toolSlot,
            String toolItemId, ItemStack toolPrototype, boolean allowRecovery, boolean protect,
            long traceId, int sequence, long clientTick, int heldMs, byte inputKind, byte stopOrigin,
            boolean shiftDown, double hitX, double hitY, double hitZ,
            double rayOriginX, double rayOriginY, double rayOriginZ,
            double rayDirX, double rayDirY, double rayDirZ) {
        this.pos = pos;
        this.face = face;
        this.start = start;
        this.toolSlot = toolSlot;
        this.toolItemId = toolItemId == null ? "" : toolItemId;
        this.toolPrototype = toolPrototype == null ? ItemStack.EMPTY : toolPrototype.copy();
        this.allowPlacedBlockRecovery = allowRecovery;
        this.toolProtectionEnabled = protect;
        this.traceId = traceId;
        this.sequence = Math.max(0, sequence);
        this.clientTick = clientTick;
        this.heldMs = Math.max(0, heldMs);
        this.inputKind = inputKind;
        this.stopOrigin = stopOrigin;
        this.shiftDown = shiftDown;
        this.hitX = hitX; this.hitY = hitY; this.hitZ = hitZ;
        this.rayOriginX = rayOriginX; this.rayOriginY = rayOriginY; this.rayOriginZ = rayOriginZ;
        this.rayDirX = rayDirX; this.rayDirY = rayDirY; this.rayDirZ = rayDirZ;
    }

    @Override public void fromBytes(ByteBuf buffer) {
        pos = BlockPos.fromLong(buffer.readLong()); face = buffer.readByte();
        start = buffer.readBoolean(); toolSlot = buffer.readByte();
        toolItemId = RtsPacketBuffer.readString(buffer, 256, "tool id");
        toolPrototype = RtsPacketBuffer.readItemStack(buffer);
        allowPlacedBlockRecovery = buffer.readBoolean();
        toolProtectionEnabled = buffer.readBoolean();
        traceId = buffer.readLong(); sequence = RtsPacketBuffer.readVarInt(buffer);
        clientTick = buffer.readLong(); heldMs = RtsPacketBuffer.readVarInt(buffer);
        inputKind = buffer.readByte(); stopOrigin = buffer.readByte();
        shiftDown = buffer.readBoolean();
        hitX = buffer.readDouble(); hitY = buffer.readDouble(); hitZ = buffer.readDouble();
        rayOriginX = buffer.readDouble(); rayOriginY = buffer.readDouble(); rayOriginZ = buffer.readDouble();
        rayDirX = buffer.readDouble(); rayDirY = buffer.readDouble(); rayDirZ = buffer.readDouble();
    }

    @Override public void toBytes(ByteBuf buffer) {
        if (pos == null) throw new IllegalArgumentException("mine pos");
        buffer.writeLong(pos.toLong()); buffer.writeByte(face); buffer.writeBoolean(start);
        buffer.writeByte(toolSlot); RtsPacketBuffer.writeString(buffer, toolItemId, 256, "tool id");
        RtsPacketBuffer.writeItemStack(buffer, toolPrototype);
        buffer.writeBoolean(allowPlacedBlockRecovery); buffer.writeBoolean(toolProtectionEnabled);
        buffer.writeLong(traceId); RtsPacketBuffer.writeVarInt(buffer, sequence);
        buffer.writeLong(clientTick); RtsPacketBuffer.writeVarInt(buffer, heldMs);
        buffer.writeByte(inputKind); buffer.writeByte(stopOrigin);
        buffer.writeBoolean(shiftDown);
        buffer.writeDouble(hitX); buffer.writeDouble(hitY); buffer.writeDouble(hitZ);
        buffer.writeDouble(rayOriginX); buffer.writeDouble(rayOriginY); buffer.writeDouble(rayOriginZ);
        buffer.writeDouble(rayDirX); buffer.writeDouble(rayDirY); buffer.writeDouble(rayDirZ);
    }

    public boolean isValid() {
        return pos != null && face >= 0 && face < EnumFacing.values().length
                && toolSlot >= 0 && toolSlot <= 8 && toolItemId != null && toolItemId.length() <= 256
                && sequence >= 0 && heldMs >= 0;
    }

    public BlockPos pos() { return pos; }
    public byte face() { return face; }
    public boolean start() { return start; }
    public byte toolSlot() { return toolSlot; }
    public String toolItemId() { return toolItemId; }
    public ItemStack toolPrototype() { return toolPrototype; }
    public boolean allowPlacedBlockRecovery() { return allowPlacedBlockRecovery; }
    public boolean toolProtectionEnabled() { return toolProtectionEnabled; }
    @Override public long traceId() { return traceId; }
    public int sequence() { return sequence; }
    public long clientTick() { return clientTick; }
    public int heldMs() { return heldMs; }
    public byte inputKind() { return inputKind; }
    public byte stopOrigin() { return stopOrigin; }
    public boolean shiftDown() { return shiftDown; }
    public double hitX() { return hitX; }
    public double hitY() { return hitY; }
    public double hitZ() { return hitZ; }
    public double rayOriginX() { return rayOriginX; }
    public double rayOriginY() { return rayOriginY; }
    public double rayOriginZ() { return rayOriginZ; }
    public double rayDirX() { return rayDirX; }
    public double rayDirY() { return rayDirY; }
    public double rayDirZ() { return rayDirZ; }
}
