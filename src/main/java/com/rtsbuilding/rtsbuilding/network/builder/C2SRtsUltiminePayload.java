package com.rtsbuilding.rtsbuilding.network.builder;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

public record C2SRtsUltiminePayload(
        BlockPos pos,
        byte face,
        byte toolSlot,
        String toolItemId,
        ItemStack toolPrototype,
        short limit,
        byte mode,
        boolean toolProtectionEnabled) implements CustomPacketPayload {
    public static final Type<C2SRtsUltiminePayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(RtsbuildingMod.MODID, "c2s_rts_ultimine"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SRtsUltiminePayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeBlockPos(payload.pos());
                buf.writeByte(payload.face());
                buf.writeByte(payload.toolSlot());
                buf.writeUtf(payload.toolItemId() == null ? "" : payload.toolItemId(), 256);
                ItemStack toolPrototype = payload.toolPrototype() == null ? ItemStack.EMPTY : payload.toolPrototype();
                buf.writeBoolean(!toolPrototype.isEmpty());
                if (!toolPrototype.isEmpty()) {
                    ItemStack.STREAM_CODEC.encode(buf, toolPrototype);
                }
                buf.writeShort(payload.limit());
                buf.writeByte(payload.mode());
                buf.writeBoolean(payload.toolProtectionEnabled());
            },
            (buf) -> new C2SRtsUltiminePayload(
                    buf.readBlockPos(),
                    buf.readByte(),
                    buf.readByte(),
                    buf.readUtf(256),
                    buf.readBoolean() ? ItemStack.STREAM_CODEC.decode(buf) : ItemStack.EMPTY,
                    buf.readShort(),
                    buf.readByte(),
                    buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
