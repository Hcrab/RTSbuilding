package com.rtsbuilding.rtsbuilding.network.craft;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 通用 JEI 配方转移负载，不绑定特定 RecipeType。
 * 客户端统计所有 INPUT 槽位的物品及数量后发送，服务端按列表从关联存储取料填入合成格。
 */
public record C2SRtsJeiUniversalTransferPayload(
        List<ItemStack> prototypes,
        List<Integer> quantities,
        boolean clearGridFirst) implements CustomPacketPayload {

    public static final Type<C2SRtsJeiUniversalTransferPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RtsbuildingMod.MODID, "c2s_rts_jei_universal_transfer"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SRtsJeiUniversalTransferPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        int size = Math.min(payload.prototypes().size(), payload.quantities().size());
                        if (payload.prototypes().size() != payload.quantities().size()) {
                            RtsbuildingMod.LOGGER.warn(
                                    "C2SRtsJeiUniversalTransfer: prototypes ({}) and quantities ({}) size mismatch; truncating to {}",
                                    payload.prototypes().size(), payload.quantities().size(), size);
                        }
                        buf.writeVarInt(size);
                        for (int i = 0; i < size; i++) {
                            ItemStack stack = payload.prototypes().get(i);
                            ItemStack.STREAM_CODEC.encode(buf, stack == null || stack.isEmpty()
                                    ? ItemStack.EMPTY : stack.copyWithCount(1));
                            buf.writeVarInt(payload.quantities().get(i));
                        }
                        buf.writeBoolean(payload.clearGridFirst());
                    },
                    (buf) -> {
                        int size = buf.readVarInt();
                        List<ItemStack> prototypes = new ArrayList<>(size);
                        List<Integer> quantities = new ArrayList<>(size);
                        for (int i = 0; i < size; i++) {
                            prototypes.add(ItemStack.STREAM_CODEC.decode(buf));
                            quantities.add(buf.readVarInt());
                        }
                        return new C2SRtsJeiUniversalTransferPayload(
                                prototypes,
                                quantities,
                                buf.readBoolean());
                    });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
