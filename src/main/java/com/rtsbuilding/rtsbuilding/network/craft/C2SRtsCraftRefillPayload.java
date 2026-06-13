package com.rtsbuilding.rtsbuilding.network.craft;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.forgecompat.network.RegistryFriendlyByteBuf;
import com.rtsbuilding.rtsbuilding.forgecompat.network.StreamCodec;
import com.rtsbuilding.rtsbuilding.forgecompat.network.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public record C2SRtsCraftRefillPayload(
        List<ItemStack> blueprintStacks,
        String craftedItemId,
        int craftedCount) implements CustomPacketPayload {
    private static final int BLUEPRINT_SIZE = 9;
    public static final Type<C2SRtsCraftRefillPayload> TYPE = new Type<>(
            new ResourceLocation(RtsbuildingMod.MODID, "c2s_rts_craft_refill"), C2SRtsCraftRefillPayload.class);

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SRtsCraftRefillPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        List<ItemStack> stacks = payload.blueprintStacks();
                        for (int i = 0; i < BLUEPRINT_SIZE; i++) {
                            ItemStack stack = stacks != null && i < stacks.size() ? stacks.get(i) : ItemStack.EMPTY;
                            if (stack == null || stack.isEmpty()) {
                                buf.writeBoolean(false);
                                continue;
                            }
                            buf.writeBoolean(true);
                            com.rtsbuilding.rtsbuilding.forgecompat.network.RtsForgeBufCodecs.writeItem(buf, stack.copyWithCount(1));
                        }
                        buf.writeUtf(payload.craftedItemId() == null ? "" : payload.craftedItemId(), 128);
                        buf.writeVarInt(Math.max(0, payload.craftedCount()));
                    },
                    (buf) -> {
                        List<ItemStack> stacks = new ArrayList<>(BLUEPRINT_SIZE);
                        for (int i = 0; i < BLUEPRINT_SIZE; i++) {
                            stacks.add(buf.readBoolean() ? com.rtsbuilding.rtsbuilding.forgecompat.network.RtsForgeBufCodecs.readItem(buf) : ItemStack.EMPTY);
                        }
                        return new C2SRtsCraftRefillPayload(stacks, buf.readUtf(128), buf.readVarInt());
                    });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
