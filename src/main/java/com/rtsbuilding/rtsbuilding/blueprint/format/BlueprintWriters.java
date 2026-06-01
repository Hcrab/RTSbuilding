package com.rtsbuilding.rtsbuilding.blueprint.format;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.blueprint.BlueprintFormat;
import com.rtsbuilding.rtsbuilding.blueprint.BlueprintTransform;
import com.rtsbuilding.rtsbuilding.blueprint.RtsBlueprint;
import com.rtsbuilding.rtsbuilding.blueprint.RtsBlueprintBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class BlueprintWriters {
    private BlueprintWriters() {
    }

    public static int maxCaptureBlocks() {
        return Config.maxBlueprintBlocks();
    }

    public static long maxCaptureVolume() {
        return (long) maxCaptureBlocks() * 8L;
    }

    public static RtsBlueprint rotatedCopy(RtsBlueprint blueprint, int yRotationSteps, int xRotationSteps, int zRotationSteps,
            String name, String sourceName) {
        if (blueprint == null || blueprint.blocks().isEmpty()) {
            return RtsBlueprint.create(name, sourceName, BlueprintFormat.VANILLA_NBT, Vec3i.ZERO, List.of());
        }

        List<RtsBlueprintBlock> rotated = new ArrayList<>(blueprint.blocks().size());
        int ySteps = BlueprintTransform.normalizeSteps(yRotationSteps);
        int xSteps = BlueprintTransform.normalizeSteps(xRotationSteps);
        int zSteps = BlueprintTransform.normalizeSteps(zRotationSteps);
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;

        for (RtsBlueprintBlock block : blueprint.blocks()) {
            BlockPos pos = BlueprintTransform.rotate(block.relativePos(), ySteps, xSteps, zSteps);
            if (block.isMissingBlock()) {
                rotated.add(RtsBlueprintBlock.missing(pos, block.missingBlockId(), new CompoundTag()));
                minX = Math.min(minX, pos.getX());
                minY = Math.min(minY, pos.getY());
                minZ = Math.min(minZ, pos.getZ());
                maxX = Math.max(maxX, pos.getX());
                maxY = Math.max(maxY, pos.getY());
                maxZ = Math.max(maxZ, pos.getZ());
                continue;
            }
            BlockState state = BlueprintTransform.rotateState(block.state(), ySteps, xSteps, zSteps);
            rotated.add(new RtsBlueprintBlock(pos, state, new CompoundTag()));
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }

        BlockPos offset = new BlockPos(-minX, -minY, -minZ);
        List<RtsBlueprintBlock> normalized = new ArrayList<>(rotated.size());
        for (RtsBlueprintBlock block : rotated) {
            if (block.isMissingBlock()) {
                normalized.add(RtsBlueprintBlock.missing(block.relativePos().offset(offset), block.missingBlockId(), new CompoundTag()));
                continue;
            }
            normalized.add(new RtsBlueprintBlock(block.relativePos().offset(offset), block.state(), new CompoundTag()));
        }
        Vec3i size = new Vec3i(maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1);
        return RtsBlueprint.create(name, sourceName, BlueprintFormat.VANILLA_NBT, size, normalized);
    }

    public static RtsBlueprint capture(Level level, BlockPos first, BlockPos second, String name, String sourceName) {
        if (level == null || first == null || second == null) {
            return RtsBlueprint.create(name, sourceName, BlueprintFormat.VANILLA_NBT, Vec3i.ZERO, List.of());
        }
        int minX = Math.min(first.getX(), second.getX());
        int minY = Math.min(first.getY(), second.getY());
        int minZ = Math.min(first.getZ(), second.getZ());
        int maxX = Math.max(first.getX(), second.getX());
        int maxY = Math.max(first.getY(), second.getY());
        int maxZ = Math.max(first.getZ(), second.getZ());
        int captureMinY = minY + 1;
        List<RtsBlueprintBlock> blocks = new ArrayList<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = captureMinY; y <= maxY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int x = minX; x <= maxX; x++) {
                    cursor.set(x, y, z);
                    BlockState state = level.getBlockState(cursor);
                    if (state.isAir() || state.is(Blocks.STRUCTURE_VOID)) {
                        continue;
                    }
                    blocks.add(new RtsBlueprintBlock(new BlockPos(x - minX, y - captureMinY, z - minZ), state, new CompoundTag()));
                    if (blocks.size() > maxCaptureBlocks()) {
                        throw new IllegalArgumentException("Blueprint capture contains more than " + maxCaptureBlocks() + " blocks");
                    }
                }
            }
        }
        Vec3i size = new Vec3i(maxX - minX + 1, Math.max(0, maxY - minY), maxZ - minZ + 1);
        return RtsBlueprint.create(name, sourceName, BlueprintFormat.VANILLA_NBT, size, blocks);
    }

    public static void writeVanillaStructure(RtsBlueprint blueprint, Path output) throws IOException {
        CompoundTag root = toVanillaStructureTag(blueprint);
        writeTag(root, output);
    }

    private static void writeTag(CompoundTag root, Path output) throws IOException {
        Path parent = output.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (OutputStream stream = Files.newOutputStream(output, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE)) {
            NbtIo.writeCompressed(root, stream);
        }
    }

    private static CompoundTag toVanillaStructureTag(RtsBlueprint blueprint) {
        CompoundTag root = new CompoundTag();
        Vec3i size = blueprint.size();
        ListTag sizeTag = new ListTag();
        sizeTag.add(IntTag.valueOf(Math.max(0, size.getX())));
        sizeTag.add(IntTag.valueOf(Math.max(0, size.getY())));
        sizeTag.add(IntTag.valueOf(Math.max(0, size.getZ())));
        root.put("size", sizeTag);

        Map<BlockState, Integer> paletteIds = new LinkedHashMap<>();
        for (RtsBlueprintBlock block : blueprint.blocks()) {
            if (block.isMissingBlock()) {
                continue;
            }
            paletteIds.computeIfAbsent(block.state(), ignored -> paletteIds.size());
        }

        ListTag palette = new ListTag();
        for (BlockState state : paletteIds.keySet()) {
            palette.add(NbtUtils.writeBlockState(state));
        }
        root.put("palette", palette);

        ListTag blocks = new ListTag();
        for (RtsBlueprintBlock block : blueprint.blocks()) {
            if (block.isMissingBlock()) {
                continue;
            }
            CompoundTag blockTag = new CompoundTag();
            ListTag pos = new ListTag();
            pos.add(IntTag.valueOf(block.relativePos().getX()));
            pos.add(IntTag.valueOf(block.relativePos().getY()));
            pos.add(IntTag.valueOf(block.relativePos().getZ()));
            blockTag.put("pos", pos);
            blockTag.putInt("state", paletteIds.getOrDefault(block.state(), 0));
            blocks.add(blockTag);
        }
        root.put("blocks", blocks);
        return root;
    }
}
