package com.rtsbuilding.rtsbuilding.gametest;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import com.rtsbuilding.rtsbuilding.platform.math.BlockPos;
import net.minecraft.util.ChatComponentText;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Forge 1.12.2 的机器可判定 GameTest 等价入口。
 *
 * <p>本命令只负责编排和报告，不在命令层伪造世界行为。已经迁移的便携契约会真实执行；仍缺
 * 世界夹具的原 1.21 GameTest 会明确报告 {@code BLOCKED}，其中 {@code strict} 与具名
 * {@code run} 会以命令失败结束。每轮均原子写出 JSON 报告，并在日志打印固定前缀，方便
 * runServer 驱动脚本在没有原生 GameTest 框架的 1.12 环境判定结果。</p>
 */
public final class RtsGameTestCommand extends CommandBase {
    private static final Path REPORT_DIRECTORY = Paths.get("logs", "rtsbuilding-gametest");
    private static final Path LATEST_REPORT = REPORT_DIRECTORY.resolve("latest.json");

    @Override
    public String getCommandName() {
        return "rtsbuilding_test";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/rtsbuilding_test <list|portable|strict|run <scenario>>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 4;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) throws CommandException {
        if (args.length == 1 && "list".equalsIgnoreCase(args[0])) {
            list(sender);
            return;
        }

        final String mode;
        final List<RtsServerGameTests.Result> results;
        if (args.length == 1 && "portable".equalsIgnoreCase(args[0])) {
            mode = "portable";
            results = RtsServerGameTests.run(false);
        } else if (args.length == 1 && "strict".equalsIgnoreCase(args[0])) {
            mode = "strict";
            results = RtsServerGameTests.run(true);
        } else if (args.length == 2 && "run".equalsIgnoreCase(args[0])) {
            mode = "scenario:" + args[1];
            try {
                results = Collections.singletonList(RtsServerGameTests.runScenario(args[1]));
            } catch (IllegalArgumentException unknown) {
                throw new CommandException(unknown.getMessage());
            }
        } else {
            throw new CommandException(getCommandUsage(sender));
        }

        Summary summary = summarize(results);
        Path report;
        try {
            report = writeReport(mode, results, summary);
        } catch (IOException failure) {
            throw new CommandException("RTS_112_GAMETEST_REPORT_FAILED: " + failure.getMessage());
        }

        String marker = "RTS_112_GAMETEST_SUMMARY pass=" + summary.passed
                + " blocked=" + summary.blocked + " failed=" + summary.failed
                + " mode=" + mode + " report=" + report.toAbsolutePath();
        System.out.println(marker);
        sender.addChatMessage(new ChatComponentText(marker));

        boolean strict = "strict".equals(mode) || mode.startsWith("scenario:");
        if (summary.failed > 0 || (strict && summary.blocked > 0)) {
            throw new CommandException("RTS_112_GAMETEST_FAILED pass=" + summary.passed
                    + " blocked=" + summary.blocked + " failed=" + summary.failed);
        }
    }

    private static void list(ICommandSender sender) {
        List<RtsServerGameTests.Scenario> scenarios = RtsServerGameTests.scenarios();
        for (RtsServerGameTests.Scenario scenario : scenarios) {
            String line = (scenario.isPortable() ? "PORTABLE " : "BLOCKED ") + scenario.id()
                    + " [" + scenario.group() + "] " + scenario.intent();
            sender.addChatMessage(new ChatComponentText(line));
        }
        sender.addChatMessage(new ChatComponentText("RTS_112_GAMETEST_INVENTORY total=" + scenarios.size()));
    }

    private static Summary summarize(List<RtsServerGameTests.Result> results) {
        Summary summary = new Summary();
        for (RtsServerGameTests.Result result : results) {
            if (result.outcome() == RtsServerGameTests.Outcome.PASS) summary.passed++;
            else if (result.outcome() == RtsServerGameTests.Outcome.BLOCKED) summary.blocked++;
            else summary.failed++;
        }
        return summary;
    }

    private static Path writeReport(String mode, List<RtsServerGameTests.Result> results, Summary summary)
            throws IOException {
        Files.createDirectories(REPORT_DIRECTORY);
        String runId = Long.toString(System.currentTimeMillis());
        Path runReport = REPORT_DIRECTORY.resolve("run-" + runId + ".json");
        Path temporary = REPORT_DIRECTORY.resolve("run-" + runId + ".json.tmp");
        StringBuilder json = new StringBuilder(4096);
        json.append("{\n  \"schema\":1,\n  \"time\":\"").append(escape(Instant.now().toString()))
                .append("\",\n  \"mode\":\"").append(escape(mode)).append("\",")
                .append("\n  \"pass\":").append(summary.passed)
                .append(",\n  \"blocked\":").append(summary.blocked)
                .append(",\n  \"failed\":").append(summary.failed)
                .append(",\n  \"results\":[\n");
        for (int i = 0; i < results.size(); i++) {
            RtsServerGameTests.Result result = results.get(i);
            json.append("    {\"id\":\"").append(escape(result.scenario().id()))
                    .append("\",\"group\":\"").append(escape(result.scenario().group()))
                    .append("\",\"outcome\":\"").append(result.outcome().name().toLowerCase(Locale.ROOT))
                    .append("\",\"detail\":\"").append(escape(result.detail())).append("\"}");
            if (i + 1 < results.size()) json.append(',');
            json.append('\n');
        }
        json.append("  ]\n}\n");
        Files.write(temporary, json.toString().getBytes(StandardCharsets.UTF_8));
        moveReplacing(temporary, runReport);
        Files.copy(runReport, LATEST_REPORT, StandardCopyOption.REPLACE_EXISTING);
        return runReport;
    }

    private static void moveReplacing(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String escape(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\r", "\\r").replace("\n", "\\n");
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "list", "portable", "strict", "run");
        }
        if (args.length == 2 && "run".equalsIgnoreCase(args[0])) {
            List<String> ids = new ArrayList<String>();
            for (RtsServerGameTests.Scenario scenario : RtsServerGameTests.scenarios()) ids.add(scenario.id());
            return getListOfStringsMatchingLastWord(args, ids.toArray(new String[ids.size()]));
        }
        return Collections.emptyList();
    }

    private static final class Summary {
        private int passed;
        private int blocked;
        private int failed;
    }
}
