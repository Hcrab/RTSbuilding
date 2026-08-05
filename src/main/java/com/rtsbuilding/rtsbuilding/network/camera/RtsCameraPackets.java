package com.rtsbuilding.rtsbuilding.network.camera;

import com.rtsbuilding.rtsbuilding.network.ClientPayloadDispatcher;
import com.rtsbuilding.rtsbuilding.network.RtsPayloadRegistrar;
import com.rtsbuilding.rtsbuilding.network.camera.handler.RtsCameraNetworkHandlers;
import cpw.mods.fml.relauncher.Side;

/** 注册 RTS 相机会话和移动消息。 */
public final class RtsCameraPackets {
    private RtsCameraPackets() {
    }

    public static void register() {
        RtsPayloadRegistrar.registerMessage(0, RtsCameraNetworkHandlers.ToggleHandler.class,
                C2SRtsToggleCameraPayload.class, Side.SERVER);
        RtsPayloadRegistrar.registerMessage(1, RtsCameraNetworkHandlers.MoveHandler.class,
                C2SRtsCameraMovePayload.class, Side.SERVER);
        RtsPayloadRegistrar.registerMessage(2, ClientPayloadDispatcher.CameraStateHandler.class,
                S2CRtsCameraStatePayload.class, Side.CLIENT);
        RtsPayloadRegistrar.registerMessage(3, ClientPayloadDispatcher.CameraAnchorHandler.class,
                S2CRtsCameraAnchorPayload.class, Side.CLIENT);
    }
}
