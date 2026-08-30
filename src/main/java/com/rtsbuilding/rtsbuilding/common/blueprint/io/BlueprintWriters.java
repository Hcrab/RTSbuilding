package com.rtsbuilding.rtsbuilding.common.blueprint.io;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.common.blueprint.model.BlueprintFormat;
import com.rtsbuilding.rtsbuilding.common.blueprint.model.RtsBlueprint;
import com.rtsbuilding.rtsbuilding.common.blueprint.model.RtsBlueprintBlock;
import com.rtsbuilding.rtsbuilding.common.blueprint.transform.BlueprintTransform;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 蓝图写入器 —— 提供蓝图的捕获、旋转复制和持久化功能。
 * <p>
 * 支持从世界中捕获方块（capture）、旋转蓝图副本（rotatedCopy）、
 * 以及写出为原版结构 NBT 文件（writeVanillaStructure）。
 */
public final class BlueprintWriters {

    private BlueprintWriters() {
    }

    /** 获取最大可捕获方块数 */
    public static int maxCaptureBlocks() {
        return Config.maxBlueprintBlocks();
    }

    /** 获取最大可捕获体积（方块数的 8 倍） */
    public static long maxCaptureVolume() {
        return (long) maxCaptureBlocks() * 8L;
    }

    /**
     * 创建蓝图的一个旋转副本。
     * <p>
     * 绕三个轴分别旋转指定步数（每步 90°），
     * 并自动归一化坐标以实现居中效果。
     *
     * @param blueprint       原始蓝图
     * @param yRotationSteps  Y 轴旋转步数
     * @param xRotationSteps  X 轴旋转步数
     * @param zRotationSteps  Z 轴旋转步数
     * @param name            新蓝图名称
     * @param sourceName      来源名称
     * @return 旋转后的新蓝图
     */
    public static RtsBlueprint rotatedCopy(RtsBlueprint blueprint, int yRotationSteps, int xRotationSteps, int zRotationSteps,
            String name, String sourceName) {
        if (blueprint == null || blueprint.blocks().isEmpty()) {
            return RtsBlueprint.create(name, sourceName, BlueprintFormat.VANILLA_NBT, Vec3i.ZERO, List.of());
        }

        List<RtsBlueprintBlock> rotated = new ArrayList<>(blueprint.blocks().size());
        int ySteps = BlueprintTransform.normalizeSteps(yRotationSteps);
        int xSteps = BlueprintTransform.normalizeSteps(xRotationSteps);
        int zSteps = BlueprintTransform.normalizeSteps(zRotationSteps);
        BlockPos centerOffset = BlueprintTransform.centerRotationOffset(blueprint.size(), ySteps, xSteps, zSteps);
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;

        // 逐个方块旋转并计算新边界
        for (RtsBlueprintBlock block : blueprint.blocks()) {
            BlockPos pos = BlueprintTransform.rotateAroundCenter(block.relativePos(), ySteps, xSteps, zSteps, centerOffset);
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
            rotated.add(new RtsBlueprintBlock(pos, state, blockEntityTagCopy(block), "", block.materialItemId()));
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }

        // 归一化坐标到以 (0,0,0) 为起点
        BlockPos offset = new BlockPos(-minX, -minY, -minZ);
        List<RtsBlueprintBlock> normalized = new ArrayList<>(rotated.size());
        for (RtsBlueprintBlock block : rotated) {
            if (block.isMissingBlock()) {
                normalized.add(RtsBlueprintBlock.missing(block.relativePos().offset(offset), block.missingBlockId(), new CompoundTag()));
                continue;
            }
            normalized.add(new RtsBlueprintBlock(block.relativePos().offset(offset), block.state(), blockEntityTagCopy(block), "",
                    block.materialItemId()));
        }
        Vec3i size = new Vec3i(maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1);
        return RtsBlueprint.create(name, sourceName, BlueprintFormat.VANILLA_NBT, size, normalized);
    }

