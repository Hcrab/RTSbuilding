package com.rtsbuilding.rtsbuilding;

import com.rtsbuilding.rtsbuilding.common.mining.MiningLimits;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Forge 原始 TOML 迁移与 NeoForge 保持相同的 canonical/legacy 优先级。 */
class MiningSelectionConfigMigrationTest {
    /** 模拟旧版生成器写出的完整 mining 段，只把 X 轴改成 80。 */
    private static final String LEGACY_FULL_TOML = """
            [mining]
            ultimineMaxBlocks = 256
            areaMineMaxSize = 36
            areaMineMaxVolume = 46656
            areaMineMaxWidth = 80
            areaMineMaxHeight = 36
            areaMineMaxDepth = 36
            areaDestroyMaxTargets = 98304
            """;

    @Test
    void rawTomlKeepsCanonicalVolumeAndOnlyMigratesMissingAxes() {
        MiningSelectionConfigMigration.Result result = MiningSelectionConfigMigration.resolve(
                MiningSelectionConfigMigration.Source.fromRawToml("""
                        [mining]
                        maxSelectionVolume = 50000
                        maxSelectionSizeX = 80
                        areaMineMaxWidth = 12
                        areaMineMaxHeight = 13
                        areaMineMaxDepth = 14
                        """));
        assertEquals(50000, result.volume());
        assertEquals(80, result.sizeX());
        assertEquals(64, result.sizeY());
        assertEquals(64, result.sizeZ());
    }

    @Test
    void freshAndZeroPlaceholderFilesKeepCanonicalAxisDefault() {
        MiningSelectionConfigMigration.Result fresh = MiningSelectionConfigMigration.resolve(
                MiningSelectionConfigMigration.Source.fromRawToml(""));
        assertEquals(64, fresh.sizeX());
        assertEquals(64, fresh.sizeY());
        assertEquals(64, fresh.sizeZ());
        MiningSelectionConfigMigration.Result zero = MiningSelectionConfigMigration.resolve(
                MiningSelectionConfigMigration.Source.fromRawToml("""
                        [mining]
                        areaMineMaxVolume = 0
                        areaMineMaxWidth = 0
                        areaMineMaxHeight = 0
                        areaMineMaxDepth = 0
                        areaMineMaxSize = 0
                        areaDestroyMaxTargets = 0
                        """));
        assertEquals(64, zero.sizeX());
        assertEquals(64, zero.sizeY());
        assertEquals(64, zero.sizeZ());
    }

    @Test
    void oldTomlUsesIndependentLegacyAxesAndSizeFallback() {
        MiningSelectionConfigMigration.Result result = MiningSelectionConfigMigration.resolve(
                MiningSelectionConfigMigration.Source.fromRawToml("""
                        [mining]
                        areaMineMaxWidth = 48
                        areaMineMaxSize = 20
                        """));
        assertEquals(MiningLimits.DEFAULT_VOLUME, result.volume());
        assertEquals(48, result.sizeX());
        assertEquals(20, result.sizeY());
        assertEquals(20, result.sizeZ());
    }

    @Test
    void completeLegacyFixtureKeepsOnlyChangedAxisAndExistingVolume() {
        MiningSelectionConfigMigration.Result result = MiningSelectionConfigMigration.resolve(
                MiningSelectionConfigMigration.Source.fromRawToml(LEGACY_FULL_TOML));
        assertEquals(46_656, result.volume());
        assertEquals(80, result.sizeX());
        assertEquals(36, result.sizeY());
        assertEquals(36, result.sizeZ());
    }

    @Test
    void targetOnlyCountWithZeroLegacyPlaceholdersKeepsNewAxes() {
        MiningSelectionConfigMigration.Result result = MiningSelectionConfigMigration.resolve(
                MiningSelectionConfigMigration.Source.fromRawToml("""
                        [mining]
                        areaMineMaxVolume = 0
                        areaMineMaxWidth = 0
                        areaMineMaxSize = 0
                        areaDestroyMaxTargets = 99
                        """));
        assertEquals(99, result.volume());
        assertEquals(64, result.sizeX());
        assertEquals(64, result.sizeY());
        assertEquals(64, result.sizeZ());
    }

    @Test
    void generatedFixtureIsCapturedBeforeFrameworkAddsCanonicalDefaults() throws Exception {
        String raw = new String(getClass().getResourceAsStream("/fixtures/config/rtsbuilding-server-old-axis.toml").readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        String corrected = raw + "\n[mining]\nmaxSelectionSizeX = 64\n";
        MiningSelectionConfigMigration.Result result = MiningSelectionConfigMigration.resolve(MiningSelectionConfigMigration.Source.fromRawToml(raw));
        MiningSelectionConfigMigration.Result correctedResult = MiningSelectionConfigMigration.resolve(MiningSelectionConfigMigration.Source.fromRawToml(corrected));
        assertEquals(80, result.sizeX()); assertEquals(64, correctedResult.sizeX());
        assertTrue(raw.contains("areaMineMaxWidth = 80")); assertFalse(raw.equals(corrected));
    }

    @Test
    void generatedCommonFixtureContainsAllMigratedWorldRules() throws Exception {
        String raw = new String(getClass().getResourceAsStream("/fixtures/config/rtsbuilding-common-old-generated.toml").readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(raw.contains("enableSurvivalProgression = true")); assertTrue(raw.contains("maxActionRadiusBlocks = 192"));
    }

    @Test
    void canonicalVolumeOnlyFixtureAddsMissingIndependentAxes() throws Exception {
        String raw = new String(getClass().getResourceAsStream(
                "/fixtures/config/rtsbuilding-server-canonical-volume-only.toml").readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8);
        MiningSelectionConfigMigration.Result result = MiningSelectionConfigMigration.resolve(
                MiningSelectionConfigMigration.Source.fromRawToml(raw));
        assertEquals(50_000, result.volume());
        assertEquals(64, result.sizeX());
        assertEquals(64, result.sizeY());
        assertEquals(64, result.sizeZ());
        assertTrue(raw.contains("maxSelectionVolume = 50000"));
    }
}
