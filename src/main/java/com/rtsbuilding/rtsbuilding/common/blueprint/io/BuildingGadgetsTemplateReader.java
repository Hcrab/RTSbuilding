package com.rtsbuilding.rtsbuilding.common.blueprint.io;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rtsbuilding.rtsbuilding.common.blueprint.model.BlueprintFormat;
import com.rtsbuilding.rtsbuilding.common.blueprint.model.BlueprintParseException;
import com.rtsbuilding.rtsbuilding.common.blueprint.model.RtsBlueprint;
import com.rtsbuilding.rtsbuilding.common.blueprint.model.RtsBlueprintBlock;
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
 * Building Gadgets JSON 模板蓝图读取器。
 * <p>
 * 解析 Building Gadgets 模组导出的 JSON 模板文件。
 * 此读取器仅负责导入时的格式兼容性，不处理 Building Gadgets 的世界存档、
 * 物品 NBT、撤销数据、材料缓存或放置规则。
 */
final class BuildingGadgetsTemplateReader {

    private static final int B1_BYTE_MASK = 0xFF;
    private static final int B2_BYTE_MASK = 0xFF_FF;
    private static final int B3_BYTE_MASK = 0xFF_FF_FF;

    private BuildingGadgetsTemplateReader() {
    }

    /**
     * 从字节数组解析 Building Gadgets JSON 模板。
     */
    static RtsBlueprint parse(byte[] data, String fileName, RegistryAccess registryAccess)
            throws BlueprintParseException {
        JsonObject root = readJsonObject(data, fileName);
        HolderGetter<Block> blockLookup = registryAccess.lookupOrThrow(Registries.BLOCK);
        String name = readName(root, fileName);

        // 新版格式：statePosArrayList
        String statePosArrayList = readString(root, "statePosArrayList");
        if (!statePosArrayList.isBlank()) {
            return parseStatePosArrayList(statePosArrayList, name, fileName, blockLookup);
        }

        // 旧版格式：body
        String body = readString(root, "body");
        if (!body.isBlank()) {
            return parseLegacyBody(root, body, name, fileName, blockLookup);
        }

        throw new BlueprintParseException("Building Gadgets JSON 缺少模板数据: " + fileName);
    }

    /** 读取并解析 JSON 对象 */
    private static JsonObject readJsonObject(byte[] data, String fileName) throws BlueprintParseException {
        try {
            JsonElement parsed = JsonParser.parseString(new String(data, StandardCharsets.UTF_8));
            if (!parsed.isJsonObject()) {
                throw new BlueprintParseException("Building Gadgets 模板不是 JSON 对象: " + fileName);
            }
            return parsed.getAsJsonObject();
        } catch (BlueprintParseException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BlueprintParseException("读取 Building Gadgets JSON 失败: " + fileName, ex);
        }
    }

    /**
     * 解析新版格式（statePosArrayList）。
     * <p>
     * 新版格式使用 SNBT（Stringified NBT）存储方块状态映射和位置数据。
     */
    private static RtsBlueprint parseStatePosArrayList(String snbt, String name, String fileName,
            HolderGetter<Block> blockLookup) throws BlueprintParseException {
        CompoundTag tag;
        try {
            tag = TagParser.parseTag(snbt);
        } catch (Exception ex) {
            throw new BlueprintParseException("读取 Building Gadgets 方块列表失败: " + fileName, ex);
        }
        return parseMappedStateList(tag, name, fileName, blockLookup);
    }

