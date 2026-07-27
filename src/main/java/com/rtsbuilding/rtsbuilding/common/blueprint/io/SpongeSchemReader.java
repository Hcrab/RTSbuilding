package com.rtsbuilding.rtsbuilding.common.blueprint.io;

import com.rtsbuilding.rtsbuilding.common.blueprint.model.BlueprintFormat;
import com.rtsbuilding.rtsbuilding.common.blueprint.model.BlueprintParseException;
import com.rtsbuilding.rtsbuilding.common.blueprint.model.RtsBlueprint;
import com.rtsbuilding.rtsbuilding.common.blueprint.model.RtsBlueprintBlock;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import net.minecraftforge.common.util.Constants;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Sponge schematic v2/v3 的 Palette + VarInt 数据读取器。 */
final class SpongeSchemReader {
    private SpongeSchemReader() {}

    static RtsBlueprint parse(byte[] data, String fileName) throws BlueprintParseException {
        NBTTagCompound root = readCompressed(data, fileName);
        NBTTagCompound schematic = root.hasKey("Schematic", Constants.NBT.TAG_COMPOUND)
                ? root.getCompoundTag("Schematic") : root;
        if (!schematic.hasKey("Blocks", Constants.NBT.TAG_COMPOUND)) {
            throw new BlueprintParseException("Schematic 文件缺少 Blocks 数据: " + fileName);
        }
        int width = dimension(schematic, "Width"), height = dimension(schematic, "Height"), length = dimension(schematic, "Length");
        if (width <= 0 || height <= 0 || length <= 0) throw new BlueprintParseException("Schematic 尺寸无效: " + fileName);
        NBTTagCompound blocksRoot = schematic.getCompoundTag("Blocks");
        Map<Integer, BlueprintNbtCompat.StateResult> palette = readPalette(blocksRoot.getCompoundTag("Palette"));
        List<Integer> ids = decodeVarInts(blocksRoot.getByteArray("Data"), width * height * length);
        List<RtsBlueprintBlock> out = new ArrayList<RtsBlueprintBlock>();
        int expected = width * height * length;
        for (int index = 0; index < expected && index < ids.size(); index++) {
            BlueprintNbtCompat.StateResult entry = palette.get(ids.get(index));
            if (entry == null) continue;
            BlockPos pos = new BlockPos(index % width, index / (width * length), (index / width) % length);
            if (entry.isMissing()) out.add(RtsBlueprintBlock.missing(pos, entry.missingBlockId(), new NBTTagCompound()));
            else if (entry.state().getBlock() != Blocks.AIR && entry.state().getBlock() != Blocks.STRUCTURE_VOID)
                out.add(new RtsBlueprintBlock(pos, entry.state(), new NBTTagCompound()));
        }
        return RtsBlueprint.create(cleanName(fileName), fileName, BlueprintFormat.SPONGE_SCHEM,
                new Vec3i(width, height, length), out);
    }

    private static NBTTagCompound readCompressed(byte[] data, String fileName) throws BlueprintParseException {
        try { return CompressedStreamTools.readCompressed(new ByteArrayInputStream(data)); }
        catch (Exception ex) { throw new BlueprintParseException("读取压缩 Schematic 失败: " + fileName, ex); }
    }

    private static int dimension(NBTTagCompound tag, String key) {
        return tag.hasKey(key, Constants.NBT.TAG_SHORT) ? tag.getShort(key) & 0xffff : tag.getInteger(key);
    }

    private static Map<Integer, BlueprintNbtCompat.StateResult> readPalette(NBTTagCompound tag) {
        Map<Integer, BlueprintNbtCompat.StateResult> out = new HashMap<Integer, BlueprintNbtCompat.StateResult>();
        for (String key : tag.getKeySet()) out.put(tag.getInteger(key), BlueprintNbtCompat.readStateString(key));
        return out;
    }

    private static List<Integer> decodeVarInts(byte[] data, int maxEntries) throws BlueprintParseException {
        List<Integer> out = new ArrayList<Integer>(Math.min(Math.max(0, maxEntries), 8192));
        int value = 0, shift = 0;
        for (byte b : data) {
            value |= (b & 0x7f) << shift;
            if ((b & 0x80) == 0) {
                out.add(value); if (out.size() >= maxEntries) break; value = 0; shift = 0;
            } else if ((shift += 7) > 35) {
                throw new BlueprintParseException("Schematic 方块数据 VarInt 格式错误");
            }
        }
        return out;
    }

    private static String cleanName(String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) return "Blueprint";
        int slash = Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\'));
        String base = slash >= 0 ? fileName.substring(slash + 1) : fileName;
        int dot = base.lastIndexOf('.'); return dot > 0 ? base.substring(0, dot) : base;
    }
}
