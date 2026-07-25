package com.rtsbuilding.rtsbuilding.client.rendering.blueprint;

import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.screen.blueprint.BlueprintPanel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * Blueprint ghost bounds filter.
 * <p>
 * Clips blueprint ghost blocks to the RTS boundary, so only preview blocks
 * within the player's base area are rendered.
 */
public final class BlueprintGhostBoundsFilter {

    private BlueprintGhostBoundsFilter() {
    }

    /**
     * Filters the blueprint block list, keeping only blocks within RTS bounds.
     *
     * @param blocks the blueprint block list to filter
     * @return a new list containing only in-bounds blocks; returns the original list if the controller has no bounds
     */
    public static List<BlueprintPanel.BlueprintGhostBlock> filter(
            List<BlueprintPanel.BlueprintGhostBlock> blocks) {
        ClientRtsController controller = ClientRtsController.get();
        if (!controller.hasBounds()) {
            return blocks;
        }
        double ax = controller.getAnchorX();
        double az = controller.getAnchorZ();
        double r = controller.getMaxRadius();
        int minBlockX = Mth.floor(ax - r);
        int maxBlockX = Mth.ceil(ax + r) - 1;
        int minBlockZ = Mth.floor(az - r);
        int maxBlockZ = Mth.ceil(az + r) - 1;
        List<BlueprintPanel.BlueprintGhostBlock> result = new ArrayList<>(blocks.size());
        for (BlueprintPanel.BlueprintGhostBlock block : blocks) {
            if (block == null) continue;
            BlockPos pos = block.pos();
            if (pos.getX() >= minBlockX && pos.getX() <= maxBlockX
                    && pos.getZ() >= minBlockZ && pos.getZ() <= maxBlockZ) {
                result.add(block);
            }
        }
        return result.isEmpty() ? List.of() : result;
    }
}
