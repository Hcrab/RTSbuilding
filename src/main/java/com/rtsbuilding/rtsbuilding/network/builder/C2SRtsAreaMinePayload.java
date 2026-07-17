package com.rtsbuilding.rtsbuilding.network.builder;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

public record C2SRtsAreaMinePayload(
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
        boolean toolProtectionEnabled) implements CustomPacketPayload {
    public static final Type<C2SRtsAreaMinePayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(RtsbuildingMod.MODID, "c2s_rts_area_mine"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SRtsAreaMinePayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeInt(payload.minX());
                buf.writeInt(payload.maxX());
                buf.writeInt(payload.minY());
                buf.writeInt(payload.maxY());
                buf.writeInt(payload.minZ());
                buf.writeInt(payload.maxZ());
                buf.writeByte(payload.toolSlot());
                buf.writeUtf(payload.toolItemId() == null ? "" : payload.toolItemId(), 256);
                ItemStack toolPrototype = payload.toolPrototype() == null ? ItemStack.EMPTY : payload.toolPrototype();
                buf.writeBoolean(!toolPrototype.isEmpty());
                if (!toolPrototype.isEmpty()) {
                    ItemStack.STREAM_CODEC.encode(buf, toolPrototype);
                }
                buf.writeByte(payload.shapeType());
                buf.writeByte(payload.fillType());
                buf.writeBoolean(payload.toolProtectionEnabled());
            },
            (buf) -> new C2SRtsAreaMinePayload(
                    buf.readInt(),
                    buf.readInt(),
                    buf.readInt(),
                    buf.readInt(),
                    buf.readInt(),
                    buf.readInt(),
                    buf.readByte(),
                    buf.readUtf(256),
                    buf.readBoolean() ? ItemStack.STREAM_CODEC.decode(buf) : ItemStack.EMPTY,
                    buf.readByte(),
                    buf.readByte(),
                    buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
