package com.rtsbuilding.rtsbuilding.common.blueprint.io;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.rtsbuilding.rtsbuilding.common.blueprint.model.BlueprintFormat;
import com.rtsbuilding.rtsbuilding.common.blueprint.model.BlueprintParseException;
import com.rtsbuilding.rtsbuilding.common.blueprint.model.RtsBlueprint;
import com.rtsbuilding.rtsbuilding.common.blueprint.model.RtsBlueprintBlock;
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

/**
 * Sponge Schematic 格式（.schem / .schematic）蓝图读取器。
 * <p>
 * 解析 Sponge 模组生态的标准 Schematic 格式，
 * 包含调色板（Palette）和变长编码（VarInt）的方块数据。
 * 支持识别缺失方块的 ID 并标记。
 */
final class SpongeSchemReader {

    private SpongeSchemReader() {
    }

    /**
     * 从压缩的字节数组解析 Sponge Schematic 蓝图。
     */
    static RtsBlueprint parse(byte[] data, String fileName, RegistryAccess registryAccess) throws BlueprintParseException {
        CompoundTag root = readCompressed(data, fileName);
        // 有些 Schematic 文件在最外层包含 "Schematic" 键
        CompoundTag schematic = root.contains("Schematic", Tag.TAG_COMPOUND) ? root.getCompound("Schematic") : root;
        if (!schematic.contains("Blocks", Tag.TAG_COMPOUND)) {
            throw new BlueprintParseException("Schematic 文件缺少 Blocks 数据: " + fileName);
        }

        int width = readPositiveDimension(schematic, "Width");
        int height = readPositiveDimension(schematic, "Height");
        int length = readPositiveDimension(schematic, "Length");
        if (width <= 0 || height <= 0 || length <= 0) {
            throw new BlueprintParseException("Schematic 尺寸无效: " + fileName);
        }

        CompoundTag blocksRoot = schematic.getCompound("Blocks");
        CompoundTag paletteTag = blocksRoot.getCompound("Palette");
        byte[] packed = readBlockData(blocksRoot);
        HolderLookup<Block> blockLookup = registryAccess.lookupOrThrow(Registries.BLOCK);
        Map<Integer, PaletteEntry> palette = readPalette(paletteTag, blockLookup);

        // 解码变长整型数组并生成方块列表
        List<Integer> stateIds = decodeVarInts(packed, width * height * length);
        List<RtsBlueprintBlock> out = new ArrayList<>();
        int expected = width * height * length;
        for (int index = 0; index < expected && index < stateIds.size(); index++) {
            PaletteEntry paletteEntry = palette.get(stateIds.get(index));
            if (paletteEntry == null) {
                continue;
            }
            BlockState state = paletteEntry.state();
            // 计算 3D 索引对应的坐标 (x, y, z)
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

    /** 读取并解压 Schematic 文件 */
    private static CompoundTag readCompressed(byte[] data, String fileName) throws BlueprintParseException {
        try {
            return NbtIo.readCompressed(new ByteArrayInputStream(data), NbtAccounter.unlimitedHeap());
        } catch (Exception ex) {
            throw new BlueprintParseException("读取压缩 Schematic 失败: " + fileName, ex);
        }
    }

    /** 读取正数尺寸值（支持 short 或 int） */
    private static int readPositiveDimension(CompoundTag tag, String key) {
        return tag.contains(key, Tag.TAG_SHORT) ? Short.toUnsignedInt(tag.getShort(key)) : tag.getInt(key);
    }

    /** 读取方块数据字节数组 */
    private static byte[] readBlockData(CompoundTag blocksRoot) throws BlueprintParseException {
        if (!blocksRoot.contains("Data", Tag.TAG_BYTE_ARRAY)) {
            throw new BlueprintParseException("Schematic 缺少 Blocks.Data");
        }
        Tag raw = blocksRoot.get("Data");
        if (raw instanceof ByteArrayTag byteArrayTag) {
            return byteArrayTag.getAsByteArray();
        }
        return blocksRoot.getByteArray("Data");
    }

    /** 读取调色板并解析方块状态 */
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
                throw new BlueprintParseException("Schematic 调色板中存在未知方块状态: " + key, ex);
            }
        }
        return out;
    }

    /** 检测方块状态键是否对应缺失方块 */
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

    /**
     * 解码变长整数（VarInt）数组。
     * <p>
     * Sponge Schematic 使用类似 Protocol Buffers 的 VarInt 编码来存储方块状态索引。
     * 每个 VarInt 由若干个 7-bit 分组组成，最高位表示是否还有后续字节。
     */
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
                    throw new BlueprintParseException("Schematic 方块数据变长编码格式错误");
                }
            }
        }
        return out;
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

    /** 内部调色板条目记录 */
    private record PaletteEntry(BlockState state, String missingBlockId) {
    }
}
