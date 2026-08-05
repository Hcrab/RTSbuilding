package com.rtsbuilding.rtsbuilding.network.progression;

import com.rtsbuilding.rtsbuilding.network.RtsPacketBuffer;
import io.netty.buffer.ByteBuf;
import com.rtsbuilding.rtsbuilding.platform.math.BlockPos;
import cpw.mods.fml.common.network.simpleimpl.IMessage;

/** 服务端权威的 RTS 生存进度与家园约束快照。 */
public final class S2CRtsProgressionStatePayload implements IMessage {
    private static final int MAX_DIMENSION_CHARS = 128;
    private static final int MAX_LIMIT = 10_000_000;

    private boolean enabled;
    private boolean homeSet;
    private BlockPos homePos = BlockPos.ORIGIN;
    private String homeDimension = "";
    private long homeCooldownTicks;
    private int radiusBlocks;
    private int fluidCapacityBuckets;
    private int ultimineLimit;
    private boolean bypassHomeRadius;

    public S2CRtsProgressionStatePayload() {
    }

    public S2CRtsProgressionStatePayload(boolean enabled, boolean homeSet, BlockPos homePos,
                                         String homeDimension, long homeCooldownTicks, int radiusBlocks,
                                         int fluidCapacityBuckets, int ultimineLimit, boolean bypassHomeRadius) {
        this.enabled = enabled;
        this.homeSet = homeSet;
        this.homePos = homePos == null ? BlockPos.ORIGIN : homePos;
        this.homeDimension = boundedDimension(homeDimension);
        this.homeCooldownTicks = boundedCooldown(homeCooldownTicks);
        this.radiusBlocks = boundedLimit(radiusBlocks, "radius blocks");
        this.fluidCapacityBuckets = boundedLimit(fluidCapacityBuckets, "fluid capacity buckets");
        this.ultimineLimit = boundedLimit(ultimineLimit, "ultimine limit");
        this.bypassHomeRadius = bypassHomeRadius;
    }

    public boolean enabled() { return this.enabled; }
    public boolean homeSet() { return this.homeSet; }
    public BlockPos homePos() { return this.homePos; }
    public String homeDimension() { return this.homeDimension; }
    public long homeCooldownTicks() { return this.homeCooldownTicks; }
    public int radiusBlocks() { return this.radiusBlocks; }
    public int fluidCapacityBuckets() { return this.fluidCapacityBuckets; }
    public int ultimineLimit() { return this.ultimineLimit; }
    public boolean bypassHomeRadius() { return this.bypassHomeRadius; }

    @Override
    public void fromBytes(ByteBuf buffer) {
        this.enabled = buffer.readBoolean();
        this.homeSet = buffer.readBoolean();
        this.homePos = BlockPos.fromLong(buffer.readLong());
        this.homeDimension = RtsPacketBuffer.readString(buffer, MAX_DIMENSION_CHARS, "home dimension");
        this.homeCooldownTicks = boundedCooldown(buffer.readLong());
        this.radiusBlocks = RtsPacketBuffer.readBoundedCount(buffer, MAX_LIMIT, "radius blocks");
        this.fluidCapacityBuckets = RtsPacketBuffer.readBoundedCount(buffer, MAX_LIMIT, "fluid capacity buckets");
        this.ultimineLimit = RtsPacketBuffer.readBoundedCount(buffer, MAX_LIMIT, "ultimine limit");
        this.bypassHomeRadius = buffer.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeBoolean(this.enabled);
        buffer.writeBoolean(this.homeSet);
        buffer.writeLong((this.homePos == null ? BlockPos.ORIGIN : this.homePos).toLong());
        RtsPacketBuffer.writeString(buffer, boundedDimension(this.homeDimension), MAX_DIMENSION_CHARS,
                "home dimension");
        buffer.writeLong(boundedCooldown(this.homeCooldownTicks));
        RtsPacketBuffer.writeVarInt(buffer, boundedLimit(this.radiusBlocks, "radius blocks"));
        RtsPacketBuffer.writeVarInt(buffer, boundedLimit(this.fluidCapacityBuckets, "fluid capacity buckets"));
        RtsPacketBuffer.writeVarInt(buffer, boundedLimit(this.ultimineLimit, "ultimine limit"));
        buffer.writeBoolean(this.bypassHomeRadius);
    }

    private static String boundedDimension(String value) {
        String safe = value == null ? "" : value;
        if (safe.length() > MAX_DIMENSION_CHARS) {
            throw new IllegalArgumentException("home dimension exceeds " + MAX_DIMENSION_CHARS + " characters");
        }
        return safe;
    }

    private static long boundedCooldown(long value) {
        if (value < 0L) throw new IllegalArgumentException("home cooldown ticks out of range: " + value);
        return value;
    }

    private static int boundedLimit(int value, String name) {
        if (value < 0 || value > MAX_LIMIT) throw new IllegalArgumentException(name + " out of range: " + value);
        return value;
    }
}
