package com.rtsbuilding.rtsbuilding.blueprint.network;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.blueprint.BlueprintParseException;
import com.rtsbuilding.rtsbuilding.blueprint.RtsBlueprint;
import com.rtsbuilding.rtsbuilding.blueprint.format.BlueprintReaders;
import com.rtsbuilding.rtsbuilding.blueprint.server.BlueprintPlacementService;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class BlueprintNetworkHandlers {
    private BlueprintNetworkHandlers() {
    }

    public static void handlePlace(C2SBlueprintPlacePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            if (!Config.areBlueprintsEnabled()) {
                send(player, S2CBlueprintStatusPayload.ERROR, "screen.rtsbuilding.blueprints.status.disabled", "");
                return;
            }
            if (payload.data() == null || payload.data().length <= 0) {
                send(player, S2CBlueprintStatusPayload.ERROR, "screen.rtsbuilding.blueprints.status.empty", "");
                return;
            }
            if (payload.data().length > C2SBlueprintPlacePayload.MAX_FILE_BYTES) {
                send(player, S2CBlueprintStatusPayload.ERROR, "screen.rtsbuilding.blueprints.status.too_large", "");
                return;
            }
            try {
                RtsBlueprint blueprint = BlueprintReaders.parse(payload.data(), payload.fileName(), player.registryAccess());
                BlueprintPlacementService.queuePlacement(
                        player,
                        blueprint,
                        payload.anchor(),
                        payload.yRotationSteps(),
                        payload.xRotationSteps(),
                        payload.zRotationSteps());
            } catch (BlueprintParseException ex) {
                send(player, S2CBlueprintStatusPayload.ERROR, "screen.rtsbuilding.blueprints.status.parse_failed", ex.getMessage());
            }
        });
    }

    public static void send(ServerPlayer player, byte status, String messageKey, String detail) {
        PacketDistributor.sendToPlayer(player, new S2CBlueprintStatusPayload(status, messageKey, detail));
    }
}
