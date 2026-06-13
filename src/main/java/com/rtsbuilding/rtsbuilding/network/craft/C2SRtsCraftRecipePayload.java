package com.rtsbuilding.rtsbuilding.network.craft;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.forgecompat.network.RegistryFriendlyByteBuf;
import com.rtsbuilding.rtsbuilding.forgecompat.network.StreamCodec;
import com.rtsbuilding.rtsbuilding.forgecompat.network.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record C2SRtsCraftRecipePayload(String recipeId, int craftCount) implements CustomPacketPayload {
    public static final Type<C2SRtsCraftRecipePayload> TYPE = new Type<>(
            new ResourceLocation(RtsbuildingMod.MODID, "c2s_rts_craft_recipe"), C2SRtsCraftRecipePayload.class);

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
