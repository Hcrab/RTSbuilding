package com.rtsbuilding.rtsbuilding.blueprint.format;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Reads file-exported Building Gadgets templates into RTSBuilding blueprints.
 *
 * <p>This class owns only import-time format compatibility. It does not attempt
 * to read Building Gadgets world saves, item NBT, undo data, material caches, or
 * placement rules. Keeping that boundary here lets the blueprint panel offer a
 * broad "sync other mods" button without mixing UI state with third-party file
 * schemas.</p>
 */
final class BuildingGadgetsTemplateReader {
    private static final int B1_BYTE_MASK = 0xFF;
    private static final int B2_BYTE_MASK = 0xFF_FF;
    private static final int B3_BYTE_MASK = 0xFF_FF_FF;

    private BuildingGadgetsTemplateReader() {
    }

    static RtsBlueprint parse(byte[] data, String fileName, RegistryAccess registryAccess)
            throws BlueprintParseException {
        JsonObject root = readJsonObject(data, fileName);
        HolderGetter<Block> blockLookup = registryAccess.lookupOrThrow(Registries.BLOCK);
        String name = readName(root, fileName);

        String statePosArrayList = readString(root, "statePosArrayList");
        if (!statePosArrayList.isBlank()) {
            return parseStatePosArrayList(statePosArrayList, name, fileName, blockLookup);
        }

        String body = readString(root, "body");
        if (!body.isBlank()) {
            return parseLegacyBody(root, body, name, fileName, blockLookup);
        }

        throw new BlueprintParseException("Building Gadgets JSON is missing template data: " + fileName);
    }

    private static JsonObject readJsonObject(byte[] data, String fileName) throws BlueprintParseException {
        try {
            JsonElement parsed = JsonParser.parseString(new String(data, StandardCharsets.UTF_8));
            if (!parsed.isJsonObject()) {
                throw new BlueprintParseException("Building Gadgets template is not a JSON object: " + fileName);
            }
            return parsed.getAsJsonObject();
        } catch (BlueprintParseException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BlueprintParseException("Failed to read Building Gadgets JSON: " + fileName, ex);
        }
    }

    private static RtsBlueprint parseStatePosArrayList(String snbt, String name, String fileName,
            HolderGetter<Block> blockLookup) throws BlueprintParseException {
        CompoundTag tag;
        try {
            tag = TagParser.parseTag(snbt);
        } catch (Exception ex) {
            throw new BlueprintParseException("Failed to read Building Gadgets block list: " + fileName, ex);
        }
        return parseMappedStateList(tag, name, fileName, blockLookup);
    }

    private static RtsBlueprint parseMappedStateList(CompoundTag tag, String name, String fileName,
            HolderGetter<Block> blockLookup) throws BlueprintParseException {
        if (!tag.contains("blockstatemap", Tag.TAG_LIST) || !tag.contains("statelist", Tag.TAG_INT_ARRAY)) {
            throw new BlueprintParseException("Building Gadgets template is missing block state map: " + fileName);
        }

        Bounds bounds = Bounds.from(readBlockPos(tag.getCompound("startpos")), readBlockPos(tag.getCompound("endpos")));
        List<PaletteEntry> palette = readPalette(tag.getList("blockstatemap", Tag.TAG_COMPOUND), blockLookup);
        int[] stateList = tag.getIntArray("statelist");
        List<RtsBlueprintBlock> blocks = new ArrayList<>();
        int index = 0;
        for (BlockPos pos : BlockPos.betweenClosed(
                bounds.min().getX(), bounds.min().getY(), bounds.min().getZ(),
                bounds.max().getX(), bounds.max().getY(), bounds.max().getZ())) {
            if (index >= stateList.length) {
                break;
            }
            int stateIndex = stateList[index++];
            if (stateIndex < 0 || stateIndex >= palette.size()) {
                continue;
            }
            addBlock(blocks, pos, bounds.min(), palette.get(stateIndex));
        }
        return RtsBlueprint.create(name, fileName, BlueprintFormat.BUILDING_GADGETS_JSON, bounds.size(), blocks);
    }

