package com.rtsbuilding.rtsbuilding.network.blueprint;

import com.rtsbuilding.rtsbuilding.common.blueprint.model.RtsBlueprint;
import com.rtsbuilding.rtsbuilding.network.RtsPayloadRegistrar;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsScanBlueprintResumePayload;
import com.rtsbuilding.rtsbuilding.network.builder.S2CRtsBlueprintResumeScanPayload;
import com.rtsbuilding.rtsbuilding.server.pipeline.context.BlueprintContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineRegistry;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowType;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

/** 蓝图 C2S 的服务端权威边界；所有解析、扫描和任务创建均在世界主线程执行。 */
public final class BlueprintNetworkHandlers {
    private static final String CAMERA_MANAGER =
            "com.rtsbuilding.rtsbuilding.server.camera.RtsCameraManager";
    private static final String BLUEPRINT_READERS =
            "com.rtsbuilding.rtsbuilding.common.blueprint.io.BlueprintReaders";
    private static final String BLUEPRINT_JOB_SERVICE =
            "com.rtsbuilding.rtsbuilding.server.service.RtsBlueprintJobService";

    private BlueprintNetworkHandlers() {
    }

    public static final class PlaceHandler implements IMessageHandler<C2SBlueprintPlacePayload, IMessage> {
        @Override
        public IMessage onMessage(final C2SBlueprintPlacePayload message, MessageContext context) {
            final EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    handlePlaceOnServer(player, message);
                }
            });
            return null;
        }
    }

    public static final class ResumeScanHandler
            implements IMessageHandler<C2SRtsScanBlueprintResumePayload, IMessage> {
        @Override
        public IMessage onMessage(final C2SRtsScanBlueprintResumePayload message, MessageContext context) {
            if (!message.isValid()) return null;
            final EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    sendResumeScan(player, message.workflowEntryId());
                }
            });
            return null;
        }
    }

    private static void handlePlaceOnServer(EntityPlayerMP player, C2SBlueprintPlacePayload payload) {
        if (!payload.isValid()) {
            send(player, S2CBlueprintStatusPayload.ERROR, "screen.rtsbuilding.blueprints.status.invalid", "");
            return;
        }
        if (!isWithinActionRange(player, payload.anchor())) {
            send(player, S2CBlueprintStatusPayload.ERROR, "screen.rtsbuilding.blueprints.status.out_of_range", "");
            return;
        }
        try {
            RtsBlueprint blueprint = parseBlueprint(payload.data(), payload.fileName());
            BlueprintContext context = BlueprintContext.builder(player)
                    .submissionId(payload.submissionId())
                    .blueprint(blueprint)
                    .anchor(payload.anchor())
                    .yRotationSteps(payload.yRotationSteps())
                    .xRotationSteps(payload.xRotationSteps())
                    .zRotationSteps(payload.zRotationSteps())
                    .totalBlocks(blueprint.blocks().size())
                    .build();
            PipelineRegistry.execute(RtsWorkflowType.BLUEPRINT_BUILD, context);
        } catch (BlueprintParseFailure exception) {
            send(player, S2CBlueprintStatusPayload.ERROR,
                    "screen.rtsbuilding.blueprints.status.parse_failed", exception.getMessage());
        }
    }

    private static boolean isWithinActionRange(EntityPlayerMP player, BlockPos anchor) {
        try {
            Class<?> manager = Class.forName(CAMERA_MANAGER);
            Method method = manager.getMethod("isWithinActionRange", EntityPlayerMP.class, BlockPos.class);
            return Boolean.TRUE.equals(method.invoke(null, player, anchor));
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException exception) {
            throw new IllegalStateException("1.12.2 camera range adapter is unavailable", exception);
        } catch (InvocationTargetException exception) {
            throw propagate("Camera range validation failed", exception);
        }
    }

    private static RtsBlueprint parseBlueprint(byte[] data, String fileName) throws BlueprintParseFailure {
        try {
            Class<?> readers = Class.forName(BLUEPRINT_READERS);
            Method parse = readers.getMethod("parse", byte[].class, String.class);
            return (RtsBlueprint) parse.invoke(null, data, fileName);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException exception) {
            throw new IllegalStateException("1.12.2 blueprint parser adapter is unavailable", exception);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            throw new BlueprintParseFailure(cause == null ? "unknown parse failure" : cause.getMessage(), cause);
        }
    }

    @SuppressWarnings("unchecked")
    private static void sendResumeScan(EntityPlayerMP player, int workflowEntryId) {
        try {
            Class<?> service = Class.forName(BLUEPRINT_JOB_SERVICE);
            Method scanMethod = service.getMethod("scanBlueprintMaterials", EntityPlayerMP.class, int.class);
            Object scan = scanMethod.invoke(null, player, workflowEntryId);
            if (scan == null) {
                send(player, S2CBlueprintStatusPayload.ERROR,
                        "screen.rtsbuilding.blueprints.status.resume_not_found", "");
                return;
            }
            Class<?> scanType = scan.getClass();
            S2CRtsBlueprintResumeScanPayload response = new S2CRtsBlueprintResumeScanPayload(
                    (List<String>) scanType.getMethod("itemIds").invoke(scan),
                    (List<String>) scanType.getMethod("itemLabels").invoke(scan),
                    (List<Integer>) scanType.getMethod("required").invoke(scan),
                    (List<Long>) scanType.getMethod("available").invoke(scan),
                    workflowEntryId,
                    ((Number) scanType.getMethod("completedCount").invoke(scan)).intValue(),
                    ((Number) scanType.getMethod("totalCount").invoke(scan)).intValue());
            RtsPayloadRegistrar.sendToPlayer(player, response);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException exception) {
            throw new IllegalStateException("1.12.2 blueprint resume adapter is unavailable", exception);
        } catch (InvocationTargetException exception) {
            throw propagate("Blueprint material scan failed", exception);
        }
    }

    public static void send(EntityPlayerMP player, byte status, String messageKey, String detail) {
        RtsPayloadRegistrar.sendToPlayer(player, new S2CBlueprintStatusPayload(status, messageKey, detail));
    }

    private static RuntimeException propagate(String message, InvocationTargetException exception) {
        Throwable cause = exception.getCause();
        if (cause instanceof RuntimeException) return (RuntimeException) cause;
        return new IllegalStateException(message, cause);
    }

    private static final class BlueprintParseFailure extends Exception {
        private BlueprintParseFailure(String message, Throwable cause) {
            super(message == null ? "unknown parse failure" : message, cause);
        }
    }
}
