package com.rtsbuilding.rtsbuilding.server.service.mining;

import com.rtsbuilding.rtsbuilding.server.storage.state.RtsMiningState;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link RtsMiningStateMachine#beginRemoteMining}.
 *
 * <p>{@link RtsStorageSession#RtsStorageSession()} and
 * {@link RtsMiningState}'s field initializer both reach {@code ItemStack.EMPTY}
 * (via {@code quickSlotPreviews} and {@code miningToolLease}), which requires
 * a bootstrapped Minecraft environment not available in unit tests.
 *
 * <p>To work around this, we use Mockito mocks + reflection to create a
 * {@link RtsStorageSession} without invoking its constructor, and manually
 * wire the {@code mining} field. {@code beginRemoteMining} only sets fields on
 * the mining state and never calls {@code RtsToolLease.empty()}, so it is
 * safe to test this way.
 *
 * <p>{@code resetMiningState} cannot be tested because it unconditionally
 * calls {@code RtsToolLease.empty()} which triggers
 * {@code ItemStack.EMPTY}.
 */
class RtsMiningStateMachineTest {

    /**
     * Creates a mock {@link RtsStorageSession} with a real (mocked) mining
     * state wired into the {@code mining} final field. The mock session
     * constructor never runs, avoiding {@code ItemStack.EMPTY}.
     */
    private static RtsStorageSession createMockSession() {
        RtsStorageSession session = mock(RtsStorageSession.class);
        RtsMiningState mining = mock(RtsMiningState.class);
        try {
            Field miningField = RtsStorageSession.class.getDeclaredField("mining");
            miningField.setAccessible(true);
            miningField.set(session, mining);
        } catch (Exception e) {
            throw new RuntimeException("Failed to wire mining field", e);
        }
        return session;
    }

    // ======================================================================
    //  beginRemoteMining (uses mock session)
    // ======================================================================

    @Test
    void beginRemoteMiningSetsExpectedFields() {
        RtsStorageSession s = createMockSession();
        BlockPos pos = new BlockPos(10, 20, 30);

        RtsMiningStateMachine.beginRemoteMining(null, s, pos, Direction.NORTH, 4);

        assertEquals(pos, s.mining.miningPos);
        assertEquals(Direction.NORTH, s.mining.miningFace);
        assertEquals(4, s.mining.miningToolSlot);
        assertEquals(0.0F, s.mining.miningProgress);
        assertEquals(-1, s.mining.miningStage);
    }

    @Test
    void beginRemoteMiningWithNullFaceDefaultsToDown() {
        RtsStorageSession s = createMockSession();
        RtsMiningStateMachine.beginRemoteMining(null, s, BlockPos.ZERO, null, 0);
        assertEquals(Direction.DOWN, s.mining.miningFace);
    }

    @Test
    void beginRemoteMiningClampsToolSlot() {
        RtsStorageSession s = createMockSession();
        RtsMiningStateMachine.beginRemoteMining(null, s, BlockPos.ZERO, Direction.UP, 999);
        assertEquals(8, s.mining.miningToolSlot);
    }

    @Test
    void beginRemoteMiningNegativeToolSlotClampsToZero() {
        RtsStorageSession s = createMockSession();
        RtsMiningStateMachine.beginRemoteMining(null, s, BlockPos.ZERO, Direction.UP, -5);
        assertEquals(0, s.mining.miningToolSlot);
    }

    @Test
    void beginRemoteMiningClearsPreviousProgressOnDifferentPos() {
        RtsStorageSession s = createMockSession();
        s.mining.miningPos = new BlockPos(1, 2, 3);

        // This should still work because the mock's mining.miningPos is set
        // and it doesn't equal the new pos, but player is null so
        // clearMineProgress won't be called.
        RtsMiningStateMachine.beginRemoteMining(null, s, new BlockPos(10, 20, 30), Direction.UP, 0);

        assertEquals(new BlockPos(10, 20, 30), s.mining.miningPos);
    }

    @Test
    void beginRemoteMiningSamePosDoesNotTriggerClear() {
        RtsStorageSession s = createMockSession();
        BlockPos pos = new BlockPos(5, 5, 5);
        s.mining.miningPos = pos;

        // Same position: the if-block condition (miningPos != null && !miningPos.equals(pos))
        // evaluates to false, so clearMineProgress is skipped.
        RtsMiningStateMachine.beginRemoteMining(null, s, pos, Direction.UP, 0);

        assertEquals(pos, s.mining.miningPos);
        assertEquals(Direction.UP, s.mining.miningFace);
    }
}
