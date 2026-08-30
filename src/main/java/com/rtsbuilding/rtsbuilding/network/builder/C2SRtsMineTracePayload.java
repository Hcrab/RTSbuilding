package com.rtsbuilding.rtsbuilding.network.builder;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.network.RtsTracedPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** 带端到端 trace 的单方块挖掘 v2 包；legacy 包继续注册一个补丁周期。 */
public record C2SRtsMineTracePayload(
        long traceId,
        int sequence,
        long clientTick,
        int heldMs,
        byte inputKind,
        byte stopOrigin,
        BlockPos pos,
        byte face,
        boolean start,
        byte toolSlot,
        String toolItemId,
        ItemStack toolPrototype,
        boolean allowPlacedBlockRecovery,
        boolean toolProtectionEnabled,
        boolean shiftDown,
        double hitX,
        double hitY,
        double hitZ,
        double rayOriginX,
        double rayOriginY,
        double rayOriginZ,
        double rayDirX,
        double rayDirY,
        double rayDirZ) implements CustomPacketPayload, RtsTracedPayload {
    public static final Type<C2SRtsMineTracePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RtsbuildingMod.MODID, "c2s_rts_mine_v2"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SRtsMineTracePayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                writeTraceHeader(buf, payload.traceId(), payload.sequence(), payload.clientTick(),
                        payload.heldMs(), payload.inputKind(), payload.stopOrigin());
                buf.writeBlockPos(payload.pos());
                buf.writeByte(payload.face());
                buf.writeBoolean(payload.start());
                buf.writeByte(payload.toolSlot());
                writeTool(buf, payload.toolItemId(), payload.toolPrototype());
                buf.writeBoolean(payload.allowPlacedBlockRecovery());
                buf.writeBoolean(payload.toolProtectionEnabled());
                buf.writeBoolean(payload.shiftDown());
                buf.writeDouble(payload.hitX());
                buf.writeDouble(payload.hitY());
                buf.writeDouble(payload.hitZ());
                buf.writeDouble(payload.rayOriginX());
                buf.writeDouble(payload.rayOriginY());
                buf.writeDouble(payload.rayOriginZ());
                buf.writeDouble(payload.rayDirX());
                buf.writeDouble(payload.rayDirY());
                buf.writeDouble(payload.rayDirZ());
            },
            buf -> {
                Header header = readTraceHeader(buf);
                BlockPos pos = buf.readBlockPos();
                byte face = buf.readByte();
                boolean start = buf.readBoolean();
                byte toolSlot = buf.readByte();
                String toolId = buf.readUtf(256);
                ItemStack tool = buf.readBoolean() ? ItemStack.STREAM_CODEC.decode(buf) : ItemStack.EMPTY;
                return new C2SRtsMineTracePayload(
                        header.traceId(), header.sequence(), header.clientTick(), header.heldMs(),
                        header.inputKind(), header.stopOrigin(), pos, face, start, toolSlot,
                        toolId, tool, buf.readBoolean(), buf.readBoolean(),
                        buf.readBoolean(),
                        buf.readDouble(), buf.readDouble(), buf.readDouble(),
                        buf.readDouble(), buf.readDouble(), buf.readDouble(),
                        buf.readDouble(), buf.readDouble(), buf.readDouble());
            });

    static void writeTraceHeader(RegistryFriendlyByteBuf buf, long traceId, int sequence,
            long clientTick, int heldMs, byte inputKind, byte stopOrigin) {
        buf.writeLong(traceId);
        buf.writeVarInt(sequence);
        buf.writeVarLong(clientTick);
        buf.writeVarInt(Math.max(0, heldMs));
        buf.writeByte(inputKind);
        buf.writeByte(stopOrigin);
    }

    static Header readTraceHeader(RegistryFriendlyByteBuf buf) {
        return new Header(buf.readLong(), buf.readVarInt(), buf.readVarLong(),
                buf.readVarInt(), buf.readByte(), buf.readByte());
    }

    static void writeTool(RegistryFriendlyByteBuf buf, String toolItemId, ItemStack toolPrototype) {
        buf.writeUtf(toolItemId == null ? "" : toolItemId, 256);
        ItemStack tool = toolPrototype == null ? ItemStack.EMPTY : toolPrototype;
        buf.writeBoolean(!tool.isEmpty());
        if (!tool.isEmpty()) ItemStack.STREAM_CODEC.encode(buf, tool);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    record Header(long traceId, int sequence, long clientTick, int heldMs,
            byte inputKind, byte stopOrigin) {
    }
}
