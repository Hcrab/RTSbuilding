package com.rtsbuilding.rtsbuilding.blueprint.format;

import com.rtsbuilding.rtsbuilding.blueprint.BlueprintFormat;
import com.rtsbuilding.rtsbuilding.blueprint.BlueprintParseException;
import com.rtsbuilding.rtsbuilding.blueprint.RtsBlueprint;
import com.rtsbuilding.rtsbuilding.blueprint.RtsBlueprintBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
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

final class LitematicReader {
    private LitematicReader() {
    }

    static RtsBlueprint parse(byte[] data, String fileName, RegistryAccess registryAccess) throws BlueprintParseException {
        CompoundTag root = readCompressed(data, fileName);
        if (!root.contains("Regions", Tag.TAG_COMPOUND)) {
            throw new BlueprintParseException("Litematic file is missing Regions data: " + fileName);
        }

        HolderGetter<Block> blockLookup = registryAccess.lookupOrThrow(Registries.BLOCK);
        CompoundTag regions = root.getCompound("Regions");
        List<PendingBlock> pending = new ArrayList<>();

        for (String regionName : regions.getAllKeys()) {
            if (!regions.contains(regionName, Tag.TAG_COMPOUND)) {
                continue;
            }
            readRegion(fileName, regions.getCompound(regionName), blockLookup, pending);
        }

        if (pending.isEmpty()) {
            return RtsBlueprint.create(readName(root, fileName), fileName, BlueprintFormat.LITEMATIC, Vec3i.ZERO, List.of());
        }

        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (PendingBlock block : pending) {
            BlockPos pos = block.pos();
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }

        List<RtsBlueprintBlock> out = new ArrayList<>(pending.size());
        BlockPos offset = new BlockPos(-minX, -minY, -minZ);
        for (PendingBlock block : pending) {
            BlockPos relative = block.pos().offset(offset);
            CompoundTag blockEntityTag = block.blockEntityTag() == null ? new CompoundTag() : block.blockEntityTag().copy();
            PaletteEntry paletteEntry = block.paletteEntry();
            if (!paletteEntry.missingBlockId().isBlank()) {
                out.add(RtsBlueprintBlock.missing(relative, paletteEntry.missingBlockId(), blockEntityTag));
                continue;
            }
            out.add(new RtsBlueprintBlock(relative, paletteEntry.state(), blockEntityTag));
        }

        Vec3i size = new Vec3i(maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1);
        return RtsBlueprint.create(readName(root, fileName), fileName, BlueprintFormat.LITEMATIC, size, out);
    }

    private static void readRegion(String fileName, CompoundTag region, HolderGetter<Block> blockLookup,
            List<PendingBlock> out) throws BlueprintParseException {
        Vec3i position = readVec(region, "Position", Vec3i.ZERO);
        Vec3i size = readVec(region, "Size", Vec3i.ZERO);
        int width = size.getX();
        int height = size.getY();
        int length = size.getZ();
        if (width == 0 || height == 0 || length == 0) {
            return;
        }

        int absWidth = Math.abs(width);
        int absHeight = Math.abs(height);
        int absLength = Math.abs(length);
        long volume = (long) absWidth * absHeight * absLength;
        if (volume > Integer.MAX_VALUE) {
            throw new BlueprintParseException("Litematic region is too large to read: " + fileName);
        }

        List<PaletteEntry> palette = readPalette(region.getList("BlockStatePalette", Tag.TAG_COMPOUND), blockLookup);
        if (palette.isEmpty()) {
            throw new BlueprintParseException("Litematic region is missing BlockStatePalette: " + fileName);
        }

        Map<BlockPos, CompoundTag> blockEntities = readBlockEntities(region);
        long[] blockStates = region.contains("BlockStates", Tag.TAG_LONG_ARRAY)
                ? region.getLongArray("BlockStates")
                : new long[0];
        int bits = bitsPerEntry(palette.size());
        if (palette.size() > 1 && blockStates.length == 0) {
            throw new BlueprintParseException("Litematic region is missing BlockStates: " + fileName);
        }

        int expected = (int) volume;
        for (int index = 0; index < expected; index++) {
            int paletteIndex = palette.size() == 1 ? 0 : readPacked(blockStates, index, bits);
            if (paletteIndex < 0 || paletteIndex >= palette.size()) {
                continue;
            }
            PaletteEntry paletteEntry = palette.get(paletteIndex);
            if (paletteEntry.missingBlockId().isBlank()
                    && (paletteEntry.state().isAir() || paletteEntry.state().is(Blocks.STRUCTURE_VOID))) {
                continue;
            }
            int storeX = index % absWidth;
            int storeZ = (index / absWidth) % absLength;
            int storeY = index / (absWidth * absLength);
            BlockPos local = new BlockPos(
                    toRegionCoordinate(storeX, width),
                    toRegionCoordinate(storeY, height),
                    toRegionCoordinate(storeZ, length));
            BlockPos absolute = new BlockPos(
                    position.getX() + local.getX(),
                    position.getY() + local.getY(),
                    position.getZ() + local.getZ());
            out.add(new PendingBlock(absolute, paletteEntry, blockEntities.getOrDefault(local, new CompoundTag())));
        }
    }