    /**
     * 解析 mapped state list 格式的方块数据。
     * <p>
     * 包含 blockstatemap（调色板）和 statelist（int 数组索引）两部分。
     */
    private static RtsBlueprint parseMappedStateList(CompoundTag tag, String name, String fileName,
            HolderGetter<Block> blockLookup) throws BlueprintParseException {
        if (!tag.contains("blockstatemap", Tag.TAG_LIST) || !tag.contains("statelist", Tag.TAG_INT_ARRAY)) {
            throw new BlueprintParseException("Building Gadgets 模板缺少方块状态映射: " + fileName);
        }

        Bounds bounds = Bounds.from(readBlockPos(tag.getCompound("startpos")), readBlockPos(tag.getCompound("endpos")));
        List<PaletteEntry> palette = readPalette(tag.getList("blockstatemap", Tag.TAG_COMPOUND), blockLookup);
        int[] stateList = tag.getIntArray("statelist");
        List<RtsBlueprintBlock> blocks = new ArrayList<>();
        int index = 0;
        // 遍历包围盒内的所有位置，映射到调色板索引
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

    /**
     * 解析旧版格式（body 字段）。
     * <p>
     * 旧版格式使用 Base64 编码的压缩 NBT 数据。
     * 可能包含 blockstatemap + statelist（映射格式）或 pos + data（旧版格式）。
     */
    private static RtsBlueprint parseLegacyBody(JsonObject root, String body, String name, String fileName,
            HolderGetter<Block> blockLookup) throws BlueprintParseException {
        CompoundTag nbt = readCompressedBody(body, fileName);
        if (nbt.contains("blockstatemap", Tag.TAG_LIST) && nbt.contains("statelist", Tag.TAG_INT_ARRAY)) {
            return parseMappedStateList(nbt, name, fileName, blockLookup);
        }
        if (!nbt.contains("pos", Tag.TAG_LIST) || !nbt.contains("data", Tag.TAG_LIST)) {
            throw new BlueprintParseException("Building Gadgets 旧版模板缺少方块数据: " + fileName);
        }

        // 解析旧版：编码位置和方块数据
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

    /** 读取并解压旧版 body 数据 */
    private static CompoundTag readCompressedBody(String body, String fileName) throws BlueprintParseException {
        try {
            byte[] decoded = Base64.getDecoder().decode(body);
            return NbtIo.readCompressed(new ByteArrayInputStream(decoded), NbtAccounter.unlimitedHeap());
        } catch (Exception ex) {
            throw new BlueprintParseException("读取 Building Gadgets 模板 body 失败: " + fileName, ex);
        }
    }

    /** 读取调色板列表 */
    private static List<PaletteEntry> readPalette(ListTag paletteTag, HolderGetter<Block> blockLookup) {
        List<PaletteEntry> out = new ArrayList<>(paletteTag.size());
        for (int i = 0; i < paletteTag.size(); i++) {
            out.add(readBlockStateEntry(paletteTag.getCompound(i), blockLookup));
        }
        return out;
    }

    /** 读取旧版方块数据 */
    private static PaletteEntry readLegacyBlockData(CompoundTag blockData, HolderGetter<Block> blockLookup) {
        CompoundTag stateTag = blockData.contains("state", Tag.TAG_COMPOUND)
                ? blockData.getCompound("state")
                : blockData;
        return readBlockStateEntry(stateTag, blockLookup);
    }

    /** 读取方块状态条目 */
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

    /** 检测方块是否缺失 */
    private static String missingBlockId(CompoundTag stateTag) {
        String name = stateTag.getString("Name");
        ResourceLocation id = ResourceLocation.tryParse(name);
        if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) {
            return name == null ? "" : name;
        }
        return "";
    }

    /** 将方块添加到结果列表（转换绝对坐标到相对坐标） */
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

    /** 读取旧版包围盒（优先从 JSON，其次 NBT header，最后从方块坐标计算） */
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

    /** 从 JSON 中读取包围盒 */
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

    /** 从 NBT 中读取包围盒 */
    private static Bounds readBoundsFromNbt(CompoundTag bounds) {
        if (bounds == null || bounds.isEmpty()) {
            return null;
        }
        return Bounds.from(
                new BlockPos(bounds.getInt("minX"), bounds.getInt("minY"), bounds.getInt("minZ")),
                new BlockPos(bounds.getInt("maxX"), bounds.getInt("maxY"), bounds.getInt("maxZ")));
    }

    /** 从 NBT 标签中读取 BlockPos */
    private static BlockPos readBlockPos(CompoundTag tag) {
        return new BlockPos(tag.getInt("X"), tag.getInt("Y"), tag.getInt("Z"));
    }

    /** 解码旧版编码坐标 */
    private static BlockPos legacyPos(long encoded) {
        int x = (int) ((encoded >> 24) & B2_BYTE_MASK);
        int y = (int) ((encoded >> 16) & B1_BYTE_MASK);
        int z = (int) (encoded & B2_BYTE_MASK);
        return new BlockPos(x, y, z);
    }

    /** 从旧版编码中提取方块状态 ID */
    private static int legacyStateId(long encoded) {
        return (int) ((encoded >> 40) & B3_BYTE_MASK);
    }

    /** 读取蓝图名称 */
    private static String readName(JsonObject root, String fileName) {
        String rootName = readString(root, "name");
        if (!rootName.isBlank()) {
            return rootName;
        }
        JsonObject header = readObject(root, "header");
        String headerName = header == null ? "" : readString(header, "name");
        return headerName.isBlank() ? cleanName(fileName) : headerName;
    }

    /** 安全读取 JSON 对象 */
    private static JsonObject readObject(JsonObject root, String key) {
        JsonElement element = root == null ? null : root.get(key);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    /** 安全读取 JSON 字符串 */
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

    /** 安全读取 JSON 整数（支持蛇形和驼峰命名） */
    private static int readInt(JsonObject root, String snakeKey, String camelKey) {
        JsonElement element = root.has(snakeKey) ? root.get(snakeKey) : root.get(camelKey);
        return element == null ? 0 : element.getAsInt();
    }

    /** 从文件名中提取干净的名称 */
    private static String cleanName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "Building Gadgets Template";
        }
        int slash = Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\'));
        String base = slash >= 0 ? fileName.substring(slash + 1) : fileName;
        int dot = base.lastIndexOf('.');
        return dot > 0 ? base.substring(0, dot) : base;
    }

    /** 内部调色板条目记录 */
    private record PaletteEntry(BlockState state, String missingBlockId) {
    }

    /** 内部包围盒记录 */
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
            int minX = 0, minY = 0, minZ = 0;
            int maxX = 0, maxY = 0, maxZ = 0;
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
