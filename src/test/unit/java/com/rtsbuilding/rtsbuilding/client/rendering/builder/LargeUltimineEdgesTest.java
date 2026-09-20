package com.rtsbuilding.rtsbuilding.client.rendering.builder;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LargeUltimineEdgesTest {
    @Test
    void full4096BlockCubeRetainsAllOuterEdges() {
        List<BlockPos> positions = new ArrayList<>();
        for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) {
            positions.add(new BlockPos(x, y, z));
        }
        var edges = UltimineBlockMerger.getEdgeLines(positions);
        double length = edges.stream().mapToDouble(edge ->
                Math.abs(edge.x2() - edge.x1()) + Math.abs(edge.y2() - edge.y1()) + Math.abs(edge.z2() - edge.z1())).sum();
        assertEquals(12 * 16, length, 0.0001);
    }

    @Test
    void sparse4096BlockPreviewDoesNotLoseTargets() {
        List<BlockPos> positions = new ArrayList<>();
        for (int x = 0; x < 64; x++) for (int z = 0; z < 64; z++) positions.add(new BlockPos(x * 2, 0, z * 2));
        assertEquals(4096 * 12, UltimineBlockMerger.getEdgeLines(positions).size());
    }
}
