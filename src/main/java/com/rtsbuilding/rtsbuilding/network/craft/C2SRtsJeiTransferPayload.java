package com.rtsbuilding.rtsbuilding.network.craft;

import java.util.ArrayList;
import java.util.List;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;

import com.rtsbuilding.rtsbuilding.forgecompat.network.RegistryFriendlyByteBuf;
import com.rtsbuilding.rtsbuilding.forgecompat.network.StreamCodec;
import com.rtsbuilding.rtsbuilding.forgecompat.network.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public record C2SRtsJeiTransferPayload(
        String recipeId,
        List<ItemStack> ingredientPrototypes,
        boolean maxTransfer,
        boolean clearGridFirst) implements CustomPacketPayload {
    private static final int GRID_SIZE = 9;

    public static final Type<C2SRtsJeiTransferPayload> TYPE = new Type<>(new ResourceLocation(RtsbuildingMod.MODID, "c2s_rts_jei_transfer"), C2SRtsJeiTransferPayload.class);

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SRtsJeiTransferPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeUtf(payload.recipeId(), 256);
                        List<ItemStack> prototypes = payload.ingredientPrototypes();
                        for (int i = 0; i < GRID_SIZE; i++) {
                            ItemStack prototype = prototypes != null && i < prototypes.size() ? prototypes.get(i) : ItemStack.EMPTY;
                            if (prototype == null || prototype.isEmpty()) {
                                buf.writeBoolean(false);
                                continue;
                            }
                            buf.writeBoolean(true);
                            com.rtsbuilding.rtsbuilding.forgecompat.network.RtsForgeBufCodecs.writeItem(buf, prototype.copyWithCount(1));
                        }
                        buf.writeBoolean(payload.maxTransfer());
                        buf.writeBoolean(payload.clearGridFirst());
                    },
                    (buf) -> {
                        String recipeId = buf.readUtf(256);
                        List<ItemStack> prototypes = new ArrayList<>(GRID_SIZE);
                        for (int i = 0; i < GRID_SIZE; i++) {
                            prototypes.add(buf.readBoolean() ? com.rtsbuilding.rtsbuilding.forgecompat.network.RtsForgeBufCodecs.readItem(buf) : ItemStack.EMPTY);
                        }
                        return new C2SRtsJeiTransferPayload(
                                recipeId,
                                prototypes,
                                buf.readBoolean(),
                                buf.readBoolean());
                    });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
