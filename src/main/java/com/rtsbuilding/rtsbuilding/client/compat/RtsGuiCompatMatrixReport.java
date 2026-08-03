package com.rtsbuilding.rtsbuilding.client.compat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * GUI 矩阵的追加式 TSV 报告。
 *
 * <p>每个候选先写 BEGIN、最后写 RESULT。若第三方方块令客户端崩溃，下次运行会
 * 把只有 BEGIN 的候选视为上次崩溃点并跳过，从而让大矩阵可以断点续跑。</p>
 */
final class RtsGuiCompatMatrixReport {
    private static final String HEADER = "timestamp\tevent\tindex\ttotal\tkey\tnamespace\tblockId\tmeta\t"
            + "blockClass\ttileEntity\toverridesActivation\tnearDistance\tnearOutcome\tnearScreen\tnearMenu\t"
            + "farDistance\tfarOutcome\tfarScreen\tfarMenu\tnote\r\n";

    private final Path path;

    RtsGuiCompatMatrixReport(Path path) {
        this.path = path;
    }

    ResumeState readResumeState() {
        if (path == null || !Files.isRegularFile(path)) return ResumeState.empty();
        Set<String> begun = new HashSet<String>();
        Set<String> completed = new HashSet<String>();
        Set<String> openedNear = new HashSet<String>();
        Set<String> passedBothDistances = new HashSet<String>();
        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            for (String line : lines) {
                String[] columns = line.split("\\t", -1);
                if (columns.length < 6 || "event".equals(columns[1])) continue;
                if ("BEGIN".equals(columns[1])) {
                    begun.add(columns[4]);
                } else if ("RESULT".equals(columns[1])) {
                    String key = columns[4];
                    begun.remove(key);
                    if ("OPEN_STABLE".equals(columns[12])) openedNear.add(key);
                    if (isFullPass(columns)) passedBothDistances.add(key);

                    // “曾经确实开出过 GUI”比后一次孤立状态没开出来更强。只有完整
                    // 近/远通过才结束这种候选的重测，不能把抖动降级成 NO_GUI 洗白。
                    if (passedBothDistances.contains(key)
                            || isReliableTerminalResult(columns, openedNear.contains(key))) {
                        completed.add(key);
                    } else {
                        completed.remove(key);
                    }
                }
            }
        } catch (IOException ignored) {
            return ResumeState.empty();
        }
        begun.removeAll(completed);
        return new ResumeState(completed, begun);
    }

    /** 失败结果必须允许新版探针定向重测；成功、明确无 GUI 和已隔离崩溃点才可跳过。 */
    private static boolean isReliableTerminalResult(String[] columns, boolean openedNearBeforeOrNow) {
        if (columns.length < 20) return false;
        String near = columns[12];
        return "NO_GUI_OR_PREREQUISITE".equals(near) && !openedNearBeforeOrNow
                || "INTERRUPTED_PREVIOUS_RUN".equals(near);
    }

    private static boolean isFullPass(String[] columns) {
        return columns.length >= 20
                && "OPEN_STABLE".equals(columns[12])
                && "OPEN_STABLE".equals(columns[16]);
    }

    synchronized void begin(int index, int total, RtsGuiCompatCandidateCatalog.Candidate candidate,
            int nearDistance, int farDistance) {
        append("BEGIN", index, total, candidate, nearDistance,
                "", "", "", farDistance, "", "", "", "");
    }

    synchronized void result(int index, int total, RtsGuiCompatCandidateCatalog.Candidate candidate,
            int nearDistance, Observation near, int farDistance, Observation far, String note) {
        append("RESULT", index, total, candidate, nearDistance,
                near.outcome, near.screen, near.menu,
                farDistance, far.outcome, far.screen, far.menu, note);
    }

    synchronized void summary(int total, int guiCandidates, int passed, int failed,
            int skipped, int previousCrashes) {
        if (path == null) return;
        ensureHeader();
        write(System.currentTimeMillis() + "\tSUMMARY\t0\t" + total
                + "\t-\t-\t-\t0\t-\tfalse\tfalse\t0\t"
                + guiCandidates + " GUI\t\t\t0\t"
                + passed + " PASS / " + failed + " FAIL\t\t\t"
                + escape("skipped=" + skipped + " previousCrashes=" + previousCrashes) + "\r\n");
    }

    private void append(String event, int index, int total,
            RtsGuiCompatCandidateCatalog.Candidate candidate,
            int nearDistance, String nearOutcome, String nearScreen, String nearMenu,
            int farDistance, String farOutcome, String farScreen, String farMenu, String note) {
        if (path == null) return;
        ensureHeader();
        write(System.currentTimeMillis() + "\t" + event + "\t" + index + "\t" + total
                + "\t" + escape(candidate.key()) + "\t" + escape(candidate.namespace())
                + "\t" + escape(candidate.blockId()) + "\t" + candidate.meta()
                + "\t" + escape(candidate.blockClass()) + "\t" + candidate.tileEntity()
                + "\t" + candidate.overridesActivation() + "\t" + nearDistance
                + "\t" + escape(nearOutcome) + "\t" + escape(nearScreen) + "\t" + escape(nearMenu)
                + "\t" + farDistance + "\t" + escape(farOutcome)
                + "\t" + escape(farScreen) + "\t" + escape(farMenu)
                + "\t" + escape(note) + "\r\n");
    }

    private void ensureHeader() {
        if (path == null || Files.exists(path)) return;
        try {
            Path parent = path.getParent();
            if (parent != null) Files.createDirectories(parent);
            Files.write(path, HEADER.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
            // 调用方仍会通过缺失报告令 Gradle 门禁失败。
        }
    }

    private void write(String text) {
        try {
            Files.write(path, text.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
            // 报告写入失败不应在客户端线程制造第二次崩溃。
        }
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace('\t', ' ').replace('\r', ' ').replace('\n', ' ');
    }

    static final class Observation {
        static final Observation EMPTY = new Observation("", "", "");
        final String outcome;
        final String screen;
        final String menu;

        Observation(String outcome, String screen, String menu) {
            this.outcome = outcome == null ? "" : outcome;
            this.screen = screen == null ? "" : screen;
            this.menu = menu == null ? "" : menu;
        }
    }

    static final class ResumeState {
        final Set<String> completed;
        final Set<String> interrupted;

        ResumeState(Set<String> completed, Set<String> interrupted) {
            this.completed = Collections.unmodifiableSet(new HashSet<String>(completed));
            this.interrupted = Collections.unmodifiableSet(new HashSet<String>(interrupted));
        }

        static ResumeState empty() {
            return new ResumeState(Collections.<String>emptySet(), Collections.<String>emptySet());
        }
    }
}
