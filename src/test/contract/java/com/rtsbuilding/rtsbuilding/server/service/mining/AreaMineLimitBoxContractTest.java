package com.rtsbuilding.rtsbuilding.server.service.mining;

import net.minecraft.util.math.BlockPos;
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
    void configuredSelectionLimitsStayInsideFeatureEnvelopeWhileWireUsesSafeChunks() throws IOException {
        String config = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/Config.java"));
        String minePayload = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/network/builder/C2SRtsAreaMinePayload.java"));
        String destroyPayload = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/network/builder/C2SRtsAreaDestroyPayload.java"));

        assertTrue(config.contains("RtsProtocolLimits.AREA_MINE_MAX_VOLUME"));
        assertTrue(config.contains("RtsProtocolLimits.AREA_DESTROY_MAX_POSITIONS"));
        assertTrue(minePayload.contains("MAX_VOLUME=RtsProtocolLimits.AREA_MINE_MAX_VOLUME"));
        assertTrue(destroyPayload.contains(
                "MAX_POSITIONS = RtsProtocolLimits.AREA_DESTROY_MAX_POSITIONS"));
        assertTrue(destroyPayload.contains("MAX_POSITIONS_PER_PACKET = 2048"),
                "1.12 自定义包必须把大范围破坏拆成远低于 32767 字节上限的分片");
        assertTrue(destroyPayload.contains("chunkCount")
                        && destroyPayload.contains("totalPositions"),
                "分片协议必须保留整次提交的总量与顺序元数据");
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
