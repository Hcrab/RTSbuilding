package com.rtsbuilding.rtsbuilding.gametest;

import com.mojang.authlib.GameProfile;
import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.forgecompat.fml.ModList;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsInteractPayload;
import com.rtsbuilding.rtsbuilding.server.api.RtsAPI;
import com.rtsbuilding.rtsbuilding.server.camera.RtsCameraManager;
import com.rtsbuilding.rtsbuilding.server.service.RtsSessionService;
import com.rtsbuilding.rtsbuilding.server.service.mining.RtsMiningStateMachine;
import com.rtsbuilding.rtsbuilding.server.service.mining.RtsMiningValidator;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageSession;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Integrated Dynamics 的真实模组兼容烟测。
 *
 * <p>这些测试只在显式开启时运行，用于验证 issue #96 里的玩家路径：
 * RTS 远程右键 ID 方块不应踢出菜单/崩溃，RTS 挖 ID cable 应产生正常掉落。</p>
 */
@GameTestHolder(RtsbuildingMod.MODID)
@PrefixGameTestTemplate(false)
public final class RtsIntegratedDynamicsCompatGameTests {
    private static final String EMPTY_TEMPLATE = "gametest/empty";
    private static final String ID_MOD = "integrateddynamics";

    private RtsIntegratedDynamicsCompatGameTests() {
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 120)
    public static void rtsEmptyHandRightClickIntegratedDynamicsMachineDoesNotCloseMenu(GameTestHelper helper) {
        if (skipUnlessEnabled(helper)) {
            return;
        }
        requireMod(helper, ID_MOD);

        BlockPos machineRel = new BlockPos(3, 1, 3);
        setBlockById(helper, machineRel, "integrateddynamics:logic_programmer");
        ServerPlayer player = startRtsPlayer(helper);
        AbstractContainerMenu before = player.containerMenu;

        BlockPos machineAbs = helper.absolutePos(machineRel);
        Vec3 hit = Vec3.atCenterOf(machineAbs);
        Vec3 rayOrigin = player.getEyePosition();
        Vec3 rayDir = hit.subtract(rayOrigin).normalize();

        RtsAPI.get().interaction().interactTarget(
                player,
                C2SRtsInteractPayload.NO_ENTITY,
                machineAbs,
                Direction.UP,
                hit.x,
                hit.y,
                hit.z,
                C2SRtsInteractPayload.SOURCE_EMPTY_HAND,
                (byte) 0,
                "",
                rayOrigin.x,
                rayOrigin.y,
                rayOrigin.z,
                rayDir.x,
                rayDir.y,
                rayDir.z);

        helper.assertTrue(player.containerMenu != before,
                "RTS 远程右键 Integrated Dynamics 机器后应该保持玩家在线并打开/切换菜单");
        stopPlayer(player);
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 260)
    public static void rtsMiningIntegratedDynamicsCableProducesDrop(GameTestHelper helper) {
        if (skipUnlessEnabled(helper)) {
            return;
        }
        requireMod(helper, ID_MOD);

        ServerPlayer player = startRtsPlayer(helper);
        BlockPos cableRel = new BlockPos(3, 1, 3);
        placeBlockByIdAsPlayer(helper, cableRel, "integrateddynamics:cable", player);
        player.getInventory().setItem(0, new ItemStack(Items.NETHERITE_PICKAXE));

        BlockPos cableAbs = helper.absolutePos(cableRel);
        RtsAPI.get().mining().mine(player, cableAbs, Direction.UP, true, (byte) 0,
                "", ItemStack.EMPTY, false, false);

        helper.succeedWhen(() -> {
            RtsStorageSession session = RtsSessionService.getIfPresent(player);
            helper.assertTrue(session != null, "RTS session should still exist after mining ID cable");
            if (session != null) {
                RtsMiningStateMachine.tickActiveMining(player, session);
            }
            helper.assertTrue(helper.getBlockState(cableRel).isAir(),
                    "RTS 挖掘完成后 Integrated Dynamics cable 应该被破坏; "
                            + describeMiningState(helper, player, session, cableRel, cableAbs));
            helper.assertTrue(hasDroppedOrCollectedItem(helper, player, cableAbs, "integrateddynamics:cable"),
                    "RTS 挖 Integrated Dynamics cable 应该产生 cable 掉落物");
            stopPlayer(player);
            helper.succeed();
        });
    }

