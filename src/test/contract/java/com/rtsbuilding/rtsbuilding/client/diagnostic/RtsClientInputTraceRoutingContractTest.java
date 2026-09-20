package com.rtsbuilding.rtsbuilding.client.diagnostic;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 生产输入边界契约：只接受真实来源，禁止通过无参旧调用把诊断降回 UNKNOWN。
 */
class RtsClientInputTraceRoutingContractTest {
    private static final Path JAVA_ROOT = Path.of(
            "src/main/java/com/rtsbuilding/rtsbuilding");

    @Test
    void everyClientMiningStopUsesAnExplicitLifecycleOrigin() throws IOException {
        for (String file : new String[]{
                "client/screen/input/CameraInputHandler.java",
                "client/screen/standalone/BuilderScreenPointerActionOwner.java",
                "client/screen/standalone/BuilderScreenPointerGestureOwner.java",
                "client/screen/standalone/BuilderScreenKeyboardSessionOwner.java",
                "client/screen/standalone/BuilderScreenModeSessionOwner.java",
                "client/screen/standalone/BuilderScreenLifecycleOwner.java"}) {
            String source = source(file);
            assertFalse(source.contains("stopActiveMining();"),
                    file + " must not discard the stop origin");
        }
        String camera = source("client/screen/input/CameraInputHandler.java");
        assertTrue(camera.contains("public void stopActiveMining(RtsMiningStopOrigin origin)"));
        assertTrue(source("client/screen/standalone/BuilderScreenPointerGestureOwner.java")
                .contains("stopActiveMining(RtsMiningStopOrigin.POINTER_RELEASE)"));
        assertTrue(source("client/screen/standalone/BuilderScreenKeyboardSessionOwner.java")
                .contains("stopActiveMining(RtsMiningStopOrigin.KEY_RELEASE)"));
        assertTrue(source("client/screen/standalone/BuilderScreenLifecycleOwner.java")
                .contains("RtsMiningStopOrigin.LIFECYCLE_KEY_NOT_DOWN"));
    }

    @Test
    void cameraInputUsesOneObservedKindAcrossNormalMiningEntrances() throws IOException {
        String source = source("client/screen/input/CameraInputHandler.java");
        String method = methodBody(source, "public boolean startMiningAt");

        assertTrue(method.contains(
                "RtsTraceInputKind inputKind = keyboard ? RtsTraceInputKind.KEYBOARD : RtsTraceInputKind.MOUSE"));
        assertTrue(method.contains("handleQuickBuildRangeDestroyClick(mouseX, mouseY, inputKind)"));
        assertTrue(method.contains("confirmAreaMine(screen.getSelectedToolSlot(), screen.getShapeFillMode(), inputKind)"));
        assertTrue(method.contains("confirmPreview(screen, hit, preview, inputKind)"));
        assertTrue(method.contains("screen.getSelectedToolSlot(), inputKind"));
    }

    @Test
    void shapeConvenienceAndChainAdaptersKeepTheObservedKind() throws IOException {
        Map<String, String> sources = Map.of(
                "shape", source("client/screen/handler/ScreenShapeController.java"),
                "window", source("client/screen/standalone/BuilderScreenWindowActionOwner.java"),
                "convenience", source("client/screen/quickbuild/QuickBuildConvenienceController.java"),
                "panel", source("client/screen/quickbuild/QuickBuildPanel.java"),
                "ultimine", source("client/screen/ultimine/UltimineUiAdapter.java"),
                "controller", source("client/controller/ClientRtsController.java"),
                "owner", source("client/controller/ClientRtsInteractionOwner.java"));

        assertTrue(sources.get("shape").contains("tryConfirmPendingRangeDestroy(inputKind)"));
        assertTrue(sources.get("shape").contains("getSelectedToolSlot(), inputKind"));
        assertTrue(sources.get("window").contains("submitConvenienceDestroy(target.blockHit(), inputKind)"));
        assertTrue(sources.get("window").contains("selectRangeDestroyShape("));
        assertTrue(sources.get("window").contains("target.rayDir(), inputKind"));
        assertTrue(sources.get("convenience").contains(
                "confirmConvenienceDestroy(commonMode(), hit, commonSettings(), toolSlot, inputKind)"));
        assertTrue(sources.get("panel").contains("getSelectedToolSlot(), inputKind"));
        assertTrue(sources.get("ultimine").contains("transition.state.limit, (byte) 0, inputKind"));
        assertTrue(sources.get("controller").contains("abortMining(int toolSlot, RtsMiningStopOrigin stopOrigin)"));
        assertTrue(sources.get("owner").contains("inputOrigin(inputKind)"));
    }

    private static String source(String relativePath) throws IOException {
        return Files.readString(JAVA_ROOT.resolve(relativePath));
    }

    private static String methodBody(String source, String signatureStart) {
        int start = source.indexOf(signatureStart);
        assertTrue(start >= 0, "method not found: " + signatureStart);
        int bodyStart = source.indexOf('{', start);
        assertTrue(bodyStart >= 0, "method body not found: " + signatureStart);
        int depth = 0;
        for (int i = bodyStart; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(bodyStart, i + 1);
                }
            }
        }
        throw new AssertionError("method body is not closed: " + signatureStart);
    }
}
