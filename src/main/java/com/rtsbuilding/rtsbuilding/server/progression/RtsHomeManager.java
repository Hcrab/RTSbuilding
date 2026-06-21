package com.rtsbuilding.rtsbuilding.server.progression;

import com.rtsbuilding.rtsbuilding.progression.RtsProgressionNodes;
import com.rtsbuilding.rtsbuilding.server.data.RtsSharedProgressionData;
import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager.HomeAnchor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 家的管理与家选择状态管???
 * <p>包私有——仅??{@link RtsProgressionManager} 内部委托??
 */
final class RtsHomeManager {

    // ======================================================================
    //  家选择???
    // ======================================================================

    private static final ConcurrentMap<UUID, HomeSelection> HOME_SELECTIONS = new ConcurrentHashMap<>();

    private RtsHomeManager() {
    }

    static void beginHomeSelection(ServerPlayer player) {
        if (player == null) {
            return;
        }
        int chunkX = player.blockPosition().getX() >> 4;
        int chunkZ = player.blockPosition().getZ() >> 4;
        HOME_SELECTIONS.put(player.getUUID(), new HomeSelection(player.serverLevel().dimension(), chunkX, chunkZ));
    }

    static void endHomeSelection(ServerPlayer player) {
        if (player != null) {
            HOME_SELECTIONS.remove(player.getUUID());
        }
    }

    static boolean isHomeSelectionActive(ServerPlayer player) {
        return player != null && HOME_SELECTIONS.containsKey(player.getUUID());
    }

    static boolean canSelectHome(ServerPlayer player, BlockPos pos) {
        HomeSelection selection = player == null ? null : HOME_SELECTIONS.get(player.getUUID());
        if (selection == null || pos == null || !selection.dimension().equals(player.serverLevel().dimension())) {
            return false;
        }
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;
        return Math.abs(chunkX - selection.centerChunkX()) <= 1
                && Math.abs(chunkZ - selection.centerChunkZ()) <= 1;
    }

    // ======================================================================
    //  家位置读??
    // ======================================================================

    /**
     * 获取玩家已保存的个人???
     */
    static HomeAnchor personalHome(ServerPlayer player) {
        if (player == null) {
            return null;
        }
        CompoundTag root = RtsProgressionPersistence.root(player);
        if (!root.contains(RtsProgressionPersistence.NBT_HOME_POS)
                || !root.contains(RtsProgressionPersistence.NBT_HOME_DIMENSION)) {
            return null;
        }
        ResourceLocation dimensionId = ResourceLocation.tryParse(
                root.getString(RtsProgressionPersistence.NBT_HOME_DIMENSION));
        if (dimensionId == null) {
            return null;
        }
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, dimensionId);
        return new HomeAnchor(
                BlockPos.of(root.getLong(RtsProgressionPersistence.NBT_HOME_POS)).immutable(),
                dimension,
                root.getLong(RtsProgressionPersistence.NBT_HOME_SET_GAME_TIME));
    }

    /**
     * 获取玩家的家（共??> 个人???
     */
    static HomeAnchor getHome(ServerPlayer player) {
        if (player == null) {
            return null;
        }
        String sharedKey = RtsProgressionPersistence.sharedProgressionKey(player);
        if (!sharedKey.isBlank()) {
            RtsSharedProgressionData.SharedHome sharedHome =
                    RtsProgressionPersistence.sharedProgressionData(player).home(sharedKey);
            if (sharedHome != null) {
                return new HomeAnchor(sharedHome.pos(), sharedHome.dimension(), sharedHome.setGameTime());
            }
        }
        return personalHome(player);
    }

    // ======================================================================
    //  家访问控??
    // ======================================================================

    static boolean hasHome(ServerPlayer player) {
        return getHome(player) != null;
    }

    static boolean canAccessHomeRadius(ServerPlayer player, BlockPos pos) {
        if (!RtsProgressionManager.isEnabled() || RtsProgressionManager.canBypassHomeRadius(player)) {
            return true;
        }
        if (player == null || pos == null) {
            return false;
        }
        HomeAnchor home = getHome(player);
        if (home == null || !home.dimension().equals(player.serverLevel().dimension())) {
            return false;
        }
        double radius = RtsProgressionManager.getActionRadius(player);
        double dx = (pos.getX() + 0.5D) - (home.pos().getX() + 0.5D);
        double dz = (pos.getZ() + 0.5D) - (home.pos().getZ() + 0.5D);
        double halfExtent = radius;
        return Math.abs(dx) <= halfExtent && Math.abs(dz) <= halfExtent;
    }

    // ======================================================================
    //  家变更控??
    // ======================================================================

    static boolean canChangeHome(ServerPlayer player) {
        if (!RtsProgressionManager.isEnabled()) {
            return true;
        }
        HomeAnchor home = getHome(player);
        if (home == null) {
            return true;
        }
        return RtsProgressionPersistence.unlockedNodes(player).contains(RtsProgressionNodes.FIELD_DEPLOYMENT)
                || remainingHomeCooldownTicks(player) <= 0L;
    }

    static long remainingHomeCooldownTicks(ServerPlayer player) {
        if (!RtsProgressionManager.isEnabled() || player == null) {
            return 0L;
        }
        if (RtsProgressionPersistence.unlockedNodes(player).contains(RtsProgressionNodes.FIELD_DEPLOYMENT)) {
            return 0L;
        }
        HomeAnchor home = getHome(player);
        if (home == null) {
            return 0L;
        }
        long elapsed = Math.max(0L, player.serverLevel().getGameTime() - home.setGameTime());
        return Math.max(0L, RtsProgressionManager.HOME_RELOCATION_COOLDOWN_TICKS - elapsed);
    }

    static long remainingHomeCooldownDays(ServerPlayer player) {
        long ticks = remainingHomeCooldownTicks(player);
        return ticks <= 0L ? 0L : (ticks + RtsProgressionManager.TICKS_PER_GAME_DAY - 1L) / RtsProgressionManager.TICKS_PER_GAME_DAY;
    }

    // ======================================================================
    //  家提??
    // ======================================================================

    static boolean commitHome(ServerPlayer player, BlockPos pos) {
        if (!RtsProgressionManager.isEnabled()) {
            return false;
        }
        if (player == null || pos == null || !canSelectHome(player, pos)) {
            return false;
        }
        if (hasHome(player) && !canChangeHome(player)) {
            return false;
        }
        String sharedKey = RtsProgressionPersistence.sharedProgressionKey(player);
        if (sharedKey.isBlank()) {
            CompoundTag root = RtsProgressionPersistence.root(player);
            root.putInt(RtsProgressionPersistence.NBT_VERSION, 1);
            root.putLong(RtsProgressionPersistence.NBT_HOME_POS, pos.immutable().asLong());
            root.putString(RtsProgressionPersistence.NBT_HOME_DIMENSION,
                    player.serverLevel().dimension().location().toString());
            root.putLong(RtsProgressionPersistence.NBT_HOME_SET_GAME_TIME, player.serverLevel().getGameTime());
            player.getPersistentData().put(RtsProgressionPersistence.NBT_ROOT, root);
        } else {
            RtsProgressionPersistence.sharedProgressionData(player).setHome(
                    sharedKey, pos, player.serverLevel().dimension(), player.serverLevel().getGameTime());
        }
        endHomeSelection(player);
        return true;
    }

    // ======================================================================
    //  内部记录
    // ======================================================================

    private record HomeSelection(ResourceKey<Level> dimension, int centerChunkX, int centerChunkZ) {
    }
}
