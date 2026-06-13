package com.rtsbuilding.rtsbuilding.server.loadout;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.OptionalInt;

public final class MiningLoadoutState {
    private static final String ROOT_KEY = "rtsbuilding";
    private static final String LOADOUT_KEY = "mining_loadout";

    private static final int MIN_SLOT = 0;
    private static final int MAX_SLOT = 35;

    private MiningLoadoutState() {
    }

    public static OptionalInt getSlot(ServerPlayer player, MiningLoadoutRole role) {
        CompoundTag loadout = getLoadoutTag(player, false);
        if (loadout == null) {
            return OptionalInt.empty();
        }

        String key = roleKey(role);
        if (!loadout.contains(key)) {
            return OptionalInt.empty();
        }

        int slot = loadout.getInt(key);
        if (slot < MIN_SLOT || slot > MAX_SLOT) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(slot);
    }

    public static boolean setSlot(ServerPlayer player, MiningLoadoutRole role, int slot) {
        if (slot < MIN_SLOT || slot > MAX_SLOT) {
            return false;
        }

        CompoundTag loadout = getLoadoutTag(player, true);
        String key = roleKey(role);
        loadout.putInt(key, slot);
        loadout.putString(fingerprintKey(role), stackFingerprint(player.getInventory().getItem(slot)));
        return true;
    }

    public static void clearSlot(ServerPlayer player, MiningLoadoutRole role) {
        CompoundTag loadout = getLoadoutTag(player, false);
        if (loadout == null) {
            return;
        }
        loadout.remove(roleKey(role));
        loadout.remove(fingerprintKey(role));
    }

    public static boolean isStillMatching(ServerPlayer player, MiningLoadoutRole role) {
        OptionalInt slotOpt = getSlot(player, role);
        if (slotOpt.isEmpty()) {
            return false;
        }

        CompoundTag loadout = getLoadoutTag(player, false);
        if (loadout == null || !loadout.contains(fingerprintKey(role))) {
            return false;
        }

        String expected = loadout.getString(fingerprintKey(role));
        String current = stackFingerprint(player.getInventory().getItem(slotOpt.getAsInt()));
        return expected.equals(current);
    }

    public static ItemStack getAssignedStack(ServerPlayer player, MiningLoadoutRole role) {
        OptionalInt slot = getSlot(player, role);
        if (slot.isEmpty()) {
            return ItemStack.EMPTY;
        }
        return player.getInventory().getItem(slot.getAsInt());
    }

    private static String stackFingerprint(ItemStack stack) {
        if (stack.isEmpty()) {
            return "";
        }
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        return id + ":" + stack.getDamageValue();
    }

    private static String roleKey(MiningLoadoutRole role) {
        return role.name().toLowerCase();
    }

    private static String fingerprintKey(MiningLoadoutRole role) {
        return roleKey(role) + "_fp";
    }

    private static CompoundTag getLoadoutTag(ServerPlayer player, boolean create) {
        CompoundTag persistent = player.getPersistentData();
        CompoundTag root;
        if (persistent.contains(ROOT_KEY)) {
            root = persistent.getCompound(ROOT_KEY);
        } else if (create) {
            root = new CompoundTag();
            persistent.put(ROOT_KEY, root);
        } else {
            return null;
        }

        if (root.contains(LOADOUT_KEY)) {
            return root.getCompound(LOADOUT_KEY);
        }
        if (!create) {
            return null;
        }

        CompoundTag loadout = new CompoundTag();
        root.put(LOADOUT_KEY, loadout);
        return loadout;
    }
}

