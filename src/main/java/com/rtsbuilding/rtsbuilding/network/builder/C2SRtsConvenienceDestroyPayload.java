package com.rtsbuilding.rtsbuilding.network.builder;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.common.destruction.RtsConvenienceDestroyMode;
import com.rtsbuilding.rtsbuilding.common.destruction.RtsConvenienceDestroySettings;
import com.rtsbuilding.rtsbuilding.forgecompat.network.CustomPacketPayload;
import com.rtsbuilding.rtsbuilding.forgecompat.network.RegistryFriendlyByteBuf;
import com.rtsbuilding.rtsbuilding.forgecompat.network.StreamCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * 便捷破坏的声明式请求；客户端不得提交可信目标数组，服务端只接受锚点和有界设置。
 */
public record C2SRtsConvenienceDestroyPayload(
        long requestId,
        RtsConvenienceDestroyMode mode,
        BlockPos anchor,
        byte face,
        RtsConvenienceDestroySettings settings,
        byte toolSlot,
        String toolItemId,
        ItemStack toolPrototype,
        boolean toolProtectionEnabled) implements CustomPacketPayload {
    public static final Type<C2SRtsConvenienceDestroyPayload> TYPE = new Type<>(
            new ResourceLocation(RtsbuildingMod.MODID, "c2s_rts_convenience_destroy"),
            C2SRtsConvenienceDestroyPayload.class);

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SRtsConvenienceDestroyPayload> STREAM_CODEC =
            StreamCodec.of(C2SRtsConvenienceDestroyPayload::encode, C2SRtsConvenienceDestroyPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buf, C2SRtsConvenienceDestroyPayload payload) {
        RtsConvenienceDestroySettings value = payload.settings() == null
                ? RtsConvenienceDestroySettings.DEFAULT : payload.settings();
        buf.writeLong(payload.requestId());
        buf.writeByte((payload.mode() == null
                ? RtsConvenienceDestroyMode.REPEAT_BOX : payload.mode()).ordinal());
        buf.writeBlockPos(payload.anchor() == null ? BlockPos.ZERO : payload.anchor());
        buf.writeByte(payload.face());
        buf.writeVarInt(value.sizeX());
        buf.writeVarInt(value.sizeY());
        buf.writeVarInt(value.sizeZ());
        buf.writeVarInt(value.chunkUp());
        buf.writeVarInt(value.chunkDown());
        buf.writeVarInt(value.treeMaxBlocks());
        buf.writeByte(payload.toolSlot());
        buf.writeUtf(payload.toolItemId() == null ? "" : payload.toolItemId(), 256);
        ItemStack prototype = payload.toolPrototype() == null ? ItemStack.EMPTY : payload.toolPrototype();
        buf.writeBoolean(!prototype.isEmpty());
        if (!prototype.isEmpty()) {
            com.rtsbuilding.rtsbuilding.forgecompat.network.RtsForgeBufCodecs.writeItem(buf, prototype);
        }
        buf.writeBoolean(payload.toolProtectionEnabled());
    }

    private static C2SRtsConvenienceDestroyPayload decode(RegistryFriendlyByteBuf buf) {
        long requestId = buf.readLong();
        int ordinal = buf.readUnsignedByte();
        RtsConvenienceDestroyMode[] modes = RtsConvenienceDestroyMode.values();
        RtsConvenienceDestroyMode mode = modes[Math.min(ordinal, modes.length - 1)];
        return new C2SRtsConvenienceDestroyPayload(
                requestId,
                mode,
                buf.readBlockPos().immutable(),
                buf.readByte(),
                new RtsConvenienceDestroySettings(
                        buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                        buf.readVarInt(), buf.readVarInt(), buf.readVarInt()),
                buf.readByte(),
                buf.readUtf(256),
                buf.readBoolean()
                        ? com.rtsbuilding.rtsbuilding.forgecompat.network.RtsForgeBufCodecs.readItem(buf)
                        : ItemStack.EMPTY,
                buf.readBoolean());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
