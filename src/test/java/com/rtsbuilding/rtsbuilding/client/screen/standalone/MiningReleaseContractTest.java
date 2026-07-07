package com.rtsbuilding.rtsbuilding.client.screen.standalone;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MiningReleaseContractTest {
    @Test
    void mouseReleaseStopsMiningBeforeFloatingWindowsCanConsumeRelease() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/standalone/BuilderScreen.java"));
        String body = methodBody(source, "public boolean mouseReleased");

        int miningGuard = body.indexOf("this.cameraInput.isLeftMiningActive()");
        int stopMining = body.indexOf("this.cameraInput.stopActiveMining()", miningGuard);
        int floatingRelease = body.indexOf("handleFloatingWindowRelease");

        assertTrue(miningGuard >= 0, "mouse release must check active mining");
        assertTrue(stopMining > miningGuard, "mouse release must stop active mining");
        assertTrue(floatingRelease > stopMining,
                "active mining release must be handled before floating windows can swallow the release event");
    }

    @Test
    void keyboardReleaseStopsMiningImmediately() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/standalone/BuilderScreen.java"));
        String body = methodBody(source, "public boolean keyReleased");

        int keyboardMiningGuard = body.indexOf("this.cameraInput.isKeyboardMining()");
        int breakReleaseGuard = body.indexOf("ClientKeyMappings.ACTION_BREAK.matches(keyCode, scanCode)", keyboardMiningGuard);
        int stopMining = body.indexOf("this.cameraInput.stopActiveMining()", breakReleaseGuard);

        assertTrue(keyboardMiningGuard >= 0, "keyboard mining release guard missing");
        assertTrue(breakReleaseGuard > keyboardMiningGuard,
                "keyboard mining release must be tied to the break key");
        assertTrue(stopMining > breakReleaseGuard,
                "releasing the keyboard break key must stop mining immediately");
    }

    @Test
    void abortMiningClearsLocalBreakProgressImmediately() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/service/MiningOperationService.java"));
        String body = methodBody(source, "public void abortMining");

        int sendAbort = body.indexOf("RtsClientPacketGateway.sendMineAbort");
        int clearRender = body.indexOf("clearMineProgressRender(abortPos)");

        assertTrue(sendAbort >= 0, "abortMining must still send the server abort packet when possible");
        assertTrue(clearRender > sendAbort,
                "abortMining must clear local break progress immediately after sending abort");
        assertTrue(source.contains("private void clearMineProgressRender(BlockPos fallbackPos)"),
                "local render clearing should stay in a shared helper");
    }

    @Test
    void oldServerClearDoesNotWipeNewMiningRenderTarget() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/service/MiningOperationService.java"));
        String clearBody = methodBody(source, "private void clearMineProgressRender");
        String progressBody = methodBody(source, "public void applyMineProgress");

        assertTrue(progressBody.contains("clearActiveMineTargetIfMatches(pos)"),
                "server progress clear should release only the matching active mine target");
        assertTrue(clearBody.contains("fallbackPos == null || this.mineRenderPos.equals(fallbackPos)"),
                "a stale stage=-1 packet for an old block must not clear the new render target");
        assertTrue(source.contains("private void clearActiveMineTargetIfMatches(BlockPos pos)"),
                "active mining target clearing should be coordinate-matched");
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
