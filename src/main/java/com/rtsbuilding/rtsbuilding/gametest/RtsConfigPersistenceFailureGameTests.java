package com.rtsbuilding.rtsbuilding.gametest;

import com.electronwill.nightconfig.toml.TomlParser;
import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.common.config.RtsServerConfigChange;
import com.rtsbuilding.rtsbuilding.common.config.RtsServerConfigUpdateRequest;
import com.rtsbuilding.rtsbuilding.common.config.RtsServerConfigUpdateResult;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.GameType;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.io.StringReader;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 在真实已加载 spec、真实服务端玩家和文件写入链上注入一次保存故障。
 * 仅专用配置 GameTest namespace 执行；反射只替换测试中的保存端口，finally 原样恢复。
 */
@GameTestHolder("rtsbuilding_config_test")
@PrefixGameTestTemplate(false)
public final class RtsConfigPersistenceFailureGameTests {
    private RtsConfigPersistenceFailureGameTests() {
    }

    @GameTest(template = "gametest/empty", timeoutTicks = 160, batch = "isolated_server_config")
    public static void failedAuthoritativeSaveRestoresMemoryAndDisk(GameTestHelper helper) throws Exception {
        var player = RtsServerGameTests.startRtsPlayer(helper, GameType.SURVIVAL);
        var players = helper.getLevel().getServer().getPlayerList();
        int previous = Config.areaMineMaxWidth();
        Field loadedField = Config.SERVER_SPEC.getClass().getDeclaredField("childConfig");
        loadedField.setAccessible(true);
        CommentedFileConfig original = (CommentedFileConfig) loadedField.get(Config.SERVER_SPEC);
        try {
            players.getOps().add(new net.minecraft.server.players.ServerOpListEntry(player.getGameProfile(), 4, false));
            helper.assertTrue(player.hasPermissions(2), "夹具需要真实OP权限");
            for (boolean failAfterWrite : new boolean[]{false, true}) {
                AtomicBoolean inject = new AtomicBoolean(true);
                CommentedFileConfig intercepted = (CommentedFileConfig) java.lang.reflect.Proxy.newProxyInstance(
                        CommentedFileConfig.class.getClassLoader(), new Class<?>[]{CommentedFileConfig.class},
                        (proxy, method, arguments) -> {
                            if (method.getName().equals("save") && inject.compareAndSet(true, false)) {
                                if (failAfterWrite) original.save();
                                throw new IllegalStateException("injected config save failure");
                            }
                            try {
                                return method.invoke(original, arguments);
                            } catch (java.lang.reflect.InvocationTargetException failure) {
                                throw failure.getCause();
                            }
                        });
                Config.SERVER_SPEC.acceptConfig(intercepted);
                int revision = Config.currentServerSettings(true).revision();
                int changed = previous == 80 ? 81 : 80;
                var request = new RtsServerConfigUpdateRequest(revision, List.of(new RtsServerConfigChange(
                        RtsServerConfigChange.Key.MAX_SELECTION_SIZE_X,
                        new RtsServerConfigChange.IntValue(changed))));
                var result = Config.applyServerConfig(request, player);
                helper.assertTrue(!inject.get(), "必须触发真实 Config.save 链的故障端口: "
                        + result.status() + " / " + result.message());
                helper.assertTrue(result.status() == RtsServerConfigUpdateResult.Status.SAVE_FAILED,
                        "写入前/后失败都不能返回成功 ACK");
                helper.assertTrue(Config.areaMineMaxWidth() == previous, "失败必须还原内存中的有效值");
                var file = original.getNioPath();
                var persisted = new TomlParser().parse(new StringReader(Files.readString(file)));
                helper.assertTrue(persisted.getInt(List.of("mining", "maxSelectionSizeX")) == previous,
                        "包括先写入后失败在内，磁盘最终都必须保留旧值");
                Config.SERVER_SPEC.acceptConfig(original);
            }
        } finally {
            Config.SERVER_SPEC.acceptConfig(original);
            Config.MAX_SELECTION_SIZE_X.set(previous);
            Config.SERVER_SPEC.save();
            Config.pollServerConfigRuntimeChange();
            players.deop(player.getGameProfile());
            RtsServerGameTests.stopPlayers(player);
        }
        helper.succeed();
    }
}
