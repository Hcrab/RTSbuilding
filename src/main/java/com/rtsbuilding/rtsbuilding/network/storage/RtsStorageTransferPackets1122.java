package com.rtsbuilding.rtsbuilding.network.storage;

import com.rtsbuilding.rtsbuilding.network.RtsPayloadRegistrar;
import com.rtsbuilding.rtsbuilding.network.storage.handler.RtsBindingHandlers;
import com.rtsbuilding.rtsbuilding.network.storage.handler.RtsTransferHandlers;
import net.minecraftforge.fml.relauncher.Side;

/** 注册快捷槽、远程 GUI 与物品传输的 1.12 C2S 固定协议。 */
public final class RtsStorageTransferPackets1122 {
    private RtsStorageTransferPackets1122() {}
    public static void register() {
        RtsPayloadRegistrar.registerMessage(35, RtsBindingHandlers.CloseRemoteMenu.class,
                C2SRtsCloseRemoteMenuPayload.class, Side.SERVER);
        RtsPayloadRegistrar.registerMessage(36, RtsTransferHandlers.FillInventory.class,
                C2SRtsFillInventoryPayload.class, Side.SERVER);
        RtsPayloadRegistrar.registerMessage(37, RtsTransferHandlers.ImportMenuSlot.class,
                C2SRtsImportMenuSlotPayload.class, Side.SERVER);
        RtsPayloadRegistrar.registerMessage(38, RtsTransferHandlers.LinkedPickup.class,
                C2SRtsLinkedPickupPayload.class, Side.SERVER);
        RtsPayloadRegistrar.registerMessage(39, RtsTransferHandlers.LinkedQuickMove.class,
                C2SRtsLinkedQuickMovePayload.class, Side.SERVER);
        RtsPayloadRegistrar.registerMessage(40, RtsBindingHandlers.OpenGuiBinding.class,
                C2SRtsOpenGuiBindingPayload.class, Side.SERVER);
        RtsPayloadRegistrar.registerMessage(41, RtsTransferHandlers.ReturnCarried.class,
                C2SRtsReturnCarriedPayload.class, Side.SERVER);
        RtsPayloadRegistrar.registerMessage(42, RtsBindingHandlers.SetBdNetwork.class,
                C2SRtsSetBdNetworkPayload.class, Side.SERVER);
        RtsPayloadRegistrar.registerMessage(43, RtsBindingHandlers.SetGuiBinding.class,
                C2SRtsSetGuiBindingPayload.class, Side.SERVER);
        RtsPayloadRegistrar.registerMessage(44, RtsBindingHandlers.SetQuickSlot.class,
                C2SRtsSetQuickSlotPayload.class, Side.SERVER);
        RtsPayloadRegistrar.registerMessage(45, RtsBindingHandlers.StoreHotbarSlot.class,
                C2SRtsStoreHotbarSlotPayload.class, Side.SERVER);
    }
}
