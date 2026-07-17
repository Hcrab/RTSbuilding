package com.rtsbuilding.rtsbuilding.server.service;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.common.diagnostics.RtsAsyncJsonlWriter;
import com.rtsbuilding.rtsbuilding.server.task.TaskType;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Locale;

/** OP 开发者场景的服务端采样边界；只读状态并异步写出 JSONL。 */
@EventBusSubscriber(modid = RtsbuildingMod.MODID)
public final class RtsDeveloperScenarioCommand {
    private RtsDeveloperScenarioCommand() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("rtsbuilding_dev")
                .then(Commands.argument("action", StringArgumentType.word())
                        .then(Commands.argument("task", StringArgumentType.word())
                                .then(Commands.argument("runId", StringArgumentType.word())
                                        .executes(context -> checkpoint(
                                                context.getSource().getPlayerOrException(),
                                                StringArgumentType.getStringOr(context, "action", ""),
                                                StringArgumentType.getStringOr(context, "task", ""),
                                                StringArgumentType.getStringOr(context, "runId", "")))))));
    }

    private static int checkpoint(ServerPlayer player, String action, String task, String runId) {
        String safeAction = trim(action, 16);
        String safeTask = trim(task, 48);
        String safeRunId = trim(runId, 64);
        if (!player.hasPermissions(2)) {
            player.sendSystemMessage(Component.translatable(
                    "message.rtsbuilding.developer.server_metrics_requires_op"));
            return 0;
        }
        if ("start".equals(safeAction)) {
            if (!RtsDeveloperMetrics.begin(player, safeRunId, safeTask)) {
                player.sendSystemMessage(Component.translatable(
                        "message.rtsbuilding.developer.run_mismatch"));
                return 0;
            }
            write(player, safeAction, safeTask, safeRunId, null);
        } else if ("finish".equals(safeAction)) {
            var finish = RtsDeveloperMetrics.finish(player, safeRunId, safeTask);
            if (!finish.accepted()) {
                player.sendSystemMessage(Component.translatable(
                        "message.rtsbuilding.developer.run_mismatch"));
                return 0;
            }
            write(player, safeAction, safeTask, safeRunId, finish.snapshot());
        } else {
            return 0;
        }
        player.sendSystemMessage(Component.translatable(
                "message.rtsbuilding.developer.checkpoint_saved", safeRunId));
        return Command.SINGLE_SUCCESS;
    }

    private static void write(ServerPlayer player, String action, String task, String runId,
            RtsDeveloperMetrics.Snapshot metrics) {
        StringBuilder line = new StringBuilder(1024)
                .append("{\"time\":\"").append(escape(Instant.now().toString()))
                .append("\",\"runId\":\"").append(escape(runId))
                .append("\",\"task\":\"").append(escape(task))
                .append("\",\"action\":\"").append(escape(action))
                .append("\",\"player\":\"").append(player.getUUID())
                .append("\",\"dimension\":\"")
                .append(escape(player.level().dimension().identifier().toString())).append('"');
        if (metrics != null) {
            line.append(",\"taskTickAverageNanos\":").append(metrics.averageTickNanos())
                    .append(",\"taskTickMaxNanos\":").append(metrics.maxTickNanos())
                    .append(",\"taskTickSamples\":").append(metrics.tickSamples())
                    .append(",\"processedUnits\":").append(metrics.processedUnits())
                    .append(",\"slices\":").append(metrics.slices())
                    .append(",\"timeBudgetExhausted\":").append(metrics.timeBudgetExhausted())
                    .append(",\"unitBudgetExhausted\":").append(metrics.unitBudgetExhausted());
            for (TaskType type : TaskType.values()) {
                String label = type.name().toLowerCase(Locale.ROOT);
                line.append(",\"active_").append(label).append("_max\":")
                        .append(metrics.maxActive().getOrDefault(type, 0));
                line.append(",\"waiting_").append(label).append("_max\":")
                        .append(metrics.maxWaiting().getOrDefault(type, 0));
            }
            line.append(",\"bufferItems\":").append(metrics.bufferItems())
                    .append(",\"bufferStacks\":").append(metrics.bufferStacks())
                    .append(",\"bufferItemsMax\":").append(metrics.maxBufferItems())
                    .append(",\"bufferStacksMax\":").append(metrics.maxBufferStacks())
                    .append(",\"bufferAgeTicks\":").append(metrics.bufferAgeTicks())
                    .append(",\"bufferAgeTicksMax\":").append(metrics.maxBufferAgeTicks())
                    .append(",\"bufferFallbacks\":").append(metrics.bufferFallbacks())
                    .append(",\"pageBuilds\":").append(metrics.pageBuilds())
                    .append(",\"pageSends\":").append(metrics.pageSends())
                    .append(",\"endpointRebuilds\":").append(metrics.endpointRebuilds())
                    .append(",\"endpointReuses\":").append(metrics.endpointReuses())
                    .append(",\"sessionSnapshots\":").append(metrics.sessionSnapshots())
                    .append(",\"workflowSnapshots\":").append(metrics.workflowSnapshots())
                    .append(",\"historySnapshots\":").append(metrics.historySnapshots())
                    .append(",\"pluginSnapshots\":").append(metrics.pluginSnapshots())
                    .append(",\"progressionSnapshots\":").append(metrics.progressionSnapshots())
                    .append(",\"effectAttemptedTargets\":").append(metrics.effectAttemptedTargets())
                    .append(",\"effectCommittedKinds\":").append(metrics.effectCommittedKinds())
                    .append(",\"effectRetryTargets\":").append(metrics.effectRetryTargets())
                    .append(",\"effectDeferredTargets\":").append(metrics.effectDeferredTargets())
                    .append(",\"effectFailedTargets\":").append(metrics.effectFailedTargets());
        }
        line.append("}\n");
        RtsAsyncJsonlWriter.append(
                Path.of("logs", "rtsbuilding-dev", "server-scenarios.jsonl"), line.toString());
    }

    private static String trim(String value, int maxLength) {
        if (value == null) return "";
        return value.substring(0, Math.min(value.length(), maxLength));
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\r", "\\r").replace("\n", "\\n");
    }
}
