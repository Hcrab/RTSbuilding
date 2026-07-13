package com.rtsbuilding.rtsbuilding.client.developer;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.common.diagnostics.RtsAsyncJsonlWriter;
import net.minecraft.client.Minecraft;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 开发者场景的客户端作业记录器。
 *
 * <p>它只记录 RTS 操作类别、数量和时间，不记录聊天、账号、完整物品 NBT 或服务器地址。
 * 日志位于游戏目录的 logs/rtsbuilding-dev，永远不会写入源码仓库。</p>
 */
public final class RtsDeveloperScenarioTracker {
    public enum Scenario {
        BIND_STORAGE("screen.rtsbuilding.developer.task.bind",
                Map.of("storage_link_request", 1, "storage_page_received", 1)),
        CONTINUOUS_PLACE("screen.rtsbuilding.developer.task.place",
                Map.of("place_request", 20, "place_confirmed", 20)),
        SMALL_MINING("screen.rtsbuilding.developer.task.mine",
                Map.of("mine_request", 1, "break_confirmed", 1, "storage_page_received", 1)),
        LARGE_BATCH("screen.rtsbuilding.developer.task.batch",
                Map.of("place_batch_request", 1, "place_confirmed", 1, "workflow_update_received", 1));

        private final String translationKey;
        private final Map<String, Integer> expectedEvents;

        Scenario(String translationKey, Map<String, Integer> expectedEvents) {
            this.translationKey = translationKey;
            this.expectedEvents = expectedEvents;
        }

        public String translationKey() {
            return translationKey;
        }
    }

    private static final RtsDeveloperScenarioTracker INSTANCE = new RtsDeveloperScenarioTracker();
    private Scenario active;
    private UUID runId;
    private RtsDeveloperScenarioProgress progress;
    private long startedNanos;

    private RtsDeveloperScenarioTracker() {
    }

    public static RtsDeveloperScenarioTracker getInstance() {
        return INSTANCE;
    }

    public synchronized void start(Scenario scenario) {
        if (!Config.isDeveloperModeEnabled() || scenario == null) {
            return;
        }
        active = scenario;
        runId = UUID.randomUUID();
        progress = new RtsDeveloperScenarioProgress(scenario.expectedEvents);
        startedNanos = System.nanoTime();
        append("start", "scenario=" + scenario.name());
        sendServerCheckpoint("start");
    }

    public synchronized void record(String event, String detail) {
        if (!Config.isDeveloperModeEnabled() || active == null || event == null) {
            return;
        }
        append(event, detail == null ? "" : detail);
        progress.record(event);
        if (progress.isComplete()) {
            append("complete", "scenario=" + active.name());
            sendServerCheckpoint("finish");
            active = null;
        }
    }

    public synchronized Scenario activeScenario() {
        return active;
    }

    public synchronized int currentStep() {
        return progress == null ? 0 : progress.completedEvents();
    }

    public synchronized int requiredSteps() {
        return progress == null ? 0 : progress.requiredEvents();
    }

    private void append(String event, String detail) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.gameDirectory == null || runId == null) {
            return;
        }
        Path file = minecraft.gameDirectory.toPath().resolve("logs").resolve("rtsbuilding-dev")
                .resolve("scenario-" + runId + ".jsonl");
        long elapsedMillis = Math.max(0L, (System.nanoTime() - startedNanos) / 1_000_000L);
        String line = "{\"time\":\"" + Instant.now() + "\",\"runId\":\"" + runId
                + "\",\"elapsedMs\":" + elapsedMillis + ",\"event\":\"" + escape(event)
                + "\",\"detail\":\"" + escape(detail) + "\"}\n";
        RtsAsyncJsonlWriter.append(file, line);
    }

    private void sendServerCheckpoint(String action) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getConnection() == null || active == null || runId == null) return;
        minecraft.getConnection().sendCommand("rtsbuilding_dev " + action + " "
                + active.name().toLowerCase(Locale.ROOT) + " " + runId);
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\r", "\\r").replace("\n", "\\n");
    }
}
