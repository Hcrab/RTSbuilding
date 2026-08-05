package com.rtsbuilding.rtsbuilding.compat.create;

import net.minecraft.block.Block;
import com.rtsbuilding.rtsbuilding.platform.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import com.rtsbuilding.rtsbuilding.platform.math.BlockPos;
import net.minecraft.world.WorldServer;

import javax.annotation.Nullable;

/**
 * Create Legacy（非官方 1.12.2 回移植）的蓝图放置隔离层。
 *
 * <p>Create Legacy 没有现代 Create 的 {@code BlockHelper.prepareBlockEntityData} 或结构蓝图 API，
 * 所以这里绝不反射调用不存在的现代类。普通方块始终使用原版 Forge 放置路径；仅对确认属于
 * {@code create} 命名空间的旧版传送带做最小 NBT 清理，避免复制运行时物品与运动状态。</p>
 */
public final class BlueprintCreatePlacementCompat {
    private static final String CREATE_NAMESPACE = "create";
    private static final String BELT_STRAIGHT = "belt_straight";
    private static final String BELT_DIAGONAL = "belt_diagonal";
    private static final int NOTIFY_NEIGHBORS_AND_CLIENTS = 3;

    private BlueprintCreatePlacementCompat() {
    }

    /** 1.12.2 无 UPDATE_KNOWN_SHAPE；Create Legacy 也没有要求抑制邻居通知的公开放置协议。 */
    public static int placementFlags(BlockState state) {
        return NOTIFY_NEIGHBORS_AND_CLIENTS;
    }

    /**
     * 返回可安全交给 1.12 TileEntity.readFromNBT 的副本。
     * Create Legacy 缺失时本方法只做原样复制，不会禁用或跳过蓝图放置。
     */
    @Nullable
    public static NBTTagCompound prepareBlockEntityTag(
            WorldServer level, BlockPos target, BlockState state,
            @Nullable NBTTagCompound original) {
        if (original == null || com.rtsbuilding.rtsbuilding.platform.nbt.NbtCompat.isEmpty(original)) {
            return original;
        }
        NBTTagCompound prepared = (NBTTagCompound) original.copy();
        if (isLegacyBelt(state)) {
            // 这些字段是 Create Legacy TileEntityBeltBase 的瞬时运输/插值状态，不能跨世界复制。
            removeAll(prepared,
                    "left", "right", "leftPos", "rightPos", "leftPosOld", "rightPosOld",
                    "speed", "lastUpdateTick", "flag");
        }
        prepared.setInteger("x", target.getX());
        prepared.setInteger("y", target.getY());
        prepared.setInteger("z", target.getZ());
        return prepared;
    }

    /**
     * 使用 1.12 原版放置回调完成初始化。Create Legacy 未安装时，普通模组与原版方块行为不变。
     */
    public static void finishPlacement(
            WorldServer level, BlockPos target, BlockState state, @Nullable ItemStack stack) {
        if (level == null || target == null || !isCreateBlock(state)) {
            return;
        }
        try {
            Block block = state.getBlock();
            block.onBlockPlacedBy(level, target.getX(), target.getY(), target.getZ(),
                    null, stack);
        } catch (RuntimeException ignored) {
            // 可选第三方回调失败不能回滚已经成功写入世界的整批蓝图。
        }
    }

    private static void removeAll(NBTTagCompound tag, String... keys) {
        for (String key : keys) {
            tag.removeTag(key);
        }
    }

    private static boolean isLegacyBelt(BlockState state) {
        ResourceLocation id = registryName(state);
        if (id == null || !CREATE_NAMESPACE.equals(id.getResourceDomain())) {
            return false;
        }
        return BELT_STRAIGHT.equals(id.getResourcePath()) || BELT_DIAGONAL.equals(id.getResourcePath());
    }

    private static boolean isCreateBlock(BlockState state) {
        ResourceLocation id = registryName(state);
        return id != null && CREATE_NAMESPACE.equals(id.getResourceDomain());
    }

    @Nullable
    private static ResourceLocation registryName(BlockState state) {
        return state == null || state.getBlock() == null ? null
                : com.rtsbuilding.rtsbuilding.platform.registry.RtsRegistries.BLOCKS.getKey(state.getBlock());
    }
}
