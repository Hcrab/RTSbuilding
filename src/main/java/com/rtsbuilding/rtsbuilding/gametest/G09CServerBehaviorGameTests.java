package com.rtsbuilding.rtsbuilding.gametest;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.api.RtsAPI;
import com.rtsbuilding.rtsbuilding.server.service.ServiceRegistry;
import com.rtsbuilding.rtsbuilding.server.service.placement.ConstructionMaterialSources;
import com.rtsbuilding.rtsbuilding.server.storage.resolver.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** G09C 服务端材料来源回归：菜单状态只影响显示，不能改变真实放置来源。 */
@GameTestHolder(RtsbuildingMod.MODID)
@PrefixGameTestTemplate(false)
public final class G09CServerBehaviorGameTests {
    private static final String EMPTY_TEMPLATE = "gametest/empty";

    private G09CServerBehaviorGameTests() {
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 240, batch = "g09c_server")
    public static void materialSourcesIgnoreOpenMenusAndPlaceFromLinkedOrBackpack(
            GameTestHelper helper) {
        BlockPos chestRel = new BlockPos(6, 1, 3);
        BlockPos firstSupportRel = new BlockPos(2, 1, 2);
        BlockPos secondSupportRel = new BlockPos(4, 1, 2);
        helper.setBlock(chestRel, Blocks.CHEST);
        helper.setBlock(firstSupportRel, Blocks.STONE);
        helper.setBlock(secondSupportRel, Blocks.STONE);
        setChestStack(helper, chestRel, new ItemStack(Items.DIRT, 1));

        ServerPlayer player = RtsServerGameTests.startRtsPlayer(helper, GameType.SURVIVAL);
        player.getInventory().setItem(0, new ItemStack(Items.DIRT, 1));
        RtsAPI.get().bindings().linkStorage(player, helper.absolutePos(chestRel),
                RtsLinkedStorageResolver.LINK_MODE_BIDIRECTIONAL);
        RtsStorageSession session = ServiceRegistry.getInstance().session().getIfPresent(player);
        helper.assertTrue(session != null, "真实 RTS 玩家必须有服务端储存会话");

        player.containerMenu = player.inventoryMenu;
        assertSourceCount(helper, player, session, 2L, "背包菜单");
        player.containerMenu = new com.rtsbuilding.rtsbuilding.server.menu.RtsCraftTerminalMenu(
                0, player.getInventory());
        assertSourceCount(helper, player, session, 2L, "RTS 合成终端");
        ChestBlockEntity chest = (ChestBlockEntity) helper.getBlockEntity(chestRel);
        player.containerMenu = ChestMenu.threeRows(0, player.getInventory(), chest);
        assertSourceCount(helper, player, session, 2L, "普通箱子菜单");

        // 留在普通菜单上下文中提交真正的服务端入口；第一格取链接箱，第二格取玩家背包。
        submitPlacement(player, helper.absolutePos(firstSupportRel));
        submitPlacement(player, helper.absolutePos(secondSupportRel));

        helper.succeedWhen(() -> {
            helper.assertBlockPresent(Blocks.DIRT, firstSupportRel.above());
            helper.assertBlockPresent(Blocks.DIRT, secondSupportRel.above());
            helper.assertValueEqual(0L,
                    ConstructionMaterialSources.countItem(player, session, Items.DIRT),
                    "真实放置完成后链接箱与背包都不能保留重复材料");
            RtsServerGameTests.stopPlayers(player);
        });
    }

    private static void submitPlacement(ServerPlayer player, BlockPos support) {
        Vec3 hit = Vec3.atBottomCenterOf(support.above());
        Vec3 origin = player.getEyePosition();
        Vec3 direction = hit.subtract(origin).normalize();
        ServiceRegistry.getInstance().placement().placeSelected(
                player, support, Direction.UP, hit.x, hit.y, hit.z,
                (byte) 0, "", false, false,
                "minecraft:dirt", new ItemStack(Items.DIRT),
                origin.x, origin.y, origin.z,
                direction.x, direction.y, direction.z,
                false, false);
    }

    private static void assertSourceCount(GameTestHelper helper, ServerPlayer player,
            RtsStorageSession session, long expected, String menuName) {
        helper.assertValueEqual(expected,
                ConstructionMaterialSources.countItem(player, session, Items.DIRT),
                menuName + " 不得改变后台材料计数");
    }

    private static void setChestStack(GameTestHelper helper, BlockPos chestRel, ItemStack stack) {
        ChestBlockEntity chest = (ChestBlockEntity) helper.getBlockEntity(chestRel);
        helper.assertTrue(chest != null, "场景必须有真实箱子方块实体");
        chest.setItem(0, stack);
        chest.setChanged();
    }
}
