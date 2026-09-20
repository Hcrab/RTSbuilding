package com.rtsbuilding.rtsbuilding;

import com.rtsbuilding.rtsbuilding.common.mining.MiningLimits;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 旧挖掘配置迁移优先级必须稳定且不会因重复重载改变结果。 */
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
    void freshConfigUsesSharedDefaultVolume() {
        assertEquals(MiningLimits.DEFAULT_VOLUME,
                MiningSelectionConfigMigration.resolve(0, 0, 0, 0, 0, 0, 0));
        MiningSelectionConfigMigration.Result result = MiningSelectionConfigMigration.resolve(
                MiningSelectionConfigMigration.Source.values(0, 0, 0, 0, 0, 0, 0, 0, 0, 0));
        assertEquals(64, result.sizeX());
        assertEquals(64, result.sizeY());
        assertEquals(64, result.sizeZ());
    }

    @Test
    void zeroPlaceholdersAndTargetsOnlyDoNotReintroduceLegacyAxisDefault() {
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
        MiningSelectionConfigMigration.Result targets = MiningSelectionConfigMigration.resolve(
                MiningSelectionConfigMigration.Source.fromRawToml("""
                        [mining]
                        areaDestroyMaxTargets = 99
                        """));
        assertEquals(99, targets.volume());
        assertEquals(64, targets.sizeX());
        assertEquals(64, targets.sizeY());
        assertEquals(64, targets.sizeZ());
    }

    @Test
    void canonicalValueWinsOverEveryLegacyKey() {
        assertEquals(12_345,
                MiningSelectionConfigMigration.resolve(12_345, 60, 10, 10, 10, 20, 99));
    }

    @Test
    void legacyVolumeWinsOverAxesAndTargetOnly() {
        assertEquals(60,
                MiningSelectionConfigMigration.resolve(0, 60, 10, 10, 10, 20, 99));
    }

    @Test
    void legacyAxesRemainIndependentAndDoNotRewriteVolume() {
        MiningSelectionConfigMigration.Result result = MiningSelectionConfigMigration.resolve(
                MiningSelectionConfigMigration.Source.values(0, 0, 0, 0, 0, 48, 0, 0, 0, 0));
        assertEquals(MiningLimits.DEFAULT_VOLUME, result.volume());
        assertEquals(48, result.sizeX());
        assertEquals(36, result.sizeY());
        assertEquals(36, result.sizeZ());
        assertEquals(48, MiningSelectionConfigMigration.resolve(
                MiningSelectionConfigMigration.Source.values(0, 0, 0, 0, 0, 48, 0, 0, 2, 0)).sizeX());
        MiningSelectionConfigMigration.Result independent = MiningSelectionConfigMigration.resolve(
                MiningSelectionConfigMigration.Source.values(0, 0, 0, 0, 0, 3, 4, 5, 20, 99));
        assertEquals(MiningLimits.DEFAULT_VOLUME, independent.volume());
        assertEquals(3, independent.sizeX());
        assertEquals(4, independent.sizeY());
        assertEquals(5, independent.sizeZ());
        assertEquals(MiningLimits.DEFAULT_VOLUME,
                MiningSelectionConfigMigration.resolve(0, 0, 0, 0, 0, 2, 99));
        assertEquals(99,
                MiningSelectionConfigMigration.resolve(0, 0, 0, 0, 0, 0, 99));
    }

    @Test
    void oversizedLegacyValuesClampToCommonMaximum() {
        assertEquals(MiningLimits.DEFAULT_VOLUME,
                MiningSelectionConfigMigration.resolve(0, 0, 256, 256, 256, 0, 0));
        assertEquals(MiningLimits.MAX_VOLUME,
                MiningSelectionConfigMigration.resolve(300_000, 0, 0, 0, 0, 0, 0));
    }

    @Test
    void migrationSentinelIsIdempotentAfterCanonicalWrite() {
        assertTrue(MiningSelectionConfigMigration.needsMigration(0));
        assertFalse(MiningSelectionConfigMigration.needsMigration(46_656));
        int migrated = MiningSelectionConfigMigration.resolve(0, 0, 12, 8, 4, 2, 1);
        assertEquals(migrated,
                MiningSelectionConfigMigration.resolve(migrated, 1, 1, 1, 1, 1, 1));
    }

    @Test
    void rawTomlPresenceKeepsCanonicalVolumeAndIndependentAxes() {
        String toml = """
                [mining]
                maxSelectionVolume = 50000
                maxSelectionSizeX = 80
                areaMineMaxWidth = 12
                areaMineMaxHeight = 13
                areaMineMaxDepth = 14
                """;
        MiningSelectionConfigMigration.Result result = MiningSelectionConfigMigration.resolve(
                MiningSelectionConfigMigration.Source.fromRawToml(toml));
        assertEquals(50000, result.volume());
        assertEquals(80, result.sizeX());
        assertEquals(64, result.sizeY());
        assertEquals(64, result.sizeZ());
    }

    @Test
    void oldTomlUsesLegacyAxisDefaultsWithoutSizeCube() {
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
        String raw = new String(getClass().getResourceAsStream(
                "/fixtures/config/rtsbuilding-server-old-axis.toml").readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8);
        String frameworkCorrected = raw + "\n[mining]\nmaxSelectionSizeX = 64\n";
        MiningSelectionConfigMigration.Result result = MiningSelectionConfigMigration.resolve(
                MiningSelectionConfigMigration.Source.fromRawToml(raw));
        MiningSelectionConfigMigration.Result correctedResult = MiningSelectionConfigMigration.resolve(
                MiningSelectionConfigMigration.Source.fromRawToml(frameworkCorrected));
        assertEquals(80, result.sizeX());
        assertEquals(64, correctedResult.sizeX());
        assertTrue(raw.contains("areaMineMaxWidth = 80"));
        assertFalse(raw.equals(frameworkCorrected));
    }

    @Test
    void generatedCommonFixtureKeepsWorldRuleBeforeServerMigration() throws Exception {
        String raw = new String(getClass().getResourceAsStream(
                "/fixtures/config/rtsbuilding-common-old-generated.toml").readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(raw.contains("enableSurvivalProgression = true"));
        assertTrue(raw.contains("maxActionRadiusBlocks = 192"));
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
