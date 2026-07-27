package com.rtsbuilding.rtsbuilding.client.presentation.panel.handler;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.ContainerEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class BoxTargetCollector {

    private static final Logger LOGGER = LogUtils.getLogger();

    
    private static final Map<Class<?>, Boolean> USE_OVERRIDE_CACHE = new ConcurrentHashMap<>();

    
    public record BlockInfo(BlockPos blockPos, BlockHitResult blockHit, String displayName, Vec3 hitLocation) {}

    

    
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

    
    private static boolean isMekanismBoundingBlock(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if ("mekanism.common.block.BlockBounding".equals(state.getBlock().getClass().getName())) {
            return true;
        }
        BlockEntity be = level.getBlockEntity(pos);
        return be != null && "mekanism.common.tile.TileEntityBoundingBlock".equals(be.getClass().getName());
    }

    
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

    
    private static boolean hasMenuProvider(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getMenuProvider(level, pos) != null) return true;
        BlockEntity be = level.getBlockEntity(pos);
        return be instanceof MenuProvider;
    }

    
    public record BoxSelectorCache(BlockPos minCorner, BlockPos maxCorner) {
        public AABB toAABB() {
            if (minCorner == null || maxCorner == null) return null;
            return new AABB(minCorner.getX(), minCorner.getY(), minCorner.getZ(),
                    maxCorner.getX(), maxCorner.getY(), maxCorner.getZ());
        }
    }
}
