package com.rtsbuilding.rtsbuilding.blueprint.client;


import net.minecraft.core.BlockPos;

import static com.rtsbuilding.rtsbuilding.blueprint.client.BlueprintPanelUi.text;

/**
 * Calculates capture-box size, volume, and hit testing for blueprint creation.
 */
public final class BlueprintCaptureGeometry {
    private BlueprintCaptureGeometry() {
    }

    static String shortPos(BlockPos pos) {
        return pos == null ? "-" : pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }

    static String captureSizeText(BlockPos first, BlockPos second) {
        if (first == null || second == null) {
            return "";
        }
        int x = Math.abs(first.getX() - second.getX()) + 1;
        int y = Math.abs(first.getY() - second.getY());
        int z = Math.abs(first.getZ() - second.getZ()) + 1;
        return x + "x" + y + "x" + z;
    }

    static String capturePreviewSummaryLine(BlockPos first, BlockPos second, long blockCount) {
        return text("screen.rtsbuilding.blueprints.capture_preview_summary",
                captureSizeText(first, second),
                Long.toString(Math.max(0L, blockCount)));
    }

    static long captureVolume(BlockPos first, BlockPos second) {
        if (first == null || second == null) {
            return 0L;
        }
        long x = Math.abs(first.getX() - second.getX()) + 1L;
        long y = Math.abs(first.getY() - second.getY());
        long z = Math.abs(first.getZ() - second.getZ()) + 1L;
        return x * y * z;
    }

    static boolean isInsideSelection(BlockPos first, BlockPos second, BlockPos pos) {
        if (first == null || second == null || pos == null) {
            return false;
        }
        int minX = Math.min(first.getX(), second.getX());
        int minY = Math.min(first.getY(), second.getY()) + 1;
        int minZ = Math.min(first.getZ(), second.getZ());
        int maxX = Math.max(first.getX(), second.getX());
        int maxY = Math.max(first.getY(), second.getY());
        int maxZ = Math.max(first.getZ(), second.getZ());
        if (minY > maxY) {
            return false;
        }
        return pos.getX() >= minX && pos.getX() <= maxX
                && pos.getY() >= minY && pos.getY() <= maxY
                && pos.getZ() >= minZ && pos.getZ() <= maxZ;
    }
}
