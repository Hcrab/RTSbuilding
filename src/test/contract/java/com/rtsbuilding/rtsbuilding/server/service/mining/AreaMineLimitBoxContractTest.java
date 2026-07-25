package com.rtsbuilding.rtsbuilding.server.service.mining;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AreaMineLimitBoxContractTest {
    @Test
    void oversizedAreaMineBoxIsClampedByAxisAndVolumeCaps() {
        RtsUltimineProcessor.AreaMineLimitBox box =
                RtsUltimineProcessor.limitAreaMineBox(10, 49, 20, 59, 30, 69);

        int width = box.maxX() - box.minX() + 1;
        int height = box.maxY() - box.minY() + 1;
        int depth = box.maxZ() - box.minZ() + 1;

        assertEquals(10, box.minX());
        assertEquals(20, box.minY());
        assertEquals(30, box.minZ());
        assertTrue(width <= RtsMiningValidator.areaMineMaxWidth());
        assertTrue(height <= RtsMiningValidator.areaMineMaxHeight());
        assertTrue(depth <= RtsMiningValidator.areaMineMaxDepth());
        assertTrue((long) width * height * depth <= RtsMiningValidator.areaMineMaxVolume());
    }

    @Test
    void reversedCornersAreNormalizedBeforeClamping() {
        RtsUltimineProcessor.AreaMineLimitBox box =
                RtsUltimineProcessor.limitAreaMineBox(49, 10, 59, 20, 69, 30);

        assertEquals(10, box.minX());
        assertEquals(20, box.minY());
        assertEquals(30, box.minZ());
        assertTrue(box.maxX() >= box.minX());
        assertTrue(box.maxY() >= box.minY());
        assertTrue(box.maxZ() >= box.minZ());
    }

    @Test
    void queuedAreaMineUsesAxisAndVolumeLimitBox() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/server/service/mining/RtsUltimineProcessor.java"));
        String method = slice(source, "public static int queueAreaMine", "static AreaMineLimitBox limitAreaMineBox");

        assertTrue(method.contains("limitAreaMineBox(minX, maxX, minY, maxY, minZ, maxZ)"));
        assertFalse(method.contains("areaMineMaxSize"));
    }

    @Test
    void explicitRoundAreaDestroyEnvelopeAllowsCenteredDiameterMargin() {
        List<BlockPos> centeredDiameter = new ArrayList<>();
        for (int x = -6; x <= 6; x++) {
            centeredDiameter.add(new BlockPos(x, 64, 0));
        }
        List<BlockPos> tooWide = new ArrayList<>();
        for (int x = -7; x <= 6; x++) {
            tooWide.add(new BlockPos(x, 64, 0));
        }

        assertTrue(RtsUltimineProcessor.explicitAreaDestroyFitsSoftEnvelopeForCaps(
                centeredDiameter, 12, 12, 12, 1728));
        assertFalse(RtsUltimineProcessor.explicitAreaDestroyFitsSoftEnvelopeForCaps(
                tooWide, 12, 12, 12, 1728));
    }

    private static String slice(String source, String start, String end) {
        int startIndex = source.indexOf(start);
        int endIndex = source.indexOf(end, startIndex);
        assertTrue(startIndex >= 0, "Missing start marker: " + start);
        assertTrue(endIndex > startIndex, "Missing end marker after: " + start);
        return source.substring(startIndex, endIndex);
    }
}
