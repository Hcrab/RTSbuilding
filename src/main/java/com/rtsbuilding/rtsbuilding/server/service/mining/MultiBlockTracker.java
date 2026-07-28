package com.rtsbuilding.rtsbuilding.server.service.mining;

import com.rtsbuilding.rtsbuilding.server.history.HistoryBlockRecord;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;

import java.util.ArrayList;
import java.util.List;

/** 多方块结构的邻居快照；只记录真实状态，不参与破坏或掉落。 */
public final class MultiBlockTracker {
    private MultiBlockTracker() { }

    public static List<HistoryBlockRecord> captureNeighborRecords(WorldServer world, BlockPos pos) {
        List<HistoryBlockRecord> records = new ArrayList<HistoryBlockRecord>(6);
        for (EnumFacing direction : EnumFacing.values()) {
            BlockPos neighbor = pos.offset(direction);
            IBlockState state = world.getBlockState(neighbor);
            if (state.getBlock() != Blocks.AIR) {
                records.add(new HistoryBlockRecord(neighbor.toImmutable(), state));
            }
        }
        return records;
    }

    public static void recordCollateralBlocks(WorldServer world, RtsStorageSession session,
            List<HistoryBlockRecord> records, BlockPos brokenPos) {
        for (HistoryBlockRecord record : records) {
            if (record.pos().equals(brokenPos)) continue;
            IBlockState current = world.getBlockState(record.pos());
            if (current.getBlock() == Blocks.AIR && record.state().getBlock() != Blocks.AIR) {
                session.mining.ultimineProcessedPositions.add(record);
            }
        }
    }
}
