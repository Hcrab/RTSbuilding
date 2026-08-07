package com.rtsbuilding.rtsbuilding.network.builder;

import com.rtsbuilding.rtsbuilding.network.ClientPayloadDispatcher;
import com.rtsbuilding.rtsbuilding.network.RtsPayloadRegistrar;
import com.rtsbuilding.rtsbuilding.network.builder.handler.RtsBuilderSyncHandlers1122;
import net.minecraftforge.fml.relauncher.Side;

/** 注册工作流控制及建造反馈的 1.12 固定协议编号。 */
public final class RtsBuilderSyncPackets1122 {
    private RtsBuilderSyncPackets1122() {}

    public static void register() {
        RtsPayloadRegistrar.registerMessage(16, RtsBuilderSyncHandlers1122.PauseWorkflow.class,
                C2SRtsPauseWorkflowPayload.class, Side.SERVER);
        RtsPayloadRegistrar.registerMessage(17, RtsBuilderSyncHandlers1122.ScanResumePlacement.class,
                C2SRtsScanResumePlacementPayload.class, Side.SERVER);
        RtsPayloadRegistrar.registerMessage(18, RtsBuilderSyncHandlers1122.ResumePlacementAction.class,
                C2SRtsResumePlacementActionPayload.class, Side.SERVER);
        RtsPayloadRegistrar.registerMessage(19, ClientPayloadDispatcher.BreakAnimationHandler.class,
                S2CRtsBreakAnimationPayload.class, Side.CLIENT);
        RtsPayloadRegistrar.registerMessage(20, ClientPayloadDispatcher.HistorySyncHandler.class,
                S2CRtsHistorySyncPayload.class, Side.CLIENT);
        RtsPayloadRegistrar.registerMessage(21, ClientPayloadDispatcher.PlaceAnimationHandler.class,
                S2CRtsPlaceAnimationPayload.class, Side.CLIENT);
        RtsPayloadRegistrar.registerMessage(22, ClientPayloadDispatcher.MineProgressHandler.class,
                S2CRtsMineProgressPayload.class, Side.CLIENT);
        RtsPayloadRegistrar.registerMessage(23, ClientPayloadDispatcher.HarvestTierSkippedHandler.class,
                S2CRtsHarvestTierSkippedPayload.class, Side.CLIENT);
        RtsPayloadRegistrar.registerMessage(24, ClientPayloadDispatcher.UltimineProgressHandler.class,
                S2CRtsUltimineProgressPayload.class, Side.CLIENT);
        RtsPayloadRegistrar.registerMessage(25, ClientPayloadDispatcher.WorkflowProgressBatchHandler.class,
                S2CRtsWorkflowProgressBatchPayload.class, Side.CLIENT);
        RtsPayloadRegistrar.registerMessage(26, ClientPayloadDispatcher.WorkflowProgressHandler.class,
                S2CRtsWorkflowProgressPayload.class, Side.CLIENT);
        RtsPayloadRegistrar.registerMessage(27, ClientPayloadDispatcher.ResumePlacementScanHandler.class,
                S2CRtsResumePlacementScanPayload.class, Side.CLIENT);
        RtsPayloadRegistrar.registerMessage(28, ClientPayloadDispatcher.OperationTerminalHandler.class,
                S2CRtsOperationTerminalPayload.class, Side.CLIENT);
    }
}
