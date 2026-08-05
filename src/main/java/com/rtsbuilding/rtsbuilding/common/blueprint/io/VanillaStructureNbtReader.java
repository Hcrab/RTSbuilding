package com.rtsbuilding.rtsbuilding.common.blueprint.io;

import com.rtsbuilding.rtsbuilding.common.blueprint.model.BlueprintFormat;
import com.rtsbuilding.rtsbuilding.common.blueprint.model.BlueprintParseException;
import com.rtsbuilding.rtsbuilding.common.blueprint.model.RtsBlueprint;
import com.rtsbuilding.rtsbuilding.common.blueprint.model.RtsBlueprintBlock;
import com.rtsbuilding.rtsbuilding.platform.block.BlockState;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import com.rtsbuilding.rtsbuilding.platform.math.BlockPos;
import com.rtsbuilding.rtsbuilding.platform.math.Vec3i;
import net.minecraftforge.common.util.Constants;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 原版结构 NBT 读取器，使用 1.12 Name/Properties 显式状态格式。 */
public final class VanillaStructureNbtReader {
    private VanillaStructureNbtReader() {}

    static RtsBlueprint parse(byte[] data, String fileName) throws BlueprintParseException {
        return parse(readCompressed(data, fileName), cleanName(fileName), fileName);
    }

    public static RtsBlueprint parse(NBTTagCompound root, String name, String sourceName) {
        if (!root.hasKey("palette", Constants.NBT.TAG_LIST) || !root.hasKey("blocks", Constants.NBT.TAG_LIST)) {
            return RtsBlueprint.create(name, sourceName, BlueprintFormat.VANILLA_NBT,
                    new Vec3i(0, 0, 0), Collections.<RtsBlueprintBlock>emptyList());
        }
        NBTTagList paletteTag = root.getTagList("palette", Constants.NBT.TAG_COMPOUND);
        List<BlueprintNbtCompat.StateResult> palette = new ArrayList<BlueprintNbtCompat.StateResult>(paletteTag.tagCount());
        for (int i = 0; i < paletteTag.tagCount(); i++) {
            palette.add(BlueprintNbtCompat.readState(paletteTag.getCompoundTagAt(i)));
        }

        List<RtsBlueprintBlock> out = new ArrayList<RtsBlueprintBlock>();
        NBTTagList blockList = root.getTagList("blocks", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < blockList.tagCount(); i++) {
            NBTTagCompound blockTag = blockList.getCompoundTagAt(i);
            int stateIndex = blockTag.getInteger("state");
            if (stateIndex < 0 || stateIndex >= palette.size()) continue;
            BlueprintNbtCompat.StateResult entry = palette.get(stateIndex);
            BlockPos pos = readPos(blockTag);
            NBTTagCompound blockEntityTag = blockTag.hasKey("nbt", Constants.NBT.TAG_COMPOUND)
                    ? com.rtsbuilding.rtsbuilding.platform.nbt.NbtCompat.copyCompound(
                            blockTag.getCompoundTag("nbt")) : new NBTTagCompound();
            if (entry.isMissing()) {
                out.add(RtsBlueprintBlock.missing(pos, entry.missingBlockId(), blockEntityTag));
            } else {
                BlockState state = entry.state();
                if (state.getBlock() != Blocks.air && state.getBlock() != Blocks.air) {
                    out.add(new RtsBlueprintBlock(pos, state, blockEntityTag, "",
                            blockTag.getString("rtsbuilding_material_item")));
                }
            }
        }
        return RtsBlueprint.create(name, sourceName, BlueprintFormat.VANILLA_NBT, readSize(root), out);
    }

    /** 迁移期兼容入口；1.12 不需要 RegistryAccess。 */
    public static RtsBlueprint parse(NBTTagCompound root, String name, String sourceName, Object ignored) {
        return parse(root, name, sourceName);
    }

    private static NBTTagCompound readCompressed(byte[] data, String fileName) throws BlueprintParseException {
        try { return CompressedStreamTools.readCompressed(new ByteArrayInputStream(data)); }
        catch (Exception ex) { throw new BlueprintParseException("读取压缩 NBT 蓝图失败: " + fileName, ex); }
    }

    private static Vec3i readSize(NBTTagCompound root) {
        NBTTagList values = root.getTagList("size", Constants.NBT.TAG_INT);
        return values.tagCount() < 3 ? new Vec3i(0, 0, 0)
                : new Vec3i(
                        com.rtsbuilding.rtsbuilding.platform.nbt.NbtCompat.getIntAt(values, 0),
                        com.rtsbuilding.rtsbuilding.platform.nbt.NbtCompat.getIntAt(values, 1),
                        com.rtsbuilding.rtsbuilding.platform.nbt.NbtCompat.getIntAt(values, 2));
    }

    private static BlockPos readPos(NBTTagCompound blockTag) {
        NBTTagList values = blockTag.getTagList("pos", Constants.NBT.TAG_INT);
        return values.tagCount() < 3 ? BlockPos.ORIGIN
                : new BlockPos(
                        com.rtsbuilding.rtsbuilding.platform.nbt.NbtCompat.getIntAt(values, 0),
                        com.rtsbuilding.rtsbuilding.platform.nbt.NbtCompat.getIntAt(values, 1),
                        com.rtsbuilding.rtsbuilding.platform.nbt.NbtCompat.getIntAt(values, 2));
    }

    private static String cleanName(String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) return "Blueprint";
        int slash = Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\'));
        String base = slash >= 0 ? fileName.substring(slash + 1) : fileName;
        int dot = base.lastIndexOf('.');
        return dot > 0 ? base.substring(0, dot) : base;
    }
}
