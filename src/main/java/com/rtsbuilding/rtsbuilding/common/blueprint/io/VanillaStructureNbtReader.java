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
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 原版 Minecraft 结构 NBT（.nbt）蓝图读取器。
 * <p>
 * 解析由 Minecraft 结构方块保存的标准 NBT 格式，
 * 以及由 {@link BlueprintWriters#toVanillaStructureTag(RtsBlueprint)} 生成的内部格式。
 * 还包含一个从已解压的 {@link CompoundTag} 直接解析的公开方法，用于持久化恢复路径。
 */
public final class VanillaStructureNbtReader {

    private VanillaStructureNbtReader() {
    }

    /**
     * 从压缩的字节数组解析原版 NBT 蓝图。
     */
    static RtsBlueprint parse(byte[] data, String fileName, RegistryAccess registryAccess) throws BlueprintParseException {
        CompoundTag root = readCompressed(data, fileName);
        return parse(root, cleanName(fileName), fileName, registryAccess);
    }

    /**
     * 从已经解压的 {@link CompoundTag} 解析蓝图。
     * <p>
     * 用于持久化恢复路径——直接从存储的 NBT 重建蓝图，无需经过文件 I/O。
     *
     * @param root           包含蓝图数据的 NBT 标签
     * @param name           蓝图名称
     * @param sourceName     来源文件名
     * @param registryAccess 注册表访问
     * @return 解析后的蓝图对象
     */
    public static RtsBlueprint parse(CompoundTag root, String name, String sourceName, RegistryAccess registryAccess) {
        if (!root.contains("palette") || !root.contains("blocks")) {
            return RtsBlueprint.create(name, sourceName, BlueprintFormat.VANILLA_NBT, Vec3i.ZERO, List.of());
        }

        HolderGetter<Block> blocks = registryAccess.lookupOrThrow(Registries.BLOCK);
        ListTag paletteTag = root.getListOrEmpty("palette");
        List<PaletteEntry> palette = new ArrayList<>(paletteTag.size());
        for (int i = 0; i < paletteTag.size(); i++) {
            CompoundTag paletteEntry = paletteTag.getCompoundOrEmpty(i);
            String missingId = missingBlockId(paletteEntry);
            palette.add(missingId.isBlank()
                    ? new PaletteEntry(NbtUtils.readBlockState(blocks, paletteEntry), "")
                    : new PaletteEntry(Blocks.AIR.defaultBlockState(), missingId));
        }

        Vec3i size = readSize(root);
        ListTag blockList = root.getListOrEmpty("blocks");
        List<RtsBlueprintBlock> out = new ArrayList<>();
        for (int i = 0; i < blockList.size(); i++) {
            CompoundTag blockTag = blockList.getCompoundOrEmpty(i);
            int stateIndex = blockTag.getIntOr("state", 0);
            if (stateIndex < 0 || stateIndex >= palette.size()) {
                continue;
            }
            PaletteEntry paletteEntry = palette.get(stateIndex);
            BlockState state = paletteEntry.state();
            BlockPos pos = readPos(blockTag);
            CompoundTag blockEntityTag = blockTag.contains("nbt")
                    ? blockTag.getCompoundOrEmpty("nbt").copy()
                    : new CompoundTag();
            String materialItemId = blockTag.contains("rtsbuilding_material_item")
                    ? blockTag.getStringOr("rtsbuilding_material_item", "")
                    : "";
            if (!paletteEntry.missingBlockId().isBlank()) {
                out.add(RtsBlueprintBlock.missing(pos, paletteEntry.missingBlockId(), blockEntityTag));
                continue;
            }
            if (state.isAir() || state.is(Blocks.STRUCTURE_VOID)) {
                continue;
            }
            out.add(new RtsBlueprintBlock(pos, state, blockEntityTag, "", materialItemId));
        }
        return RtsBlueprint.create(name, sourceName, BlueprintFormat.VANILLA_NBT, size, out);
    }

    /** 检测调色板条目是否对应缺失方块 */
    private static String missingBlockId(CompoundTag paletteEntry) {
        if (!paletteEntry.contains("Name")) {
            return "";
        }
        String name = paletteEntry.getStringOr("Name", "");
        Identifier id = Identifier.tryParse(name);
        if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) {
            return name == null ? "" : name;
        }
        return "";
    }

    /** 读取并解压 NBT 数据 */
    private static CompoundTag readCompressed(byte[] data, String fileName) throws BlueprintParseException {
        try {
            return NbtIo.readCompressed(new ByteArrayInputStream(data), NbtAccounter.create(128L * 1024L * 1024L));
        } catch (Exception ex) {
            throw new BlueprintParseException("读取压缩 NBT 蓝图失败: " + fileName, ex);
        }
    }

    /** 读取蓝图尺寸 */
    private static Vec3i readSize(CompoundTag root) {
        if (!root.contains("size")) {
            return Vec3i.ZERO;
        }
        ListTag sizeTag = root.getListOrEmpty("size");
        if (sizeTag.size() < 3) {
            return Vec3i.ZERO;
        }
        return new Vec3i(sizeTag.getIntOr(0, 0), sizeTag.getIntOr(1, 0), sizeTag.getIntOr(2, 0));
    }

    /** 读取方块位置数据 */
    private static BlockPos readPos(CompoundTag blockTag) {
        ListTag posTag = blockTag.getListOrEmpty("pos");
        if (posTag.size() < 3) {
            return BlockPos.ZERO;
        }
        return new BlockPos(posTag.getIntOr(0, 0), posTag.getIntOr(1, 0), posTag.getIntOr(2, 0));
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
