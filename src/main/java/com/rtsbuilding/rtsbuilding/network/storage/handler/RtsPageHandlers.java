package com.rtsbuilding.rtsbuilding.network.storage.handler;

import com.rtsbuilding.rtsbuilding.network.storage.RtsStorageSort;
import com.rtsbuilding.rtsbuilding.server.service.RtsPageService;
import net.minecraft.server.level.ServerPlayer;
import com.rtsbuilding.rtsbuilding.forgecompat.network.IPayloadContext;

/**
 * Server-side C2S adapter for linked-storage page requests.
 *
 * <p>Keep page lookup and compatibility behavior in
 * RtsPageService; this layer should only unwrap payloads and enqueue work on
 * the server thread.
 */
public final class RtsPageHandlers {
    private RtsPageHandlers() {
    }

    public static void handleRequestStoragePage(com.rtsbuilding.rtsbuilding.network.storage.C2SRtsRequestStoragePagePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                RtsPageService.requestPage(
                        serverPlayer,
                        payload.page(),
                        payload.search(),
                        payload.category(),
                        RtsStorageSort.byId(payload.sort()),
                        payload.ascending(),
                        payload.pageSize(),
                        payload.pinyinSearchEnabled(),
                        payload.localizedSearchMatches());
            }
        });
    }
}
