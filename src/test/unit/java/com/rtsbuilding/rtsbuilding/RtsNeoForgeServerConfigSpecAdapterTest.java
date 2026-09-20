package com.rtsbuilding.rtsbuilding;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.toml.TomlParser;
import com.rtsbuilding.rtsbuilding.common.config.RtsServerConfigRawSnapshot;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 用真实 NeoForge ModConfigSpec 走适配器的 correction 边界；不启动 Minecraft。
 * 快照必须发生在 loader correction 前，之后才允许 canonical 默认值补齐。
 */
class RtsNeoForgeServerConfigSpecAdapterTest {
    @Test
    void capturesLegacyTomlBeforeCorrectionAndEveryLaterLoad() throws IOException {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.defineInRange("mining.maxSelectionVolume", 46_656, 1, 262_144);
        builder.defineInRange("mining.maxSelectionSizeX", 64, 1, Integer.MAX_VALUE);
        builder.defineInRange("mining.maxSelectionSizeY", 64, 1, Integer.MAX_VALUE);
        builder.defineInRange("mining.maxSelectionSizeZ", 64, 1, Integer.MAX_VALUE);
        ModConfigSpec spec = builder.build();
        List<RtsServerConfigRawSnapshot> captured = new ArrayList<>();
        RtsNeoForgeServerConfigSpecAdapter adapter =
                new RtsNeoForgeServerConfigSpecAdapter(spec, captured::add);

        CommentedConfig legacy = parse(fixture("/fixtures/config/rtsbuilding-server-old-axis.toml"));
        adapter.isCorrect(legacy);
        assertEquals(1, captured.size());
        assertTrue(captured.get(0).contents().contains("areaMineMaxWidth = 80"));
        MiningSelectionConfigMigration.Result legacyValues = MiningSelectionConfigMigration.resolve(
                MiningSelectionConfigMigration.Source.fromRawToml(captured.get(0).contents()));
        assertEquals(46_656, legacyValues.volume());
        assertEquals(80, legacyValues.sizeX());
        assertEquals(36, legacyValues.sizeY());
        assertEquals(36, legacyValues.sizeZ());
        adapter.correct(legacy);
        assertEquals(64, legacy.getInt(List.of("mining", "maxSelectionSizeX")));

        CommentedConfig canonicalVolumeOnly = parse("""
                [mining]
                maxSelectionVolume = 50000
                """);
        adapter.isCorrect(canonicalVolumeOnly);
        adapter.correct(canonicalVolumeOnly);
        assertEquals(2, captured.size());
        assertTrue(captured.get(1).contents().contains("maxSelectionVolume = 50000"));
        assertEquals(50_000, canonicalVolumeOnly.getInt(List.of("mining", "maxSelectionVolume")));
        assertEquals(64, canonicalVolumeOnly.getInt(List.of("mining", "maxSelectionSizeX")));
        assertEquals(64, canonicalVolumeOnly.getInt(List.of("mining", "maxSelectionSizeY")));
        assertEquals(64, canonicalVolumeOnly.getInt(List.of("mining", "maxSelectionSizeZ")));

        CommentedConfig fresh = parse("");
        adapter.isCorrect(fresh);
        adapter.correct(fresh);
        assertEquals(3, captured.size());
        MiningSelectionConfigMigration.Result freshValues = MiningSelectionConfigMigration.resolve(
                MiningSelectionConfigMigration.Source.fromRawToml(captured.get(2).contents()));
        assertEquals(46_656, freshValues.volume());
        assertEquals(64, freshValues.sizeX());
        assertEquals(64, freshValues.sizeY());
        assertEquals(64, freshValues.sizeZ());
        assertEquals(46_656, fresh.getInt(List.of("mining", "maxSelectionVolume")));
        assertEquals(64, fresh.getInt(List.of("mining", "maxSelectionSizeX")));
        assertEquals(64, fresh.getInt(List.of("mining", "maxSelectionSizeY")));
        assertEquals(64, fresh.getInt(List.of("mining", "maxSelectionSizeZ")));
    }

    private static CommentedConfig parse(String raw) {
        return new TomlParser().parse(new StringReader(raw));
    }

    private static String fixture(String resource) throws IOException {
        try (var stream = RtsNeoForgeServerConfigSpecAdapterTest.class.getResourceAsStream(resource)) {
            if (stream == null) throw new IOException("missing fixture: " + resource);
            return new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }
}
