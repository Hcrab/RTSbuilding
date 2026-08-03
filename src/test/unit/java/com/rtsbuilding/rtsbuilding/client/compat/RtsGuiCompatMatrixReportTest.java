package com.rtsbuilding.rtsbuilding.client.compat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RtsGuiCompatMatrixReportTest {
    @TempDir
    Path tempDir;

    @Test
    void 失败候选可重测且最后一次成功会覆盖旧失败() {
        RtsGuiCompatMatrixReport report = new RtsGuiCompatMatrixReport(tempDir.resolve("matrix.tsv"));
        RtsGuiCompatCandidateCatalog.Candidate candidate = candidate("example:machine", 3);

        report.begin(1, 1, candidate, 3, 120);
        report.result(1, 1, candidate, 3,
                new RtsGuiCompatMatrixReport.Observation("OPEN_STABLE", "NearScreen", "Menu"),
                120, new RtsGuiCompatMatrixReport.Observation("SCREEN_MISSING", "", "Menu"),
                "first attempt");
        assertFalse(report.readResumeState().completed.contains(candidate.key()));
        assertFalse(report.readResumeState().interrupted.contains(candidate.key()));

        report.begin(1, 1, candidate, 3, 120);
        report.result(1, 1, candidate, 3,
                new RtsGuiCompatMatrixReport.Observation("OPEN_STABLE", "NearScreen", "Menu"),
                120, new RtsGuiCompatMatrixReport.Observation("OPEN_STABLE", "FarScreen", "Menu"),
                "retry passed");
        assertTrue(report.readResumeState().completed.contains(candidate.key()));
    }

    @Test
    void 只有BEGIN的候选仍会被识别为上次崩溃点() {
        RtsGuiCompatMatrixReport report = new RtsGuiCompatMatrixReport(tempDir.resolve("interrupted.tsv"));
        RtsGuiCompatCandidateCatalog.Candidate candidate = candidate("example:crashing_machine", 0);
        report.begin(1, 1, candidate, 3, 120);

        assertTrue(report.readResumeState().interrupted.contains(candidate.key()));
        assertFalse(report.readResumeState().completed.contains(candidate.key()));
    }

    @Test
    void 曾开过GUI的候选不能被后一次NO_GUI结果降级洗白() {
        RtsGuiCompatMatrixReport report = new RtsGuiCompatMatrixReport(tempDir.resolve("known-gui.tsv"));
        RtsGuiCompatCandidateCatalog.Candidate candidate = candidate("example:stateful_machine", 0);

        report.begin(1, 1, candidate, 3, 120);
        report.result(1, 1, candidate, 3,
                new RtsGuiCompatMatrixReport.Observation("OPEN_STABLE", "NearScreen", "Menu"),
                120, new RtsGuiCompatMatrixReport.Observation("SCREEN_MISSING", "", "Menu"),
                "far failed");
        report.begin(1, 1, candidate, 3, 120);
        report.result(1, 1, candidate, 3,
                new RtsGuiCompatMatrixReport.Observation("NO_GUI_OR_PREREQUISITE", "", ""),
                120, RtsGuiCompatMatrixReport.Observation.EMPTY,
                "retry lacked prerequisite");

        assertFalse(report.readResumeState().completed.contains(candidate.key()));
        assertFalse(report.readResumeState().interrupted.contains(candidate.key()));

        report.begin(1, 1, candidate, 3, 120);
        report.result(1, 1, candidate, 3,
                new RtsGuiCompatMatrixReport.Observation("OPEN_STABLE", "NearScreen", "Menu"),
                120, new RtsGuiCompatMatrixReport.Observation("OPEN_STABLE", "FarScreen", "Menu"),
                "eventual pass");
        assertTrue(report.readResumeState().completed.contains(candidate.key()));
    }

    private static RtsGuiCompatCandidateCatalog.Candidate candidate(String blockId, int meta) {
        return new RtsGuiCompatCandidateCatalog.Candidate(
                blockId, meta, "example.BlockMachine", true, true);
    }
}
