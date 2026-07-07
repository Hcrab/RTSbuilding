package com.rtsbuilding.rtsbuilding.network.blueprint;

import com.rtsbuilding.rtsbuilding.common.blueprint.io.BlueprintReaders;
import com.rtsbuilding.rtsbuilding.common.blueprint.model.BlueprintParseException;
import com.rtsbuilding.rtsbuilding.common.blueprint.model.RtsBlueprint;
import com.rtsbuilding.rtsbuilding.server.pipeline.context.BlueprintContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineRegistry;
import com.rtsbuilding.rtsbuilding.server.network.RtsClientboundPackets;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowType;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class BlueprintNetworkHandlers {
    private BlueprintNetworkHandlers() {
    }

    public static void handlePlace(C2SBlueprintPlacePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
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
                BlueprintContext ctx = BlueprintContext.builder(player)
                        .blueprint(blueprint)
                        .anchor(payload.anchor())
                        .yRotationSteps(payload.yRotationSteps())
                        .xRotationSteps(payload.xRotationSteps())
                        .zRotationSteps(payload.zRotationSteps())
                        .totalBlocks(blueprint.blocks().size())
                        .build();
                PipelineRegistry.execute(RtsWorkflowType.BLUEPRINT_BUILD, ctx);
            } catch (BlueprintParseException ex) {
                send(player, S2CBlueprintStatusPayload.ERROR, "screen.rtsbuilding.blueprints.status.parse_failed", ex.getMessage());
            }
        });
    }

    public static void send(ServerPlayer player, byte status, String messageKey, String detail) {
        RtsClientboundPackets.sendToPlayer(player, new S2CBlueprintStatusPayload(status, messageKey, detail));
    }
}
