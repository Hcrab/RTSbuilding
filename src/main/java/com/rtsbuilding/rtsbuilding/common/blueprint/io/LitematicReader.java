package com.rtsbuilding.rtsbuilding.common.blueprint.io;

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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Litematica 格式（.litematic）蓝图读取器。
 * <p>
 * 解析 Litematica 模组保存的蓝图格式，
 * 支持多区域（Region）、调色板（BlockStatePalette）和位压缩（Bit-Packed）方块数据。
 * 支持识别缺失方块并处理方块实体数据。
 */
final class LitematicReader {

    private LitematicReader() {
    }

    /**
     * 从压缩的字节数组解析 Litematic 蓝图。
     */
    static RtsBlueprint parse(byte[] data, String fileName, RegistryAccess registryAccess) throws BlueprintParseException {
        CompoundTag root = readCompressed(data, fileName);
        if (!root.contains("Regions", Tag.TAG_COMPOUND)) {
            throw new BlueprintParseException("Litematic 文件缺少 Regions 数据: " + fileName);
        }

        HolderGetter<Block> blockLookup = registryAccess.lookupOrThrow(Registries.BLOCK);
        CompoundTag regions = root.getCompound("Regions");
        List<PendingBlock> pending = new ArrayList<>();

        // 遍历所有区域，读取每个区域的方块数据
        for (String regionName : regions.getAllKeys()) {
            if (!regions.contains(regionName, Tag.TAG_COMPOUND)) {
                continue;
            }
            readRegion(fileName, regions.getCompound(regionName), blockLookup, pending);
        }

        if (pending.isEmpty()) {
            return RtsBlueprint.create(readName(root, fileName), fileName, BlueprintFormat.LITEMATIC, Vec3i.ZERO, List.of());
        }

        // 计算所有方块的整体包围盒
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (PendingBlock block : pending) {
            BlockPos pos = block.pos();
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }

        // 将所有坐标归一化到以 (0,0,0) 为起点的相对坐标
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

    /**
     * 读取单个区域（Region）的方块数据。
     * <p>
     * 每个区域包含位置偏移、尺寸、调色板、位压缩方块数据以及方块实体列表。
     */
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
            throw new BlueprintParseException("Litematic 区域过大，无法读取: " + fileName);
        }

        // 读取调色板和方块实体
        List<PaletteEntry> palette = readPalette(region.getList("BlockStatePalette", Tag.TAG_COMPOUND), blockLookup);
        if (palette.isEmpty()) {
            throw new BlueprintParseException("Litematic 区域缺少 BlockStatePalette: " + fileName);
        }

        Map<BlockPos, CompoundTag> blockEntities = readBlockEntities(region);
        long[] blockStates = region.contains("BlockStates", Tag.TAG_LONG_ARRAY)
                ? region.getLongArray("BlockStates")
                : new long[0];
        int bits = bitsPerEntry(palette.size());
        if (palette.size() > 1 && blockStates.length == 0) {
            throw new BlueprintParseException("Litematic 区域缺少 BlockStates: " + fileName);
        }

        // 解码位压缩数据并生成方块列表
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
            // 计算 3D 索引对应的局部坐标
            int storeX = index % absWidth;
            int storeZ = (index / absWidth) % absLength;
            int storeY = index / (absWidth * absLength);
            BlockPos local = new BlockPos(
                    toRegionCoordinate(storeX, width),
                    toRegionCoordinate(storeY, height),
                    toRegionCoordinate(storeZ, length));
            // 转换为绝对世界坐标
            BlockPos absolute = new BlockPos(
                    position.getX() + local.getX(),
                    position.getY() + local.getY(),
                    position.getZ() + local.getZ());
            out.add(new PendingBlock(absolute, paletteEntry, blockEntities.getOrDefault(local, new CompoundTag())));
        }
    }

    /** 读取调色板并解析方块状态 */
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
                throw new BlueprintParseException("Litematic 调色板中存在未知方块状态: " + paletteEntry, ex);
            }
        }
        return out;
    }

    /** 读取方块实体数据 */
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

    /** 从方块实体标签中读取位置（支持多种格式） */
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

    /**
     * 从位压缩数组中读取指定索引的值。
     * <p>
     * Litematic 使用位压缩（Bit-Packed）方式将多个调色板索引
     * 存储在一个 long 数组中，每个条目占 {@code bits} 位。
     */
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

    /** 计算调色板大小所需的位数 */
    private static int bitsPerEntry(int paletteSize) {
        return Math.max(2, 32 - Integer.numberOfLeadingZeros(Math.max(1, paletteSize - 1)));
    }

    /** 将存储坐标转换为区域坐标（处理负尺寸的情况） */
    private static int toRegionCoordinate(int storeCoordinate, int size) {
        return size < 0 ? storeCoordinate + size + 1 : storeCoordinate;
    }

    /** 检测调色板条目是否对应缺失方块 */
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

    /** 读取并解压 NBT 数据 */
    private static CompoundTag readCompressed(byte[] data, String fileName) throws BlueprintParseException {
        try {
            return NbtIo.readCompressed(new ByteArrayInputStream(data), NbtAccounter.unlimitedHeap());
        } catch (Exception ex) {
            throw new BlueprintParseException("读取压缩 Litematic 蓝图失败: " + fileName, ex);
        }
    }

    /** 读取向量（支持 Compound、IntArray、List 三种格式） */
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

    /** 读取蓝图名称 */
    private static String readName(CompoundTag root, String fileName) {
        if (root.contains("Metadata", Tag.TAG_COMPOUND)) {
            CompoundTag metadata = root.getCompound("Metadata");
            if (metadata.contains("Name", Tag.TAG_STRING) && !metadata.getString("Name").isBlank()) {
                return metadata.getString("Name");
            }
        }
        return cleanName(fileName);
    }

    /** 从文件名中提取干净的名称 */
    private static String cleanName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "Blueprint";
        }
        int slash = Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\'));
        String base = slash >= 0 ? fileName.substring(slash + 1) : fileName;
        int dot = base.lastIndexOf('.');
        return dot > 0 ? base.substring(0, dot) : base;
    }

    /** 待处理的方块记录（绝对位置 + 调色板条目 + 方块实体 NBT） */
    private record PendingBlock(BlockPos pos, PaletteEntry paletteEntry, CompoundTag blockEntityTag) {
    }

    /** 内部调色板条目记录 */
    private record PaletteEntry(BlockState state, String missingBlockId) {
    }
}
