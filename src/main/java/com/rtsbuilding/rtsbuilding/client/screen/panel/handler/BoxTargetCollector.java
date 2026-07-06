package com.rtsbuilding.rtsbuilding.client.screen.panel.handler;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.vehicle.ContainerEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 框选目标收集器——扫描框选区域内可交互的实体和方块。
 *
 * <p>封装了区域扫描、实体 GUI 能力检测、方块右键能力检测等逻辑，
 * 供 {@link EntityInteractionHandler} 在框选模式下使用。</p>
 */
public final class BoxTargetCollector {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * 缓存：Block 类 → use/useWithoutItem 方法是否被覆写。
     * 某些模组（如 Mekanism）的方块不在 Block.getMenuProvider() 中暴露 GUI，
     * 而是直接在 Block.use() 中打开。
     */
    private static final Map<Class<?>, Boolean> USE_OVERRIDE_CACHE = new ConcurrentHashMap<>();

    /** 框选内可交互方块信息 */
    public record BlockInfo(BlockPos blockPos, BlockHitResult blockHit, String displayName, Vec3 hitLocation) {}

    // ======================== 实体收集 ========================

    /**
     * 收集框选区域内所有有 GUI 交互能力的实体。
     *
     * @param level        当前世界
     * @param sel          框选器（含选区边界）
     * @param cameraEntity 当前相机实体（用于排除自身）
     * @return 可交互实体列表
     */
    public List<Entity> collectEntities(Level level, BoxSelectorCache sel, Entity cameraEntity) {
        AABB selectionBox = sel.toAABB();
        if (selectionBox == null) return List.of();

        List<Entity> all = level.getEntities((Entity) null, selectionBox,
                e -> e != null && e.isAlive() && e.isPickable() && e != cameraEntity
                        && hasGuiInteraction(e));

        List<Entity> result = new ArrayList<>();
        for (Entity entity : all) {
            if (entity.getBoundingBox().intersects(selectionBox)) {
                result.add(entity);
            }
        }
        return result;
    }

    // ======================== 方块收集 ========================

    /**
     * 收集框选区域内所有有 GUI 交互的方块（实现了 {@link MenuProvider} 的方块实体）。
     *
     * @param level 当前世界
     * @param sel   框选器
     * @return GUI 方块信息列表
     */
    public List<BlockInfo> collectGuiBlocks(Level level, BoxSelectorCache sel) {
        BlockPos min = sel.minCorner();
        BlockPos max = sel.maxCorner();
        if (min == null || max == null) return List.of();

        List<BlockInfo> result = new ArrayList<>();
        for (int x = min.getX(); x < max.getX(); x++) {
            for (int y = min.getY(); y < max.getY(); y++) {
                for (int z = min.getZ(); z < max.getZ(); z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (isMekanismBoundingBlock(level, pos)) continue;
                    MenuProvider provider = resolveMenuProvider(level, pos);
                    if (provider != null) {
                        Vec3 center = Vec3.atCenterOf(pos);
                        result.add(new BlockInfo(
                                pos.immutable(),
                                new BlockHitResult(center, Direction.UP, pos.immutable(), false),
                                provider.getDisplayName().getString(),
                                center));
                    }
                }
            }
        }
        return result;
    }

    /**
     * 收集框选区域内无 GUI 但有右键交互的方块。
     * <p>这类方块没有 GUI 界面（如拉杆、按钮等），框选时直接批量交互。</p>
     *
     * @param level 当前世界
     * @param sel   框选器
     * @return 非 GUI 交互方块信息列表
     */
    public List<BlockInfo> collectNonGuiBlocks(Level level, BoxSelectorCache sel) {
        BlockPos min = sel.minCorner();
        BlockPos max = sel.maxCorner();
        if (min == null || max == null) return List.of();

        List<BlockInfo> result = new ArrayList<>();
        for (int x = min.getX(); x < max.getX(); x++) {
            for (int y = min.getY(); y < max.getY(); y++) {
                for (int z = min.getZ(); z < max.getZ(); z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (isMekanismBoundingBlock(level, pos)) continue;
                    BlockState state = level.getBlockState(pos);
                    if (isNonPrimaryMultiBlockPart(state)) continue;
                    if (hasMenuProvider(level, pos)) continue;
                    if (hasUseOverride(state.getBlock())) {
                        Vec3 center = Vec3.atCenterOf(pos);
                        result.add(new BlockInfo(
                                pos.immutable(),
                                new BlockHitResult(center, Direction.UP, pos.immutable(), false),
                                state.getBlock().getName().getString(),
                                center));
                    }
                }
            }
        }
        return result;
    }

