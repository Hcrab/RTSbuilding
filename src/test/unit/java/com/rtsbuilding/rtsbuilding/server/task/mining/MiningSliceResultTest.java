package com.rtsbuilding.rtsbuilding.server.task.mining;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;

class MiningSliceResultTest {
    @Test
    void waitingOutcomeRequiresExplicitWakeHint() {
        MiningTaskState state = new MiningTaskState(
                MiningTaskState.Mode.BATCH, -1, List.of(new BlockPos(0, 0, 0)),
                1, 0, 0, 0, Direction.DOWN, 0,
                false, true, 0.0F, -1, List.of());
        assertThrows(IllegalArgumentException.class,
                () -> new MiningSliceResult(state, 0, 0, 0, 0,
                        MiningSliceResult.Outcome.WAITING, null));
        assertThrows(IllegalArgumentException.class,
                () -> new MiningSliceResult(state, 0, 0, 0, 0,
                        MiningSliceResult.Outcome.CONTINUE, MiningWaitHint.tool()));
    }
}