    private static List<PaletteEntry> readPalette(ListTag paletteTag, HolderGetter<Block> blockLookup)
            throws BlueprintParseException {
        List<PaletteEntry> out = new ArrayList<>(paletteTag.size());
        for (int i = 0; i < paletteTag.size(); i++) {
            CompoundTag paletteEntry = paletteTag.getCompound(i);
            String missingId = missingBlockId(paletteEntry);
            if (!missingId.isBlank()) {
                out.add(new PaletteEntry(Blocks.AIR.defaultBlockState(), missingId));
                continue;
            }
            try {
                out.add(new PaletteEntry(NbtUtils.readBlockState(blockLookup, paletteEntry), ""));
            } catch (Exception ex) {
                throw new BlueprintParseException("Unknown block state in litematic palette: " + paletteEntry, ex);
            }
        }
        return out;
    }

    private static Map<BlockPos, CompoundTag> readBlockEntities(CompoundTag region) {
        Map<BlockPos, CompoundTag> out = new HashMap<>();
        if (!region.contains("TileEntities", Tag.TAG_LIST)) {
            return out;
        }
        ListTag list = region.getList("TileEntities", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag tag = list.getCompound(i);
            BlockPos pos = readBlockEntityPos(tag);
            if (pos != null) {
                out.put(pos, tag.copy());
            }
        }
        return out;
    }

    private static BlockPos readBlockEntityPos(CompoundTag tag) {
        if (tag.contains("x") && tag.contains("y") && tag.contains("z")) {
            return new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z"));
        }
        if (tag.contains("Pos", Tag.TAG_COMPOUND)) {
            Vec3i pos = readVec(tag, "Pos", null);
            return pos == null ? null : new BlockPos(pos);
        }
        if (tag.contains("Pos", Tag.TAG_INT_ARRAY) || tag.contains("Pos", Tag.TAG_LIST)) {
            Vec3i pos = readVec(tag, "Pos", null);
            return pos == null ? null : new BlockPos(pos);
        }
        return null;
    }

    private static int readPacked(long[] data, int index, int bits) {
        long bitIndex = (long) index * bits;
        int startLong = (int) (bitIndex >> 6);
        int endLong = (int) ((((long) index + 1) * bits - 1) >> 6);
        int bitOffset = (int) (bitIndex & 63L);
        long mask = (1L << bits) - 1L;
        if (startLong < 0 || startLong >= data.length) {
            return 0;
        }
        long value = data[startLong] >>> bitOffset;
        if (startLong != endLong && endLong >= 0 && endLong < data.length) {
            value |= data[endLong] << (64 - bitOffset);
        }
        return (int) (value & mask);
    }

    private static int bitsPerEntry(int paletteSize) {
        return Math.max(2, 32 - Integer.numberOfLeadingZeros(Math.max(1, paletteSize - 1)));
    }

    private static int toRegionCoordinate(int storeCoordinate, int size) {
        return size < 0 ? storeCoordinate + size + 1 : storeCoordinate;
    }

    private static String missingBlockId(CompoundTag paletteEntry) {
        if (!paletteEntry.contains("Name", Tag.TAG_STRING)) {
            return "";
        }
        String name = paletteEntry.getString("Name");
        ResourceLocation id = ResourceLocation.tryParse(name);
        if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) {
            return name == null ? "" : name;
        }
        return "";
    }

    private static CompoundTag readCompressed(byte[] data, String fileName) throws BlueprintParseException {
        try {
            return NbtIo.readCompressed(new ByteArrayInputStream(data));
        } catch (Exception ex) {
            throw new BlueprintParseException("Failed to read compressed litematic blueprint: " + fileName, ex);
        }
    }

    private static Vec3i readVec(CompoundTag root, String key, Vec3i fallback) {
        if (!root.contains(key)) {
            return fallback;
        }
        if (root.contains(key, Tag.TAG_COMPOUND)) {
            CompoundTag tag = root.getCompound(key);
            return new Vec3i(tag.getInt("x"), tag.getInt("y"), tag.getInt("z"));
        }
        if (root.contains(key, Tag.TAG_INT_ARRAY)) {
            int[] values = root.getIntArray(key);
            if (values.length >= 3) {
                return new Vec3i(values[0], values[1], values[2]);
            }
        }
        Tag raw = root.get(key);
        if (raw instanceof IntArrayTag arrayTag) {
            int[] values = arrayTag.getAsIntArray();
            if (values.length >= 3) {
                return new Vec3i(values[0], values[1], values[2]);
            }
        }
        if (root.contains(key, Tag.TAG_LIST)) {
            ListTag values = root.getList(key, Tag.TAG_INT);
            if (values.size() >= 3) {
                return new Vec3i(values.getInt(0), values.getInt(1), values.getInt(2));
            }
        }
        return fallback;
    }

    private static String readName(CompoundTag root, String fileName) {
        if (root.contains("Metadata", Tag.TAG_COMPOUND)) {
            CompoundTag metadata = root.getCompound("Metadata");
            if (metadata.contains("Name", Tag.TAG_STRING) && !metadata.getString("Name").isBlank()) {
                return metadata.getString("Name");
            }
        }
        return cleanName(fileName);
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

    private record PendingBlock(BlockPos pos, PaletteEntry paletteEntry, CompoundTag blockEntityTag) {
    }

    private record PaletteEntry(BlockState state, String missingBlockId) {
    }
}
