package com.rtsbuilding.rtsbuilding.network.progression;

import com.rtsbuilding.rtsbuilding.network.RtsPayloadRegistrar;
import com.rtsbuilding.rtsbuilding.network.progression.handler.RtsProgressionNetworkHandlers;
import cpw.mods.fml.relauncher.Side;

/** 注册任务检测、家园选择和生存进度同步消息。 */
public final class RtsProgressionPackets {
    private RtsProgressionPackets() {
    }

    public static void register() {
        RtsPayloadRegistrar.registerMessage(8, RtsProgressionNetworkHandlers.QuestDetect.class,
                C2SRtsQuestDetectPayload.class, Side.SERVER);
        RtsPayloadRegistrar.registerMessage(9, RtsProgressionNetworkHandlers.SetSurvivalProgression.class,
                C2SRtsSetSurvivalProgressionPayload.class, Side.SERVER);
        RtsPayloadRegistrar.registerMessage(10, RtsProgressionNetworkHandlers.SetHome.class,
                C2SRtsSetHomePayload.class, Side.SERVER);
        RtsPayloadRegistrar.registerMessage(11, RtsProgressionNetworkHandlers.BeginHomeSelection.class,
                C2SRtsBeginHomeSelectionPayload.class, Side.SERVER);
        RtsPayloadRegistrar.registerMessage(12, RtsProgressionNetworkHandlers.RequestProgressionState.class,
                C2SRtsRequestProgressionStatePayload.class, Side.SERVER);
        RtsPayloadRegistrar.registerMessage(13, RtsProgressionNetworkHandlers.ClientQuestDetectStatus.class,
                S2CRtsQuestDetectStatusPayload.class, Side.CLIENT);
        RtsPayloadRegistrar.registerMessage(14, RtsProgressionNetworkHandlers.ClientProgressionState.class,
                S2CRtsProgressionStatePayload.class, Side.CLIENT);
    }
}
