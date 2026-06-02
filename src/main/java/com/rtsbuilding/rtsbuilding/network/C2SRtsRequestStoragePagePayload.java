package com.rtsbuilding.rtsbuilding.network;

import java.util.ArrayList;
import java.util.List;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;

import com.rtsbuilding.rtsbuilding.forgecompat.network.RegistryFriendlyByteBuf;
import com.rtsbuilding.rtsbuilding.forgecompat.network.StreamCodec;
import com.rtsbuilding.rtsbuilding.forgecompat.network.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record C2SRtsRequestStoragePagePayload(
        int page,
        String search,
        String category,
        byte sort,
        boolean ascending,
        int pageSize,
        boolean pinyinSearchEnabled,
        List<String> localizedSearchMatches) implements CustomPacketPayload {
    public static final Type<C2SRtsRequestStoragePagePayload> TYPE = new Type<>(
            new ResourceLocation(RtsbuildingMod.MODID, "c2s_rts_request_storage_page"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SRtsRequestStoragePagePayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.page());
                buf.writeUtf(payload.search(), 128);
                buf.writeUtf(payload.category(), 128);
                buf.writeByte(payload.sort());
                buf.writeBoolean(payload.ascending());
                buf.writeVarInt(payload.pageSize());
                buf.writeBoolean(payload.pinyinSearchEnabled());
                writeStringList(buf, payload.localizedSearchMatches());
            },
            (buf) -> new C2SRtsRequestStoragePagePayload(
                    buf.readVarInt(),
                    buf.readUtf(128),
                    buf.readUtf(128),
                    buf.readByte(),
                    buf.readBoolean(),
                    buf.readVarInt(),
                    buf.readBoolean(),
                    readStringList(buf)));

    private static void writeStringList(RegistryFriendlyByteBuf buf, List<String> values) {
        int size = values == null ? 0 : Math.min(values.size(), 8192);
        buf.writeVarInt(size);
        for (int i = 0; i < size; i++) {
            buf.writeUtf(values.get(i) == null ? "" : values.get(i), 128);
        }
    }

    private static List<String> readStringList(RegistryFriendlyByteBuf buf) {
        int size = Math.min(Math.max(0, buf.readVarInt()), 8192);
        List<String> values = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            values.add(buf.readUtf(128));
        }
        return values;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
