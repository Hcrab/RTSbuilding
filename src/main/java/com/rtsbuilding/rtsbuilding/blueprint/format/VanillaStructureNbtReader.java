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
import java.util.List;

final class VanillaStructureNbtReader {
    private VanillaStructureNbtReader() {
    }

    static RtsBlueprint parse(byte[] data, String fileName, RegistryAccess registryAccess) throws BlueprintParseException {
        CompoundTag root = readCompressed(data, fileName);
        if (!root.contains("palette", Tag.TAG_LIST) || !root.contains("blocks", Tag.TAG_LIST)) {
            throw new BlueprintParseException("NBT file is not a vanilla structure blueprint: " + fileName);
        }

        HolderGetter<Block> blocks = registryAccess.lookupOrThrow(Registries.BLOCK);
        ListTag paletteTag = root.getList("palette", Tag.TAG_COMPOUND);
        List<PaletteEntry> palette = new ArrayList<>(paletteTag.size());
        for (int i = 0; i < paletteTag.size(); i++) {
            CompoundTag paletteEntry = paletteTag.getCompound(i);
            String missingId = missingBlockId(paletteEntry);
            palette.add(missingId.isBlank()
                    ? new PaletteEntry(NbtUtils.readBlockState(blocks, paletteEntry), "")
                    : new PaletteEntry(Blocks.AIR.defaultBlockState(), missingId));
        }

        Vec3i size = readSize(root);
        ListTag blockList = root.getList("blocks", Tag.TAG_COMPOUND);
        List<RtsBlueprintBlock> out = new ArrayList<>();
        for (int i = 0; i < blockList.size(); i++) {
            CompoundTag blockTag = blockList.getCompound(i);
            int stateIndex = blockTag.getInt("state");
            if (stateIndex < 0 || stateIndex >= palette.size()) {
                continue;
            }
            PaletteEntry paletteEntry = palette.get(stateIndex);
            BlockState state = paletteEntry.state();
            BlockPos pos = readPos(blockTag);
            CompoundTag blockEntityTag = blockTag.contains("nbt", Tag.TAG_COMPOUND)
                    ? blockTag.getCompound("nbt").copy()
                    : new CompoundTag();
            String materialItemId = blockTag.contains("rtsbuilding_material_item", Tag.TAG_STRING)
                    ? blockTag.getString("rtsbuilding_material_item")
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
        return RtsBlueprint.create(cleanName(fileName), fileName, BlueprintFormat.VANILLA_NBT, size, out);
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
            throw new BlueprintParseException("Failed to read compressed NBT blueprint: " + fileName, ex);
        }
    }

    private static Vec3i readSize(CompoundTag root) {
        if (!root.contains("size", Tag.TAG_LIST)) {
            return Vec3i.ZERO;
        }
        ListTag sizeTag = root.getList("size", Tag.TAG_INT);
        if (sizeTag.size() < 3) {
            return Vec3i.ZERO;
        }
        return new Vec3i(sizeTag.getInt(0), sizeTag.getInt(1), sizeTag.getInt(2));
    }

    private static BlockPos readPos(CompoundTag blockTag) {
        ListTag posTag = blockTag.getList("pos", Tag.TAG_INT);
        if (posTag.size() < 3) {
            return BlockPos.ZERO;
        }
        return new BlockPos(posTag.getInt(0), posTag.getInt(1), posTag.getInt(2));
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