    /**
     * 从世界中捕获指定区域内的方块并创建蓝图。
     * <p>
     * 遍历两个对角点定义的长方体区域，
     * 跳过空气和结构虚空方块，
     * 记录方块的相对坐标、方块状态、方块实体数据和材料物品 ID。
     *
     * @param level      世界
     * @param first      第一个角点
     * @param second     第二个角点
     * @param name       蓝图名称
     * @param sourceName 来源名称
     * @return 捕获的蓝图
     */
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
        int captureMinY = minY + 1; // 跳过地板层
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
                    blocks.add(new RtsBlueprintBlock(
                            new BlockPos(x - minX, y - captureMinY, z - minZ),
                            state,
                            captureBlockEntityTag(level, cursor),
                            "",
                            resolveMaterialItemId(level, state, cursor)));
                    if (blocks.size() > maxCaptureBlocks()) {
                        throw new IllegalArgumentException("蓝图捕获包含超过 " + maxCaptureBlocks() + " 个方块");
                    }
                }
            }
        }
        Vec3i size = new Vec3i(maxX - minX + 1, Math.max(0, maxY - minY), maxZ - minZ + 1);
        return RtsBlueprint.create(name, sourceName, BlueprintFormat.VANILLA_NBT, size, blocks);
    }

    /**
     * 将蓝图写出为原版结构 NBT 文件。
     */
    public static void writeVanillaStructure(RtsBlueprint blueprint, Path output) throws IOException {
        CompoundTag root = toVanillaStructureTag(blueprint);
        writeTag(root, output);
    }

    /** 将 NBT 标签写入压缩文件 */
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

    /**
     * 将 {@link RtsBlueprint} 转换为原版结构方块的 NBT 标签格式。
     * <p>
     * 生成包含 size、palette（调色板）和 blocks（方块列表）的标准结构文件。
     */
    public static CompoundTag toVanillaStructureTag(RtsBlueprint blueprint) {
        CompoundTag root = new CompoundTag();
        Vec3i size = blueprint.size();
        ListTag sizeTag = new ListTag();
        sizeTag.add(IntTag.valueOf(Math.max(0, size.getX())));
        sizeTag.add(IntTag.valueOf(Math.max(0, size.getY())));
        sizeTag.add(IntTag.valueOf(Math.max(0, size.getZ())));
        root.put("size", sizeTag);

        // 构建调色板：为每种方块状态分配一个唯一整数 ID
        Map<PaletteKey, Integer> paletteIds = new LinkedHashMap<>();
        for (RtsBlueprintBlock block : blueprint.blocks()) {
            paletteIds.computeIfAbsent(PaletteKey.of(block), ignored -> paletteIds.size());
        }

        ListTag palette = new ListTag();
        for (PaletteKey key : paletteIds.keySet()) {
            if (!key.missingBlockId().isBlank()) {
                CompoundTag missing = new CompoundTag();
                missing.putString("Name", key.missingBlockId());
                palette.add(missing);
            } else {
                palette.add(NbtUtils.writeBlockState(key.state()));
            }
        }
        root.put("palette", palette);

        // 写出每个方块的位置、调色板索引、材料和 NBT
        ListTag blocks = new ListTag();
        for (RtsBlueprintBlock block : blueprint.blocks()) {
            CompoundTag blockTag = new CompoundTag();
            ListTag pos = new ListTag();
            pos.add(IntTag.valueOf(block.relativePos().getX()));
            pos.add(IntTag.valueOf(block.relativePos().getY()));
            pos.add(IntTag.valueOf(block.relativePos().getZ()));
            blockTag.put("pos", pos);
            blockTag.putInt("state", paletteIds.getOrDefault(PaletteKey.of(block), 0));
            if (block.materialItemId() != null && !block.materialItemId().isBlank()) {
                blockTag.putString("rtsbuilding_material_item", block.materialItemId());
            }
            if (block.hasBlockEntityTag()) {
                blockTag.put("nbt", block.blockEntityTag().copy());
            }
            blocks.add(blockTag);
        }
        root.put("blocks", blocks);
        return root;
    }

    /** 缺失方块也必须保留在 durable blob 中，否则恢复后的 blockCount/游标会漂移。 */
    private record PaletteKey(BlockState state, String missingBlockId) {
        static PaletteKey of(RtsBlueprintBlock block) {
            return block.isMissingBlock()
                    ? new PaletteKey(Blocks.AIR.defaultBlockState(), block.missingBlockId())
                    : new PaletteKey(block.state(), "");
        }
    }

    /** 复制方块实体的 NBT 标签 */
    private static CompoundTag blockEntityTagCopy(RtsBlueprintBlock block) {
        return block == null || block.blockEntityTag() == null ? new CompoundTag() : block.blockEntityTag().copy();
    }

    /** 捕获世界中方块实体的 NBT 数据 */
    private static CompoundTag captureBlockEntityTag(Level level, BlockPos pos) {
        if (level == null || pos == null) {
            return new CompoundTag();
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null) {
            return new CompoundTag();
        }
        try {
            CompoundTag tag = blockEntity.saveWithFullMetadata(level.registryAccess());
            tag.remove("x");
            tag.remove("y");
            tag.remove("z");
            return tag;
        } catch (RuntimeException ignored) {
            return new CompoundTag();
        }
    }

    /** 解析方块的材料物品 ID */
    private static String resolveMaterialItemId(Level level, BlockState state, BlockPos pos) {
        if (level == null || state == null || pos == null) {
            return "";
        }
        try {
            ItemStack cloneStack = state.getBlock().getCloneItemStack(level, pos, state);
            if (!cloneStack.isEmpty()) {
                ResourceLocation id = BuiltInRegistries.ITEM.getKey(cloneStack.getItem());
                if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
                    return id.toString();
                }
            }
        } catch (RuntimeException ignored) {
        }
        ResourceLocation fallback = BuiltInRegistries.ITEM.getKey(state.getBlock().asItem());
        return fallback == null || !BuiltInRegistries.ITEM.containsKey(fallback) ? "" : fallback.toString();
    }
}
