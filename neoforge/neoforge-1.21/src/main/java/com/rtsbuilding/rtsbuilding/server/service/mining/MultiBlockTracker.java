package com.rtsbuilding.rtsbuilding.server.service.mining;

import com.rtsbuilding.rtsbuilding.server.history.HistoryBlockRecord;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * 多方块结构追踪工具集。
 *
 * <p>从 {@link RtsMiningStateMachine} 和 {@link RtsUltimineProcessor} 中提取两者重复的
 * 邻方块捕获和附属破坏记录逻辑。所有方法均为静态且无副作用。</p>
 *
 * <p><b>使用场景：</b>远程挖掘破坏方块时，记录被连带破坏的其他方块
 * （如门的一半、床的另一半、双植物等），以便历史记录系统回滚。</p>
 */
public final class MultiBlockTracker {

    private MultiBlockTracker() {
    }

    /**
     * 捕获指定位置所有 6 个邻居的破坏前状态，用于多方块结构追踪。
     *
     * @param level 服务器世界
     * @param pos   被破坏方块的位置
     * @return 所有非空气邻居的破坏前记录列表
     */
    public static List<HistoryBlockRecord> captureNeighborRecords(ServerLevel level, BlockPos pos) {
        List<HistoryBlockRecord> records = new ArrayList<>(6);
        for (Direction dir : Direction.values()) {
            BlockPos neighbor = pos.relative(dir);
            BlockState state = level.getBlockState(neighbor);
            if (!state.isAir()) {
                records.add(new HistoryBlockRecord(neighbor.immutable(), state));
            }
        }
        return records;
    }

    /**
     * 目标方块被破坏后，检查哪些邻居位置由实变空，加入附属破坏记录。
     *
     * <p>对 {@link #captureNeighborRecords} 捕获的破坏前快照，
     * 逐一检查当前世界状态。若原来非空现在为空气，则为连带破坏。</p>
     *
     * @param level           服务器世界
     * @param session         玩家的 RTS 会话
     * @param neighborRecords 之前捕获的邻居破坏前快照
     * @param brokenPos       被破坏方块的精确位置（跳过自身）
     */
    public static void recordCollateralBlocks(ServerLevel level, RtsStorageSession session,
                                               List<HistoryBlockRecord> neighborRecords, BlockPos brokenPos) {
        for (HistoryBlockRecord nr : neighborRecords) {
            if (nr.pos().equals(brokenPos)) {
                continue;
            }
            BlockState currentState = level.getBlockState(nr.pos());
            if (currentState.isAir() && !nr.state().isAir()) {
                session.mining.ultimineProcessedPositions.add(nr);
            }
        }
    }
}
