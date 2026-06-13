package com.rtsbuilding.rtsbuilding.blueprint.format;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.rtsbuilding.rtsbuilding.blueprint.BlueprintFormat;
import com.rtsbuilding.rtsbuilding.blueprint.BlueprintParseException;
import com.rtsbuilding.rtsbuilding.blueprint.RtsBlueprint;
import com.rtsbuilding.rtsbuilding.blueprint.RtsBlueprintBlock;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class SpongeSchemReader {
    private SpongeSchemReader() {
    }

    static RtsBlueprint parse(byte[] data, String fileName, RegistryAccess registryAccess) throws BlueprintParseException {
        CompoundTag root = readCompressed(data, fileName);
        CompoundTag schematic = root.contains("Schematic", Tag.TAG_COMPOUND) ? root.getCompound("Schematic") : root;
        if (!schematic.contains("Blocks", Tag.TAG_COMPOUND)) {
            throw new BlueprintParseException("Schematic file is missing Blocks data: " + fileName);
        }

        int width = readPositiveDimension(schematic, "Width");
        int height = readPositiveDimension(schematic, "Height");
        int length = readPositiveDimension(schematic, "Length");
        if (width <= 0 || height <= 0 || length <= 0) {
            throw new BlueprintParseException("Schematic has invalid dimensions: " + fileName);
        }

        CompoundTag blocksRoot = schematic.getCompound("Blocks");
        CompoundTag paletteTag = blocksRoot.getCompound("Palette");
        byte[] packed = readBlockData(blocksRoot);
        HolderLookup<Block> blockLookup = registryAccess.lookupOrThrow(Registries.BLOCK);
        Map<Integer, PaletteEntry> palette = readPalette(paletteTag, blockLookup);

        List<Integer> stateIds = decodeVarInts(packed, width * height * length);
        List<RtsBlueprintBlock> out = new ArrayList<>();
        int expected = width * height * length;
        for (int index = 0; index < expected && index < stateIds.size(); index++) {
            PaletteEntry paletteEntry = palette.get(stateIds.get(index));
            if (paletteEntry == null) {
                continue;
            }
            BlockState state = paletteEntry.state();
            int x = index % width;
            int z = (index / width) % length;
            int y = index / (width * length);
            if (!paletteEntry.missingBlockId().isBlank()) {
                out.add(RtsBlueprintBlock.missing(new BlockPos(x, y, z), paletteEntry.missingBlockId(), new CompoundTag()));
                continue;
            }
            if (state == null || state.isAir() || state.is(Blocks.STRUCTURE_VOID)) {
                continue;
            }
            out.add(new RtsBlueprintBlock(new BlockPos(x, y, z), state, new CompoundTag()));
        }

        return RtsBlueprint.create(cleanName(fileName), fileName, BlueprintFormat.SPONGE_SCHEM, new Vec3i(width, height, length), out);
    }

    private static CompoundTag readCompressed(byte[] data, String fileName) throws BlueprintParseException {
        try {
            return NbtIo.readCompressed(new ByteArrayInputStream(data));
        } catch (Exception ex) {
            throw new BlueprintParseException("Failed to read compressed schematic: " + fileName, ex);
        }
    }

    private static int readPositiveDimension(CompoundTag tag, String key) {
        return tag.contains(key, Tag.TAG_SHORT) ? Short.toUnsignedInt(tag.getShort(key)) : tag.getInt(key);
    }

    private static byte[] readBlockData(CompoundTag blocksRoot) throws BlueprintParseException {
        if (!blocksRoot.contains("Data", Tag.TAG_BYTE_ARRAY)) {
            throw new BlueprintParseException("Schematic is missing Blocks.Data");
        }
        Tag raw = blocksRoot.get("Data");
        if (raw instanceof ByteArrayTag byteArrayTag) {
            return byteArrayTag.getAsByteArray();
        }
        return blocksRoot.getByteArray("Data");
    }

    private static Map<Integer, PaletteEntry> readPalette(CompoundTag paletteTag, HolderLookup<Block> blockLookup)
            throws BlueprintParseException {
        Map<Integer, PaletteEntry> out = new HashMap<>();
        for (String key : paletteTag.getAllKeys()) {
            String missingId = missingBlockId(key);
            if (!missingId.isBlank()) {
                out.put(paletteTag.getInt(key), new PaletteEntry(Blocks.AIR.defaultBlockState(), missingId));
                continue;
            }
            try {
                BlockState state = BlockStateParser.parseForBlock(blockLookup, key, false).blockState();
                out.put(paletteTag.getInt(key), new PaletteEntry(state, ""));
            } catch (CommandSyntaxException ex) {
                throw new BlueprintParseException("Unknown block state in schematic palette: " + key, ex);
            }
        }
        return out;
    }

    private static String missingBlockId(String stateKey) {
        if (stateKey == null || stateKey.isBlank()) {
            return "";
        }
        int propertyStart = stateKey.indexOf('[');
        String blockId = propertyStart >= 0 ? stateKey.substring(0, propertyStart) : stateKey;
        ResourceLocation id = ResourceLocation.tryParse(blockId);
        if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) {
            return blockId;
        }
        return "";
    }

    private static List<Integer> decodeVarInts(byte[] data, int maxEntries) throws BlueprintParseException {
        List<Integer> out = new ArrayList<>(Math.min(Math.max(0, maxEntries), 8192));
        int value = 0;
        int shift = 0;
        for (byte b : data) {
            value |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                out.add(value);
                if (out.size() >= maxEntries) {
                    break;
                }
                value = 0;
                shift = 0;
            } else {
                shift += 7;
                if (shift > 35) {
                    throw new BlueprintParseException("Malformed schematic block data varint");
                }
            }
        }
        return out;
    }

    private static String cleanName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "Blueprint";
        }
        int slash = Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\'));
        String base = slash >= 0 ? fileName.substring(slash + 1) : fileName;
        int dot = base.lastIndexOf('.');
        return dot > 0 ? base.substring(0, dot) : base;
    }

    private record PaletteEntry(BlockState state, String missingBlockId) {
    }
}
