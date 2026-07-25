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
                "src/main/java/com/rtsbuilding/rtsbuilding/network/builder/handler/RtsPlaceHandlers.java");
        String helper = source(
                "src/main/java/com/rtsbuilding/rtsbuilding/server/service/placement/RtsPlacementHelper.java");
        String rotationStep = source(
                "src/main/java/com/rtsbuilding/rtsbuilding/common/placement/PlacedBlockRotationStep.java");

        assertTrue(payload.contains("byte axisDirection"));
        assertTrue(payload.contains("byte quarterTurns"));
        assertFalse(payload.contains("BlockState state"));
        assertTrue(handler.contains("Math.abs(payload.quarterTurns()) == 1"));
        assertTrue(handler.contains("Direction.from3DDataValue(payload.axisDirection())"));
        assertTrue(helper.contains("PlacedBlockRotationStep.rotate("),
                "客户端圆弧预判和服务端落地必须共用增量旋转器");
        assertTrue(helper.contains("RtsPlacedBlockRotation.applyResolvedState("),
                "共享转换器只表达意图，结构安全仍由服务端统一校验");
        assertTrue(rotationStep.contains("state.rotate(rotation)"),
                "水平旋转优先采用方块注册的原生旋转实现");
        assertTrue(rotationStep.contains("BlockStateProperties.HALF"));
        assertTrue(rotationStep.contains("step > 0 ? Half.TOP : Half.BOTTOM"),
                "楼梯等无竖直 facing 的方块必须由上/下手势切换 top/bottom");
        assertTrue(rotationStep.contains("BlockStateProperties.SLAB_TYPE"));
        assertTrue(rotationStep.contains("BlockStateProperties.ATTACH_FACE"));
    }

    @Test
    void applicationRejectsUnsafeOrUnloadedStatesAndRevalidatesNeighbors() throws Exception {
        String rotation = source(
                "src/main/java/com/rtsbuilding/rtsbuilding/server/service/placement/RtsPlacedBlockRotation.java");
        String implementation = source(
                "src/main/java/com/rtsbuilding/rtsbuilding/server/service/impl/RtsPlacementServiceImpl.java");
        String handler = source(
                "src/main/java/com/rtsbuilding/rtsbuilding/network/builder/handler/RtsPlaceHandlers.java");

        assertTrue(handler.contains("context.enqueueWork("));
        assertTrue(handler.contains(
                "placement().rotateBlock(serverPlayer, payload.pos())"),
                "旧顺时针接口应保持单一位置载荷，不再携带废弃的属性选择");
        assertTrue(implementation.contains("RtsProgressionManager.canUse(player, RtsFeature.ROTATE_BLOCK)"));
        assertTrue(implementation.contains("registry.session().getIfPresent(player)"));
        assertTrue(implementation.contains("session.mode != com.rtsbuilding.rtsbuilding.common.build.BuilderMode.ROTATE"));
        assertTrue(implementation.contains("player.isSpectator()"));
        assertTrue(implementation.contains("!player.mayBuild()"));
        assertTrue(implementation.contains("RtsLinkedStorageResolver.canAccessWorldTarget(player, pos)"));
        assertTrue(implementation.contains("RtsClaimProtectionService.canInteractBlock("));
        assertTrue(rotation.contains("level.hasChunkAt(pos)"));
        assertTrue(rotation.contains("level.hasChunkAt(pos.relative(direction))"));
        assertTrue(rotation.contains("Block.updateFromNeighbourShapes(requested, level, pos)"));
        assertTrue(rotation.contains("adjusted.getBlock() != current.getBlock()"));
        assertTrue(rotation.contains("!adjusted.canSurvive(level, pos)"));
        assertTrue(rotation.contains("level.setBlock(pos, adjusted, Block.UPDATE_ALL)"));
        assertTrue(rotation.contains("block instanceof BedBlock"));
        assertTrue(rotation.contains("block instanceof DoorBlock"));
        assertTrue(rotation.contains("ChestType.SINGLE"));
        assertTrue(rotation.contains("block instanceof PistonBaseBlock"));
        assertTrue(rotation.contains("state.getValue(BlockStateProperties.EXTENDED)"));
        assertTrue(rotation.contains("ROTATION_BLACKLIST"));
        assertTrue(rotation.contains("state.is(ROTATION_BLACKLIST)"));
        assertTrue(rotation.contains("switchCreateKineticState(level, pos, adjusted)"));
        assertTrue(rotation.contains("level.getBlockEntity(pos) != blockEntity"));
    }

    private static String source(String path) throws Exception {
        return Files.readString(Path.of(path));
    }
}
