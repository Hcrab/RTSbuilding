package com.rtsbuilding.rtsbuilding.server.storage;

import com.rtsbuilding.rtsbuilding.server.history.HistoryBlockRecord;
import com.rtsbuilding.rtsbuilding.server.service.mining.RtsMiningStateMachine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * 远程挖掘与连锁挖掘运行时状态。
 *
 * <p>本类只保存挖掘状态机需要跨 tick 持有的数据：当前目标、工具租约、
 * ultimine 队列、历史预记录和 workflow 绑定。破坏方块、处理掉落物、
 * 工具归还和发包仍然由 mining service 负责。</p>
 */
public class RtsMiningState {

    /** 当前单方块挖掘目标，null 表示没有单方块挖掘。 */
    public BlockPos miningPos;
    /** 挖掘方向。 */
    public Direction miningFace = Direction.DOWN;
    /** 当前使用的工具栏槽位。 */
    public int miningToolSlot;
    /** 当前借用的远程挖掘工具租约。 */
    public RtsToolLease miningToolLease = RtsToolLease.empty();
    /** true 表示必须使用 RTS 选中的非方块工具，不可静默回退热键栏。 */
    public boolean miningSelectedToolRequested;
    /** true 表示批量挖掘应在耐久工具进入最后 5% 前停止。 */
    public boolean miningToolProtectionEnabled = true;
    /** 当前挖掘进度。 */
    public float miningProgress;
    /** 当前破坏阶段；-1 表示尚未开始。 */
    public int miningStage = -1;
    /** 当前挖掘/连锁挖掘绑定的 workflow 条目；-1 表示无。 */
    public int miningWorkflowEntryId = -1;

    /** 连锁挖掘待处理目标队列。 */
    public final Deque<BlockPos> ultimineTargets = new ArrayDeque<>();
    /** 等待当前批量挖掘完成后继续执行的独立作业队列。 */
    public final Deque<RtsMiningStateMachine.MiningJob> ultimineJobQueue = new ArrayDeque<>();
    /** 连锁挖掘当前正在显示进度的目标。 */
    public BlockPos ultimineProgressPos;
    /** 连锁挖掘本次任务总目标数。 */
    public int ultimineTotalTargets;
    /** 连锁挖掘已处理目标数。 */
    public int ultimineProcessedTargets;
    /** 已成功破坏的位置预记录，用于批量写入历史。 */
    public final List<HistoryBlockRecord> ultimineProcessedPositions = new ArrayList<>();
    /** 已成功破坏的目标数，用于 workflow 进度统计。 */
    public int ultimineBrokenTargets;
    /** 是否已经吸收本轮掉落物。 */
    public boolean ultimineAbsorbedDrops;
}
