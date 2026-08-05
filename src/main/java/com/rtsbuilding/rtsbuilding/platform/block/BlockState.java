package com.rtsbuilding.rtsbuilding.platform.block;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import com.rtsbuilding.rtsbuilding.platform.math.AxisAlignedBB;
import com.rtsbuilding.rtsbuilding.platform.math.BlockPos;
import com.rtsbuilding.rtsbuilding.platform.math.EnumFacing;

/**
 * 1.7.10 的 Block + metadata 在 RTSBuilding 业务层中的不可变表示。
 *
 * <p>这不是伪造的 Minecraft {@code BlockState}。它明确保存旧版真实数据，并把世界坐标
 * 访问集中在少数方法中。GTNH 机器的额外朝向/模式以后由属性适配器或 TileEntity NBT
 * 负责，未知属性默认拒绝写入。</p>
 */
public final class BlockState {
    private final Block block;
    private final int metadata;
    private final Map<IProperty<?>, Comparable<?>> properties;

    private BlockState(Block block, int metadata, Map<IProperty<?>, Comparable<?>> properties) {
        this.block = block == null ? net.minecraft.init.Blocks.air : block;
        this.metadata = metadata;
        this.properties = properties == null || properties.isEmpty()
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(properties));
    }

    public static BlockState of(Block block, int metadata) {
        return new BlockState(block, metadata, Collections.emptyMap());
    }

    public static BlockState defaultState(Block block) {
        return of(block, 0);
    }

    /** 网络负载使用稳定的 1.7.10 block-id + 4-bit metadata 紧凑表示。 */
    public static int getStateId(BlockState state) {
        if (state == null) return 0;
        return Block.getIdFromBlock(state.block) << 4 | state.metadata & 15;
    }

    public static BlockState getStateById(int packed) {
        Block block = Block.getBlockById(packed >>> 4);
        return of(block, packed & 15);
    }

    /** 复现 1.7.10 ItemBlock 在真正写入世界前计算 metadata 的步骤。 */
    public static BlockState forPlacement(ItemBlock item, ItemStack stack, World world,
            BlockPos pos, EnumFacing face, float hitX, float hitY, float hitZ) {
        if (item == null || stack == null || world == null || pos == null || face == null) {
            return defaultState(net.minecraft.init.Blocks.air);
        }
        Block block = Block.getBlockFromItem(item);
        int baseMetadata = item.getMetadata(stack.getItemDamage());
        int placedMetadata = block.onBlockPlaced(
                world, pos.getX(), pos.getY(), pos.getZ(), face.getIndex(),
                hitX, hitY, hitZ, baseMetadata);
        return of(block, placedMetadata);
    }

    public static BlockState fromWorld(World world, BlockPos pos) {
        if (world == null || pos == null) return defaultState(net.minecraft.init.Blocks.air);
        return of(
                world.getBlock(pos.getX(), pos.getY(), pos.getZ()),
                world.getBlockMetadata(pos.getX(), pos.getY(), pos.getZ()));
    }

    public Block getBlock() {
        return this.block;
    }

    public int getMetadata() {
        return this.metadata;
    }

    public Material getMaterial() {
        return this.block.getMaterial();
    }

    public float getBlockHardness(World world, BlockPos pos) {
        return this.block.getBlockHardness(world, pos.getX(), pos.getY(), pos.getZ());
    }

    public float getPlayerRelativeBlockHardness(EntityPlayer player, World world, BlockPos pos) {
        return this.block.getPlayerRelativeBlockHardness(
                player, world, pos.getX(), pos.getY(), pos.getZ());
    }

    public AxisAlignedBB getCollisionBoundingBox(World world, BlockPos pos) {
        return AxisAlignedBB.fromNative(this.block.getCollisionBoundingBoxFromPool(
                world, pos.getX(), pos.getY(), pos.getZ()));
    }

    public EnumBlockRenderType getRenderType() {
        int renderType = this.block.getRenderType();
        if (renderType < 0) return EnumBlockRenderType.INVISIBLE;
        return this.block.hasTileEntity(this.metadata)
                ? EnumBlockRenderType.ENTITYBLOCK_ANIMATED
                : EnumBlockRenderType.MODEL;
    }

    public Collection<IProperty<?>> getPropertyKeys() {
        return this.properties.keySet();
    }

    public Map<IProperty<?>, Comparable<?>> getProperties() {
        return this.properties;
    }

    @SuppressWarnings("unchecked")
    public <T extends Comparable<T>> T getValue(IProperty<T> property) {
        Comparable<?> cached = this.properties.get(property);
        return cached == null ? property.read(this.block, this.metadata) : (T) cached;
    }

    public <T extends Comparable<T>> BlockState withProperty(IProperty<T> property, T value) {
        if (property == null || value == null || !property.getAllowedValues().contains(value)) return this;
        int changedMetadata = property.write(this.block, this.metadata, value);
        Map<IProperty<?>, Comparable<?>> changed = new LinkedHashMap<>(this.properties);
        changed.put(property, value);
        return new BlockState(this.block, changedMetadata, changed);
    }

    public BlockState withRotation(Rotation rotation) {
        // 纯 metadata 旋转由后续 GTNH 方块属性注册表处理；未知方块保持原值最安全。
        return this;
    }

    public boolean setInWorld(World world, BlockPos pos, int flags) {
        return world.setBlock(
                pos.getX(), pos.getY(), pos.getZ(), this.block, this.metadata, flags);
    }

    @Override
    public boolean equals(Object value) {
        if (this == value) return true;
        if (!(value instanceof BlockState)) return false;
        BlockState other = (BlockState) value;
        return this.block == other.block && this.metadata == other.metadata
                && this.properties.equals(other.properties);
    }

    @Override
    public int hashCode() {
        int result = System.identityHashCode(this.block);
        result = 31 * result + this.metadata;
        return 31 * result + this.properties.hashCode();
    }

    @Override
    public String toString() {
        return "BlockState{" + Block.blockRegistry.getNameForObject(this.block)
                + ":" + this.metadata + "}";
    }
}
