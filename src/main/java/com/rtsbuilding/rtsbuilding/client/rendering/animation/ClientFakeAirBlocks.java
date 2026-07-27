package com.rtsbuilding.rtsbuilding.client.rendering.animation;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 在服务端确认远程挖掘后，立即把客户端方块暂时显示为空气。
 *
 * <p>服务端可能先回收掉落物，稍后才把世界更新送到客户端。确认结果为空气时，本地空气一直等待
 * 服务端世界包接管；确认结果为非空气时（例如方块被模组替换），仅在缩小动画期间保持假空气，
 * 超时后恢复服务端确认的结果。切换世界会清空全部临时状态，避免坐标跨世界污染。</p>
 */
public final class ClientFakeAirBlocks {
    static final long NON_AIR_SETTLE_TIMEOUT_MS = 750L;
    private static final int CLIENT_BLOCK_UPDATE_FLAGS = 3;
    private static final Map<Long, FakeAirEntry> ENTRIES = new LinkedHashMap<Long, FakeAirEntry>();
    private static WorldClient activeWorld;

    private ClientFakeAirBlocks() {
    }

    public static void hideUntilServerState(BlockPos pos, IBlockState originalState, IBlockState resultState) {
        Minecraft minecraft = Minecraft.getMinecraft();
        WorldClient world = minecraft.world;
        if (world == null || pos == null) return;
        syncWorld(world);
        if (!world.isBlockLoaded(pos)) return;

        IBlockState animationState = isAir(originalState, world, pos)
                ? world.getBlockState(pos) : originalState;
        IBlockState confirmedState = resultState == null ? Blocks.AIR.getDefaultState() : resultState;
        if (isAir(animationState, world, pos)
                && isAir(confirmedState, world, pos)
                && isAir(world.getBlockState(pos), world, pos)) return;

        BlockPos immutablePos = pos.toImmutable();
        long key = immutablePos.toLong();
        if (isAir(confirmedState, world, immutablePos)) {
            ENTRIES.remove(key);
        } else {
            ENTRIES.put(key, new FakeAirEntry(immutablePos, confirmedState,
                    System.currentTimeMillis() + NON_AIR_SETTLE_TIMEOUT_MS));
        }
        world.setBlockState(immutablePos, Blocks.AIR.getDefaultState(), CLIENT_BLOCK_UPDATE_FLAGS);
    }

    public static void tick() {
        Minecraft minecraft = Minecraft.getMinecraft();
        WorldClient world = minecraft.world;
        if (world == null) {
            ENTRIES.clear();
            activeWorld = null;
            return;
        }
        syncWorld(world);
        settleEntries(world, System.currentTimeMillis());
    }

    static void settleEntries(WorldClient world, long now) {
        if (ENTRIES.isEmpty()) return;
        Iterator<Map.Entry<Long, FakeAirEntry>> iterator = ENTRIES.entrySet().iterator();
        while (iterator.hasNext()) {
            FakeAirEntry entry = iterator.next().getValue();
            if (entry == null) {
                iterator.remove();
                continue;
            }
            IBlockState current = world.getBlockState(entry.pos);
            if (current.equals(entry.confirmedState)) {
                iterator.remove();
                continue;
            }
            if (now < entry.settleAtMs) continue;
            iterator.remove();
            if (world.isBlockLoaded(entry.pos) && isAir(world.getBlockState(entry.pos), world, entry.pos)) {
                world.setBlockState(entry.pos, entry.confirmedState, CLIENT_BLOCK_UPDATE_FLAGS);
            }
        }
    }

    private static boolean isAir(IBlockState state, WorldClient world, BlockPos pos) {
        if (state == null) return true;
        Block block = state.getBlock();
        return block == Blocks.AIR || block.isAir(state, world, pos);
    }

    private static void syncWorld(WorldClient world) {
        if (activeWorld == world) return;
        ENTRIES.clear();
        activeWorld = world;
    }

    private static final class FakeAirEntry {
        private final BlockPos pos;
        private final IBlockState confirmedState;
        private final long settleAtMs;

        private FakeAirEntry(BlockPos pos, IBlockState confirmedState, long settleAtMs) {
            this.pos = pos;
            this.confirmedState = confirmedState;
            this.settleAtMs = settleAtMs;
        }
    }
}