    private static RtsBlueprint parseLegacyBody(JsonObject root, String body, String name, String fileName,
            HolderGetter<Block> blockLookup) throws BlueprintParseException {
        CompoundTag nbt = readCompressedBody(body, fileName);
        if (nbt.contains("blockstatemap", Tag.TAG_LIST) && nbt.contains("statelist", Tag.TAG_INT_ARRAY)) {
            return parseMappedStateList(nbt, name, fileName, blockLookup);
        }
        if (!nbt.contains("pos", Tag.TAG_LIST) || !nbt.contains("data", Tag.TAG_LIST)) {
            throw new BlueprintParseException("Building Gadgets legacy template is missing block data: " + fileName);
        }

        ListTag posList = nbt.getList("pos", Tag.TAG_LONG);
        ListTag dataList = nbt.getList("data", Tag.TAG_COMPOUND);
        Map<BlockPos, PaletteEntry> byPos = new HashMap<>();
        for (Tag rawPos : posList) {
            if (!(rawPos instanceof LongTag longTag)) {
                continue;
            }
            long encoded = longTag.getAsLong();
            int stateIndex = legacyStateId(encoded);
            if (stateIndex < 0 || stateIndex >= dataList.size()) {
                continue;
            }
            PaletteEntry entry = readLegacyBlockData(dataList.getCompound(stateIndex), blockLookup);
            byPos.put(legacyPos(encoded), entry);
        }
        if (byPos.isEmpty()) {
            return RtsBlueprint.create(name, fileName, BlueprintFormat.BUILDING_GADGETS_JSON, Vec3i.ZERO, List.of());
        }

        Bounds bounds = readLegacyBounds(root, nbt, byPos);
        List<RtsBlueprintBlock> blocks = new ArrayList<>();
        for (Map.Entry<BlockPos, PaletteEntry> entry : byPos.entrySet()) {
            addBlock(blocks, entry.getKey(), bounds.min(), entry.getValue());
        }
        return RtsBlueprint.create(name, fileName, BlueprintFormat.BUILDING_GADGETS_JSON, bounds.size(), blocks);
    }

    private static CompoundTag readCompressedBody(String body, String fileName) throws BlueprintParseException {
        try {
            byte[] decoded = Base64.getDecoder().decode(body);
            return NbtIo.readCompressed(new ByteArrayInputStream(decoded));
        } catch (Exception ex) {
            throw new BlueprintParseException("Failed to read Building Gadgets template body: " + fileName, ex);
        }
    }

    private static List<PaletteEntry> readPalette(ListTag paletteTag, HolderGetter<Block> blockLookup) {
        List<PaletteEntry> out = new ArrayList<>(paletteTag.size());
        for (int i = 0; i < paletteTag.size(); i++) {
            out.add(readBlockStateEntry(paletteTag.getCompound(i), blockLookup));
        }
        return out;
    }

    private static PaletteEntry readLegacyBlockData(CompoundTag blockData, HolderGetter<Block> blockLookup) {
        CompoundTag stateTag = blockData.contains("state", Tag.TAG_COMPOUND)
                ? blockData.getCompound("state")
                : blockData;
        return readBlockStateEntry(stateTag, blockLookup);
    }

    private static PaletteEntry readBlockStateEntry(CompoundTag stateTag, HolderGetter<Block> blockLookup) {
        if (!stateTag.contains("Name", Tag.TAG_STRING)) {
            return new PaletteEntry(Blocks.AIR.defaultBlockState(), "");
        }
        String missingId = missingBlockId(stateTag);
        if (!missingId.isBlank()) {
            return new PaletteEntry(Blocks.AIR.defaultBlockState(), missingId);
        }
        try {
            return new PaletteEntry(NbtUtils.readBlockState(blockLookup, stateTag), "");
        } catch (Exception ex) {
            return new PaletteEntry(Blocks.AIR.defaultBlockState(), stateTag.getString("Name"));
        }
    }

