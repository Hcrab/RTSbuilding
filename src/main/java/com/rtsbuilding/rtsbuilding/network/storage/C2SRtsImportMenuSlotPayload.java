package com.rtsbuilding.rtsbuilding.network.storage;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record C2SRtsImportMenuSlotPayload(int menuSlot) implements CustomPacketPayload {
    public static final Type<C2SRtsImportMenuSlotPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RtsbuildingMod.MODID, "c2s_rts_import_menu_slot"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SRtsImportMenuSlotPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> buf.writeVarInt(payload.menuSlot()),
                    (buf) -> new C2SRtsImportMenuSlotPayload(buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
