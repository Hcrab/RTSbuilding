package com.rtsbuilding.rtsbuilding.server.progression;

import com.rtsbuilding.rtsbuilding.server.data.RtsSharedProgressionData;
import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager.HomeAnchor;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

final class RtsHomeManager {
    private static final ConcurrentMap<UUID, HomeSelection> HOME_SELECTIONS = new ConcurrentHashMap<>();

    private RtsHomeManager() {
    }

    static void beginHomeSelection(EntityPlayerMP player) {
        if (player == null) {
            return;
        }
        int chunkX = player.getPosition().getX() >> 4;
        int chunkZ = player.getPosition().getZ() >> 4;
        HOME_SELECTIONS.put(player.getUniqueID(), new HomeSelection(player.dimension, chunkX, chunkZ));
    }

    static void endHomeSelection(EntityPlayerMP player) {
        if (player != null) {
            HOME_SELECTIONS.remove(player.getUniqueID());
        }
    }

    static boolean isHomeSelectionActive(EntityPlayerMP player) {
        return player != null && HOME_SELECTIONS.containsKey(player.getUniqueID());
    }

    static boolean canSelectHome(EntityPlayerMP player, BlockPos pos) {
        HomeSelection selection = player == null ? null : HOME_SELECTIONS.get(player.getUniqueID());
        if (selection == null || pos == null || selection.dimension() != player.dimension) {
            return false;
        }
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;
        return Math.abs(chunkX - selection.centerChunkX()) <= 1
                && Math.abs(chunkZ - selection.centerChunkZ()) <= 1;
    }

    static HomeAnchor personalHome(EntityPlayerMP player) {
        if (player == null) {
            return null;
        }
        NBTTagCompound root = RtsProgressionPersistence.root(player);
        if (!root.hasKey(RtsProgressionPersistence.NBT_HOME_POS)
                || !root.hasKey(RtsProgressionPersistence.NBT_HOME_DIMENSION)) {
            return null;
        }
        Integer dimension = parseDimension(root.getString(RtsProgressionPersistence.NBT_HOME_DIMENSION));
        if (dimension == null) {
            return null;
        }
        return new HomeAnchor(
                BlockPos.fromLong(root.getLong(RtsProgressionPersistence.NBT_HOME_POS)).toImmutable(),
                dimension,
                root.getLong(RtsProgressionPersistence.NBT_HOME_SET_GAME_TIME));
    }

    static HomeAnchor getHome(EntityPlayerMP player) {
        if (player == null) {
            return null;
        }
        String sharedKey = RtsProgressionPersistence.sharedProgressionKey(player);
        if (!isBlank(sharedKey)) {
            RtsSharedProgressionData.SharedHome sharedHome =
                    RtsProgressionPersistence.sharedProgressionData(player).home(sharedKey);
            if (sharedHome != null) {
                return new HomeAnchor(sharedHome.pos(), sharedHome.dimension(), sharedHome.setGameTime());
            }
        }
        return personalHome(player);
    }

    static boolean hasHome(EntityPlayerMP player) {
        return getHome(player) != null;
    }

    /**
     * 检查玩家当前所在区块是否位于家园区块周围的 3x3 区块内。
     *
     * <p>这个限制只决定玩家能否开启一次普通 RTS 会话。会话开启后，
     * 世界操作范围由相机锚点和插件提供的操作半径决定，不再与家园位置取交集。</p>
     */
    static boolean canOpenRtsNearHome(EntityPlayerMP player) {
        if (!RtsProgressionManager.isEnabled() || RtsProgressionManager.canBypassHomeRadius(player)) {
            return true;
        }
        if (player == null) {
            return false;
        }
        HomeAnchor home = getHome(player);
        if (home == null || home.dimension() != player.dimension) {
            return false;
        }
        return isWithinHomeOpeningChunks(home.pos(), player.getPosition());
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

    static boolean canChangeHome(EntityPlayerMP player) {
        if (!RtsProgressionManager.isEnabled()) {
            return true;
        }
        HomeAnchor home = getHome(player);
        return home == null || remainingHomeCooldownTicks(player) <= 0L;
    }

    static long remainingHomeCooldownTicks(EntityPlayerMP player) {
        if (!RtsProgressionManager.isEnabled() || player == null) {
            return 0L;
        }
        HomeAnchor home = getHome(player);
        if (home == null) {
            return 0L;
        }
        long elapsed = Math.max(0L, player.getServerWorld().getTotalWorldTime() - home.setGameTime());
        return Math.max(0L, RtsProgressionManager.HOME_RELOCATION_COOLDOWN_TICKS - elapsed);
    }

    static long remainingHomeCooldownDays(EntityPlayerMP player) {
        long ticks = remainingHomeCooldownTicks(player);
        return ticks <= 0L ? 0L : (ticks + RtsProgressionManager.TICKS_PER_GAME_DAY - 1L) / RtsProgressionManager.TICKS_PER_GAME_DAY;
    }

    static boolean commitHome(EntityPlayerMP player, BlockPos pos) {
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
        if (isBlank(sharedKey)) {
            NBTTagCompound root = RtsProgressionPersistence.root(player);
            root.setInteger(RtsProgressionPersistence.NBT_VERSION, 1);
            root.setLong(RtsProgressionPersistence.NBT_HOME_POS, pos.toImmutable().toLong());
            root.setString(RtsProgressionPersistence.NBT_HOME_DIMENSION, dimensionName(player.dimension));
            root.setLong(RtsProgressionPersistence.NBT_HOME_SET_GAME_TIME, player.getServerWorld().getTotalWorldTime());
            RtsProgressionPersistence.save(player, root);
        } else {
            RtsProgressionPersistence.sharedProgressionData(player).setHome(
                    sharedKey, pos, player.dimension, player.getServerWorld().getTotalWorldTime());
        }
        endHomeSelection(player);
        return true;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static Integer parseDimension(String value) {
        if ("minecraft:overworld".equals(value)) return Integer.valueOf(0);
        if ("minecraft:the_nether".equals(value)) return Integer.valueOf(-1);
        if ("minecraft:the_end".equals(value)) return Integer.valueOf(1);
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String dimensionName(int dimension) {
        if (dimension == 0) return "minecraft:overworld";
        if (dimension == -1) return "minecraft:the_nether";
        if (dimension == 1) return "minecraft:the_end";
        return Integer.toString(dimension);
    }

    private static final class HomeSelection {
        private final int dimension;
        private final int centerChunkX;
        private final int centerChunkZ;

        private HomeSelection(int dimension, int centerChunkX, int centerChunkZ) {
            this.dimension = dimension;
            this.centerChunkX = centerChunkX;
            this.centerChunkZ = centerChunkZ;
        }

        int dimension() { return dimension; }
        int centerChunkX() { return centerChunkX; }
        int centerChunkZ() { return centerChunkZ; }
    }
}
