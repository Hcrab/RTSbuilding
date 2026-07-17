package com.rtsbuilding.rtsbuilding.network.craft;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public record C2SRtsJeiTransferPayload(
        String recipeId,
        List<ItemStack> ingredientPrototypes,
        boolean maxTransfer,
        boolean clearGridFirst) implements CustomPacketPayload {
    private static final int GRID_SIZE = 9;

    public static final Type<C2SRtsJeiTransferPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(RtsbuildingMod.MODID, "c2s_rts_jei_transfer"));

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
                            ItemStack.STREAM_CODEC.encode(buf, prototype.copyWithCount(1));
                        }
                        buf.writeBoolean(payload.maxTransfer());
                        buf.writeBoolean(payload.clearGridFirst());
                    },
                    (buf) -> {
                        String recipeId = buf.readUtf(256);
                        List<ItemStack> prototypes = new ArrayList<>(GRID_SIZE);
                        for (int i = 0; i < GRID_SIZE; i++) {
                            prototypes.add(buf.readBoolean() ? ItemStack.STREAM_CODEC.decode(buf) : ItemStack.EMPTY);
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
