package com.rtsbuilding.rtsbuilding.network.builder;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.common.destruction.RtsConvenienceDestroyMode;
import com.rtsbuilding.rtsbuilding.common.destruction.RtsConvenienceDestroySettings;
import com.rtsbuilding.rtsbuilding.network.RtsTracedPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * 带端到端 trace 的声明式便捷破坏请求。
 *
 * <p>与 legacy 载荷相同，本包只传锚点和有界设置，不接受客户端规划出的坐标数组。</p>
 */
public record C2SRtsConvenienceDestroyTracePayload(
        long traceId,
        int sequence,
        long clientTick,
        int heldMs,
        byte inputKind,
        byte stopOrigin,
        long requestId,
        RtsConvenienceDestroyMode mode,
        BlockPos anchor,
        byte face,
        RtsConvenienceDestroySettings settings,
        byte toolSlot,
        String toolItemId,
        ItemStack toolPrototype,
        boolean toolProtectionEnabled) implements CustomPacketPayload, RtsTracedPayload {

    public static final Type<C2SRtsConvenienceDestroyTracePayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(RtsbuildingMod.MODID, "c2s_rts_convenience_destroy_v2"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SRtsConvenienceDestroyTracePayload> STREAM_CODEC =
            StreamCodec.of(C2SRtsConvenienceDestroyTracePayload::encode,
                    C2SRtsConvenienceDestroyTracePayload::decode);

    private static void encode(RegistryFriendlyByteBuf buf, C2SRtsConvenienceDestroyTracePayload payload) {
        C2SRtsMineTracePayload.writeTraceHeader(
                buf, payload.traceId(), payload.sequence(), payload.clientTick(),
                payload.heldMs(), payload.inputKind(), payload.stopOrigin());
        RtsConvenienceDestroySettings settings = payload.settings() == null
                ? RtsConvenienceDestroySettings.DEFAULT : payload.settings();
        buf.writeLong(payload.requestId());
        buf.writeByte((payload.mode() == null ? RtsConvenienceDestroyMode.REPEAT_BOX : payload.mode()).ordinal());
        buf.writeBlockPos(payload.anchor() == null ? BlockPos.ZERO : payload.anchor());
        buf.writeByte(payload.face());
        buf.writeVarInt(settings.sizeX());
        buf.writeVarInt(settings.sizeY());
        buf.writeVarInt(settings.sizeZ());
        buf.writeVarInt(settings.chunkUp());
        buf.writeVarInt(settings.chunkDown());
        buf.writeVarInt(settings.treeMaxBlocks());
        buf.writeByte(payload.toolSlot());
        C2SRtsMineTracePayload.writeTool(buf, payload.toolItemId(), payload.toolPrototype());
        buf.writeBoolean(payload.toolProtectionEnabled());
    }

    private static C2SRtsConvenienceDestroyTracePayload decode(RegistryFriendlyByteBuf buf) {
        C2SRtsMineTracePayload.Header header = C2SRtsMineTracePayload.readTraceHeader(buf);
        long requestId = buf.readLong();
        int modeOrdinal = buf.readUnsignedByte();
        RtsConvenienceDestroyMode[] modes = RtsConvenienceDestroyMode.values();
        RtsConvenienceDestroyMode mode = modes[Math.min(modeOrdinal, modes.length - 1)];
        BlockPos anchor = buf.readBlockPos().immutable();
        byte face = buf.readByte();
        RtsConvenienceDestroySettings settings = new RtsConvenienceDestroySettings(
                buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
        byte toolSlot = buf.readByte();
        String toolItemId = buf.readUtf(256);
        ItemStack toolPrototype = buf.readBoolean()
                ? ItemStack.STREAM_CODEC.decode(buf) : ItemStack.EMPTY;
        return new C2SRtsConvenienceDestroyTracePayload(
                header.traceId(), header.sequence(), header.clientTick(), header.heldMs(),
                header.inputKind(), header.stopOrigin(), requestId, mode, anchor, face, settings,
                toolSlot, toolItemId, toolPrototype, buf.readBoolean());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
