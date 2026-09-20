package com.rtsbuilding.rtsbuilding.gametest;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.common.config.RtsServerConfigChange;
import com.rtsbuilding.rtsbuilding.common.config.RtsServerConfigUpdateRequest;
import com.rtsbuilding.rtsbuilding.common.config.RtsServerConfigUpdateResult;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * 在真实已加载的 SERVER spec 和服务端线程上验证配置权威链。
 * 只使用隔离 GameTest 世界，所有改动在 finally 还原，不依赖客户端或平行测试模型。
 */
@GameTestHolder(RtsbuildingMod.MODID)
@PrefixGameTestTemplate(false)
public final class RtsServerConfigGameTests {
    private RtsServerConfigGameTests() {
    }

    @GameTest(template = "gametest/empty", timeoutTicks = 120, batch = "server_config")
    public static void readingReloadedRevisionDoesNotConsumeBroadcast(GameTestHelper helper) {
        int previous = Config.areaMineMaxWidth();
        try {
            Config.pollServerConfigRuntimeChange();
            int revision = Config.currentServerSettings(false).revision();
            Config.MAX_SELECTION_SIZE_X.set(previous == 80 ? 81 : 80);
            int changedRevision = Config.currentServerSettings(false).revision();
            helper.assertTrue(changedRevision != revision, "外部有效值改变必须立即更新 revision");
            helper.assertValueEqual(changedRevision, Config.currentServerSettings(false).revision(),
                    "稳定查询不能反复增加 revision");
            helper.assertTrue(Config.pollServerConfigRuntimeChange(),
                    "查询或冲突响应不能吞掉其他在线玩家应收到的广播");
            helper.assertTrue(!Config.pollServerConfigRuntimeChange(),
                    "一次变更只产生一次 tick 广播");
        } finally {
            Config.MAX_SELECTION_SIZE_X.set(previous);
            Config.pollServerConfigRuntimeChange();
        }
        helper.succeed();
    }

    @GameTest(template = "gametest/empty", timeoutTicks = 160, batch = "server_config")
    public static void realPlayersShareRevisionAndPermissionIsRechecked(GameTestHelper helper) {
        ServerPlayer first = RtsServerGameTests.startRtsPlayer(helper, GameType.SURVIVAL);
        ServerPlayer second = RtsServerGameTests.startRtsPlayer(helper, GameType.SURVIVAL);
        var players = helper.getLevel().getServer().getPlayerList();
        int previous = Config.areaMineMaxWidth();
        try {
            // GameTestServer 默认 OP 级别是0；向真实名单授予级别4，不绕过生产权限判断。
            players.getOps().add(new net.minecraft.server.players.ServerOpListEntry(first.getGameProfile(), 4, false));
            players.getOps().add(new net.minecraft.server.players.ServerOpListEntry(second.getGameProfile(), 4, false));
            helper.assertTrue(first.hasPermissions(2) && second.hasPermissions(2), "夹具需要真实OP权限");
            int revision = Config.currentServerSettings(true).revision();
            int changed = previous == 80 ? 81 : 80;
            var applied = Config.applyServerConfig(axisUpdate(revision, changed), first);
            helper.assertTrue(applied.status() == RtsServerConfigUpdateResult.Status.APPLIED,
                    "真实管理员保存必须落盘成功: " + applied.status() + " / " + applied.message());
            var conflict = Config.applyServerConfig(axisUpdate(revision, changed + 1), second);
            helper.assertTrue(conflict.status() == RtsServerConfigUpdateResult.Status.REVISION_CONFLICT,
                    "同一 revision 的第二位管理员不能覆盖先行保存");
            helper.assertValueEqual(changed, Config.areaMineMaxWidth(), "冲突不能改动实际配置");
            var unchanged = Config.applyServerConfig(
                    axisUpdate(applied.view().revision(), changed), first);
            helper.assertTrue(unchanged.status() == RtsServerConfigUpdateResult.Status.NO_CHANGE,
                    "相同值必须返回 NO_CHANGE");
            players.deop(second.getGameProfile());
            var denied = Config.applyServerConfig(
                    axisUpdate(unchanged.view().revision(), changed + 1), second);
            helper.assertTrue(denied.status() == RtsServerConfigUpdateResult.Status.NOT_EDITABLE,
                    "打开设置后撤销权限仍必须阻止提交");
            helper.assertTrue(Config.pollServerConfigRuntimeChange(),
                    "保存后的查询、冲突和权限失败不能消耗广播义务");
            helper.assertTrue(!Config.pollServerConfigRuntimeChange(), "没有后续修改时不重复广播");
        } finally {
            Config.MAX_SELECTION_SIZE_X.set(previous);
            Config.SERVER_SPEC.save();
            Config.pollServerConfigRuntimeChange();
            players.deop(first.getGameProfile());
            players.deop(second.getGameProfile());
            RtsServerGameTests.stopPlayers(first);
            RtsServerGameTests.stopPlayers(second);
        }
        helper.succeed();
    }

    private static RtsServerConfigUpdateRequest axisUpdate(int revision, int value) {
        return new RtsServerConfigUpdateRequest(revision, List.of(new RtsServerConfigChange(
                RtsServerConfigChange.Key.MAX_SELECTION_SIZE_X,
                new RtsServerConfigChange.IntValue(value))));
    }
}