    // ======================== 实体能力检测 ========================

    /** 判断实体是否具有 GUI 交互能力（容器/交易/物品栏屏幕）。 */
    private static boolean hasGuiInteraction(Entity entity) {
        if (entity instanceof AbstractVillager) {
            if (entity instanceof Villager villager) {
                return villager.getVillagerData().getProfession() != VillagerProfession.NONE;
            }
            return true;
        }
        if (entity instanceof AbstractHorse) return true;
        if (entity instanceof ContainerEntity) return true;
        return entity instanceof MenuProvider;
    }

    // ======================== 方块能力检测 ========================

    /**
     * 通过反射检测 Block 的 use()/useWithoutItem() 方法是否被覆写。
     * 结果缓存在 {@link #USE_OVERRIDE_CACHE} 中。
     */
    public static boolean hasUseOverride(Block block) {
        Class<?> clazz = block.getClass();
        if (clazz == Block.class) return false;
        return USE_OVERRIDE_CACHE.computeIfAbsent(clazz, c -> {
            Class<?> current = c;
            while (current != Block.class && current != null) {
                try {
                    current.getDeclaredMethod("use", BlockState.class, Level.class, BlockPos.class, Player.class, InteractionHand.class, BlockHitResult.class);
                    return true;
                } catch (NoSuchMethodException ignored) {}
                try {
                    current.getDeclaredMethod("useWithoutItem", BlockState.class, Level.class, BlockPos.class, Player.class, BlockHitResult.class);
                    return true;
                } catch (NoSuchMethodException ignored) {}
                current = current.getSuperclass();
            }
            return false;
        });
    }

    /** 判断方块是否为 Mekanism 的绑定方块（Bounding Block）。 */
    private static boolean isMekanismBoundingBlock(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if ("mekanism.common.block.BlockBounding".equals(state.getBlock().getClass().getName())) {
            return true;
        }
        BlockEntity be = level.getBlockEntity(pos);
        return be != null && "mekanism.common.tile.TileEntityBoundingBlock".equals(be.getClass().getName());
    }

    /** 判断方块是否是多方块结构中的"附属方块"。 */
    private static boolean isNonPrimaryMultiBlockPart(BlockState state) {
        if (state.hasProperty(BlockStateProperties.BED_PART)
                && state.getValue(BlockStateProperties.BED_PART) == BedPart.FOOT) {
            return true;
        }
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
            return true;
        }
        for (Property<?> prop : state.getProperties()) {
            if (!(prop instanceof EnumProperty<?> enumProp)) continue;
            String name = prop.getName();
            if (!name.contains("half") && !name.contains("part") && !name.contains("piece")
                    && !name.contains("section") && !name.contains("type")) {
                continue;
            }
            Object value = state.getValue(enumProp);
            String valueStr = value.toString().toLowerCase(Locale.ROOT);
            if (valueStr.contains("upper") || valueStr.contains("top")
                    || valueStr.contains("foot") || valueStr.contains("secondary")
                    || valueStr.contains("right")) {
                return true;
            }
        }
        return false;
    }

    // ======================== 辅助方法 ========================

    /** 解析方块位置的 MenuProvider（同时检查 BlockState 和 BlockEntity）。 */
    private static MenuProvider resolveMenuProvider(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        MenuProvider provider = state.getMenuProvider(level, pos);
        if (provider == null) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof MenuProvider mp) {
                if (be instanceof LecternBlockEntity lectern && lectern.getBook().isEmpty()) {
                    LOGGER.info("[SelectBlock] pos={} Lectern with no book, excluded", pos);
                    return null;
                }
                LOGGER.info("[SelectBlock] pos={} BE→MenuProvider: {}", pos, be.getClass().getSimpleName());
                return mp;
            }
        }
        return provider;
    }

    /** 判断方块位置是否有 MenuProvider（含 BlockEntity 兜底检查）。 */
    private static boolean hasMenuProvider(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getMenuProvider(level, pos) != null) return true;
        BlockEntity be = level.getBlockEntity(pos);
        return be instanceof MenuProvider;
    }

    /** 简化框选器边界访问的缓存对象。 */
    public record BoxSelectorCache(BlockPos minCorner, BlockPos maxCorner) {
        public AABB toAABB() {
            if (minCorner == null || maxCorner == null) return null;
            return new AABB(minCorner.getX(), minCorner.getY(), minCorner.getZ(),
                    maxCorner.getX(), maxCorner.getY(), maxCorner.getZ());
        }
    }
}
