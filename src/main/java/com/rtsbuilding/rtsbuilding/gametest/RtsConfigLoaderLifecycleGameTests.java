package com.rtsbuilding.rtsbuilding.gametest;

import com.electronwill.nightconfig.toml.TomlParser;
import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.common.config.RtsServerConfigRawSnapshot;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.fml.config.ConfigTracker;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.io.StringReader;
import java.util.List;

/**
 * 仅在 rtsbuilding_config_test 专用 namespace 中运行的真实 loader 生命周期回归。
 * 用临时世界配置目录逐次装载同一个生产 spec，验证 correction 前迁移及世界隔离；
 * 不改真实世界文件，finally 恢复当前 GameTest 世界的原配置，不并发其他测试 namespace。
 */
@GameTestHolder("rtsbuilding_config_test")
@PrefixGameTestTemplate(false)
public final class RtsConfigLoaderLifecycleGameTests {
    private static final String FILE_NAME = "rtsbuilding-server.toml";
    private static final String OLD_COMMON = """
            enableSurvivalProgression = true
            maxActionRadiusBlocks = 192
            """;

    private RtsConfigLoaderLifecycleGameTests() {
    }

    @GameTest(template = "gametest/empty", timeoutTicks = 200, batch = "isolated_server_config")
    public static void realLoaderMigratesAndIsolatesSuccessiveWorldConfigurations(GameTestHelper helper)
            throws Exception {
        Path original = helper.getLevel().getServer().getWorldPath(LevelResource.ROOT).resolve("serverconfig");
        Path testRoot = Files.createTempDirectory(original.getParent(), "rts-config-lifecycle-");
        Path active = original;
        try {
            List<Fixture> fixtures = List.of(
                    new Fixture("legacy", """
                            [mining]
                            areaMineMaxVolume = 46656
                            areaMineMaxWidth = 80
                            areaMineMaxHeight = 36
                            areaMineMaxDepth = 36
                            areaMineMaxSize = 36
                            areaDestroyMaxTargets = 98304
                            """, 46656, 80, 36, 36, true, 192),
                    new Fixture("canonical-volume", """
                            [mining]
                            maxSelectionVolume = 50000
                            areaMineMaxWidth = 12
                            areaMineMaxHeight = 13
                            """, 50000, 64, 64, 64, true, 192),
                    new Fixture("fresh", "", 46656, 64, 64, 64, true, 192),
                    new Fixture("explicit-world", """
                            enableSurvivalProgression = false
                            maxActionRadiusBlocks = 256
                            [mining]
                            maxSelectionVolume = 60000
                            maxSelectionSizeX = 96
                            """, 60000, 96, 64, 64, false, 256));
            for (Fixture fixture : fixtures) {
                unload(active);
                active = null;
                Config.endServerConfigRuntime();
                Config.clearRawConfigSnapshots();
                Path next = testRoot.resolve(fixture.name());
                Path configFile = next.resolve(FILE_NAME);
                Files.createDirectories(configFile.getParent());
                Files.writeString(configFile, fixture.raw());
                active = next;
                ConfigTracker.INSTANCE.loadConfigs(ModConfig.Type.SERVER, next);
                // 与 ServerStarting 的顺序相同：SERVER 已经由 loader 加载，COMMON 原文刚被捕获。
                Config.captureRawCommonConfig(new RtsServerConfigRawSnapshot("fixture:common", OLD_COMMON, false));
                Config.consumePendingLegacyServerMigration();
                Config.beginServerConfigRuntime();
                assertValues(helper, fixture);
                var persisted = new TomlParser().parse(new StringReader(Files.readString(configFile)));
                helper.assertTrue(persisted.getInt(List.of("mining", "maxSelectionSizeX")) == fixture.x(),
                        fixture.name() + ": 迁移后的轴长必须真正落盘");
                helper.assertTrue(persisted.getInt(List.of("mining", "maxSelectionVolume")) == fixture.volume(),
                        fixture.name() + ": 迁移后的体积必须真正落盘");
                Config.SERVER_SPEC.save();
                Config.consumePendingLegacyServerMigration();
                assertValues(helper, fixture);
            }
        } finally {
            if (active != null) unload(active);
            Config.clearRawConfigSnapshots();
            ConfigTracker.INSTANCE.loadConfigs(ModConfig.Type.SERVER, original);
            Config.captureRawCommonConfig(new RtsServerConfigRawSnapshot("fixture:restore", "", false));
            Config.consumePendingLegacyServerMigration();
            Config.beginServerConfigRuntime();
        }
        helper.succeed();
    }

    private static void assertValues(GameTestHelper helper, Fixture fixture) {
        var values = Config.currentServerSettings(true);
        helper.assertTrue(values.maxSelectionVolume() == fixture.volume(), fixture.name() + ": volume");
        helper.assertTrue(values.maxSelectionSizeX() == fixture.x(), fixture.name() + ": X");
        helper.assertTrue(values.maxSelectionSizeY() == fixture.y(), fixture.name() + ": Y");
        helper.assertTrue(values.maxSelectionSizeZ() == fixture.z(), fixture.name() + ": Z");
        helper.assertTrue(values.enableSurvivalProgression() == fixture.progression(),
                fixture.name() + ": SERVER 原文优先于旧 COMMON");
        helper.assertTrue(values.maxActionRadiusBlocks() == fixture.radius(), fixture.name() + ": radius");
    }

    private static void unload(Path active) {
        ConfigTracker.INSTANCE.unloadConfigs(ModConfig.Type.SERVER, active);
    }

    private record Fixture(String name, String raw, int volume, int x, int y, int z,
                           boolean progression, int radius) {
    }
}
