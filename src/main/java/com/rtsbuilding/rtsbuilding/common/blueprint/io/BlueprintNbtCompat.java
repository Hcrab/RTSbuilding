package com.rtsbuilding.rtsbuilding.common.blueprint.io;

import net.minecraft.block.Block;
import com.rtsbuilding.rtsbuilding.platform.block.IProperty;
import com.rtsbuilding.rtsbuilding.platform.block.BlockState;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.util.Constants;

import java.util.Map;
import com.google.common.base.Optional;

/** 1.12 方块状态没有 Codec；这里用原版结构 NBT 的 Name/Properties 显式往返。 */
final class BlueprintNbtCompat {
    private BlueprintNbtCompat() {}

    static StateResult readState(NBTTagCompound tag) {
        String name = tag.hasKey("Name", Constants.NBT.TAG_STRING) ? tag.getString("Name") : tag.getString("name");
        ResourceLocation id = parseId(name);
        Block block = id == null ? null : com.rtsbuilding.rtsbuilding.platform.registry.RtsRegistries.BLOCKS.getObject(id);
        if (block == null || block == Blocks.air && !"minecraft:air".equals(name)) {
            return new StateResult(BlockState.defaultState(Blocks.air), name == null ? "" : name);
        }
        BlockState state = BlockState.defaultState(block);
        String propertiesKey = tag.hasKey("Properties", Constants.NBT.TAG_COMPOUND) ? "Properties" : "properties";
        if (tag.hasKey(propertiesKey, Constants.NBT.TAG_COMPOUND)) {
            NBTTagCompound properties = tag.getCompoundTag(propertiesKey);
            for (IProperty<?> property : state.getPropertyKeys()) {
                if (properties.hasKey(property.getName(), Constants.NBT.TAG_STRING)) {
                    state = applyProperty(state, property, properties.getString(property.getName()));
                }
            }
        }
        return new StateResult(state, "");
    }

    static StateResult readStateString(String text) {
        String value = text == null ? "" : text.trim();
        int start = value.indexOf('[');
        String name = start < 0 ? value : value.substring(0, start);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("Name", name);
        if (start >= 0 && value.endsWith("]")) {
            NBTTagCompound properties = new NBTTagCompound();
            String body = value.substring(start + 1, value.length() - 1);
            if (!body.isEmpty()) {
                for (String pair : body.split(",")) {
                    int equals = pair.indexOf('=');
                    if (equals > 0) properties.setString(pair.substring(0, equals).trim(), pair.substring(equals + 1).trim());
                }
            }
            tag.setTag("Properties", properties);
        }
        return readState(tag);
    }

    static NBTTagCompound writeState(BlockState state) {
        NBTTagCompound out = new NBTTagCompound();
        ResourceLocation id = com.rtsbuilding.rtsbuilding.platform.registry.RtsRegistries.BLOCKS.getNameForObject(state.getBlock());
        out.setString("Name", id == null ? "minecraft:air" : id.toString());
        NBTTagCompound properties = new NBTTagCompound();
        for (Map.Entry<IProperty<?>, Comparable<?>> entry : state.getProperties().entrySet()) {
            properties.setString(entry.getKey().getName(), propertyValueName(entry.getKey(), entry.getValue()));
        }
        if (!com.rtsbuilding.rtsbuilding.platform.nbt.NbtCompat.isEmpty(properties)) out.setTag("Properties", properties);
        return out;
    }

    private static ResourceLocation parseId(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        try { return new ResourceLocation(value); } catch (RuntimeException ignored) { return null; }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static BlockState applyProperty(BlockState state, IProperty property, String value) {
        Optional parsed = property.parseValue(value);
        return parsed.isPresent() ? state.withProperty(property, (Comparable) parsed.get()) : state;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static String propertyValueName(IProperty property, Comparable value) {
        return property.getName(value);
    }

    static final class StateResult {
        private final BlockState state;
        private final String missingBlockId;
        StateResult(BlockState state, String missingBlockId) {
            this.state = state;
            this.missingBlockId = missingBlockId == null ? "" : missingBlockId;
        }
        BlockState state() { return state; }
        String missingBlockId() { return missingBlockId; }
        boolean isMissing() { return !missingBlockId.trim().isEmpty(); }
    }
}
