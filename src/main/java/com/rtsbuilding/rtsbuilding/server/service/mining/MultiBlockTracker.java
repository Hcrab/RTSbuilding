package com.rtsbuilding.rtsbuilding.server.service.mining;

import com.rtsbuilding.rtsbuilding.server.history.HistoryBlockRecord;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import com.rtsbuilding.rtsbuilding.platform.block.BlockState;
import net.minecraft.init.Blocks;
import com.rtsbuilding.rtsbuilding.platform.math.EnumFacing;
import com.rtsbuilding.rtsbuilding.platform.math.BlockPos;
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
            BlockState state = BlockState.fromWorld(world, neighbor);
            if (state.getBlock() != Blocks.air) {
                records.add(new HistoryBlockRecord(neighbor.toImmutable(), state));
            }
        }
        return records;
    }

    public static void recordCollateralBlocks(WorldServer world, RtsStorageSession session,
            List<HistoryBlockRecord> records, BlockPos brokenPos) {
        for (HistoryBlockRecord record : records) {
            if (record.pos().equals(brokenPos)) continue;
            BlockState current = BlockState.fromWorld(world, record.pos());
            if (current.getBlock() == Blocks.air && record.state().getBlock() != Blocks.air) {
                session.mining.ultimineProcessedPositions.add(record);
            }
        }
    }
}
