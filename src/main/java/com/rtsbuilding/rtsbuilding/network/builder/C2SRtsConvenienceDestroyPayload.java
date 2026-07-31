package com.rtsbuilding.rtsbuilding.network.builder;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.common.destruction.RtsConvenienceDestroyMode;
import com.rtsbuilding.rtsbuilding.common.destruction.RtsConvenienceDestroySettings;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * 便捷破坏的声明式请求。
 *
 * <p>载荷不接受客户端规划出的坐标数组；服务端只信任锚点和受硬上限约束的设置，
 * 并在玩家当前维度重新读取世界。</p>
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
            ResourceLocation.fromNamespaceAndPath(RtsbuildingMod.MODID, "c2s_rts_convenience_destroy"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SRtsConvenienceDestroyPayload> STREAM_CODEC =
            StreamCodec.of(C2SRtsConvenienceDestroyPayload::encode, C2SRtsConvenienceDestroyPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buf, C2SRtsConvenienceDestroyPayload payload) {
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
        buf.writeUtf(payload.toolItemId() == null ? "" : payload.toolItemId(), 256);
        ItemStack prototype = payload.toolPrototype() == null ? ItemStack.EMPTY : payload.toolPrototype();
        buf.writeBoolean(!prototype.isEmpty());
        if (!prototype.isEmpty()) {
            ItemStack.STREAM_CODEC.encode(buf, prototype);
        }
        buf.writeBoolean(payload.toolProtectionEnabled());
    }

    private static C2SRtsConvenienceDestroyPayload decode(RegistryFriendlyByteBuf buf) {
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
        boolean toolProtectionEnabled = buf.readBoolean();
        return new C2SRtsConvenienceDestroyPayload(
                requestId, mode, anchor, face, settings, toolSlot,
                toolItemId, toolPrototype, toolProtectionEnabled);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
