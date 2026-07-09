package com.rtsbuilding.rtsbuilding.network.storage;

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
    public static final int MAX_LOCALIZED_SEARCH_MATCHES = 256;

    public static final Type<C2SRtsRequestStoragePagePayload> TYPE = new Type<>(new ResourceLocation(RtsbuildingMod.MODID, "c2s_rts_request_storage_page"), C2SRtsRequestStoragePagePayload.class);

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

    public static List<String> limitLocalizedSearchMatches(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        int size = Math.min(values.size(), MAX_LOCALIZED_SEARCH_MATCHES);
        List<String> limited = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            limited.add(values.get(i) == null ? "" : values.get(i));
        }
        return List.copyOf(limited);
    }

    private static void writeStringList(RegistryFriendlyByteBuf buf, List<String> values) {
        List<String> limited = limitLocalizedSearchMatches(values);
        buf.writeVarInt(limited.size());
        for (String value : limited) {
            buf.writeUtf(value, 128);
        }
    }

    private static List<String> readStringList(RegistryFriendlyByteBuf buf) {
        int declaredSize = Math.max(0, buf.readVarInt());
        int storedSize = Math.min(declaredSize, MAX_LOCALIZED_SEARCH_MATCHES);
        List<String> values = new ArrayList<>(storedSize);
        for (int i = 0; i < declaredSize; i++) {
            String value = buf.readUtf(128);
            if (i < storedSize) {
                values.add(value);
            }
        }
        return values;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
