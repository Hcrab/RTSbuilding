package com.rtsbuilding.rtsbuilding.network.blueprint;

import com.rtsbuilding.rtsbuilding.network.ClientPayloadDispatcher;
import com.rtsbuilding.rtsbuilding.network.RtsPayloadRegistrar;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsScanBlueprintResumePayload;
import com.rtsbuilding.rtsbuilding.network.builder.S2CRtsBlueprintResumeScanPayload;
import net.minecraftforge.fml.relauncher.Side;

/** 注册蓝图上传、状态和挂起任务材料扫描闭环。 */
public final class BlueprintPayloadRegistrar {
    private BlueprintPayloadRegistrar() {
    }

    public static void register() {
        RtsPayloadRegistrar.registerMessage(64, BlueprintNetworkHandlers.PlaceHandler.class,
                C2SBlueprintPlacePayload.class, Side.SERVER);
        RtsPayloadRegistrar.registerMessage(65, ClientPayloadDispatcher.BlueprintStatusHandler.class,
                S2CBlueprintStatusPayload.class, Side.CLIENT);
        RtsPayloadRegistrar.registerMessage(66, BlueprintNetworkHandlers.ResumeScanHandler.class,
                C2SRtsScanBlueprintResumePayload.class, Side.SERVER);
        RtsPayloadRegistrar.registerMessage(67, ClientPayloadDispatcher.BlueprintResumeScanHandler.class,
                S2CRtsBlueprintResumeScanPayload.class, Side.CLIENT);
    }
}
