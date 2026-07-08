package com.rtsbuilding.rtsbuilding.server.service.mining;

import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageSession;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

/**
 * 验证当前远程挖掘状态机写入 {@link RtsStorageSession} 的字段契约。
 *
 * <p>B2/pipeline 结构已经不再使用独立的 {@code RtsMiningState} 子对象，
 * 因此测试只覆盖对玩家可见行为有意义的会话字段：目标坐标、方向、工具槽、
 * 进度和破坏阶段。这里用 Mockito 跳过 {@code RtsStorageSession} 构造函数，
 * 避免单元测试环境提前触发 {@code ItemStack.EMPTY} 的 Minecraft bootstrap。</p>
 */
class RtsMiningStateMachineTest {

    private static RtsStorageSession createSessionWithoutMinecraftBootstrap() {
        return mock(RtsStorageSession.class);
    }

    @Test
    void beginRemoteMiningSetsExpectedSessionFields() {
        RtsStorageSession session = createSessionWithoutMinecraftBootstrap();
        BlockPos pos = new BlockPos(10, 20, 30);

        RtsMiningStateMachine.beginRemoteMining(null, session, pos, Direction.NORTH, 4);

        assertEquals(pos, session.miningPos);
        assertEquals(Direction.NORTH, session.miningFace);
        assertEquals(4, session.miningToolSlot);
        assertEquals(0.0F, session.miningProgress);
        assertEquals(-1, session.miningStage);
    }

    @Test
    void beginRemoteMiningWithNullFaceDefaultsToDown() {
        RtsStorageSession session = createSessionWithoutMinecraftBootstrap();

        RtsMiningStateMachine.beginRemoteMining(null, session, BlockPos.ZERO, null, 0);

        assertEquals(Direction.DOWN, session.miningFace);
    }

    @Test
    void beginRemoteMiningClampsToolSlot() {
        RtsStorageSession session = createSessionWithoutMinecraftBootstrap();

        RtsMiningStateMachine.beginRemoteMining(null, session, BlockPos.ZERO, Direction.UP, 999);

        assertEquals(8, session.miningToolSlot);
    }

    @Test
    void beginRemoteMiningNegativeToolSlotClampsToZero() {
        RtsStorageSession session = createSessionWithoutMinecraftBootstrap();

        RtsMiningStateMachine.beginRemoteMining(null, session, BlockPos.ZERO, Direction.UP, -5);

        assertEquals(0, session.miningToolSlot);
    }

    @Test
    void beginRemoteMiningSamePosUpdatesFaceWithoutClearingProgressTarget() {
        RtsStorageSession session = createSessionWithoutMinecraftBootstrap();
        BlockPos pos = new BlockPos(5, 5, 5);
        session.miningPos = pos;

        RtsMiningStateMachine.beginRemoteMining(null, session, pos, Direction.UP, 0);

        assertEquals(pos, session.miningPos);
        assertEquals(Direction.UP, session.miningFace);
    }
}
