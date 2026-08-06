package com.rtsbuilding.rtsbuilding.gametest;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.server.service.ServiceRegistry;
import com.rtsbuilding.rtsbuilding.server.storage.model.LinkedStorageRef;
import com.rtsbuilding.rtsbuilding.server.storage.resolver.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** 批量链接必须由服务端重新发现端点，并保持重复提交幂等。 */
@GameTestHolder(RtsbuildingMod.MODID)
@PrefixGameTestTemplate(false)
public final class RtsBatchStorageGameTests {
    private static final String EMPTY_TEMPLATE = "gametest/empty";

    private RtsBatchStorageGameTests() {
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 80)
    public static void selectionLinksLoadedContainersAndIsIdempotent(GameTestHelper helper) {
        BlockPos chest = new BlockPos(3, 1, 3);
        BlockPos barrel = new BlockPos(7, 1, 6);
        BlockPos outside = new BlockPos(12, 1, 12);
        helper.setBlock(chest, Blocks.CHEST);
        helper.setBlock(barrel, Blocks.BARREL);
        helper.setBlock(outside, Blocks.CHEST);

        ServerPlayer player = RtsServerGameTests.startRtsPlayer(helper, GameType.CREATIVE);
        try {
            BlockPos first = helper.absolutePos(new BlockPos(2, 0, 2));
            BlockPos second = helper.absolutePos(new BlockPos(8, 3, 8));
            ServiceRegistry.getInstance().binding().linkStoragesInSelection(
                    player, first, second, RtsLinkedStorageResolver.LINK_MODE_BIDIRECTIONAL);

            RtsStorageSession session = ServiceRegistry.getInstance().session().getOrCreate(player);
            LinkedStorageRef chestRef = new LinkedStorageRef(
                    player.serverLevel().dimension(), helper.absolutePos(chest));
            LinkedStorageRef barrelRef = new LinkedStorageRef(
                    player.serverLevel().dimension(), helper.absolutePos(barrel));
            LinkedStorageRef outsideRef = new LinkedStorageRef(
                    player.serverLevel().dimension(), helper.absolutePos(outside));
            helper.assertValueEqual(2, session.linkedStorageInfo.size(),
                    "选区内两个储存端点都应被链接");
            helper.assertTrue(session.linkedStorageInfo.contains(chestRef),
                    "选区内箱子应被链接");
            helper.assertTrue(session.linkedStorageInfo.contains(barrelRef),
                    "选区内木桶应被链接");
            helper.assertTrue(!session.linkedStorageInfo.contains(outsideRef),
                    "选区外储存不能被客户端框选意图带入");

            ServiceRegistry.getInstance().binding().linkStoragesInSelection(
                    player, second, first, RtsLinkedStorageResolver.LINK_MODE_BIDIRECTIONAL);
            helper.assertValueEqual(2, session.linkedStorageInfo.size(),
                    "重复批量提交必须幂等，不能像单点切换那样反向解绑");
            helper.succeed();
        } finally {
            RtsServerGameTests.stopPlayers(player);
        }
    }
}
