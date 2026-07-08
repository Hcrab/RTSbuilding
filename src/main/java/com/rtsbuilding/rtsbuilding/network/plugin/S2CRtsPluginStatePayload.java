package com.rtsbuilding.rtsbuilding.network.plugin;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.forgecompat.network.CustomPacketPayload;
import com.rtsbuilding.rtsbuilding.forgecompat.network.RegistryFriendlyByteBuf;
import com.rtsbuilding.rtsbuilding.forgecompat.network.RtsForgeBufCodecs;
import com.rtsbuilding.rtsbuilding.forgecompat.network.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public record S2CRtsPluginStatePayload(
        List<String> pluginIds,
        List<String> families,
        List<Integer> radiusBlocks,
        List<Boolean> fieldDeployment,
        List<Boolean> personal,
        List<String> ownerNames,
        List<ItemStack> stacks,
        String teamName) implements CustomPacketPayload {
    public static final Type<S2CRtsPluginStatePayload> TYPE = new Type<>(
            new ResourceLocation(RtsbuildingMod.MODID, "s2c_rts_plugin_state"),
            S2CRtsPluginStatePayload.class);

    public static final StreamCodec<RegistryFriendlyByteBuf, S2CRtsPluginStatePayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                int size = Math.min(payload.pluginIds().size(),
                        Math.min(payload.families().size(),
                                Math.min(payload.radiusBlocks().size(),
                                        Math.min(payload.fieldDeployment().size(),
                                                Math.min(payload.personal().size(),
                                                        Math.min(payload.ownerNames().size(), payload.stacks().size()))))));
                buf.writeVarInt(size);
                for (int i = 0; i < size; i++) {
                    buf.writeUtf(payload.pluginIds().get(i) == null ? "" : payload.pluginIds().get(i), 128);
                    buf.writeUtf(payload.families().get(i) == null ? "" : payload.families().get(i), 64);
                    buf.writeVarInt(Math.max(0, payload.radiusBlocks().get(i)));
                    buf.writeBoolean(Boolean.TRUE.equals(payload.fieldDeployment().get(i)));
                    buf.writeBoolean(Boolean.TRUE.equals(payload.personal().get(i)));
                    buf.writeUtf(payload.ownerNames().get(i) == null ? "" : payload.ownerNames().get(i), 64);
                    ItemStack stack = payload.stacks().get(i);
                    RtsForgeBufCodecs.writeItem(buf, stack == null ? ItemStack.EMPTY : stack.copyWithCount(1));
                }
                buf.writeUtf(payload.teamName() == null ? "" : payload.teamName(), 128);
            },
            (buf) -> {
                int size = Math.min(64, Math.max(0, buf.readVarInt()));
                List<String> pluginIds = new ArrayList<>(size);
                List<String> families = new ArrayList<>(size);
                List<Integer> radiusBlocks = new ArrayList<>(size);
                List<Boolean> fieldDeployment = new ArrayList<>(size);
                List<Boolean> personal = new ArrayList<>(size);
                List<String> ownerNames = new ArrayList<>(size);
                List<ItemStack> stacks = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    pluginIds.add(buf.readUtf(128));
                    families.add(buf.readUtf(64));
                    radiusBlocks.add(buf.readVarInt());
                    fieldDeployment.add(buf.readBoolean());
                    personal.add(buf.readBoolean());
                    ownerNames.add(buf.readUtf(64));
                    stacks.add(RtsForgeBufCodecs.readItem(buf));
                }
                return new S2CRtsPluginStatePayload(pluginIds, families, radiusBlocks, fieldDeployment, personal,
                        ownerNames, stacks, buf.readUtf(128));
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
