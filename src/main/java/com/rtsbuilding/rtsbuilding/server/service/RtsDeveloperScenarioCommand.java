package com.rtsbuilding.rtsbuilding.server.service;

import com.rtsbuilding.rtsbuilding.common.diagnostics.RtsAsyncJsonlWriter;
import com.rtsbuilding.rtsbuilding.server.task.TaskType;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentTranslation;

import java.nio.file.Paths;
import java.time.Instant;
import java.util.Locale;

/** OP 开发者场景的服务端采样边界；只读状态并异步写出 JSONL。 */
public final class RtsDeveloperScenarioCommand extends CommandBase {
    public RtsDeveloperScenarioCommand() {
    }

    @Override public String getCommandName() { return "rtsbuilding_dev"; }
    @Override public String getCommandUsage(ICommandSender sender) { return "/rtsbuilding_dev <start|finish> <task> <runId>"; }
    @Override public int getRequiredPermissionLevel() { return 2; }

    @Override
    public void processCommand(ICommandSender sender, String[] args) throws CommandException {
        if (args.length != 3) throw new CommandException(getCommandUsage(sender));
        checkpoint(getCommandSenderAsPlayer(sender), args[0], args[1], args[2]);
    }

    private static void checkpoint(EntityPlayerMP player, String action, String task, String runId) {
        String safeAction = trim(action, 16);
        String safeTask = trim(task, 48);
        String safeRunId = trim(runId, 64);
        if (!com.rtsbuilding.rtsbuilding.platform.player.PlayerCompat.canUseCommand(player, 2, "rtsbuilding_dev")) {
            player.addChatMessage(new ChatComponentTranslation(
                    "message.rtsbuilding.developer.server_metrics_requires_op"));
            return;
        }
        if ("start".equals(safeAction)) {
            if (!RtsDeveloperMetrics.begin(player, safeRunId, safeTask)) {
                player.addChatMessage(new ChatComponentTranslation(
                        "message.rtsbuilding.developer.run_mismatch"));
                return;
            }
            write(player, safeAction, safeTask, safeRunId, null);
        } else if ("finish".equals(safeAction)) {
            RtsDeveloperMetrics.FinishResult finish = RtsDeveloperMetrics.finish(player, safeRunId, safeTask);
            if (!finish.accepted()) {
                player.addChatMessage(new ChatComponentTranslation(
                        "message.rtsbuilding.developer.run_mismatch"));
                return;
            }
            write(player, safeAction, safeTask, safeRunId, finish.snapshot());
        } else {
            return;
        }
        player.addChatMessage(new ChatComponentTranslation(
                "message.rtsbuilding.developer.checkpoint_saved", safeRunId));
    }

    private static void write(EntityPlayerMP player, String action, String task, String runId,
            RtsDeveloperMetrics.Snapshot metrics) {
        StringBuilder line = new StringBuilder(1024)
                .append("{\"time\":\"").append(escape(Instant.now().toString()))
                .append("\",\"runId\":\"").append(escape(runId))
                .append("\",\"task\":\"").append(escape(task))
                .append("\",\"action\":\"").append(escape(action))
                .append("\",\"player\":\"").append(player.getUniqueID())
                .append("\",\"dimension\":\"")
                .append(player.dimension).append('"');
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
                Paths.get("logs", "rtsbuilding-dev", "server-scenarios.jsonl"), line.toString());
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
