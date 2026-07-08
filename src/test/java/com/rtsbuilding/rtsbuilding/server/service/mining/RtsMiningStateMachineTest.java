package com.rtsbuilding.rtsbuilding.server.service.mining;

import com.rtsbuilding.rtsbuilding.server.storage.RtsMiningState;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageSession;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

/**
 * 验证当前远程挖掘状态机写入 {@link RtsStorageSession#mining} 的字段契约。
 *
 * <p>单元测试环境不提前触发 Minecraft bootstrap，因此这里用 Mockito 跳过
 * {@link RtsStorageSession} 和 {@link RtsMiningState} 构造函数，再通过反射补上
 * 测试需要的嵌套状态对象。这个测试只覆盖玩家可见行为相关的状态字段：
 * 目标坐标、方向、工具槽、进度和破坏阶段。</p>
 */
class RtsMiningStateMachineTest {

    private static RtsStorageSession createSessionWithoutMinecraftBootstrap() {
        RtsStorageSession session = mock(RtsStorageSession.class);
        attachMiningState(session, mock(RtsMiningState.class));
        return session;
    }

    private static void attachMiningState(RtsStorageSession session, RtsMiningState mining) {
        try {
            Field field = RtsStorageSession.class.getDeclaredField("mining");
            field.setAccessible(true);
            field.set(session, mining);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to attach mining state for unit test", e);
        }
    }

    @Test
    void beginRemoteMiningSetsExpectedSessionFields() {
        RtsStorageSession session = createSessionWithoutMinecraftBootstrap();
        BlockPos pos = new BlockPos(10, 20, 30);

        RtsMiningStateMachine.beginRemoteMining(null, session, pos, Direction.NORTH, 4);

        assertEquals(pos, session.mining.miningPos);
        assertEquals(Direction.NORTH, session.mining.miningFace);
        assertEquals(4, session.mining.miningToolSlot);
        assertEquals(0.0F, session.mining.miningProgress);
        assertEquals(-1, session.mining.miningStage);
    }

    @Test
    void beginRemoteMiningWithNullFaceDefaultsToDown() {
        RtsStorageSession session = createSessionWithoutMinecraftBootstrap();

        RtsMiningStateMachine.beginRemoteMining(null, session, BlockPos.ZERO, null, 0);

        assertEquals(Direction.DOWN, session.mining.miningFace);
    }

    @Test
    void beginRemoteMiningClampsToolSlot() {
        RtsStorageSession session = createSessionWithoutMinecraftBootstrap();

        RtsMiningStateMachine.beginRemoteMining(null, session, BlockPos.ZERO, Direction.UP, 999);

        assertEquals(8, session.mining.miningToolSlot);
    }

    @Test
    void beginRemoteMiningNegativeToolSlotClampsToZero() {
        RtsStorageSession session = createSessionWithoutMinecraftBootstrap();

        RtsMiningStateMachine.beginRemoteMining(null, session, BlockPos.ZERO, Direction.UP, -5);

        assertEquals(0, session.mining.miningToolSlot);
    }

    @Test
    void beginRemoteMiningSamePosUpdatesFaceWithoutClearingProgressTarget() {
        RtsStorageSession session = createSessionWithoutMinecraftBootstrap();
        BlockPos pos = new BlockPos(5, 5, 5);
        session.mining.miningPos = pos;

        RtsMiningStateMachine.beginRemoteMining(null, session, pos, Direction.UP, 0);

        assertEquals(pos, session.mining.miningPos);
        assertEquals(Direction.UP, session.mining.miningFace);
    }
}
