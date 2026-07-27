package com.rtsbuilding.rtsbuilding.common.blueprint.io;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.common.blueprint.material.BlueprintMaterialResolver;
import com.rtsbuilding.rtsbuilding.common.blueprint.model.BlueprintFormat;
import com.rtsbuilding.rtsbuilding.common.blueprint.model.RtsBlueprint;
import com.rtsbuilding.rtsbuilding.common.blueprint.model.RtsBlueprintBlock;
import com.rtsbuilding.rtsbuilding.common.blueprint.transform.BlueprintTransform;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagInt;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.World;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 蓝图捕获、旋转以及原版结构 NBT 写入。 */
public final class BlueprintWriters {
    private BlueprintWriters() {}

    public static int maxCaptureBlocks() { return Config.maxBlueprintBlocks(); }
    public static long maxCaptureVolume() { return (long) maxCaptureBlocks() * 8L; }

    public static RtsBlueprint rotatedCopy(RtsBlueprint blueprint, int yRotationSteps, int xRotationSteps,
                                           int zRotationSteps, String name, String sourceName) {
        if (blueprint == null || blueprint.blocks().isEmpty()) {
            return RtsBlueprint.create(name, sourceName, BlueprintFormat.VANILLA_NBT,
                    new Vec3i(0, 0, 0), Collections.<RtsBlueprintBlock>emptyList());
        }
        int y = BlueprintTransform.normalizeSteps(yRotationSteps);
        int x = BlueprintTransform.normalizeSteps(xRotationSteps);
        int z = BlueprintTransform.normalizeSteps(zRotationSteps);
        BlockPos center = BlueprintTransform.centerRotationOffset(blueprint.size(), y, x, z);
        List<RtsBlueprintBlock> rotated = new ArrayList<RtsBlueprintBlock>(blueprint.blocks().size());
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (RtsBlueprintBlock block : blueprint.blocks()) {
            BlockPos pos = BlueprintTransform.rotateAroundCenter(block.relativePos(), y, x, z, center);
            RtsBlueprintBlock copy = block.isMissingBlock()
                    ? RtsBlueprintBlock.missing(pos, block.missingBlockId(), blockEntityTagCopy(block))
                    : new RtsBlueprintBlock(pos, BlueprintTransform.rotateState(block.state(), y, x, z),
                    blockEntityTagCopy(block), "", block.materialItemId());
            rotated.add(copy);
            minX = Math.min(minX, pos.getX()); minY = Math.min(minY, pos.getY()); minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX()); maxY = Math.max(maxY, pos.getY()); maxZ = Math.max(maxZ, pos.getZ());
        }
        BlockPos offset = new BlockPos(-minX, -minY, -minZ);
        List<RtsBlueprintBlock> normalized = new ArrayList<RtsBlueprintBlock>(rotated.size());
        for (RtsBlueprintBlock block : rotated) {
            BlockPos pos = block.relativePos().add(offset);
            normalized.add(block.isMissingBlock()
                    ? RtsBlueprintBlock.missing(pos, block.missingBlockId(), blockEntityTagCopy(block))
                    : new RtsBlueprintBlock(pos, block.state(), blockEntityTagCopy(block), "", block.materialItemId()));
        }
        return RtsBlueprint.create(name, sourceName, BlueprintFormat.VANILLA_NBT,
                new Vec3i(maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1), normalized);
    }

    public static RtsBlueprint capture(World world, BlockPos first, BlockPos second, String name, String sourceName) {
        if (world == null || first == null || second == null) {
            return RtsBlueprint.create(name, sourceName, BlueprintFormat.VANILLA_NBT,
                    new Vec3i(0, 0, 0), Collections.<RtsBlueprintBlock>emptyList());
        }
        int minX = Math.min(first.getX(), second.getX()), maxX = Math.max(first.getX(), second.getX());
        int minY = Math.min(first.getY(), second.getY()), maxY = Math.max(first.getY(), second.getY());
        int minZ = Math.min(first.getZ(), second.getZ()), maxZ = Math.max(first.getZ(), second.getZ());
        int captureMinY = minY + 1;
        List<RtsBlueprintBlock> blocks = new ArrayList<RtsBlueprintBlock>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = captureMinY; y <= maxY; y++) for (int z = minZ; z <= maxZ; z++) for (int x = minX; x <= maxX; x++) {
            cursor.setPos(x, y, z);
            IBlockState state = world.getBlockState(cursor);
            if (state.getBlock() == Blocks.AIR || state.getBlock() == Blocks.STRUCTURE_VOID) continue;
            blocks.add(new RtsBlueprintBlock(new BlockPos(x - minX, y - captureMinY, z - minZ), state,
                    captureBlockEntityTag(world, cursor), "", resolveMaterialItemId(state)));
            if (blocks.size() > maxCaptureBlocks()) {
                throw new IllegalArgumentException("蓝图捕获包含超过 " + maxCaptureBlocks() + " 个方块");
            }
        }
        return RtsBlueprint.create(name, sourceName, BlueprintFormat.VANILLA_NBT,
                new Vec3i(maxX - minX + 1, Math.max(0, maxY - minY), maxZ - minZ + 1), blocks);
    }

    public static void writeVanillaStructure(RtsBlueprint blueprint, Path output) throws IOException {
        Path parent = output.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        try (OutputStream stream = Files.newOutputStream(output, StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            CompressedStreamTools.writeCompressed(toVanillaStructureTag(blueprint), stream);
        }
    }

    public static NBTTagCompound toVanillaStructureTag(RtsBlueprint blueprint) {
        NBTTagCompound root = new NBTTagCompound();
        root.setTag("size", intList(blueprint.size().getX(), blueprint.size().getY(), blueprint.size().getZ()));
        Map<PaletteKey, Integer> paletteIds = new LinkedHashMap<PaletteKey, Integer>();
        for (RtsBlueprintBlock block : blueprint.blocks()) {
            PaletteKey key = PaletteKey.of(block);
            if (!paletteIds.containsKey(key)) paletteIds.put(key, paletteIds.size());
        }
        NBTTagList palette = new NBTTagList();
        for (PaletteKey key : paletteIds.keySet()) {
            if (!key.missingBlockId.isEmpty()) {
                NBTTagCompound missing = new NBTTagCompound();
                missing.setString("Name", key.missingBlockId);
                palette.appendTag(missing);
            } else {
                palette.appendTag(BlueprintNbtCompat.writeState(key.state));
            }
        }
        root.setTag("palette", palette);

        NBTTagList blocks = new NBTTagList();
        for (RtsBlueprintBlock block : blueprint.blocks()) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setTag("pos", intList(block.relativePos().getX(), block.relativePos().getY(), block.relativePos().getZ()));
            Integer stateId = paletteIds.get(PaletteKey.of(block));
            tag.setInteger("state", stateId == null ? 0 : stateId);
            if (!block.materialItemId().trim().isEmpty()) tag.setString("rtsbuilding_material_item", block.materialItemId());
            if (block.hasBlockEntityTag()) tag.setTag("nbt", blockEntityTagCopy(block));
            blocks.appendTag(tag);
        }
        root.setTag("blocks", blocks);
        return root;
    }

    private static NBTTagList intList(int x, int y, int z) {
        NBTTagList out = new NBTTagList();
        out.appendTag(new NBTTagInt(Math.max(0, x)));
        out.appendTag(new NBTTagInt(Math.max(0, y)));
        out.appendTag(new NBTTagInt(Math.max(0, z)));
        return out;
    }

    private static NBTTagCompound blockEntityTagCopy(RtsBlueprintBlock block) {
        return block == null || block.blockEntityTag() == null ? new NBTTagCompound() : block.blockEntityTag().copy();
    }

    private static NBTTagCompound captureBlockEntityTag(World world, BlockPos pos) {
        TileEntity tile = world.getTileEntity(pos);
        if (tile == null) return new NBTTagCompound();
        try {
            NBTTagCompound tag = tile.writeToNBT(new NBTTagCompound());
            tag.removeTag("x"); tag.removeTag("y"); tag.removeTag("z");
            return tag;
        } catch (RuntimeException ignored) { return new NBTTagCompound(); }
    }

    private static String resolveMaterialItemId(IBlockState state) {
        Item item = BlueprintMaterialResolver.materialItem(state);
        ResourceLocation id = Item.REGISTRY.getNameForObject(item);
        return id == null ? "" : id.toString();
    }

    private static final class PaletteKey {
        private final IBlockState state;
        private final String missingBlockId;
        private PaletteKey(IBlockState state, String missingBlockId) {
            this.state = state;
            this.missingBlockId = missingBlockId == null ? "" : missingBlockId;
        }
        static PaletteKey of(RtsBlueprintBlock block) {
            return block.isMissingBlock() ? new PaletteKey(Blocks.AIR.getDefaultState(), block.missingBlockId())
                    : new PaletteKey(block.state(), "");
        }
        @Override public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof PaletteKey)) return false;
            PaletteKey other = (PaletteKey) obj;
            return state.equals(other.state) && missingBlockId.equals(other.missingBlockId);
        }
        @Override public int hashCode() { return 31 * state.hashCode() + missingBlockId.hashCode(); }
    }
}
