package com.rtsbuilding.rtsbuilding.network.plugin;

import com.rtsbuilding.rtsbuilding.network.RtsPacketBuffer;
import io.netty.buffer.ByteBuf;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class S2CRtsPluginStatePayload implements IMessage {
    private static final int MAX_PLUGIN_COUNT = 64;
    private static final int MAX_PLUGIN_ID_CHARS = 128;
    private static final int MAX_FAMILY_CHARS = 64;
    private static final int MAX_OWNER_NAME_CHARS = 64;
    private static final int MAX_TEAM_NAME_CHARS = 128;

    private List<String> pluginIds = Collections.emptyList();
    private List<String> families = Collections.emptyList();
    private List<Integer> radiusBlocks = Collections.emptyList();
    private List<Boolean> fieldDeployment = Collections.emptyList();
    private List<Boolean> personal = Collections.emptyList();
    private List<String> ownerNames = Collections.emptyList();
    private List<ItemStack> stacks = Collections.emptyList();
    private String teamName = "";

    public S2CRtsPluginStatePayload() {
    }

    public S2CRtsPluginStatePayload(List<String> pluginIds, List<String> families,
                                    List<Integer> radiusBlocks, List<Boolean> fieldDeployment,
                                    List<Boolean> personal, List<String> ownerNames,
                                    List<ItemStack> stacks, String teamName) {
        this.pluginIds = immutable(pluginIds);
        this.families = immutable(families);
        this.radiusBlocks = immutable(radiusBlocks);
        this.fieldDeployment = immutable(fieldDeployment);
        this.personal = immutable(personal);
        this.ownerNames = immutable(ownerNames);
        this.stacks = immutableStacks(stacks);
        this.teamName = teamName == null ? "" : teamName;
    }

    public List<String> pluginIds() { return pluginIds; }
    public List<String> families() { return families; }
    public List<Integer> radiusBlocks() { return radiusBlocks; }
    public List<Boolean> fieldDeployment() { return fieldDeployment; }
    public List<Boolean> personal() { return personal; }
    public List<String> ownerNames() { return ownerNames; }
    public List<ItemStack> stacks() { return stacks; }
    public String teamName() { return teamName; }

    @Override
    public void fromBytes(ByteBuf buffer) {
        int size = RtsPacketBuffer.readBoundedCount(buffer, MAX_PLUGIN_COUNT, "plugin count");
        List<String> decodedIds = new ArrayList<>(size);
        List<String> decodedFamilies = new ArrayList<>(size);
        List<Integer> decodedRadius = new ArrayList<>(size);
        List<Boolean> decodedField = new ArrayList<>(size);
        List<Boolean> decodedPersonal = new ArrayList<>(size);
        List<String> decodedOwners = new ArrayList<>(size);
        List<ItemStack> decodedStacks = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            decodedIds.add(RtsPacketBuffer.readString(buffer, MAX_PLUGIN_ID_CHARS, "plugin id"));
            decodedFamilies.add(RtsPacketBuffer.readString(buffer, MAX_FAMILY_CHARS, "plugin family"));
            decodedRadius.add(Math.max(0, RtsPacketBuffer.readVarInt(buffer)));
            decodedField.add(buffer.readBoolean());
            decodedPersonal.add(buffer.readBoolean());
            decodedOwners.add(RtsPacketBuffer.readString(buffer, MAX_OWNER_NAME_CHARS, "plugin owner"));
            decodedStacks.add(normalizeStack(RtsPacketBuffer.readItemStack(buffer)));
        }
        pluginIds = immutable(decodedIds);
        families = immutable(decodedFamilies);
        radiusBlocks = immutable(decodedRadius);
        fieldDeployment = immutable(decodedField);
        personal = immutable(decodedPersonal);
        ownerNames = immutable(decodedOwners);
        stacks = immutableStacks(decodedStacks);
        teamName = RtsPacketBuffer.readString(buffer, MAX_TEAM_NAME_CHARS, "plugin team");
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        int size = Math.min(MAX_PLUGIN_COUNT, Math.min(pluginIds.size(),
                Math.min(families.size(), Math.min(radiusBlocks.size(),
                        Math.min(fieldDeployment.size(), Math.min(personal.size(),
                                Math.min(ownerNames.size(), stacks.size())))))));
        RtsPacketBuffer.writeVarInt(buffer, size);
        for (int i = 0; i < size; i++) {
            RtsPacketBuffer.writeString(buffer,
                    RtsPluginPayloadText.fit(pluginIds.get(i), MAX_PLUGIN_ID_CHARS),
                    MAX_PLUGIN_ID_CHARS, "plugin id");
            RtsPacketBuffer.writeString(buffer,
                    RtsPluginPayloadText.fit(families.get(i), MAX_FAMILY_CHARS),
                    MAX_FAMILY_CHARS, "plugin family");
            RtsPacketBuffer.writeVarInt(buffer, Math.max(0, safeInteger(radiusBlocks.get(i))));
            buffer.writeBoolean(Boolean.TRUE.equals(fieldDeployment.get(i)));
            buffer.writeBoolean(Boolean.TRUE.equals(personal.get(i)));
            RtsPacketBuffer.writeString(buffer,
                    RtsPluginPayloadText.fit(ownerNames.get(i), MAX_OWNER_NAME_CHARS),
                    MAX_OWNER_NAME_CHARS, "plugin owner");
            RtsPacketBuffer.writeItemStack(buffer, normalizeStack(stacks.get(i)));
        }
        RtsPacketBuffer.writeString(buffer,
                RtsPluginPayloadText.fit(teamName, MAX_TEAM_NAME_CHARS),
                MAX_TEAM_NAME_CHARS, "plugin team");
    }

    private static int safeInteger(Integer value) { return value == null ? 0 : value.intValue(); }

    private static ItemStack normalizeStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return ItemStack.EMPTY;
        ItemStack copy = stack.copy();
        copy.setCount(1);
        return copy;
    }

    private static <T> List<T> immutable(List<T> values) {
        return values == null ? Collections.<T>emptyList()
                : Collections.unmodifiableList(new ArrayList<>(values));
    }

    private static List<ItemStack> immutableStacks(List<ItemStack> values) {
        if (values == null || values.isEmpty()) return Collections.emptyList();
        List<ItemStack> copies = new ArrayList<>(values.size());
        for (ItemStack stack : values) copies.add(normalizeStack(stack));
        return Collections.unmodifiableList(copies);
    }
}