    private static String missingBlockId(CompoundTag stateTag) {
        String name = stateTag.getString("Name");
        ResourceLocation id = ResourceLocation.tryParse(name);
        if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) {
            return name == null ? "" : name;
        }
        return "";
    }

    private static void addBlock(List<RtsBlueprintBlock> blocks, BlockPos absolutePos, BlockPos min,
            PaletteEntry entry) {
        BlockPos relative = absolutePos.offset(-min.getX(), -min.getY(), -min.getZ());
        if (!entry.missingBlockId().isBlank()) {
            blocks.add(RtsBlueprintBlock.missing(relative, entry.missingBlockId(), new CompoundTag()));
            return;
        }
        BlockState state = entry.state();
        if (state == null || state.isAir() || state.is(Blocks.STRUCTURE_VOID)) {
            return;
        }
        blocks.add(new RtsBlueprintBlock(relative, state, new CompoundTag()));
    }

    private static Bounds readLegacyBounds(JsonObject root, CompoundTag nbt, Map<BlockPos, PaletteEntry> byPos) {
        Bounds fromJson = readBoundsFromJson(root);
        Bounds fromPositions = Bounds.fromPositions(byPos.keySet());
        if (fromJson != null && fromJson.containsAll(byPos.keySet())) {
            return fromJson;
        }

        CompoundTag header = nbt.getCompound("header");
        if (header.contains("bounds", Tag.TAG_COMPOUND)) {
            Bounds fromNbt = readBoundsFromNbt(header.getCompound("bounds"));
            if (fromNbt != null && fromNbt.containsAll(byPos.keySet())) {
                return fromNbt;
            }
        }
        return fromPositions;
    }

    private static Bounds readBoundsFromJson(JsonObject root) {
        JsonObject header = readObject(root, "header");
        JsonObject bounds = header == null ? null : readObject(header, "bounding_box");
        if (bounds == null && header != null) {
            bounds = readObject(header, "bounds");
        }
        if (bounds == null) {
            return null;
        }
        return Bounds.from(
                new BlockPos(readInt(bounds, "min_x", "minX"), readInt(bounds, "min_y", "minY"),
                        readInt(bounds, "min_z", "minZ")),
                new BlockPos(readInt(bounds, "max_x", "maxX"), readInt(bounds, "max_y", "maxY"),
                        readInt(bounds, "max_z", "maxZ")));
    }

    private static Bounds readBoundsFromNbt(CompoundTag bounds) {
        if (bounds == null || bounds.isEmpty()) {
            return null;
        }
        return Bounds.from(
                new BlockPos(bounds.getInt("minX"), bounds.getInt("minY"), bounds.getInt("minZ")),
                new BlockPos(bounds.getInt("maxX"), bounds.getInt("maxY"), bounds.getInt("maxZ")));
    }

    private static BlockPos readBlockPos(CompoundTag tag) {
        return new BlockPos(tag.getInt("X"), tag.getInt("Y"), tag.getInt("Z"));
    }

    private static BlockPos legacyPos(long encoded) {
        int x = (int) ((encoded >> 24) & B2_BYTE_MASK);
        int y = (int) ((encoded >> 16) & B1_BYTE_MASK);
        int z = (int) (encoded & B2_BYTE_MASK);
        return new BlockPos(x, y, z);
    }

    private static int legacyStateId(long encoded) {
        return (int) ((encoded >> 40) & B3_BYTE_MASK);
    }

    private static String readName(JsonObject root, String fileName) {
        String rootName = readString(root, "name");
        if (!rootName.isBlank()) {
            return rootName;
        }
        JsonObject header = readObject(root, "header");
        String headerName = header == null ? "" : readString(header, "name");
        return headerName.isBlank() ? cleanName(fileName) : headerName;
    }

    private static JsonObject readObject(JsonObject root, String key) {
        JsonElement element = root == null ? null : root.get(key);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    private static String readString(JsonObject root, String key) {
        JsonElement element = root == null ? null : root.get(key);
        if (element == null || !element.isJsonPrimitive()) {
            return "";
        }
        try {
            return element.getAsString();
        } catch (Exception ex) {
            return "";
        }
    }

    private static int readInt(JsonObject root, String snakeKey, String camelKey) {
        JsonElement element = root.has(snakeKey) ? root.get(snakeKey) : root.get(camelKey);
        return element == null ? 0 : element.getAsInt();
    }

    private static String cleanName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "Building Gadgets Template";
        }
        int slash = Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\'));
        String base = slash >= 0 ? fileName.substring(slash + 1) : fileName;
        int dot = base.lastIndexOf('.');
        return dot > 0 ? base.substring(0, dot) : base;
    }

    private record PaletteEntry(BlockState state, String missingBlockId) {
    }

    private record Bounds(BlockPos min, BlockPos max) {
        static Bounds from(BlockPos a, BlockPos b) {
            return new Bounds(
                    new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()),
                            Math.min(a.getZ(), b.getZ())),
                    new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()),
                            Math.max(a.getZ(), b.getZ())));
        }

        static Bounds fromPositions(Iterable<BlockPos> positions) {
            BlockPos first = null;
            int minX = 0;
            int minY = 0;
            int minZ = 0;
            int maxX = 0;
            int maxY = 0;
            int maxZ = 0;
            for (BlockPos pos : positions) {
                if (first == null) {
                    first = pos;
                    minX = maxX = pos.getX();
                    minY = maxY = pos.getY();
                    minZ = maxZ = pos.getZ();
                    continue;
                }
                minX = Math.min(minX, pos.getX());
                minY = Math.min(minY, pos.getY());
                minZ = Math.min(minZ, pos.getZ());
                maxX = Math.max(maxX, pos.getX());
                maxY = Math.max(maxY, pos.getY());
                maxZ = Math.max(maxZ, pos.getZ());
            }
            return first == null ? new Bounds(BlockPos.ZERO, BlockPos.ZERO)
                    : new Bounds(new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ));
        }

        Vec3i size() {
            return new Vec3i(
                    this.max.getX() - this.min.getX() + 1,
                    this.max.getY() - this.min.getY() + 1,
                    this.max.getZ() - this.min.getZ() + 1);
        }

        boolean containsAll(Iterable<BlockPos> positions) {
            for (BlockPos pos : positions) {
                if (pos.getX() < this.min.getX() || pos.getX() > this.max.getX()
                        || pos.getY() < this.min.getY() || pos.getY() > this.max.getY()
                        || pos.getZ() < this.min.getZ() || pos.getZ() > this.max.getZ()) {
                    return false;
                }
            }
            return true;
        }
    }
}
