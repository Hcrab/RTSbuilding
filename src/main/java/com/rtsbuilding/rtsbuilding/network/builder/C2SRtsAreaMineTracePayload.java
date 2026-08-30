package com.rtsbuilding.rtsbuilding.network.builder;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.network.RtsTracedPayload;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** 带 trace 的区域挖掘 v2 包。 */
public record C2SRtsAreaMineTracePayload(
        long traceId,
        int sequence,
        long clientTick,
        int heldMs,
        byte inputKind,
        byte stopOrigin,
        int minX,
        int maxX,
        int minY,
        int maxY,
        int minZ,
        int maxZ,
        byte toolSlot,
        String toolItemId,
        ItemStack toolPrototype,
        byte shapeType,
        byte fillType,
        boolean toolProtectionEnabled) implements CustomPacketPayload, RtsTracedPayload {
    public static final Type<C2SRtsAreaMineTracePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RtsbuildingMod.MODID, "c2s_rts_area_mine_v2"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SRtsAreaMineTracePayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                C2SRtsMineTracePayload.writeTraceHeader(buf, payload.traceId(), payload.sequence(),
                        payload.clientTick(), payload.heldMs(), payload.inputKind(), payload.stopOrigin());
                buf.writeInt(payload.minX());
                buf.writeInt(payload.maxX());
                buf.writeInt(payload.minY());
                buf.writeInt(payload.maxY());
                buf.writeInt(payload.minZ());
                buf.writeInt(payload.maxZ());
                buf.writeByte(payload.toolSlot());
                C2SRtsMineTracePayload.writeTool(buf, payload.toolItemId(), payload.toolPrototype());
                buf.writeByte(payload.shapeType());
                buf.writeByte(payload.fillType());
                buf.writeBoolean(payload.toolProtectionEnabled());
            },
            buf -> {
                var header = C2SRtsMineTracePayload.readTraceHeader(buf);
                int minX = buf.readInt();
                int maxX = buf.readInt();
                int minY = buf.readInt();
                int maxY = buf.readInt();
                int minZ = buf.readInt();
                int maxZ = buf.readInt();
                byte toolSlot = buf.readByte();
                String toolId = buf.readUtf(256);
                ItemStack tool = buf.readBoolean() ? ItemStack.STREAM_CODEC.decode(buf) : ItemStack.EMPTY;
                return new C2SRtsAreaMineTracePayload(
                        header.traceId(), header.sequence(), header.clientTick(), header.heldMs(),
                        header.inputKind(), header.stopOrigin(), minX, maxX, minY, maxY, minZ, maxZ,
                        toolSlot, toolId, tool, buf.readByte(), buf.readByte(), buf.readBoolean());
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