    private static boolean skipUnlessEnabled(GameTestHelper helper) {
        if (flagEnabled(System.getProperty("rtsbuilding.integratedDynamicsGameTests"))
                || flagEnabled(System.getenv("RTSBUILDING_INTEGRATED_DYNAMICS_GAMETESTS"))) {
            return false;
        }
        helper.succeed();
        return true;
    }

    private static boolean flagEnabled(String value) {
        if (value == null) {
            return false;
        }
        String normalized = value.trim().toLowerCase(java.util.Locale.ROOT);
        return normalized.equals("1") || normalized.equals("true") || normalized.equals("yes")
                || normalized.equals("on");
    }

    private static void requireMod(GameTestHelper helper, String modId) {
        helper.assertTrue(ModList.get().isLoaded(modId), "GameTest 需要加载真实模组 " + modId);
    }

    private static ServerPlayer startRtsPlayer(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.get(helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "rts-id-compat"));
        Vec3 playerPos = helper.absoluteVec(new Vec3(3.5D, 2.0D, 5.5D));
        player.moveTo(playerPos.x, playerPos.y, playerPos.z, 180.0F, 0.0F);
        player.setGameMode(GameType.SURVIVAL);
        RtsCameraManager.start(player);
        helper.assertTrue(RtsCameraManager.isActive(player), "GameTest 玩家应该能进入 RTS 模式");
        helper.assertTrue(RtsSessionService.getIfPresent(player) != null, "RTS session should be initialized");
        return player;
    }

    private static void stopPlayer(ServerPlayer player) {
        RtsCameraManager.stopIfActive(player);
    }

    private static void setBlockById(GameTestHelper helper, BlockPos rel, String idText) {
        Block block = BuiltInRegistries.BLOCK.get(new ResourceLocation(idText));
        helper.assertTrue(block != Blocks.AIR, "测试方块必须存在: " + idText);
        helper.setBlock(rel, block.defaultBlockState());
    }

    private static void placeBlockByIdAsPlayer(GameTestHelper helper, BlockPos rel, String idText,
            ServerPlayer player) {
        Block block = BuiltInRegistries.BLOCK.get(new ResourceLocation(idText));
        helper.assertTrue(block != Blocks.AIR, "测试方块必须存在: " + idText);
        helper.setBlock(rel, block.defaultBlockState());
        BlockPos absPos = helper.absolutePos(rel);
        BlockState state = helper.getLevel().getBlockState(absPos);
        block.setPlacedBy(helper.getLevel(), absPos, state, player, new ItemStack(block.asItem()));
    }

    private static boolean hasDroppedOrCollectedItem(GameTestHelper helper, ServerPlayer player, BlockPos absPos,
            String expectedItemId) {
        Item expected = BuiltInRegistries.ITEM.get(new ResourceLocation(expectedItemId));
        if (expected == Items.AIR) {
            return false;
        }
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (player.getInventory().getItem(slot).is(expected)) {
                return true;
            }
        }
        AABB box = new AABB(absPos).inflate(8.0D);
        List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, box);
        for (ItemEntity drop : drops) {
            if (drop.getItem().is(expected)) {
                return true;
            }
        }
        return false;
    }

    private static String describeMiningState(GameTestHelper helper, ServerPlayer player,
            RtsStorageSession session, BlockPos relPos, BlockPos absPos) {
        BlockState state = helper.getBlockState(relPos);
        float destroySpeed = state.getDestroySpeed(helper.getLevel(), absPos);
        float step = session == null ? -1.0F : RtsMiningStateMachine.computeRemoteDestroyStep(
                player,
                state,
                absPos,
                session.mining.miningToolSlot,
                session.mining.miningToolLease.stack(),
                session.mining.miningSelectedToolRequested);
        ItemStack activeTool = session == null ? ItemStack.EMPTY : RtsMiningValidator.activeMiningTool(player, session);
        ItemStack hotbarTool = player.getInventory().getItem(0);
        return "state=" + state
                + ", destroySpeed=" + destroySpeed
                + ", step=" + step
                + ", miningPos=" + (session == null ? "null" : session.mining.miningPos)
                + ", progress=" + (session == null ? -1.0F : session.mining.miningProgress)
                + ", stage=" + (session == null ? -1 : session.mining.miningStage)
                + ", selectedToolRequested=" + (session != null && session.mining.miningSelectedToolRequested)
                + ", leaseEmpty=" + (session == null || session.mining.miningToolLease.isEmpty())
                + ", activeTool=" + activeTool
                + ", hotbar0=" + hotbarTool;
    }
}
