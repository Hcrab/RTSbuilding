package com.rtsbuilding.rtsbuilding.server.loadout;

import com.rtsbuilding.rtsbuilding.server.data.PlayerComponents;
import com.rtsbuilding.rtsbuilding.server.data.SaveScheduler;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.OptionalInt;

/**
 * 玩家挖掘装备栏状态的持久化存储与查询工具类。
 *
 * <p>数据存储于 {@link com.rtsbuilding.rtsbuilding.server.data.DataCluster}
 * 的 {@link PlayerComponents#MINING_LOADOUT} 组件中，由 {@link SaveScheduler} 统一管理。
 */
public final class MiningLoadoutState {
    /** 有效槽位最小值（快捷栏第一格） */
    private static final int MIN_SLOT = 0;
    /** 有效槽位最大值（背包最后一格） */
    private static final int MAX_SLOT = 35;

    /** 工具类，禁止实例化 */
    private MiningLoadoutState() {
    }

    /**
     * 获取指定角色绑定的工具槽位。
     */
    public static OptionalInt getSlot(ServerPlayer player, MiningLoadoutRole role) {
        CompoundTag loadout = loadoutTag(player);
        if (loadout == null) return OptionalInt.empty();

        String key = roleKey(role);
        if (!loadout.contains(key)) return OptionalInt.empty();

        int slot = loadout.getIntOr(key, 0);
        return slot >= MIN_SLOT && slot <= MAX_SLOT ? OptionalInt.of(slot) : OptionalInt.empty();
    }

    /**
     * 为指定角色绑定一个工具槽位，同时记录该槽位物品的指纹以检测后续变化。
     */
    public static boolean setSlot(ServerPlayer player, MiningLoadoutRole role, int slot) {
        if (slot < MIN_SLOT || slot > MAX_SLOT) return false;

        CompoundTag loadout = loadoutTag(player);
        String key = roleKey(role);
        loadout.putInt(key, slot);
        loadout.putString(fingerprintKey(role), stackFingerprint(player.getInventory().getItem(slot)));
        markDirty(player);
        return true;
    }

    /**
     * 清除指定角色的绑定信息（槽位和指纹）。
     */
    public static void clearSlot(ServerPlayer player, MiningLoadoutRole role) {
        CompoundTag loadout = loadoutTag(player);
        if (loadout == null) return;
        loadout.remove(roleKey(role));
        loadout.remove(fingerprintKey(role));
        markDirty(player);
    }

    /**
     * 检查指定角色绑定的槽位中的物品是否仍然与记录的指纹匹配。
     */
    public static boolean isStillMatching(ServerPlayer player, MiningLoadoutRole role) {
        OptionalInt slotOpt = getSlot(player, role);
        if (slotOpt.isEmpty()) return false;

        CompoundTag loadout = loadoutTag(player);
        if (loadout == null || !loadout.contains(fingerprintKey(role))) return false;

        String expected = loadout.getString(fingerprintKey(role));
        String current = stackFingerprint(player.getInventory().getItem(slotOpt.getAsInt()));
        return expected.equals(current);
    }

    /**
     * 获取指定角色绑定的槽位中的物品堆。
     */
    public static ItemStack getAssignedStack(ServerPlayer player, MiningLoadoutRole role) {
        OptionalInt slot = getSlot(player, role);
        if (slot.isEmpty()) return ItemStack.EMPTY;
        return player.getInventory().getItem(slot.getAsInt());
    }

    // ── 内部方法 ──

    private static String stackFingerprint(ItemStack stack) {
        if (stack.isEmpty()) return "";
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString() + ":" + stack.getDamageValue();
    }

    private static String roleKey(MiningLoadoutRole role) {
        return role.name().toLowerCase();
    }

    private static String fingerprintKey(MiningLoadoutRole role) {
        return roleKey(role) + "_fp";
    }

    /** 从 DataCluster 获取装备栏 NBT 标签（永不返回 null） */
    private static CompoundTag loadoutTag(ServerPlayer player) {
        return SaveScheduler.INSTANCE.player(player).get(PlayerComponents.MINING_LOADOUT);
    }

    /** 标记装备栏数据为脏，下次 SaveScheduler 刷盘时写入 */
    private static void markDirty(ServerPlayer player) {
        SaveScheduler.INSTANCE.player(player).set(PlayerComponents.MINING_LOADOUT,
                SaveScheduler.INSTANCE.player(player).get(PlayerComponents.MINING_LOADOUT));
    }
}

