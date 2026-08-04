package com.rtsbuilding.rtsbuilding.client.presentation.panel.interaction;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.CakeBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 目标条目能力探测器：判断框选/单点目标是否具有可打开的 GUI 交互。
 *
 * <p>判定规则：</p>
 * <ul>
 *   <li>实体：有职业的村民、马类、容器实体、任何 {@link MenuProvider}。</li>
 *   <li>方块：方块状态或方块实体提供 {@link MenuProvider}（讲台无书除外），
 *       或方块类覆写了 {@code use}/{@code useWithoutItem} 方法（结果按类反射缓存）。</li>
 * </ul>
 */
public final class TargetProbe {

    private TargetProbe() {
    }

    /** 判断条目是否具有可打开的 GUI 交互。 */
    public static boolean hasGuiInteraction(SelectableEntry entry) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return false;
        return switch (entry) {
            case EntityEntry ee -> hasEntityGui(ee.entity());
            case BlockEntry be -> hasBlockGui(mc, be.blockPos());
        };
    }

    /** 判断实体是否具有 GUI 交互（村民无职业视为无）。 */
    public static boolean hasEntityGui(@Nullable Entity entity) {
        if (entity == null || !entity.isAlive()) return false;

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

    /** 判断方块是否具有 GUI 交互。 */
    public static boolean hasBlockGui(Minecraft mc, BlockPos blockPos) {
        BlockState state = mc.level.getBlockState(blockPos);
        if (state.getMenuProvider(mc.level, blockPos) != null) return true;

        BlockEntity be = mc.level.getBlockEntity(blockPos);
        if (be instanceof MenuProvider) {
            if (be instanceof LecternBlockEntity lectern && lectern.getBook().isEmpty()) return false;
            return true;
        }

        return hasUseOverride(state.getBlock());
    }

    private static final Map<Class<?>, Boolean> USE_OVERRIDE_CACHE = new ConcurrentHashMap<>();

    /**
     * 反射探测方块类是否覆写了 use 方法（按类缓存，避免反复反射遍历类层级）。
     * 覆写了 {@code use} 或 {@code useWithoutItem} 的方块视为可交互（如门、按钮等）。
     */
    private static boolean hasUseOverride(Block block) {
        Class<?> clazz = block.getClass();
        if (clazz == Block.class) return false;
        // 排除“覆写 use 但不打开容器 GUI”的常见交互方块，避免误判为可交互目标
        //（这些方块在容器标签栏中无法被 RTS 打开，只会产生无效标签）
        if (block instanceof DoorBlock || block instanceof TrapDoorBlock
                || block instanceof FenceGateBlock || block instanceof ButtonBlock
                || block instanceof LeverBlock || block instanceof BedBlock
                || block instanceof CakeBlock || block instanceof BellBlock
                || block instanceof SignBlock || block instanceof FlowerPotBlock) {
            return false;
        }
        return USE_OVERRIDE_CACHE.computeIfAbsent(clazz, TargetProbe::scanUseOverride);
    }

    private static boolean scanUseOverride(Class<?> clazz) {
        Class<?> current = clazz;
        while (current != null && current != Block.class) {
            if (hasDeclaredMethod(current, "use",
                    BlockState.class, Level.class, BlockPos.class, Player.class,
                    InteractionHand.class, BlockHitResult.class)
                    || hasDeclaredMethod(current, "useWithoutItem",
                    BlockState.class, Level.class, BlockPos.class, Player.class,
                    BlockHitResult.class)) {
                return true;
            }
            current = current.getSuperclass();
        }
        return false;
    }

    private static boolean hasDeclaredMethod(Class<?> clazz, String name, Class<?>... params) {
        try {
            clazz.getDeclaredMethod(name, params);
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }
}
