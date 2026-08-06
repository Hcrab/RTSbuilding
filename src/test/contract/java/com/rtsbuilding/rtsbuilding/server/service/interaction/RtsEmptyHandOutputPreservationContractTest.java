package com.rtsbuilding.rtsbuilding.server.service.interaction;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** 防止第三方方块把返回物放进临时空手槽后，又被真实主手恢复过程覆盖。 */
class RtsEmptyHandOutputPreservationContractTest {
    private static final Path INTERACTOR = Path.of(
            "src/main/java/com/rtsbuilding/rtsbuilding/server/service/interaction/RtsEmptyHandInteractor.java");
    private static final Path RECOVERY = Path.of(
            "src/main/java/com/rtsbuilding/rtsbuilding/server/util/RtsSyntheticHandOutputRecovery.java");
    private static final Path PLACEMENT = Path.of(
            "src/main/java/com/rtsbuilding/rtsbuilding/server/service/placement/RtsPlacementExecutor.java");
    private static final Path GUI_BINDING = Path.of(
            "src/main/java/com/rtsbuilding/rtsbuilding/server/storage/RtsGuiBindingHelper.java");

    @Test
    void syntheticEmptyHandOutputsAreReturnedBeforeFallbackContinues() throws IOException {
        String source = Files.readString(INTERACTOR, StandardCharsets.UTF_8);
        int primary = source.indexOf("UseOnOutcome primary =");
        int outputCheck = source.indexOf("RtsSyntheticHandOutputRecovery.hasOutput(primary)", primary);
        int airFallback = source.indexOf("return InteractionHelper.useItemWithMainHand", outputCheck);
        int restoreOutput = source.indexOf(
                "RtsSyntheticHandOutputRecovery.recoverToPlayer(player, outcome)", airFallback);

        assertTrue(primary >= 0);
        assertTrue(outputCheck > primary);
        assertTrue(airFallback > outputCheck);
        assertTrue(restoreOutput > airFallback);
        String recovery = Files.readString(RECOVERY, StandardCharsets.UTF_8);
        assertTrue(recovery.contains(
                "ItemHandlerHelper.giveItemToPlayer(player, outcome.remainder().copy())"));
        assertTrue(recovery.contains("return EnumActionResult.SUCCESS"));
    }

    @Test
    void everyGeneralEmptyHandProxyRecoversThirdPartyOutputs() throws IOException {
        String placement = Files.readString(PLACEMENT, StandardCharsets.UTF_8);
        String guiBinding = Files.readString(GUI_BINDING, StandardCharsets.UTF_8);

        assertTrue(placement.contains(
                "RtsSyntheticHandOutputRecovery.recoverToPlayer(player, emptyUse)"));
        assertTrue(placement.contains(
                "RtsSyntheticHandOutputRecovery.recoverToPlayer(player, emptyFallback)"));
        assertTrue(guiBinding.contains(
                "RtsSyntheticHandOutputRecovery.recoverToPlayer(player, outcome)"));
    }
}
