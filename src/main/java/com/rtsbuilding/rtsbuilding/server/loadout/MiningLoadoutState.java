package com.rtsbuilding.rtsbuilding.server.loadout;

import com.rtsbuilding.rtsbuilding.server.data.PlayerComponents;
import com.rtsbuilding.rtsbuilding.server.data.SaveScheduler;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.OptionalInt;

/**
 * 鐜╁鎸栨帢瑁呭鏍忕姸鎬佺殑鎸佷箙鍖栧瓨鍌ㄤ笌鏌ヨ宸ュ叿绫汇€?
 *
 * <p>鏁版嵁瀛樺偍浜?{@link com.rtsbuilding.rtsbuilding.server.data.DataCluster}
 * 鐨?{@link PlayerComponents#MINING_LOADOUT} 缁勪欢涓紝鐢?{@link SaveScheduler} 缁熶竴绠＄悊銆?
 */
public final class MiningLoadoutState {
    /** 鏈夋晥妲戒綅鏈€灏忓€硷紙蹇嵎鏍忕涓€鏍硷級 */
    private static final int MIN_SLOT = 0;
    /** 鏈夋晥妲戒綅鏈€澶у€硷紙鑳屽寘鏈€鍚庝竴鏍硷級 */
    private static final int MAX_SLOT = 35;

    /** 宸ュ叿绫伙紝绂佹瀹炰緥鍖?*/
    private MiningLoadoutState() {
    }

    /**
     * 鑾峰彇鎸囧畾瑙掕壊缁戝畾鐨勫伐鍏锋Ы浣嶃€?
     */
    public static OptionalInt getSlot(ServerPlayer player, MiningLoadoutRole role) {
        CompoundTag loadout = loadoutTag(player);
        if (loadout == null) return OptionalInt.empty();

        String key = roleKey(role);
        if (!loadout.contains(key)) return OptionalInt.empty();

        int slot = loadout.getInt(key);
        return slot >= MIN_SLOT && slot <= MAX_SLOT ? OptionalInt.of(slot) : OptionalInt.empty();
    }

    /**
     * 涓烘寚瀹氳鑹茬粦瀹氫竴涓伐鍏锋Ы浣嶏紝鍚屾椂璁板綍璇ユЫ浣嶇墿鍝佺殑鎸囩汗浠ユ娴嬪悗缁彉鍖栥€?
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
     * 娓呴櫎鎸囧畾瑙掕壊鐨勭粦瀹氫俊鎭紙妲戒綅鍜屾寚绾癸級銆?
     */
    public static void clearSlot(ServerPlayer player, MiningLoadoutRole role) {
        CompoundTag loadout = loadoutTag(player);
        if (loadout == null) return;
        loadout.remove(roleKey(role));
        loadout.remove(fingerprintKey(role));
        markDirty(player);
    }

    /**
     * 妫€鏌ユ寚瀹氳鑹茬粦瀹氱殑妲戒綅涓殑鐗╁搧鏄惁浠嶇劧涓庤褰曠殑鎸囩汗鍖归厤銆?
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
     * 鑾峰彇鎸囧畾瑙掕壊缁戝畾鐨勬Ы浣嶄腑鐨勭墿鍝佸爢銆?
     */
    public static ItemStack getAssignedStack(ServerPlayer player, MiningLoadoutRole role) {
        OptionalInt slot = getSlot(player, role);
        if (slot.isEmpty()) return ItemStack.EMPTY;
        return player.getInventory().getItem(slot.getAsInt());
    }

    // 鈹€鈹€ 鍐呴儴鏂规硶 鈹€鈹€

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

    /** 浠?DataCluster 鑾峰彇瑁呭鏍?NBT 鏍囩锛堟案涓嶈繑鍥?null锛?*/
    private static CompoundTag loadoutTag(ServerPlayer player) {
        return SaveScheduler.INSTANCE.player(player).get(PlayerComponents.MINING_LOADOUT);
    }

    /** 鏍囪瑁呭鏍忔暟鎹负鑴忥紝涓嬫 SaveScheduler 鍒风洏鏃跺啓鍏?*/
    private static void markDirty(ServerPlayer player) {
        SaveScheduler.INSTANCE.player(player).set(PlayerComponents.MINING_LOADOUT,
                SaveScheduler.INSTANCE.player(player).get(PlayerComponents.MINING_LOADOUT));
    }
}
