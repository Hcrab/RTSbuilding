package com.rtsbuilding.rtsbuilding.network.craft;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record C2SRtsCraftRecipePayload(String recipeId, int craftCount) implements CustomPacketPayload {
    public static final Type<C2SRtsCraftRecipePayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(RtsbuildingMod.MODID, "c2s_rts_craft_recipe"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SRtsCraftRecipePayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeUtf(payload.recipeId() == null ? "" : payload.recipeId(), 256);
                        buf.writeVarInt(Math.max(1, payload.craftCount()));
                    },
                    (buf) -> new C2SRtsCraftRecipePayload(
                            buf.readUtf(256),
                            Math.max(1, buf.readVarInt())));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
