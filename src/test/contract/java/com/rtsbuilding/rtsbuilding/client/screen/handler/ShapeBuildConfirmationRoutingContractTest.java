package com.rtsbuilding.rtsbuilding.client.screen.handler;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ShapeBuildConfirmationRoutingContractTest {
    @Test
    void completingSelectionUsesTheSameClickWhenKeyboardConfirmationIsDisabled() throws IOException {
        String source = Files.readString(Path.of(
                "src/client/java/com/rtsbuilding/rtsbuilding/client/screen/handler/ScreenShapeController.java"));
        String buildMethod = compact(methodBody(source, "public void placeWithShape"));
        String destroyMethod = compact(methodBody(
                source, "BlockHitResult hit, double mouseY, Vec3 rayDir, RtsTraceInputKind inputKind)"));

        int buildAdvance = buildMethod.indexOf("advanceShapeSession(hit, rayDir, mouseY, shape)");
        int buildPolicy = buildMethod.indexOf("shouldSubmitShapeAfterSelection()", buildAdvance);
        int buildConfirm = buildMethod.indexOf("tryConfirmPendingShapeBuild(forcePlace)", buildPolicy);
        int destroyAdvance = destroyMethod.indexOf("advanceShapeSession(hit, rayDir, mouseY, shape)");
        int destroyPolicy = destroyMethod.indexOf("shouldSubmitShapeAfterSelection()", destroyAdvance);
        int destroyConfirm = destroyMethod.indexOf("tryConfirmPendingRangeDestroy(inputKind)", destroyPolicy);

        assertTrue(buildAdvance >= 0 && buildPolicy > buildAdvance && buildConfirm > buildPolicy,
                "范围建造完成选点后必须立即应用自动确认策略，不能再暗中等待第三次点击");
        assertTrue(destroyAdvance >= 0 && destroyPolicy > destroyAdvance && destroyConfirm > destroyPolicy,
                "范围破坏完成选点后必须与建造使用同一套自动确认策略");
    }

    private static String compact(String source) {
        return source.replaceAll("\\s+", " ");
    }

    private static String methodBody(String source, String signatureStart) {
        int start = source.indexOf(signatureStart);
        assertTrue(start >= 0, "method not found: " + signatureStart);
        int bodyStart = source.indexOf('{', start);
        int depth = 0;
        for (int i = bodyStart; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}' && --depth == 0) {
                return source.substring(bodyStart, i + 1);
            }
        }
        throw new AssertionError("method body is not closed: " + signatureStart);
    }
}
