package com.rtsbuilding.rtsbuilding.server.service.placement;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RtsPlacedBlockRotationContractTest {
    @Test
    void worldArcPayloadCarriesOnlyPositionAxisAndOneStepIntent() throws Exception {
        String payload = source(
                "src/main/java/com/rtsbuilding/rtsbuilding/network/builder/C2SRtsOrientBlockPayload.java");
        String handler = source(
                "src/main/java/com/rtsbuilding/rtsbuilding/network/builder/handler/RtsPlacementControlHandlers.java");
        String helper = source(
                "src/main/java/com/rtsbuilding/rtsbuilding/server/service/placement/RtsPlacementHelper.java");
        String rotationStep = source(
                "src/main/java/com/rtsbuilding/rtsbuilding/common/placement/PlacedBlockRotationStep.java");

        assertTrue(payload.contains("byte axisDirection"));
        assertTrue(payload.contains("byte quarterTurns"));
        assertFalse(payload.contains("IBlockState state"));
        assertTrue(payload.contains("Math.abs((int) quarterTurns) == 1"));
        assertTrue(handler.contains("EnumFacing.byIndex(message.axisDirection())"));
        assertTrue(handler.contains("(int) message.quarterTurns()"));
        assertTrue(helper.contains("PlacedBlockRotationStep.rotate("),
                "客户端圆弧预判和服务端落地必须共用增量旋转器");
        assertTrue(helper.contains("RtsPlacedBlockRotation.applyResolvedState("),
                "共享转换器只表达意图，结构安全仍由服务端统一校验");
        assertTrue(rotationStep.contains("state.getBlock().withRotation(state, rotation)"),
                "1.12 水平旋转必须优先使用方块注册的原生旋转实现");
        assertTrue(rotationStep.contains("findProperty(result, \"half\")"));
        assertTrue(rotationStep.contains("step > 0 ? new String[]{\"top\", \"upper\"}"),
                "楼梯等无垂直 facing 的方块必须由上下手势切换 half");
        assertTrue(rotationStep.contains("result.getBlock() instanceof BlockSlab"));
        assertTrue(rotationStep.contains("findProperty(result, \"attach_face\")"));
    }

    @Test
    void applicationRejectsUnsafeOrUnloadedStatesAndRevalidatesNeighbors() throws Exception {
        String rotation = source(
                "src/main/java/com/rtsbuilding/rtsbuilding/server/service/placement/RtsPlacedBlockRotation.java");
        String implementation = source(
                "src/main/java/com/rtsbuilding/rtsbuilding/server/service/impl/RtsPlacementServiceImpl.java");
        String handler = source(
                "src/main/java/com/rtsbuilding/rtsbuilding/network/builder/handler/RtsPlacementControlHandlers.java");

        assertTrue(handler.contains("player.getServerWorld().addScheduledTask("));
        assertTrue(handler.contains("invoke(placement, \"rotateBlock\""),
                "旧顺时针接口必须保持单位置载荷，不携带客户端方块状态");
        assertTrue(implementation.contains("RtsProgressionManager.canUse(player, RtsFeature.ROTATE_BLOCK)"));
        assertTrue(implementation.contains("registry.session().getIfPresent(player)"));
        assertTrue(implementation.contains(
                "session.mode != com.rtsbuilding.rtsbuilding.common.build.BuilderMode.ROTATE"));
        assertTrue(implementation.contains("player.isSpectator()"));
        assertTrue(implementation.contains("!player.capabilities.allowEdit"));
        assertTrue(implementation.contains("RtsLinkedStorageResolver.canAccessWorldTarget(player, pos)"));
        assertTrue(implementation.contains("RtsClaimProtectionService.canInteractBlock("));
        assertTrue(rotation.contains("!world.isBlockLoaded(pos)"));
        assertTrue(rotation.contains("!world.isBlockLoaded(pos.offset(side))"));
        assertTrue(rotation.contains("current.getBlock() != requested.getBlock()"));
        assertTrue(rotation.contains("!requested.getBlock().canPlaceBlockAt(world, pos)"));
        assertTrue(rotation.contains("world.setBlockState(pos, requested, 3)"));
        assertTrue(rotation.contains("block instanceof BlockBed"));
        assertTrue(rotation.contains("block instanceof BlockDoor"));
        assertTrue(rotation.contains("block instanceof BlockDoublePlant"));
        assertTrue(rotation.contains("block instanceof BlockPistonBase"));
        assertTrue(rotation.contains("state.getValue(BlockPistonBase.EXTENDED)"));
        assertTrue(rotation.contains("ROTATION_BLACKLIST"));
        assertTrue(rotation.contains("current.getBlock() instanceof BlockChest"));
        assertTrue(rotation.contains("hasSameBlockNeighbor(world, pos, current.getBlock())"));
        assertTrue(rotation.contains("after != before"));
        assertTrue(rotation.contains("world.setBlockState(pos, current, 3)"));
    }

    private static String source(String path) throws Exception {
        return Files.readString(Path.of(path));
    }
}
