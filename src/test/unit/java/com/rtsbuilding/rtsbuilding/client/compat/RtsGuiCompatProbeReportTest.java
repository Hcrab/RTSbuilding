package com.rtsbuilding.rtsbuilding.client.compat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RtsGuiCompatProbeReportTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void appendsEvidenceAndResumesOnlyMatchingBaseline() throws Exception {
        Path reportPath = this.temporaryDirectory.resolve("results.tsv");
        RtsGuiCompatCase guiCase = new RtsGuiCompatCase(
                "vanilla_chest_far", "minecraft:chest", 24, "VANILLA_INTERACTION",
                "vanilla_chest", 40, "", "UP", 0.0D, 0.0D, 0.0D,
                ".*ChestMenu", ".*ContainerScreen");
        RtsGuiCompatProbeReport report = new RtsGuiCompatProbeReport(
                reportPath, "atm10-p0", "abc123", "manifest-1");

        report.append(10L, guiCase, "run-finish", "PASS",
                "example.Screen", "Chest", "example.Menu", 4, "ok");
        report.markCompleted(2, guiCase, "PASS");

        String tsv = Files.readString(reportPath);
        assertTrue(tsv.contains("suiteId\tcaseId"));
        assertTrue(tsv.contains("atm10-p0\tvanilla_chest_far"));
        assertEquals(3, report.resumeIndex(10));

        RtsGuiCompatProbeReport differentBaseline = new RtsGuiCompatProbeReport(
                reportPath, "atm10-p0", "different", "manifest-1");
        assertEquals(0, differentBaseline.resumeIndex(10));
    }
}
