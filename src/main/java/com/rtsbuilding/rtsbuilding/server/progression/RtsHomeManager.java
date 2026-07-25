package com.rtsbuilding.rtsbuilding.server.progression;

import com.rtsbuilding.rtsbuilding.server.data.RtsSharedProgressionData;
import com.rtsbuilding.rtsbuilding.server.data.RtsDimensionKeys;
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

final class RtsHomeManager {
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
        ResourceKey<Level> dimension = RtsDimensionKeys.create(dimensionId);
        return new HomeAnchor(
                BlockPos.of(root.getLong(RtsProgressionPersistence.NBT_HOME_POS)).immutable(),
                dimension,
                root.getLong(RtsProgressionPersistence.NBT_HOME_SET_GAME_TIME));
    }

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

    static boolean hasHome(ServerPlayer player) {
        return getHome(player) != null;
    }

    /**
     * 妫€鏌ョ帺瀹跺綋鍓嶆墍鍦ㄥ尯鍧楁槸鍚︿綅浜庡鍥尯鍧楀懆鍥寸殑 3x3 鍖哄潡鍐呫€?     *
     * <p>杩欎釜闄愬埗鍙喅瀹氱帺瀹惰兘鍚﹀紑鍚竴娆℃櫘閫?RTS 浼氳瘽銆備細璇濆紑鍚悗锛?     * 涓栫晫鎿嶄綔鑼冨洿鐢辩浉鏈洪敋鐐瑰拰鎻掍欢鎻愪緵鐨勬搷浣滃崐寰勫喅瀹氾紝涓嶅啀涓庡鍥綅缃彇浜ら泦銆?/p>
     */
    static boolean canOpenRtsNearHome(ServerPlayer player) {
        if (!RtsProgressionManager.isEnabled() || RtsProgressionManager.canBypassHomeRadius(player)) {
            return true;
        }
        if (player == null) {
            return false;
        }
        HomeAnchor home = getHome(player);
        if (home == null || !home.dimension().equals(player.serverLevel().dimension())) {
            return false;
        }
        return isWithinHomeOpeningChunks(home.pos(), player.blockPosition());
    }

    static boolean isWithinHomeOpeningChunks(BlockPos homePos, BlockPos playerPos) {
        if (homePos == null || playerPos == null) {
            return false;
        }
        int homeChunkX = homePos.getX() >> 4;
        int homeChunkZ = homePos.getZ() >> 4;
        int playerChunkX = playerPos.getX() >> 4;
        int playerChunkZ = playerPos.getZ() >> 4;
        return Math.abs(playerChunkX - homeChunkX) <= 1
                && Math.abs(playerChunkZ - homeChunkZ) <= 1;
    }

    static boolean canChangeHome(ServerPlayer player) {
        if (!RtsProgressionManager.isEnabled()) {
            return true;
        }
        HomeAnchor home = getHome(player);
        return home == null || remainingHomeCooldownTicks(player) <= 0L;
    }

    static long remainingHomeCooldownTicks(ServerPlayer player) {
        if (!RtsProgressionManager.isEnabled() || player == null) {
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
            RtsProgressionPersistence.save(player, root);
        } else {
            RtsProgressionPersistence.sharedProgressionData(player).setHome(
                    sharedKey, pos, player.serverLevel().dimension(), player.serverLevel().getGameTime());
        }
        endHomeSelection(player);
        return true;
    }

    private record HomeSelection(ResourceKey<Level> dimension, int centerChunkX, int centerChunkZ) {
    }
}
