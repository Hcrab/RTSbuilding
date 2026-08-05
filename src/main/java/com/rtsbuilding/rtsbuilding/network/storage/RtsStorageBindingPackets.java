package com.rtsbuilding.rtsbuilding.network.storage;
import com.rtsbuilding.rtsbuilding.network.RtsPayloadRegistrar;
import com.rtsbuilding.rtsbuilding.network.storage.handler.RtsStorageBindingPacketHandlers;
import cpw.mods.fml.relauncher.Side;
/** 注册储存链接和漏斗设置的 C2S 消息。 */
public final class RtsStorageBindingPackets {
    private RtsStorageBindingPackets() { }
    public static void register(){
        RtsPayloadRegistrar.registerMessage(128,RtsStorageBindingPacketHandlers.Link.class,C2SRtsLinkStoragePayload.class,Side.SERVER);
        RtsPayloadRegistrar.registerMessage(129,RtsStorageBindingPacketHandlers.Unlink.class,C2SRtsUnlinkStoragePayload.class,Side.SERVER);
        RtsPayloadRegistrar.registerMessage(130,RtsStorageBindingPacketHandlers.Update.class,C2SRtsUpdateLinkedStoragePayload.class,Side.SERVER);
        RtsPayloadRegistrar.registerMessage(131,RtsStorageBindingPacketHandlers.FunnelTarget.class,C2SRtsFunnelTargetPayload.class,Side.SERVER);
        RtsPayloadRegistrar.registerMessage(132,RtsStorageBindingPacketHandlers.SetFunnel.class,C2SRtsSetFunnelPayload.class,Side.SERVER);
        RtsPayloadRegistrar.registerMessage(133,RtsStorageBindingPacketHandlers.SetAutoStore.class,C2SRtsSetAutoStorePayload.class,Side.SERVER);
    }
}
