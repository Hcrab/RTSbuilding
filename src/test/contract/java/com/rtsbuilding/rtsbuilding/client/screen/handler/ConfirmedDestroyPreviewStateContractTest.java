package com.rtsbuilding.rtsbuilding.client.screen.handler;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 锁定已确认破坏预览的状态 owner，防止超时和裁剪逻辑重新散落到屏幕控制器。
 */
class ConfirmedDestroyPreviewStateContractTest {
    @Test
    void controllerDelegatesStorageExpiryAndPruningToDedicatedState() throws IOException {
        String controller = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/handler/ScreenShapeController.java"));

        assertTrue(controller.contains(
                "private final ConfirmedDestroyPreviewState confirmedDestroyPreviews"));
        assertTrue(controller.contains("this.confirmedDestroyPreviews.rememberRange"));
        assertTrue(controller.contains("this.confirmedDestroyPreviews.rememberChain"));
        assertTrue(controller.contains("this.confirmedDestroyPreviews.activeRange"));
        assertTrue(controller.contains("this.confirmedDestroyPreviews.activeChain"));
        assertTrue(controller.contains("this.confirmedDestroyPreviews.removeRangeBlocks"));
        assertFalse(controller.contains("confirmedRangeDestroyPreviewUntilMs"));
        assertFalse(controller.contains("confirmedChainDestroyPreviewUntilMs"));
        assertFalse(controller.contains("pruneConfirmedDestroyPreview"));
        assertFalse(controller.contains("previewContains("));
    }

    @Test
    void stateOwnerCannotReachScreenWorldControllerConfigOrNetwork() throws IOException {
        String state = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/shape/ConfirmedDestroyPreviewState.java"));

        assertFalse(state.contains("BuilderScreen"));
        assertFalse(state.contains("ClientRtsController"));
        assertFalse(state.contains("import net.minecraft.client"));
        assertFalse(state.contains("Minecraft."));
        assertFalse(state.contains("import com.rtsbuilding.rtsbuilding.Config"));
        assertFalse(state.contains("Config."));
        assertFalse(state.contains("RtsWorkflowStatus"));
        assertFalse(state.contains("Packet"));
        assertTrue(state.contains("LongSupplier"),
                "超时必须使用可注入时钟，不能让单测等待墙钟");
        assertTrue(state.contains("Predicate<BlockPos> liveTarget"),
                "世界活目标判断必须由生产适配器注入");
    }
}
